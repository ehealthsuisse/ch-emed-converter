package ch.emed.converter.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.emed.converter.core.api.Converter;
import ch.emed.converter.rest.config.ConverterHolder;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full application context with MockMvc. All subclasses share one cached context and thus one warm
 * converter (building the validation context is expensive).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractRestTest {

  @Autowired protected MockMvc mvc;

  @Autowired private ConverterHolder holder;

  /** The service's shared converter, used for library and CLI comparisons. */
  protected Converter converter() {
    return holder.converter().orElseThrow();
  }

  protected static MockHttpServletRequestBuilder convert(
      String from, String to, byte[] document, JsonNode options) {
    return post("/api/v1/conversions/{from}/{to}", from, to)
        .contentType(MediaType.APPLICATION_JSON)
        .content(RestTestSupport.requestMessage(document, options));
  }

  /**
   * Feature 004: runs CH EMED EPR in both directions through the service, also as treatment-plans
   * and with treatment-plan references (privacy and network tests, SC-007, SC-008).
   */
  protected void convertEpr() throws Exception {
    for (java.nio.file.Path input : RestTestSupport.corpus(RestTestSupport.EPR)) {
      mvc.perform(convert("ch-emed-epr", "ch-emed", RestTestSupport.read(input), null));
    }
    JsonNode references =
        RestTestSupport.json(
            "{\"treatmentPlanReferences\":[{\"documentId\":"
                + "\"urn:uuid:11111111-1111-4111-8111-111111111111\",\"statementId\":"
                + "\"urn:uuid:22222222-2222-4222-8222-222222222222\"}]}");
    JsonNode plans = RestTestSupport.json("{\"outputForm\":\"treatment-plans\"}");
    for (java.nio.file.Path input : RestTestSupport.corpus(RestTestSupport.CH_EMED)) {
      byte[] document = RestTestSupport.read(input);
      mvc.perform(convert("ch-emed", "ch-emed-epr", document, references));
      mvc.perform(convert("ch-emed", "ch-emed-epr", document, plans));
    }
  }
}
