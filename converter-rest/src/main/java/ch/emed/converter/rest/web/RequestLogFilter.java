package ch.emed.converter.rest.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Writes exactly one operational log line per request (data-model §10, FR-014). It records metadata
 * only: never body content, option values, report messages or patient data.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLogFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger("ch.emed.converter.rest.requests");

  /** Path values are caller input; only well-formed format names are logged. */
  private static final Pattern FORMAT_NAME = Pattern.compile("[a-z0-9-]{1,32}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - start) / 1_000_000;
      LOG.info(
          "request timestamp={} correlationId={} method={} route={} from={} to={} status={}"
              + " errorType={} outcome={} durationMs={} requestBytes={} documentBytes={}",
          Instant.now(),
          RequestAttributes.correlationId(request),
          request.getMethod(),
          route(request),
          pathFormat(request, "from"),
          pathFormat(request, "to"),
          response.getStatus(),
          attribute(request, RequestAttributes.ERROR_TYPE),
          attribute(request, RequestAttributes.OUTCOME),
          durationMs,
          request.getContentLengthLong(),
          attribute(request, RequestAttributes.DOCUMENT_BYTES));
    }
  }

  private static Object route(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return pattern != null ? pattern : "-";
  }

  private static String pathFormat(HttpServletRequest request, String name) {
    Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (!(vars instanceof Map<?, ?> map) || !(map.get(name) instanceof String value)) {
      return "-";
    }
    return FORMAT_NAME.matcher(value).matches() ? value : "invalid";
  }

  private static Object attribute(HttpServletRequest request, String name) {
    Object value = request.getAttribute(name);
    return value != null ? value : "-";
  }
}
