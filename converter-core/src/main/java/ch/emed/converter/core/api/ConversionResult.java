package ch.emed.converter.core.api;

import ch.emed.converter.core.report.ConversionReport;
import ch.emed.converter.core.report.Outcome;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of a conversion. {@code output} is present iff {@code outcome != FAILED} (FR-015); {@code
 * signature} only if {@code output} is present (feature 003).
 *
 * @param outcome conversion outcome
 * @param output output in the requested output form (UTF-8 JSON, ASCII payload or PNG)
 * @param report conversion report
 * @param signature signature data of a signed input, raw and not verified
 */
public record ConversionResult(
    Outcome outcome,
    Optional<byte[]> output,
    ConversionReport report,
    Optional<SignatureData> signature) {

  public ConversionResult {
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(report, "report");
    Objects.requireNonNull(signature, "signature");
    if ((outcome == Outcome.FAILED) == output.isPresent()) {
      throw new IllegalArgumentException("output must be present iff outcome is not FAILED");
    }
    if (signature.isPresent() && output.isEmpty()) {
      throw new IllegalArgumentException("signature must only be present with output");
    }
  }

  /** Result without signature data. */
  public ConversionResult(Outcome outcome, Optional<byte[]> output, ConversionReport report) {
    this(outcome, output, report, Optional.empty());
  }
}
