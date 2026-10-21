package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.chmed16a.model.Posology;
import ch.emed.converter.chmed16a.model.TakingTime;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Timing.EventTiming;

/**
 * CHMED16A Posology → CH EMED Dosage (base entry + split entries, mapping spec M-DOS-*). Never
 * produces a clinically different dosage: what cannot be represented exactly becomes free text and
 * is reported with clinical loss.
 */
public final class DosageToFhirMapper {

  private static final EventTiming[] D_EVENTS = {
    EventTiming.MORN, EventTiming.NOON, EventTiming.EVE, EventTiming.NIGHT
  };

  /**
   * CHMED16A route (INDEX CDTYP 26) → EDQM route of administration. Only unambiguous mappings are
   * listed; extend after domain review (task T116).
   */
  public static final Map<String, String[]> ROUTES =
      Map.of("PO", new String[] {"20053000", "Oral use"});

  private static final String UCUM = "http://unitsofmeasure.org";

  /**
   * CHMED16A unit (INDEX CDTYP 9, case-insensitive) → code of the CH Term UnitCode value set
   * {system, code, display}. Only unambiguous mappings are listed; extend after domain review (task
   * T116). Unmapped units lead to a free-text dosage (CH EMED requires coded dose units).
   */
  public static final Map<String, String[]> UNITS =
      Map.ofEntries(
          Map.entry("STK", new String[] {Profiles.SNOMED, "767525000", "Unit"}),
          Map.entry("MG", new String[] {UCUM, "mg", "milligram"}),
          Map.entry("G", new String[] {UCUM, "g", "gram"}),
          Map.entry("UG", new String[] {UCUM, "ug", "microgram"}),
          Map.entry("MCG", new String[] {UCUM, "ug", "microgram"}),
          Map.entry("ML", new String[] {UCUM, "mL", "milliliter"}),
          Map.entry("L", new String[] {UCUM, "L", "liter"}),
          Map.entry("IE", new String[] {UCUM, "[iU]", "international unit"}),
          Map.entry("MMOL", new String[] {UCUM, "mmol", "millimole"}),
          Map.entry(
              "TR", new String[] {Profiles.SNOMED, "732994000", "Drop (unit of presentation)"}),
          Map.entry(
              "HUB",
              new String[] {Profiles.SNOMED, "732981002", "Actuation (unit of presentation)"}));

  private DosageToFhirMapper() {}

  @MappingRule({
    "M-DOS-01",
    "M-DOS-02",
    "M-DOS-03",
    "M-DOS-04",
    "M-DOS-05",
    "M-DOS-06",
    "M-DOS-07",
    "M-DOS-08"
  })
  public static List<Dosage> map(
      Posology pos, Medicament m, String posPath, String language, MappingScope scope) {
    String medPath = posPath.substring(0, posPath.indexOf("/Pos/"));
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
    if (pos.isReserve()) {
      base.setAsNeeded(new BooleanType(true));
    }
    if (present(m.roa())) {
      String[] edqm = ROUTES.get(m.roa().trim().toUpperCase(java.util.Locale.ROOT));
      if (edqm != null) {
        base.setRoute(
            new CodeableConcept(
                    new Coding().setSystem(Profiles.EDQM).setCode(edqm[0]).setDisplay(edqm[1]))
                .setText(m.roa()));
      } else {
        scope
            .report()
            .add(
                ReportEntry.warning(
                        ReasonCode.UNMAPPABLE_ELEMENT,
                        Phase.MAP,
                        medPath + "/Roa",
                        "Route of administration has no confirmed EDQM mapping; CH EMED requires a"
                            + " coded route")
                    .withTargetPath("Dosage.route")
                    .withClinicalLoss(true));
      }
    }
    if (present(m.appInstr())) {
      base.setPatientInstruction(m.appInstr());
    }

    List<Dosage> result = new ArrayList<>();
    result.add(base);
    if (!pos.tt().isEmpty()) {
      takingTimes(pos, m, posPath, base, scope);
    } else if (!pos.d().isEmpty()) {
      simplified(pos, m, posPath, base, result, scope);
      if (pos.cyDu() != null) {
        scope
            .report()
            .add(
                ReportEntry.information(
                    ReasonCode.UNMAPPABLE_ELEMENT,
                    Phase.MAP,
                    posPath + "/CyDu",
                    "CyDu is ignored without TT (CHMED16A specification)"));
      }
    }
    return result;
  }

  /**
   * Dosage of a medicament without posology: CH EMED requires a dosage, so the localized note
   * "dosage not given" is its text; instruction and route of the medicament are kept.
   */
  public static Dosage noPosology(
      Medicament m, String note, String medPath, String language, MappingScope scope) {
    Dosage base =
        map(
                new Posology(null, null, null, null, List.of(), List.of()),
                m,
                medPath + "/Pos/0",
                language,
                scope)
            .get(0);
    base.setText(note);
    return base;
  }

  private static void simplified(
      Posology pos,
      Medicament m,
      String posPath,
      Dosage base,
      List<Dosage> result,
      MappingScope scope) {
    List<Integer> nonZero = new ArrayList<>();
    for (int i = 0; i < pos.d().size(); i++) {
      if (pos.d().get(i).signum() != 0) {
        nonZero.add(i);
      }
    }
    if (nonZero.isEmpty()) {
      scope
          .report()
          .add(
              ReportEntry.information(
                  ReasonCode.DERIVED_VALUE,
                  Phase.MAP,
                  posPath + "/D",
                  "All doses are 0; no dose quantity is set"));
      return;
    }
    if (unitCode(m.unit()) == null) {
      StringBuilder text = new StringBuilder();
      for (int index : nonZero) {
        if (text.length() > 0) {
          text.append("; ");
        }
        text.append(D_EVENTS[index].toCode())
            .append(' ')
            .append(pos.d().get(index).stripTrailingZeros().toPlainString());
        if (present(m.unit())) {
          text.append(' ').append(m.unit());
        }
      }
      base.setText(text.toString());
      unitFallback(m, posPath, scope);
      return;
    }
    Dosage current = base;
    for (int k = 0; k < nonZero.size(); k++) {
      int index = nonZero.get(k);
      if (k > 0) {
        current = new Dosage();
        result.add(current);
      }
      current.getTiming().getRepeat().addWhen(D_EVENTS[index]);
      current.addDoseAndRate().setDose(quantity(pos.d().get(index), m.unit()));
    }
    if (nonZero.size() > 1) {
      for (int k = 0; k < result.size(); k++) {
        result.get(k).setSequence(k + 1);
      }
    }
  }

  private static void takingTimes(
      Posology pos, Medicament m, String posPath, Dosage base, MappingScope scope) {
    List<TakingTime> tts = pos.tt();
    BigDecimal dose = pos.isReserve() ? tts.get(0).a() : tts.get(0).doFrom();
    boolean simple = pos.effectiveCycleSeconds() == Posology.DEFAULT_CYCLE_SECONDS && dose != null;
    for (TakingTime tt : tts) {
      BigDecimal ttDose = pos.isReserve() ? tt.a() : tt.doFrom();
      simple &=
          tt.off() != null
              && tt.off() < Posology.DEFAULT_CYCLE_SECONDS
              && (tt.du() == null || tt.du() == 0)
              && (tt.doTo() == null || tt.doTo().compareTo(tt.doFrom()) == 0)
              && tt.ma() == null
              && ttDose != null
              && ttDose.compareTo(dose) == 0;
    }
    if (simple && unitCode(m.unit()) == null) {
      unitFallback(m, posPath, scope);
      base.setText(describe(pos, m));
      return;
    }
    if (simple) {
      for (TakingTime tt : tts) {
        base.getTiming().getRepeat().addTimeOfDay(timeOfDay(tt.off()));
      }
      base.addDoseAndRate().setDose(quantity(dose, m.unit()));
      return;
    }
    base.setText(describe(pos, m));
    scope
        .report()
        .add(
            ReportEntry.warning(
                    ReasonCode.DOSAGE_FREE_TEXT_FALLBACK,
                    Phase.MAP,
                    posPath + "/TT",
                    "Taking times cannot be represented as structured CH EMED dosage; carried as"
                        + " Dosage.text")
                .withTargetPath("Dosage.text")
                .withClinicalLoss(true));
  }

  private static String describe(Posology pos, Medicament m) {
    StringBuilder sb = new StringBuilder();
    if (pos.effectiveCycleSeconds() != Posology.DEFAULT_CYCLE_SECONDS) {
      sb.append("Cycle ").append(pos.effectiveCycleSeconds()).append(" s: ");
    }
    String unit = present(m.unit()) ? " " + m.unit() : "";
    List<String> parts = new ArrayList<>();
    for (TakingTime tt : pos.tt()) {
      StringBuilder part = new StringBuilder(offset(tt.off()));
      BigDecimal from = pos.isReserve() ? tt.a() : tt.doFrom();
      if (from != null) {
        part.append(' ').append(from.stripTrailingZeros().toPlainString());
        if (tt.doTo() != null && tt.doTo().compareTo(from) != 0) {
          part.append('-').append(tt.doTo().stripTrailingZeros().toPlainString());
        }
        part.append(unit);
      }
      if (tt.du() != null && tt.du() > 0) {
        part.append(" over ").append(tt.du()).append(" s");
      }
      if (tt.ma() != null) {
        part.append(" (max ")
            .append(tt.ma().stripTrailingZeros().toPlainString())
            .append(unit)
            .append(")");
      }
      parts.add(part.toString());
    }
    return sb.append(String.join("; ", parts)).toString();
  }

  private static String offset(Long off) {
    long seconds = off == null ? 0 : off;
    long days = seconds / 86_400;
    long rest = seconds % 86_400;
    String time = String.format("%02d:%02d", rest / 3600, (rest % 3600) / 60);
    return days > 0 ? "day " + (days + 1) + " " + time : time;
  }

  private static String timeOfDay(long off) {
    return String.format("%02d:%02d:%02d", off / 3600, (off % 3600) / 60, off % 60);
  }

  private static void unitFallback(Medicament m, String posPath, MappingScope scope) {
    String medPath = posPath.substring(0, posPath.indexOf("/Pos/"));
    scope
        .report()
        .add(
            ReportEntry.warning(
                    ReasonCode.DOSAGE_FREE_TEXT_FALLBACK,
                    Phase.MAP,
                    medPath + "/Unit",
                    "Dose unit has no confirmed mapping to the CH Term UnitCode value set; dosage"
                        + " carried as Dosage.text")
                .withTargetPath("Dosage.text")
                .withClinicalLoss(true));
  }

  static String[] unitCode(String unit) {
    return present(unit) ? UNITS.get(unit.trim().toUpperCase(java.util.Locale.ROOT)) : null;
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
}
