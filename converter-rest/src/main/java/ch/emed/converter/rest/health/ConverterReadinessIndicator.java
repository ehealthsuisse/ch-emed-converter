package ch.emed.converter.rest.health;

import ch.emed.converter.rest.config.ConverterHolder;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness of the converter (FR-016, research R7): UP only when the shared converter with its
 * validation context is built; DOWN while starting or if building failed (e.g. missing IG
 * packages). Part of the readiness group ({@code application.yaml}), not of liveness, so a failed
 * build takes the instance out of traffic without restarting it in a loop.
 */
@Component("converter")
public class ConverterReadinessIndicator implements HealthIndicator {

  private final ConverterHolder converters;

  public ConverterReadinessIndicator(ConverterHolder converters) {
    this.converters = converters;
  }

  @Override
  public Health health() {
    ConverterHolder.State state = converters.state();
    Health.Builder health = state == ConverterHolder.State.READY ? Health.up() : Health.down();
    return health.withDetail("state", state.name()).build();
  }
}
