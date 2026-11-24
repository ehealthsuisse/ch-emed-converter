package ch.emed.converter.validation;

import java.util.regex.Pattern;

/**
 * Removes values from validator messages so that reports never contain patient data (FR-017):
 * quoted values, dates and long digit sequences are replaced; the message id and structural text
 * are kept.
 */
public final class ValidationMessageSanitizer {

  /*
   * A single quote between two letters or digits is an apostrophe ("can't", "patient's", "O'Brien"),
   * not a quote: a value starts at a quote not preceded by a letter or digit and ends at the first
   * quote not followed by one. Without this, the apostrophe of "can't" paired with the opening quote
   * of the value and left the value itself visible. A value that is never closed is removed up to
   * the end of the message.
   */
  private static final Pattern SINGLE_QUOTED =
      Pattern.compile("(?<![\\p{L}\\p{N}])'(?:.*?'(?![\\p{L}\\p{N}])|.*)", Pattern.DOTALL);
  private static final Pattern DOUBLE_QUOTED = Pattern.compile("\"[^\"]*\"");
  private static final Pattern DATE =
      Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}(T[0-9:.+\\-Z]*)?\\b");
  private static final Pattern LONG_DIGITS = Pattern.compile("\\b[\\d.]{7,}\\b");

  private ValidationMessageSanitizer() {}

  /**
   * Sanitizes a validator message.
   *
   * @param messageId HAPI message id, may be null
   * @param message raw message
   * @return {@code messageId: message} without values
   */
  public static String sanitize(String messageId, String message) {
    String text = message == null ? "" : message;
    text =
        SINGLE_QUOTED
            .matcher(text)
            .replaceAll(m -> m.group().length() > 1 && m.group().endsWith("'") ? "'…'" : "'…");
    text = DOUBLE_QUOTED.matcher(text).replaceAll("\"…\"");
    text = DATE.matcher(text).replaceAll("…");
    text = LONG_DIGITS.matcher(text).replaceAll("…");
    return messageId == null || messageId.isBlank() ? text : messageId + ": " + text;
  }

  private static final Pattern RESOURCE_COMMENT = Pattern.compile("/\\*([A-Za-z]+)/?[^*]*\\*/");

  /**
   * Sanitizes a validator location. The HL7 validator inserts {@code /*Type/id*}{@code /} after
   * each resource; resource ids are free text and may contain a name (feature 004, the official CH
   * EMED EPR example {@code PatientDupont}), so only the type is kept, as {@code /*Type*}{@code /}.
   *
   * @param location raw location, may be null
   * @return location without resource ids, {@code Bundle} if null
   */
  public static String location(String location) {
    return location == null ? "Bundle" : RESOURCE_COMMENT.matcher(location).replaceAll("/*$1*/");
  }
}
