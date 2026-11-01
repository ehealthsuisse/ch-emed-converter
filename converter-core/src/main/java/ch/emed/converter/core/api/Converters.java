package ch.emed.converter.core.api;

import ch.emed.converter.core.spi.ChEmedValidator;
import ch.emed.converter.core.spi.FormatRegistry;
import java.util.ServiceLoader;

/** Factory for {@link Converter} instances. */
public final class Converters {

  private Converters() {}

  /**
   * Discovers format modules and the CH EMED validator via {@link ServiceLoader}. Building the
   * validation context is expensive; create one converter and reuse it.
   *
   * @return a converter
   */
  public static Converter create() {
    ChEmedValidator validator = ServiceLoader.load(ChEmedValidator.class).findFirst().orElse(null);
    return create(validator);
  }

  /**
   * Uses the given (e.g. shared, warm) validator.
   *
   * @param validator CH EMED validator
   * @return a converter
   */
  public static Converter create(ChEmedValidator validator) {
    return new DefaultConverter(FormatRegistry.load(), validator);
  }
}
