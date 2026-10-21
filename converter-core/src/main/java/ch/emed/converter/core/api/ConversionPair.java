package ch.emed.converter.core.api;

import java.util.Objects;

/** A supported source → target conversion. */
public record ConversionPair(FormatId source, FormatId target) {

  public ConversionPair {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(target, "target");
  }
}
