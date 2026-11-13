package ch.emed.converter.chmed23a.mapping;

import static ch.emed.converter.chmed23a.mapping.MappingScope.present;

import ch.emed.converter.chmed23a.model.Medicament;
import ch.emed.converter.core.chemed.Profiles;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationStatement;

/** CHMED23A Medicament → contained CH EMED Medication and statement fields (M23-MED-*). */
public final class MedicationMapper {

  /** Id of the contained Medication. */
  public static final String CONTAINED_ID = "med";

  private MedicationMapper() {}

  /**
   * Maps the product identification. CH EMED requires {@code Medication.code.text}; CHMED23A
   * carries no product name for coded medicaments, so the text is derived from the identifier.
   */
  @MappingRule({"M23-MED-01", "M23-MED-02", "M23-MED-03", "M23-MED-04"})
  public static Medication mapMedication(Medicament m, String path, MappingScope scope) {
    Medication medication = new Medication();
    medication.setId(CONTAINED_ID);
    medication.getMeta().addProfile(Profiles.MEDICATION);
    CodeableConcept code = new CodeableConcept();
    int idType = m.idType() == null ? Medicament.ID_NONE : m.idType();
    switch (idType) {
      case Medicament.ID_GTIN -> {
        code.addCoding(new Coding().setSystem(Profiles.GTIN).setCode(m.id()));
        code.setText("GTIN " + m.id());
        scope
            .report()
            .add(
                ReportEntry.information(
                        ReasonCode.DERIVED_VALUE,
                        Phase.MAP,
                        path + "/id",
                        "Medication.code.text derived from the GTIN (CHMED23A carries no product"
                            + " name)")
                    .withTargetPath("Medication.code.text"));
      }
      case Medicament.ID_PHARMACODE -> {
        code.setText("Pharmacode " + m.id());
        notCoded(scope, path, "Pharmacode");
      }
      case Medicament.ID_PRODUCT_NUMBER -> {
        code.setText("Product number " + m.id());
        notCoded(scope, path, "product number");
      }
      default -> code.setText(m.id());
    }
    medication.setCode(code);
    return medication;
  }

  private static void notCoded(MappingScope scope, String path, String kind) {
    scope
        .report()
        .add(
            ReportEntry.warning(
                    ReasonCode.UNMAPPABLE_ELEMENT,
                    Phase.MAP,
                    path + "/idType",
                    "No code system for the "
                        + kind
                        + " in the pinned specifications; the identifier is carried as"
                        + " Medication.code.text only")
                .withTargetPath("Medication.code.text"));
  }

  /** Maps reason, self-medication and prescriber onto the statement. */
  @MappingRule({"M23-MED-05", "M23-MED-06", "M23-MED-07"})
  public static void applyStatementFields(
      MedicationStatement statement, Medicament m, String path, MappingScope scope) {
    if (present(m.rsn())) {
      statement.addReasonCode().setText(m.rsn());
    }
    if (Boolean.TRUE.equals(m.autoMed())) {
      statement.setCategory(
          new CodeableConcept(
              new Coding()
                  .setSystem(Profiles.STATEMENT_CATEGORY)
                  .setCode("patientspecified")
                  .setDisplay("Patient Specified")));
    }
    if (present(m.prscbBy())) {
      scope
          .report()
          .add(
              ReportEntry.warning(
                  ReasonCode.UNMAPPABLE_ELEMENT,
                  Phase.MAP,
                  path + "/prscbBy",
                  "The prescriber of a medicament has no representation in the CH EMED"
                      + " Medication Card"));
    }
  }
}
