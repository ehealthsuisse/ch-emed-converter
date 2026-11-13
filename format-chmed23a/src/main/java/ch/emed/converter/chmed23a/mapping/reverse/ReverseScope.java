package ch.emed.converter.chmed23a.mapping.reverse;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.chemed.ChEmedDocumentReader.ChEmedDocument;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.MappingContext;

/**
 * State of one CH EMED → CHMED23A mapping: the opened document and the report. Paths are
 * FHIRPath-like expressions of the source document; messages never contain values.
 *
 * @param document the opened CH EMED document
 * @param context mapping context of the conversion
 */
public record ReverseScope(ChEmedDocument document, MappingContext context) {

  public ReportCollector report() {
    return context.report();
  }

  public ConversionOptions options() {
    return context.options();
  }

  void missing(String path, String message) {
    report().add(ReportEntry.error(ReasonCode.MISSING_REQUIRED_DATA, Phase.MAP, path, message));
  }

  void unmapped(String path, String message) {
    report().add(ReportEntry.information(ReasonCode.UNMAPPABLE_ELEMENT, Phase.MAP, path, message));
  }

  void unmappedWarning(String path, String message, boolean clinicalLoss) {
    report()
        .add(
            ReportEntry.warning(ReasonCode.UNMAPPABLE_ELEMENT, Phase.MAP, path, message)
                .withClinicalLoss(clinicalLoss));
  }

  void derived(String path, String message) {
    report().add(ReportEntry.information(ReasonCode.DERIVED_VALUE, Phase.MAP, path, message));
  }

  static boolean present(String value) {
    return value != null && !value.isBlank();
  }
}
