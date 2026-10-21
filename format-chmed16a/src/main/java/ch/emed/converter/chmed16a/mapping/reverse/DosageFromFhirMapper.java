package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.mapping.DosageToFhirMapper;
import ch.emed.converter.chmed16a.mapping.Labels;
import ch.emed.converter.chmed16a.mapping.MappingRule;
import ch.emed.converter.chmed16a.mapping.Profiles;
import ch.emed.converter.chmed16a.model.Posology;
import ch.emed.converter.chmed16a.model.TakingTime;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Dosage.DosageDoseAndRateComponent;
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
 * CH EMED Dosage list of one statement → CHMED16A posologies, unit, route and application
 * instruction (mapping spec M-REV-14..M-REV-21). Never produces a clinically different dosage: a
 * statement with any dosage that cannot be expressed exactly is carried as text in {@code AppInstr}
 * and reported with clinical loss.
 */
public final class DosageFromFhirMapper {

  /**
   * Mapped dosage of a statement.
   *
   * @param pos posologies (empty if none can be produced)
   * @param unit CHMED16A unit, or null
   * @param appInstr application instruction incl. text fallbacks, or null
   * @param roa CHMED16A route of administration, or null
   */
  public record Result(List<Posology> pos, String unit, String appInstr, String roa) {}

  /**
   * Target document type. A prescription has no start date, route, "as needed" flag or taking
   * times; its posology is only {@code DtTo} and {@code D}.
   *
   * @param element name of the dosage element of the source resource, used in report paths
   * @param prescription true for CHMED16A prescriptions
   * @param prescriptionDate date of the prescription (start dates equal to it are not a loss)
   */
  public record Mode(String element, boolean prescription, String prescriptionDate) {

    /** Medication plan: {@code MedicationStatement.dosage}. */
    public static final Mode PLAN = new Mode("dosage", false, null);

    /** Prescription: {@code MedicationRequest.dosageInstruction}. */
    public static Mode prescription(String prescriptionDate) {
      return new Mode("dosageInstruction", true, prescriptionDate);
    }
  }

  private static final List<String> SLOTS = List.of("MORN", "NOON", "EVE", "NIGHT");
  private static final String UCUM_TIME_DAY = UnitsOfTime.D.toCode();
  private static final Map<String, String> UNIT_BY_CODE = unitByCode();
  private static final Map<String, String> ROA_BY_EDQM = roaByEdqm();

  private DosageFromFhirMapper() {}

  /**
   * Maps the dosages of one statement.
   *
   * @param dosages {@code MedicationStatement.dosage}
   * @param path path of the statement, e.g. {@code MedicationStatement[0]}
   * @param scope mapping scope
   * @return the mapped dosage
   */
  @MappingRule({
    "M-REV-14",
    "M-REV-15",
    "M-REV-16",
    "M-REV-17",
    "M-REV-18",
    "M-REV-19",
    "M-REV-20",
    "M-REV-21",
    "M-REV-40"
  })
  public static Result map(List<Dosage> dosages, String path, ReverseScope scope) {
    return map(dosages, path, scope, Mode.PLAN);
  }

  /** Maps the dosages for a medication plan or a prescription. */
  public static Result map(List<Dosage> dosages, String path, ReverseScope scope, Mode mode) {
    if (dosages.isEmpty()) {
      return new Result(List.of(), null, null, null);
    }
    if (isNoDosageNote(dosages)) {
      scope.derived(
          path + "." + mode.element() + "[0]",
          "The note \"dosage not given\" is recognised; no posology is set");
      Parsed note = parse(dosages.get(0), path + "." + mode.element() + "[0]", mode, scope);
      List<String> kept =
          instructions(List.of(note), path, mode, scope).stream()
              .filter(i -> !Labels.all(Labels.Key.NO_DOSAGE).contains(i.trim()))
              .toList();
      return new Result(
          List.of(),
          null,
          kept.isEmpty() ? null : String.join("; ", kept),
          route(List.of(note), mode, scope));
    }

    List<Parsed> parsed = new ArrayList<>();
    for (int i = 0; i < dosages.size(); i++) {
      parsed.add(parse(dosages.get(i), path + "." + mode.element() + "[" + i + "]", mode, scope));
    }
    String roa = route(parsed, mode, scope);
    List<String> instructions = instructions(parsed, path, mode, scope);
    String unit = unit(parsed);
    inheritBounds(parsed);
    Map<GroupKey, List<Parsed>> groups = groups(parsed);
    checkGroups(groups, mode);

    boolean fallback = parsed.stream().anyMatch(p -> p.cause != null);
    for (Parsed p : parsed) {
      if (p.cause != null) {
        scope
            .report()
            .add(
                ReportEntry.warning(
                        ReasonCode.DOSAGE_FREE_TEXT_FALLBACK,
                        Phase.MAP,
                        p.path,
                        "Dosage cannot be expressed exactly in CHMED16A ("
                            + p.cause
                            + "); carried as text in AppInstr")
                    .withTargetPath("Medicament.AppInstr")
                    .withClinicalLoss(true));
      }
    }

    List<Posology> posologies = new ArrayList<>();
    List<String> dateTexts = new ArrayList<>();
    for (Map.Entry<GroupKey, List<Parsed>> group : groups.entrySet()) {
      GroupKey key = group.getKey();
      if (carriesPosology(group.getValue(), key, unit, fallback, mode)) {
        posologies.add(posology(group.getValue(), key, fallback, mode, scope));
      } else if (!fallback && (key.start() != null || key.end() != null)) {
        dateTexts.add(dates(key));
        scope
            .report()
            .add(
                ReportEntry.warning(
                        ReasonCode.DOSAGE_FREE_TEXT_FALLBACK,
                        Phase.MAP,
                        path + "." + mode.element(),
                        "The dosage has dates but cannot be carried as posology (no dose or no unit); the"
                            + " dates are carried as text in AppInstr")
                    .withTargetPath("Medicament.AppInstr"));
      }
    }

    List<String> parts = new ArrayList<>(instructions);
    if (fallback) {
      List<String> described = new ArrayList<>();
      for (Parsed p : parsed) {
        GroupKey key = p.key();
        String text = p.dosage.hasText() ? p.dosage.getText() : describe(p);
        boolean datesInPos = carriesPosology(List.of(p), key, unit, true, mode);
        if (!datesInPos && (key.start() != null || key.end() != null)) {
          text = text + "; " + dates(key);
        }
        described.add(text);
      }
      parts.add(String.join(" / ", described));
    } else if (!dateTexts.isEmpty()) {
      parts.add(String.join(" / ", dateTexts));
    }
    parts = new ArrayList<>(new LinkedHashSet<>(parts));
    return new Result(
        List.copyOf(posologies), unit, parts.isEmpty() ? null : String.join("; ", parts), roa);
  }

  // ---------------------------------------------------------------------------------------------
  // parsing

  private static Parsed parse(Dosage d, String path, Mode mode, ReverseScope scope) {
    Parsed p = new Parsed(d, path);
    TimingRepeatComponent r =
        d.hasTiming() && d.getTiming().hasRepeat() ? d.getTiming().getRepeat() : null;
    boolean structure = false;
    if (d.hasTiming() && d.getTiming().hasEvent()) {
      p.cause("event");
    }
    if (r != null) {
      structure |= timing(p, r, scope);
    }
    if (d.hasAsNeededBooleanType()) {
      p.asNeeded = d.getAsNeededBooleanType().booleanValue();
    } else if (d.hasAsNeededCodeableConcept()) {
      p.asNeeded = true;
      scope.unmapped(path + ".asNeeded", "The reason for \"as needed\" is not carried over");
    }
    if (d.getDoseAndRate().size() > 1) {
      p.cause("several doses");
      structure = true;
    } else if (d.getDoseAndRate().size() == 1) {
      structure = true;
      dose(p, d.getDoseAndRate().get(0));
    }
    if (mode.prescription()) {
      if (p.asNeeded) {
        p.cause("as needed");
      }
      if (!p.timeOfDay.isEmpty()) {
        p.cause("time of day");
      }
    }
    if ((!p.when.isEmpty() || !p.timeOfDay.isEmpty()) && !p.hasDose) {
      p.cause("no dose");
    }
    if (p.hasDose && p.when.isEmpty() && p.timeOfDay.isEmpty()) {
      p.cause("no time of day");
    }
    if (!p.when.isEmpty() && !p.timeOfDay.isEmpty()) {
      p.cause("time of day and day period");
    }
    if (!structure && d.hasText()) {
      p.cause("free text");
    }
    return p;
  }

  /** Reads the repeat element; returns true if it carries structure beyond the bounds. */
  private static boolean timing(Parsed p, TimingRepeatComponent r, ReverseScope scope) {
    boolean structure = false;
    if (r.hasBoundsPeriod()) {
      Period bounds = r.getBoundsPeriod();
      p.start = bounds.hasStart() ? bounds.getStartElement().getValueAsString() : null;
      p.end = bounds.hasEnd() ? bounds.getEndElement().getValueAsString() : null;
    }
    if (r.hasBoundsDuration() || r.hasBoundsRange()) {
      p.cause("bounds");
    }
    for (Enumeration<EventTiming> when : r.getWhen()) {
      String code = when.getValue() == null ? null : when.getValue().toCode();
      if (code == null || !SLOTS.contains(code)) {
        p.cause("day period");
      } else {
        p.when.add(code);
      }
      structure = true;
    }
    for (Enumeration<DayOfWeek> day : r.getDayOfWeek()) {
      p.dayOfWeek.add(day.getValue().toCode());
      p.cause("day of week");
      structure = true;
    }
    for (TimeType time : r.getTimeOfDay()) {
      try {
        p.timeOfDay.add(LocalTime.parse(time.getValue()).toSecondOfDay());
        p.timeOfDayText.add(time.getValue());
      } catch (DateTimeParseException | NullPointerException e) {
        p.cause("time of day");
      }
      structure = true;
    }
    if (r.hasFrequency() && r.getFrequency() != 1) {
      p.cause("frequency");
      p.extra.add("frequency=" + r.getFrequency());
      structure = true;
    }
    if (r.hasPeriod()
        && (r.getPeriod().compareTo(BigDecimal.ONE) != 0
            || !r.hasPeriodUnit()
            || !UCUM_TIME_DAY.equals(r.getPeriodUnit().toCode()))) {
      p.cause("period");
      p.extra.add(
          "period=" + plain(r.getPeriod()) + (r.hasPeriodUnit() ? r.getPeriodUnit().toCode() : ""));
      structure = true;
    }
    if (r.hasFrequencyMax() || r.hasPeriodMax()) {
      p.cause("maximum frequency");
      structure = true;
    }
    if (r.hasCount() || r.hasCountMax()) {
      p.cause("count");
      p.extra.add("count=" + r.getCount());
      structure = true;
    }
    if (r.hasDuration() || r.hasDurationMax()) {
      p.cause("duration");
      p.extra.add("duration=" + plain(r.getDuration()));
      structure = true;
    }
    if (r.hasOffset()) {
      p.cause("offset");
      p.extra.add("offset=" + r.getOffset());
      structure = true;
    }
    return structure;
  }

  private static void dose(Parsed p, DosageDoseAndRateComponent dar) {
    if (dar.hasRate()) {
      p.cause("rate");
    }
    if (dar.hasDoseQuantity()) {
      Quantity q = dar.getDoseQuantity();
      p.hasDose = true;
      p.dose = q.getValue();
      p.doseText = plain(q.getValue());
      p.unitLabel = label(q);
      p.unitMapped = mapped(q);
      if (q.getValue() == null) {
        p.cause("dose without value");
      }
    } else if (dar.hasDoseRange()) {
      Range range = dar.getDoseRange();
      p.hasDose = true;
      p.cause("dose range");
      Quantity low = range.getLow();
      Quantity high = range.getHigh();
      p.doseText = plain(low.getValue()) + "-" + plain(high.getValue());
      p.unitLabel = label(low);
      p.unitMapped = mapped(low);
      if (p.unitMapped != null && !p.unitMapped.equals(mapped(high))) {
        p.unitMapped = null;
        p.unitLabel = label(low);
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // cross-dosage rules

  private static String route(List<Parsed> parsed, Mode mode, ReverseScope scope) {
    String roa = null;
    for (Parsed p : parsed) {
      if (!p.dosage.hasRoute()) {
        continue;
      }
      if (mode.prescription()) {
        scope.unmappedWarning(
            p.path + ".route",
            "A prescription has no route of administration; it is not carried over",
            true);
        continue;
      }
      String mapped =
          p.dosage.getRoute().getCoding().stream()
              .filter(c -> Profiles.EDQM.equals(c.getSystem()))
              .map(c -> ROA_BY_EDQM.get(c.getCode()))
              .filter(java.util.Objects::nonNull)
              .findFirst()
              .orElse(null);
      if (mapped == null) {
        scope.unmappedWarning(
            p.path + ".route",
            "The route of administration has no confirmed CHMED16A mapping; not carried over",
            true);
      } else if (roa == null) {
        roa = mapped;
      }
    }
    return roa;
  }

  private static List<String> instructions(
      List<Parsed> parsed, String path, Mode mode, ReverseScope scope) {
    Set<String> distinct = new LinkedHashSet<>();
    for (Parsed p : parsed) {
      if (p.dosage.hasPatientInstruction() && present(p.dosage.getPatientInstruction())) {
        distinct.add(p.dosage.getPatientInstruction());
      }
    }
    if (distinct.size() > 1) {
      scope.derived(
          path + "." + mode.element() + ".patientInstruction",
          "Different patient instructions are joined");
    }
    return List.copyOf(distinct);
  }

  /** The unit of the medicament; marks dosages whose unit is unknown or conflicting. */
  private static String unit(List<Parsed> parsed) {
    String unit = null;
    boolean usable = true;
    for (Parsed p : parsed) {
      if (!p.hasDose) {
        continue;
      }
      if (p.unitMapped == null) {
        p.cause("unit");
        usable = false;
      } else if (unit == null) {
        unit = p.unitMapped;
      } else if (!unit.equals(p.unitMapped)) {
        p.cause("different units");
        usable = false;
      }
    }
    return usable ? unit : null;
  }

  /** Dosages without own bounds share the bounds of the preceding base entry (M-DOS-02). */
  private static void inheritBounds(List<Parsed> parsed) {
    String start = null;
    String end = null;
    for (Parsed p : parsed) {
      if (p.hasBounds()) {
        start = p.start;
        end = p.end;
      }
      p.effectiveStart = start;
      p.effectiveEnd = end;
    }
  }

  private static Map<GroupKey, List<Parsed>> groups(List<Parsed> parsed) {
    Map<GroupKey, List<Parsed>> groups = new LinkedHashMap<>();
    for (Parsed p : parsed) {
      groups.computeIfAbsent(p.key(), k -> new ArrayList<>()).add(p);
    }
    return groups;
  }

  private static void checkGroups(Map<GroupKey, List<Parsed>> groups, Mode mode) {
    boolean first = true;
    for (Map.Entry<GroupKey, List<Parsed>> entry : groups.entrySet()) {
      List<Parsed> group = entry.getValue();
      if (mode.prescription() && !first) {
        group.forEach(p -> p.cause("several periods"));
      }
      if (!mode.prescription() && entry.getKey().start() == null) {
        // a posology of a medication plan needs DtFrom; doses must not get lost
        group.stream()
            .filter(p -> !p.when.isEmpty() || !p.timeOfDay.isEmpty())
            .forEach(p -> p.cause("no start date"));
      }
      first = false;
      Set<String> slots = new LinkedHashSet<>();
      boolean timeOfDay = false;
      boolean daySlots = false;
      for (Parsed p : group) {
        if (!p.timeOfDay.isEmpty()) {
          if (timeOfDay || daySlots) {
            p.cause("several timing forms");
          }
          timeOfDay = true;
        }
        if (!p.when.isEmpty()) {
          if (timeOfDay) {
            p.cause("several timing forms");
          }
          daySlots = true;
          for (String slot : p.when) {
            if (!slots.add(slot)) {
              p.cause("day period used twice");
            }
          }
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // output

  /** Whether the group becomes a posology (otherwise its dates go into the description). */
  private static boolean carriesPosology(
      List<Parsed> group, GroupKey key, String unit, boolean fallback, Mode mode) {
    if (unit == null) {
      return false;
    }
    if (mode.prescription()) {
      return !fallback && group.stream().anyMatch(p -> !p.when.isEmpty());
    }
    return key.start() != null;
  }

  private static Posology posology(
      List<Parsed> group, GroupKey key, boolean fallback, Mode mode, ReverseScope scope) {
    List<BigDecimal> doses = new ArrayList<>();
    List<TakingTime> takingTimes = new ArrayList<>();
    if (!fallback) {
      BigDecimal[] slots = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
      boolean any = false;
      for (Parsed p : group) {
        for (String slot : p.when) {
          slots[SLOTS.indexOf(slot)] = p.dose;
          any = true;
        }
        for (int seconds : p.timeOfDay) {
          takingTimes.add(
              p.asNeeded
                  ? new TakingTime((long) seconds, null, null, null, p.dose, null)
                  : new TakingTime((long) seconds, null, p.dose, null, null, null));
        }
      }
      if (any) {
        doses.addAll(List.of(slots));
      }
    }
    boolean asNeeded = group.stream().anyMatch(p -> p.asNeeded);
    if (mode.prescription()) {
      startOfPrescription(group, key, mode, scope);
      return new Posology(
          null, datePart(key.end(), group, false, scope), null, null, doses, takingTimes);
    }
    return new Posology(
        datePart(key.start(), group, true, scope),
        datePart(key.end(), group, false, scope),
        null,
        asNeeded ? 1 : null,
        doses,
        takingTimes);
  }

  /** A prescription has no start date: reported, a loss unless it is the prescription date. */
  private static void startOfPrescription(
      List<Parsed> group, GroupKey key, Mode mode, ReverseScope scope) {
    if (key.start() == null) {
      return;
    }
    Parsed owner =
        group.stream().filter(p -> key.start().equals(p.start)).findFirst().orElse(group.get(0));
    String path = owner.path + ".timing.repeat.boundsPeriod.start";
    String day = key.start().length() >= 10 ? key.start().substring(0, 10) : key.start();
    if (day.equals(mode.prescriptionDate())) {
      scope.unmapped(path, "The start date equals the prescription date; a prescription has none");
    } else {
      scope.unmappedWarning(
          path,
          "A prescription has no start date; the start of the therapy is not carried over",
          true);
    }
  }

  /** Date part of a date or date-time; a lost time of day is reported. */
  private static String datePart(
      String value, List<Parsed> group, boolean start, ReverseScope scope) {
    if (value == null) {
      return null;
    }
    if (value.length() > 10 && value.charAt(10) == 'T') {
      Parsed owner =
          group.stream()
              .filter(p -> value.equals(start ? p.start : p.end))
              .findFirst()
              .orElse(group.get(0));
      scope.unmappedWarning(
          owner.path + ".timing.repeat.boundsPeriod." + (start ? "start" : "end"),
          "CHMED16A carries dates only; the time of day is not carried over",
          false);
      return value.substring(0, 10);
    }
    return value;
  }

  private static String dates(GroupKey key) {
    List<String> parts = new ArrayList<>();
    if (key.start() != null) {
      parts.add("from=" + key.start());
    }
    if (key.end() != null) {
      parts.add("to=" + key.end());
    }
    return String.join("; ", parts);
  }

  /** Language-neutral description of a dosage that cannot be expressed exactly. */
  private static String describe(Parsed p) {
    List<String> parts = new ArrayList<>();
    if (p.hasDose) {
      parts.add("dose=" + p.doseText + (p.unitLabel == null ? "" : " " + p.unitLabel));
    }
    if (!p.when.isEmpty()) {
      parts.add("when=" + String.join(",", p.when));
    }
    if (!p.dayOfWeek.isEmpty()) {
      parts.add("dayOfWeek=" + String.join(",", p.dayOfWeek));
    }
    if (!p.timeOfDayText.isEmpty()) {
      parts.add("timeOfDay=" + String.join(",", p.timeOfDayText));
    }
    parts.addAll(p.extra);
    if (p.asNeeded) {
      parts.add("asNeeded");
    }
    return String.join("; ", parts);
  }

  /**
   * The generated note "dosage not given" as text (and, in prescriptions, as patient instruction),
   * without timing or dose. An instruction or route next to it belongs to the medicament.
   */
  private static boolean isNoDosageNote(List<Dosage> dosages) {
    if (dosages.size() != 1) {
      return false;
    }
    Dosage d = dosages.get(0);
    String note = d.hasText() ? d.getText() : d.getPatientInstruction();
    return present(note)
        && Labels.all(Labels.Key.NO_DOSAGE).contains(note.trim())
        && !d.hasTiming()
        && d.getDoseAndRate().isEmpty();
  }

  // ---------------------------------------------------------------------------------------------
  // units and helpers

  private static String key(Quantity q) {
    return q.getSystem() + "|" + q.getCode();
  }

  private static String mapped(Quantity q) {
    return q.hasSystem() && q.hasCode() ? UNIT_BY_CODE.get(key(q)) : null;
  }

  private static String label(Quantity q) {
    String mapped = mapped(q);
    if (mapped != null) {
      return mapped;
    }
    if (q.hasCode()) {
      return q.getCode();
    }
    return q.hasUnit() ? q.getUnit() : null;
  }

  private static String plain(BigDecimal value) {
    return value == null ? "?" : value.stripTrailingZeros().toPlainString();
  }

  /** Inverse of {@link DosageToFhirMapper#UNITS}; {@code MCG} is only an alias of {@code UG}. */
  private static Map<String, String> unitByCode() {
    Map<String, String> map = new LinkedHashMap<>();
    DosageToFhirMapper.UNITS.entrySet().stream()
        .filter(e -> !e.getKey().equals("MCG"))
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> map.put(e.getValue()[0] + "|" + e.getValue()[1], e.getKey()));
    // SNOMED "Tablet (unit of presentation)", used by the official CH EMED examples
    map.put(Profiles.SNOMED + "|732936001", "STK");
    return Map.copyOf(map);
  }

  /** Inverse of {@link DosageToFhirMapper#ROUTES}. */
  private static Map<String, String> roaByEdqm() {
    Map<String, String> map = new LinkedHashMap<>();
    DosageToFhirMapper.ROUTES.forEach((roa, edqm) -> map.put(edqm[0], roa));
    return Map.copyOf(map);
  }

  /** Bounds and as-needed flag shared by the dosages of one posology. */
  private record GroupKey(String start, String end, boolean asNeeded) {}

  private static final class Parsed {
    final Dosage dosage;
    final String path;
    String start;
    String end;
    String effectiveStart;
    String effectiveEnd;
    boolean asNeeded;
    final List<String> when = new ArrayList<>();
    final List<String> dayOfWeek = new ArrayList<>();
    final List<Integer> timeOfDay = new ArrayList<>();
    final List<String> timeOfDayText = new ArrayList<>();
    final List<String> extra = new ArrayList<>();
    boolean hasDose;
    BigDecimal dose;
    String doseText;
    String unitLabel;
    String unitMapped;
    String cause;

    Parsed(Dosage dosage, String path) {
      this.dosage = dosage;
      this.path = path;
    }

    void cause(String value) {
      if (cause == null) {
        cause = value;
      }
    }

    boolean hasBounds() {
      return start != null || end != null;
    }

    GroupKey key() {
      return new GroupKey(effectiveStart, effectiveEnd, asNeeded);
    }
  }
}
