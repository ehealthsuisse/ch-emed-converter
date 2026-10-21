package ch.emed.converter.chmed16a.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.Chmed16aJsonReader;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Field rules of data-model.md §2 (medication plans and prescriptions). */
class Chmed16aValidatorTest {

  private static final String BASE =
      """
      {"Id":"0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e01","MedType":1,"Auth":"7601000000001",
       "Dt":"2026-10-01T09:30:00+02:00",
       "Patient":{"FName":"Hans","LName":"Muster","BDt":"1950-03-21","Gender":1,"Lng":"de"},
       "Medicaments":[%s]}
      """;

  private static final String MED =
      "{\"Id\":\"7680521101306\",\"IdType\":2,\"Unit\":\"STK\",\"AutoMed\":0,"
          + "\"Pos\":[{\"DtFrom\":\"2026-10-01\",\"D\":[1,0,0,0]}]}";

  private List<ReportEntry> validate(String json) {
    ReportCollector report = new ReportCollector();
    Medication med =
        Chmed16aJsonReader.read(
            json.getBytes(StandardCharsets.UTF_8), ConversionOptions.defaults(), report);
    assertThat(med).as("parsed").isNotNull();
    Chmed16aValidator.validate(med, report);
    return report.entries();
  }

  private List<ReportEntry> validateMedicament(String medicament) {
    return validate(BASE.formatted(medicament));
  }

  private static List<String> errorPaths(List<ReportEntry> entries) {
    return entries.stream()
        .filter(e -> e.severity() == Severity.ERROR)
        .map(ReportEntry::path)
        .toList();
  }

  @Test
  void validPlanHasNoErrors() {
    assertThat(errorPaths(validateMedicament(MED))).isEmpty();
    assertThat(errorPaths(validate(Fixtures.text("chmed16a/plan-basic.json")))).isEmpty();
    assertThat(
            errorPaths(
                validate(Fixtures.text("chmed16a/official/readme-example-medication-plan.json"))))
        .isEmpty();
  }

  @Test
  void idMustBeUuid() {
    assertThat(
            errorPaths(
                validate(
                    BASE.formatted(MED).replace("0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e01", "abc"))))
        .contains("/Id");
  }

  @Test
  void medType2IsUnsupportedDocumentType() {
    List<ReportEntry> entries = validate(Fixtures.text("chmed16a/chmed16a-pmc.json"));
    assertThat(entries)
        .anyMatch(
            e ->
                e.code().equals(ReasonCode.UNSUPPORTED_DOCUMENT_TYPE)
                    && e.severity() == Severity.ERROR);
  }

  @Test
  void medTypeOtherThan123IsInvalid() {
    assertThat(errorPaths(validate(BASE.formatted(MED).replace("\"MedType\":1", "\"MedType\":5"))))
        .contains("/MedType");
  }

  @Test
  void dtMustBeIsoDateTimeWithOffset() {
    assertThat(
            errorPaths(
                validate(
                    BASE.formatted(MED).replace("2026-10-01T09:30:00+02:00", "01.10.2026 09:30"))))
        .contains("/Dt");
  }

  @Test
  void patientLanguageRequiredForPlansAndGenderRestricted() {
    assertThat(errorPaths(validate(BASE.formatted(MED).replace(",\"Lng\":\"de\"", ""))))
        .contains("/Patient/Lng");
    assertThat(errorPaths(validate(BASE.formatted(MED).replace("\"Gender\":1", "\"Gender\":3"))))
        .contains("/Patient/Gender");
    assertThat(
            errorPaths(
                validate(
                    BASE.formatted(MED)
                        .replace("\"BDt\":\"1950-03-21\"", "\"BDt\":\"21.03.1950\""))))
        .contains("/Patient/BDt");
  }

  @Test
  void idTypeMustBeOneToFour() {
    assertThat(errorPaths(validateMedicament(MED.replace("\"IdType\":2", "\"IdType\":5"))))
        .contains("/Medicaments/0/IdType");
  }

  @Test
  void invalidGtinCheckDigitIsWarningAndCarried() {
    List<ReportEntry> entries = validateMedicament(MED.replace("7680521101306", "7680521101307"));
    assertThat(errorPaths(entries)).isEmpty();
    assertThat(entries)
        .anyMatch(
            e ->
                e.severity() == Severity.WARNING
                    && e.code().equals(ReasonCode.CHMED_INVALID_FIELD)
                    && e.path().equals("/Medicaments/0/Id"));
  }

  @Test
  void missingAutoMedInPlanIsWarning() {
    List<ReportEntry> entries = validateMedicament(MED.replace("\"AutoMed\":0,", ""));
    assertThat(errorPaths(entries)).isEmpty();
    assertThat(entries)
        .anyMatch(
            e -> e.severity() == Severity.WARNING && e.path().equals("/Medicaments/0/AutoMed"));
  }

  @Test
  void gtinMustHave13DigitsAndPharmacodeUpTo7() {
    assertThat(errorPaths(validateMedicament(MED.replace("7680521101306", "12345"))))
        .contains("/Medicaments/0/Id");
    assertThat(
            errorPaths(
                validateMedicament(
                    MED.replace("\"IdType\":2", "\"IdType\":3")
                        .replace("7680521101306", "12345678"))))
        .contains("/Medicaments/0/Id");
    assertThat(
            errorPaths(
                validateMedicament(
                    MED.replace("\"IdType\":2", "\"IdType\":3")
                        .replace("7680521101306", "4461382"))))
        .isEmpty();
  }

  @Test
  void posologyRules() {
    assertThat(errorPaths(validateMedicament(MED.replace("\"DtFrom\":\"2026-10-01\",", ""))))
        .contains("/Medicaments/0/Pos/0/DtFrom");
    assertThat(
            errorPaths(
                validateMedicament(
                    MED.replace(
                        "\"DtFrom\":\"2026-10-01\"",
                        "\"DtFrom\":\"2026-10-01\",\"DtTo\":\"2026-09-01\""))))
        .contains("/Medicaments/0/Pos/0/DtTo");
    assertThat(errorPaths(validateMedicament(MED.replace("[1,0,0,0]", "[1,0,0,0,1]"))))
        .contains("/Medicaments/0/Pos/0/D");
    List<ReportEntry> both =
        validateMedicament(
            MED.replace("\"D\":[1,0,0,0]", "\"D\":[1,0,0,0],\"TT\":[{\"Off\":0,\"DoFrom\":1}]"));
    assertThat(errorPaths(both)).isEmpty();
    assertThat(both)
        .anyMatch(
            e -> e.severity() == Severity.WARNING && e.path().equals("/Medicaments/0/Pos/0/D"));
  }

  @Test
  void zeroOneFlags() {
    assertThat(errorPaths(validateMedicament(MED.replace("\"AutoMed\":0", "\"AutoMed\":2"))))
        .contains("/Medicaments/0/AutoMed");
    assertThat(
            errorPaths(
                validateMedicament(
                    MED.replace(
                        "\"DtFrom\":\"2026-10-01\"", "\"DtFrom\":\"2026-10-01\",\"InRes\":3"))))
        .contains("/Medicaments/0/Pos/0/InRes");
  }

  @Test
  void unitRequiredWhenPosologyDefinedInPlans() {
    assertThat(errorPaths(validateMedicament(MED.replace("\"Unit\":\"STK\",", ""))))
        .contains("/Medicaments/0/Unit");
  }

  // -------------------------------------------------------------------------------------------
  // prescriptions (MedType 3, revision 3)

  private static final String RX =
      """
      {"Id":"7b1f3c1e-9d54-4a0e-8e0c-2a6c1f9e8d02","MedType":3,"Auth":"7601000000001",
       "Dt":"2026-10-01T09:30:00+02:00","rev":3,
       "Patient":{"FName":"Hans","LName":"Muster","BDt":"1950-03-21","Gender":1},
       "HcPerson":{"Gln":"7601000000001","FName":"Thea","LName":"Test"},
       "HcOrg":{"Name":"Praxis Test","Street":"Testgasse 1","Zip":"3000","City":"Bern"},
       "Medicaments":[%s]}
      """;

  private static final String RX_MED =
      "{\"Id\":\"7680521101306\",\"IdType\":2,\"Unit\":\"STK\","
          + "\"Pos\":[{\"DtTo\":\"2026-12-31\",\"D\":[1,0,0,0]}],"
          + "\"Rep\":3,\"Subs\":1,\"NbPack\":2,\"IndC\":\"12345.01\"}";

  private List<ReportEntry> validateRx(String medicament) {
    return validate(RX.formatted(medicament));
  }

  private static List<ReportEntry> only(List<ReportEntry> entries, String path) {
    return entries.stream().filter(e -> e.path().equals(path)).toList();
  }

  @Test
  void validPrescriptionsHaveNoErrors() {
    assertThat(errorPaths(validateRx(RX_MED))).isEmpty();
    for (String fixture :
        List.of("rx-basic.json", "rx-erezept-example.json", "rx-missing-hcperson.json")) {
      assertThat(errorPaths(validate(Fixtures.text("chmed16a/" + fixture)))).as(fixture).isEmpty();
    }
  }

  @Test
  void aPrescriptionDoesNotNeedDtFromLngOrAutoMed() {
    List<ReportEntry> entries = validateRx(RX_MED);

    assertThat(entries).noneMatch(e -> e.path().endsWith("/DtFrom") || e.path().endsWith("/Lng"));
    assertThat(entries).noneMatch(e -> e.path().endsWith("/AutoMed"));
  }

  @Test
  void authOfAPrescriptionMustBeAGlnOfThirteenDigits() {
    assertThat(
            errorPaths(
                validate(
                    RX.formatted(RX_MED)
                        .replace("\"Auth\":\"7601000000001\"", "\"Auth\":\"patient\""))))
        .contains("/Auth");
    assertThat(
            errorPaths(
                validate(
                    RX.formatted(RX_MED)
                        .replace("\"Auth\":\"7601000000001\"", "\"Auth\":\"760100000000\""))))
        .contains("/Auth");
  }

  @Test
  void missingHcPersonAndHcOrgIsAWarningForRevisionOneInput() {
    List<ReportEntry> entries = validate(Fixtures.text("chmed16a/rx-missing-hcperson.json"));

    for (String path : List.of("/HcPerson", "/HcOrg")) {
      assertThat(only(entries, path))
          .singleElement()
          .satisfies(
              e -> {
                assertThat(e.severity()).isEqualTo(Severity.WARNING);
                assertThat(e.code()).isEqualTo(ReasonCode.CHMED_REVISION_FIELD_MISSING);
              });
    }
    assertThat(errorPaths(entries)).isEmpty();
  }

  @Test
  void missingHcPersonAndHcOrgIsAnErrorFromRevisionTwo() {
    String json =
        Fixtures.text("chmed16a/rx-missing-hcperson.json")
            .replace("\"MedType\":3", "\"MedType\":3,\"rev\":2");

    assertThat(errorPaths(validate(json))).contains("/HcPerson", "/HcOrg");
  }

  @Test
  void missingHcPersonAndHcOrgIsAnErrorOnOutputWhateverTheRevision() {
    ReportCollector report = new ReportCollector();
    Medication med =
        Chmed16aJsonReader.read(
            Fixtures.bytes("chmed16a/rx-missing-hcperson.json"),
            ConversionOptions.defaults(),
            report);

    Chmed16aValidator.validate(med, report, Phase.VALIDATE_OUTPUT);

    assertThat(report.entries())
        .filteredOn(e -> e.severity() == Severity.ERROR)
        .extracting(ReportEntry::path)
        .contains("/HcPerson", "/HcOrg");
    assertThat(report.entries()).allMatch(e -> e.phase() == Phase.VALIDATE_OUTPUT);
  }

  @Test
  void healthcarePersonAndOrganisationFieldsAreRequiredWhenPresent() {
    String json =
        RX.formatted(RX_MED)
            .replace("\"Gln\":\"7601000000001\"", "\"Gln\":\"12\"")
            .replace("\"LName\":\"Test\"", "\"LName\":\"\"")
            .replace("\"City\":\"Bern\"}", "\"City\":\"\"}");

    assertThat(errorPaths(validate(json)))
        .contains("/HcPerson/Gln", "/HcPerson/LName", "/HcOrg/City");
  }

  @Test
  void repAtMostTwoDigitsSubsZeroOrOneNbPackAtMostTenDigits() {
    assertThat(errorPaths(validateRx(RX_MED.replace("\"Rep\":3", "\"Rep\":123"))))
        .contains("/Medicaments/0/Rep");
    assertThat(errorPaths(validateRx(RX_MED.replace("\"Subs\":1", "\"Subs\":2"))))
        .contains("/Medicaments/0/Subs");
    assertThat(errorPaths(validateRx(RX_MED.replace("\"NbPack\":2", "\"NbPack\":12345678901"))))
        .contains("/Medicaments/0/NbPack");
    assertThat(errorPaths(validate(Fixtures.text("chmed16a/rx-invalid-fields.json"))))
        .contains("/Medicaments/0/Rep", "/Medicaments/0/Subs", "/Medicaments/0/IndC");
  }

  @Test
  void indicationCodeFormat() {
    assertThat(errorPaths(validateRx(RX_MED.replace("12345.01", "1234.5"))))
        .contains("/Medicaments/0/IndC");
    assertThat(errorPaths(validateRx(RX_MED.replace("12345.01", "00000.00")))).isEmpty();
  }

  @Test
  void productNumbersAreNotAllowedInPrescriptions() {
    assertThat(errorPaths(validate(Fixtures.text("chmed16a/rx-idtype4.json"))))
        .contains("/Medicaments/0/IdType");
  }

  @Test
  void aPrescriptionHasAtMostOnePosologyAndNeedsAUnitForIt() {
    String two =
        RX_MED.replace(
            "\"Pos\":[{\"DtTo\":\"2026-12-31\",\"D\":[1,0,0,0]}]",
            "\"Pos\":[{\"D\":[1,0,0,0]},{\"D\":[0,1,0,0]}]");

    assertThat(errorPaths(validateRx(two))).contains("/Medicaments/0/Pos");
    assertThat(errorPaths(validateRx(RX_MED.replace("\"Unit\":\"STK\",", ""))))
        .contains("/Medicaments/0/Unit");
  }

  @Test
  void prescriptionOnlyFieldsInAPlanAreWarnings() {
    List<ReportEntry> entries =
        validateMedicament(MED.replace("\"AutoMed\":0", "\"AutoMed\":0,\"Rep\":3,\"NbPack\":1"));

    assertThat(errorPaths(entries)).isEmpty();
    assertThat(only(entries, "/Medicaments/0/Rep"))
        .singleElement()
        .satisfies(e -> assertThat(e.severity()).isEqualTo(Severity.WARNING));
    assertThat(only(entries, "/Medicaments/0/NbPack")).hasSize(1);
  }
}
