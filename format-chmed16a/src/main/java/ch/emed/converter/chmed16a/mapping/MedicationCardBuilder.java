package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.model.Posology;
import ch.emed.converter.core.api.Confidentiality;
import ch.emed.converter.core.render.OriginalRepresentationRenderer;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.DocumentConfidentiality;
import org.hl7.fhir.r4.model.Composition.SectionComponent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;

/** Builds the CH EMED 6.0.0 Medication Card document from a CHMED16A medication plan. */
public final class MedicationCardBuilder {

  static final Map<String, String> LANGUAGES =
      Map.of("de", "de-CH", "fr", "fr-CH", "it", "it-CH", "en", "en-CH");
  static final String ORIGINAL_PATH = "/OriginalRepresentation";
  private static final String HTML_ID = "original-representation";

  private MedicationCardBuilder() {}

  /**
   * Builds the document. Problems are reported in the scope; the caller checks for errors.
   *
   * @param med validated CHMED16A medication plan
   * @param scope mapping scope
   * @return the document Bundle
   */
  @MappingRule({
    "M-DOC-01",
    "M-DOC-04",
    "M-DOC-05",
    "M-DOC-06",
    "M-DOC-07",
    "M-DOC-08",
    "M-DOC-09",
    "M-DOC-11",
    "M-MED-08"
  })
  public static Bundle build(Medication med, MappingScope scope) {
    String language = language(med, scope);
    Patient patient = PatientMapper.map(med.patient(), med, scope);
    Reference patientRef = new Reference(scope.ref("/Patient"));
    AuthorMapper.Author author = AuthorMapper.map(med, patientRef, scope);
    Reference authorRef = author == null ? null : author.reference();

    List<MedicationStatement> statements = new ArrayList<>();
    for (int i = 0; i < med.medicaments().size(); i++) {
      Medicament m = med.medicaments().get(i);
      String medPath = "/Medicaments/" + i;
      if (m.pos().isEmpty()) {
        unitWithoutPosology(m, medPath, scope);
        MedicationStatement statement = statement(med, m, medPath, patientRef, authorRef, scope);
        statement.addDosage(
            DosageToFhirMapper.noPosology(
                m, Labels.get(language, Labels.Key.NO_DOSAGE), medPath, language, scope));
        MedicationMapper.applyStatementFields(statement, m, medPath, scope);
        scope
            .report()
            .add(
                ReportEntry.information(
                        ReasonCode.DERIVED_VALUE,
                        Phase.MAP,
                        medPath + "/Pos",
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
                    medPath + "/Pos",
                    "Medicament with several posologies is split into one MedicationStatement per"
                        + " posology"));
      }
      for (int j = 0; j < m.pos().size(); j++) {
        String posPath = medPath + "/Pos/" + j;
        Posology pos = m.pos().get(j);
        MedicationStatement statement = statement(med, m, posPath, patientRef, authorRef, scope);
        if (present(pos.dtTo()) && present(med.dt()) && endsBefore(pos.dtTo(), med.dt())) {
          statement.setStatus(MedicationStatement.MedicationStatementStatus.COMPLETED);
        }
        MedicationMapper.applyStatementFields(statement, m, medPath, scope);
        DosageToFhirMapper.map(pos, m, posPath, language, scope).forEach(statement::addDosage);
        statements.add(statement);
      }
    }
    MedicalDataMapper.map(med.patient().med(), scope);
    if (present(med.valBy()) || present(med.valDt())) {
      for (String field : new String[] {"ValBy", "ValDt"}) {
        String value = field.equals("ValBy") ? med.valBy() : med.valDt();
        if (present(value)) {
          scope
              .report()
              .add(
                  ReportEntry.warning(
                      ReasonCode.UNMAPPABLE_ELEMENT,
                      Phase.MAP,
                      "/" + field,
                      "Validation of the medication plan has no representation in the CH EMED"
                          + " Medication Card"));
        }
      }
    }

    Narratives.patient(patient);
    statements.forEach(Narratives::statement);
    Composition composition = composition(med, language, patientRef, authorRef, statements, scope);
    Narratives.composition(composition);

    Bundle bundle = new Bundle();
    bundle.getMeta().addProfile(Profiles.DOCUMENT_CARD);
    bundle.getIdentifier().setSystem(Profiles.URI).setValue("urn:uuid:" + med.id());
    bundle.setType(Bundle.BundleType.DOCUMENT);
    bundle.setTimestampElement(new org.hl7.fhir.r4.model.InstantType(med.dt()));
    add(bundle, composition);
    add(bundle, patient);
    if (author != null) {
      author.resources().forEach(r -> add(bundle, r));
    }
    statements.forEach(s -> add(bundle, s));

    Binary binary = new Binary();
    binary.setId(scope.id(ORIGINAL_PATH));
    binary.setContentType("application/pdf");
    binary.setData(OriginalRepresentationRenderer.render(bundle, scope.report()));
    add(bundle, binary);
    return bundle;
  }

  /** A unit has no dose to attach to without a posology: reported, not silently dropped. */
  @MappingRule("M-MED-09")
  static void unitWithoutPosology(Medicament m, String medPath, MappingScope scope) {
    if (present(m.unit())) {
      scope
          .report()
          .add(
              ReportEntry.information(
                  ReasonCode.UNMAPPABLE_ELEMENT,
                  Phase.MAP,
                  medPath + "/Unit",
                  "A unit without posology has no dose to attach to and is not carried over"));
    }
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
            m, path.contains("/Pos/") ? path.substring(0, path.indexOf("/Pos/")) : path, scope));
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
    c.getIdentifier().setSystem(Profiles.URI).setValue("urn:uuid:" + med.id());
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
    confidentiality(c, scope);

    originalRepresentationSection(c, language, scope);

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
    for (Resource s : statements) {
      card.addEntry(new Reference("urn:uuid:" + s.getIdPart()));
    }

    annotationSection(c, language, med.rmk());
    return c;
  }

  /** Section {@code originalRepresentation} (M-DOC-10) with the link to the PDF Binary. */
  @MappingRule("M-DOC-10")
  static void originalRepresentationSection(Composition c, String language, MappingScope scope) {
    SectionComponent original = c.addSection();
    original.setTitle(Labels.get(language, Labels.Key.SECTION_ORIGINAL));
    original.setCode(
        new CodeableConcept(
            new Coding(Profiles.LOINC, "55108-5", "Clinical presentation Document")));
    Narrative text = new Narrative();
    Extension link = new Extension(Profiles.EXT_TEXT_LINK);
    link.addExtension(new Extension("htmlid", new StringType(HTML_ID)));
    link.addExtension(new Extension("data", new UriType(scope.ref(ORIGINAL_PATH))));
    text.addExtension(link);
    text.setStatus(Narrative.NarrativeStatus.GENERATED);
    text.setDivAsString(
        "<div xmlns=\"http://www.w3.org/1999/xhtml\">Representation of the original view: <span id=\""
            + HTML_ID
            + "\">PDF</span></div>");
    original.setText(text);
    original.addEntry(new Reference(scope.ref(ORIGINAL_PATH)));
  }

  /** Section {@code annotation} (M-DOC-06) for a non-empty remark. */
  static void annotationSection(Composition c, String language, String remark) {
    if (!present(remark)) {
      return;
    }
    SectionComponent annotation = c.addSection();
    annotation.setTitle(Labels.get(language, Labels.Key.SECTION_ANNOTATION));
    annotation.setCode(
        new CodeableConcept(
            new Coding(
                Profiles.LOINC, "48767-8", "Annotation comment [Interpretation] Narrative")));
    annotation.setText(Narratives.generated(remark));
  }

  static void confidentiality(Composition c, MappingScope scope) {
    Confidentiality confidentiality = scope.options().confidentiality();
    c.setConfidentiality(DocumentConfidentiality.fromCode(confidentiality.hl7Code()));
    c.getConfidentialityElement()
        .addExtension(
            new Extension(
                Profiles.EXT_CONFIDENTIALITY,
                new CodeableConcept(
                    new Coding(
                        Profiles.SNOMED,
                        confidentiality.snomedCode(),
                        confidentiality.snomedDisplay()))));
    scope
        .report()
        .add(
            ReportEntry.information(
                    ReasonCode.DERIVED_VALUE,
                    Phase.MAP,
                    "/",
                    "Document confidentiality set from the conversion options")
                .withTargetPath("Composition.confidentiality"));
  }

  static String language(Medication med, MappingScope scope) {
    String lng = med.patient() == null ? null : med.patient().lng();
    String mapped = lng == null ? null : LANGUAGES.get(lng.trim().toLowerCase(Locale.ROOT));
    if (mapped != null) {
      scope
          .report()
          .add(
              ReportEntry.information(
                      ReasonCode.DERIVED_VALUE,
                      Phase.MAP,
                      "/Patient/Lng",
                      "Document language derived from the patient language")
                  .withTargetPath("Composition.language"));
      return mapped;
    }
    scope
        .report()
        .add(
            ReportEntry.information(
                    ReasonCode.DERIVED_VALUE,
                    Phase.MAP,
                    "/Patient/Lng",
                    "Patient language absent or not a CH EMED language; default document language"
                        + " used")
                .withTargetPath("Composition.language"));
    return scope.options().defaultLanguage();
  }

  private static boolean endsBefore(String dtTo, String documentDate) {
    try {
      return LocalDate.parse(dtTo).isBefore(OffsetDateTime.parse(documentDate).toLocalDate());
    } catch (RuntimeException e) {
      return false;
    }
  }

  static void add(Bundle bundle, Resource resource) {
    bundle.addEntry().setFullUrl("urn:uuid:" + resource.getIdPart()).setResource(resource);
  }
}
