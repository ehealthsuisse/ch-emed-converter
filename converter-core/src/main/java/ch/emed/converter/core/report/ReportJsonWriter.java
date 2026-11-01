package ch.emed.converter.core.report;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Serializes a {@link ConversionReport} to JSON with a fixed field order, so that identical reports
 * serialize to identical bytes (FR-018). Hand-written to keep converter-core free of a JSON
 * library.
 */
public final class ReportJsonWriter {

  private ReportJsonWriter() {}

  public static byte[] write(ConversionReport report) {
    return toJson(report).getBytes(StandardCharsets.UTF_8);
  }

  public static String toJson(ConversionReport report) {
    StringBuilder sb = new StringBuilder(256);
    sb.append('{');
    field(sb, "reportVersion", report.reportVersion()).append(',');
    field(sb, "converterVersion", report.converterVersion()).append(',');
    field(sb, "source", report.source().id()).append(',');
    field(sb, "target", report.target().id()).append(',');
    string(sb, "specifications").append(":{");
    boolean first = true;
    for (Map.Entry<String, String> spec : new TreeMap<>(report.specifications()).entrySet()) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      field(sb, spec.getKey(), spec.getValue());
    }
    sb.append("},");
    field(sb, "outcome", report.outcome().name()).append(',');
    string(sb, "entries").append(":[");
    first = true;
    for (ReportEntry entry : report.entries()) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      sb.append('{');
      field(sb, "severity", entry.severity().name()).append(',');
      field(sb, "code", entry.code()).append(',');
      field(sb, "phase", entry.phase().name()).append(',');
      field(sb, "path", entry.path()).append(',');
      if (entry.targetPath() != null) {
        field(sb, "targetPath", entry.targetPath()).append(',');
      }
      field(sb, "message", entry.message()).append(',');
      string(sb, "clinicalLoss").append(':').append(entry.clinicalLoss());
      sb.append('}');
    }
    sb.append("]}");
    return sb.toString();
  }

  private static StringBuilder field(StringBuilder sb, String name, String value) {
    return string(string(sb, name).append(':'), value);
  }

  private static StringBuilder string(StringBuilder sb, String value) {
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.append('"');
  }
}
