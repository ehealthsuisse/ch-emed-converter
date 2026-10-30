package ch.emed.converter.rest.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * Successful conversion (data-model §4).
 *
 * @param outcome {@code SUCCESS} or {@code SUCCESS_WITH_WARNINGS}
 * @param correlationId same as the {@code X-Correlation-Id} response header
 * @param report the library's report JSON, embedded unchanged
 * @param output the library's output bytes, base64-encoded
 * @param outputMediaType media type of the decoded output document
 */
@JsonPropertyOrder({"outcome", "correlationId", "report", "output", "outputMediaType"})
public record ConversionResponse(
    String outcome,
    String correlationId,
    @JsonRawValue String report,
    String output,
    String outputMediaType) {}
