package ch.emed.converter.chmed16a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.Chmed16aJsonReader;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.validation.Chmed16aValidator;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/** US2 end to end through the library (SC-002). */
class ChEmedToChmed16aIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult convert(String fixture) {
    return converter.convert(
        new ConversionRequest(
            FormatId.CH_EMED,
            FormatId.CHMED16A,
            Fixtures.bytes("chemed/" + fixture),
            ConversionOptions.defaults()));
  }

  private static List<ReportEntry> errors(ConversionResult result) {
    return result.report().entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }

  @ParameterizedTest
  @CsvSource({
    "medication-card-example.json, medication-card-example.json",
    "medication-card-example-uuid-fullurl.json, medication-card-example.json",
    "card-epr-elements.json, medication-card-example.json",
    "card-inactive-medication.json, card-inactive-medication.json",
    "card-complex-timing.json, card-complex-timing.json"
  })
  void outputMatchesTheHandAuthoredExpectedDocument(String fixture, String expected)
      throws JSONException {
    ConversionResult result = convert(fixture);

    assertThat(errors(result)).as("errors for " + fixture).isEmpty();
    assertThat(result.outcome()).isNotEqualTo(Outcome.FAILED);
    JSONAssert.assertEquals(
        Fixtures.text("expected/chemed-to-chmed16a/" + expected),
        new String(result.output().orElseThrow(), StandardCharsets.UTF_8),
        JSONCompareMode.STRICT);
  }

  @ParameterizedTest
  @CsvSource({
    "medication-card-example.json",
    "medication-card-example-uuid-fullurl.json",
    "card-epr-elements.json",
    "card-inactive-medication.json",
    "card-complex-timing.json"
  })
  void outputPassesTheChmed16aValidatorAndEveryMedicationIsPresentOrReported(String fixture) {
    ConversionResult result = convert(fixture);

    ReportCollector readReport = new ReportCollector();
    Medication med =
        Chmed16aJsonReader.read(
            result.output().orElseThrow(), ConversionOptions.defaults(), readReport);
    assertThat(readReport.hasErrors()).isFalse();
    ReportCollector validation = new ReportCollector();
    Chmed16aValidator.validate(med, validation);
    assertThat(validation.hasErrors()).as("CHMED16A validation").isFalse();

    long statements =
        ChEmedJson.parse(Fixtures.bytes("chemed/" + fixture)).getEntry().stream()
            .filter(e -> e.getResource() instanceof MedicationStatement)
            .count();
    long excluded =
        result.report().entries().stream()
            .filter(e -> e.path().matches("MedicationStatement\\[\\d+]\\.status"))
            .filter(e -> e.message().contains("excluded"))
            .count();
    assertThat(med.medicaments().size() + excluded)
        .as("every medication present or reported (SC-003)")
        .isEqualTo(statements);
  }

  @Test
  void originalRepresentationIsReportedAsNotCarriedOver() {
    ConversionResult result = convert("medication-card-example.json");

    assertThat(result.report().entries())
        .anySatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
              assertThat(e.severity()).isEqualTo(Severity.INFORMATION);
              assertThat(e.path()).isEqualTo("Composition.section.where(code='55108-5')");
            });
  }

  @Test
  void reportEntriesNeverContainPatientValues() {
    ConversionResult result = convert("medication-card-example.json");

    assertThat(result.report().entries())
        .allSatisfy(
            e ->
                assertThat(e.message() + e.path())
                    .doesNotContain("Wegmüller")
                    .doesNotContain("Monika")
                    .doesNotContain("1943-05-15")
                    .doesNotContain("11111111"));
  }

  @Test
  void profileViolationFailsWithFhirValidationErrorsAndNoOutput() {
    ConversionResult result = convert("card-invalid-profile.json");

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(errors(result))
        .isNotEmpty()
        .allSatisfy(e -> assertThat(e.code()).isEqualTo(ReasonCode.FHIR_VALIDATION));
    assertThat(errors(result))
        .allSatisfy(e -> assertThat(e.phase()).isEqualTo(Phase.VALIDATE_INPUT));
  }

  @Test
  void unsupportedDocumentTypeFailsNamingTheType() {
    ConversionResult result = convert("card-unsupported-type.json");

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(errors(result))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNSUPPORTED_DOCUMENT_TYPE);
              assertThat(e.message()).contains("77603-9");
            });
  }

  @Test
  void nonJsonInputIsAParseError() {
    ConversionResult result =
        converter.convert(
            new ConversionRequest(
                FormatId.CH_EMED,
                FormatId.CHMED16A,
                "not json".getBytes(StandardCharsets.UTF_8),
                ConversionOptions.defaults()));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(errors(result))
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo(ReasonCode.PARSE_ERROR));
  }

  @Test
  void outputIsByteDeterministic() {
    assertThat(convert("card-complex-timing.json").output().orElseThrow())
        .isEqualTo(convert("card-complex-timing.json").output().orElseThrow());
  }
}
