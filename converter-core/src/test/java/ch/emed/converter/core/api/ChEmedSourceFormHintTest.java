package ch.emed.converter.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.spi.FormatModule;
import ch.emed.converter.core.spi.FormatRegistry;
import ch.emed.converter.core.spi.MappingContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T008 (feature 003, FR-013): input of another form declared as CH EMED is refused with a hint on
 * what was detected, never with the input itself.
 */
class ChEmedSourceFormHintTest {

  private static final FormatId TARGET = FormatId.of("TARGET");

  private static final class TargetModule implements FormatModule {
    @Override
    public FormatId format() {
      return TARGET;
    }

    @Override
    public String pinnedSpecification() {
      return "target#1";
    }

    @Override
    public Bundle toChEmed(byte[] input, MappingContext ctx) {
      return null;
    }

    @Override
    public byte[] fromChEmed(Bundle document, MappingContext ctx) {
      return new byte[0];
    }
  }

  private static final String URL =
      "https://eprescription.hin.ch/#CHMED16A1H4sIAAAAAAAAA&i=Erika&t=1767225600&s=3066";
  private static final String PAYLOAD = "CHMED16A1H4sIAAAAAAAAAzQ1Njc4OTA=";

  static List<Arguments> inputs() {
    return List.of(
        Arguments.of(
            new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3},
            "QR code image"),
        Arguments.of(
            new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x41, 0x42}, "QR code image"),
        Arguments.of(URL.getBytes(StandardCharsets.UTF_8), "eRezept URL"),
        Arguments.of(("  " + URL.toUpperCase()).getBytes(StandardCharsets.UTF_8), "eRezept URL"),
        Arguments.of(PAYLOAD.getBytes(StandardCharsets.UTF_8), "QR payload"));
  }

  @ParameterizedTest
  @MethodSource("inputs")
  void otherFormDeclaredAsChEmedIsRefusedWithHint(byte[] input, String detected) {
    Converter converter =
        new DefaultConverter(FormatRegistry.of(List.of(new TargetModule())), null);

    ConversionResult result =
        converter.convert(
            new ConversionRequest(
                FormatId.CH_EMED,
                TARGET,
                input,
                ConversionOptions.defaults().withValidateOutput(false)));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.report().entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.PARSE_ERROR);
              assertThat(e.message()).contains(detected).contains("use source chmed16a");
              assertThat(e.message())
                  .doesNotContain("1767225600")
                  .doesNotContain("H4sI")
                  .doesNotContain("Erika");
            });
  }
}
