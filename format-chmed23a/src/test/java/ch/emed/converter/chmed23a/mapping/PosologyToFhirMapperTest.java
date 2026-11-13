package ch.emed.converter.chmed23a.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed23a.model.Application;
import ch.emed.converter.chmed23a.model.DosageObject;
import ch.emed.converter.chmed23a.model.Posology;
import ch.emed.converter.chmed23a.model.PosologyDetail;
import ch.emed.converter.chmed23a.model.SequenceObject;
import ch.emed.converter.chmed23a.model.TimedDosage;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.MappingContext;
import java.math.BigDecimal;
import java.util.List;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Timing.DayOfWeek;
import org.hl7.fhir.r4.model.Timing.EventTiming;
import org.hl7.fhir.r4.model.Timing.UnitsOfTime;
import org.junit.jupiter.api.Test;

/** T028: posology rules M23-DOS-* (research R9), one test per row. */
class PosologyToFhirMapperTest {

  private final ReportCollector report = new ReportCollector();
  private final MappingScope scope =
      new MappingScope(
          "0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e23",
          new MappingContext(ConversionOptions.defaults(), report));

  private static BigDecimal n(String value) {
    return new BigDecimal(value);
  }

  private static DosageObject.Simple simple(String a) {
    return new DosageObject.Simple(n(a));
  }

  private static Posology pos(PosologyDetail po) {
    return new Posology(null, null, null, po, null, "Stk", null, null, null);
  }

  private List<Dosage> map(Posology pos) {
    return PosologyToFhirMapper.map(pos, "/meds/0/pos/0", "de-CH", scope);
  }

  private List<String> codes() {
    return report.entries().stream()
        .map(e -> e.code() + (e.clinicalLoss() ? " loss" : ""))
        .toList();
  }

  private static BigDecimal dose(Dosage d) {
    return ((Quantity) d.getDoseAndRateFirstRep().getDose()).getValue();
  }

  @Test
  void dailyGivesBaseAndSplitEntries() {
    List<Dosage> result =
        map(pos(new PosologyDetail.Daily(List.of(n("1"), n("0"), n("2"), n("0")))));
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getTiming().getRepeat().getWhen().get(0).getValue())
        .isEqualTo(EventTiming.MORN);
    assertThat(result.get(1).getTiming().getRepeat().getWhen().get(0).getValue())
        .isEqualTo(EventTiming.EVE);
    assertThat(dose(result.get(1))).isEqualByComparingTo("2");
    assertThat(result).extracting(Dosage::getSequence).containsExactly(1, 2);
    Quantity q = (Quantity) result.get(0).getDoseAndRateFirstRep().getDose();
    assertThat(q.getCode()).isEqualTo("767525000");
    assertThat(report.entries()).isEmpty();
  }

  @Test
  void freeTextIsLossless() {
    List<Dosage> result = map(pos(new PosologyDetail.FreeText("Nach Bedarf ½ Tablette")));
    assertThat(result).singleElement().satisfies(d -> assertThat(d.getText()).contains("½"));
    assertThat(report.entries()).isEmpty();
  }

  @Test
  void singleWithOneTimeIsCountOneAtThatTime() {
    Dosage d =
        map(pos(
                new PosologyDetail.Single(
                    new TimedDosage.Times(List.of(new Application.AtTime("09:00", simple("1")))))))
            .get(0);
    assertThat(d.getTiming().getRepeat().getCount()).isEqualTo(1);
    assertThat(d.getTiming().getRepeat().getTimeOfDay().get(0).getValue()).isEqualTo("09:00:00");
  }

  @Test
  void cyclicDosageOnlyIsFrequencyPerPeriod() {
    Dosage d =
        map(pos(new PosologyDetail.Cyclic(5, 1, new TimedDosage.DosageOnly(simple("1")), 2)))
            .get(0);
    assertThat(d.getTiming().getRepeat().getFrequency()).isEqualTo(2);
    assertThat(d.getTiming().getRepeat().getPeriod()).isEqualByComparingTo("1");
    assertThat(d.getTiming().getRepeat().getPeriodUnit()).isEqualTo(UnitsOfTime.WK);
  }

  @Test
  void dailyTimesWithEqualDosesAreTimesOfDay() {
    Dosage d =
        map(pos(
                new PosologyDetail.Cyclic(
                    4,
                    1,
                    new TimedDosage.Times(
                        List.of(
                            new Application.AtTime("08:00:00", simple("1")),
                            new Application.AtTime("20:00:00", simple("1")))),
                    null)))
            .get(0);
    assertThat(d.getTiming().getRepeat().getTimeOfDay()).hasSize(2);
    assertThat(dose(d)).isEqualByComparingTo("1");
  }

  @Test
  void dailyTimesWithDifferentDosesAreText() {
    List<Dosage> result =
        map(
            pos(
                new PosologyDetail.Cyclic(
                    4,
                    1,
                    new TimedDosage.Times(
                        List.of(
                            new Application.AtTime("08:00:00", simple("1")),
                            new Application.AtTime("20:00:00", simple("2")))),
                    null)));
    assertThat(result).singleElement().satisfies(d -> assertThat(d.getText()).contains("08:00"));
    assertThat(codes()).containsExactly(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK + " loss");
  }

  @Test
  void weekDaysWithSegmentsRepeatTheDays() {
    List<Dosage> result =
        map(
            pos(
                new PosologyDetail.Cyclic(
                    5,
                    1,
                    new TimedDosage.WeekDays(
                        List.of(1, 5),
                        new TimedDosage.DaySegments(
                            List.of(
                                new Application.InSegment(1, simple("1")),
                                new Application.InSegment(3, simple("1"))))),
                    null)));
    assertThat(result).hasSize(2);
    assertThat(result)
        .allSatisfy(
            d ->
                assertThat(d.getTiming().getRepeat().getDayOfWeek())
                    .extracting(e -> e.getValue())
                    .containsExactly(DayOfWeek.MON, DayOfWeek.FRI));
  }

  @Test
  void dosageRangeIsDoseRange() {
    Dosage d =
        map(pos(
                new PosologyDetail.Single(
                    new TimedDosage.DosageOnly(new DosageObject.Range(n("1"), n("3"))))))
            .get(0);
    Range range = (Range) d.getDoseAndRateFirstRep().getDose();
    assertThat(range.getLow().getValue()).isEqualByComparingTo("1");
    assertThat(range.getHigh().getValue()).isEqualByComparingTo("3");
  }

  @Test
  void structuresWithoutExactEquivalentAreText() {
    List<PosologyDetail> details =
        List.of(
            new PosologyDetail.Cyclic(
                6,
                1,
                new TimedDosage.DaysOfMonth(
                    List.of(1, 15), new TimedDosage.DosageOnly(simple("1"))),
                null),
            new PosologyDetail.Cyclic(4, 1, new TimedDosage.Interval(simple("1"), 6, 3), 4),
            new PosologyDetail.Sequence(
                List.of(
                    new SequenceObject.PosologySequence(
                        21, 4, new PosologyDetail.Daily(List.of(n("1"), n("0"), n("0"), n("0")))),
                    new SequenceObject.Pause(7, 4))),
            new PosologyDetail.Single(
                new TimedDosage.DosageOnly(new DosageObject.FromTo(n("5"), n("10"), 2, 45))));
    for (PosologyDetail po : details) {
      ReportCollector r = new ReportCollector();
      MappingScope s =
          new MappingScope(scope.documentId(), new MappingContext(ConversionOptions.defaults(), r));
      List<Dosage> result = PosologyToFhirMapper.map(pos(po), "/meds/0/pos/0", "de-CH", s);
      assertThat(result).as(po.toString()).singleElement();
      assertThat(result.get(0).getText()).as(po.toString()).isNotBlank();
      assertThat(result.get(0).getDoseAndRate()).isEmpty();
      assertThat(r.entries())
          .extracting(ReportEntry::code)
          .containsExactly(ReasonCode.DOSAGE_FREE_TEXT_FALLBACK);
    }
  }

  @Test
  void attributesOfThePosology() {
    Posology pos =
        new Posology(
            "2024-01-10",
            "2024-02-29",
            true,
            new PosologyDetail.Daily(List.of(n("1"), n("0"), n("0"), n("0"))),
            null,
            "Stk",
            "Mit Wasser",
            "20053000",
            "19");
    Dosage d = map(pos).get(0);
    assertThat(d.getTiming().getRepeat().getBoundsPeriod().getStartElement().getValueAsString())
        .isEqualTo("2024-01-10");
    assertThat(d.getAsNeededBooleanType().getValue()).isTrue();
    assertThat(d.getPatientInstruction()).isEqualTo("Mit Wasser");
    assertThat(d.getRoute().getCodingFirstRep().getCode()).isEqualTo("20053000");
    assertThat(d.getMethod().getCodingFirstRep().getCode()).isEqualTo("0019");
  }

  @Test
  void unmappedMethodAndUnitAreReported() {
    Posology pos =
        new Posology(
            null,
            null,
            null,
            new PosologyDetail.Daily(List.of(n("1"), n("0"), n("0"), n("0"))),
            null,
            "TABL",
            null,
            null,
            "20");
    Dosage d = map(pos).get(0);
    assertThat(d.getText()).contains("TABL");
    assertThat(codes())
        .containsExactlyInAnyOrder(
            ReasonCode.UNMAPPABLE_ELEMENT + " loss",
            ReasonCode.DOSAGE_FREE_TEXT_FALLBACK + " loss");
  }

  @Test
  void mealRelationAsTimingOrAsText() {
    Posology before =
        new Posology(
            null,
            null,
            null,
            new PosologyDetail.Daily(List.of(n("1"), n("0"), n("1"), n("0"))),
            1,
            "Stk",
            null,
            null,
            null);
    List<Dosage> result = map(before);
    assertThat(result.get(0).getTiming().getRepeat().getWhen().get(0).getValue())
        .isEqualTo(EventTiming.ACM);
    assertThat(result.get(1).getTiming().getRepeat().getWhen().get(0).getValue())
        .isEqualTo(EventTiming.ACV);
    assertThat(report.entries()).isEmpty();

    Posology emptyStomach =
        new Posology(
            null,
            null,
            null,
            new PosologyDetail.Daily(List.of(n("1"), n("0"), n("0"), n("0"))),
            4,
            "Stk",
            null,
            null,
            null);
    Dosage d = map(emptyStomach).get(0);
    assertThat(d.getAdditionalInstructionFirstRep().getText()).isEqualTo("auf nüchternen Magen");
    assertThat(d.getAdditionalInstructionFirstRep().hasCoding()).isFalse();
    assertThat(codes()).containsExactly(ReasonCode.UNMAPPABLE_ELEMENT);
  }
}
