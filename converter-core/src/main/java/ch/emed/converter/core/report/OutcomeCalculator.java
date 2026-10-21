package ch.emed.converter.core.report;

import java.util.List;

/** Outcome rules of data-model.md §1. */
public final class OutcomeCalculator {

  private OutcomeCalculator() {}

  /**
   * Non-strict rules: any ERROR → FAILED; any WARNING → SUCCESS_WITH_WARNINGS; otherwise SUCCESS.
   *
   * @param entries report entries
   * @return the outcome
   */
  public static Outcome calculate(List<ReportEntry> entries) {
    return calculate(entries, false);
  }

  /**
   * Outcome rules including strict mode: any ERROR → FAILED; strict and any entry with clinical
   * loss → FAILED; any WARNING → SUCCESS_WITH_WARNINGS; otherwise SUCCESS.
   *
   * @param entries report entries
   * @param strict whether strict mode (FR-016) is on
   * @return the outcome
   */
  public static Outcome calculate(List<ReportEntry> entries, boolean strict) {
    if (entries.stream().anyMatch(e -> e.severity() == Severity.ERROR)) {
      return Outcome.FAILED;
    }
    if (strict && entries.stream().anyMatch(ReportEntry::clinicalLoss)) {
      return Outcome.FAILED;
    }
    if (entries.stream().anyMatch(e -> e.severity() == Severity.WARNING)) {
      return Outcome.SUCCESS_WITH_WARNINGS;
    }
    return Outcome.SUCCESS;
  }
}
