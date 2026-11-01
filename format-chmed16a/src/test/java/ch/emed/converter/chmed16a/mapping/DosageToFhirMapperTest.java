package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.read;
import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.util.List;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Timing.EventTiming;
import org.junit.jupiter.api.Test;

class DosageToFhirMapperTest {

  private List<Dosage> map(String fixture, int medicament, MappingScope[] scopeOut) {
    Medication med = read(fixture);
    MappingScope scope = scope(med, ConversionOptions.defaults());
    scopeOut[0] = scope;
    Medicament m = med.medicaments().get(medicament);
    return DosageToFhirMapper.map(
        m.pos().get(0), m, "/Medicaments/" + medicament + "/Pos/0", "de-CH", scope);
  }

  @Test
  void splitDosageMorningEvening() {
    MappingScope[] scope = new MappingScope[1];
    List<Dosage> dosages = map("plan-basic.json", 0, scope);

    assertThat(dosages).hasSize(2);
    Dosage base = dosages.get(0);
    assertThat(base.getSequence()).isEqualTo(1);
    assertThat(base.getTiming().getRepeat().getWhen().get(0).getValue())
        .isEqualTo(EventTiming.MORN);
    assertThat(base.getTiming().getRepeat().getBoundsPeriod().getStartElement().getValueAsString())
        .isEqualTo("2026-10-01");
    assertThat(base.getRoute().getCodingFirstRep().getCode()).isEqualTo("20053000");
    assertThat(base.getPatientInstruction()).isEqualTo("Mit Wasser einnehmen");
    assertThat(base.getDoseAndRateFirstRep().getDoseQuantity().getValue())
        .isEqualByComparingTo("1");
    assertThat(base.getDoseAndRateFirstRep().getDoseQuantity().getUnit()).isEqualTo("STK");
    Dosage evening = dosages.get(1);
    assertThat(evening.getSequence()).isEqualTo(2);
    assertThat(evening.getTiming().getRepeat().getWhen().get(0).getValue())
        .isEqualTo(EventTiming.EVE);
    assertThat(evening.getDoseAndRateFirstRep().getDoseQuantity().getValue())
        .isEqualByComparingTo("0.5");
    assertThat(evening.hasRoute()).isFalse();
    assertThat(evening.getTiming().getRepeat().hasBounds()).isFalse();
  }

  @Test
  void singleDoseHasNoSequenceAndKeepsEndDate() {
    MappingScope[] scope = new MappingScope[1];
    List<Dosage> dosages = map("plan-basic.json", 1, scope);

    assertThat(dosages)
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.hasSequence()).isFalse();
              assertThat(
                      d.getTiming()
                          .getRepeat()
                          .getBoundsPeriod()
                          .getEndElement()
                          .getValueAsString())
                  .isEqualTo("2026-12-31");
            });
  }

  @Test
  void reserveBecomesAsNeeded() {
    MappingScope[] scope = new MappingScope[1];
    List<Dosage> dosages = map("plan-reserve.json", 0, scope);

    assertThat(dosages.get(0).getAsNeededBooleanType().booleanValue()).isTrue();
  }

  @Test
  void dailyTakingTimesWithEqualDoseBecomeTimeOfDay() {
    MappingScope[] scope = new MappingScope[1];
    List<Dosage> dosages = map("plan-taking-times.json", 0, scope);

    assertThat(dosages)
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.getTiming().getRepeat().getTimeOfDay())
                  .extracting(t -> t.getValue())
                  .containsExactly("08:00:00", "20:00:00");
              assertThat(d.getDoseAndRateFirstRep().getDoseQuantity().getValue())
                  .isEqualByComparingTo("1");
            });
    assertThat(scope[0].report().entries())
        .noneMatch(e -> e.code().equals(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK));
  }

  @Test
  void unrepresentableTakingTimesFallBackToFreeTextWithClinicalLoss() {
    MappingScope[] scope = new MappingScope[1];
    List<Dosage> dosages = map("plan-taking-times.json", 1, scope);

    assertThat(dosages)
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.getText())
                  .contains("08:00")
                  .contains("20:00")
                  .contains("2")
                  .contains("STK");
              assertThat(d.hasDoseAndRate()).isFalse();
            });
    ReportEntry entry =
        scope[0].report().entries().stream()
            .filter(e -> e.code().equals(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK))
            .findFirst()
            .orElseThrow();
    assertThat(entry.severity()).isEqualTo(Severity.WARNING);
    assertThat(entry.clinicalLoss()).isTrue();
  }

  @Test
  void weeklyCycleFallsBackToFreeText() {
    MappingScope[] scope = new MappingScope[1];
    map("plan-cycle.json", 0, scope);

    assertThat(scope[0].report().entries())
        .anyMatch(e -> e.code().equals(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK));
  }

  @Test
  void allZeroDosesGiveBaseEntryWithoutDose() {
    Medication med = read("official/readme-example-medication-plan.json");
    MappingScope scope = scope(med, ConversionOptions.defaults());
    Medicament m = med.medicaments().get(0);

    List<Dosage> dosages =
        DosageToFhirMapper.map(m.pos().get(0), m, "/Medicaments/0/Pos/0", "de-CH", scope);

    assertThat(dosages)
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.hasDoseAndRate()).isFalse();
              assertThat(d.getTiming().getRepeat().hasBoundsPeriod()).isTrue();
            });
    assertThat(scope.report().entries())
        .anyMatch(
            e ->
                e.code().equals(ReasonCode.DERIVED_VALUE)
                    || e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT));
  }

  @Test
  void unknownRouteIsReportedWithClinicalLoss() {
    Medication med =
        MappingTestSupport.parse(
            """
            {"Id":"0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e01","MedType":1,"Auth":"7601000000001",
             "Dt":"2026-10-01T09:30:00+02:00",
             "Patient":{"FName":"Hans","LName":"Muster","BDt":"1950-03-21","Gender":1,"Lng":"de"},
             "Medicaments":[{"Id":"7680521101306","IdType":2,"Unit":"STK","AutoMed":0,"Roa":"XYZ",
               "Pos":[{"DtFrom":"2026-10-01","D":[1,0,0,0]}]}]}
            """);
    MappingScope scope = scope(med, ConversionOptions.defaults());
    Medicament m = med.medicaments().get(0);

    List<Dosage> dosages =
        DosageToFhirMapper.map(m.pos().get(0), m, "/Medicaments/0/Pos/0", "de-CH", scope);

    assertThat(dosages.get(0).hasRoute()).isFalse();
    assertThat(scope.report().entries())
        .anyMatch(
            e ->
                e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT)
                    && e.path().equals("/Medicaments/0/Roa")
                    && e.clinicalLoss());
  }
}
