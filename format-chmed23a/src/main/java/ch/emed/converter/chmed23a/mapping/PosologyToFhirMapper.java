package ch.emed.converter.chmed23a.mapping;

import static ch.emed.converter.chmed23a.mapping.MappingScope.present;

import ch.emed.converter.chmed23a.model.Application;
import ch.emed.converter.chmed23a.model.DosageObject;
import ch.emed.converter.chmed23a.model.Posology;
import ch.emed.converter.chmed23a.model.PosologyDetail;
import ch.emed.converter.chmed23a.model.TimeUnit;
import ch.emed.converter.chmed23a.model.TimedDosage;
import ch.emed.converter.core.chemed.Profiles;
import ch.emed.converter.core.chemed.Units;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.hl7.fhir.r4.model.Timing.TimingRepeatComponent;
import org.hl7.fhir.r4.model.Timing.UnitsOfTime;

/**
 * CHMED23A Posology → CH EMED Dosage (base entry + split entries, mapping spec M23-DOS-*, research
 * R9). Only exact equivalents are structured; everything else is carried as localized text and
 * reported with clinical loss. Never produces a clinically different dosage.
 */
public final class PosologyToFhirMapper {

  private static final String UCUM = "http://unitsofmeasure.org";
  private static final EventTiming[] SEGMENTS = {
    EventTiming.MORN, EventTiming.NOON, EventTiming.EVE, EventTiming.NIGHT
  };
  private static final DayOfWeek[] DAYS = {
    DayOfWeek.MON,
    DayOfWeek.TUE,
    DayOfWeek.WED,
    DayOfWeek.THU,
    DayOfWeek.FRI,
    DayOfWeek.SAT,
    DayOfWeek.SUN
  };

  /**
   * CHMED23A units (CDTYP 9) beyond the shared table, each unambiguous in the CH Term UnitCode
   * value set (research R9; domain review).
   */
  static final Map<String, String[]> EXTRA_UNITS =
      Map.ofEntries(
          Map.entry("%", new String[] {UCUM, "%", "percent"}),
          Map.entry("BQ", new String[] {UCUM, "Bq", "Becquerel"}),
          Map.entry("KBQ", new String[] {UCUM, "kBq", "kiloBecquerel"}),
          Map.entry("MBQ", new String[] {UCUM, "MBq", "megaBecquerel"}),
          Map.entry("GBQ", new String[] {UCUM, "GBq", "gigaBecquerel"}),
          Map.entry("NG", new String[] {UCUM, "ng", "nanogram"}),
          Map.entry("KG", new String[] {UCUM, "kg", "kilogram"}),
          Map.entry("KCAL", new String[] {UCUM, "kcal", "kilocalorie"}),
          Map.entry("KJ", new String[] {UCUM, "kJ", "kilojoule"}),
          Map.entry("MCL", new String[] {UCUM, "uL", "microliter"}),
          Map.entry("MCMOL", new String[] {UCUM, "umol", "micromole"}),
          Map.entry("MOL", new String[] {UCUM, "mol", "mole"}),
          Map.entry("UI", new String[] {UCUM, "[iU]", "international unit"}),
          Map.entry(
              "GTT", new String[] {Profiles.SNOMED, "732994000", "Drop (unit of presentation)"}));

  static final Map<String, String> ROUTES = load("/chmed23a/route-edqm.csv", 1);
  static final Map<String, String[]> METHODS = loadRows("/chmed23a/method-edqm.csv");

  /** One structured entry: timing and dose. */
  private record Entry(
      List<EventTiming> when,
      List<String> timeOfDay,
      List<DayOfWeek> days,
      Integer count,
      Integer frequency,
      Integer period,
      TimeUnit periodUnit,
      DosageObject dose) {

    Entry withWhen(List<EventTiming> value) {
      return new Entry(value, timeOfDay, days, count, frequency, period, periodUnit, dose);
    }
  }

  private PosologyToFhirMapper() {}

  /**
   * Maps one posology.
   *
   * @param pos the posology
   * @param posPath JSON path of the posology, e.g. {@code /meds/0/pos/1}
   * @param language CH EMED language of the document
   * @param scope mapping scope
   * @return base entry first, then split entries
   */
  @MappingRule({
    "M23-DOS-01",
    "M23-DOS-02",
    "M23-DOS-03",
    "M23-DOS-04",
    "M23-DOS-05",
    "M23-DOS-06",
    "M23-DOS-07",
    "M23-DOS-08",
    "M23-DOS-09",
    "M23-DOS-10",
    "M23-DOS-11",
    "M23-DOS-12"
  })
  public static List<Dosage> map(
      Posology pos, String posPath, String language, MappingScope scope) {
    Dosage base = new Dosage();
    if (present(pos.dtFrom()) || present(pos.dtTo())) {
      Period bounds = new Period();
      if (present(pos.dtFrom())) {
        bounds.setStartElement(new DateTimeType(pos.dtFrom()));
      }
      if (present(pos.dtTo())) {
        bounds.setEndElement(new DateTimeType(pos.dtTo()));
      }
      base.getTiming().getRepeat().setBounds(bounds);
    }
    if (Boolean.TRUE.equals(pos.inRes())) {
      base.setAsNeeded(new BooleanType(true));
    }
    route(pos, base, posPath, scope);
    method(pos, base, posPath, scope);
    if (present(pos.appInstr())) {
      base.setPatientInstruction(pos.appInstr());
    }
    List<Dosage> result = new ArrayList<>();
    result.add(base);
    if (pos.po() instanceof PosologyDetail.FreeText free) {
      base.setText(free.text());
      meal(pos, base, false, language, posPath, scope);
      return result;
    }
    if (pos.po() instanceof PosologyDetail.Daily daily
        && daily.ds().stream().allMatch(d -> d.signum() == 0)) {
      scope
          .report()
          .add(
              ReportEntry.information(
                  ReasonCode.DERIVED_VALUE,
                  Phase.MAP,
                  posPath + "/po/ds",
                  "All doses are 0; no dose quantity is set"));
      return result;
    }
    List<Entry> entries = entries(pos.po());
    if (entries == null) {
      text(pos, base, posPath, language, scope, "The posology");
      return result;
    }
    if (unitCode(pos.unit()) == null) {
      text(pos, base, posPath, language, scope, "The unit");
      return result;
    }
    boolean mealTiming =
        pos.relMeal() != null
            && pos.relMeal() >= 1
            && pos.relMeal() <= 3
            && mealAsTiming(pos.relMeal(), entries);
    entries = mealTimings(pos.relMeal(), entries);
    for (int i = 0; i < entries.size(); i++) {
      Dosage target = i == 0 ? base : new Dosage();
      if (i > 0) {
        result.add(target);
      }
      apply(entries.get(i), target, pos.unit());
    }
    if (result.size() > 1) {
      for (int i = 0; i < result.size(); i++) {
        result.get(i).setSequence(i + 1);
      }
    }
    meal(pos, base, mealTiming, language, posPath, scope);
    return result;
  }

  /**
   * Dosage of a medicament without posology: CH EMED requires a dosage, so the localized note is
   * its text (M23-MED-09, M23-RX-10).
   */
  public static Dosage noPosology(String note) {
    return new Dosage().setText(note);
  }

  /** Structured entries of a posology detail, or null if CH EMED cannot hold it exactly. */
  private static List<Entry> entries(PosologyDetail po) {
    if (po == null) {
      return null;
    }
    return switch (po) {
      case PosologyDetail.Daily d -> {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < d.ds().size() && i < 4; i++) {
          if (d.ds().get(i).signum() != 0) {
            entries.add(
                new Entry(
                    List.of(SEGMENTS[i]),
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    new DosageObject.Simple(d.ds().get(i))));
          }
        }
        yield entries;
      }
      case PosologyDetail.Single s -> single(s.tdo());
      case PosologyDetail.Cyclic c -> cyclic(c);
      case PosologyDetail.FreeText f -> null;
      case PosologyDetail.Sequence s -> null;
    };
  }

  private static List<Entry> single(TimedDosage tdo) {
    if (tdo instanceof TimedDosage.DosageOnly d && structured(d.dose())) {
      return List.of(new Entry(List.of(), List.of(), List.of(), 1, null, null, null, d.dose()));
    }
    if (tdo instanceof TimedDosage.Times t
        && t.ts().size() == 1
        && structured(t.ts().get(0).dose())) {
      return List.of(
          new Entry(
              List.of(),
              List.of(timeOfDay(t.ts().get(0).dt())),
              List.of(),
              1,
              null,
              null,
              null,
              t.ts().get(0).dose()));
    }
    if (tdo instanceof TimedDosage.DaySegments s
        && s.ss().size() == 1
        && structured(s.ss().get(0).dose())) {
      return List.of(
          new Entry(
              List.of(SEGMENTS[s.ss().get(0).s() - 1]),
              List.of(),
              List.of(),
              1,
              null,
              null,
              null,
              s.ss().get(0).dose()));
    }
    return null;
  }

  private static List<Entry> cyclic(PosologyDetail.Cyclic c) {
    TimeUnit unit = TimeUnit.of(c.cyDuU());
    int tdpc = c.timedDosagesPerCycle();
    boolean oneDay = unit == TimeUnit.DAY && c.cyDu() != null && c.cyDu() == 1 && tdpc == 1;
    boolean oneWeek = unit == TimeUnit.WEEK && c.cyDu() != null && c.cyDu() == 1 && tdpc == 1;
    if (c.tdo() instanceof TimedDosage.DosageOnly d && structured(d.dose()) && unit != null) {
      return List.of(
          new Entry(List.of(), List.of(), List.of(), null, tdpc, c.cyDu(), unit, d.dose()));
    }
    if (oneDay) {
      return daily(c.tdo(), List.of());
    }
    if (oneWeek && c.tdo() instanceof TimedDosage.WeekDays w) {
      List<DayOfWeek> days = new ArrayList<>();
      for (Integer day : w.wds()) {
        days.add(DAYS[day - 1]);
      }
      if (w.tdo() instanceof TimedDosage.DosageOnly d && structured(d.dose())) {
        return List.of(new Entry(List.of(), List.of(), days, null, null, null, null, d.dose()));
      }
      return daily(w.tdo(), days);
    }
    return null;
  }

  /** Times with equal doses or day segments, on every day or on the given days. */
  private static List<Entry> daily(TimedDosage tdo, List<DayOfWeek> days) {
    if (tdo instanceof TimedDosage.Times t && !t.ts().isEmpty()) {
      DosageObject dose = t.ts().get(0).dose();
      List<String> times = new ArrayList<>();
      for (Application.AtTime a : t.ts()) {
        if (!structured(a.dose()) || !a.dose().equals(dose)) {
          return null;
        }
        String time = timeOfDay(a.dt());
        if (times.contains(time)) {
          return null;
        }
        times.add(time);
      }
      return List.of(new Entry(List.of(), times, days, null, null, null, null, dose));
    }
    if (tdo instanceof TimedDosage.DaySegments s && !s.ss().isEmpty()) {
      List<Entry> entries = new ArrayList<>();
      List<Integer> seen = new ArrayList<>();
      for (Application.InSegment a : s.ss()) {
        if (!structured(a.dose()) || seen.contains(a.s())) {
          return null;
        }
        seen.add(a.s());
        entries.add(
            new Entry(
                List.of(SEGMENTS[a.s() - 1]), List.of(), days, null, null, null, null, a.dose()));
      }
      return entries;
    }
    return null;
  }

  private static boolean structured(DosageObject dose) {
    return dose instanceof DosageObject.Simple || dose instanceof DosageObject.Range;
  }

  /** Relation to a meal 1-3 as qualified event timing where exact (M23-DOS-16). */
  private static List<Entry> mealTimings(Integer relMeal, List<Entry> entries) {
    if (relMeal == null || relMeal < 1 || relMeal > 3 || !mealAsTiming(relMeal, entries)) {
      return entries;
    }
    List<Entry> result = new ArrayList<>();
    for (Entry e : entries) {
      if (e.when().isEmpty()) {
        result.add(
            e.withWhen(
                List.of(
                    switch (relMeal) {
                      case 1 -> EventTiming.AC;
                      case 2 -> EventTiming.C;
                      default -> EventTiming.PC;
                    })));
      } else {
        result.add(e.withWhen(List.of(qualified(relMeal, e.when().get(0)))));
      }
    }
    return result;
  }

  /** True if every entry can carry the meal relation as {@code when}. */
  private static boolean mealAsTiming(int relMeal, List<Entry> entries) {
    for (Entry e : entries) {
      if (!e.timeOfDay().isEmpty()) {
        return false;
      }
      if (!e.when().isEmpty() && qualified(relMeal, e.when().get(0)) == null) {
        return false;
      }
    }
    return true;
  }

  private static EventTiming qualified(int relMeal, EventTiming segment) {
    return switch (segment) {
      case MORN -> relMeal == 1 ? EventTiming.ACM : relMeal == 2 ? EventTiming.CM : EventTiming.PCM;
      case NOON -> relMeal == 1 ? EventTiming.ACD : relMeal == 2 ? EventTiming.CD : EventTiming.PCD;
      case EVE -> relMeal == 1 ? EventTiming.ACV : relMeal == 2 ? EventTiming.CV : EventTiming.PCV;
      default -> null;
    };
  }

  /** Meal relations that are not timings: the localized label as additional instruction. */
  @MappingRule("M23-DOS-16")
  private static void meal(
      Posology pos,
      Dosage base,
      boolean asTiming,
      String language,
      String posPath,
      MappingScope scope) {
    Integer relMeal = pos.relMeal();
    if (relMeal == null) {
      return;
    }
    if (asTiming) {
      return;
    }
    String label = PosologyText.mealLabel(relMeal, language);
    if (label == null) {
      return;
    }
    base.addAdditionalInstruction(new CodeableConcept().setText(label));
    scope
        .report()
        .add(
            ReportEntry.warning(
                    ReasonCode.UNMAPPABLE_ELEMENT,
                    Phase.MAP,
                    posPath + "/relMeal",
                    "Relation to a meal kept as text of Dosage.additionalInstruction")
                .withTargetPath("Dosage.additionalInstruction.text"));
  }

  private static void apply(Entry e, Dosage dosage, String unit) {
    TimingRepeatComponent repeat = dosage.getTiming().getRepeat();
    e.when().forEach(repeat::addWhen);
    e.timeOfDay().forEach(repeat::addTimeOfDay);
    e.days().forEach(repeat::addDayOfWeek);
    if (e.count() != null) {
      repeat.setCount(e.count());
    }
    if (e.frequency() != null) {
      repeat.setFrequency(e.frequency());
      repeat.setPeriod(e.period());
      repeat.setPeriodUnit(units(e.periodUnit()));
    }
    switch (e.dose()) {
      case DosageObject.Simple s -> dosage.addDoseAndRate().setDose(quantity(s.a(), unit));
      case DosageObject.Range r ->
          dosage
              .addDoseAndRate()
              .setDose(
                  new Range().setLow(quantity(r.aMin(), unit)).setHigh(quantity(r.aMax(), unit)));
      case DosageObject.FromTo f -> throw new IllegalStateException("not structured");
    }
  }

  private static void text(
      Posology pos,
      Dosage base,
      String posPath,
      String language,
      MappingScope scope,
      String subject) {
    base.setText(PosologyText.describe(pos, language));
    scope
        .report()
        .add(
            ReportEntry.warning(
                    ReasonCode.DOSAGE_FREE_TEXT_FALLBACK,
                    Phase.MAP,
                    subject.equals("The unit") ? posPath + "/unit" : posPath + "/po",
                    subject
                        + " cannot be represented as structured CH EMED dosage; carried as"
                        + " Dosage.text")
                .withTargetPath("Dosage.text")
                .withClinicalLoss(true));
  }

  @MappingRule("M23-DOS-13")
  private static void route(Posology pos, Dosage base, String posPath, MappingScope scope) {
    if (!present(pos.roa())) {
      return;
    }
    String display = ROUTES.get(pos.roa().trim());
    if (display != null) {
      base.setRoute(
          new CodeableConcept(
              new Coding().setSystem(Profiles.EDQM).setCode(pos.roa().trim()).setDisplay(display)));
      return;
    }
    scope
        .report()
        .add(
            ReportEntry.warning(
                    ReasonCode.UNMAPPABLE_ELEMENT,
                    Phase.MAP,
                    posPath + "/roa",
                    "Route of administration is not in the CH Term EDQM value set")
                .withTargetPath("Dosage.route")
                .withClinicalLoss(true));
  }

  @MappingRule("M23-DOS-14")
  private static void method(Posology pos, Dosage base, String posPath, MappingScope scope) {
    if (!present(pos.moa())) {
      return;
    }
    String[] edqm = METHODS.get(pos.moa().trim());
    if (edqm != null) {
      base.setMethod(
          new CodeableConcept(
              new Coding().setSystem(Profiles.EDQM).setCode(edqm[1]).setDisplay(edqm[2])));
      return;
    }
    scope
        .report()
        .add(
            ReportEntry.warning(
                    ReasonCode.UNMAPPABLE_ELEMENT,
                    Phase.MAP,
                    posPath + "/moa",
                    "Method of administration has no confirmed EDQM mapping")
                .withTargetPath("Dosage.method")
                .withClinicalLoss(true));
  }

  /** CH Term code {system, code, display} of a CDTYP 9 unit, or null (M23-DOS-15). */
  @MappingRule("M23-DOS-15")
  public static String[] unitCode(String unit) {
    if (!present(unit)) {
      return null;
    }
    String[] shared = Units.code(unit);
    return shared != null ? shared : EXTRA_UNITS.get(unit.trim().toUpperCase(Locale.ROOT));
  }

  private static Quantity quantity(BigDecimal value, String unit) {
    String[] code = unitCode(unit);
    Quantity q = new Quantity();
    q.setValue(value);
    q.setUnit(unit);
    q.setSystem(code[0]);
    q.setCode(code[1]);
    return q;
  }

  private static UnitsOfTime units(TimeUnit unit) {
    return switch (unit) {
      case SECOND -> UnitsOfTime.S;
      case MINUTE -> UnitsOfTime.MIN;
      case HOUR -> UnitsOfTime.H;
      case DAY -> UnitsOfTime.D;
      case WEEK -> UnitsOfTime.WK;
      case MONTH -> UnitsOfTime.MO;
      case YEAR -> UnitsOfTime.A;
    };
  }

  /** {@code hh:mm} or {@code hh:mm:ss} as FHIR time {@code hh:mm:ss}. */
  static String timeOfDay(String dt) {
    return dt.length() == 5 ? dt + ":00" : dt;
  }

  private static Map<String, String> load(String resource, int column) {
    Map<String, String> map = new HashMap<>();
    loadRows(resource).forEach((k, v) -> map.put(k, v[column]));
    return Map.copyOf(map);
  }

  private static Map<String, String[]> loadRows(String resource) {
    Map<String, String[]> rows = new HashMap<>();
    try (InputStream in = PosologyToFhirMapper.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing resource " + resource);
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      boolean header = true;
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        if (line.startsWith("#") || line.isBlank()) {
          continue;
        }
        if (header) {
          header = false;
          continue;
        }
        String[] row = line.split(";", -1);
        rows.put(row[0], row);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return Map.copyOf(rows);
  }
}
