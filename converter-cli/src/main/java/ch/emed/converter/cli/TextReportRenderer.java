package ch.emed.converter.cli;

import ch.emed.converter.core.report.ConversionReport;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;

/** Human-readable rendering of a conversion report (never contains patient values). */
final class TextReportRenderer {

  private TextReportRenderer() {}

  static String render(ConversionReport report, boolean strict) {
    StringBuilder sb = new StringBuilder();
    sb.append("Outcome: ").append(report.outcome());
    if (strict
        && report.outcome() == Outcome.FAILED
        && report.entries().stream().noneMatch(e -> e.severity() == Severity.ERROR)) {
      sb.append(" (strict mode: entries marked [clinical loss])");
    }
    sb.append(System.lineSeparator());
    for (ReportEntry e : report.entries()) {
      sb.append(e.severity())
          .append(' ')
          .append(e.code())
          .append(' ')
          .append(e.path())
          .append(": ")
          .append(e.message());
      if (e.clinicalLoss()) {
        sb.append(" [clinical loss]");
      }
      sb.append(System.lineSeparator());
    }
    return sb.toString();
  }
}
