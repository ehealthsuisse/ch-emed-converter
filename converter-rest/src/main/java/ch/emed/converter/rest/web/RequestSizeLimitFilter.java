package ch.emed.converter.rest.web;

import ch.emed.converter.rest.config.ConverterRestProperties;
import ch.emed.converter.rest.error.ErrorType;
import ch.emed.converter.rest.error.Problem;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Enforces {@code converter.rest.max-request-size} (research R6): a declared Content-Length above
 * the limit is rejected before the body is read; a body without Content-Length is counted while it
 * is read and aborted with {@link RequestTooLargeException} at the limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

  /** The request body exceeded the limit while being read. */
  public static class RequestTooLargeException extends IOException {
    RequestTooLargeException() {
      super("Request body exceeds the configured limit");
    }
  }

  private final long limit;
  private final JsonMapper json;

  public RequestSizeLimitFilter(ConverterRestProperties properties, JsonMapper json) {
    this.limit = properties.maxRequestSize().toBytes();
    this.json = json;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > limit) {
      reject(request, response);
      return;
    }
    chain.doFilter(new CountingRequest(request, limit), response);
  }

  private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
    ErrorType type = ErrorType.REQUEST_TOO_LARGE;
    request.setAttribute(RequestAttributes.ERROR_TYPE, type);
    response.setStatus(type.status());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(
        response.getOutputStream(),
        new Problem(
            type.typeUri(),
            type.title(),
            type.status(),
            "The request message exceeds the configured maximum size.",
            type,
            RequestAttributes.correlationId(request),
            null,
            null));
  }

  private static final class CountingRequest extends HttpServletRequestWrapper {

    private final long limit;
    private ServletInputStream stream;

    CountingRequest(HttpServletRequest request, long limit) {
      super(request);
      this.limit = limit;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (stream == null) {
        stream = new CountingStream(super.getInputStream(), limit);
      }
      return stream;
    }
  }

  private static final class CountingStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long limit;
    private long count;

    CountingStream(ServletInputStream delegate, long limit) {
      this.delegate = delegate;
      this.limit = limit;
    }

    @Override
    public int read() throws IOException {
      int b = delegate.read();
      if (b >= 0) {
        add(1);
      }
      return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int n = delegate.read(buffer, offset, length);
      if (n > 0) {
        add(n);
      }
      return n;
    }

    private void add(int n) throws RequestTooLargeException {
      count += n;
      if (count > limit) {
        throw new RequestTooLargeException();
      }
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
      delegate.setReadListener(listener);
    }
  }
}
