package ch.emed.converter.chmed16a.mapping;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
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

  /**
   * Records that an option supplied data at {@code path} that the signature of a signed eRezept
   * does not cover (feature 003, research R7); no entry for unsigned input.
   */
  @MappingRule("M-URL-05")
  public void suppliedByOption(String path) {
    if (context.signature().isPresent()) {
      report()
          .add(
              ReportEntry.information(
                  ReasonCode.NOT_COVERED_BY_SIGNATURE,
                  Phase.MAP,
                  path,
                  "Supplied by a conversion option; not covered by the eRezept signature"));
    }
  }
}
