package ch.emed.converter.rest.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Request attributes through which the handlers pass operational metadata to the filters. They
 * carry metadata only, never content (research R5).
 */
public final class RequestAttributes {

  public static final String CORRELATION_ID = RequestAttributes.class.getName() + ".correlationId";
  public static final String ERROR_TYPE = RequestAttributes.class.getName() + ".errorType";
  public static final String OUTCOME = RequestAttributes.class.getName() + ".outcome";
  public static final String DOCUMENT_BYTES = RequestAttributes.class.getName() + ".documentBytes";

  private RequestAttributes() {}

  /** Correlation id set by {@link CorrelationIdFilter}. */
  public static String correlationId(HttpServletRequest request) {
    return (String) request.getAttribute(CORRELATION_ID);
  }
}
