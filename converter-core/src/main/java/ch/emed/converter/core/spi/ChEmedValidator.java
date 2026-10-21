package ch.emed.converter.core.spi;

import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReportEntry;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;

/**
 * Validates CH EMED documents against the pinned IG packages (constitution I). Implementations MUST
 * work offline and MUST be thread-safe.
 */
public interface ChEmedValidator {

  /**
   * Validates a document.
   *
   * @param bundle the document
   * @param phase {@link Phase#VALIDATE_INPUT} or {@link Phase#VALIDATE_OUTPUT}
   * @return entries with code {@code FHIR_VALIDATION}, without patient values
   */
  List<ReportEntry> validate(Bundle bundle, Phase phase);

  /**
   * Pinned root IG package, e.g. {@code ch.fhir.ig.ch-emed#6.0.0}.
   *
   * @return pinned specification
   */
  String pinnedSpecification();
}
