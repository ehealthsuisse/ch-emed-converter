package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.mapping.MappingRule;
import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.chmed16a.model.Medication;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Patient;

/**
 * Builds the CHMED16A prescription from a CH EMED Medication Prescription document (mapping spec
 * M-REV-30..M-REV-40).
 */
public final class PrescriptionBuilder {

  private PrescriptionBuilder() {}

  /**
   * Builds the prescription. Problems are reported in the scope.
   *
   * @return the prescription, or null if required data is missing (ERROR entries)
   */
  @MappingRule({"M-REV-30", "M-REV-39"})
  public static Medication build(ReverseScope scope) {
    Composition composition = scope.document().composition();

    String id = lowerCaseId(MedicationPlanBuilder.id(scope), scope);
    String dt =
        composition.hasDateElement() ? composition.getDateElement().getValueAsString() : null;
    if (!present(dt)) {
      scope.missing("Composition.date", "The document date is required by CHMED16A");
    }

    AuthorReverseMapper.PrescriptionAuthor author = AuthorReverseMapper.mapPrescription(scope);

    Patient source = scope.document().resolve(composition.getSubject(), Patient.class);
    ch.emed.converter.chmed16a.model.Patient patient = null;
    if (source == null) {
      scope.missing("Composition.subject", "The patient of the document cannot be resolved");
    } else {
      patient = PatientReverseMapper.map(source, scope, true);
    }

    String prescriptionDate = dt == null || dt.length() < 10 ? null : dt.substring(0, 10);
    List<Medicament> medicaments = new ArrayList<>();
    String receiver = null;
    List<MedicationRequest> requests = scope.document().all(MedicationRequest.class);
    for (int i = 0; i < requests.size(); i++) {
      String path = "MedicationRequest[" + i + "]";
      var prescribed = MedicationReverseMapper.map(requests.get(i), path, prescriptionDate, scope);
      if (prescribed == null) {
        continue;
      }
      medicaments.add(prescribed.medicament());
      if (prescribed.receiver() != null) {
        if (receiver == null) {
          receiver = prescribed.receiver();
        } else if (!receiver.equals(prescribed.receiver())) {
          scope.unmapped(
              path + ".dispenseRequest.performer",
              "A prescription has one receiver; this different receiver is not carried over");
        }
      }
    }
    if (medicaments.isEmpty()) {
      scope.erezept("MedicationRequest", "A prescription needs at least one medicament");
    }

    if (scope.report().hasErrors() || author == null || patient == null) {
      return null;
    }
    return new Medication(
        receiver == null ? patient : patient.withRcv(receiver),
        medicaments,
        false,
        Medication.MED_TYPE_PRESCRIPTION,
        id,
        author.auth(),
        author.zsr(),
        dt,
        MedicationPlanBuilder.remarks(composition, scope),
        null,
        null,
        Medication.LATEST_PRESCRIPTION_REVISION,
        author.person(),
        author.org(),
        null,
        List.of());
  }

  /** M-REV-30: the schema of eRezept Schweiz expects a lower-case UUID. */
  private static String lowerCaseId(String id, ReverseScope scope) {
    if (id == null) {
      return null;
    }
    String lower = id.toLowerCase(Locale.ROOT);
    if (!lower.equals(id)) {
      scope.derived("Bundle.identifier", "The document identifier is written in lower case");
    }
    return lower;
  }
}
