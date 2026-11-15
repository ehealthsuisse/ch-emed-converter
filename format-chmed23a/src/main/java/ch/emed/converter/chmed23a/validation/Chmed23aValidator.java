package ch.emed.converter.chmed23a.validation;

import ch.emed.converter.chmed23a.mapping.MappingRule;
import ch.emed.converter.chmed23a.model.Application;
import ch.emed.converter.chmed23a.model.DosageObject;
import ch.emed.converter.chmed23a.model.Extension;
import ch.emed.converter.chmed23a.model.HealthcareOrganization;
import ch.emed.converter.chmed23a.model.HealthcarePerson;
import ch.emed.converter.chmed23a.model.MedicalData;
import ch.emed.converter.chmed23a.model.Medicament;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.chmed23a.model.Patient;
import ch.emed.converter.chmed23a.model.PatientId;
import ch.emed.converter.chmed23a.model.Posology;
import ch.emed.converter.chmed23a.model.PosologyDetail;
import ch.emed.converter.chmed23a.model.Repetition;
import ch.emed.converter.chmed23a.model.RiskCategory;
import ch.emed.converter.chmed23a.model.SequenceObject;
import ch.emed.converter.chmed23a.model.TimeUnit;
import ch.emed.converter.chmed23a.model.TimedDosage;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Conformance of a CHMED23A document to the pinned specification (rules C23-*, research R7). There
 * is no JSON schema; the rules come from the object model, posology.md and terminology.md. Errors
 * are {@code CHMED_INVALID_FIELD} with the JSON path, never the value.
 */
public final class Chmed23aValidator {

  private static final Pattern DATE_TIME =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}:\\d{2}|Z)");
  private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
  private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?");
  private static final Pattern GESTATION = Pattern.compile("\\d+-[1-9]\\d*");
  private static final Pattern TWO_LETTERS = Pattern.compile("[A-Za-z]{2}");

  private final ReportCollector report;
  private final Phase phase;
  private boolean plan;
  private boolean prescription;

  private Chmed23aValidator(ReportCollector report, Phase phase) {
    this.report = report;
    this.phase = phase;
  }

  /**
   * Validates input (phase {@code VALIDATE_INPUT}).
   *
   * @param medication the document
   * @param report receives problems
   */
  public static void validate(Medication medication, ReportCollector report) {
    validate(medication, report, Phase.VALIDATE_INPUT);
  }

  /**
   * Validates a document.
   *
   * @param medication the document
   * @param report receives problems
   * @param phase {@code VALIDATE_INPUT} or {@code VALIDATE_OUTPUT}
   */
  public static void validate(Medication medication, ReportCollector report, Phase phase) {
    new Chmed23aValidator(report, phase).medication(medication);
  }

  @MappingRule({"C23-OBJ-01", "C23-OBJ-02", "C23-OBJ-03", "M23-DOC-03"})
  private void medication(Medication m) {
    if (m.medType() == null) {
      error("/medType", "medType is required");
      return;
    }
    if (m.medType() != Medication.MEDICATION_PLAN && m.medType() != Medication.PRESCRIPTION) {
      report.add(
          ReportEntry.error(
              ReasonCode.UNSUPPORTED_DOCUMENT_TYPE,
              phase,
              "/medType",
              (m.medType() == Medication.POLYMEDICATION_CHECK
                      ? "PolymedicationCheck (medType 2) is deprecated and not supported"
                      : "Document type is not supported")
                  + "; supported: medication plan (MP, medType 1), prescription (Rx, medType 3)"));
      return;
    }
    plan = m.isPlan();
    prescription = m.isPrescription();
    if (m.patient() == null) {
      error("/patient", "patient is required");
    } else {
      patient(m.patient());
    }
    if (m.auth() == null) {
      error("/auth", "auth is required");
    } else if (m.auth() != Medication.AUTH_HEALTHCARE_PERSON
        && m.auth() != Medication.AUTH_PATIENT) {
      error("/auth", "auth must be 1 (healthcare person) or 2 (patient)");
    } else if (m.auth() == Medication.AUTH_PATIENT && prescription) {
      error("/auth", "A prescription must be authored by a healthcare person (auth 1)");
    }
    if (m.dt() == null) {
      error("/dt", "dt is required");
    } else if (!isDateTime(m.dt())) {
      error("/dt", "dt must be a date-time with offset");
    }
    boolean authoredByPerson = m.auth() != null && m.auth() == Medication.AUTH_HEALTHCARE_PERSON;
    if (authoredByPerson && m.hcPerson() == null) {
      error("/hcPerson", "hcPerson is required when auth is 1");
    }
    if (authoredByPerson && m.hcOrg() == null) {
      error("/hcOrg", "hcOrg is required when auth is 1");
    }
    author(m.hcPerson(), m.hcOrg());
    if (prescription && m.meds().isEmpty()) {
      error("/meds", "A prescription needs at least one medicament");
    }
    gs1(m.rec(), "/rec", "GLN");
    if (plan && m.rec() != null) {
      notUsed("/rec");
    }
    for (int i = 0; i < m.meds().size(); i++) {
      medicament(m.meds().get(i), "/meds/" + i);
    }
    extensions(m.exts(), "/exts");
  }

  @MappingRule({"C23-OBJ-04", "C23-OBJ-05"})
  private void patient(Patient p) {
    required(p.fName(), "/patient/fName");
    required(p.lName(), "/patient/lName");
    if (required(p.bdt(), "/patient/bdt") && !isDate(p.bdt())) {
      error("/patient/bdt", "bdt must be a date (YYYY-MM-DD)");
    }
    if (p.gender() == null) {
      error("/patient/gender", "gender is required");
    } else if (p.gender() < Patient.MALE || p.gender() > Patient.OTHER) {
      error("/patient/gender", "gender must be 1, 2 or 3");
    }
    if (p.country() != null && !TWO_LETTERS.matcher(p.country()).matches()) {
      error("/patient/country", "country must be an ISO 3166 alpha-2 code");
    }
    if (plan) {
      if (required(p.lng(), "/patient/lng") && !TWO_LETTERS.matcher(p.lng()).matches()) {
        error("/patient/lng", "lng must be an ISO 639-1 code");
      }
    } else if (p.lng() != null) {
      notUsed("/patient/lng");
    }
    if (p.ids().isEmpty()) {
      error("/patient/ids", "At least one patient id is required");
    }
    for (int i = 0; i < p.ids().size(); i++) {
      patientId(p.ids().get(i), "/patient/ids/" + i);
    }
    if (p.mData() != null) {
      if (prescription) {
        notUsed("/patient/mData");
      } else {
        medicalData(p.mData());
      }
    }
    extensions(p.exts(), "/patient/exts");
  }

  private void patientId(PatientId id, String path) {
    required(id.val(), path + "/val");
    if (id.type() == null) {
      error(path + "/type", "type is required");
    } else if (id.type() == PatientId.LOCAL_PID) {
      required(id.sId(), path + "/sId");
    } else if (id.type() != PatientId.INSURANCE_CARD) {
      error(path + "/type", "type must be 1 or 2");
    }
  }

  @MappingRule("C23-OBJ-06")
  private void medicalData(MedicalData d) {
    if (d.dLstMen() != null && !isDate(d.dLstMen())) {
      error("/patient/mData/dLstMen", "dLstMen must be a date (YYYY-MM-DD)");
    }
    if (d.w() != null && d.w().signum() < 0) {
      error("/patient/mData/w", "Weight must not be negative");
    }
    if (d.h() != null && d.h().signum() < 0) {
      error("/patient/mData/h", "Height must not be negative");
    }
    if (d.toG() != null && !GESTATION.matcher(d.toG()).matches()) {
      error("/patient/mData/toG", "toG must have the form {week}-{day}");
    }
    for (int i = 0; i < d.rcs().size(); i++) {
      RiskCategory rc = d.rcs().get(i);
      String path = "/patient/mData/rcs/" + i;
      if (rc.id() == null) {
        error(path + "/id", "id is required");
      } else if (rc.id() < 1 || rc.id() > 7) {
        error(path + "/id", "id must be a risk category 1-7");
      }
      for (int j = 0; j < rc.rIds().size(); j++) {
        Integer category = Terminology.riskCategory(rc.rIds().get(j));
        if (category == null || !category.equals(rc.id())) {
          warning(path + "/rIds/" + j, "Risk id is not a risk of this category in terminology.md");
        }
      }
    }
    extensions(d.exts(), "/patient/mData/exts");
  }

  @MappingRule({"C23-OBJ-07", "C23-OBJ-08"})
  private void author(HealthcarePerson person, HealthcareOrganization org) {
    gs1(person == null ? null : person.gln(), "/hcPerson/gln", "GLN");
    gs1(org == null ? null : org.gln(), "/hcOrg/gln", "GLN");
    if (person != null) {
      required(person.fName(), "/hcPerson/fName");
      required(person.lName(), "/hcPerson/lName");
      if (prescription) {
        required(person.gln(), "/hcPerson/gln");
      }
      if (plan && person.zsr() != null) {
        notUsed("/hcPerson/zsr");
      }
    }
    if (org != null) {
      required(org.name(), "/hcOrg/name");
      required(org.street(), "/hcOrg/street");
      required(org.zip(), "/hcOrg/zip");
      required(org.city(), "/hcOrg/city");
      if (org.country() != null && !TWO_LETTERS.matcher(org.country()).matches()) {
        error("/hcOrg/country", "country must be an ISO 3166 alpha-2 code");
      }
      if (plan && (person == null || person.gln() == null)) {
        required(org.gln(), "/hcOrg/gln");
      }
      if (prescription && org.gln() != null) {
        notUsed("/hcOrg/gln");
      }
      if (plan && org.zsr() != null) {
        notUsed("/hcOrg/zsr");
      }
    }
    if (prescription
        && person != null
        && org != null
        && person.zsr() != null
        && org.zsr() != null) {
      error("/hcOrg/zsr", "The ZSR number may be set only once, in hcPerson or in hcOrg");
    }
  }

  @MappingRule({"C23-OBJ-09", "C23-OBJ-10", "C23-OBJ-12"})
  private void medicament(Medicament m, String path) {
    required(m.id(), path + "/id");
    if (m.idType() != null && m.idType() == Medicament.ID_GTIN) {
      gs1(m.id(), path + "/id", "GTIN");
    }
    if (m.idType() == null) {
      error(path + "/idType", "idType is required");
    } else if (m.idType() < Medicament.ID_NONE || m.idType() > Medicament.ID_PRODUCT_NUMBER) {
      error(path + "/idType", "idType must be 1-4");
    } else if (prescription && m.idType() == Medicament.ID_PRODUCT_NUMBER) {
      error(path + "/idType", "A product number (idType 4) is not allowed in a prescription");
    }
    if (plan) {
      if (m.autoMed() == null) {
        error(path + "/autoMed", "autoMed is required in a medication plan");
      }
      if (m.reps() != null) {
        notUsed(path + "/reps");
      }
      if (m.sic() != null) {
        notUsed(path + "/sic");
      }
      if (m.nbPack() != null) {
        notUsed(path + "/nbPack");
      }
    } else {
      if (m.autoMed() != null) {
        notUsed(path + "/autoMed");
      }
      if (m.prscbBy() != null) {
        notUsed(path + "/prscbBy");
      }
      repetition(m.reps(), path + "/reps");
    }
    if (m.nbPack() != null && m.nbPack().signum() <= 0) {
      error(path + "/nbPack", "nbPack must be greater than 0");
    }
    for (int i = 0; i < m.pos().size(); i++) {
      posology(m.pos().get(i), path + "/pos/" + i);
    }
    extensions(m.exts(), path + "/exts");
  }

  private void repetition(Repetition r, String path) {
    if (r == null) {
      return;
    }
    switch (r) {
      case Repetition.Number n -> atLeast(n.v(), 0, path + "/v");
      case Repetition.Duration d -> duration(d.d(), d.u(), path, "d", "u");
      case Repetition.NumberAndDuration nd -> {
        atLeast(nd.v(), 0, path + "/v");
        duration(nd.d(), nd.u(), path, "d", "u");
      }
    }
  }

  @MappingRule({"C23-POS-01", "C23-TERM-01", "C23-TERM-02", "C23-TERM-03"})
  private void posology(Posology p, String path) {
    if (p.po() == null) {
      error(path + "/po", "po is required");
    } else {
      detail(p.po(), path + "/po", false);
    }
    if (plan && p.unit() == null) {
      error(path + "/unit", "unit is required in a medication plan");
    }
    if (p.unit() != null && !Terminology.isUnit(p.unit())) {
      warning(path + "/unit", "unit is not a code of CDTYP 9 (terminology.md)");
    }
    if (p.roa() != null && !Terminology.isRoute(p.roa())) {
      warning(path + "/roa", "roa is not a code of CDTYP 61 (terminology.md)");
    }
    if (p.moa() != null && !Terminology.isMethod(p.moa())) {
      warning(path + "/moa", "moa is not a code of CDTYP 62 (terminology.md)");
    }
    if (p.relMeal() != null && (p.relMeal() < 1 || p.relMeal() > 7)) {
      error(path + "/relMeal", "relMeal must be 1-7");
    }
    boolean from = p.dtFrom() == null || isDateOrDateTime(p.dtFrom());
    boolean to = p.dtTo() == null || isDateOrDateTime(p.dtTo());
    if (!from) {
      error(path + "/dtFrom", "dtFrom must be a date or a date-time with offset");
    }
    if (!to) {
      error(path + "/dtTo", "dtTo must be a date or a date-time with offset");
    }
    if (from && to && p.dtFrom() != null && p.dtTo() != null && before(p.dtTo(), p.dtFrom())) {
      error(path + "/dtTo", "dtTo must not be before dtFrom");
    }
  }

  @MappingRule({"C23-POS-02", "C23-POS-03", "C23-POS-04", "C23-POS-05", "C23-POS-06"})
  private void detail(PosologyDetail detail, String path, boolean inSequence) {
    switch (detail) {
      case PosologyDetail.Daily d -> {
        if (d.ds().size() != 4) {
          error(path + "/ds", "ds must contain exactly 4 values");
        }
        for (int i = 0; i < d.ds().size(); i++) {
          if (d.ds().get(i).signum() < 0) {
            error(path + "/ds/" + i, "Dosages must not be negative");
          }
        }
      }
      case PosologyDetail.FreeText f -> required(f.text(), path + "/text");
      case PosologyDetail.Single s ->
          timed(
              s.tdo(),
              path + "/tdo",
              Set.of(
                  TimedDosage.DosageOnly.class,
                  TimedDosage.Times.class,
                  TimedDosage.DaySegments.class),
              "Single supports DosageOnly, Times and DaySegments");
      case PosologyDetail.Cyclic c -> {
        TimeUnit unit = duration(c.cyDu(), c.cyDuU(), path, "cyDu", "cyDuU");
        if (c.tdpc() != null && c.tdpc() <= 0) {
          error(path + "/tdpc", "tdpc must be greater than 0");
        }
        if (c.tdo() instanceof TimedDosage.WeekDays && unit != null && unit != TimeUnit.WEEK) {
          error(path + "/tdo/t", "WeekDays requires the cycle duration unit week");
        } else if (c.tdo() instanceof TimedDosage.DaysOfMonth
            && unit != null
            && unit != TimeUnit.MONTH) {
          error(path + "/tdo/t", "DaysOfMonth requires the cycle duration unit month");
        } else {
          timed(
              c.tdo(),
              path + "/tdo",
              Set.of(
                  TimedDosage.DosageOnly.class,
                  TimedDosage.Times.class,
                  TimedDosage.DaySegments.class,
                  TimedDosage.WeekDays.class,
                  TimedDosage.DaysOfMonth.class,
                  TimedDosage.Interval.class),
              "");
        }
      }
      case PosologyDetail.Sequence s -> {
        if (inSequence) {
          error(path + "/t", "Sequences must not be nested");
          return;
        }
        if (s.sos().isEmpty()) {
          error(path + "/sos", "sos must contain at least one element");
        }
        for (int i = 0; i < s.sos().size(); i++) {
          SequenceObject so = s.sos().get(i);
          String p = path + "/sos/" + i;
          duration(so.du(), so.duU(), p, "du", "duU");
          if (so instanceof SequenceObject.PosologySequence ps) {
            if (ps.po() == null) {
              error(p + "/po", "po is required");
            } else {
              detail(ps.po(), p + "/po", true);
            }
          }
        }
      }
    }
  }

  @MappingRule({"C23-POS-07", "C23-POS-08", "C23-POS-09", "C23-POS-10"})
  private void timed(
      TimedDosage tdo, String path, Set<Class<?>> allowed, String notAllowedMessage) {
    if (tdo == null) {
      error(path, "tdo is required");
      return;
    }
    if (!allowed.contains(tdo.getClass())) {
      error(
          path + "/t",
          notAllowedMessage.isEmpty() ? "Timed dosage not allowed here" : notAllowedMessage);
      return;
    }
    Set<Class<?>> inner =
        Set.of(
            TimedDosage.DosageOnly.class, TimedDosage.Times.class, TimedDosage.DaySegments.class);
    switch (tdo) {
      case TimedDosage.DosageOnly d -> dosage(d.dose(), path + "/do");
      case TimedDosage.Times t -> {
        if (t.ts().isEmpty()) {
          error(path + "/ts", "ts must contain at least one application");
        }
        for (int i = 0; i < t.ts().size(); i++) {
          Application.AtTime a = t.ts().get(i);
          if (required(a.dt(), path + "/ts/" + i + "/dt") && !TIME.matcher(a.dt()).matches()) {
            error(path + "/ts/" + i + "/dt", "dt must be a time of day hh:mm[:ss]");
          }
          dosage(a.dose(), path + "/ts/" + i + "/do");
        }
      }
      case TimedDosage.DaySegments s -> {
        if (s.ss().isEmpty()) {
          error(path + "/ss", "ss must contain at least one application");
        }
        for (int i = 0; i < s.ss().size(); i++) {
          Application.InSegment a = s.ss().get(i);
          if (a.s() == null) {
            error(path + "/ss/" + i + "/s", "s is required");
          } else if (a.s() < 1 || a.s() > 4) {
            error(path + "/ss/" + i + "/s", "s must be a day segment 1-4");
          }
          dosage(a.dose(), path + "/ss/" + i + "/do");
        }
      }
      case TimedDosage.WeekDays w -> {
        days(w.wds(), path + "/wds", 7);
        timed(w.tdo(), path + "/tdo", inner, "WeekDays supports DosageOnly, Times and DaySegments");
      }
      case TimedDosage.DaysOfMonth d -> {
        days(d.doms(), path + "/doms", 28);
        timed(
            d.tdo(),
            path + "/tdo",
            inner,
            "DaysOfMonth supports DosageOnly, Times and DaySegments");
      }
      case TimedDosage.Interval i -> {
        dosage(i.dose(), path + "/do");
        duration(i.miDu(), i.miDuU(), path, "miDu", "miDuU");
      }
    }
  }

  @MappingRule("C23-POS-11")
  private void dosage(DosageObject dose, String path) {
    if (dose == null) {
      error(path, "do is required");
      return;
    }
    switch (dose) {
      case DosageObject.Simple s -> positive(s.a(), path + "/a");
      case DosageObject.FromTo f -> {
        if (required(f.aFrom(), path + "/aFrom") && f.aFrom().signum() < 0) {
          error(path + "/aFrom", "aFrom must not be negative");
        }
        if (required(f.aTo(), path + "/aTo")
            && f.aFrom() != null
            && f.aTo().compareTo(f.aFrom()) <= 0) {
          error(path + "/aTo", "aTo must be greater than aFrom");
        }
        duration(f.du(), f.duU(), path, "du", "duU");
      }
      case DosageObject.Range r -> {
        positive(r.aMin(), path + "/aMin");
        if (required(r.aMax(), path + "/aMax")
            && r.aMin() != null
            && r.aMax().compareTo(r.aMin()) <= 0) {
          error(path + "/aMax", "aMax must be greater than aMin");
        }
      }
    }
  }

  @MappingRule("C23-OBJ-11")
  private void extensions(List<Extension> exts, String path) {
    for (int i = 0; i < exts.size(); i++) {
      Extension e = exts.get(i);
      required(e.nm(), path + "/" + i + "/nm");
      required(e.schema(), path + "/" + i + "/schema");
      extensions(e.exts(), path + "/" + i + "/exts");
    }
  }

  /**
   * GLN and GTIN: 13 digits with the GS1 check digit (C23-OBJ-15). CHMED23A does not constrain the
   * format and the official examples use invalid GLNs (finding F7), so a violation is a WARNING and
   * the value is carried unchanged; CH EMED EPR, however, requires valid GLNs.
   */
  @MappingRule("C23-OBJ-15")
  private void gs1(String value, String path, String kind) {
    if (value != null && !validGs1(value)) {
      warning(
          path,
          kind
              + " is not 13 digits with a valid GS1 check digit; carried unchanged (CH EMED EPR"
              + " requires valid GLNs)");
    }
  }

  /** True for 13 digits whose last digit is the GS1 check digit (GLN, GTIN-13). */
  static boolean validGs1(String value) {
    if (value.length() != 13 || !value.chars().allMatch(Character::isDigit)) {
      return false;
    }
    int sum = 0;
    for (int i = 0; i < 12; i++) {
      int digit = value.charAt(i) - '0';
      sum += (i % 2 == 0) ? digit : digit * 3;
    }
    return (10 - (sum % 10)) % 10 == value.charAt(12) - '0';
  }

  // -------------------------------------------------------------------------------------------

  private void days(List<Integer> days, String path, int max) {
    if (days.isEmpty()) {
      error(path, "At least one day is required");
    }
    Set<Integer> seen = new HashSet<>();
    for (int i = 0; i < days.size(); i++) {
      Integer day = days.get(i);
      if (day < 1 || day > max) {
        error(path + "/" + i, "Day must be 1-" + max);
      } else if (!seen.add(day)) {
        error(path + "/" + i, "Day occurs more than once");
      }
    }
  }

  private TimeUnit duration(
      Integer value, Integer unit, String path, String valueName, String unitName) {
    positive(value, path + "/" + valueName);
    if (unit == null) {
      error(path + "/" + unitName, unitName + " is required");
      return null;
    }
    TimeUnit timeUnit = TimeUnit.of(unit);
    if (timeUnit == null) {
      error(path + "/" + unitName, unitName + " must be a time unit 1-7");
    }
    return timeUnit;
  }

  private void positive(Integer value, String path) {
    if (value == null) {
      error(path, "Value is required");
    } else if (value <= 0) {
      error(path, "Value must be greater than 0");
    }
  }

  private void positive(BigDecimal value, String path) {
    if (value == null) {
      error(path, "Value is required");
    } else if (value.signum() <= 0) {
      error(path, "Value must be greater than 0");
    }
  }

  private void atLeast(Integer value, int min, String path) {
    if (value == null) {
      error(path, "Value is required");
    } else if (value < min) {
      error(path, "Value must be at least " + min);
    }
  }

  private boolean required(Object value, String path) {
    if (value == null) {
      error(path, "Value is required");
      return false;
    }
    return true;
  }

  private void notUsed(String path) {
    report.add(
        ReportEntry.information(
            ReasonCode.UNMAPPABLE_ELEMENT,
            phase,
            path,
            "Not used for this document type (usage \"-\"); ignored"));
  }

  private void error(String path, String message) {
    report.add(ReportEntry.error(ReasonCode.CHMED_INVALID_FIELD, phase, path, message));
  }

  private void warning(String path, String message) {
    report.add(ReportEntry.warning(ReasonCode.CHMED_INVALID_FIELD, phase, path, message));
  }

  static boolean isDate(String value) {
    if (!DATE.matcher(value).matches()) {
      return false;
    }
    try {
      LocalDate.parse(value);
      return true;
    } catch (DateTimeParseException e) {
      return false;
    }
  }

  static boolean isDateTime(String value) {
    if (!DATE_TIME.matcher(value).matches()) {
      return false;
    }
    try {
      OffsetDateTime.parse(value);
      return true;
    } catch (DateTimeParseException e) {
      return false;
    }
  }

  static boolean isDateOrDateTime(String value) {
    return isDate(value) || isDateTime(value);
  }

  /** True if {@code a} is before {@code b}; dates compare by day. */
  private static boolean before(String a, String b) {
    if (isDateTime(a) && isDateTime(b)) {
      return OffsetDateTime.parse(a).isBefore(OffsetDateTime.parse(b));
    }
    return LocalDate.parse(a.substring(0, 10)).isBefore(LocalDate.parse(b.substring(0, 10)));
  }
}
