package ch.emed.converter.chmed16a.model;

import java.util.List;

/**
 * CHMED16A medication object ("Med", README + prescription.md, pinned commit 0b579aa). Field names
 * follow the JSON keys; optional fields are null when absent, defaults are never materialised.
 */
public record Medication(
    Patient patient,
    List<Medicament> medicaments,
    boolean hasRecommendations,
    Integer medType,
    String id,
    String auth,
    String zsr,
    String dt,
    String rmk,
    String valBy,
    String valDt,
    Integer rev,
    HcPerson hcPerson,
    HcOrg hcOrg,
    String pSchema,
    List<PField> pFields) {

  public static final int MED_TYPE_PLAN = 1;
  public static final int MED_TYPE_PMC = 2;
  public static final int MED_TYPE_PRESCRIPTION = 3;

  /** Latest prescription revision of the pinned specification. */
  public static final int LATEST_PRESCRIPTION_REVISION = 3;

  public Medication {
    medicaments = medicaments == null ? List.of() : List.copyOf(medicaments);
    pFields = pFields == null ? List.of() : List.copyOf(pFields);
  }

  public boolean isPlan() {
    return medType != null && medType == MED_TYPE_PLAN;
  }

  public boolean isPrescription() {
    return medType != null && medType == MED_TYPE_PRESCRIPTION;
  }

  /** Declared prescription revision; absent means revision 1. */
  public int effectiveRevision() {
    return rev == null ? 1 : rev;
  }
}
