package ch.emed.converter.chmed16a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.Chmed16aJsonReader;
import ch.emed.converter.chmed16a.io.Chmed16aPayloadDecoder;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.ErezeptSchema;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.validation.Chmed16aValidator;
import ch.emed.converter.chmed16a.validation.ErezeptPrescriptionRules;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.OutputForm;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T043 (feature 003, US4): a CH EMED Medication Prescription gives the unsigned CHMED16A payload
 * that the HIN erxproxy signs (FR-009, SC-004); a QR image of it is refused (FR-007).
 */
class PrescriptionPayloadIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult convert(String fixture, OutputForm form) {
    return converter.convert(
        new ConversionRequest(
            FormatId.CH_EMED,
            FormatId.CHMED16A,
            Fixtures.bytes("chemed/" + fixture),
            ConversionOptions.defaults().withOutputForm(form)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"medication-prescription-example.json", "rx-substitution-repeats.json"})
  void unsignedPayloadMeetsTheErezeptRequirements(String fixture) throws Exception {
    ConversionResult result = convert(fixture, OutputForm.QR_PAYLOAD);

    assertThat(result.outcome()).isNotEqualTo(Outcome.FAILED);
    assertThat(result.signature()).isEmpty();
    String payload = new String(result.output().orElseThrow(), StandardCharsets.US_ASCII);
    assertThat(payload).startsWith("CHMED16A1");
    byte[] json = Chmed16aPayloadDecoder.decode(payload, Long.MAX_VALUE);
    ReportCollector report = new ReportCollector();
    Medication med = Chmed16aJsonReader.read(json, ConversionOptions.defaults(), report);
    Chmed16aValidator.validate(med, report, Phase.VALIDATE_OUTPUT);
    ErezeptPrescriptionRules.validate(med, report, Phase.VALIDATE_OUTPUT);
    assertThat(report.entries()).as("CHMED16A and eRezept rules").isEmpty();
    assertThat(ErezeptSchema.validate(json)).as("published eRezept schema v7").isEmpty();
    assertThat(json).isEqualTo(convert(fixture, OutputForm.DOCUMENT).output().orElseThrow());
  }

  @Test
  void invalidPrescriptionGivesNoPayload() {
    ConversionResult result = convert("rx-missing-prescriber-gln.json", OutputForm.QR_PAYLOAD);

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(result.report().entries()).anyMatch(e -> e.severity() == Severity.ERROR);
  }

  @Test
  void qrImageOfAPrescriptionIsRefused() {
    ConversionResult result = convert("medication-prescription-example.json", OutputForm.QR_IMAGE);

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(result.report().entries())
        .filteredOn(e -> e.severity() == Severity.ERROR)
        .extracting(ReportEntry::code, ReportEntry::message)
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.toList().get(0)).isEqualTo(ReasonCode.QR_IMAGE_NOT_ALLOWED);
              assertThat((String) t.toList().get(1)).contains("unsigned payload only");
            });
  }
}
