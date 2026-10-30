package ch.emed.converter.cli;

import static ch.emed.converter.cli.CliTestSupport.resource;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.api.ConversionPair;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.report.ConversionReport;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A conversion whose validation could not be completed (system load, {@code VALIDATION_INCOMPLETE})
 * is not an invalid input: the CLI exits 3 (internal error, retry) instead of 1 (contracts/cli.md).
 */
class ValidationIncompleteExitTest {

  @Test
  void incompleteValidationExitsThreeWithNothingOnStdout() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();

    int exit =
        CliMain.execute(
            IncompleteConverter::new,
            new String[] {
              "convert",
              "--from",
              "chmed16a",
              "--to",
              "ch-emed",
              resource("plan-basic.json").toString()
            },
            new ByteArrayInputStream(new byte[0]),
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

    assertThat(exit).isEqualTo(CliMain.EXIT_INTERNAL);
    assertThat(out.toByteArray()).isEmpty();
    assertThat(err.toString(StandardCharsets.UTF_8)).contains(ReasonCode.VALIDATION_INCOMPLETE);
  }

  /** Converter whose output validation always hits the validator's time limit. */
  private static final class IncompleteConverter implements Converter {

    @Override
    public ConversionResult convert(ConversionRequest request) {
      ReportEntry entry =
          ReportEntry.of(
              Severity.ERROR,
              ReasonCode.VALIDATION_INCOMPLETE,
              Phase.VALIDATE_OUTPUT,
              "Bundle",
              "Validation could not be completed under the current system load; retry later.");
      return new ConversionResult(
          Outcome.FAILED,
          Optional.empty(),
          ConversionReport.of(
              version(),
              request.source(),
              request.target(),
              pinnedSpecifications(),
              Outcome.FAILED,
              List.of(entry)));
    }

    @Override
    public Set<ConversionPair> supportedConversions() {
      return Set.of(
          new ConversionPair(FormatId.CHMED16A, FormatId.CH_EMED),
          new ConversionPair(FormatId.CH_EMED, FormatId.CHMED16A));
    }

    @Override
    public Map<String, String> pinnedSpecifications() {
      return Map.of();
    }

    @Override
    public String version() {
      return "test";
    }
  }
}
