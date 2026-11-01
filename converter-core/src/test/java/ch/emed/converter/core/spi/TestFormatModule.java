package ch.emed.converter.core.spi;

import ch.emed.converter.core.api.FormatId;
import org.hl7.fhir.r4.model.Bundle;

/** Format module registered via test META-INF/services for {@link FormatRegistryTest}. */
public class TestFormatModule implements FormatModule {

  static final FormatId FORMAT = FormatId.of("TEST_FORMAT");

  @Override
  public FormatId format() {
    return FORMAT;
  }

  @Override
  public String pinnedSpecification() {
    return "test#1";
  }

  @Override
  public Bundle toChEmed(byte[] input, MappingContext ctx) {
    return null;
  }

  @Override
  public byte[] fromChEmed(Bundle document, MappingContext ctx) {
    return new byte[0];
  }
}
