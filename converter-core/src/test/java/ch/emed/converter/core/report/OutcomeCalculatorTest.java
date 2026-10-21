package ch.emed.converter.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Outcome rules from data-model.md §1 ("Outcome state rules"), non-strict and strict. */
class OutcomeCalculatorTest {

  private static ReportEntry entry(Severity severity) {
    return ReportEntry.of(severity, ReasonCode.UNMAPPABLE_ELEMENT, Phase.MAP, "/Patient", "msg");
  }

  @Test
  void noEntriesIsSuccess() {
    assertThat(OutcomeCalculator.calculate(List.of())).isEqualTo(Outcome.SUCCESS);
  }

  @Test
  void informationOnlyIsSuccess() {
    assertThat(OutcomeCalculator.calculate(List.of(entry(Severity.INFORMATION))))
        .isEqualTo(Outcome.SUCCESS);
  }

  @Test
  void anyWarningIsSuccessWithWarnings() {
    assertThat(
            OutcomeCalculator.calculate(
                List.of(entry(Severity.INFORMATION), entry(Severity.WARNING))))
        .isEqualTo(Outcome.SUCCESS_WITH_WARNINGS);
  }

  @Test
  void anyErrorIsFailed() {
    assertThat(
            OutcomeCalculator.calculate(
                List.of(
                    entry(Severity.WARNING), entry(Severity.ERROR), entry(Severity.INFORMATION))))
        .isEqualTo(Outcome.FAILED);
  }

  private static ReportEntry loss(Severity severity) {
    return entry(severity).withClinicalLoss(true);
  }

  @Test
  void strictModeFailsOnAnyEntryWithClinicalLoss() {
    assertThat(OutcomeCalculator.calculate(List.of(loss(Severity.WARNING)), true))
        .isEqualTo(Outcome.FAILED);
    assertThat(OutcomeCalculator.calculate(List.of(loss(Severity.INFORMATION)), true))
        .isEqualTo(Outcome.FAILED);
  }

  @Test
  void withoutStrictModeClinicalLossIsOnlyAWarning() {
    assertThat(OutcomeCalculator.calculate(List.of(loss(Severity.WARNING)), false))
        .isEqualTo(Outcome.SUCCESS_WITH_WARNINGS);
    assertThat(OutcomeCalculator.calculate(List.of(loss(Severity.WARNING))))
        .isEqualTo(Outcome.SUCCESS_WITH_WARNINGS);
  }

  @Test
  void strictModeLeavesEntriesWithoutClinicalLossAlone() {
    assertThat(OutcomeCalculator.calculate(List.of(entry(Severity.WARNING)), true))
        .isEqualTo(Outcome.SUCCESS_WITH_WARNINGS);
    assertThat(OutcomeCalculator.calculate(List.of(entry(Severity.INFORMATION)), true))
        .isEqualTo(Outcome.SUCCESS);
    assertThat(OutcomeCalculator.calculate(List.of(), true)).isEqualTo(Outcome.SUCCESS);
  }

  @Test
  void errorsFailInStrictModeToo() {
    assertThat(OutcomeCalculator.calculate(List.of(entry(Severity.ERROR)), true))
        .isEqualTo(Outcome.FAILED);
  }
}
