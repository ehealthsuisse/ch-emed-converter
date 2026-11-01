package ch.emed.converter.rest.contract;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * T023: every failure type of data-model §6 that the running service can produce yields its
 * documented status, {@code errorType} and contract-conform problem; a report is present exactly
 * when a conversion was attempted (422, constitution v1.4.1). Busy, timeout, not ready and internal
 * error: {@link ServiceStateStatusContractTest}.
 */
class FailureStatusContractTest extends AbstractRestTest {

  private static final byte[] CARD =
      RestTestSupport.read(RestTestSupport.CH_EMED.resolve("medication-card-example.json"));

  @Test
  void messageNotJson() throws Exception {
    assertProblem(raw("ch-emed", "chmed16a", "not json"), 400, "MALFORMED_REQUEST");
  }

  @Test
  void documentMissing() throws Exception {
    assertProblem(raw("ch-emed", "chmed16a", "{\"options\":{}}"), 400, "MALFORMED_REQUEST");
  }

  @Test
  void documentEmpty() throws Exception {
    assertProblem(raw("ch-emed", "chmed16a", "{\"document\":\"\"}"), 400, "MALFORMED_REQUEST");
  }

  @Test
  void documentNotBase64() throws Exception {
    assertProblem(
        raw("ch-emed", "chmed16a", "{\"document\":\"not base64!\"}"), 400, "MALFORMED_REQUEST");
  }

  @Test
  void unknownProperty() throws Exception {
    JsonNode problem =
        assertProblem(
            convert("ch-emed", "chmed16a", CARD, RestTestSupport.json("{\"foo\":1}")),
            400,
            "MALFORMED_REQUEST");
    // the caller's property name is not echoed, only the known parent path
    assertThat(problem.get("detail").asText())
        .contains("options.(unknown property)")
        .doesNotContain("foo");
  }

  @Test
  void invalidOptionValues() throws Exception {
    String[][] cases = {
      {"{\"strict\":\"yes\"}", "options.strict"},
      {"{\"language\":\"de-DE\"}", "options.language"},
      {"{\"confidentiality\":\"top\"}", "options.confidentiality"},
      {"{\"patientIdentifier\":{\"system\":\"urn:oid:2.999.1\"}}", "options.patientIdentifier"},
    };
    for (String[] c : cases) {
      JsonNode problem =
          assertProblem(
              convert("ch-emed", "chmed16a", CARD, RestTestSupport.json(c[0])),
              400,
              "MALFORMED_REQUEST");
      assertThat(problem.get("detail").asText()).as(c[0]).contains(c[1]);
    }
  }

  @Test
  void unparseableDocumentIsInvalidInputWithParseReport() throws Exception {
    byte[] notJson = RestTestSupport.read(RestTestSupport.CHMED16A.resolve("invalid-json.txt"));

    JsonNode problem =
        assertProblem(
            convert("chmed16a", "ch-emed", notJson, RestTestSupport.withAuthor("")),
            422,
            "INVALID_INPUT");
    assertThat(entries(problem))
        .anySatisfy(e -> assertThat(e.get("phase").asText()).isEqualTo("PARSE"));
  }

  @Test
  void unsupportedPairListsSupportedConversions() throws Exception {
    JsonNode problem =
        assertProblem(convert("chmed16a", "chmed16a", CARD, null), 404, "UNSUPPORTED_CONVERSION");
    assertThat(problem.get("supportedConversions"))
        .anySatisfy(
            p -> {
              assertThat(p.get("from").asText()).isEqualTo("chmed16a");
              assertThat(p.get("to").asText()).isEqualTo("ch-emed");
            });
  }

  @Test
  void unknownOrInvalidFormatNameIsUnsupportedNot500() throws Exception {
    assertProblem(convert("foo", "ch-emed", CARD, null), 404, "UNSUPPORTED_CONVERSION");
    assertProblem(convert("ch_emed!", "chmed16a", CARD, null), 404, "UNSUPPORTED_CONVERSION");
  }

  @Test
  void strictLossIsConversionFailedWithoutOutput() throws Exception {
    byte[] plan =
        RestTestSupport.read(RestTestSupport.CHMED16A.resolve("plan-unmappable-element.json"));

    JsonNode problem =
        assertProblem(
            convert("chmed16a", "ch-emed", plan, RestTestSupport.withAuthor("\"strict\":true")),
            422,
            "CONVERSION_FAILED");
    assertThat(problem.has("output")).isFalse();
  }

  @Test
  void missingPatientIdentifierIsConversionFailed() throws Exception {
    byte[] plan = RestTestSupport.read(RestTestSupport.restFixture("plan-no-ids.json"));

    JsonNode problem =
        assertProblem(
            convert("chmed16a", "ch-emed", plan, RestTestSupport.withAuthor("")),
            422,
            "CONVERSION_FAILED");
    assertThat(entries(problem))
        .anySatisfy(e -> assertThat(e.get("code").asText()).isEqualTo("MISSING_REQUIRED_DATA"));
  }

  @Test
  void wrongContentType() throws Exception {
    for (MediaType type : new MediaType[] {MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN}) {
      assertProblem(
          post("/api/v1/conversions/{from}/{to}", "ch-emed", "chmed16a")
              .contentType(type)
              .content("<x/>"),
          415,
          "UNSUPPORTED_MEDIA_TYPE");
    }
  }

  private static MockHttpServletRequestBuilder raw(String from, String to, String body) {
    return post("/api/v1/conversions/{from}/{to}", from, to)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  /** Asserts status, errorType, contract and report presence; returns the problem. */
  private JsonNode assertProblem(MockHttpServletRequestBuilder request, int status, String type)
      throws Exception {
    MockHttpServletResponse response =
        mvc.perform(request)
            .andExpect(openApi().isValid(RestTestSupport.RESPONSE_CONTRACT))
            .andReturn()
            .getResponse();
    assertThat(response.getStatus()).isEqualTo(status);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    JsonNode problem = RestTestSupport.json(response.getContentAsByteArray());
    assertThat(problem.get("errorType").asText()).isEqualTo(type);
    assertThat(problem.get("status").asInt()).isEqualTo(status);
    assertThat(problem.get("correlationId").asText()).isNotBlank();
    assertThat(problem.has("report")).as("report present").isEqualTo(status == 422);
    return problem;
  }

  private static Iterable<JsonNode> entries(JsonNode problem) {
    return () ->
        StreamSupport.stream(problem.get("report").get("entries").spliterator(), false).iterator();
  }
}
