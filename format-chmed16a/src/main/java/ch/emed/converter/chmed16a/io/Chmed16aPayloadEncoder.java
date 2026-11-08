package ch.emed.converter.chmed16a.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

/**
 * Encodes CHMED16A JSON as the textual QR payload {@code CHMED16A1} + Base64 (RFC 4648 §4, with
 * padding) of GZip (feature 003, research R2). The GZip header has no timestamp, so equal JSON
 * gives equal payloads (FR-011); decoding gives back exactly the JSON bytes (FR-012).
 */
public final class Chmed16aPayloadEncoder {

  /** Prefix for ReleaseYear 16, SubVersion A, compressed. */
  public static final String PREFIX = "CHMED16A1";

  private Chmed16aPayloadEncoder() {}

  /**
   * Encodes JSON bytes.
   *
   * @param json UTF-8 CHMED16A JSON, as written by {@link Chmed16aJsonWriter}
   * @return the ASCII payload, without line break
   */
  public static byte[] encode(byte[] json) {
    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
      gzip.write(json);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return (PREFIX + Base64.getEncoder().encodeToString(compressed.toByteArray()))
        .getBytes(StandardCharsets.US_ASCII);
  }
}
