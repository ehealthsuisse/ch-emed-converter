package ch.emed.converter.rest.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * T026: problem responses never echo input content, option values or patient values and contain no
 * stack traces (US2 scenario 4, FR-008).
 */
class ProblemLeakTest extends AbstractRestTest {

  private static final byte[] CARD =
      RestTestSupport.read(RestTestSupport.CH_EMED.resolve("medication-card-example.json"));

  /** Values that must never come back in a problem. */
  private static final String[] SECRETS = {
    "Geheim-4711",
    "urn:oid:2.999.77",
    "Erika",
    "Beispiel",
    "7601000000001",
    "Wegm",
    "Monika",
    "Exception",
    "at ch.",
    "at org.",
    "tools.jackson",
    "com.fasterxml"
  };

  @Test
  void invalidOptionValuesAreNotEchoed() throws Exception {
    assertNoLeak(
        convert(
            "ch-emed",
            "chmed16a",
            CARD,
            RestTestSupport.json(
                "{\"patientIdentifier\":{\"system\":\"urn:oid:2.999.77\",\"value\":\" \"},"
                    + "\"language\":\"Geheim-4711\"}")));
    assertNoLeak(
        convert("ch-emed", "chmed16a", CARD, RestTestSupport.json("{\"strict\":\"Geheim-4711\"}")));
    assertNoLeak(
        convert(
            "ch-emed",
            "chmed16a",
            CARD,
            RestTestSupport.json(
                "{\"author\":{\"gln\":{\"x\":\"Geheim-4711\"},\"given\":\"Erika\"}}")));
  }

  @Test
  void malformedMessageWithPatientDataIsNotEchoed() throws Exception {
    assertNoLeak(raw("{\"document\":\"Geheim-4711 Monika Wegmüller\""));
    assertNoLeak(raw("{\"document\":42,\"Geheim-4711\":true}"));
  }

  @Test
  void failedConversionDetailContainsNoInput() throws Exception {
    // card fixture converted in the wrong direction: the report may name paths, the detail not data
    byte[] response =
        mvc.perform(convert("chmed16a", "ch-emed", CARD, RestTestSupport.withAuthor("")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    String detail = RestTestSupport.json(response).get("detail").asText();
    assertThat(detail).doesNotContain(SECRETS);
  }

  private void assertNoLeak(MockHttpServletRequestBuilder request) throws Exception {
    String body =
        mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(body).startsWith("{").doesNotContain(SECRETS);
  }

  private static MockHttpServletRequestBuilder raw(String body) {
    return post("/api/v1/conversions/{from}/{to}", "ch-emed", "chmed16a")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }
}
