package ch.emed.converter.qr;

import java.util.Objects;

/** A QR image could not be read or written. The message never contains image or code content. */
public final class QrException extends Exception {

  /** What went wrong. */
  public enum Kind {
    /** Unsupported image format, or an image that cannot be decoded. */
    UNREADABLE,
    /** Image above the pixel limit. */
    TOO_LARGE,
    /** No readable QR code in the image. */
    NOT_FOUND,
    /** More than one QR code in the image. */
    MULTIPLE,
    /** Text larger than one QR code holds. */
    CAPACITY
  }

  private final Kind kind;

  public QrException(Kind kind, String message) {
    super(message);
    this.kind = Objects.requireNonNull(kind, "kind");
  }

  public Kind kind() {
    return kind;
  }
}
