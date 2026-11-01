package ch.emed.converter.chmed16a.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Decodes the textual CHMED16A QR payload (ChTransmissionFormat, ReleaseYear 16): {@code
 * CHMED16{SubVersion}{Compression}{Data}}, Data = Base64 GZip JSON if Compression = 1, plain JSON
 * if 0. Error messages never contain the payload (constitution IV).
 */
public final class Chmed16aPayloadDecoder {

  private static final Pattern FORMAT =
      Pattern.compile("^CHMED([0-9]{2})([A-Z]+)(.*)$", Pattern.DOTALL);
  private static final Pattern RELEASE_16 = Pattern.compile("^([A-Z])([01])(.*)$", Pattern.DOTALL);

  /** Raised for undecodable payloads; the message never contains payload content. */
  public static final class DecodingException extends Exception {
    private final boolean unsupportedVersion;

    DecodingException(String message, boolean unsupportedVersion) {
      super(message);
      this.unsupportedVersion = unsupportedVersion;
    }

    public boolean unsupportedVersion() {
      return unsupportedVersion;
    }
  }

  private Chmed16aPayloadDecoder() {}

  /** True if the text starts with the ChTransmissionFormat prefix (case-insensitive). */
  public static boolean isPayload(String text) {
    return text.regionMatches(true, 0, "CHMED", 0, 5);
  }

  /**
   * Decodes a payload.
   *
   * @param payload trimmed payload text
   * @param maxDecodedSize maximum size of the decompressed JSON in bytes
   * @return the CHMED16A JSON (UTF-8)
   * @throws DecodingException if the payload is not a decodable CHMED16A payload
   */
  public static byte[] decode(String payload, long maxDecodedSize) throws DecodingException {
    String normalized =
        payload.substring(0, Math.min(payload.length(), 8)).toUpperCase(Locale.ROOT)
            + payload.substring(Math.min(payload.length(), 8));
    Matcher format = FORMAT.matcher(normalized);
    if (!format.matches()) {
      throw new DecodingException("Input is not a valid ChTransmissionFormat payload", false);
    }
    if (!format.group(1).equals("16") || !format.group(2).startsWith("A")) {
      throw new DecodingException(
          "Payload declares CHMED"
              + format.group(1)
              + format.group(2).charAt(0)
              + "; only CHMED16A is supported by this conversion",
          true);
    }
    Matcher release = RELEASE_16.matcher(normalized.substring(7));
    if (!release.matches()) {
      throw new DecodingException("CHMED16A payload has no valid compression flag", false);
    }
    String data = release.group(3).trim();
    if (release.group(2).equals("0")) {
      byte[] json = data.getBytes(StandardCharsets.UTF_8);
      if (json.length > maxDecodedSize) {
        throw new DecodingException("Decoded payload exceeds the maximum size", false);
      }
      return json;
    }
    byte[] compressed;
    try {
      compressed = Base64.getDecoder().decode(data);
    } catch (IllegalArgumentException e) {
      throw new DecodingException("CHMED16A payload is not valid Base64", false);
    }
    try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed));
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      long total = 0;
      for (int n = in.read(buffer); n > 0; n = in.read(buffer)) {
        total += n;
        if (total > maxDecodedSize) {
          throw new DecodingException("Decoded payload exceeds the maximum size", false);
        }
        out.write(buffer, 0, n);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new DecodingException("CHMED16A payload is not valid GZip data", false);
    }
  }
}
