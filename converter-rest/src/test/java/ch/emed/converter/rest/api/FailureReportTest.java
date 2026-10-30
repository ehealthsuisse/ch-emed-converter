package ch.emed.converter.rest.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.report.ReportJsonWriter;
import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * T024: failure reports equal the library report; wrong source format and eRezept URLs are invalid
 * input, and eRezept signature data never appears in response or log (constitution IV).
 */
@ExtendWith(OutputCaptureExtension.class)
class FailureReportTest extends AbstractRestTest {

  @Test
  void failureReportEqualsLibraryReport() throws Exception {
    Path input = RestTestSupport.CHMED16A.resolve("plan-unmappable-element.json");
    byte[] document = RestTestSupport.read(input);
    ConversionOptions strict =
        ConversionOptions.defaults().withAuthor(RestTestSupport.AUTHOR).withStrict(true);

    JsonNode problem =
        problem(document, RestTestSupport.withAuthor("\"strict\":true"), "CONVERSION_FAILED");

    byte[] library =
        ReportJsonWriter.write(
            converter()
                .convert(
                    new ConversionRequest(FormatId.CHMED16A, FormatId.CH_EMED, document, strict))
                .report());
    assertThat(problem.get("report")).isEqualTo(RestTestSupport.json(library));
  }

  @Test
  void chmed23aSentAsChmed16aIsInvalidInputNamingTheDetectedFormat() throws Exception {
    byte[] plan = RestTestSupport.read(RestTestSupport.CHMED16A.resolve("chmed23a-plan.json"));

    JsonNode problem = problem(plan, RestTestSupport.withAuthor(""), "INVALID_INPUT");

    assertThat(StreamSupport.stream(problem.get("report").get("entries").spliterator(), false))
        .anySatisfy(e -> assertThat(e.get("message").asText()).contains("CHMED23A"));
  }

  @Test
  void erezeptUrlIsInvalidInputAndSignatureDataIsNeverLogged(CapturedOutput output)
      throws Exception {
    byte[] url = RestTestSupport.read(RestTestSupport.CHMED16A.resolve("erezept-url.txt"));

    JsonNode problem = problem(url, RestTestSupport.withAuthor(""), "INVALID_INPUT");

    // identity (i), timestamp (t) and signature (s) parameters of the synthetic URL
    String[] signatureData = {"TestArzt", "20261001T093000", "c2lnbmF0dXJlLXRlc3QtMDAwMDAw"};
    assertThat(problem.toString()).doesNotContain(signatureData);
    assertThat(output.getAll()).doesNotContain(signatureData).doesNotContain("eprescription");
  }

  private JsonNode problem(byte[] document, JsonNode options, String errorType) throws Exception {
    MockHttpServletResponse response =
        mvc.perform(convert("chmed16a", "ch-emed", document, options)).andReturn().getResponse();
    assertThat(response.getStatus()).isEqualTo(422);
    JsonNode problem = RestTestSupport.json(response.getContentAsByteArray());
    assertThat(problem.get("errorType").asText()).isEqualTo(errorType);
    return problem;
  }
}
