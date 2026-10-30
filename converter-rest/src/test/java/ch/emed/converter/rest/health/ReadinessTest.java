package ch.emed.converter.rest.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import ch.emed.converter.rest.config.ConverterHolder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T036: liveness and readiness probes (FR-016, research R7). Readiness is DOWN as long as the
 * converter with its validation resources could not be built, and conversions are refused.
 */
class ReadinessTest {

  /** Normal start: the vendored IG packages load and the service is ready. */
  @Nested
  class Started extends AbstractRestTest {

    @Test
    void livenessAndReadinessAreUp() throws Exception {
      assertThat(body("/actuator/health/liveness")).contains("\"status\":\"UP\"");
      assertThat(body("/actuator/health/readiness")).contains("\"status\":\"UP\"");
    }

    private String body(String path) throws Exception {
      return mvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }
  }

  /**
   * Building the converter fails as with missing or corrupt IG packages ({@code
   * HapiChEmedValidator} throws "Cannot load package …").
   */
  @Nested
  @SpringBootTest
  @AutoConfigureMockMvc
  class MissingPackages {

    @Autowired private MockMvc mvc;

    @TestBean private ConverterHolder converterHolder;

    static ConverterHolder converterHolder() {
      return ConverterHolder.build(
          () -> {
            throw new IllegalStateException("Cannot load package ch.fhir.ig.ch-emed#6.0.0");
          });
    }

    @Test
    void readinessIsDownButLivenessUp() throws Exception {
      MockHttpServletResponse readiness =
          mvc.perform(get("/actuator/health/readiness")).andReturn().getResponse();
      MockHttpServletResponse liveness =
          mvc.perform(get("/actuator/health/liveness")).andReturn().getResponse();

      assertThat(readiness.getStatus()).isEqualTo(503);
      assertThat(readiness.getContentAsString()).contains("\"status\":\"DOWN\"");
      assertThat(liveness.getStatus()).isEqualTo(200);
    }

    @Test
    void conversionsAreRefusedAsNotReady() throws Exception {
      byte[] card =
          RestTestSupport.read(RestTestSupport.CH_EMED.resolve("medication-card-example.json"));

      MockHttpServletResponse response =
          mvc.perform(
                  post("/api/v1/conversions/{from}/{to}", "ch-emed", "chmed16a")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(RestTestSupport.requestMessage(card, null)))
              .andReturn()
              .getResponse();

      assertThat(response.getStatus()).isEqualTo(503);
      assertThat(RestTestSupport.json(response.getContentAsByteArray()).get("errorType").asText())
          .isEqualTo("NOT_READY");
    }
  }
}
