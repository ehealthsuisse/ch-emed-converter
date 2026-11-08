package ch.emed.converter.chmed16a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.Chmed16aPayloadDecoder;
import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.testsupport.LargePlanGenerator;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.OutputForm;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.qr.QrImageReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T037 (feature 003, US3): CH EMED Medication Card → CHMED16A QR payload and QR image (FR-006,
 * FR-007, FR-008, FR-011, FR-012).
 */
class QrOutputIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult toChmed16a(byte[] chEmed, OutputForm form) {
    return converter.convert(
        new ConversionRequest(
            FormatId.CH_EMED,
            FormatId.CHMED16A,
            chEmed,
            ConversionOptions.defaults().withOutputForm(form)));
  }

  private static byte[] toChEmed(byte[] chmed16a) {
    ConversionResult result =
        converter.convert(
            new ConversionRequest(
                FormatId.CHMED16A,
                FormatId.CH_EMED,
                chmed16a,
                TestOptions.withAuthorAndPatientId()));
    assertThat(result.outcome()).isNotEqualTo(Outcome.FAILED);
    return result.output().orElseThrow();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "medication-card-example.json",
        "medication-card-example-uuid-fullurl.json",
        "card-complex-timing.json",
        "card-epr-elements.json",
        "card-inactive-medication.json"
      })
  void payloadAndImageCarryExactlyTheDocument(String fixture) throws Exception {
    byte[] card = Fixtures.bytes("chemed/" + fixture);

    ConversionResult document = toChmed16a(card, OutputForm.DOCUMENT);
    ConversionResult payload = toChmed16a(card, OutputForm.QR_PAYLOAD);
    ConversionResult image = toChmed16a(card, OutputForm.QR_IMAGE);

    assertThat(payload.report().entries()).isEqualTo(document.report().entries());
    assertThat(image.report().entries()).isEqualTo(document.report().entries());
    String text = new String(payload.output().orElseThrow(), StandardCharsets.US_ASCII);
    assertThat(text).startsWith("CHMED16A1");
    assertThat(Chmed16aPayloadDecoder.decode(text, Long.MAX_VALUE))
        .isEqualTo(document.output().orElseThrow());
    assertThat(QrImageReader.read(image.output().orElseThrow(), QrImageReader.DEFAULT_MAX_PIXELS))
        .isEqualTo(payload.output().orElseThrow());
  }

  @ParameterizedTest
  @ValueSource(strings = {"medication-card-example.json", "card-complex-timing.json"})
  void imageRoundTripEqualsTheDocumentRoundTrip(String fixture) {
    // the document round trip differs only by the documented gaps (RoundTripIT); the image adds
    // none
    byte[] card = Fixtures.bytes("chemed/" + fixture);

    byte[] viaDocument = toChEmed(toChmed16a(card, OutputForm.DOCUMENT).output().orElseThrow());
    byte[] viaImage = toChEmed(toChmed16a(card, OutputForm.QR_IMAGE).output().orElseThrow());

    assertThat(viaImage).isEqualTo(viaDocument);
  }

  @ParameterizedTest
  @ValueSource(strings = {"plan-empty-meds.json", "plan-non-ascii.json"})
  void emptyAndNonAsciiPlansSurviveTheImage(String fixture) throws Exception {
    byte[] card = toChEmed(Fixtures.bytes("chmed16a/" + fixture));

    ConversionResult image = toChmed16a(card, OutputForm.QR_IMAGE);
    ConversionResult document = toChmed16a(card, OutputForm.DOCUMENT);

    assertThat(image.outcome()).isNotEqualTo(Outcome.FAILED);
    byte[] payload =
        QrImageReader.read(image.output().orElseThrow(), QrImageReader.DEFAULT_MAX_PIXELS);
    assertThat(
            Chmed16aPayloadDecoder.decode(
                new String(payload, StandardCharsets.US_ASCII), Long.MAX_VALUE))
        .isEqualTo(document.output().orElseThrow());
  }

  @Test
  void oversizedPlanFailsAsImageButNotAsPayload() {
    // generated plans compress well; 600 medicaments give a payload above one QR code. Without
    // the validator (not under test here) the large plan converts quickly.
    Converter unvalidated = Converters.create(null);
    ConversionResult cardResult =
        unvalidated.convert(
            new ConversionRequest(
                FormatId.CHMED16A,
                FormatId.CH_EMED,
                LargePlanGenerator.plan(600),
                TestOptions.withAuthorAndPatientId().withValidateOutput(false)));
    byte[] card = cardResult.output().orElseThrow();
    ConversionOptions noValidation = ConversionOptions.defaults().withValidateOutput(false);

    ConversionResult image =
        unvalidated.convert(
            new ConversionRequest(
                FormatId.CH_EMED,
                FormatId.CHMED16A,
                card,
                noValidation.withOutputForm(OutputForm.QR_IMAGE)));
    ConversionResult payload =
        unvalidated.convert(
            new ConversionRequest(
                FormatId.CH_EMED,
                FormatId.CHMED16A,
                card,
                noValidation.withOutputForm(OutputForm.QR_PAYLOAD)));

    assertThat(payload.output().orElseThrow().length).as("precondition").isGreaterThan(2331);
    assertThat(image.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(image.output()).isEmpty();
    assertThat(errors(image))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.QR_CAPACITY_EXCEEDED);
              assertThat(e.message()).contains("2331");
            });
    assertThat(payload.outcome()).isNotEqualTo(Outcome.FAILED);
  }

  @Test
  void outputIsDeterministic() {
    byte[] card = Fixtures.bytes("chemed/medication-card-example.json");

    assertThat(toChmed16a(card, OutputForm.QR_IMAGE).output().orElseThrow())
        .isEqualTo(toChmed16a(card, OutputForm.QR_IMAGE).output().orElseThrow());
    assertThat(toChmed16a(card, OutputForm.QR_PAYLOAD).output().orElseThrow())
        .isEqualTo(toChmed16a(card, OutputForm.QR_PAYLOAD).output().orElseThrow());
  }

  private static List<ReportEntry> errors(ConversionResult result) {
    return result.report().entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }
}
