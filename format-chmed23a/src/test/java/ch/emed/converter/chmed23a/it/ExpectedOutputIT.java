package ch.emed.converter.chmed23a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed23a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T031: byte-exact expected CH EMED outputs of the official examples (the PDF of the original
 * representation is masked). Without an expected file, the actual output is written to {@code
 * target/expected-candidates/} for review and the test fails.
 */
class ExpectedOutputIT {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  @ParameterizedTest
  @ValueSource(strings = {"Example1", "Example2", "Example3", "Example4"})
  void officialExampleMatchesTheExpectedOutput(String example) throws Exception {
    ConversionResult result =
        converter.convert(
            new ConversionRequest(
                FormatId.CHMED23A,
                FormatId.CH_EMED,
                Fixtures.bytes("chmed23a/official/" + example + ".json"),
                TestOptions.withAuthorAndPatientId()));
    byte[] actual =
        result.output().orElseThrow(() -> new AssertionError(result.report().entries()));
    String expected = "expected/chmed23a-to-chemed/" + example + ".json";
    if (getClass().getResource("/fixtures/" + expected) == null) {
      JsonNode root = MAPPER.readTree(actual);
      for (JsonNode entry : root.path("entry")) {
        if ("Binary".equals(entry.path("resource").path("resourceType").asText())) {
          ((ObjectNode) entry.path("resource")).put("data", "{{pdf}}");
        }
      }
      Path candidate = Path.of("target/expected-candidates/" + example + ".json");
      Files.createDirectories(candidate.getParent());
      Files.writeString(
          candidate,
          MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n",
          StandardCharsets.UTF_8);
      assertThat(candidate).as("expected output missing; candidate written").doesNotExist();
    }
    ChEmedAssertions.assertMatchesExpected(actual, expected, "unused");
  }
}
