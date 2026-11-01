package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.core.render.OriginalRepresentationRenderer;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Binary;
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
 * Builds the CH EMED 6.0.0 Medication Prescription document from a CHMED16A prescription (mapping
 * spec M-RX-01..M-RX-04).
 */
public final class MedicationPrescriptionBuilder {

  private MedicationPrescriptionBuilder() {}

  /**
   * Builds the document. Problems are reported in the scope; the caller checks for errors.
   *
   * @param med validated CHMED16A prescription
   * @param scope mapping scope
   * @return the document Bundle
   */
  @MappingRule({"M-DOC-02", "M-DOC-04", "M-DOC-05", "M-DOC-08", "M-RX-01", "M-RX-03", "M-RX-04"})
  public static Bundle build(Medication med, MappingScope scope) {
    String language = MedicationCardBuilder.language(med, scope);
    Patient patient = PatientMapper.map(med.patient(), med, scope);
    Reference patientRef = new Reference(scope.ref("/Patient"));
    AuthorMapper.Author author = AuthorMapper.mapPrescription(med, scope);
    Reference authorRef = author == null ? null : author.reference();

    if (med.medicaments().isEmpty()) {
      scope
          .report()
          .add(
              ReportEntry.error(
                  ReasonCode.MISSING_REQUIRED_DATA,
                  Phase.MAP,
                  "/Medicaments",
                  "A prescription needs at least one medicament (the prescription section of the"
                      + " CH EMED document requires an entry)"));
    }
    List<MedicationRequest> requests = new ArrayList<>();
    for (int i = 0; i < med.medicaments().size(); i++) {
      requests.add(
          request(
              med,
              med.medicaments().get(i),
              "/Medicaments/" + i,
              patientRef,
              authorRef,
              language,
              scope));
    }

    Narratives.patient(patient);
    requests.forEach(Narratives::request);
    Composition composition = composition(med, language, patientRef, authorRef, requests, scope);
    Narratives.composition(composition);

    Bundle bundle = new Bundle();
    bundle.getMeta().addProfile(Profiles.DOCUMENT_PRESCRIPTION);
    bundle.getIdentifier().setSystem(Profiles.URI).setValue("urn:uuid:" + med.id());
    bundle.setType(Bundle.BundleType.DOCUMENT);
    bundle.setTimestampElement(new InstantType(med.dt()));
    MedicationCardBuilder.add(bundle, composition);
    MedicationCardBuilder.add(bundle, patient);
    if (author != null) {
      author.resources().forEach(r -> MedicationCardBuilder.add(bundle, r));
    }
    requests.forEach(r -> MedicationCardBuilder.add(bundle, r));

    Binary binary = new Binary();
    binary.setId(scope.id(MedicationCardBuilder.ORIGINAL_PATH));
    binary.setContentType("application/pdf");
    binary.setData(OriginalRepresentationRenderer.render(bundle, scope.report()));
    MedicationCardBuilder.add(bundle, binary);
    return bundle;
  }

  private static MedicationRequest request(
      Medication med,
      Medicament m,
      String path,
      Reference patient,
      Reference author,
      String language,
      MappingScope scope) {
    MedicationRequest request = new MedicationRequest();
    request.setId(scope.id(path));
    request.getMeta().addProfile(Profiles.MEDICATION_REQUEST);
    request.addContained(MedicationMapper.mapMedication(m, path, scope));
    request.addIdentifier().setSystem(Profiles.URI).setValue(scope.ref(path + "/identifier"));
    request.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
    request.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
    request.setMedication(new Reference("#" + MedicationMapper.CONTAINED_ID));
    request.setSubject(new Reference(patient.getReference()));
    request.setAuthoredOnElement(new DateTimeType(med.dt()));
    if (author != null) {
      request.setRequester(new Reference(author.getReference()));
    }
    if (present(m.tkgRsn())) {
      request.addReasonCode().setText(m.tkgRsn());
    }
    if (present(m.prscbBy())) {
      scope
          .report()
          .add(
              ReportEntry.warning(
                  ReasonCode.UNMAPPABLE_ELEMENT,
                  Phase.MAP,
                  path + "/PrscbBy",
                  "The prescriber of a medicament has no representation in the CH EMED Medication"
                      + " Prescription; the author of the document is the requester"));
    }

    if (m.pos().isEmpty()) {
      MedicationCardBuilder.unitWithoutPosology(m, path, scope);
      String note = Labels.get(language, Labels.Key.NO_DOSAGE);
      Dosage dosage = DosageToFhirMapper.noPosology(m, note, path, language, scope);
      if (!dosage.hasPatientInstruction()) {
        // the profile requires a patient instruction or timing with dose (ch-dosage-3)
        dosage.setPatientInstruction(note);
      }
      request.addDosageInstruction(dosage);
      scope
          .report()
          .add(
              ReportEntry.information(
                      ReasonCode.DERIVED_VALUE,
                      Phase.MAP,
                      path + "/Pos",
                      "No posology given; CH EMED requires a dosage, a localized note is set")
                  .withTargetPath("MedicationRequest.dosageInstruction.text"));
    } else {
      DosageToFhirMapper.map(m.pos().get(0), m, path + "/Pos/0", language, scope)
          .forEach(request::addDosageInstruction);
      satisfyDosageConstraint(request.getDosageInstructionFirstRep(), path, scope);
    }
    PrescriptionDetailsMapper.apply(request, m, med, path, scope);
    return request;
  }

  /**
   * ch-dosage-3 of the MedicationRequest base entry: a patient instruction, or timing with dose. A
   * dosage carried as free text only gets the text as patient instruction too.
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
                    path + "/Pos/0",
                    "The free-text dosage is also set as patient instruction (required by the"
                        + " CH EMED MedicationRequest dosage profile)")
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
    c.getIdentifier().setSystem(Profiles.URI).setValue("urn:uuid:" + med.id());
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
    MedicationCardBuilder.confidentiality(c, scope);

    MedicationCardBuilder.originalRepresentationSection(c, language, scope);

    SectionComponent prescription = c.addSection();
    prescription.setTitle(Labels.get(language, Labels.Key.SECTION_PRESCRIPTION));
    prescription.setCode(
        new CodeableConcept(new Coding(Profiles.LOINC, "57828-6", "Prescription list")));
    for (MedicationRequest r : requests) {
      prescription.addEntry(new Reference("urn:uuid:" + r.getIdPart()));
    }

    MedicationCardBuilder.annotationSection(c, language, med.rmk());
    return c;
  }
}
