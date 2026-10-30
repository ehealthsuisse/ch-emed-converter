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
}
