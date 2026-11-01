package ch.emed.converter.rest.parity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * T043: umlauts, accents and typographic characters survive the base64 transport unchanged in both
 * directions (spec edge case "Non-ASCII content").
 */
class NonAsciiPreservationTest extends AbstractRestTest {

  private static final String[] NON_ASCII = {"Zoë", "Müller-Rüegg", "Hôpital", "–"};

  @Test
  void nonAsciiSurvivesBothDirections() throws Exception {
    byte[] plan = RestTestSupport.read(RestTestSupport.CHMED16A.resolve("plan-non-ascii.json"));

    byte[] chEmed = output(convertOk("chmed16a", "ch-emed", plan));
    byte[] library =
        converter()
            .convert(
                new ConversionRequest(
                    FormatId.CHMED16A,
                    FormatId.CH_EMED,
                    plan,
                    ConversionOptions.defaults().withAuthor(RestTestSupport.AUTHOR)))
            .output()
            .orElseThrow();
    assertThat(chEmed).isEqualTo(library);
    assertThat(new String(chEmed, StandardCharsets.UTF_8)).contains(NON_ASCII);

    byte[] back = output(convertOk("ch-emed", "chmed16a", chEmed));
    assertThat(new String(back, StandardCharsets.UTF_8)).contains("Zoë", "Müller-Rüegg");
  }

  private MockHttpServletResponse convertOk(String from, String to, byte[] document)
      throws Exception {
    MockHttpServletResponse response =
        mvc.perform(
                convert(
                    from,
                    to,
                    document,
                    from.equals("chmed16a") ? RestTestSupport.withAuthor("") : null))
            .andReturn()
            .getResponse();
    assertThat(response.getStatus()).isEqualTo(200);
    return response;
  }

  private static byte[] output(MockHttpServletResponse response) throws Exception {
    return Base64.getDecoder()
        .decode(RestTestSupport.json(response.getContentAsByteArray()).get("output").asText());
  }
}
