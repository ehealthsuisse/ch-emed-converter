package ch.emed.converter.core.render;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The vendored sRGB ICC profile (version 2) used as output intent of the PDF/A original
 * representation (research R16, task T064). Source, licence and checksum: {@code
 * docs/specifications.md}, {@code docs/third-party-licenses.md}. The checksum is verified on load,
 * like the IG packages.
 */
final class SrgbProfile {

  /** SHA-256 of {@code sRGB2014.icc} of the International Color Consortium. */
  static final String SHA256 = "384b832de3412066743b52a75ee906b6fb9fb8d9e09e936fc2c43223815c6e0a";

  /** Output condition identifier and info of the output intent. */
  static final String IDENTIFIER = "sRGB IEC61966-2.1";

  private static final String RESOURCE = "/render/srgb.icc";

  private SrgbProfile() {}

  private static final class Holder {
    private static final byte[] BYTES = load();
  }

  /**
   * @return a copy of the verified profile bytes
   */
  static byte[] bytes() {
    return Holder.BYTES.clone();
  }

  private static byte[] load() {
    byte[] bytes;
    try (InputStream in = SrgbProfile.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("sRGB ICC profile missing: " + RESOURCE);
      }
      bytes = in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read sRGB ICC profile " + RESOURCE, e);
    }
    String actual;
    try {
      actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    if (!SHA256.equals(actual)) {
      throw new IllegalStateException(
          "sRGB ICC profile " + RESOURCE + " has SHA-256 " + actual + ", expected " + SHA256);
    }
    return bytes;
  }
}
