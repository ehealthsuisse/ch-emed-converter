package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.CARD;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.document;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.entries;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.mapping.reverse.DosageFromFhirMapper.Result;
import ch.emed.converter.chmed16a.model.Posology;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.Severity;
import java.math.BigDecimal;
import java.util.List;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Timing.DayOfWeek;
import org.hl7.fhir.r4.model.Timing.EventTiming;
import org.junit.jupiter.api.Test;

class DosageFromFhirMapperTest {

  private static final String SNOMED = "http://snomed.info/sct";
  private static final String UCUM = "http://unitsofmeasure.org";

  private final ReverseScope scope = scope(document(CARD));

  private Result map(Dosage... dosages) {
    return DosageFromFhirMapper.map(List.of(dosages), "MedicationStatement[0]", scope);
  }

  private static Quantity tablets(double value) {
    return new Quantity()
        .setValue(BigDecimal.valueOf(value))
        .setUnit("Tablet (unit of presentation)")
        .setSystem(SNOMED)
        .setCode("732936001");
  }

  private static Dosage dosage(double dose, EventTiming... when) {
    Dosage d = new Dosage();
    for (EventTiming w : when) {
      d.getTiming().getRepeat().addWhen(w);
    }
    d.addDoseAndRate().setDose(tablets(dose));
    return d;
  }

  private static Dosage withStart(Dosage d, String start) {
    d.getTiming().getRepeat().setBounds(new Period().setStartElement(new DateTimeType(start)));
    return d;
  }

  private static void assertDoses(Posology p, double... expected) {
    assertThat(p.d()).hasSameSizeAs(expected);
    for (int i = 0; i < expected.length; i++) {
      assertThat(p.d().get(i)).isEqualByComparingTo(BigDecimal.valueOf(expected[i]));
    }
  }

  @Test
  void splitDosageSharesTheBoundsOfTheBaseEntry() {
    Dosage base = withStart(dosage(1, EventTiming.MORN).setSequence(1), "2012-02-04");
    base.setPatientInstruction("-");
    base.setRoute(
        new CodeableConcept(new Coding("urn:oid:0.4.0.127.0.16.1.1.2.1", "20053000", "Oral use")));
    Dosage evening = dosage(0.5, EventTiming.EVE).setSequence(2);

    Result result = map(base, evening);

    assertThat(result.pos())
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.dtFrom()).isEqualTo("2012-02-04");
              assertThat(p.dtTo()).isNull();
              assertDoses(p, 1, 0, 0.5, 0);
              assertThat(p.cyDu()).isNull();
              assertThat(p.tt()).isEmpty();
            });
    assertThat(result.unit()).isEqualTo("STK");
    assertThat(result.appInstr()).isEqualTo("-");
    assertThat(result.roa()).isEqualTo("PO");
    assertThat(entries(scope, e -> e.clinicalLoss())).isEmpty();
  }

  @Test
  void severalWhenCodesOfOneDosageShareTheDose() {
    Result result = map(withStart(dosage(1, EventTiming.MORN, EventTiming.EVE), "2012-02-04"));

    assertDoses(result.pos().get(0), 1, 0, 1, 0);
  }

  @Test
  void allFourDaySlotsAreMapped() {
    Result result =
        map(
            withStart(dosage(1, EventTiming.MORN).setSequence(1), "2012-02-04"),
            dosage(2, EventTiming.NOON).setSequence(2),
            dosage(3, EventTiming.EVE).setSequence(3),
            dosage(4, EventTiming.NIGHT).setSequence(4));

    assertDoses(result.pos().get(0), 1, 2, 3, 4);
  }

  @Test
  void boundsEndBecomesDtTo() {
    Dosage d = dosage(1, EventTiming.MORN);
    d.getTiming()
        .getRepeat()
        .setBounds(
            new Period()
                .setStartElement(new DateTimeType("2012-02-04"))
                .setEndElement(new DateTimeType("2012-03-31")));

    Posology p = map(d).pos().get(0);

    assertThat(p.dtFrom()).isEqualTo("2012-02-04");
    assertThat(p.dtTo()).isEqualTo("2012-03-31");
  }

  @Test
  void dateTimeBoundsKeepTheCalendarDateAndReportTheLostTime() {
    Posology p =
        map(withStart(dosage(1, EventTiming.MORN), "2012-02-04T23:30:00+01:00")).pos().get(0);

    assertThat(p.dtFrom()).isEqualTo("2012-02-04");
    assertThat(entries(scope, e -> e.path().endsWith("boundsPeriod.start")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
              assertThat(e.clinicalLoss()).isFalse();
            });
  }

  @Test
  void asNeededBecomesInReserve() {
    Dosage d = withStart(dosage(1, EventTiming.MORN), "2012-02-04");
    d.setAsNeeded(new BooleanType(true));

    assertThat(map(d).pos().get(0).inRes()).isEqualTo(1);
  }

  @Test
  void otherRoutesAreOmittedWithClinicalLossWarning() {
    Dosage d = withStart(dosage(1, EventTiming.MORN), "2012-02-04");
    d.setRoute(
        new CodeableConcept(
            new Coding("urn:oid:0.4.0.127.0.16.1.1.2.1", "20066000", "Subcutaneous use")));

    Result result = map(d);

    assertThat(result.roa()).isNull();
    assertThat(entries(scope, e -> e.path().endsWith("route")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).isTrue();
            });
  }

  @Test
  void unitsAreMappedByCodeInverseToTheForwardTable() {
    Object[][] cases = {
      {SNOMED, "767525000", "STK"},
      {SNOMED, "732936001", "STK"},
      {SNOMED, "732994000", "TR"},
      {SNOMED, "732981002", "HUB"},
      {UCUM, "mg", "MG"},
      {UCUM, "g", "G"},
      {UCUM, "ug", "UG"},
      {UCUM, "mL", "ML"},
      {UCUM, "L", "L"},
      {UCUM, "[iU]", "IE"},
      {UCUM, "mmol", "MMOL"}
    };
    for (Object[] c : cases) {
      Dosage d = withStart(new Dosage(), "2012-02-04");
      d.getTiming().getRepeat().addWhen(EventTiming.MORN);
      d.addDoseAndRate()
          .setDose(new Quantity().setValue(2).setSystem((String) c[0]).setCode((String) c[1]));

      assertThat(map(d).unit()).as(c[1].toString()).isEqualTo(c[2]);
    }
  }

  @Test
  void unknownUnitFallsBackToText() {
    Dosage d = withStart(new Dosage(), "2012-02-04");
    d.getTiming().getRepeat().addWhen(EventTiming.MORN);
    d.addDoseAndRate().setDose(new Quantity().setValue(2).setSystem(SNOMED).setCode("999999"));

    Result result = map(d);

    assertThat(result.unit()).isNull();
    assertThat(result.pos()).as("a posology needs a unit").isEmpty();
    assertThat(result.appInstr()).isEqualTo("dose=2 999999; when=MORN; from=2012-02-04");
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK)))
        .singleElement()
        .satisfies(e -> assertThat(e.clinicalLoss()).isTrue());
  }

  @Test
  void dayOfWeekFallsBackToTextKeepingTheDates() {
    Dosage d = withStart(dosage(1, EventTiming.MORN), "2012-02-04");
    d.getTiming().getRepeat().addDayOfWeek(DayOfWeek.MON).addDayOfWeek(DayOfWeek.THU);
    d.setPatientInstruction("Nur an zwei Wochentagen");

    Result result = map(d);

    assertThat(result.pos())
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.dtFrom()).isEqualTo("2012-02-04");
              assertThat(p.d()).isEmpty();
            });
    assertThat(result.unit()).isEqualTo("STK");
    assertThat(result.appInstr())
        .isEqualTo("Nur an zwei Wochentagen; dose=1 STK; when=MORN; dayOfWeek=mon,thu");
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK)))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).isTrue();
              assertThat(e.path()).isEqualTo("MedicationStatement[0].dosage[0]");
            });
  }

  @Test
  void doseRangeFallsBackToText() {
    Dosage d = withStart(new Dosage(), "2012-02-04");
    d.getTiming().getRepeat().addWhen(EventTiming.MORN);
    d.addDoseAndRate().setDose(new Range().setLow(tablets(1)).setHigh(tablets(2)));

    Result result = map(d);

    assertThat(result.unit()).isEqualTo("STK");
    assertThat(result.appInstr()).isEqualTo("dose=1-2 STK; when=MORN");
    assertThat(result.pos().get(0).d()).isEmpty();
  }

  @Test
  void freeTextOnlyDosageIsCarriedAsInstruction() {
    Dosage d = withStart(new Dosage(), "2012-02-04");
    d.setText("Nach Schema des Hausarztes");

    Result result = map(d);

    assertThat(result.appInstr()).isEqualTo("Nach Schema des Hausarztes; from=2012-02-04");
    assertThat(result.unit()).isNull();
    assertThat(result.pos()).as("a posology needs a unit").isEmpty();
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK)))
        .hasSize(1);
  }

  @Test
  void aSlotUsedTwiceCannotBeExpressedExactly() {
    Result result =
        map(
            withStart(dosage(1, EventTiming.MORN).setSequence(1), "2012-02-04"),
            dosage(2, EventTiming.MORN).setSequence(2));

    assertThat(result.pos().get(0).d()).isEmpty();
    assertThat(result.appInstr()).contains("when=MORN");
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK)))
        .isNotEmpty()
        .allSatisfy(e -> assertThat(e.clinicalLoss()).isTrue());
  }

  @Test
  void entriesWithDifferentBoundsBecomeSeparatePosologies() {
    Dosage first = dosage(1, EventTiming.MORN);
    first
        .getTiming()
        .getRepeat()
        .setBounds(
            new Period()
                .setStartElement(new DateTimeType("2012-02-04"))
                .setEndElement(new DateTimeType("2012-02-10")));
    Dosage second = withStart(dosage(2, EventTiming.MORN), "2012-02-11");

    Result result = map(first, second);

    assertThat(result.pos()).hasSize(2);
    assertThat(result.pos().get(0).dtFrom()).isEqualTo("2012-02-04");
    assertThat(result.pos().get(0).dtTo()).isEqualTo("2012-02-10");
    assertDoses(result.pos().get(0), 1, 0, 0, 0);
    assertThat(result.pos().get(1).dtFrom()).isEqualTo("2012-02-11");
    assertDoses(result.pos().get(1), 2, 0, 0, 0);
  }

  @Test
  void conflictingUnitsFallBackToText() {
    Dosage mg = withStart(new Dosage(), "2012-02-04");
    mg.getTiming().getRepeat().addWhen(EventTiming.MORN);
    mg.addDoseAndRate().setDose(new Quantity().setValue(5).setSystem(UCUM).setCode("mg"));
    mg.setSequence(1);
    Dosage tablet = dosage(1, EventTiming.EVE).setSequence(2);

    Result result = map(mg, tablet);

    assertThat(result.unit()).isNull();
    assertThat(result.pos()).isEmpty();
    assertThat(result.appInstr()).contains("dose=5 MG").contains("dose=1 STK");
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK)))
        .isNotEmpty();
  }

  @Test
  void timeOfDayWithEqualDosesBecomesTakingTimes() {
    Dosage d = withStart(new Dosage(), "2012-02-04");
    d.getTiming().getRepeat().addTimeOfDayElement().setValue("08:00:00");
    d.getTiming().getRepeat().addTimeOfDayElement().setValue("20:30:00");
    d.addDoseAndRate().setDose(tablets(1));

    Posology p = map(d).pos().get(0);

    assertThat(p.d()).isEmpty();
    assertThat(p.tt()).hasSize(2);
    assertThat(p.tt().get(0).off()).isEqualTo(28_800L);
    assertThat(p.tt().get(0).doFrom()).isEqualByComparingTo("1");
    assertThat(p.tt().get(1).off()).isEqualTo(73_800L);
    assertThat(entries(scope, e -> e.clinicalLoss())).isEmpty();
  }

  @Test
  void theLocalizedNoDosageNoteBecomesNoPosology() {
    for (String note :
        List.of(
            "Dosierung nicht angegeben",
            "Posologie non indiquée",
            "Posologia non indicata",
            "Dosage not specified")) {
      ReverseScope local = scope(document(CARD));
      Result result =
          DosageFromFhirMapper.map(
              List.of(new Dosage().setText(note)), "MedicationStatement[0]", local);

      assertThat(result.pos()).as(note).isEmpty();
      assertThat(result.appInstr()).as(note).isNull();
      assertThat(entries(local, e -> e.code().equals(ReasonCode.DERIVED_VALUE)))
          .as(note)
          .hasSize(1);
      assertThat(entries(local, e -> e.clinicalLoss())).as(note).isEmpty();
    }
  }

  @Test
  void noDosageAtAllGivesAnEmptyResult() {
    Result result = map();

    assertThat(result.pos()).isEmpty();
    assertThat(result.unit()).isNull();
    assertThat(result.appInstr()).isNull();
    assertThat(result.roa()).isNull();
    assertThat(scope.report().entries()).isEmpty();
  }

  @Test
  void aDosageWithOnlyDatesCarriesTheDatesAsTextAndReportsIt() {
    Dosage d = withStart(new Dosage(), "2012-02-04");

    Result result = map(d);

    assertThat(result.pos()).isEmpty();
    assertThat(result.unit()).isNull();
    assertThat(result.appInstr()).isEqualTo("from=2012-02-04");
    assertThat(entries(scope, e -> e.path().equals("MedicationStatement[0].dosage")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK);
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).as("there is no dose to lose").isFalse();
            });
  }

  @Test
  void dosesWithoutAStartDateAreNotLostInAPlan() {
    Dosage d = dosage(1, EventTiming.MORN);
    d.getTiming().getRepeat().setBounds(new Period().setEndElement(new DateTimeType("2012-03-31")));

    Result result = map(d);

    assertThat(result.pos()).as("a plan posology needs DtFrom").isEmpty();
    assertThat(result.appInstr()).isEqualTo("dose=1 STK; when=MORN; to=2012-03-31");
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK)))
        .singleElement()
        .satisfies(e -> assertThat(e.clinicalLoss()).isTrue());
  }

  @Test
  void aPrescriptionPosologyNeedsNoStartDate() {
    Dosage d = dosage(1, EventTiming.MORN);

    Result result =
        DosageFromFhirMapper.map(
            List.of(d),
            "MedicationRequest[0]",
            scope,
            DosageFromFhirMapper.Mode.prescription("2012-02-04"));

    assertThat(result.pos()).singleElement().satisfies(p -> assertDoses(p, 1, 0, 0, 0));
    assertThat(entries(scope, e -> e.clinicalLoss())).isEmpty();
  }

  @Test
  void takingTimesWithoutADoseCannotBeExpressed() {
    Dosage d = withStart(new Dosage(), "2012-02-04");
    d.getTiming().getRepeat().addTimeOfDayElement().setValue("08:00:00");

    Result result = map(d);

    assertThat(result.pos()).isEmpty();
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK)))
        .isNotEmpty();
  }

  @Test
  void theNoDosageNoteKeepsAnInstructionAndTheRoute() {
    Dosage d = new Dosage().setText("Dosierung nicht angegeben");
    d.setPatientInstruction("vor dem Essen");
    d.setRoute(
        new CodeableConcept(new Coding("urn:oid:0.4.0.127.0.16.1.1.2.1", "20053000", "Oral use")));

    Result result = map(d);

    assertThat(result.pos()).isEmpty();
    assertThat(result.appInstr()).isEqualTo("vor dem Essen");
    assertThat(result.roa()).isEqualTo("PO");
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.DERIVED_VALUE))).hasSize(1);
  }

  @Test
  void theNoDosageNoteAsPatientInstructionIsNotAnInstruction() {
    Dosage d = new Dosage().setText("Dosierung nicht angegeben");
    d.setPatientInstruction("Dosierung nicht angegeben");

    Result result = map(d);

    assertThat(result.pos()).isEmpty();
    assertThat(result.appInstr()).isNull();
  }
}
