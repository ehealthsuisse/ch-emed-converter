package ch.emed.converter.chmed16a.io;

import ch.emed.converter.chmed16a.mapping.MappingRule;
import ch.emed.converter.core.api.SignatureData;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses a signed eRezept URL {@code https://eprescription.hin.ch/#CHMED16A1…&i=…&t=…&s=…} (feature
 * 003, research R6). The signature parameters are returned raw and never verified; messages name
 * parameters, never their values (FR-016).
 */
public final class ErezeptUrlParser {

  /** Payload and signature data of a signed eRezept. */
  public record Parsed(String payload, SignatureData signature) {}

  private static final List<String> SIGNATURE_PARAMETERS = List.of("i", "t", "s");
  private static final Pattern TIMESTAMP = Pattern.compile("[0-9]+");
  private static final Pattern HEX = Pattern.compile("[0-9A-Fa-f]+");

  private ErezeptUrlParser() {}

  /**
   * Parses a URL.
   *
   * @param url the URL text, trimmed or not
   * @param maxLength maximum length in characters
   * @param report receives problems
   * @return the payload and signature data, or null if the URL is not a valid signed eRezept URL
   *     (ERROR entry)
   */
  @MappingRule({"M-URL-01", "M-URL-02", "M-URL-03"})
  public static Parsed parse(String url, long maxLength, ReportCollector report) {
    String text = url.strip();
    if (text.length() > maxLength) {
      return invalid(
          report, "The eRezept URL exceeds the size limit of " + maxLength + " characters");
    }
    String prefix = InputFormDetector.EREZEPT_URL_PREFIX;
    if (!text.regionMatches(true, 0, prefix, 0, prefix.length())) {
      return invalid(report, "The 'host' of the URL is not eprescription.hin.ch");
    }
    int hash = text.indexOf('#');
    if (text.indexOf('?') >= 0 && (hash < 0 || text.indexOf('?') < hash)) {
      report.add(
          ReportEntry.information(
              ReasonCode.UNKNOWN_FIELD_IGNORED,
              Phase.PARSE,
              "/",
              "The query part of the eRezept URL is ignored"));
    }
    String fragment = hash < 0 ? "" : text.substring(hash + 1);

    String payload = null;
    Map<String, String> parameters = new LinkedHashMap<>();
    for (String token : fragment.split("&", -1)) {
      if (token.isEmpty()) {
        continue;
      }
      if (Chmed16aPayloadDecoder.isPayload(token)) {
        if (payload != null) {
          return invalid(report, "The eRezept URL holds more than one 'payload'");
        }
        payload = token;
        continue;
      }
      int equals = token.indexOf('=');
      String name = equals < 0 ? token : token.substring(0, equals);
      String value = equals < 0 ? "" : token.substring(equals + 1);
      if (!SIGNATURE_PARAMETERS.contains(name)) {
        report.add(
            ReportEntry.information(
                ReasonCode.UNKNOWN_FIELD_IGNORED,
                Phase.PARSE,
                "/",
                "Unknown eRezept URL parameter '" + safeName(name) + "' is ignored"));
        continue;
      }
      if (parameters.putIfAbsent(name, value) != null) {
        return invalid(report, "The eRezept URL parameter '" + name + "' occurs more than once");
      }
    }
    if (payload == null) {
      return invalid(report, "The eRezept URL holds no CHMED 'payload'");
    }
    for (String name : SIGNATURE_PARAMETERS) {
      String value = parameters.get(name);
      if (value == null || value.isEmpty()) {
        return invalid(
            report,
            "The eRezept URL parameter '" + name + "' (signature data) is missing or empty");
      }
    }
    SignatureData signature =
        new SignatureData(parameters.get("i"), parameters.get("t"), parameters.get("s"));
    unrecognised(report, "t", TIMESTAMP, signature.timestamp());
    unrecognised(report, "s", HEX, signature.signature());
    return new Parsed(payload, signature);
  }

  private static void unrecognised(
      ReportCollector report, String name, Pattern expected, String value) {
    if (!expected.matcher(value).matches()) {
      report.add(
          ReportEntry.information(
              ReasonCode.SIGNATURE_FORM_UNRECOGNISED,
              Phase.PARSE,
              "/",
              "The form of the eRezept URL parameter '"
                  + name
                  + "' is not recognised; it is passed on unchanged"));
    }
  }

  /** Parameter names come from the input; only short plain names are echoed. */
  private static String safeName(String name) {
    return name.matches("[A-Za-z0-9_-]{1,20}") ? name : "?";
  }

  private static Parsed invalid(ReportCollector report, String message) {
    report.add(ReportEntry.error(ReasonCode.EREZEPT_URL_INVALID, Phase.PARSE, "/", message));
    return null;
  }
}
