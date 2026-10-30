package ch.emed.converter.rest.error;

import ch.emed.converter.core.report.ConversionReport;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.Severity;

/**
 * Classifies a {@code FAILED} library result (data-model §6): an ERROR while parsing or validating
 * the input means the caller sent an invalid document; any other failure (mapping, output
 * validation, strict-mode loss, missing required data) means the conversion failed.
 */
public final class FailureClassifier {

  private FailureClassifier() {}

  public static ErrorType classify(ConversionReport report) {
    boolean invalidInput =
        report.entries().stream()
            .anyMatch(
                e ->
                    e.severity() == Severity.ERROR
                        && (e.phase() == Phase.PARSE || e.phase() == Phase.VALIDATE_INPUT));
    return invalidInput ? ErrorType.INVALID_INPUT : ErrorType.CONVERSION_FAILED;
  }

  /** Fixed, content-free problem detail per failure type. */
  public static String detail(ErrorType type) {
    return type == ErrorType.INVALID_INPUT
        ? "The input document is invalid; see the report."
        : "The conversion failed; see the report.";
  }
}
