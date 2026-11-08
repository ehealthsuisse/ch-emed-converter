package ch.emed.converter.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.emed.converter.core.determinism.NameBasedIdStrategy;
import ch.emed.converter.core.report.ConversionReport;
import ch.emed.converter.core.report.Outcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** T006 (feature 003): library API additions are compatible with existing calls. */
class ConversionApiAdditionsTest {

  private static final SignatureData SIGNATURE =
      new SignatureData("Erika+Muster", "1767225600", "3066");

  @Test
  void defaultOutputFormIsDocument() {
    assertThat(ConversionOptions.defaults().outputForm()).isEqualTo(OutputForm.DOCUMENT);
  }

  @Test
  void withOutputFormKeepsAllOtherOptions() {
    ConversionOptions base =
        ConversionOptions.defaults()
            .withStrict(true)
            .withDefaultLanguage("fr-CH")
            .withConfidentiality(Confidentiality.RESTRICTED)
            .withMaxDecodedSize(1234);

    ConversionOptions changed = base.withOutputForm(OutputForm.QR_IMAGE);

    assertThat(changed.outputForm()).isEqualTo(OutputForm.QR_IMAGE);
    assertThat(changed.withOutputForm(OutputForm.DOCUMENT)).isEqualTo(base);
    assertThat(changed.withStrict(false).outputForm()).isEqualTo(OutputForm.QR_IMAGE);
  }

  @Test
  void previousCanonicalConstructorStillWorks() {
    ConversionOptions options =
        new ConversionOptions(
            false,
            true,
            new NameBasedIdStrategy(),
            Optional.empty(),
            ConversionOptions.DEFAULT_MAX_DECODED_SIZE,
            "de-CH",
            Confidentiality.NORMAL,
            Optional.empty(),
            Optional.empty());

    assertThat(options.outputForm()).isEqualTo(OutputForm.DOCUMENT);
  }

  @Test
  void outputFormHasExternalNames() {
    assertThat(OutputForm.QR_PAYLOAD.externalName()).isEqualTo("qr-payload");
    assertThat(OutputForm.fromExternalName("qr-image")).isEqualTo(OutputForm.QR_IMAGE);
    assertThat(OutputForm.fromExternalName("document")).isEqualTo(OutputForm.DOCUMENT);
    assertThatThrownBy(() -> OutputForm.fromExternalName("pdf"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void threeArgumentResultHasNoSignature() {
    ConversionResult result =
        new ConversionResult(Outcome.SUCCESS, Optional.of(new byte[] {1}), report(Outcome.SUCCESS));

    assertThat(result.signature()).isEmpty();
  }

  @Test
  void signatureRequiresOutput() {
    assertThatThrownBy(
            () ->
                new ConversionResult(
                    Outcome.FAILED,
                    Optional.empty(),
                    report(Outcome.FAILED),
                    Optional.of(SIGNATURE)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void signatureIsNeverVerified() {
    assertThat(SIGNATURE.verified()).isFalse();
    assertThatThrownBy(() -> new SignatureData(null, "1", "2"))
        .isInstanceOf(NullPointerException.class);
  }

  private static ConversionReport report(Outcome outcome) {
    return ConversionReport.of(
        "0.0.0", FormatId.CHMED16A, FormatId.CH_EMED, Map.of(), outcome, List.of());
  }
}
