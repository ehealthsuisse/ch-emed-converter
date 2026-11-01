package ch.emed.converter.rest.error;

import java.util.Locale;

/** Failure types of the service and their HTTP status (data-model §6, research R4). */
public enum ErrorType {
  MALFORMED_REQUEST(400, "Malformed request"),
  UNSUPPORTED_CONVERSION(404, "Unsupported conversion"),
  REQUEST_TOO_LARGE(413, "Request too large"),
  UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),
  INVALID_INPUT(422, "Invalid input document"),
  CONVERSION_FAILED(422, "Conversion failed"),
  SERVICE_BUSY(503, "Service busy"),
  NOT_READY(503, "Service not ready"),
  CONVERSION_TIMEOUT(504, "Conversion timeout"),
  INTERNAL_ERROR(500, "Internal error");

  private static final String TYPE_PREFIX = "urn:ch-emed-converter:problem:";

  private final int status;
  private final String title;

  ErrorType(int status, String title) {
    this.status = status;
    this.title = title;
  }

  public int status() {
    return status;
  }

  public String title() {
    return title;
  }

  /** RFC 9457 {@code type}, e.g. {@code urn:ch-emed-converter:problem:invalid-input}. */
  public String typeUri() {
    return TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
