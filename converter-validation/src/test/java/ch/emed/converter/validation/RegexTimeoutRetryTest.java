package ch.emed.converter.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * The HL7 validator aborts pattern checks after a fixed 500 ms of wall-clock time; under CPU load a
 * valid document then gets errors. The validator retries such a run and, if the time limit is still
 * hit, reports {@code VALIDATION_INCOMPLETE} instead of claiming the document is invalid.
 */
class RegexTimeoutRetryTest {

  private final List<Long> pauses = new ArrayList<>();

  @Test
  void runWithoutTimeoutIsNotRepeated() {
    Runs runs = new Runs(List.of(clean()));

    List<ReportEntry> entries = validate(runs);

    assertThat(runs.calls).isEqualTo(1);
    assertThat(pauses).isEmpty();
    assertThat(entries).extracting(ReportEntry::code).containsOnly(ReasonCode.FHIR_VALIDATION);
  }

  @Test
  void transientTimeoutIsRetriedAndTheCleanRunCounts() {
    Runs runs = new Runs(List.of(timedOut(), clean()));

    List<ReportEntry> entries = validate(runs);

    assertThat(runs.calls).isEqualTo(2);
    assertThat(pauses).hasSize(1);
    assertThat(entries).noneMatch(e -> e.severity() == Severity.ERROR);
    assertThat(entries)
        .extracting(ReportEntry::code)
        .doesNotContain(ReasonCode.VALIDATION_INCOMPLETE);
  }

  @Test
  void persistentTimeoutIsReportedAsIncompleteNotAsInvalid() {
    Runs runs = new Runs(List.of(timedOut(), timedOut(), timedOut()));

    List<ReportEntry> entries = validate(runs);

    assertThat(runs.calls).isEqualTo(3);
    assertThat(pauses).hasSize(2);
    assertThat(entries)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.VALIDATION_INCOMPLETE);
              assertThat(e.severity()).isEqualTo(Severity.ERROR);
              assertThat(e.phase()).isEqualTo(Phase.VALIDATE_OUTPUT);
              assertThat(e.message()).contains("retry");
            });
  }

  private List<ReportEntry> validate(Runs runs) {
    return HapiChEmedValidator.validateWithRetry(runs, Phase.VALIDATE_OUTPUT, pauses::add);
  }

  /** Validation runs returning the given message lists in order. */
  private static final class Runs implements Supplier<List<SingleValidationMessage>> {
    private final List<List<SingleValidationMessage>> results;
    int calls;

    Runs(List<List<SingleValidationMessage>> results) {
      this.results = results;
    }

    @Override
    public List<SingleValidationMessage> get() {
      return results.get(calls++);
    }
  }

  private static List<SingleValidationMessage> clean() {
    return List.of(message("Terminology_PassThrough_TX_Message", ResultSeverityEnum.INFORMATION));
  }

  /** A run hit by the time limit: the timeout error and a consequential slicing error. */
  private static List<SingleValidationMessage> timedOut() {
    return List.of(
        message(HapiChEmedValidator.REGEX_TIMEOUT_ID, ResultSeverityEnum.ERROR),
        message("Validation_VAL_Profile_Minimum_SLICE", ResultSeverityEnum.ERROR));
  }

  private static SingleValidationMessage message(String id, ResultSeverityEnum severity) {
    SingleValidationMessage message = new SingleValidationMessage();
    message.setMessageId(id);
    message.setSeverity(severity);
    message.setMessage(id + ": synthetic message");
    message.setLocationString("Bundle");
    return message;
  }
}
