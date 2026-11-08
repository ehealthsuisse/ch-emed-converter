package ch.emed.converter.rest.contract;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MvcResult;

/** T047 (feature 003, US5): QR and eRezept forms through REST (contracts/rest-api.md of 003). */
class QrConversionContractTest extends AbstractRestTest {

  private static final Path QR = RestTestSupport.FIXTURES.resolve("qr");
  private static final Path EREZEPT = RestTestSupport.FIXTURES.resolve("erezept");
  private static final byte[] CARD =
      RestTestSupport.read(RestTestSupport.CH_EMED.resolve("medication-card-example.json"));
  private static final byte[] PRESCRIPTION =
      RestTestSupport.read(RestTestSupport.CH_EMED.resolve("medication-prescription-example.json"));

  private static JsonNode form(String outputForm) {
    return RestTestSupport.json("{\"outputForm\":\"" + outputForm + "\"}");
  }

  @ParameterizedTest
  @CsvSource({"document, application/json", "qr-payload, text/plain", "qr-image, image/png"})
  void outputFormsConformToContract(String outputForm, String mediaType) throws Exception {
    mvc.perform(convert("ch-emed", "chmed16a", CARD, form(outputForm)))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.outputMediaType").value(mediaType))
        .andExpect(jsonPath("$.signature").doesNotExist());
  }

  @Test
  void unknownOutputFormIsMalformed() throws Exception {
    mvc.perform(convert("ch-emed", "chmed16a", CARD, form("pdf")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorType").value("MALFORMED_REQUEST"))
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("options.outputForm")));
  }

  @Test
  void qrFormForTargetChEmedIsMalformedWithoutReport() throws Exception {
    byte[] plan = RestTestSupport.read(RestTestSupport.CHMED16A.resolve("plan-basic.json"));

    mvc.perform(
            convert(
                "chmed16a",
                "ch-emed",
                plan,
                RestTestSupport.withAuthor("\"outputForm\":\"qr-image\"")))
        .andExpect(status().isBadRequest())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.errorType").value("MALFORMED_REQUEST"))
        .andExpect(jsonPath("$.report").doesNotExist());
  }

  @Test
  void qrImageInputConvertsLikeThePayload() throws Exception {
    byte[] image = RestTestSupport.read(QR.resolve("plan-basic.png"));

    mvc.perform(convert("chmed16a", "ch-emed", image, RestTestSupport.withAuthor("")))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.outputMediaType").value("application/fhir+json"));
  }

  @Test
  void signedErezeptReturnsUnverifiedSignatureData() throws Exception {
    byte[] url = RestTestSupport.read(EREZEPT.resolve("rx-signed.url.txt"));

    MvcResult result =
        mvc.perform(convert("chmed16a", "ch-emed", url, null))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
            .andReturn();

    JsonNode body = RestTestSupport.json(result.getResponse().getContentAsByteArray());
    JsonNode signature = body.get("signature");
    assertThat(signature.get("identity").asText()).isEqualTo("Erika+Muster+%28HIN%7Cemust1%29");
    assertThat(signature.get("timestamp").asText()).isEqualTo("1767225600");
    assertThat(signature.get("signature").asText()).startsWith("3066023100");
    assertThat(signature.get("verified").asBoolean()).isFalse();
    assertThat(body.get("report").toString())
        .contains("SIGNATURE_NOT_VERIFIED")
        .doesNotContain("Erika")
        .doesNotContain("1767225600")
        .doesNotContain("3066023100");
  }

  @ParameterizedTest
  @CsvSource({
    "qr/no-code.png, QR_NOT_FOUND, INVALID_INPUT",
    "qr/two-codes.png, QR_MULTIPLE, INVALID_INPUT",
    "qr/not-an-image.gif, IMAGE_UNREADABLE, INVALID_INPUT",
    "erezept/rx-missing-s.url.txt, EREZEPT_URL_INVALID, INVALID_INPUT"
  })
  void unusableInputsAreInvalidInputWithReport(String fixture, String code, String errorType)
      throws Exception {
    byte[] input = RestTestSupport.read(RestTestSupport.FIXTURES.resolve(fixture));

    mvc.perform(convert("chmed16a", "ch-emed", input, RestTestSupport.withAuthor("")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.errorType").value(errorType))
        .andExpect(jsonPath("$.report.entries[*].code").value(Matchers.hasItem(code)));
  }

  @Test
  void imageOfAPrescriptionIsAFailedConversionWithReport() throws Exception {
    mvc.perform(convert("ch-emed", "chmed16a", PRESCRIPTION, form("qr-image")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.errorType").value("CONVERSION_FAILED"))
        .andExpect(
            jsonPath("$.report.entries[*].code").value(Matchers.hasItem("QR_IMAGE_NOT_ALLOWED")));
  }

  @Test
  void capabilitiesListFormsAndImageLimit() throws Exception {
    mvc.perform(get("/api/v1/capabilities"))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(
            jsonPath("$.formats[?(@.name=='chmed16a')].inputForms[*]")
                .value(
                    Matchers.containsInAnyOrder("json", "qr-payload", "erezept-url", "qr-image")))
        .andExpect(
            jsonPath("$.formats[?(@.name=='chmed16a')].outputForms[*]")
                .value(Matchers.containsInAnyOrder("document", "qr-payload", "qr-image")))
        .andExpect(
            jsonPath("$.formats[?(@.name=='ch-emed')].outputForms[*]")
                .value(Matchers.contains("document")))
        .andExpect(jsonPath("$.limits.maxImagePixels").value(40_000_000))
        .andExpect(
            jsonPath("$.options[?(@.name=='outputForm')].default")
                .value(Matchers.contains("document")));
  }
}
