package ch.emed.converter.rest.parity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.api.Confidentiality;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.PatientIdentifier;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReportJsonWriter;
import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import ch.emed.converter.rest.RestTestSupport.CliRun;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * T015: for the whole feature 001 corpus and for every option, the service returns the same
 * document (byte-identical after base64 decoding) and the same report as library and CLI (SC-001,
 * FR-003, constitution Conversion Scope).
 */
class ServiceLibraryParityTest extends AbstractRestTest {

  private static final ConversionOptions DEFAULTS = ConversionOptions.defaults();
  private static final ConversionOptions WITH_AUTHOR = DEFAULTS.withAuthor(RestTestSupport.AUTHOR);

  @TempDir static Path temp;

  static Stream<Arguments> corpus() {
    return Stream.concat(
        RestTestSupport.corpus(RestTestSupport.CHMED16A).stream()
            .map(p -> Arguments.of(p, "chmed16a", "ch-emed")),
        RestTestSupport.corpus(RestTestSupport.CH_EMED).stream()
            .map(p -> Arguments.of(p, "ch-emed", "chmed16a")));
  }

  /** Includes every failing input: parity also holds for outcome and report of failures. */
  @ParameterizedTest(name = "{1} -> {2}: {0}")
  @MethodSource("corpus")
  void corpusWithDefaultOptions(Path input, String from, String to) throws Exception {
    assertParity(input, from, to, null, List.of(), DEFAULTS);
  }

  static Stream<Path> chmed16aCorpus() {
    return RestTestSupport.corpus(RestTestSupport.CHMED16A).stream();
  }

  /** CHMED16A plans need author details for CH EMED; with them the success path is compared. */
  @ParameterizedTest(name = "chmed16a -> ch-emed with author: {0}")
  @MethodSource("chmed16aCorpus")
  void chmed16aCorpusWithAuthor(Path input) throws Exception {
    assertParity(
        input, "chmed16a", "ch-emed", RestTestSupport.withAuthor(""), List.of(), WITH_AUTHOR);
  }

  /** One case per option; the library options are built explicitly, not by the service code. */
  static Stream<Arguments> options() {
    Path plan = RestTestSupport.CHMED16A.resolve("plan-basic.json");
    Path noIds = RestTestSupport.restFixture("plan-no-ids.json");
    String patientId = "\"patientIdentifier\":{\"system\":\"urn:oid:2.999.1\",\"value\":\"4711\"}";
    return Stream.of(
        Arguments.of("author", plan, "", List.of(), WITH_AUTHOR),
        Arguments.of(
            "strict", plan, "\"strict\":true", List.of("--strict"), WITH_AUTHOR.withStrict(true)),
        Arguments.of(
            "language",
            plan,
            "\"language\":\"fr-CH\"",
            List.of("--language", "fr-CH"),
            WITH_AUTHOR.withDefaultLanguage("fr-CH")),
        Arguments.of(
            "confidentiality",
            plan,
            "\"confidentiality\":\"restricted\"",
            List.of("--confidentiality", "restricted"),
            WITH_AUTHOR.withConfidentiality(Confidentiality.RESTRICTED)),
        Arguments.of(
            "patientIdentifier",
            noIds,
            patientId,
            List.of("--patient-id", "urn:oid:2.999.1|4711"),
            WITH_AUTHOR.withPatientIdentifier(new PatientIdentifier("urn:oid:2.999.1", "4711"))));
  }

  /** Every case also passes the author option, through the request and as CLI file. */
  @ParameterizedTest(name = "option {0}")
  @MethodSource("options")
  void everyOption(
      String name,
      Path input,
      String optionMembers,
      List<String> cliArgs,
      ConversionOptions libraryOptions)
      throws Exception {
    assertParity(
        input,
        "chmed16a",
        "ch-emed",
        RestTestSupport.withAuthor(optionMembers),
        cliArgs,
        libraryOptions);
  }

  private void assertParity(
      Path input,
      String from,
      String to,
      JsonNode options,
      List<String> cliArgs,
      ConversionOptions libraryOptions)
      throws Exception {
    List<String> args = new ArrayList<>(cliArgs);
    if (options != null && options.has("author") && !args.contains("--author")) {
      args.addAll(List.of("--author", RestTestSupport.authorFile(temp).toString()));
    }
    byte[] document = RestTestSupport.read(input);
    ConversionResult library =
        converter()
            .convert(
                new ConversionRequest(
                    FormatId.fromExternalName(from),
                    FormatId.fromExternalName(to),
                    document,
                    libraryOptions));
    byte[] libraryReport = ReportJsonWriter.write(library.report());
    CliRun cli = RestTestSupport.cli(converter(), input, from, to, args);
    MockHttpServletResponse service =
        mvc.perform(convert(from, to, document, options)).andReturn().getResponse();
    JsonNode body = RestTestSupport.json(service.getContentAsByteArray());

    assertThat(new String(cli.report(), StandardCharsets.UTF_8))
        .isEqualTo(new String(libraryReport, StandardCharsets.UTF_8));
    assertThat(body.get("report")).isEqualTo(RestTestSupport.json(libraryReport));

    if (library.outcome() == Outcome.FAILED) {
      assertThat(cli.exitCode()).isEqualTo(1);
      assertThat(service.getStatus()).isEqualTo(422);
      assertThat(body.has("output")).isFalse();
    } else {
      byte[] expected = library.output().orElseThrow();
      assertThat(cli.exitCode()).isZero();
      assertThat(cli.output()).isEqualTo(expected);
      assertThat(service.getStatus()).isEqualTo(200);
      assertThat(body.get("outcome").asText()).isEqualTo(library.outcome().name());
      assertThat(Base64.getDecoder().decode(body.get("output").asText())).isEqualTo(expected);
    }
  }
}
