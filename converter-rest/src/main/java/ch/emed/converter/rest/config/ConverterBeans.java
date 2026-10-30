package ch.emed.converter.rest.config;

import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.spi.ChEmedValidator;
import java.util.ServiceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the shared converter with the vendored IG packages at startup (research R7). */
@Configuration(proxyBeanMethods = false)
public class ConverterBeans {

  @Bean
  ConverterHolder converterHolder() {
    return ConverterHolder.build(
        () -> {
          // The service never returns unvalidated output: without a validator it is not ready.
          ChEmedValidator validator =
              ServiceLoader.load(ChEmedValidator.class)
                  .findFirst()
                  .orElseThrow(() -> new IllegalStateException("No CH EMED validator available"));
          return Converters.create(validator);
        });
  }
}
