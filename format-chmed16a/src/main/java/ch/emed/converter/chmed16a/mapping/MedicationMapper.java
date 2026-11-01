package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationStatement;

/**
 * CHMED16A Medicament → contained CH EMED Medication and statement fields (mapping spec M-MED-*).
 */
public final class MedicationMapper {

  /** Id of the contained Medication. */
  public static final String CONTAINED_ID = "med";

  private MedicationMapper() {}

  /**
   * Maps the product identification. CH EMED requires {@code Medication.code.text}; CHMED16A
   * carries no product name for coded medicaments, so the text is derived from the identifier.
   * Pharmacode and product number have no code system in the pinned specifications and are carried
   * as text only.
   */
  @MappingRule({"M-MED-01", "M-MED-02", "M-MED-03", "M-MED-04"})
  public static Medication mapMedication(Medicament m, String path, MappingScope scope) {
    Medication medication = new Medication();
    medication.setId(CONTAINED_ID);
    medication.getMeta().addProfile(Profiles.MEDICATION);
    CodeableConcept code = new CodeableConcept();
    int idType = m.idType() == null ? Medicament.ID_TYPE_NONE : m.idType();
    switch (idType) {
      case Medicament.ID_TYPE_GTIN -> {
        code.addCoding(new Coding().setSystem(Profiles.GTIN).setCode(m.id()));
        code.setText("GTIN " + m.id());
        scope
            .report()
            .add(
                ReportEntry.information(
                        ReasonCode.DERIVED_VALUE,
                        Phase.MAP,
                        path + "/Id",
                        "Medication.code.text derived from the GTIN (CHMED16A carries no product"
                            + " name)")
                    .withTargetPath("Medication.code.text"));
      }
      case Medicament.ID_TYPE_PHARMACODE -> {
        code.setText("Pharmacode " + m.id());
        notCoded(scope, path, "Pharmacode");
      }
      case Medicament.ID_TYPE_PRODUCT_NUMBER -> {
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
                    path + "/IdType",
                    "No code system for the "
                        + kind
                        + " in the pinned specifications; the"
                        + " identifier is carried as Medication.code.text only")
                .withTargetPath("Medication.code.text"));
  }

  /** Maps reason, self-medication and prescriber onto the statement. */
  @MappingRule({"M-MED-05", "M-MED-06", "M-MED-07"})
  public static void applyStatementFields(
      MedicationStatement statement, Medicament m, String path, MappingScope scope) {
    if (present(m.tkgRsn())) {
      statement.addReasonCode().setText(m.tkgRsn());
    }
    if (m.autoMed() != null && m.autoMed() == 1) {
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
                  path + "/PrscbBy",
                  "The prescriber of a medicament has no representation in the CH EMED"
                      + " Medication Card"));
    }
  }
}
