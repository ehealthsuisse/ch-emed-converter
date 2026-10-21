package ch.emed.converter.core.determinism;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NameBasedIdStrategyTest {

  private final IdStrategy strategy = new NameBasedIdStrategy();

  @Test
  void sameInputGivesSameId() {
    String first = strategy.id("doc-1", "/Medicaments/0");
    String second = new NameBasedIdStrategy().id("doc-1", "/Medicaments/0");
    assertThat(first).isEqualTo(second);
  }

  @Test
  void differentPathsGiveDifferentIds() {
    assertThat(strategy.id("doc-1", "/Medicaments/0"))
        .isNotEqualTo(strategy.id("doc-1", "/Medicaments/1"));
  }

  @Test
  void differentDocumentsGiveDifferentIds() {
    assertThat(strategy.id("doc-1", "/Patient")).isNotEqualTo(strategy.id("doc-2", "/Patient"));
  }

  @Test
  void idIsVersion5Uuid() {
    UUID uuid = UUID.fromString(strategy.id("doc-1", "/Patient"));
    assertThat(uuid.version()).isEqualTo(5);
    assertThat(uuid.variant()).isEqualTo(2);
  }
}
