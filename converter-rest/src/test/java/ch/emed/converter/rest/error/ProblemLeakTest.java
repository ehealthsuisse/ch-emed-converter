package ch.emed.converter.rest.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

  @Test
  void failedQrAndErezeptInputsEchoNoContent() throws Exception {
    // feature 003, FR-016: neither the URL, its payload nor the signature values come back
    // (the fixed host name eprescription.hin.ch may appear in messages; it is no data)
    String[] content = {"CHMED16A1H4sI", "emust1", "1767225600", "3066023100"};
    for (String fixture :
        List.of(
            "erezept/rx-missing-s.url.txt",
            "erezept/rx-duplicate-t.url.txt",
            "erezept/rx-foreign-host.url.txt",
            "erezept/plan-signed.url.txt",
            "qr/two-codes.png",
            "qr/url-code.png")) {
      String body =
          mvc.perform(
                  convert(
                      "chmed16a",
                      "ch-emed",
                      RestTestSupport.read(RestTestSupport.FIXTURES.resolve(fixture)),
                      RestTestSupport.withAuthor("")))
              .andReturn()
              .getResponse()
              .getContentAsString(StandardCharsets.UTF_8);
      assertThat(body).as(fixture).contains("\"errorType\"").doesNotContain(SECRETS);
      assertThat(body).as(fixture).doesNotContain(content);
    }
  }

  @Test
  void failedEprConversionsEchoNoContent() throws Exception {
    // feature 004: refused document types, invalid input, missing data, wrong references
    String[] patient = {"Karce", "1973-12-25", "Michaël", "secret-doc"};
    List<MockHttpServletRequestBuilder> requests =
        List.of(
            convert(
                "ch-emed-epr",
                "ch-emed",
                RestTestSupport.read(
                    RestTestSupport.EPR_SYNTHETIC.resolve("Bundle-UnknownDocumentType.json")),
                null),
            convert("ch-emed-epr", "ch-emed", CARD, null),
            convert("ch-emed", "ch-emed-epr", CARD, null),
            convert(
                "ch-emed",
                "ch-emed-epr",
                CARD,
                RestTestSupport.json(
                    "{\"treatmentPlanReferences\":[{\"documentId\":\"secret-doc\","
                        + "\"statementId\":\"secret-doc\"}]}")),
            convert(
                "chmed16a",
                "ch-emed-epr",
                RestTestSupport.read(
                    RestTestSupport.EPR.resolve(
                        "Bundle-DocumentPmlcCARAPMP004AxapharmParacetamol.json")),
                null));
    for (MockHttpServletRequestBuilder request : requests) {
      String body =
          mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
      assertThat(body).contains("\"errorType\"").doesNotContain(SECRETS).doesNotContain(patient);
    }
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
