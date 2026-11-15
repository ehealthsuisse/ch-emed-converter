package ch.emed.converter.chmed23a.io;

import ch.emed.converter.chmed23a.mapping.MappingRule;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * ChTransmissionFormat, ReleaseYear 23 (chtransmissionformat/README.md, research R4, R5): {@code
 * CHMED23{SubVersion}[.{Index}/{Total}].{Data}}, Data = Base64 of GZip of the JSON. Reads single
 * payloads and complete sets of chunks; writes single payloads only. Messages never contain payload
 * data (constitution IV).
 */
public final class TransmissionFormat {

  /** Prefix written for CHMED23A. */
  public static final String PREFIX = "CHMED23A.";

  private static final Pattern FORMAT =
      Pattern.compile(
          "CHMED23([A-Z]+)(?:\\.([1-9][0-9]{0,8})/([1-9][0-9]{0,8}))?\\.(.*)", Pattern.DOTALL);

  private TransmissionFormat() {}

  /** One parsed token; {@code index} and {@code total} are null without chunk metadata. */
  record Token(String subVersion, Integer index, Integer total, String data) {
    boolean chunk() {
      return index != null;
    }
  }

  /** True if the text starts with {@code CHMED23}, compared case-insensitively. */
  public static boolean isPayload(String text) {
    return text.regionMatches(true, 0, "CHMED23", 0, 7);
  }

  /** True if the text is a single token with chunk metadata, e.g. read from a QR image. */
  public static boolean isChunk(String text) {
    Token token = parse(text.strip());
    return token != null && token.chunk();
  }

  /**
   * Decodes a payload or a whitespace-separated set of chunks into the JSON bytes.
   *
   * @param text payload text
   * @param maxDecodedSize maximum size of the decompressed JSON in bytes
   * @param report receives problems (phase {@code PARSE})
   * @return the JSON bytes, or null after an ERROR entry
   */
  @MappingRule({"M23-IN-03", "M23-IN-04"})
  public static byte[] decode(String text, long maxDecodedSize, ReportCollector report) {
    String[] parts = text.strip().split("\\s+");
    Token first = parse(parts[0]);
    if (first == null) {
      return error(report, ReasonCode.PARSE_ERROR, "Input is not a valid CHMED23 payload");
    }
    String data;
    String subVersion = first.subVersion();
    if (parts.length == 1 && !first.chunk()) {
      data = first.data();
    } else {
      data = join(parts, report);
      if (data == null) {
        return null;
      }
    }
    if (!subVersion.equals("A")) {
      return error(
          report,
          ReasonCode.UNSUPPORTED_VERSION,
          "Payload declares CHMED23" + subVersion + "; only CHMED23A is supported");
    }
    byte[] compressed;
    try {
      compressed = Base64.getDecoder().decode(data);
    } catch (IllegalArgumentException e) {
      return error(report, ReasonCode.PARSE_ERROR, "CHMED23A payload is not valid Base64");
    }
    try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed));
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      long total = 0;
      for (int n = in.read(buffer); n > 0; n = in.read(buffer)) {
        total += n;
        if (total > maxDecodedSize) {
          return error(
              report,
              ReasonCode.PARSE_ERROR,
              "Decoded payload exceeds the maximum size of " + maxDecodedSize + " bytes");
        }
        out.write(buffer, 0, n);
      }
      return out.toByteArray();
    } catch (IOException e) {
      return error(report, ReasonCode.PARSE_ERROR, "CHMED23A payload is not valid GZip data");
    }
  }

  /** Joins chunks in index order (research R5); null after a {@code CHUNK_INVALID} entry. */
  private static String join(String[] parts, ReportCollector report) {
    TreeMap<Integer, String> chunks = new TreeMap<>();
    String subVersion = null;
    Integer total = null;
    for (int i = 0; i < parts.length; i++) {
      Token token = parse(parts[i]);
      String position = "Token " + (i + 1) + " of " + parts.length;
      if (token == null) {
        return chunkError(report, position + " is not a CHMED23 payload");
      }
      if (!token.chunk()) {
        return chunkError(report, position + " has no chunk index; chunks cannot be mixed");
      }
      if (subVersion != null && !subVersion.equals(token.subVersion())) {
        return chunkError(report, "Chunk " + token.index() + " has another subversion");
      }
      if (total != null && !total.equals(token.total())) {
        return chunkError(report, "Chunk " + token.index() + " declares another total");
      }
      subVersion = token.subVersion();
      total = token.total();
      if (total < 2) {
        return chunkError(report, "A chunk total must be greater than 1");
      }
      if (token.index() > total) {
        return chunkError(
            report, "Chunk index " + token.index() + " is greater than the total " + total);
      }
      if (chunks.put(token.index(), token.data()) != null) {
        return chunkError(report, "Chunk " + token.index() + " occurs more than once");
      }
    }
    for (int index = 1; index <= total; index++) {
      if (!chunks.containsKey(index)) {
        return chunkError(report, "Chunk " + index + " of " + total + " is missing");
      }
    }
    return String.join("", chunks.values());
  }

  /**
   * Encodes JSON bytes as one payload (M23-QR-10). Equal JSON gives equal payloads; decoding gives
   * back exactly the JSON bytes.
   *
   * @param json UTF-8 CHMED23A JSON as written by {@link Chmed23aJsonWriter}
   * @return the ASCII payload, without line break
   */
  @MappingRule("M23-QR-10")
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

  private static Token parse(String token) {
    if (!isPayload(token)) {
      return null;
    }
    int header = token.indexOf('.', 7);
    if (header < 0) {
      return null;
    }
    Matcher m =
        FORMAT.matcher(
            token.substring(0, header).toUpperCase(Locale.ROOT) + token.substring(header));
    if (!m.matches()) {
      return null;
    }
    Integer index = m.group(2) == null ? null : Integer.valueOf(m.group(2));
    Integer total = m.group(3) == null ? null : Integer.valueOf(m.group(3));
    return new Token(m.group(1), index, total, m.group(4));
  }

  private static byte[] error(ReportCollector report, String code, String message) {
    report.add(ReportEntry.error(code, Phase.PARSE, "/", message));
    return null;
  }

  private static String chunkError(ReportCollector report, String message) {
    report.add(ReportEntry.error(ReasonCode.CHUNK_INVALID, Phase.PARSE, "/", message));
    return null;
  }
}
