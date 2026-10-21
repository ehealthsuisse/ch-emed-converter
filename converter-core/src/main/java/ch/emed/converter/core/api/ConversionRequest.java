package ch.emed.converter.core.api;

import java.util.Objects;

/**
 * A conversion request (data-model.md §1).
 *
 * @param source source format
 * @param target target format
 * @param input UTF-8 input: JSON, or the textual CHMED16A QR payload when source is CHMED16A
 * @param options conversion options
 */
public record ConversionRequest(
    FormatId source, FormatId target, byte[] input, ConversionOptions options) {

  public ConversionRequest {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(options, "options");
  }
}
