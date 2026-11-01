package ch.emed.converter.core.report;

import ch.emed.converter.core.api.FormatId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Conversion report (contracts/conversion-report.schema.json). Entries are ordered by phase, then
 * path (stable for equal keys).
 *
 * @param reportVersion report schema version
 * @param converterVersion converter version
 * @param source source format
 * @param target target format
 * @param specifications pinned specification per format id
 * @param outcome outcome
 * @param entries ordered entries
 */
public record ConversionReport(
    String reportVersion,
    String converterVersion,
    FormatId source,
    FormatId target,
    Map<String, String> specifications,
    Outcome outcome,
    List<ReportEntry> entries) {

  public static final String REPORT_VERSION = "1.0.0";

  private static final Comparator<ReportEntry> ORDER =
      Comparator.comparing(ReportEntry::phase).thenComparing(ReportEntry::path);

  public ConversionReport {
    Objects.requireNonNull(reportVersion, "reportVersion");
    Objects.requireNonNull(converterVersion, "converterVersion");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(outcome, "outcome");
    specifications = Map.copyOf(new TreeMap<>(specifications));
    entries = entries.stream().sorted(ORDER).toList();
  }

  public static ConversionReport of(
      String converterVersion,
      FormatId source,
      FormatId target,
      Map<String, String> specifications,
      Outcome outcome,
      List<ReportEntry> entries) {
    return new ConversionReport(
        REPORT_VERSION, converterVersion, source, target, specifications, outcome, entries);
  }
}
