package ch.emed.converter.rest.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/** T016: outcome with warnings, and omitted options mean library defaults (FR-004). */
class ConversionOutcomeTest extends AbstractRestTest {

  private static final ConversionOptions WITH_AUTHOR =
      ConversionOptions.defaults().withAuthor(RestTestSupport.AUTHOR);

  @Test
  void successWithWarningsListsEveryWarning() throws Exception {
    List<Path> withWarnings =
        RestTestSupport.corpus(RestTestSupport.CHMED16A).stream()
            .filter(p -> library(p).outcome() == Outcome.SUCCESS_WITH_WARNINGS)
            .toList();
    assertThat(withWarnings).as("corpus inputs converting with warnings").isNotEmpty();

    for (Path input : withWarnings) {
      byte[] response =
          mvc.perform(
                  convert(
                      "chmed16a",
                      "ch-emed",
                      RestTestSupport.read(input),
                      RestTestSupport.withAuthor("")))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();
      JsonNode body = RestTestSupport.json(response);

      assertThat(body.get("outcome").asText()).isEqualTo("SUCCESS_WITH_WARNINGS");
      assertThat(warnings(body.get("report")))
          .as(input.getFileName().toString())
          .isEqualTo(libraryWarnings(input))
          .isNotEmpty();
    }
  }

  @Test
  void omittedAndEmptyOptionsEqualLibraryDefaults() throws Exception {
    byte[] card =
        RestTestSupport.read(RestTestSupport.CH_EMED.resolve("medication-card-example.json"));

    JsonNode withoutOptions = body(card, null);
    JsonNode emptyOptions = body(card, RestTestSupport.json("{}"));
    JsonNode explicitDefaults =
        body(
            card,
            RestTestSupport.json(
                "{\"strict\":false,\"language\":\"de-CH\",\"confidentiality\":\"normal\"}"));

    assertThat(emptyOptions.get("output")).isEqualTo(withoutOptions.get("output"));
    assertThat(explicitDefaults.get("output")).isEqualTo(withoutOptions.get("output"));
    assertThat(emptyOptions.get("report")).isEqualTo(withoutOptions.get("report"));
  }

  private JsonNode body(byte[] document, JsonNode options) throws Exception {
    return RestTestSupport.json(
        mvc.perform(convert("ch-emed", "chmed16a", document, options))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray());
  }

  private ConversionResult library(Path input) {
    return converter()
        .convert(
            new ConversionRequest(
                FormatId.CHMED16A, FormatId.CH_EMED, RestTestSupport.read(input), WITH_AUTHOR));
  }

  private List<String> libraryWarnings(Path input) {
    return library(input).report().entries().stream()
        .filter(e -> e.severity() == Severity.WARNING)
        .map(e -> e.code() + " " + e.path())
        .toList();
  }

  private static List<String> warnings(JsonNode report) {
    Stream<JsonNode> entries = StreamSupport.stream(report.get("entries").spliterator(), false);
    return entries
        .filter(e -> e.get("severity").asText().equals("WARNING"))
        .map(e -> e.get("code").asText() + " " + e.get("path").asText())
        .toList();
  }
}
