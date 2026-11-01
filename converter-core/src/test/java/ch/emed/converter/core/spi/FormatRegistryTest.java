package ch.emed.converter.core.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.emed.converter.core.api.FormatId;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormatRegistryTest {

  @Test
  void discoversModulesViaServiceLoader() {
    FormatRegistry registry = FormatRegistry.load();

    assertThat(registry.find(TestFormatModule.FORMAT)).isPresent();
    assertThat(registry.find(FormatId.of("NOT_REGISTERED"))).isEmpty();
  }

  @Test
  void duplicateFormatIsRejected() {
    assertThatThrownBy(
            () -> FormatRegistry.of(List.of(new TestFormatModule(), new TestFormatModule())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(TestFormatModule.FORMAT.id());
  }
}
