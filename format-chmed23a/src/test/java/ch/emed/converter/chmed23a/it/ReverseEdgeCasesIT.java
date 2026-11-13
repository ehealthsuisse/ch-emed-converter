package ch.emed.converter.chmed23a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed23a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** T049, T050: required data of the reverse direction (M23-REV-05, -06, -08..10). */
class ReverseEdgeCasesIT {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  /** CH EMED of an official example, changed in its Patient resource. */
  private static byte[] chEmed(String example, Consumer<ObjectNode> changePatient)
      throws Exception {
    ConversionResult forward =
        converter.convert(
            new ConversionRequest(
                FormatId.CHMED23A,
                FormatId.CH_EMED,
                Fixtures.bytes("chmed23a/official/" + example),
                TestOptions.withAuthorAndPatientId()));
    ObjectNode bundle = (ObjectNode) MAPPER.readTree(forward.output().orElseThrow());
    for (JsonNode entry : bundle.path("entry")) {
      if ("Patient".equals(entry.path("resource").path("resourceType").asText())) {
        changePatient.accept((ObjectNode) entry.path("resource"));
      }
    }
    return MAPPER.writeValueAsBytes(bundle);
  }

  private static List<String> errors(byte[] chEmed) {
    ConversionResult result =
        converter.convert(
            new ConversionRequest(
                FormatId.CH_EMED, FormatId.CHMED23A, chEmed, ConversionOptions.defaults()));
    assertThat(result.report().entries())
        .allSatisfy(e -> assertThat(e.message()).doesNotContain("Louloui"));
    return result.report().entries().stream()
        .filter(e -> e.severity() == Severity.ERROR)
        .map(e -> e.code() + " " + e.path())
        .toList();
  }

  @Test
  void unknownGenderIsMissingRequiredData() throws Exception {
    assertThat(errors(chEmed("Example1.json", p -> p.put("gender", "unknown"))))
        .containsExactly(ReasonCode.MISSING_REQUIRED_DATA + " Patient.gender");
  }

  @Test
  void anIdentifierWithoutValueIsRefused() throws Exception {
    assertThat(
            errors(
                chEmed(
                    "Example1.json",
                    p -> {
                      p.remove("identifier");
                      p.putArray("identifier")
                          .addObject()
                          .put("system", "urn:oid:2.16.756.5.30.1.123.100.1.1.1");
                    })))
        // CH Core requires an identifier with value: the input validation refuses it first
        .anySatisfy(e -> assertThat(e).startsWith(ReasonCode.FHIR_VALIDATION));
  }

  @Test
  void languageComesFromTheDocument() throws Exception {
    ConversionResult result =
        converter.convert(
            new ConversionRequest(
                FormatId.CH_EMED,
                FormatId.CHMED23A,
                chEmed("Example1.json", p -> p.remove("communication")),
                ConversionOptions.defaults()));
    String json = new String(result.output().orElseThrow(), StandardCharsets.UTF_8);
    assertThat(json).contains("\"lng\":\"de\"");
  }

  @Test
  void aPrescriptionByThePatientIsRefused() throws Exception {
    ObjectNode bundle = (ObjectNode) MAPPER.readTree(chEmed("Example4.json", p -> {}));
    String patient = null;
    for (JsonNode entry : bundle.path("entry")) {
      if ("Patient".equals(entry.path("resource").path("resourceType").asText())) {
        patient = entry.path("fullUrl").asText();
      }
    }
    for (JsonNode entry : bundle.path("entry")) {
      if ("Composition".equals(entry.path("resource").path("resourceType").asText())) {
        ObjectNode author = (ObjectNode) entry.path("resource").path("author").get(0);
        author.put("reference", patient);
      }
    }
    ConversionResult result =
        converter.convert(
            new ConversionRequest(
                FormatId.CH_EMED,
                FormatId.CHMED23A,
                MAPPER.writeValueAsBytes(bundle),
                ConversionOptions.defaults()));
    assertThat(result.output()).isEmpty();
    assertThat(result.report().entries())
        .filteredOn(e -> e.severity() == Severity.ERROR)
        .extracting(ReportEntry::path)
        .isNotEmpty();
  }
}
