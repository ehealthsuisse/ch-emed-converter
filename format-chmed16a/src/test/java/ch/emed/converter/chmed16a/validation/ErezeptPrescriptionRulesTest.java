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

/** eRezept Schweiz data requirements (data-model.md §4, mapping spec M-ERX-01..05). */
class ErezeptPrescriptionRulesTest {

  private static Medication read(String json) {
    ReportCollector report = new ReportCollector();
    Medication med =
        Chmed16aJsonReader.read(
            json.getBytes(StandardCharsets.UTF_8), ConversionOptions.defaults(), report);
    assertThat(med).isNotNull();
    return med;
  }

  private static List<ReportEntry> check(Medication med, Phase phase) {
    ReportCollector report = new ReportCollector();
    ErezeptPrescriptionRules.validate(med, report, phase);
    return report.entries();
  }

  private static List<ReportEntry> output(String json) {
    return check(read(json), Phase.VALIDATE_OUTPUT);
  }

  private static String rx() {
    return Fixtures.text("chmed16a/rx-basic.json");
  }

  private static List<String> errorPaths(List<ReportEntry> entries) {
    return entries.stream()
        .filter(e -> e.severity() == Severity.ERROR)
        .map(ReportEntry::path)
        .toList();
  }

  @Test
  void aCompletePrescriptionMeetsTheRequirements() {
    assertThat(output(rx())).isEmpty();
    assertThat(output(Fixtures.text("chmed16a/rx-erezept-example.json")))
        .isNotEmpty(); // no HcPerson/HcOrg
  }

  @Test
  void violationsOnOutputAreErezeptRequirementErrorsNamingThePath() {
    List<ReportEntry> entries = output(Fixtures.text("chmed16a/rx-erezept-example.json"));

    assertThat(entries)
        .filteredOn(e -> e.severity() == Severity.ERROR)
        .extracting(ReportEntry::path)
        .contains("/HcPerson", "/HcOrg");
    assertThat(entries)
        .allSatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.EREZEPT_REQUIREMENT);
              assertThat(e.phase()).isEqualTo(Phase.VALIDATE_OUTPUT);
            });
  }

  @Test
  void violationsOnInputAreWarnings() {
    List<ReportEntry> entries =
        check(read(Fixtures.text("chmed16a/rx-erezept-example.json")), Phase.VALIDATE_INPUT);

    assertThat(entries)
        .isNotEmpty()
        .allSatisfy(e -> assertThat(e.severity()).isEqualTo(Severity.WARNING));
    assertThat(entries).allSatisfy(e -> assertThat(e.phase()).isEqualTo(Phase.VALIDATE_INPUT));
  }

  @Test
  void documentRules() {
    assertThat(errorPaths(output(rx().replace("\"MedType\":3", "\"MedType\":1"))))
        .contains("/MedType");
    assertThat(
            errorPaths(
                output(
                    rx().replace(
                            "7b1f3c1e-9d54-4a0e-8e0c-2a6c1f9e8d02",
                            "7B1F3C1E-9D54-4A0E-8E0C-2A6C1F9E8D02"))))
        .contains("/Id");
    assertThat(errorPaths(output(rx().replace("\"Dt\":\"2026-10-01T09:30:00+02:00\",", ""))))
        .contains("/Dt");
  }

  @Test
  void prescriberRules() {
    assertThat(
            errorPaths(output(rx().replace("\"Auth\":\"7601000000001\"", "\"Auth\":\"patient\""))))
        .contains("/Auth");
    assertThat(
            errorPaths(
                output(rx().replace("\"Auth\":\"7601000000001\"", "\"Auth\":\"7601000000002\""))))
        .as("Auth must equal HcPerson.Gln")
        .contains("/Auth");
    assertThat(errorPaths(output(rx().replace("\"Gln\":\"7601000000001\"", "\"Gln\":\"123\""))))
        .contains("/HcPerson/Gln");
    assertThat(errorPaths(output(rx().replace("\"FName\":\"Thea\",", ""))))
        .contains("/HcPerson/FName");
    assertThat(errorPaths(output(rx().replace("\"Street\":\"Testgasse 1\",", ""))))
        .contains("/HcOrg/Street");
  }

  @Test
  void patientRules() {
    assertThat(errorPaths(output(rx().replace("\"BDt\":\"1950-03-21\"", "\"BDt\":\"\""))))
        .contains("/Patient/BDt");
    assertThat(
            errorPaths(
                output(
                    rx().replace(
                            "\"Zip\":\"3000\",\"City\":\"Bern\",\"Phone\"",
                            "\"Zip\":\"D-79539\",\"City\":\"Bern\",\"Phone\""))))
        .contains("/Patient/Zip");
    assertThat(errorPaths(output(rx().replace("\"Gender\":1", "\"Gender\":3"))))
        .contains("/Patient/Gender");
  }

  @Test
  void medicamentRules() {
    assertThat(errorPaths(output(Fixtures.text("chmed16a/rx-idtype4.json"))))
        .contains("/Medicaments/0/IdType");
    assertThat(errorPaths(output(rx().replace("\"Rep\":3", "\"Rep\":25"))))
        .contains("/Medicaments/0/Rep");
    assertThat(errorPaths(output(rx().replace("\"Rep\":3", "\"Rep\":24")))).isEmpty();
    assertThat(errorPaths(output(rx().replace("\"Subs\":1", "\"Subs\":2"))))
        .contains("/Medicaments/0/Subs");
    assertThat(errorPaths(output(rx().replace("12345.01", "1234.5"))))
        .contains("/Medicaments/0/IndC");
    assertThat(errorPaths(output(rx().replace("\"Unit\":\"STK\",", ""))))
        .contains("/Medicaments/0/Unit");
    assertThat(errorPaths(output(rx().replace("\"D\":[1,0,0.5,0]", "\"D\":[]"))))
        .contains("/Medicaments/0/Pos/0/D");
  }

  @Test
  void aPrescriptionNeedsAtLeastOneMedicament() {
    String empty =
        rx().replaceAll(
                "\"Medicaments\":\\[.*?\\}\\],\"MedType\"", "\"Medicaments\":[],\"MedType\"");

    assertThat(errorPaths(output(empty))).contains("/Medicaments");
  }

  @Test
  void freeTextMedicamentsAreAcceptedBySchemaV7() {
    // finding of task T094: IdType 1 is part of the published eRezept schema (enum 1, 2, 3)
    assertThat(errorPaths(output(rx()))).isEmpty();
    assertThat(read(rx()).medicaments().get(1).idType()).isEqualTo(1);
  }

  @Test
  void zsrAtMostSevenCharacters() {
    assertThat(
            errorPaths(
                output(
                    rx().replace(
                            "\"Zsr\":\"H123456\",\"Phone\"", "\"Zsr\":\"H1234567\",\"Phone\""))))
        .contains("/HcOrg/Zsr");
  }
}
