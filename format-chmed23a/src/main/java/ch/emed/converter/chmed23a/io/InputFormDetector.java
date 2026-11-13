package ch.emed.converter.chmed23a.io;

import ch.emed.converter.chmed23a.mapping.MappingRule;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Detects the form of a CHMED23A input by its content (mapping spec M23-IN-01, research R3). Image
 * signatures first, then the text forms. Never logs or returns content.
 */
public final class InputFormDetector {

  /** Input forms of source {@code chmed23a}. */
  public enum InputForm {
    /** PNG or JPEG image with a QR code. */
    IMAGE,
    /** Another image format (GIF, BMP, TIFF, WebP); refused. */
    UNSUPPORTED_IMAGE,
    /** A {@code CHMED23…} payload, possibly several chunks. */
    PAYLOAD,
    /** A CHMED16A payload or document; declared with the wrong source. */
    CHMED16A,
    /** A FHIR resource; declared with the wrong source. */
    FHIR_JSON,
    /** A JSON object: read as CHMED23A JSON. */
    JSON,
    /** Anything else. */
    UNKNOWN
  }

  private static final JsonFactory JSON = new JsonFactory();
  private static final Set<String> CHMED16A_KEYS = Set.of("Patient", "Medicaments", "MedType");

  private InputFormDetector() {}

  /**
   * Detects the form of raw input bytes.
   *
   * @param input input bytes
   * @return the form
   */
  @MappingRule("M23-IN-01")
  public static InputForm detect(byte[] input) {
    if (startsWith(input, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)
        || startsWith(input, 0, 0xFF, 0xD8, 0xFF)) {
      return InputForm.IMAGE;
    }
    if (startsWith(input, 0, 'G', 'I', 'F', '8')
        || startsWith(input, 0, 'B', 'M')
        || startsWith(input, 0, 0x49, 0x49, 0x2A, 0x00)
        || startsWith(input, 0, 0x4D, 0x4D, 0x00, 0x2A)
        || (startsWith(input, 0, 'R', 'I', 'F', 'F') && startsWith(input, 8, 'W', 'E', 'B', 'P'))) {
      return InputForm.UNSUPPORTED_IMAGE;
    }
    return detectText(text(input));
  }

  /**
   * Detects the text forms, e.g. of decoded QR content.
   *
   * @param text text, trimmed or not
   * @return the form
   */
  public static InputForm detectText(String text) {
    String trimmed = text.strip();
    if (TransmissionFormat.isPayload(trimmed)) {
      return InputForm.PAYLOAD;
    }
    if (trimmed.regionMatches(true, 0, "CHMED16", 0, 7)) {
      return InputForm.CHMED16A;
    }
    if (!trimmed.startsWith("{")) {
      return InputForm.UNKNOWN;
    }
    Set<String> keys = topLevelKeys(trimmed);
    if (keys.contains("resourceType")) {
      return InputForm.FHIR_JSON;
    }
    if (!keys.contains("patient") && keys.stream().anyMatch(CHMED16A_KEYS::contains)) {
      return InputForm.CHMED16A;
    }
    return InputForm.JSON;
  }

  /** The input as text, without a leading BOM and surrounding whitespace. */
  public static String text(byte[] input) {
    String text = new String(input, StandardCharsets.UTF_8).strip();
    return text.startsWith("﻿") ? text.substring(1).strip() : text;
  }

  private static Set<String> topLevelKeys(String text) {
    Set<String> keys = new java.util.HashSet<>();
    try (JsonParser parser = JSON.createParser(text)) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        return keys;
      }
      for (JsonToken token = parser.nextToken();
          token == JsonToken.FIELD_NAME;
          token = parser.nextToken()) {
        keys.add(parser.currentName());
        parser.nextToken();
        parser.skipChildren();
      }
    } catch (IOException e) {
      // invalid JSON: the reader reports it
    }
    return keys;
  }

  private static boolean startsWith(byte[] input, int offset, int... prefix) {
    if (input.length < offset + prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if ((input[offset + i] & 0xFF) != prefix[i]) {
        return false;
      }
    }
    return true;
  }
}
