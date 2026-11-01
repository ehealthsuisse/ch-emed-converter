package ch.emed.converter.rest.model;

import ch.emed.converter.core.api.ConversionPair;

/** Supported source/target pair with external format names (contract {@code ConversionPair}). */
public record ConversionPairDto(String from, String to) {

  public static ConversionPairDto of(ConversionPair pair) {
    return new ConversionPairDto(pair.source().externalName(), pair.target().externalName());
  }
}
