package ch.emed.converter.core.spi;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.ReportCollector;
import java.util.Objects;

/**
 * Context of one conversion passed to format modules.
 *
 * @param options conversion options (incl. id strategy and optional clock)
 * @param report collector for report entries
 */
public record MappingContext(ConversionOptions options, ReportCollector report) {

  public MappingContext {
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(report, "report");
  }
}
