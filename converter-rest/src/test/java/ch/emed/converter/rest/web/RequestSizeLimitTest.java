package ch.emed.converter.rest.web;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import ch.emed.converter.rest.config.ConverterRestProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

/**
 * T025: size limits (FR-017, research R6). The message limit applies before the body is read, the
 * document limit after base64 decoding; the library's own limit for decompressed QR payloads yields
 * invalid input.
 */
@SpringBootTest(
    properties = {"converter.rest.max-document-size=16KB", "converter.rest.max-request-size=24KB"})
class RequestSizeLimitTest extends AbstractRestTest {

  @Test
  void messageLargerThanMaxRequestSize() throws Exception {
    assertTooLarge(filled(20 * 1024)); // base64 ~27 KB > 24 KB
  }

  @Test
  void decodedDocumentLargerThanMaxDocumentSize() throws Exception {
    assertTooLarge(filled(17 * 1024)); // base64 ~23 KB < 24 KB, decoded 17 KB > 16 KB
  }

  @Test
  void oversizedQrPayloadIsInvalidInputFromTheLibrary() throws Exception {
    byte[] qr = RestTestSupport.read(RestTestSupport.CHMED16A.resolve("qr-oversized.txt"));

    MockHttpServletResponse response =
        mvc.perform(convert("chmed16a", "ch-emed", qr, RestTestSupport.withAuthor("")))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(422);
    assertThat(RestTestSupport.json(response.getContentAsByteArray()).get("errorType").asText())
        .isEqualTo("INVALID_INPUT");
  }

  /** Without Content-Length (chunked), the counting stream enforces the limit while reading. */
  @Test
  void bodyWithoutContentLengthIsCountedWhileReading() throws Exception {
    RequestSizeLimitFilter filter =
        new RequestSizeLimitFilter(
            new ConverterRestProperties(
                DataSize.ofKilobytes(1), DataSize.ofKilobytes(2), Duration.ofSeconds(10), 1, 0),
            null);
    MockHttpServletRequest chunked =
        new MockHttpServletRequest("POST", "/api/v1/conversions/chmed16a/ch-emed") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }

          @Override
          public int getContentLength() {
            return -1;
          }
        };
    chunked.setContent(new byte[4096]);

    assertThatThrownBy(
            () ->
                filter.doFilter(
                    chunked,
                    new MockHttpServletResponse(),
                    (req, res) -> ((HttpServletRequest) req).getInputStream().readAllBytes()))
        .isInstanceOf(RequestSizeLimitFilter.RequestTooLargeException.class);
  }

  private void assertTooLarge(byte[] document) throws Exception {
    MockHttpServletResponse response =
        mvc.perform(convert("ch-emed", "chmed16a", document, null))
            .andExpect(openApi().isValid(RestTestSupport.RESPONSE_CONTRACT))
            .andReturn()
            .getResponse();
    assertThat(response.getStatus()).isEqualTo(413);
    JsonNode problem = RestTestSupport.json(response.getContentAsByteArray());
    assertThat(problem.get("errorType").asText()).isEqualTo("REQUEST_TOO_LARGE");
    assertThat(problem.has("report")).isFalse();
  }

  private static byte[] filled(int size) {
    byte[] bytes = new byte[size];
    Arrays.fill(bytes, (byte) 'x');
    return bytes;
  }
}
