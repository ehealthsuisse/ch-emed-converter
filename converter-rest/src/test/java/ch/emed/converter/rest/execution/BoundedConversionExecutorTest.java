package ch.emed.converter.rest.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.emed.converter.rest.config.ConverterRestProperties;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/** T030: real pool behaviour behind SERVICE_BUSY and CONVERSION_TIMEOUT (research R6). */
class BoundedConversionExecutorTest {

  private BoundedConversionExecutor executor;

  private BoundedConversionExecutor create(Duration timeout) {
    executor =
        new BoundedConversionExecutor(
            new ConverterRestProperties(
                DataSize.ofMegabytes(5), DataSize.ofMegabytes(8), timeout, 1, 0));
    return executor;
  }

  @AfterEach
  void shutdown() {
    executor.shutdown();
  }

  @Test
  void rejectsWhenAllSlotsAndQueueAreTaken() throws Exception {
    BoundedConversionExecutor pool = create(Duration.ofSeconds(10));
    CountDownLatch running = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<Object> first =
        CompletableFuture.supplyAsync(
            () ->
                pool.execute(
                    () -> {
                      running.countDown();
                      release.await();
                      return null;
                    }));
    assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();

    assertThatThrownBy(() -> pool.execute(() -> null))
        .isInstanceOf(BoundedConversionExecutor.BusyException.class);

    release.countDown();
    first.get(5, TimeUnit.SECONDS);
  }

  @Test
  void cancelsConversionExceedingTheTimeout() throws Exception {
    BoundedConversionExecutor pool = create(Duration.ofMillis(100));
    AtomicBoolean interrupted = new AtomicBoolean();
    CountDownLatch finished = new CountDownLatch(1);

    assertThatThrownBy(
            () ->
                pool.execute(
                    () -> {
                      try {
                        Thread.sleep(10_000);
                      } catch (InterruptedException e) {
                        interrupted.set(true);
                      } finally {
                        finished.countDown();
                      }
                      return null;
                    }))
        .isInstanceOf(BoundedConversionExecutor.TimeoutExceededException.class);
    assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(interrupted).isTrue();
  }
}
