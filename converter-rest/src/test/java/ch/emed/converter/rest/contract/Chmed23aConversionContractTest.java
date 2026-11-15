package ch.emed.converter.rest.contract;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** T065 (feature 006, US4): CHMED23A through REST, OpenAPI 1.3.0. */
class Chmed23aConversionContractTest extends AbstractRestTest {

  private static final Path OFFICIAL =
      Path.of(
          "..", "format-chmed23a", "src", "test", "resources", "fixtures", "chmed23a", "official");

  private static final byte[] EXAMPLE1 = RestTestSupport.read(OFFICIAL.resolve("Example1.json"));
  private static final byte[] EXAMPLE4 = RestTestSupport.read(OFFICIAL.resolve("Example4.json"));

  /** Example 4 has no usable patient id (research F7): the option supplies one. */
  private static final String PATIENT_ID =
      "\"patientIdentifier\":{\"system\":\"urn:oid:2.999.1\",\"value\":\"TEST-0001\"}";

  private static JsonNode options(String outputForm) {
    return RestTestSupport.withAuthor("\"outputForm\":\"" + outputForm + "\"");
  }

  private byte[] chEmed(byte[] chmed23a) throws Exception {
    byte[] body =
        mvc.perform(
                convert("chmed23a", "ch-emed", chmed23a, RestTestSupport.withAuthor(PATIENT_ID)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    return Base64.getDecoder().decode(RestTestSupport.json(body).get("output").asText());
  }

  @Test
  void planAndPrescriptionToChEmed() throws Exception {
    for (byte[] input : new byte[][] {EXAMPLE1, EXAMPLE4}) {
      mvc.perform(convert("chmed23a", "ch-emed", input, RestTestSupport.withAuthor(PATIENT_ID)))
          .andExpect(status().isOk())
          .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
          .andExpect(jsonPath("$.outputMediaType").value("application/fhir+json"));
    }
  }

  @ParameterizedTest
  @CsvSource({"document, application/json", "qr-payload, text/plain", "qr-image, image/png"})
  void outputFormsForPlanAndPrescription(String outputForm, String mediaType) throws Exception {
    for (byte[] input : new byte[][] {EXAMPLE1, EXAMPLE4}) {
      mvc.perform(convert("ch-emed", "chmed23a", chEmed(input), options(outputForm)))
          .andExpect(status().isOk())
          .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
          .andExpect(jsonPath("$.outputMediaType").value(mediaType));
    }
  }

  @Test
  void payloadInputGivesTheSameDocument() throws Exception {
    byte[] card = chEmed(EXAMPLE1);
    byte[] body =
        mvc.perform(convert("ch-emed", "chmed23a", card, options("qr-payload")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    byte[] payload = Base64.getDecoder().decode(RestTestSupport.json(body).get("output").asText());
    assertThat(new String(payload, StandardCharsets.US_ASCII)).startsWith("CHMED23A.");
    byte[] back = chEmed(payload);
    assertThat(RestTestSupport.json(back).get("resourceType").asText()).isEqualTo("Bundle");
  }

  @Test
  void directPairWithChmed16aIsUnsupported() throws Exception {
    mvc.perform(convert("chmed23a", "chmed16a", EXAMPLE1, null))
        .andExpect(status().isNotFound())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.errorType").value("UNSUPPORTED_CONVERSION"))
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("Convert through ch-emed")));
  }

  @Test
  void invalidChunksAreUnprocessable() throws Exception {
    byte[] chunk = "CHMED23A.1/2.H4sIAAAA".getBytes(StandardCharsets.US_ASCII);
    mvc.perform(convert("chmed23a", "ch-emed", chunk, null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.report.entries[*].code").value(Matchers.hasItem("CHUNK_INVALID")));
  }
}
