package ch.emed.converter.chmed16a.validation;

import ch.emed.converter.chmed16a.mapping.MappingRule;
import ch.emed.converter.chmed16a.model.HcOrg;
import ch.emed.converter.chmed16a.model.HcPerson;
import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.model.Patient;
import ch.emed.converter.chmed16a.model.Posology;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.util.regex.Pattern;

/**
 * Data requirements of eRezept Schweiz for CHMED16A prescriptions (mapping spec M-ERX-01..05,
 * data-model.md §4), derived from the eRezept documentation 0.10.2 and its published JSON schema. A
 * prescription that violates them cannot be signed by the HIN erxproxy unmodified (FR-008b).
 * Violations are ERRORs on produced prescriptions ({@link Phase#VALIDATE_OUTPUT}) and WARNINGs for
 * prescriptions read as input ({@link Phase#VALIDATE_INPUT}); the code is {@code
 * EREZEPT_REQUIREMENT}, messages never contain field values.
 */
public final class ErezeptPrescriptionRules {

  private static final Pattern UUID_LOWER =
      Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
  private static final Pattern DATE_TIME =
      Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}:\\d{2}|Z)$");
  private static final Pattern GLN = Pattern.compile("^\\d{13}$");
  private static final Pattern SWISS_ZIP = Pattern.compile("^\\d{4,5}$");
  private static final Pattern IND_CODE = Pattern.compile("^\\d{5}\\.\\d{2}$");
  private static final int MAX_REP = 24;
  private static final int MAX_ZSR = 7;

  private ErezeptPrescriptionRules() {}

  /**
   * Checks a prescription against the eRezept requirements.
   *
   * @param med the prescription
   * @param report receives the violations
   * @param phase {@code VALIDATE_OUTPUT} (ERROR) or {@code VALIDATE_INPUT} (WARNING)
   */
  @MappingRule({"M-ERX-01", "M-ERX-02", "M-ERX-03", "M-ERX-04", "M-ERX-05"})
  public static void validate(Medication med, ReportCollector report, Phase phase) {
    Violations v = new Violations(report, phase);
    root(med, v);
    healthcarePerson(med, v);
    healthcareOrganization(med, v);
    patient(med.patient(), v);
    if (med.medicaments().isEmpty()) {
      v.add("/Medicaments", "A prescription needs at least one medicament");
    }
    for (int i = 0; i < med.medicaments().size(); i++) {
      medicament(med.medicaments().get(i), "/Medicaments/" + i, v);
    }
  }

  private static void root(Medication med, Violations v) {
    if (med.medType() == null || med.medType() != Medication.MED_TYPE_PRESCRIPTION) {
      v.add("/MedType", "MedType must be 3 (prescription)");
    }
    if (!present(med.id()) || !UUID_LOWER.matcher(med.id()).matches()) {
      v.add("/Id", "Id must be a lower-case UUID");
    }
    if (!present(med.dt()) || !DATE_TIME.matcher(med.dt()).matches()) {
      v.add("/Dt", "Dt must be a date-time with offset");
    }
    if (!present(med.auth()) || !GLN.matcher(med.auth()).matches()) {
      v.add("/Auth", "Auth must be the GLN of the prescribing person (13 digits)");
    } else if (med.hcPerson() != null && !med.auth().equals(med.hcPerson().gln())) {
      v.add("/Auth", "Auth must equal the GLN of HcPerson");
    }
  }

  private static void healthcarePerson(Medication med, Violations v) {
    HcPerson p = med.hcPerson();
    if (p == null) {
      v.add("/HcPerson", "HcPerson is required (revision 3)");
      return;
    }
    if (!present(p.gln()) || !GLN.matcher(p.gln()).matches()) {
      v.add("/HcPerson/Gln", "Gln must be a GLN of 13 digits");
    }
    v.required(p.fName(), "/HcPerson/FName");
    v.required(p.lName(), "/HcPerson/LName");
    v.maxLength(p.zsr(), MAX_ZSR, "/HcPerson/Zsr");
  }

  private static void healthcareOrganization(Medication med, Violations v) {
    HcOrg o = med.hcOrg();
    if (o == null) {
      v.add("/HcOrg", "HcOrg is required (revision 3)");
      return;
    }
    v.required(o.name(), "/HcOrg/Name");
    v.required(o.street(), "/HcOrg/Street");
    v.required(o.city(), "/HcOrg/City");
    if (!present(o.zip()) || !SWISS_ZIP.matcher(o.zip()).matches()) {
      v.add("/HcOrg/Zip", "Zip must have 4 or 5 digits");
    }
    v.maxLength(o.zsr(), MAX_ZSR, "/HcOrg/Zsr");
  }

  private static void patient(Patient p, Violations v) {
    if (p == null) {
      v.add("/Patient", "Patient is required");
      return;
    }
    v.required(p.fName(), "/Patient/FName");
    v.required(p.lName(), "/Patient/LName");
    v.required(p.bDt(), "/Patient/BDt");
    if (p.gender() != null && p.gender() != 1 && p.gender() != 2) {
      v.add("/Patient/Gender", "Gender must be 1 or 2");
    }
    if (present(p.zip()) && !SWISS_ZIP.matcher(p.zip()).matches()) {
      v.add("/Patient/Zip", "Zip must have 4 or 5 digits");
    }
  }

  private static void medicament(Medicament m, String path, Violations v) {
    v.required(m.id(), path + "/Id");
    if (m.idType() == null
        || m.idType() < Medicament.ID_TYPE_NONE
        || m.idType() > Medicament.ID_TYPE_PHARMACODE) {
      // free text (1) is accepted by the published schema; product numbers (4) are not
      v.add(path + "/IdType", "IdType must be 1 (none), 2 (GTIN) or 3 (Pharmacode)");
    }
    if (m.rep() != null && (m.rep() < 0 || m.rep() > MAX_REP)) {
      v.add(path + "/Rep", "Rep must be between 0 and 24 months");
    }
    if (m.subs() != null && m.subs() != 0 && m.subs() != 1) {
      v.add(path + "/Subs", "Subs must be 0 or 1");
    }
    if (present(m.indC()) && !IND_CODE.matcher(m.indC()).matches()) {
      v.add(path + "/IndC", "IndC must have the format 00000.00");
    }
    if (m.pos().size() > 1) {
      v.add(path + "/Pos", "A prescription has at most one posology");
    }
    for (int j = 0; j < m.pos().size(); j++) {
      Posology pos = m.pos().get(j);
      if (!present(m.unit())) {
        v.add(path + "/Unit", "Unit is required if a posology is given");
      }
      if (pos.d().isEmpty() || pos.d().size() > 4) {
        v.add(path + "/Pos/" + j + "/D", "D must contain 1 to 4 doses");
      }
    }
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private record Violations(ReportCollector report, Phase phase) {
    void add(String path, String message) {
      Severity severity = phase == Phase.VALIDATE_OUTPUT ? Severity.ERROR : Severity.WARNING;
      report.add(
          ReportEntry.of(
              severity,
              ReasonCode.EREZEPT_REQUIREMENT,
              phase,
              path,
              "eRezept Schweiz: " + message));
    }

    void required(String value, String path) {
      if (!present(value)) {
        add(path, "the field is required");
      }
    }

    void maxLength(String value, int max, String path) {
      if (present(value) && value.length() > max) {
        add(path, "at most " + max + " characters");
      }
    }
  }
}
