package ch.emed.converter.chmed16a.mapping.reverse;

import ch.emed.converter.chmed16a.io.ChEmedDocumentReader;
import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.ChEmedDocument;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.MappingContext;
import java.util.List;
import java.util.function.Predicate;

/** Helpers for reverse mapper unit tests. */
final class ReverseTestSupport {

  static final String CARD = "chemed/medication-card-example.json";

  private ReverseTestSupport() {}

  /** Opens a CH EMED fixture without validation (mapper tests focus on the mapping). */
  static ChEmedDocument document(String fixture) {
    ChEmedDocument doc =
        ChEmedDocumentReader.open(ChEmedJson.parse(Fixtures.bytes(fixture)), new ReportCollector());
    if (doc == null) {
      throw new IllegalArgumentException("Fixture not a supported document: " + fixture);
    }
    return doc;
  }

  static ReverseScope scope(ChEmedDocument doc) {
    return new ReverseScope(
        doc, new MappingContext(ConversionOptions.defaults(), new ReportCollector()));
  }

  static List<ReportEntry> entries(ReverseScope scope, Predicate<ReportEntry> filter) {
    return scope.report().entries().stream().filter(filter).toList();
  }
}
