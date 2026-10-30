package ch.emed.converter.rest.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.emed.converter.core.api.ConversionPair;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.rest.RestTestSupport;
import ch.emed.converter.rest.config.ConverterHolder;
import ch.emed.converter.rest.execution.BoundedConversionExecutor;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T038 (FR-009): an internal error can be traced through the correlation id, which appears in the
 * response and in the log; neither contains a stack trace or input data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private ConverterHolder holder;

  @MockitoBean private BoundedConversionExecutor executor;

  @Test
  void internalErrorIsTraceableWithoutInternals(CapturedOutput output) throws Exception {
    Converter converter = mock(Converter.class);
    when(converter.supportedConversions())
        .thenReturn(Set.of(new ConversionPair(FormatId.CH_EMED, FormatId.CHMED16A)));
    when(holder.converter()).thenReturn(Optional.of(converter));
    when(executor.execute(any()))
        .thenThrow(new IllegalStateException("Patient Hans Muster, born 1950-03-21"));
    byte[] card =
        RestTestSupport.read(RestTestSupport.CH_EMED.resolve("medication-card-example.json"));

    MockHttpServletResponse response =
        mvc.perform(
                post("/api/v1/conversions/{from}/{to}", "ch-emed", "chmed16a")
                    .header("X-Correlation-Id", "trace-4711")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(RestTestSupport.requestMessage(card, null)))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("trace-4711");
    assertThat(RestTestSupport.json(response.getContentAsByteArray()).get("correlationId").asText())
        .isEqualTo("trace-4711");

    String log = output.getAll();
    assertThat(log)
        .contains("Internal error correlationId=trace-4711")
        .contains("correlationId=trace-4711 method=POST")
        .contains("status=500 errorType=INTERNAL_ERROR")
        .doesNotContain("Muster", "1950-03-21", "\tat ", "Monika");
  }
}
