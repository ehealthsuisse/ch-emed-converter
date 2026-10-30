package ch.emed.converter.rest.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.rest.RestTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * T044: end-to-end smoke test against the packaged JAR over real HTTP - readiness, one conversion
 * in each direction, one failure, capabilities and the served contract.
 */
class PackagedJarSmokeIT {

  private final PackagedJar service = PackagedJar.get();
  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void convertsBothDirectionsAndReportsFailures() throws Exception {
    byte[] plan = RestTestSupport.read(RestTestSupport.CHMED16A.resolve("plan-basic.json"));
    HttpResponse<String> toChEmed =
        post("/api/v1/conversions/chmed16a/ch-emed", plan, RestTestSupport.withAuthor(""));
    assertThat(toChEmed.statusCode()).isEqualTo(200);
    byte[] card = output(toChEmed);
    assertThat(new String(card, StandardCharsets.UTF_8)).contains("\"resourceType\":\"Bundle\"");

    HttpResponse<String> back = post("/api/v1/conversions/ch-emed/chmed16a", card, null);
    assertThat(back.statusCode()).isEqualTo(200);
    assertThat(new String(output(back), StandardCharsets.UTF_8)).contains("\"Medicaments\"");

    HttpResponse<String> failure =
        post(
            "/api/v1/conversions/chmed16a/ch-emed",
            RestTestSupport.read(RestTestSupport.CHMED16A.resolve("invalid-json.txt")),
            RestTestSupport.withAuthor(""));
    assertThat(failure.statusCode()).isEqualTo(422);
    assertThat(failure.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    assertThat(RestTestSupport.json(failure.body()).get("errorType").asText())
        .isEqualTo("INVALID_INPUT");
  }

  @Test
  void publishesCapabilitiesAndContract() throws Exception {
    HttpResponse<String> capabilities = get("/api/v1/capabilities");
    assertThat(capabilities.statusCode()).isEqualTo(200);
    assertThat(capabilities.body()).contains("\"chmed16a\"", "ch.fhir.ig.ch-emed#6.0.0");

    HttpResponse<byte[]> contract =
        http.send(
            HttpRequest.newBuilder(service.uri("/api/v1/openapi.yaml")).build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(contract.body())
        .isEqualTo(RestTestSupport.read(java.nio.file.Path.of(RestTestSupport.OPENAPI)));
  }

  private HttpResponse<String> post(String path, byte[] document, JsonNode options)
      throws Exception {
    return http.send(
        HttpRequest.newBuilder(service.uri(path))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    RestTestSupport.requestMessage(document, options)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(service.uri(path)).build(), HttpResponse.BodyHandlers.ofString());
  }

  private static byte[] output(HttpResponse<String> response) {
    return Base64.getDecoder().decode(RestTestSupport.json(response.body()).get("output").asText());
  }
}
