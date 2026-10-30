package ch.emed.converter.rest.error;

import ch.emed.converter.rest.model.ConversionPairDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.util.List;

/**
 * RFC 9457 problem details with the extensions of data-model §5. {@code report} is the library's
 * report JSON, embedded unchanged.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "type",
  "title",
  "status",
  "detail",
  "errorType",
  "correlationId",
  "report",
  "supportedConversions"
})
public record Problem(
    String type,
    String title,
    int status,
    String detail,
    ErrorType errorType,
    String correlationId,
    @JsonRawValue String report,
    List<ConversionPairDto> supportedConversions) {}
