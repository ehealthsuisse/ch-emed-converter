package ch.emed.converter.rest.contract;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.emed.converter.core.api.ConversionPair;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.report.ConversionReport;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.rest.RestTestSupport;
import ch.emed.converter.rest.config.ConverterHolder;
import ch.emed.converter.rest.execution.BoundedConversionExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T023 (continued): failures that depend on the service state - busy (503 + Retry-After), not ready
 * (503), timeout (504) and unexpected errors (500, no internals). Converter and executor are
 * replaced, so this context builds no validation context.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceStateStatusContractTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private ConverterHolder holder;

  @MockitoBean private BoundedConversionExecutor executor;

  @BeforeEach
  void readyConverter() {
    Converter converter = mock(Converter.class);
    when(converter.supportedConversions())
        .thenReturn(Set.of(new ConversionPair(FormatId.CH_EMED, FormatId.CHMED16A)));
    when(holder.converter()).thenReturn(Optional.of(converter));
    when(holder.state()).thenReturn(ConverterHolder.State.READY);
  }

  @Test
  void notReady() throws Exception {
    when(holder.converter()).thenReturn(Optional.empty());
    when(holder.state()).thenReturn(ConverterHolder.State.STARTING);

    MockHttpServletResponse response = assertProblem(503, "NOT_READY");
    assertThat(response.getHeader("Retry-After")).isNotBlank();
  }

  @Test
  void capabilitiesNotReady() throws Exception {
    when(holder.converter()).thenReturn(Optional.empty());

    MockHttpServletResponse response =
        mvc.perform(get("/api/v1/capabilities"))
            .andExpect(openApi().isValid(RestTestSupport.RESPONSE_CONTRACT))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(RestTestSupport.json(response.getContentAsByteArray()).get("errorType").asText())
        .isEqualTo("NOT_READY");
    assertThat(response.getHeader("Retry-After")).isNotBlank();
  }

  @Test
  void busy() throws Exception {
    when(executor.execute(any())).thenThrow(new BoundedConversionExecutor.BusyException());

    MockHttpServletResponse response = assertProblem(503, "SERVICE_BUSY");
    assertThat(response.getHeader("Retry-After")).isNotBlank();
  }

  @Test
  void timeout() throws Exception {
    when(executor.execute(any()))
        .thenThrow(new BoundedConversionExecutor.TimeoutExceededException());

    assertProblem(504, "CONVERSION_TIMEOUT");
  }

  @Test
  void unexpectedErrorRevealsNoInternals() throws Exception {
    when(executor.execute(any()))
        .thenThrow(new IllegalStateException("Hans Muster 1950-03-21 secret detail"));

    MockHttpServletResponse response = assertProblem(500, "INTERNAL_ERROR");
    String body = response.getContentAsString();
    assertThat(body)
        .doesNotContain("Muster", "1950-03-21", "secret", "IllegalStateException", "at ch.");
  }

  /**
   * The validation hit the HL7 validator's time limit under load ({@code VALIDATION_INCOMPLETE}):
   * not an invalid document, so 503 SERVICE_BUSY with Retry-After - and, as a conversion was
   * attempted, with the report (constitution v1.4.1).
   */
  @Test
  void incompleteValidationIsBusyWithReport() throws Exception {
    Converter converter = holder.converter().orElseThrow();
    when(converter.convert(any()))
        .thenAnswer(
            invocation -> {
              ConversionRequest request = invocation.getArgument(0);
              ReportEntry entry =
                  ReportEntry.of(
                      Severity.ERROR,
                      ReasonCode.VALIDATION_INCOMPLETE,
                      Phase.VALIDATE_OUTPUT,
                      "Bundle",
                      "Validation could not be completed under the current system load.");
              return new ConversionResult(
                  Outcome.FAILED,
                  Optional.empty(),
                  ConversionReport.of(
                      "test",
                      request.source(),
                      request.target(),
                      Map.of(),
                      Outcome.FAILED,
                      List.of(entry)));
            });
    when(executor.execute(any()))
        .thenAnswer(invocation -> invocation.<Callable<ConversionResult>>getArgument(0).call());

    MockHttpServletResponse response =
        mvc.perform(
                post("/api/v1/conversions/{from}/{to}", "ch-emed", "chmed16a")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(RestTestSupport.requestMessage("{}".getBytes(), null)))
            .andExpect(openApi().isValid(RestTestSupport.RESPONSE_CONTRACT))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getHeader("Retry-After")).isNotBlank();
    JsonNode problem = RestTestSupport.json(response.getContentAsByteArray());
    assertThat(problem.get("errorType").asText()).isEqualTo("SERVICE_BUSY");
    assertThat(problem.get("report").get("entries").get(0).get("code").asText())
        .isEqualTo(ReasonCode.VALIDATION_INCOMPLETE);
  }

  private MockHttpServletResponse assertProblem(int status, String errorType) throws Exception {
    MockHttpServletResponse response =
        mvc.perform(
                post("/api/v1/conversions/{from}/{to}", "ch-emed", "chmed16a")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(RestTestSupport.requestMessage("{}".getBytes(), null)))
            .andExpect(openApi().isValid(RestTestSupport.RESPONSE_CONTRACT))
            .andReturn()
            .getResponse();
    assertThat(response.getStatus()).isEqualTo(status);
    JsonNode problem = RestTestSupport.json(response.getContentAsByteArray());
    assertThat(problem.get("errorType").asText()).isEqualTo(errorType);
    assertThat(problem.has("report")).isFalse();
    return response;
  }
}
