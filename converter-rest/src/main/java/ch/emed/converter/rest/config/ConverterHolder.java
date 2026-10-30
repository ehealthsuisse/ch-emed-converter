package ch.emed.converter.rest.config;

import ch.emed.converter.core.api.Converter;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the one shared, warm {@link Converter} of the service and its state (data-model §9). {@link
 * Converter} is thread-safe, so all requests use the same instance.
 */
public final class ConverterHolder {

  /** Lifecycle of the shared converter. */
  public enum State {
    STARTING,
    READY,
    FAILED
  }

  private static final Logger LOG = LoggerFactory.getLogger(ConverterHolder.class);

  private volatile State state = State.STARTING;
  private volatile Converter converter;

  private ConverterHolder() {}

  /**
   * Builds the converter. A failure (e.g. missing or corrupt IG packages) leaves the service in
   * state {@link State#FAILED} instead of stopping it, so that readiness can report it.
   */
  public static ConverterHolder build(Supplier<Converter> factory) {
    ConverterHolder holder = new ConverterHolder();
    try {
      holder.converter = factory.get();
      holder.state = State.READY;
    } catch (RuntimeException | Error e) {
      holder.state = State.FAILED;
      LOG.error("Converter could not be built: {}", e.getClass().getName());
    }
    return holder;
  }

  public State state() {
    return state;
  }

  /** The converter, present only in state {@link State#READY}. */
  public Optional<Converter> converter() {
    return state == State.READY ? Optional.of(converter) : Optional.empty();
  }
}
