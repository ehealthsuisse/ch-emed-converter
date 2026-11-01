package ch.emed.converter.core.api;

import ch.emed.converter.core.report.ConversionReport;
import ch.emed.converter.core.report.Outcome;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of a conversion. {@code output} is present iff {@code outcome != FAILED} (FR-015).
 *
 * @param outcome conversion outcome
 * @param output output document (UTF-8 JSON)
 * @param report conversion report
 */
public record ConversionResult(Outcome outcome, Optional<byte[]> output, ConversionReport report) {

  public ConversionResult {
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(report, "report");
    if ((outcome == Outcome.FAILED) == output.isPresent()) {
      throw new IllegalArgumentException("output must be present iff outcome is not FAILED");
    }
  }
}
