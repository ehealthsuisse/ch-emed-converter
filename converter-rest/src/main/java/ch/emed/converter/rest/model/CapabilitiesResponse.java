package ch.emed.converter.rest.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;

/**
 * Capabilities of the running service (data-model §7, FR-010).
 *
 * @param converterVersion version of the converter library
 * @param conversions supported source/target pairs
 * @param options conversion options of a request
 * @param specifications pinned specification per format
 * @param limits operator limits
 */
@JsonPropertyOrder({"converterVersion", "conversions", "options", "specifications", "limits"})
public record CapabilitiesResponse(
    String converterVersion,
    List<ConversionPairDto> conversions,
    List<Option> options,
    Map<String, String> specifications,
    Limits limits) {

  /**
   * One conversion option.
   *
   * @param defaultValue library default; null if the option has none
   */
  @JsonPropertyOrder({"name", "type", "default", "description"})
  public record Option(
      String name,
      String type,
      @JsonProperty("default") @JsonInclude(JsonInclude.Include.ALWAYS) Object defaultValue,
      String description) {}

  /** Operator limits, {@code converter.rest.*}. */
  public record Limits(long maxRequestSizeBytes, long maxDocumentSizeBytes, long timeoutSeconds) {}
}
