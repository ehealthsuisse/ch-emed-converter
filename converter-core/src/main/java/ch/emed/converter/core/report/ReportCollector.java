package ch.emed.converter.core.report;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Collects report entries during one conversion. Not thread-safe; one instance per request. */
public final class ReportCollector {

  private final List<ReportEntry> entries = new ArrayList<>();

  public ReportCollector add(ReportEntry entry) {
    entries.add(entry);
    return this;
  }

  public ReportCollector addAll(Collection<ReportEntry> more) {
    entries.addAll(more);
    return this;
  }

  public boolean hasErrors() {
    return entries.stream().anyMatch(e -> e.severity() == Severity.ERROR);
  }

  public List<ReportEntry> entries() {
    return List.copyOf(entries);
  }
}
