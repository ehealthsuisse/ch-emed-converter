package ch.emed.converter.rest.execution;

import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.rest.config.ConverterRestProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Runs conversions on a bounded pool (research R6): at most {@code max-concurrent} at a time, at
 * most {@code queue-size} waiting. A full pool is rejected, an overrunning conversion cancelled; no
 * partial output is ever returned.
 */
@Component
public class BoundedConversionExecutor {

  /** All conversion slots and the queue are taken. */
  public static class BusyException extends RuntimeException {
    public BusyException() {
      super(null, null, false, false);
    }
  }

  /** The conversion exceeded {@code converter.rest.timeout} and was cancelled. */
  public static class TimeoutExceededException extends RuntimeException {
    public TimeoutExceededException() {
      super(null, null, false, false);
    }
  }

  private final ThreadPoolExecutor pool;
  private final long timeoutNanos;

  public BoundedConversionExecutor(ConverterRestProperties properties) {
    int threads = properties.maxConcurrent();
    int queueSize = properties.queueSize();
    BlockingQueue<Runnable> queue =
        queueSize == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(queueSize);
    AtomicInteger counter = new AtomicInteger();
    this.pool =
        new ThreadPoolExecutor(
            threads,
            threads,
            0L,
            TimeUnit.MILLISECONDS,
            queue,
            r -> {
              Thread t = new Thread(r, "conversion-" + counter.incrementAndGet());
              t.setDaemon(true);
              return t;
            },
            new ThreadPoolExecutor.AbortPolicy());
    this.timeoutNanos = properties.timeout().toNanos();
  }

  /**
   * Runs one conversion and waits for its result.
   *
   * @throws BusyException if no slot is free
   * @throws TimeoutExceededException if the time limit is exceeded
   */
  public ConversionResult execute(Callable<ConversionResult> conversion) {
    Future<ConversionResult> future;
    try {
      future = pool.submit(conversion);
    } catch (RejectedExecutionException e) {
      throw new BusyException();
    }
    try {
      return future.get(timeoutNanos, TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new TimeoutExceededException();
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the conversion");
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (e.getCause() instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException("Conversion failed unexpectedly", e.getCause());
    }
  }

  @PreDestroy
  void shutdown() {
    pool.shutdownNow();
  }
}
