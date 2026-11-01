package ch.emed.converter.rest.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.emed.converter.rest.RestTestSupport;
import ch.emed.converter.rest.client.ApiClient;
import ch.emed.converter.rest.client.ApiException;
import ch.emed.converter.rest.client.api.DefaultApi;
import ch.emed.converter.rest.client.model.AuthorDetails;
import ch.emed.converter.rest.client.model.AuthorDetailsOrganization;
import ch.emed.converter.rest.client.model.CapabilitiesResponse;
import ch.emed.converter.rest.client.model.ConversionOptions;
import ch.emed.converter.rest.client.model.ConversionRequest;
import ch.emed.converter.rest.client.model.ConversionResponse;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * T045 (SC-005): a Java client generated from {@code contracts/openapi.yaml} by openapi-generator
 * (target/generated-test-sources) works against the packaged service without any hand-written HTTP
 * code.
 */
class GeneratedClientIT {

  private final DefaultApi api = new DefaultApi(client());

  private static ApiClient client() {
    ApiClient client = new ApiClient();
    client.updateBaseUri(PackagedJar.get().uri("/api/v1").toString());
    return client;
  }

  @Test
  void firstConversionWithGeneratedClient() throws Exception {
    byte[] plan = RestTestSupport.read(RestTestSupport.CHMED16A.resolve("plan-basic.json"));
    ConversionRequest request =
        new ConversionRequest()
            .document(Base64.getEncoder().encodeToString(plan))
            .options(
                new ConversionOptions()
                    .author(
                        new AuthorDetails()
                            .gln("7601000000001")
                            .given("Erika")
                            .family("Beispiel")
                            .organization(
                                new AuthorDetailsOrganization()
                                    .name("Praxis Beispiel")
                                    .street("Musterweg 2")
                                    .zip("3000")
                                    .city("Bern")
                                    .country("CH"))));

    ConversionResponse response = api.convert("chmed16a", "ch-emed", request, "client-it-1");

    assertThat(response.getOutcome()).isNotNull();
    assertThat(response.getCorrelationId()).isEqualTo("client-it-1");
    assertThat(response.getOutputMediaType()).isEqualTo("application/fhir+json");
    assertThat(new String(Base64.getDecoder().decode(response.getOutput())))
        .contains("\"resourceType\":\"Bundle\"");
  }

  @Test
  void capabilitiesAndFailuresWithGeneratedClient() throws Exception {
    CapabilitiesResponse capabilities = api.capabilities();
    assertThat(capabilities.getConversions())
        .anySatisfy(p -> assertThat(p.getFrom() + "->" + p.getTo()).isEqualTo("ch-emed->chmed16a"));

    ConversionRequest invalid =
        new ConversionRequest().document(Base64.getEncoder().encodeToString("{".getBytes()));
    assertThatThrownBy(() -> api.convert("ch-emed", "chmed16a", invalid, null))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo(422);
              assertThat(e.getResponseBody()).contains("INVALID_INPUT");
            });
  }
}
