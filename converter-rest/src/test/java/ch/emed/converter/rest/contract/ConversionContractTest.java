package ch.emed.converter.rest.contract;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** T014: successful conversions conform to {@code contracts/openapi.yaml} (FR-006, FR-011). */
class ConversionContractTest extends AbstractRestTest {

  private static final JsonNode AUTHOR = RestTestSupport.withAuthor("");

  @Test
  void chmed16aToChEmedConformsToContract() throws Exception {
    byte[] plan = RestTestSupport.read(RestTestSupport.CHMED16A.resolve("plan-basic.json"));

    mvc.perform(convert("chmed16a", "ch-emed", plan, AUTHOR))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.outputMediaType").value("application/fhir+json"));
  }

  @Test
  void chEmedToChmed16aConformsToContract() throws Exception {
    byte[] card =
        RestTestSupport.read(RestTestSupport.CH_EMED.resolve("medication-card-example.json"));

    mvc.perform(convert("ch-emed", "chmed16a", card, null))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
        .andExpect(jsonPath("$.outputMediaType").value("application/json"));
  }

  @Test
  void qrPayloadGivesSameDocumentAsPlainJson() throws Exception {
    byte[] plan = RestTestSupport.read(RestTestSupport.CHMED16A.resolve("plan-basic.json"));
    byte[] qr = RestTestSupport.read(RestTestSupport.CHMED16A.resolve("plan-basic.qr.txt"));

    MvcResult fromJson =
        mvc.perform(convert("chmed16a", "ch-emed", plan, AUTHOR))
            .andExpect(status().isOk())
            .andReturn();
    MvcResult fromQr =
        mvc.perform(convert("chmed16a", "ch-emed", qr, AUTHOR))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
            .andReturn();

    assertThat(output(fromQr)).isEqualTo(output(fromJson));
  }

  @Test
  void correlationIdIsEchoedInHeaderAndBody() throws Exception {
    byte[] card =
        RestTestSupport.read(RestTestSupport.CH_EMED.resolve("medication-card-example.json"));

    mvc.perform(convert("ch-emed", "chmed16a", card, null).header("X-Correlation-Id", "abc-123"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Correlation-Id", "abc-123"))
        .andExpect(jsonPath("$.correlationId").value("abc-123"));
  }

  private static byte[] output(MvcResult result) throws Exception {
    JsonNode body = RestTestSupport.json(result.getResponse().getContentAsByteArray());
    return Base64.getDecoder().decode(body.get("output").asText());
  }
}
