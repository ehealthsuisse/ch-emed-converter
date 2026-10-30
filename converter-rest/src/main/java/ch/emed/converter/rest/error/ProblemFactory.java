package ch.emed.converter.rest.error;

import ch.emed.converter.rest.model.ConversionPairDto;
import ch.emed.converter.rest.web.RequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Builds {@code application/problem+json} responses (research R3/R4). Callers pass a fixed,
 * content-free {@code detail}: never input content, option values, patient values or stack traces
 * (FR-008).
 */
@Component
public class ProblemFactory {

  public static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

  /** Conversions take seconds; a short back-off is enough for busy and not-ready answers. */
  static final String RETRY_AFTER_SECONDS = "1";

  /** Failure without a conversion attempt. */
  public ResponseEntity<Problem> create(
      ErrorType errorType, String detail, HttpServletRequest request) {
    return create(errorType, detail, null, null, request);
  }

  /** Failure of an attempted conversion; carries the report (constitution v1.4.1). */
  public ResponseEntity<Problem> withReport(
      ErrorType errorType, String detail, String reportJson, HttpServletRequest request) {
    return create(errorType, detail, reportJson, null, request);
  }

  /** {@link ErrorType#UNSUPPORTED_CONVERSION} listing what is supported. */
  public ResponseEntity<Problem> unsupported(
      String detail, List<ConversionPairDto> supported, HttpServletRequest request) {
    return create(ErrorType.UNSUPPORTED_CONVERSION, detail, null, supported, request);
  }

  /** Temporary failure (busy, not ready) with {@code Retry-After} in seconds. */
  public ResponseEntity<Problem> retryLater(
      ErrorType errorType, String detail, HttpServletRequest request) {
    ResponseEntity<Problem> problem = create(errorType, detail, null, null, request);
    return ResponseEntity.status(problem.getStatusCode())
        .headers(problem.getHeaders())
        .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
        .body(problem.getBody());
  }

  private ResponseEntity<Problem> create(
      ErrorType errorType,
      String detail,
      String reportJson,
      List<ConversionPairDto> supported,
      HttpServletRequest request) {
    request.setAttribute(RequestAttributes.ERROR_TYPE, errorType);
    Problem problem =
        new Problem(
            errorType.typeUri(),
            errorType.title(),
            errorType.status(),
            detail,
            errorType,
            RequestAttributes.correlationId(request),
            reportJson,
            supported);
    return ResponseEntity.status(errorType.status()).contentType(PROBLEM_JSON).body(problem);
  }
}
