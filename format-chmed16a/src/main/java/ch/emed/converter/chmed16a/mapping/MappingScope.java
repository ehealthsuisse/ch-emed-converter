package ch.emed.converter.chmed16a.mapping;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.spi.MappingContext;

/**
 * Mapping state of one CHMED16A document: deterministic ids derived from the CHMED {@code Id}
 * (research R8) and the report.
 *
 * @param documentId CHMED {@code Id}
 * @param context mapping context of the conversion
 */
public record MappingScope(String documentId, MappingContext context) {

  /** Deterministic id for the element at {@code path}. */
  public String id(String path) {
    return context.options().idStrategy().id(documentId, path);
  }

  /** {@code urn:uuid:} reference for the element at {@code path}. */
  public String ref(String path) {
    return "urn:uuid:" + id(path);
  }

  public ReportCollector report() {
    return context.report();
  }

  public ConversionOptions options() {
    return context.options();
  }
}
