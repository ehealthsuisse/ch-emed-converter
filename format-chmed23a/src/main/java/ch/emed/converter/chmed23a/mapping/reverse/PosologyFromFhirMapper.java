package ch.emed.converter.chmed23a.mapping.reverse;

import static ch.emed.converter.chmed23a.mapping.reverse.ReverseScope.present;

import ch.emed.converter.chmed23a.mapping.MappingRule;
import ch.emed.converter.chmed23a.mapping.PosologyText;
import ch.emed.converter.chmed23a.mapping.PosologyToFhirMapper;
import ch.emed.converter.chmed23a.model.Application;
import ch.emed.converter.chmed23a.model.DosageObject;
import ch.emed.converter.chmed23a.model.Posology;
import ch.emed.converter.chmed23a.model.PosologyDetail;
import ch.emed.converter.chmed23a.model.TimeUnit;
import ch.emed.converter.chmed23a.model.TimedDosage;
import ch.emed.converter.chmed23a.validation.Terminology;
import ch.emed.converter.core.chemed.Labels;
import ch.emed.converter.core.chemed.Profiles;
import ch.emed.converter.core.chemed.Units;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.TimeType;
import org.hl7.fhir.r4.model.Timing.DayOfWeek;
import org.hl7.fhir.r4.model.Timing.EventTiming;
import org.hl7.fhir.r4.model.Timing.TimingRepeatComponent;
import org.hl7.fhir.r4.model.Timing.UnitsOfTime;

/**
 * CH EMED Dosage (base entry + split entries) → CHMED23A Posology (mapping spec M23-REV-12..14):
 * the inverse of the exact rows of research R9; a dosage given as text only becomes FreeText; any
 * other structure becomes FreeText with a description and is reported.
 */
public final class PosologyFromFhirMapper {

  /** Unit written when CH EMED holds no dose unit but the plan requires one (CDTYP 9 "N/A"). */
  public static final String NO_UNIT = "N/A";

  private static final List<String> LANGUAGES = List.of("de-CH", "fr-CH", "it-CH", "en-CH");
  private static final DayOfWeek[] DAYS = {
    DayOfWeek.MON,
    DayOfWeek.TUE,
    DayOfWeek.WED,
    DayOfWeek.THU,
    DayOfWeek.FRI,
    DayOfWeek.SAT,
    DayOfWeek.SUN
  };

  /** One base or split entry reduced to the elements the reverse table knows. */
  private record Entry(
      Integer segment,
      Integer meal,
      List<String> times,
      List<Integer> days,
      Integer count,
      Integer frequency,
      BigDecimal period,
      UnitsOfTime periodUnit,
      DosageObject dose) {}

  private PosologyFromFhirMapper() {}

  /**
   * Maps the dosages of one statement or request.
   *
   * @param dosages base entry first, then split entries
   * @param path path of the dosage list, e.g. {@code MedicationStatement[0].dosage}
   * @param plan true for a medication plan (unit required)
   * @param scope mapping scope
   * @return the posology, or null if the dosage only says "dosage not given"
   */
  @MappingRule({"M23-REV-12", "M23-REV-13", "M23-REV-14"})
  public static Posology map(List<Dosage> dosages, String path, boolean plan, ReverseScope scope) {
    if (dosages.isEmpty()) {
      return null;
    }
    Dosage base = dosages.get(0);
    if (!base.hasDoseAndRate() && present(base.getText()) && noDosageNote(base.getText())) {
      return null;
    }
    Period bounds =
        base.getTiming().getRepeat().hasBoundsPeriod()
            ? base.getTiming().getRepeat().getBoundsPeriod()
            : null;
    String dtFrom =
        bounds != null && bounds.hasStart() ? bounds.getStartElement().getValueAsString() : null;
    String dtTo =
        bounds != null && bounds.hasEnd() ? bounds.getEndElement().getValueAsString() : null;
    Boolean inRes =
        base.hasAsNeededBooleanType() && base.getAsNeededBooleanType().booleanValue() ? true : null;
    String roa = route(base, path, scope);
    String moa = method(base, path, scope);
    Integer textMeal = textMeal(base, path, scope);
    String appInstr = base.getPatientInstruction();
    if (present(appInstr) && appInstr.equals(base.getText())) {
      appInstr = null; // the text copied for ch-dosage-3 (M23-RX-02)
    }

    // null: a structure the reverse table does not cover; empty: no dose (text only)
    List<Entry> entries = entries(dosages);
    PosologyDetail po = null;
    Integer meal = null;
    String unit = null;
    if (entries != null && !entries.isEmpty()) {
      po = detail(entries);
      meal = meal(entries);
      unit = unit(dosages, path, scope);
      boolean mealsDiffer = meal == null && entries.stream().anyMatch(e -> e.meal() != null);
      if (unit == null || mealsDiffer) {
        po = null;
      }
    }
    if (po == null) {
      String text = present(base.getText()) ? base.getText() : describe(dosages);
      po = new PosologyDetail.FreeText(text);
      if (structured(dosages)) {
        scope
            .report()
            .add(
                ReportEntry.warning(
                        ReasonCode.DOSAGE_FREE_TEXT_FALLBACK,
                        Phase.MAP,
                        path,
                        "The dosage cannot be represented as structured CHMED23A posology;"
                            + " carried as FreeText")
                    .withClinicalLoss(true));
      }
      meal = null;
      unit = quantityUnit(dosages);
    } else if (present(base.getText())) {
      scope.unmapped(
          path + "[0].text", "The dosage text is not carried over besides the structure");
    }
    if (meal == null) {
      meal = textMeal;
    } else if (textMeal != null && !textMeal.equals(meal)) {
      scope.unmapped(
          path + "[0].additionalInstruction", "A second meal relation is not carried over");
    }
    if (unit == null && plan) {
      unit = NO_UNIT;
      scope.derived(
          path, "No dose unit in CH EMED; the unit N/A of CDTYP 9 is set (required in a plan)");
    }
    return new Posology(dtFrom, dtTo, inRes, po, meal, unit, appInstr, roa, moa);
  }

  /** True if the text is the localized note for a medicament without posology. */
  public static boolean noDosageNote(String text) {
    return Labels.all(Labels.Key.NO_DOSAGE).contains(text);
  }

  // ---------------------------------------------------------------------------------------------

  private static List<Entry> entries(List<Dosage> dosages) {
    List<Entry> entries = new ArrayList<>();
    for (int i = 0; i < dosages.size(); i++) {
      Dosage d = dosages.get(i);
      if (!d.hasDoseAndRate()) {
        // a dosage without dose is text only, unless it has a timing CHMED23A cannot keep
        return i == 0 && dosages.size() == 1 && !hasTimingDetail(d) ? List.of() : null;
      }
      if (d.getDoseAndRate().size() != 1
          || d.hasMaxDosePerPeriod()
          || d.hasMaxDosePerAdministration()) {
        return null;
      }
      DosageObject dose = dose(d.getDoseAndRateFirstRep());
      if (dose == null) {
        return null;
      }
      TimingRepeatComponent r = d.getTiming().getRepeat();
      if (d.getTiming().hasEvent()
          || d.getTiming().hasCode()
          || r.hasBoundsDuration()
          || r.hasBoundsRange()
          || r.hasCountMax()
          || r.hasDuration()
          || r.hasFrequencyMax()
          || r.hasPeriodMax()
          || r.hasOffset()
          || r.getWhen().size() > 1) {
        return null;
      }
      Integer segment = null;
      Integer meal = null;
      if (r.getWhen().size() == 1) {
        int[] sm = segmentAndMeal(r.getWhen().get(0).getValue());
        if (sm == null) {
          return null;
        }
        segment = sm[0] == 0 ? null : sm[0];
        meal = sm[1] == 0 ? null : sm[1];
      }
      List<String> times = new ArrayList<>();
      for (TimeType t : r.getTimeOfDay()) {
        times.add(t.getValue());
      }
      List<Integer> days = new ArrayList<>();
      for (Enumeration<DayOfWeek> day : r.getDayOfWeek()) {
        days.add(day.getValue().ordinal() + 1);
      }
      entries.add(
          new Entry(
              segment,
              meal,
              times,
              days,
              r.hasCount() ? r.getCount() : null,
              r.hasFrequency() ? r.getFrequency() : null,
              r.hasPeriod() ? r.getPeriod() : null,
              r.hasPeriodUnit() ? r.getPeriodUnit() : null,
              dose));
    }
    return entries;
  }

  private static boolean hasTimingDetail(Dosage d) {
    TimingRepeatComponent r = d.getTiming().getRepeat();
    return r.hasWhen() || r.hasTimeOfDay() || r.hasFrequency() || r.hasDayOfWeek() || r.hasCount();
  }

  private static boolean structured(List<Dosage> dosages) {
    for (Dosage d : dosages) {
      if (d.hasDoseAndRate() || hasTimingDetail(d)) {
        return true;
      }
    }
    return false;
  }

  /** The posology detail of structured entries, or null if the reverse table does not cover it. */
  private static PosologyDetail detail(List<Entry> entries) {
    if (entries.isEmpty()) {
      return null;
    }
    Entry first = entries.get(0);
    boolean sameDays = entries.stream().allMatch(e -> e.days().equals(first.days()));
    boolean segmentsOnly =
        entries.stream()
            .allMatch(
                e ->
                    e.segment() != null
                        && e.times().isEmpty()
                        && e.count() == null
                        && e.frequency() == null);
    if (segmentsOnly && sameDays) {
      List<Integer> seen = new ArrayList<>();
      List<Application.InSegment> ss = new ArrayList<>();
      for (Entry e : entries) {
        if (seen.contains(e.segment())) {
          return null;
        }
        seen.add(e.segment());
        ss.add(new Application.InSegment(e.segment(), e.dose()));
      }
      if (first.days().isEmpty()) {
        if (entries.stream().allMatch(e -> e.dose() instanceof DosageObject.Simple)) {
          BigDecimal[] ds = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
          for (Entry e : entries) {
            ds[e.segment() - 1] = ((DosageObject.Simple) e.dose()).a();
          }
          return new PosologyDetail.Daily(List.of(ds));
        }
        return new PosologyDetail.Cyclic(
            TimeUnit.DAY.code(), 1, new TimedDosage.DaySegments(ss), null);
      }
      return new PosologyDetail.Cyclic(
          TimeUnit.WEEK.code(),
          1,
          new TimedDosage.WeekDays(first.days(), new TimedDosage.DaySegments(ss)),
          null);
    }
    if (entries.size() != 1) {
      return null;
    }
    Entry e = first;
    if (e.count() != null) {
      if (e.count() != 1 || e.frequency() != null || !e.days().isEmpty() || e.times().size() > 1) {
        return null;
      }
      TimedDosage tdo =
          e.times().size() == 1
              ? new TimedDosage.Times(List.of(new Application.AtTime(e.times().get(0), e.dose())))
              : e.segment() != null
                  ? new TimedDosage.DaySegments(
                      List.of(new Application.InSegment(e.segment(), e.dose())))
                  : new TimedDosage.DosageOnly(e.dose());
      return new PosologyDetail.Single(tdo);
    }
    if (e.frequency() != null) {
      TimeUnit unit = timeUnit(e.periodUnit());
      if (unit == null
          || e.period() == null
          || e.period().stripTrailingZeros().scale() > 0
          || !e.times().isEmpty()
          || !e.days().isEmpty()
          || e.segment() != null) {
        return null;
      }
      return new PosologyDetail.Cyclic(
          unit.code(),
          e.period().intValueExact(),
          new TimedDosage.DosageOnly(e.dose()),
          e.frequency());
    }
    if (e.segment() != null) {
      return null;
    }
    TimedDosage inner =
        e.times().isEmpty()
            ? new TimedDosage.DosageOnly(e.dose())
            : new TimedDosage.Times(
                e.times().stream().map(t -> new Application.AtTime(t, e.dose())).toList());
    if (e.days().isEmpty()) {
      return inner instanceof TimedDosage.Times
          ? new PosologyDetail.Cyclic(TimeUnit.DAY.code(), 1, inner, null)
          : null;
    }
    return new PosologyDetail.Cyclic(
        TimeUnit.WEEK.code(), 1, new TimedDosage.WeekDays(e.days(), inner), null);
  }

  /** The meal relation of the entries if all agree, else null. */
  private static Integer meal(List<Entry> entries) {
    Integer meal = entries.isEmpty() ? null : entries.get(0).meal();
    for (Entry e : entries) {
      if (!Objects.equals(e.meal(), meal)) {
        return null;
      }
    }
    return meal;
  }

  /** {segment 0-4, meal 0-3} of an event timing, or null for other events. */
  private static int[] segmentAndMeal(EventTiming when) {
    return switch (when) {
      case MORN -> new int[] {1, 0};
      case NOON -> new int[] {2, 0};
      case EVE -> new int[] {3, 0};
      case NIGHT -> new int[] {4, 0};
      case ACM -> new int[] {1, 1};
      case ACD -> new int[] {2, 1};
      case ACV -> new int[] {3, 1};
      case CM -> new int[] {1, 2};
      case CD -> new int[] {2, 2};
      case CV -> new int[] {3, 2};
      case PCM -> new int[] {1, 3};
      case PCD -> new int[] {2, 3};
      case PCV -> new int[] {3, 3};
      case AC -> new int[] {0, 1};
      case C -> new int[] {0, 2};
      case PC -> new int[] {0, 3};
      default -> null;
    };
  }

  private static DosageObject dose(Dosage.DosageDoseAndRateComponent doseAndRate) {
    if (doseAndRate.hasRate()) {
      return null;
    }
    if (doseAndRate.getDose() instanceof Quantity q && q.hasValue()) {
      return new DosageObject.Simple(q.getValue());
    }
    if (doseAndRate.getDose() instanceof Range r
        && r.getLow().hasValue()
        && r.getHigh().hasValue()) {
      return new DosageObject.Range(r.getLow().getValue(), r.getHigh().getValue());
    }
    return null;
  }

  /** The CDTYP 9 unit of the doses, or null if they have none or differ (M23-DOS-15 inverse). */
  private static String unit(List<Dosage> dosages, String path, ReverseScope scope) {
    String unit = null;
    for (Dosage d : dosages) {
      for (Quantity q : quantities(d)) {
        String u = unitOf(q);
        if (u == null) {
          scope.unmappedWarning(
              path + ".doseAndRate", "The dose unit has no CHMED23A unit code", true);
          return null;
        }
        if (unit != null && !unit.equals(u)) {
          return null;
        }
        unit = u;
      }
    }
    return unit;
  }

  /** The unit of the first quantity if it is a CDTYP 9 code, for FreeText; else null. */
  private static String quantityUnit(List<Dosage> dosages) {
    for (Dosage d : dosages) {
      for (Quantity q : quantities(d)) {
        return unitOf(q);
      }
    }
    return null;
  }

  private static List<Quantity> quantities(Dosage d) {
    List<Quantity> list = new ArrayList<>();
    for (Dosage.DosageDoseAndRateComponent dr : d.getDoseAndRate()) {
      if (dr.getDose() instanceof Quantity q) {
        list.add(q);
      } else if (dr.getDose() instanceof Range r) {
        list.add(r.getLow());
        list.add(r.getHigh());
      }
    }
    return list;
  }

  private static String unitOf(Quantity q) {
    if (present(q.getUnit()) && Terminology.isUnit(q.getUnit())) {
      String code = Terminology.unitCode(q.getUnit());
      String[] mapped = PosologyToFhirMapper.unitCode(code);
      if (mapped == null || (mapped[0].equals(q.getSystem()) && mapped[1].equals(q.getCode()))) {
        return code;
      }
    }
    for (Map<String, String[]> table : List.of(Units.CDTYP9, PosologyToFhirMapper.EXTRA_UNITS)) {
      for (Map.Entry<String, String[]> e : table.entrySet()) {
        if (e.getValue()[0].equals(q.getSystem())
            && e.getValue()[1].equals(q.getCode())
            && Terminology.isUnit(e.getKey())
            && !e.getKey().equals("MCG")) {
          return Terminology.unitCode(e.getKey());
        }
      }
    }
    if (Profiles.SNOMED.equals(q.getSystem()) && "732936001".equals(q.getCode())) {
      return Terminology.unitCode("Stk"); // "Tablet (unit of presentation)" of the CH EMED examples
    }
    return null;
  }

  private static TimeUnit timeUnit(UnitsOfTime unit) {
    if (unit == null) {
      return null;
    }
    return switch (unit) {
      case S -> TimeUnit.SECOND;
      case MIN -> TimeUnit.MINUTE;
      case H -> TimeUnit.HOUR;
      case D -> TimeUnit.DAY;
      case WK -> TimeUnit.WEEK;
      case MO -> TimeUnit.MONTH;
      case A -> TimeUnit.YEAR;
      default -> null;
    };
  }

  @MappingRule("M23-REV-16")
  private static String route(Dosage base, String path, ReverseScope scope) {
    if (!base.hasRoute()) {
      return null;
    }
    for (Coding c : base.getRoute().getCoding()) {
      if (Profiles.EDQM.equals(c.getSystem()) && Terminology.isRoute(c.getCode())) {
        return c.getCode();
      }
    }
    scope.unmappedWarning(path + "[0].route", "The route has no CHMED23A route code", true);
    return null;
  }

  private static String method(Dosage base, String path, ReverseScope scope) {
    if (!base.hasMethod()) {
      return null;
    }
    for (Coding c : base.getMethod().getCoding()) {
      if (Profiles.EDQM.equals(c.getSystem())) {
        String moa = PosologyToFhirMapper.methodOf(c.getCode());
        if (moa != null) {
          return moa;
        }
      }
    }
    scope.unmappedWarning(path + "[0].method", "The method has no CHMED23A method code", true);
    return null;
  }

  /** Meal relation from the additional instruction text written by M23-DOS-16. */
  private static Integer textMeal(Dosage base, String path, ReverseScope scope) {
    Integer meal = null;
    for (CodeableConcept ai : base.getAdditionalInstruction()) {
      Integer found = null;
      for (int value = 1; value <= 7 && found == null; value++) {
        for (String language : LANGUAGES) {
          if (Objects.equals(ai.getText(), PosologyText.mealLabel(value, language))) {
            found = value;
            break;
          }
        }
      }
      if (found != null && meal == null && !ai.hasCoding()) {
        meal = found;
      } else {
        scope.unmappedWarning(
            path + "[0].additionalInstruction",
            "The additional instruction is not carried over",
            true);
      }
    }
    return meal;
  }

  /** A short description of a structured dosage that the reverse table does not cover. */
  private static String describe(List<Dosage> dosages) {
    List<String> parts = new ArrayList<>();
    for (Dosage d : dosages) {
      List<String> p = new ArrayList<>();
      TimingRepeatComponent r = d.getTiming().getRepeat();
      r.getWhen().forEach(w -> p.add(w.getValue().toCode()));
      r.getTimeOfDay().forEach(t -> p.add(t.getValue()));
      r.getDayOfWeek().forEach(w -> p.add(w.getValue().toCode()));
      if (r.hasFrequency()) {
        p.add(
            r.getFrequency()
                + "x/"
                + r.getPeriod().stripTrailingZeros().toPlainString()
                + r.getPeriodUnit().toCode());
      }
      for (Quantity q : quantities(d)) {
        p.add(
            q.getValue().stripTrailingZeros().toPlainString()
                + (present(q.getUnit()) ? " " + q.getUnit() : ""));
      }
      if (!p.isEmpty()) {
        parts.add(String.join(" ", p));
      }
    }
    return parts.isEmpty() ? "-" : String.join("; ", parts);
  }
}
