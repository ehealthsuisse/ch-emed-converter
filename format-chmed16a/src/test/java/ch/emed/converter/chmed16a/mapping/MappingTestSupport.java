package ch.emed.converter.chmed16a.mapping;

import ch.emed.converter.chmed16a.io.Chmed16aJsonReader;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.spi.MappingContext;
import java.nio.charset.StandardCharsets;

/** Helpers for mapper unit tests. */
final class MappingTestSupport {

  private MappingTestSupport() {}

  static Medication read(String fixture) {
    return parse(Fixtures.text("chmed16a/" + fixture));
  }

  static Medication parse(String json) {
    ReportCollector report = new ReportCollector();
    Medication med =
        Chmed16aJsonReader.read(
            json.getBytes(StandardCharsets.UTF_8), ConversionOptions.defaults(), report);
    if (med == null) {
      throw new IllegalArgumentException("Fixture not parseable: " + report.entries());
    }
    return med;
  }

  static MappingScope scope(Medication med, ConversionOptions options) {
    return new MappingScope(med.id(), new MappingContext(options, new ReportCollector()));
  }
}
