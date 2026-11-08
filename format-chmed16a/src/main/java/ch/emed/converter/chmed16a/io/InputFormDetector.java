package ch.emed.converter.chmed16a.io;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Detects the form of a CHMED16A input by its content (feature 003, data-model.md §1, FR-013). The
 * order matters: image signatures first, then the text forms. Never logs or returns content.
 */
public final class InputFormDetector {

  /** Input forms of source {@code chmed16a}. */
  public enum InputForm {
    /** PNG or JPEG image with a QR code. */
    IMAGE,
    /** Another image format (GIF, BMP, TIFF, WebP); refused. */
    UNSUPPORTED_IMAGE,
    /** Signed eRezept URL {@code https://eprescription.hin.ch/#…}. */
    EREZEPT_URL,
    /** Textual QR payload {@code CHMED…}. */
    PAYLOAD,
    /** A FHIR resource (top-level {@code resourceType}); declared with the wrong source. */
    FHIR_JSON,
    /** Anything else: read as CHMED16A JSON. */
    JSON
  }

  /** Start of a signed eRezept URL (research R6), compared case-insensitively. */
  public static final String EREZEPT_URL_PREFIX = "https://eprescription.hin.ch/";

  private static final JsonFactory JSON = new JsonFactory();

  private InputFormDetector() {}

  /**
   * Detects the form of raw input bytes.
   *
   * @param input input bytes
   * @return the form
   */
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
    String text = new String(input, StandardCharsets.UTF_8).strip();
    if (text.startsWith("﻿")) {
      text = text.substring(1).strip();
    }
    InputForm form = detectText(text);
    if (form == InputForm.JSON && text.startsWith("{") && hasTopLevelResourceType(text)) {
      return InputForm.FHIR_JSON;
    }
    return form;
  }

  /**
   * Detects the text forms (eRezept URL, payload, JSON), e.g. of decoded QR content.
   *
   * @param text text, trimmed or not
   * @return {@link InputForm#EREZEPT_URL}, {@link InputForm#PAYLOAD} or {@link InputForm#JSON}
   */
  public static InputForm detectText(String text) {
    String trimmed = text.strip();
    if (trimmed.regionMatches(true, 0, EREZEPT_URL_PREFIX, 0, EREZEPT_URL_PREFIX.length())) {
      return InputForm.EREZEPT_URL;
    }
    if (Chmed16aPayloadDecoder.isPayload(trimmed)) {
      return InputForm.PAYLOAD;
    }
    return InputForm.JSON;
  }

  private static boolean hasTopLevelResourceType(String text) {
    try (JsonParser parser = JSON.createParser(text)) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        return false;
      }
      for (JsonToken token = parser.nextToken();
          token == JsonToken.FIELD_NAME;
          token = parser.nextToken()) {
        if ("resourceType".equals(parser.currentName())) {
          return true;
        }
        parser.nextToken();
        parser.skipChildren();
      }
      return false;
    } catch (IOException e) {
      return false;
    }
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
