package ch.emed.converter.rest.error;

/**
 * The request message is malformed. The message names the offending property path only, never its
 * value, and is used as problem {@code detail}.
 */
public class MalformedRequestException extends RuntimeException {

  public MalformedRequestException(String detail) {
    super(detail, null, false, false);
  }
}
