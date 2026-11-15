package ch.emed.converter.chmed23a.mapping;

import static ch.emed.converter.chmed23a.mapping.Documents.add;

import ch.emed.converter.chmed23a.model.Medicament;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.chmed23a.model.Posology;
import ch.emed.converter.core.chemed.Labels;
import ch.emed.converter.core.chemed.Narratives;
import ch.emed.converter.core.chemed.Profiles;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.SectionComponent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;

/** Builds the CH EMED 6.0.0 Medication Card from a CHMED23A medication plan (M23-DOC-*). */
public final class MedicationCardBuilder {

  private MedicationCardBuilder() {}

  /**
   * Builds the document. Problems are reported in the scope; the caller checks for errors.
   *
   * @param med validated CHMED23A medication plan
   * @param scope mapping scope
   * @return the document Bundle
   */
  @MappingRule({"M23-DOC-01", "M23-DOC-05", "M23-DOC-10", "M23-MED-08", "M23-MED-09"})
  public static Bundle build(Medication med, MappingScope scope) {
    String language = Documents.language(med, scope);
    Patient patient = PatientMapper.map(med.patient(), scope);
    Reference patientRef = new Reference(scope.ref("/patient"));
    AuthorMapper.Author author = AuthorMapper.map(med, patientRef, scope);
    Reference authorRef = author == null ? null : author.reference();

    List<MedicationStatement> statements = new ArrayList<>();
    for (int i = 0; i < med.meds().size(); i++) {
      Medicament m = med.meds().get(i);
      String medPath = "/meds/" + i;
      PatientMapper.extensions(m.exts(), medPath + "/exts", scope);
      if (m.pos().isEmpty()) {
        MedicationStatement statement = statement(med, m, medPath, patientRef, authorRef, scope);
        statement.addDosage(
            PosologyToFhirMapper.noPosology(Labels.get(language, Labels.Key.NO_DOSAGE)));
        MedicationMapper.applyStatementFields(statement, m, medPath, scope);
        scope
            .report()
            .add(
                ReportEntry.information(
                        ReasonCode.DERIVED_VALUE,
                        Phase.MAP,
                        medPath + "/pos",
                        "No posology given; CH EMED requires a dosage, a localized note is set")
                    .withTargetPath("MedicationStatement.dosage.text"));
        statements.add(statement);
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
                    "Medicament with several posologies is split into one MedicationStatement per"
                        + " posology"));
      }
      for (int j = 0; j < m.pos().size(); j++) {
        String posPath = medPath + "/pos/" + j;
        Posology pos = m.pos().get(j);
        MedicationStatement statement = statement(med, m, posPath, patientRef, authorRef, scope);
        if (pos.dtTo() != null && med.dt() != null && endsBefore(pos.dtTo(), med.dt())) {
          statement.setStatus(MedicationStatement.MedicationStatementStatus.COMPLETED);
        }
        MedicationMapper.applyStatementFields(statement, m, medPath, scope);
        PosologyToFhirMapper.map(pos, posPath, language, scope).forEach(statement::addDosage);
        statements.add(statement);
      }
    }
    PatientMapper.extensions(med.exts(), "/exts", scope);

    Narratives.patient(patient);
    statements.forEach(Narratives::statement);
    Composition composition = composition(med, language, patientRef, authorRef, statements, scope);
    Narratives.composition(composition);

    Bundle bundle = new Bundle();
    bundle.getMeta().addProfile(Profiles.DOCUMENT_CARD);
    bundle.getIdentifier().setSystem(Profiles.URI).setValue("urn:uuid:" + scope.documentId());
    bundle.setType(Bundle.BundleType.DOCUMENT);
    bundle.setTimestampElement(new InstantType(med.dt()));
    add(bundle, composition);
    add(bundle, patient);
    if (author != null) {
      author.resources().forEach(r -> add(bundle, r));
    }
    statements.forEach(s -> add(bundle, s));
    Documents.addOriginalRepresentation(bundle, scope);
    return bundle;
  }

  private static MedicationStatement statement(
      Medication med,
      Medicament m,
      String path,
      Reference patient,
      Reference author,
      MappingScope scope) {
    MedicationStatement statement = new MedicationStatement();
    statement.setId(scope.id(path));
    statement.getMeta().addProfile(Profiles.STATEMENT_CARD);
    statement.addContained(
        MedicationMapper.mapMedication(
            m, path.contains("/pos/") ? path.substring(0, path.indexOf("/pos/")) : path, scope));
    statement.addIdentifier().setSystem(Profiles.URI).setValue(scope.ref(path + "/identifier"));
    statement.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);
    statement.setMedication(new Reference("#" + MedicationMapper.CONTAINED_ID));
    statement.setSubject(new Reference(patient.getReference()));
    statement.setDateAssertedElement(new DateTimeType(med.dt()));
    if (author != null) {
      statement.setInformationSource(new Reference(author.getReference()));
    }
    return statement;
  }

  private static Composition composition(
      Medication med,
      String language,
      Reference patient,
      Reference author,
      List<MedicationStatement> statements,
      MappingScope scope) {
    Composition c = new Composition();
    c.setId(scope.id("/Composition"));
    c.getMeta().addProfile(Profiles.COMPOSITION_CARD);
    c.setLanguage(language);
    c.getIdentifier().setSystem(Profiles.URI).setValue("urn:uuid:" + scope.documentId());
    c.setStatus(Composition.CompositionStatus.FINAL);
    c.getType()
        .addCoding(new Coding(Profiles.LOINC, "56445-0", "Medication summary Document"))
        .addCoding(
            new Coding(
                Profiles.SNOMED, "736378000", "Medication management plan (record artifact)"));
    c.setSubject(new Reference(patient.getReference()));
    c.setDateElement(new DateTimeType(med.dt()));
    if (author != null) {
      c.addAuthor(new Reference(author.getReference()));
    }
    c.setTitle(Labels.get(language, Labels.Key.TITLE_CARD));
    Documents.confidentiality(c, scope);
    Documents.originalRepresentationSection(c, language, scope);

    SectionComponent card = c.addSection();
    card.setTitle(Labels.get(language, Labels.Key.SECTION_CARD));
    card.setCode(
        new CodeableConcept(
            new Coding(Profiles.LOINC, "10160-0", "History of Medication use Narrative")));
    if (statements.isEmpty()) {
      card.setEmptyReason(
          new CodeableConcept(new Coding(Profiles.LIST_EMPTY_REASON, "nilknown", "Nil Known")));
      card.setText(Narratives.generated(Labels.get(language, Labels.Key.NO_MEDICATION)));
    }
    for (MedicationStatement s : statements) {
      card.addEntry(new Reference("urn:uuid:" + s.getIdPart()));
    }
    Documents.annotationSection(c, language, med.rmk());
    return c;
  }

  private static boolean endsBefore(String dtTo, String documentDate) {
    try {
      return LocalDate.parse(dtTo.substring(0, 10))
          .isBefore(OffsetDateTime.parse(documentDate).toLocalDate());
    } catch (RuntimeException e) {
      return false;
    }
  }
}
