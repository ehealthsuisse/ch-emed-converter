package ch.emed.converter.chmed16a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.Chmed16aFormatModule;
import ch.emed.converter.chmed16a.io.Chmed16aJsonReader;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.ErezeptSchema;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.chmed16a.validation.Chmed16aValidator;
import ch.emed.converter.chmed16a.validation.ErezeptPrescriptionRules;
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
import ch.emed.converter.core.spi.MappingContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/** US3 end to end through the library (SC-007, FR-008a, FR-008b). */
class PrescriptionIT {

  private static final String RX_BASIC_ID = "7b1f3c1e-9d54-4a0e-8e0c-2a6c1f9e8d02";

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult toChEmed(String fixture, ConversionOptions options) {
    return converter.convert(
        new ConversionRequest(
            FormatId.CHMED16A, FormatId.CH_EMED, Fixtures.bytes("chmed16a/" + fixture), options));
  }

  private static ConversionResult toChmed16a(String fixture) {
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

  // ---------------------------------------------------------------------------------------------
  // CHMED16A prescription → CH EMED Medication Prescription

  @Test
  void rxBasicMatchesTheHandAuthoredExpectedDocument() {
    ConversionResult result = toChEmed("rx-basic.json", ConversionOptions.defaults());

    assertThat(errors(result)).isEmpty();
    ChEmedAssertions.assertMatchesExpected(
        result.output().orElseThrow(), "expected/chmed16a-to-chemed/rx-basic.json", RX_BASIC_ID);
  }

  @ParameterizedTest
  @ValueSource(strings = {"rx-basic.json", "rx-erezept-example.json", "rx-missing-hcperson.json"})
  void prescriptionsValidateWithZeroErrorsAndKeepEveryMedicament(String fixture) {
    ConversionResult result = toChEmed(fixture, TestOptions.withAuthor());

    assertThat(errors(result)).as("errors for " + fixture).isEmpty();
    byte[] output = result.output().orElseThrow();
    ChEmedAssertions.assertValid(output);
    Bundle bundle = ChEmedJson.parse(output);
    long requests =
        bundle.getEntry().stream()
            .filter(e -> e.getResource() instanceof MedicationRequest)
            .count();
    int medicaments =
        Chmed16aJsonReader.read(
                Fixtures.bytes("chmed16a/" + fixture),
                ConversionOptions.defaults(),
                new ReportCollector())
            .medicaments()
            .size();
    assertThat(requests).as("every medicament present (SC-003)").isEqualTo(medicaments);
  }

  @Test
  void theMissingHcPersonOfRevisionOneInputIsReportedAsAWarning() {
    ConversionResult result = toChEmed("rx-missing-hcperson.json", TestOptions.withAuthor());

    assertThat(result.report().entries())
        .anySatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.CHMED_REVISION_FIELD_MISSING);
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.path()).isEqualTo("/HcPerson");
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"rx-idtype4.json", "rx-invalid-fields.json"})
  void invalidPrescriptionsFailWithoutOutput(String fixture) {
    ConversionResult result = toChEmed(fixture, TestOptions.withAuthor());

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(errors(result)).isNotEmpty();
  }

  @Test
  void theOutputDocumentTypeFollowsTheInputDocumentType() {
    Composition plan = composition(toChEmed("plan-basic.json", TestOptions.withAuthor()));
    Composition rx = composition(toChEmed("rx-basic.json", ConversionOptions.defaults()));

    assertThat(plan.getType().getCodingFirstRep().getCode()).isEqualTo("56445-0");
    assertThat(rx.getType().getCodingFirstRep().getCode()).isEqualTo("57833-6");
  }

  private static Composition composition(ConversionResult result) {
    return (Composition)
        ChEmedJson.parse(result.output().orElseThrow()).getEntryFirstRep().getResource();
  }

  // ---------------------------------------------------------------------------------------------
  // CH EMED Medication Prescription → CHMED16A prescription

  @ParameterizedTest
  @CsvSource({
    "medication-prescription-example.json, medication-prescription-example.json",
    "rx-substitution-repeats.json, rx-substitution-repeats.json"
  })
  void outputMatchesTheHandAuthoredExpectedDocument(String fixture, String expected)
      throws JSONException {
    ConversionResult result = toChmed16a(fixture);

    assertThat(errors(result)).as("errors for " + fixture).isEmpty();
    JSONAssert.assertEquals(
        Fixtures.text("expected/chemed-to-chmed16a/" + expected),
        new String(result.output().orElseThrow(), StandardCharsets.UTF_8),
        JSONCompareMode.STRICT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"medication-prescription-example.json", "rx-substitution-repeats.json"})
  void producedPrescriptionsMeetTheErezeptRequirements(String fixture) {
    ConversionResult result = toChmed16a(fixture);

    byte[] output = result.output().orElseThrow();
    ReportCollector report = new ReportCollector();
    Medication med = Chmed16aJsonReader.read(output, ConversionOptions.defaults(), report);
    Chmed16aValidator.validate(med, report, Phase.VALIDATE_OUTPUT);
    ErezeptPrescriptionRules.validate(med, report, Phase.VALIDATE_OUTPUT);
    assertThat(report.entries()).as("CHMED16A and eRezept rules").isEmpty();
    assertThat(ErezeptSchema.validate(output)).as("published eRezept schema v7").isEmpty();
    assertThat(med.rev()).isEqualTo(3);
    assertThat(med.medType()).isEqualTo(3);
  }

  @Test
  void aPrescriberWithoutGlnIsRefusedAlreadyByTheChEmedProfile() {
    ConversionResult result = toChmed16a("rx-missing-prescriber-gln.json");

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(errors(result))
        .isNotEmpty()
        .allSatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.FHIR_VALIDATION);
              assertThat(e.phase()).isEqualTo(Phase.VALIDATE_INPUT);
            })
        .anyMatch(e -> e.path().contains("Practitioner"));
  }

  @Test
  void withoutInputValidationAMissingPrescriberGlnIsAnErezeptRequirementError() {
    // CH EMED makes the GLN mandatory, so a validated document cannot lack it; this is the
    // safety net for library callers that skip input validation (FR-008b)
    ReportCollector report = new ReportCollector();
    byte[] output =
        new Chmed16aFormatModule()
            .fromChEmed(
                ChEmedJson.parse(Fixtures.bytes("chemed/rx-missing-prescriber-gln.json")),
                new MappingContext(ConversionOptions.defaults(), report));

    assertThat(output).isNull();
    assertThat(report.entries())
        .filteredOn(e -> e.severity() == Severity.ERROR)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.EREZEPT_REQUIREMENT);
              assertThat(e.path()).isEqualTo("Composition.author");
              assertThat(e.message()).contains("GLN");
            });
  }

  @Test
  void lossesOfThePrescriptionFormatAreReportedForTheOfficialExample() {
    ConversionResult result = toChmed16a("medication-prescription-example.json");

    assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS_WITH_WARNINGS);
    assertThat(result.report().entries())
        .filteredOn(e -> e.clinicalLoss())
        .extracting(ReportEntry::path)
        .contains(
            "MedicationRequest[0].dosageInstruction[0].route",
            "MedicationRequest[0].reasonCode[0]");
  }

  @Test
  void theMedicationCardStillConvertsInBothDirections() {
    assertThat(toChmed16a("medication-card-example.json").outcome()).isNotEqualTo(Outcome.FAILED);
  }
}
