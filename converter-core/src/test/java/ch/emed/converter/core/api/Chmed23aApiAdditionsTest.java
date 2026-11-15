package ch.emed.converter.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.spi.FormatModule;
import ch.emed.converter.core.spi.FormatRegistry;
import ch.emed.converter.core.spi.MappingContext;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;

/** T006, T007, T011 (feature 006): library API additions of contracts/library-api.md. */
class Chmed23aApiAdditionsTest {

  private record Module(FormatId format, boolean deprecated) implements FormatModule {

    @Override
    public String pinnedSpecification() {
      return "test";
    }

    @Override
    public Bundle toChEmed(byte[] input, MappingContext ctx) {
      return null;
    }

    @Override
    public byte[] fromChEmed(Bundle document, MappingContext ctx) {
      return null;
    }
  }

  @Test
  void chmed23aHasItsExternalName() {
    assertThat(FormatId.CHMED23A.id()).isEqualTo("CHMED23A");
    assertThat(FormatId.CHMED23A.externalName()).isEqualTo("chmed23a");
    assertThat(FormatId.fromExternalName("chmed23a")).isEqualTo(FormatId.CHMED23A);
  }

  @Test
  void chunkInvalidIsAReasonCode() {
    assertThat(ReasonCode.CHUNK_INVALID).isEqualTo("CHUNK_INVALID");
  }

  @Test
  void existingConstructorsAreNotDeprecated() {
    assertThat(new FormatInfo(FormatId.CH_EMED, List.of("json"), List.of()).deprecated()).isFalse();
    assertThat(new FormatInfo(FormatId.CHMED16A, List.of(), List.of(), List.of("MP")).deprecated())
        .isFalse();
    assertThat(
            new FormatInfo(FormatId.CHMED23A, List.of(), List.of(), List.of(), true).deprecated())
        .isTrue();
  }

  @Test
  void modulesAreNotDeprecatedByDefault() {
    FormatModule plain =
        new FormatModule() {
          @Override
          public FormatId format() {
            return FormatId.of("PLAIN");
          }

          @Override
          public String pinnedSpecification() {
            return "test";
          }

          @Override
          public Bundle toChEmed(byte[] input, MappingContext ctx) {
            return null;
          }

          @Override
          public byte[] fromChEmed(Bundle document, MappingContext ctx) {
            return null;
          }
        };
    assertThat(plain.deprecated()).isFalse();
  }

  @Test
  void formatsCarryTheDeprecatedMarkOfTheModule() {
    Converter converter =
        new DefaultConverter(
            FormatRegistry.of(
                List.of(new Module(FormatId.CHMED23A, true), new Module(FormatId.CHMED16A, false))),
            null);

    assertThat(converter.formats())
        .extracting(f -> f.format().id() + "=" + f.deprecated())
        .containsExactly("CHMED16A=false", "CHMED23A=true", "CH_EMED=false");
  }
}
