package ch.emed.converter.validation;

import java.util.regex.Pattern;

/**
 * Removes values from validator messages so that reports never contain patient data (FR-017):
 * quoted values, dates and long digit sequences are replaced; the message id and structural text
 * are kept.
 */
public final class ValidationMessageSanitizer {

  private static final Pattern SINGLE_QUOTED = Pattern.compile("'[^']*'");
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
    text = SINGLE_QUOTED.matcher(text).replaceAll("'…'");
    text = DOUBLE_QUOTED.matcher(text).replaceAll("\"…\"");
    text = DATE.matcher(text).replaceAll("…");
    text = LONG_DIGITS.matcher(text).replaceAll("…");
    return messageId == null || messageId.isBlank() ? text : messageId + ": " + text;
  }
}
