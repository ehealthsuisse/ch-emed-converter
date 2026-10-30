package ch.emed.converter.rest.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * T032: the service publishes the contract of the spec unchanged (FR-011, research R8), and the
 * contract describes every operation the service offers.
 */
class OpenApiServedTest extends AbstractRestTest {

  @Test
  void servesTheContractUnchanged() throws Exception {
    MockHttpServletResponse response =
        mvc.perform(get("/api/v1/openapi.yaml"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();

    assertThat(response.getContentType()).startsWith("application/yaml");
    assertThat(response.getContentAsByteArray())
        .isEqualTo(RestTestSupport.read(Path.of(RestTestSupport.OPENAPI)));
  }

  @Test
  void contractDescribesEveryOfferedOperation() throws Exception {
    String contract =
        new String(
            RestTestSupport.read(Path.of(RestTestSupport.OPENAPI)),
            java.nio.charset.StandardCharsets.UTF_8);

    assertThat(contract)
        .contains(
            "  /conversions/{from}/{to}:\n    post:",
            "  /capabilities:\n    get:",
            "  /openapi.yaml:\n    get:");
    // every operation the service maps is reachable (not 404 from routing)
    assertThat(mvc.perform(get("/api/v1/capabilities")).andReturn().getResponse().getStatus())
        .isEqualTo(200);
    assertThat(mvc.perform(get("/api/v1/openapi.yaml")).andReturn().getResponse().getStatus())
        .isEqualTo(200);
  }
}
