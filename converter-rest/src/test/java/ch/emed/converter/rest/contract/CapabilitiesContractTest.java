package ch.emed.converter.rest.contract;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.emed.converter.core.api.ConversionPair;
import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import ch.emed.converter.rest.config.ConverterRestProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T031: {@code GET /api/v1/capabilities} lists exactly what the running service offers: pairs and
 * pinned specifications from the library, the CLI option set and the configured limits (FR-010).
 */
class CapabilitiesContractTest extends AbstractRestTest {

  @Autowired private ConverterRestProperties properties;

  @Test
  void capabilitiesReflectLibraryOptionsAndConfiguration() throws Exception {
    byte[] response =
        mvc.perform(get("/api/v1/capabilities"))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(RestTestSupport.OPENAPI))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    JsonNode body = RestTestSupport.json(response);

    assertThat(body.get("converterVersion").asText()).isEqualTo(converter().version());

    List<String> pairs =
        stream(body.get("conversions"))
            .map(p -> p.get("from").asText() + "->" + p.get("to").asText())
            .toList();
    assertThat(pairs)
        .containsExactlyInAnyOrderElementsOf(
            converter().supportedConversions().stream()
                .map(CapabilitiesContractTest::external)
                .toList())
        .contains("chmed16a->ch-emed", "ch-emed->chmed16a");

    assertThat(stream(body.get("options")).map(o -> o.get("name").asText()))
        .containsExactly("strict", "language", "confidentiality", "patientIdentifier", "author");
    JsonNode strict = stream(body.get("options")).findFirst().orElseThrow();
    assertThat(strict.get("type").asText()).isEqualTo("boolean");
    assertThat(strict.get("default").asBoolean()).isFalse();
    assertThat(option(body, "language").get("default").asText()).isEqualTo("de-CH");
    assertThat(option(body, "confidentiality").get("default").asText()).isEqualTo("normal");

    assertThat(body.get("specifications").get("CH_EMED").asText())
        .isEqualTo("ch.fhir.ig.ch-emed#6.0.0");
    assertThat(RestTestSupport.JSON.convertValue(body.get("specifications"), java.util.Map.class))
        .isEqualTo(converter().pinnedSpecifications());

    JsonNode limits = body.get("limits");
    assertThat(limits.get("maxRequestSizeBytes").asLong())
        .isEqualTo(properties.maxRequestSize().toBytes());
    assertThat(limits.get("maxDocumentSizeBytes").asLong())
        .isEqualTo(properties.maxDocumentSize().toBytes());
    assertThat(limits.get("timeoutSeconds").asLong()).isEqualTo(properties.timeout().toSeconds());
  }

  private static JsonNode option(JsonNode body, String name) {
    return stream(body.get("options"))
        .filter(o -> o.get("name").asText().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static String external(ConversionPair pair) {
    return pair.source().externalName() + "->" + pair.target().externalName();
  }

  private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false);
  }
}
