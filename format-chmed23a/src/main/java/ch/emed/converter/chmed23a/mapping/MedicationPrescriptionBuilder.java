package ch.emed.converter.chmed23a.mapping;

import static ch.emed.converter.chmed23a.mapping.Documents.add;
import static ch.emed.converter.chmed23a.mapping.MappingScope.present;

import ch.emed.converter.chmed23a.model.Medicament;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.core.chemed.Labels;
import ch.emed.converter.core.chemed.Narratives;
import ch.emed.converter.core.chemed.Profiles;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.SectionComponent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;

/**
 * Builds the CH EMED 6.0.0 Medication Prescription from a CHMED23A prescription (mapping spec
 * M23-RX-01, -02, -10, research R10).
 */
public final class MedicationPrescriptionBuilder {

  private MedicationPrescriptionBuilder() {}

  /**
   * Builds the document. Problems are reported in the scope; the caller checks for errors.
   *
   * @param med validated CHMED23A prescription
   * @param scope mapping scope
   * @return the document Bundle
   */
  @MappingRule({"M23-DOC-02", "M23-RX-01", "M23-RX-02", "M23-RX-10"})
  public static Bundle build(Medication med, MappingScope scope) {
    String language = Documents.language(med, scope);
    Patient patient = PatientMapper.map(med.patient(), scope);
    Reference patientRef = new Reference(scope.ref("/patient"));
    AuthorMapper.Author author = AuthorMapper.map(med, patientRef, scope);
    Reference authorRef = author == null ? null : author.reference();

    List<MedicationRequest> requests = new ArrayList<>();
    for (int i = 0; i < med.meds().size(); i++) {
      Medicament m = med.meds().get(i);
      String medPath = "/meds/" + i;
      PatientMapper.extensions(m.exts(), medPath + "/exts", scope);
      if (m.pos().isEmpty()) {
        MedicationRequest request = request(med, m, medPath, patientRef, authorRef, scope);
        String note = Labels.get(language, Labels.Key.NO_DOSAGE);
        Dosage dosage = PosologyToFhirMapper.noPosology(note);
        // ch-dosage-3: a patient instruction, or timing with dose
        dosage.setPatientInstruction(note);
        request.addDosageInstruction(dosage);
        scope
            .report()
            .add(
                ReportEntry.information(
                        ReasonCode.DERIVED_VALUE,
                        Phase.MAP,
                        medPath + "/pos",
                        "No posology given; CH EMED requires a dosage, a localized note is set")
                    .withTargetPath("MedicationRequest.dosageInstruction.text"));
        PrescriptionDetailsMapper.apply(request, m, med, medPath, scope);
        requests.add(request);
        continue;
      }
      if (m.pos().size() > 1) {
        scope
            .report()
            .add(
                ReportEntry.information(
                    ReasonCode.DERIVED_VALUE,
                    Phase.MAP,
                    medPath + "/pos",
                    "Prescribed medicament with several posologies is split into one"
                        + " MedicationRequest per posology; the dispense data is copied to each"));
      }
      for (int j = 0; j < m.pos().size(); j++) {
        String posPath = medPath + "/pos/" + j;
        MedicationRequest request = request(med, m, posPath, patientRef, authorRef, scope);
        PosologyToFhirMapper.map(m.pos().get(j), posPath, language, scope)
            .forEach(request::addDosageInstruction);
        satisfyDosageConstraint(request.getDosageInstructionFirstRep(), posPath, scope);
        PrescriptionDetailsMapper.apply(request, m, med, medPath, scope);
        requests.add(request);
      }
    }
    PatientMapper.extensions(med.exts(), "/exts", scope);

    Narratives.patient(patient);
    requests.forEach(Narratives::request);
    Composition composition = composition(med, language, patientRef, authorRef, requests, scope);
    Narratives.composition(composition);

    Bundle bundle = new Bundle();
    bundle.getMeta().addProfile(Profiles.DOCUMENT_PRESCRIPTION);
    bundle.getIdentifier().setSystem(Profiles.URI).setValue("urn:uuid:" + scope.documentId());
    bundle.setType(Bundle.BundleType.DOCUMENT);
    bundle.setTimestampElement(new InstantType(med.dt()));
    add(bundle, composition);
    add(bundle, patient);
    if (author != null) {
      author.resources().forEach(r -> add(bundle, r));
    }
    requests.forEach(r -> add(bundle, r));
    Documents.addOriginalRepresentation(bundle, scope);
    return bundle;
  }

  private static MedicationRequest request(
      Medication med,
      Medicament m,
      String path,
      Reference patient,
      Reference author,
      MappingScope scope) {
    String medPath = path.contains("/pos/") ? path.substring(0, path.indexOf("/pos/")) : path;
    MedicationRequest request = new MedicationRequest();
    request.setId(scope.id(path));
    request.getMeta().addProfile(Profiles.MEDICATION_REQUEST);
    request.addContained(MedicationMapper.mapMedication(m, medPath, scope));
    request.addIdentifier().setSystem(Profiles.URI).setValue(scope.ref(path + "/identifier"));
    request.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
    request.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
    request.setMedication(new Reference("#" + MedicationMapper.CONTAINED_ID));
    request.setSubject(new Reference(patient.getReference()));
    request.setAuthoredOnElement(new DateTimeType(med.dt()));
    if (author != null) {
      request.setRequester(new Reference(author.getReference()));
    }
    if (present(m.rsn())) {
      request.addReasonCode().setText(m.rsn());
    }
    return request;
  }

  /**
   * ch-dosage-3 of the MedicationRequest base entry: a patient instruction, or timing with dose. A
   * dosage carried as text only gets the text as patient instruction too.
   */
  private static void satisfyDosageConstraint(Dosage base, String path, MappingScope scope) {
    if (base.hasPatientInstruction()
        || (base.hasTiming() && base.hasDoseAndRate())
        || !base.hasText()) {
      return;
    }
    base.setPatientInstruction(base.getText());
    scope
        .report()
        .add(
            ReportEntry.information(
                    ReasonCode.DERIVED_VALUE,
                    Phase.MAP,
                    path,
                    "The text dosage is also set as patient instruction (required by the CH EMED"
                        + " MedicationRequest dosage profile)")
                .withTargetPath("MedicationRequest.dosageInstruction.patientInstruction"));
  }

  private static Composition composition(
      Medication med,
      String language,
      Reference patient,
      Reference author,
      List<MedicationRequest> requests,
      MappingScope scope) {
    Composition c = new Composition();
    c.setId(scope.id("/Composition"));
    c.getMeta().addProfile(Profiles.COMPOSITION_PRESCRIPTION);
    c.setLanguage(language);
    c.getIdentifier().setSystem(Profiles.URI).setValue("urn:uuid:" + scope.documentId());
    c.setStatus(Composition.CompositionStatus.FINAL);
    c.getType()
        .addCoding(new Coding(Profiles.LOINC, "57833-6", "Prescription for medication"))
        .addCoding(
            new Coding(
                Profiles.SNOMED, "761938008", "Medicinal prescription record (record artifact)"));
    c.setSubject(new Reference(patient.getReference()));
    c.setDateElement(new DateTimeType(med.dt()));
    if (author != null) {
      c.addAuthor(new Reference(author.getReference()));
    }
    c.setTitle(Labels.get(language, Labels.Key.TITLE_PRESCRIPTION));
    Documents.confidentiality(c, scope);
    Documents.originalRepresentationSection(c, language, scope);

    SectionComponent prescription = c.addSection();
    prescription.setTitle(Labels.get(language, Labels.Key.SECTION_PRESCRIPTION));
    prescription.setCode(
        new CodeableConcept(new Coding(Profiles.LOINC, "57828-6", "Prescription list")));
    for (MedicationRequest r : requests) {
      prescription.addEntry(new Reference("urn:uuid:" + r.getIdPart()));
    }
    Documents.annotationSection(c, language, med.rmk());
    return c;
  }
}
