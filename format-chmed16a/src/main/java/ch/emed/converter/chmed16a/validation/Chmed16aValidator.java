package ch.emed.converter.chmed16a.validation;

import ch.emed.converter.chmed16a.mapping.MappingRule;
import ch.emed.converter.chmed16a.model.HcOrg;
import ch.emed.converter.chmed16a.model.HcPerson;
import ch.emed.converter.chmed16a.model.Measurement;
import ch.emed.converter.chmed16a.model.MedicalData;
import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.model.Patient;
import ch.emed.converter.chmed16a.model.PatientId;
import ch.emed.converter.chmed16a.model.Posology;
import ch.emed.converter.chmed16a.model.RiskCategory;
import ch.emed.converter.chmed16a.model.TakingTime;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rule-based CHMED16A validation derived from the pinned specification (research R5, data-model.md
 * §2). There is no official JSON schema. Messages never contain field values.
 */
public final class Chmed16aValidator {

  private static final Pattern UUID =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
  private static final Pattern DATE_TIME =
      Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}:\\d{2}|Z)$");
  private static final Pattern GTIN = Pattern.compile("^\\d{13}$");
  private static final Pattern PHARMACODE = Pattern.compile("^\\d{1,7}$");
  private static final Pattern LANGUAGE = Pattern.compile("^[A-Za-z]{2}$");
  private static final Pattern GLN = Pattern.compile("^\\d{13}$");
  private static final Pattern IND_CODE = Pattern.compile("^\\d{5}\\.\\d{2}$");
  private static final long MAX_NB_PACK = 9_999_999_999L;

  private Chmed16aValidator() {}

  /**
   * Validates a CHMED16A document.
   *
   * @param med the document
   * @param report receives ERROR/WARNING entries (phase VALIDATE_INPUT)
   */
  public static void validate(Medication med, ReportCollector report) {
    validate(med, report, Phase.VALIDATE_INPUT);
  }

  /**
   * Validates a CHMED16A document.
   *
   * @param med the document
   * @param report receives ERROR/WARNING entries
   * @param phase phase of the entries: {@code VALIDATE_INPUT} for input, {@code VALIDATE_OUTPUT}
   *     for produced documents
   */
  @MappingRule({"M-DOC-03", "M-IN-05"})
  public static void validate(Medication med, ReportCollector report, Phase phase) {
    Rules r = new Rules(report, phase);
    r.required(med.id(), "/Id");
    if (present(med.id()) && !UUID.matcher(med.id()).matches()) {
      r.error("/Id", "Id must be a UUID");
    }
    if (med.medType() == null) {
      r.error("/MedType", "MedType is required");
    } else if (med.medType() == Medication.MED_TYPE_PMC) {
      report.add(
          ReportEntry.error(
              ReasonCode.UNSUPPORTED_DOCUMENT_TYPE,
              phase,
              "/MedType",
              "Document type PolymedicationCheck (MedType 2) is not supported"));
    } else if (med.medType() != Medication.MED_TYPE_PLAN
        && med.medType() != Medication.MED_TYPE_PRESCRIPTION) {
      r.error("/MedType", "MedType must be 1 (MedicationPlan) or 3 (Prescription)");
    }
    r.required(med.auth(), "/Auth");
    r.required(med.dt(), "/Dt");
    r.dateTime(med.dt(), "/Dt");
    r.dateTime(med.valDt(), "/ValDt");
    r.length(med.zsr(), 7, "/Zsr");

    boolean plan = med.isPlan();
    boolean prescription = med.isPrescription();
    if (prescription) {
      prescription(med, phase, r);
    }
    if (med.patient() == null) {
      r.error("/Patient", "Patient is required");
    } else {
      patient(med.patient(), plan, prescription, r);
    }
    for (int i = 0; i < med.medicaments().size(); i++) {
      medicament(med.medicaments().get(i), "/Medicaments/" + i, plan, prescription, r);
    }
  }

  /** Prescription-only rules of the root object (data-model.md §2, revision 3). */
  private static void prescription(Medication med, Phase phase, Rules r) {
    if (present(med.auth()) && !GLN.matcher(med.auth()).matches()) {
      r.error("/Auth", "Auth of a prescription must be the GLN of a person (13 digits)");
    }
    boolean output = phase == Phase.VALIDATE_OUTPUT;
    boolean required = output || med.effectiveRevision() >= 2;
    if (med.hcPerson() == null) {
      r.revisionField("/HcPerson", "HcPerson is required since revision 2", required);
    } else {
      HcPerson p = med.hcPerson();
      if (!present(p.gln()) || !GLN.matcher(p.gln()).matches()) {
        r.error("/HcPerson/Gln", "Gln must be a GLN of 13 digits");
      }
      r.required(p.fName(), "/HcPerson/FName");
      r.required(p.lName(), "/HcPerson/LName");
      r.maxLength(p.zsr(), 7, "/HcPerson/Zsr");
    }
    if (med.hcOrg() == null) {
      r.revisionField("/HcOrg", "HcOrg is required since revision 2", required);
    } else {
      HcOrg o = med.hcOrg();
      r.required(o.name(), "/HcOrg/Name");
      r.required(o.street(), "/HcOrg/Street");
      r.required(o.zip(), "/HcOrg/Zip");
      r.required(o.city(), "/HcOrg/City");
      r.maxLength(o.zsr(), 7, "/HcOrg/Zsr");
    }
  }

  private static void patient(Patient p, boolean plan, boolean prescription, Rules r) {
    if (prescription) {
      r.required(p.fName(), "/Patient/FName");
      r.required(p.lName(), "/Patient/LName");
      r.required(p.bDt(), "/Patient/BDt");
    }
    r.date(p.bDt(), "/Patient/BDt");
    if (p.gender() != null
        && p.gender() != Patient.GENDER_MALE
        && p.gender() != Patient.GENDER_FEMALE) {
      r.error("/Patient/Gender", "Gender must be 1 (male) or 2 (female)");
    }
    if (plan) {
      r.required(p.lng(), "/Patient/Lng");
    }
    if (present(p.lng()) && !LANGUAGE.matcher(p.lng()).matches()) {
      r.error("/Patient/Lng", "Lng must be an ISO 639-1 language code");
    }
    for (int i = 0; i < p.ids().size(); i++) {
      PatientId id = p.ids().get(i);
      String path = "/Patient/Ids/" + i;
      if (id.type() == null || id.type() != PatientId.TYPE_INSURANCE_CARD) {
        r.error(path + "/Type", "PatientId Type must be 1 (insurance card number)");
      }
      r.required(id.val(), path + "/Val");
    }
    if (p.med() != null) {
      medicalData(p.med(), r);
    }
  }

  private static void medicalData(MedicalData m, Rules r) {
    r.date(m.dLstMen(), "/Patient/Med/DLstMen");
    r.flag(m.prem(), "/Patient/Med/Prem");
    for (int i = 0; i < m.meas().size(); i++) {
      Measurement meas = m.meas().get(i);
      String path = "/Patient/Med/Meas/" + i;
      if (meas.type() == null || (meas.type() != 1 && meas.type() != 2)) {
        r.error(path + "/Type", "Measurement Type must be 1 (weight) or 2 (height)");
      }
      if (meas.unit() == null || (meas.unit() != 1 && meas.unit() != 2)) {
        r.error(path + "/Unit", "Measurement Unit must be 1 (cm) or 2 (kg)");
      }
      r.required(meas.val(), path + "/Val");
    }
    for (int i = 0; i < m.rc().size(); i++) {
      RiskCategory rc = m.rc().get(i);
      if (rc.id() == null || rc.id() < 1 || rc.id() > 7) {
        r.error("/Patient/Med/Rc/" + i + "/Id", "Risk category Id must be 1..7");
      }
    }
  }

  private static void medicament(
      Medicament m, String path, boolean plan, boolean prescription, Rules r) {
    r.required(m.id(), path + "/Id");
    if (m.idType() == null || m.idType() < 1 || m.idType() > 4) {
      r.error(
          path + "/IdType",
          "IdType must be 1 (none), 2 (GTIN), 3 (Pharmacode) or 4 (product number)");
    } else if (prescription && m.idType() == Medicament.ID_TYPE_PRODUCT_NUMBER) {
      r.error(path + "/IdType", "IdType 4 (product number) is not allowed in prescriptions");
    } else if (present(m.id())) {
      if (m.idType() == Medicament.ID_TYPE_GTIN) {
        if (!GTIN.matcher(m.id()).matches()) {
          r.error(path + "/Id", "GTIN must have 13 digits");
        } else if (!validGtinCheckDigit(m.id())) {
          r.warning(path + "/Id", "GTIN check digit is invalid; carried unchanged");
        }
      } else if (m.idType() == Medicament.ID_TYPE_PHARMACODE
          && !PHARMACODE.matcher(m.id()).matches()) {
        r.error(path + "/Id", "Pharmacode must have up to 7 digits");
      }
    }
    r.flag(m.autoMed(), path + "/AutoMed");
    if (plan && m.autoMed() == null) {
      r.warning(path + "/AutoMed", "AutoMed is required for medication plans but missing");
    }
    if (!m.pos().isEmpty() && !present(m.unit())) {
      r.error(path + "/Unit", "Unit is required if a posology is defined");
    }
    if (prescription) {
      prescriptionFields(m, path, r);
    } else {
      for (String field : prescriptionOnly(m)) {
        r.warning(path + "/" + field, field + " is only defined for prescriptions");
      }
    }
    for (int j = 0; j < m.pos().size(); j++) {
      posology(m.pos().get(j), path + "/Pos/" + j, plan, r);
    }
  }

  private static List<String> prescriptionOnly(Medicament m) {
    List<String> fields = new java.util.ArrayList<>();
    if (m.rep() != null) {
      fields.add("Rep");
    }
    if (m.subs() != null) {
      fields.add("Subs");
    }
    if (m.nbPack() != null) {
      fields.add("NbPack");
    }
    if (present(m.indC())) {
      fields.add("IndC");
    }
    return fields;
  }

  private static void prescriptionFields(Medicament m, String path, Rules r) {
    if (m.pos().size() > 1) {
      r.error(path + "/Pos", "A prescription has at most one posology");
    }
    if (m.rep() != null && (m.rep() < 0 || m.rep() > 99)) {
      r.error(path + "/Rep", "Rep must have at most 2 digits (months)");
    }
    r.flag(m.subs(), path + "/Subs");
    if (m.nbPack() != null && (m.nbPack() < 0 || m.nbPack() > MAX_NB_PACK)) {
      r.error(path + "/NbPack", "NbPack must have at most 10 digits");
    }
    if (present(m.indC()) && !IND_CODE.matcher(m.indC()).matches()) {
      r.error(path + "/IndC", "IndC must have the format 00000.00");
    }
  }

  private static void posology(Posology pos, String path, boolean plan, Rules r) {
    if (plan) {
      r.required(pos.dtFrom(), path + "/DtFrom");
    }
    r.date(pos.dtFrom(), path + "/DtFrom");
    r.date(pos.dtTo(), path + "/DtTo");
    LocalDate from = parseDate(pos.dtFrom());
    LocalDate to = parseDate(pos.dtTo());
    if (from != null && to != null && to.isBefore(from)) {
      r.error(path + "/DtTo", "DtTo must not be before DtFrom");
    }
    r.flag(pos.inRes(), path + "/InRes");
    if (pos.cyDu() != null && pos.cyDu() <= 0) {
      r.error(path + "/CyDu", "CyDu must be a positive number of seconds");
    }
    if (pos.d().size() > 4) {
      r.error(path + "/D", "D must contain at most 4 doses (morning, noon, evening, night)");
    }
    if (!pos.d().isEmpty() && !pos.tt().isEmpty()) {
      r.warning(path + "/D", "D is ignored because TT is specified");
    }
    List<TakingTime> tts = pos.tt();
    for (int k = 0; k < tts.size(); k++) {
      TakingTime tt = tts.get(k);
      String ttPath = path + "/TT/" + k;
      if (tt.off() == null || tt.off() < 0) {
        r.error(ttPath + "/Off", "Off is required and must not be negative");
      }
      if (pos.isReserve() ? tt.a() == null : tt.doFrom() == null) {
        r.error(ttPath, pos.isReserve() ? "A is required in reserve" : "DoFrom is required");
      }
    }
  }

  static boolean validGtinCheckDigit(String gtin) {
    int sum = 0;
    for (int i = 0; i < 12; i++) {
      int digit = gtin.charAt(i) - '0';
      sum += (i % 2 == 0) ? digit : digit * 3;
    }
    int check = (10 - (sum % 10)) % 10;
    return check == gtin.charAt(12) - '0';
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private static LocalDate parseDate(String value) {
    if (!present(value)) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private record Rules(ReportCollector report, Phase phase) {
    void error(String path, String message) {
      report.add(ReportEntry.error(ReasonCode.CHMED_INVALID_FIELD, phase, path, message));
    }

    void warning(String path, String message) {
      report.add(ReportEntry.warning(ReasonCode.CHMED_INVALID_FIELD, phase, path, message));
    }

    void required(String value, String path) {
      if (!present(value)) {
        error(path, "Field is required");
      }
    }

    void dateTime(String value, String path) {
      if (present(value) && !DATE_TIME.matcher(value).matches()) {
        error(path, "Expected format YYYY-MM-DDThh:mm:ss±hh:mm");
      }
    }

    void date(String value, String path) {
      if (present(value) && parseDate(value) == null) {
        error(path, "Expected format YYYY-MM-DD");
      }
    }

    void maxLength(String value, int max, String path) {
      if (present(value) && value.length() > max) {
        error(path, "Expected at most " + max + " characters");
      }
    }

    /** Field required since a later revision: WARNING for older input, ERROR otherwise. */
    void revisionField(String path, String message, boolean required) {
      if (required) {
        error(path, message);
      } else {
        report.add(
            ReportEntry.warning(ReasonCode.CHMED_REVISION_FIELD_MISSING, phase, path, message));
      }
    }

    void length(String value, int length, String path) {
      if (present(value) && value.length() != length) {
        error(path, "Expected length " + length);
      }
    }

    void flag(Integer value, String path) {
      if (value != null && value != 0 && value != 1) {
        error(path, "Expected 0 or 1");
      }
    }
  }
}
