package ch.emed.converter.rest.error;

import ch.emed.converter.rest.execution.BoundedConversionExecutor;
import ch.emed.converter.rest.web.RequestAttributes;
import ch.emed.converter.rest.web.RequestSizeLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;

/**
 * Maps exceptions to problem responses (research R4). Exception messages are never returned or
 * logged, because they may quote input values (FR-008, FR-014).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final ProblemFactory problems;

  public GlobalExceptionHandler(ProblemFactory problems) {
    this.problems = problems;
  }

  @ExceptionHandler(MalformedRequestException.class)
  ResponseEntity<Problem> malformed(MalformedRequestException e, HttpServletRequest request) {
    return problems.create(ErrorType.MALFORMED_REQUEST, e.getMessage(), request);
  }

  /** Request message is not JSON, has an unknown property or a value of the wrong type. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Problem> unreadable(
      HttpMessageNotReadableException e, HttpServletRequest request) {
    if (hasCause(e, RequestSizeLimitFilter.RequestTooLargeException.class)) {
      return problems.create(
          ErrorType.REQUEST_TOO_LARGE,
          "The request message exceeds the configured maximum size.",
          request);
    }
    String path = jsonPath(e);
    String detail =
        path.isEmpty()
            ? "The request message is not valid JSON."
            : "Invalid or unknown property: " + path;
    return problems.create(ErrorType.MALFORMED_REQUEST, detail, request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<Problem> mediaType(HttpServletRequest request) {
    return problems.create(
        ErrorType.UNSUPPORTED_MEDIA_TYPE, "The request message must be application/json.", request);
  }

  @ExceptionHandler(BoundedConversionExecutor.BusyException.class)
  ResponseEntity<Problem> busy(HttpServletRequest request) {
    return problems.retryLater(
        ErrorType.SERVICE_BUSY,
        "All conversion slots are taken; retry after the indicated time.",
        request);
  }

  @ExceptionHandler(BoundedConversionExecutor.TimeoutExceededException.class)
  ResponseEntity<Problem> timeout(HttpServletRequest request) {
    return problems.create(
        ErrorType.CONVERSION_TIMEOUT,
        "The conversion exceeded the configured time limit and was cancelled.",
        request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e, HttpServletRequest request) {
    if (e instanceof ErrorResponse standard) {
      // routing-level errors of Spring MVC (unknown path, wrong method): status only, no body
      return ResponseEntity.status(standard.getStatusCode()).build();
    }
    LOG.error(
        "Internal error correlationId={} exception={}",
        RequestAttributes.correlationId(request),
        e.getClass().getName());
    return problems.create(
        ErrorType.INTERNAL_ERROR,
        "An unexpected error occurred; quote the correlation id when reporting it.",
        request);
  }

  private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (type.isInstance(t)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Property path of a Jackson failure, e.g. {@code options.patientIdentifier.system}. Property
   * names come from the caller, so only names of the contract are echoed; the path ends before the
   * first unknown name, which is replaced by a placeholder.
   */
  private static String jsonPath(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof JacksonException jackson) {
        List<String> segments = new ArrayList<>();
        for (JacksonException.Reference ref : jackson.getPath()) {
          String name = ref.getPropertyName();
          if (name == null || !CONTRACT_PROPERTIES.contains(name)) {
            segments.add("(unknown property)");
            break;
          }
          segments.add(name);
        }
        return String.join(".", segments);
      }
    }
    return "";
  }

  /** Property names of the request message in contracts/openapi.yaml. */
  private static final Set<String> CONTRACT_PROPERTIES =
      Set.of(
          "document",
          "options",
          "strict",
          "language",
          "confidentiality",
          "patientIdentifier",
          "system",
          "value",
          "author",
          "gln",
          "given",
          "family",
          "organization",
          "name",
          "street",
          "zip",
          "city",
          "country");
}
