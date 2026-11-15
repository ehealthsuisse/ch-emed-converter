package ch.emed.converter.chmed23a.model;

import java.util.List;

/**
 * The CHMED23A document (README "Medication").
 *
 * @param patient the patient
 * @param hcPerson the author, if {@code auth} = 1
 * @param hcOrg the author's organization, if {@code auth} = 1
 * @param meds the medicaments
 * @param exts extensions
 * @param medType 1 medication plan, 3 prescription (2, PolymedicationCheck, is deprecated)
 * @param id optional id set by the creator
 * @param auth 1 healthcare person, 2 patient
 * @param rec recipient GLN (prescription)
 * @param dt creation date-time with offset
 * @param rmk remark
 */
public record Medication(
    Patient patient,
    HealthcarePerson hcPerson,
    HealthcareOrganization hcOrg,
    List<Medicament> meds,
    List<Extension> exts,
    Integer medType,
    String id,
    Integer auth,
    String rec,
    String dt,
    String rmk) {

  public static final int MEDICATION_PLAN = 1;
  public static final int POLYMEDICATION_CHECK = 2;
  public static final int PRESCRIPTION = 3;
  public static final int AUTH_HEALTHCARE_PERSON = 1;
  public static final int AUTH_PATIENT = 2;

  public Medication {
    meds = meds == null ? List.of() : List.copyOf(meds);
    exts = exts == null ? List.of() : List.copyOf(exts);
  }

  public boolean isPlan() {
    return medType != null && medType == MEDICATION_PLAN;
  }

  public boolean isPrescription() {
    return medType != null && medType == PRESCRIPTION;
  }
}
