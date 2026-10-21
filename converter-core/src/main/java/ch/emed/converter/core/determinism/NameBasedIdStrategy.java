package ch.emed.converter.core.determinism;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** UUID version 5 (SHA-1, name-based) of {@code documentId + "#" + path} in a fixed namespace. */
public final class NameBasedIdStrategy implements IdStrategy {

  /** Fixed namespace of the CH EMED converter; changing it changes every generated id. */
  public static final UUID NAMESPACE = UUID.fromString("5b1d2c1e-6a35-4c1f-9d0e-3f6b8f0c2a71");

  @Override
  public String id(String documentId, String path) {
    return uuid5(NAMESPACE, documentId + "#" + path).toString();
  }

  static UUID uuid5(UUID namespace, String name) {
    MessageDigest sha1;
    try {
      sha1 = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 not available", e);
    }
    ByteBuffer ns = ByteBuffer.allocate(16);
    ns.putLong(namespace.getMostSignificantBits()).putLong(namespace.getLeastSignificantBits());
    sha1.update(ns.array());
    byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
    hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
    hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
    ByteBuffer bb = ByteBuffer.wrap(hash, 0, 16);
    return new UUID(bb.getLong(), bb.getLong());
  }
}
