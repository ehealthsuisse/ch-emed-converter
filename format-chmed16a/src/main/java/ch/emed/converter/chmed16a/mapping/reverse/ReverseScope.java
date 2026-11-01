package ch.emed.converter.chmed16a.mapping.reverse;

import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.ChEmedDocument;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.MappingContext;

/**
 * State of one CH EMED → CHMED16A mapping: the opened document and the report. Paths in report
 * entries are FHIRPath-like expressions of the source document ({@code Patient.birthDate}, {@code
 * MedicationStatement[2].dosage[0]}); messages never contain patient values.
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

  /** Data that eRezept Schweiz requires for a prescription is missing (FR-008b). */
  void erezept(String path, String message) {
    report()
        .add(
            ReportEntry.error(
                ReasonCode.EREZEPT_REQUIREMENT, Phase.MAP, path, "eRezept Schweiz: " + message));
  }

  /** Element without CHMED16A counterpart that is not carried over; no clinical loss. */
  void unmapped(String path, String message) {
    report().add(ReportEntry.information(ReasonCode.UNMAPPABLE_ELEMENT, Phase.MAP, path, message));
  }

  /** Element that is not carried over and worth a warning; the caller states the clinical loss. */
  void unmappedWarning(String path, String message, boolean clinicalLoss) {
    report()
        .add(
            ReportEntry.warning(ReasonCode.UNMAPPABLE_ELEMENT, Phase.MAP, path, message)
                .withClinicalLoss(clinicalLoss));
  }

  void derived(String path, String message) {
    report().add(ReportEntry.information(ReasonCode.DERIVED_VALUE, Phase.MAP, path, message));
  }
}
