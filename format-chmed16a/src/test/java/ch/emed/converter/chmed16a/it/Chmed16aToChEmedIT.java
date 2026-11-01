package ch.emed.converter.chmed16a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** US1 end to end through the library (SC-001, SC-003). */
class Chmed16aToChEmedIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult convert(String fixture, ConversionOptions options) {
    return converter.convert(
        new ConversionRequest(
            FormatId.CHMED16A, FormatId.CH_EMED, Fixtures.bytes("chmed16a/" + fixture), options));
  }

  private static List<ReportEntry> errors(ConversionResult result) {
    return result.report().entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }

  @Test
  void planBasicMatchesExpectedOutput() {
    ConversionResult result = convert("plan-basic.json", TestOptions.withAuthor());

    assertThat(errors(result)).isEmpty();
    ChEmedAssertions.assertMatchesExpected(
        result.output().orElseThrow(),
        "expected/chmed16a-to-chemed/plan-basic.json",
        "0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e01");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "plan-basic.json",
        "plan-basic.qr.txt",
        "plan-free-text-med.json",
        "plan-pharmacode.json",
        "plan-taking-times.json",
        "plan-reserve.json",
        "plan-cycle.json",
        "plan-medical-data.json",
        "plan-empty-meds.json",
        "plan-invalid-gtin.json",
        "plan-older-revision.json",
        "plan-unknown-fields.json",
        "plan-non-ascii.json",
        "plan-remarks-validation.json",
        "official/readme-example-medication-plan.json",
        "official/readme-example-medication-plan.qr.txt"
      })
  void positiveFixturesValidateAndKeepEveryMedicament(String fixture) {
    ConversionResult result = convert(fixture, TestOptions.withAuthorAndPatientId());

    assertThat(errors(result)).as("errors for " + fixture).isEmpty();
    assertThat(result.outcome()).isNotEqualTo(Outcome.FAILED);
    byte[] output = result.output().orElseThrow();
    ChEmedAssertions.assertValid(output);

    Bundle bundle = ChEmedJson.parse(output);
    long statements =
        bundle.getEntry().stream()
            .filter(e -> e.getResource() instanceof MedicationStatement)
            .count();
    int posologies =
        ch.emed.converter.chmed16a.io.Chmed16aJsonReader.read(
                Fixtures.bytes("chmed16a/" + fixture),
                ConversionOptions.defaults(),
                new ch.emed.converter.core.report.ReportCollector())
            .medicaments()
            .stream()
            .mapToInt(m -> Math.max(1, m.pos().size()))
            .sum();
    assertThat(statements).as("every medicament/posology present (SC-003)").isEqualTo(posologies);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "invalid-json.txt",
        "chmed23a-plan.json",
        "chmed16a-pmc.json",
        "plan-missing-birthdate.json",
        "qr-corrupt-base64.txt",
        "qr-corrupt-gzip.txt",
        "qr-oversized.txt",
        "erezept-url.txt"
      })
  void negativeFixturesFailWithoutOutput(String fixture) {
    ConversionResult result = convert(fixture, TestOptions.withAuthorAndPatientId());

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(errors(result)).isNotEmpty();
  }

  @Test
  void missingAuthorDetailsFailWithMissingRequiredData() {
    ConversionResult result = convert("plan-basic.json", ConversionOptions.defaults());

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(errors(result)).anyMatch(e -> e.code().equals(ReasonCode.MISSING_REQUIRED_DATA));
  }
}
