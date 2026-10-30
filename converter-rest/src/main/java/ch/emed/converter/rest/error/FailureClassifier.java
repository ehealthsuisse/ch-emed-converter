package ch.emed.converter.rest.error;

import ch.emed.converter.core.report.ConversionReport;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.Severity;

/**
 * Classifies a {@code FAILED} library result (data-model §6): a validation that could not be
 * completed under system load ({@code VALIDATION_INCOMPLETE}) says nothing about the document, so
 * the caller should retry; otherwise an ERROR while parsing or validating the input means the
 * caller sent an invalid document, and any other failure (mapping, output validation, strict-mode
 * loss, missing required data) means the conversion failed.
 */
public final class FailureClassifier {

  private FailureClassifier() {}

  public static ErrorType classify(ConversionReport report) {
    boolean incomplete =
        report.entries().stream().anyMatch(e -> ReasonCode.VALIDATION_INCOMPLETE.equals(e.code()));
    if (incomplete) {
      return ErrorType.SERVICE_BUSY;
    }
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
    return switch (type) {
      case INVALID_INPUT -> "The input document is invalid; see the report.";
      case SERVICE_BUSY ->
          "The validation could not be completed under the current load; retry after the"
              + " indicated time.";
      default -> "The conversion failed; see the report.";
    };
  }
}
