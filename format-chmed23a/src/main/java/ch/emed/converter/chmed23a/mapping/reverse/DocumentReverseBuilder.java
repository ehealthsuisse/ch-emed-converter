package ch.emed.converter.chmed23a.mapping.reverse;

import static ch.emed.converter.chmed23a.mapping.reverse.ReverseScope.present;

import ch.emed.converter.chmed23a.mapping.MappingRule;
import ch.emed.converter.chmed23a.mapping.PrescriptionDetailsMapper;
import ch.emed.converter.chmed23a.model.Medicament;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.chmed23a.model.Posology;
import ch.emed.converter.chmed23a.model.Repetition;
import ch.emed.converter.chmed23a.model.TimeUnit;
import ch.emed.converter.core.chemed.ChEmedDocumentReader.DocumentType;
import ch.emed.converter.core.chemed.Profiles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.SectionComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

/**
 * Builds a CHMED23A medication plan from a CH EMED Medication Card, or a prescription from a
 * Medication Prescription (mapping spec M23-REV-*, research R8-R11).
 */
public final class DocumentReverseBuilder {

  private static final Pattern UUID =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final Pattern PHARMACODE = Pattern.compile("Pharmacode (\\S.*)");
  private static final Pattern PRODUCT_NUMBER = Pattern.compile("Product number (\\S.*)");
  private static final String ANNOTATION_CODE = "48767-8";
  private static final Set<String> CONSUMED =
      Set.of(
          "Composition",
          "Patient",
          "PractitionerRole",
          "Practitioner",
          "Organization",
          "MedicationStatement",
          "MedicationRequest",
          "Medication",
          "Binary");

  private DocumentReverseBuilder() {}

  /**
   * Builds the document.
   *
   * @return the CHMED23A document, or null after ERROR entries
   */
  @MappingRule({"M23-REV-01", "M23-REV-02", "M23-REV-03", "M23-REV-04", "M23-REV-08"})
  public static Medication build(ReverseScope scope) {
    boolean plan = scope.document().type() == DocumentType.MEDICATION_CARD;
    Composition composition = scope.document().composition();
    String dt =
        composition.hasDateElement() ? composition.getDateElement().getValueAsString() : null;
    if (!present(dt)) {
      scope.missing("Composition.date", "The document date is required by CHMED23A");
    }
    PartiesReverseMapper.Author author = PartiesReverseMapper.author(scope, !plan);
    Patient source = scope.document().resolve(composition.getSubject(), Patient.class);
    ch.emed.converter.chmed23a.model.Patient patient = null;
    if (source == null) {
      scope.missing("Composition.subject", "The patient of the document cannot be resolved");
    } else {
      patient =
          PartiesReverseMapper.patient(source, language(composition, source, scope), plan, scope);
    }

    List<Medicament> meds = new ArrayList<>();
    String rec = null;
    if (plan) {
      List<MedicationStatement> statements = scope.document().all(MedicationStatement.class);
      for (int i = 0; i < statements.size(); i++) {
        Medicament m = statement(statements.get(i), "MedicationStatement[" + i + "]", scope);
        if (m != null) {
          meds.add(m);
        }
      }
    } else {
      List<MedicationRequest> requests = scope.document().all(MedicationRequest.class);
      for (int i = 0; i < requests.size(); i++) {
        String path = "MedicationRequest[" + i + "]";
        Medicament m = request(requests.get(i), path, dt, scope);
        if (m != null) {
          meds.add(m);
        }
        String performer = performer(requests.get(i));
        if (present(performer)) {
          if (rec != null && !rec.equals(performer)) {
            scope.unmapped(
                path + ".dispenseRequest.performer", "Only one recipient is carried over");
          } else {
            rec = performer;
          }
        }
      }
    }
    for (BundleEntryComponent entry : scope.document().bundle().getEntry()) {
      if (entry.hasResource() && !CONSUMED.contains(entry.getResource().fhirType())) {
        scope.unmapped(
            "Bundle.entry.resource.ofType(" + entry.getResource().fhirType() + ")",
            "Resources of this type are not carried over");
      }
    }
    if (scope.report().hasErrors() || author == null || patient == null) {
      return null;
    }
    return new Medication(
        patient,
        author.person(),
        author.org(),
        meds,
        List.of(),
        plan ? Medication.MEDICATION_PLAN : Medication.PRESCRIPTION,
        id(scope),
        author.auth(),
        rec,
        dt,
        remarks(composition, scope));
  }

  /** The document UUID of Bundle or Composition identifier, or null (the id is optional). */
  private static String id(ReverseScope scope) {
    Identifier[] candidates = {
      scope.document().bundle().getIdentifier(), scope.document().composition().getIdentifier()
    };
    for (Identifier identifier : candidates) {
      String value = identifier == null ? null : identifier.getValue();
      if (value != null && value.startsWith("urn:uuid:")) {
        value = value.substring("urn:uuid:".length());
      }
      if (present(value) && UUID.matcher(value).matches()) {
        return value;
      }
    }
    scope.unmapped(
        "Bundle.identifier", "The document identifier is not a UUID and not carried over");
    return null;
  }

  /** Two-letter language of the document, else of the patient, else of the option. */
  private static String language(Composition composition, Patient patient, ReverseScope scope) {
    if (composition.hasLanguage()) {
      return composition.getLanguage().substring(0, 2).toLowerCase(Locale.ROOT);
    }
    for (Patient.PatientCommunicationComponent c : patient.getCommunication()) {
      for (Coding coding : c.getLanguage().getCoding()) {
        if (present(coding.getCode()) && coding.getCode().length() >= 2) {
          return coding.getCode().substring(0, 2).toLowerCase(Locale.ROOT);
        }
      }
    }
    scope.derived(
        "Composition.language", "No language in the document; the default language is used");
    return scope.options().defaultLanguage().substring(0, 2);
  }

  /** Text content of the annotation sections (M23-REV-04). */
  private static String remarks(Composition composition, ReverseScope scope) {
    List<String> texts = new ArrayList<>();
    for (SectionComponent section : composition.getSection()) {
      boolean annotation =
          section.getCode().getCoding().stream().anyMatch(c -> ANNOTATION_CODE.equals(c.getCode()));
      if (!annotation || !section.hasText() || !section.getText().hasDiv()) {
        continue;
      }
      List<String> nodes = new ArrayList<>();
      collect(section.getText().getDiv(), nodes);
      String text = String.join(" ", nodes).replaceAll("\\s+", " ").trim();
      if (present(text)) {
        texts.add(text);
      }
    }
    return texts.isEmpty() ? null : String.join("\n", texts);
  }

  private static void collect(XhtmlNode node, List<String> texts) {
    if (node.getNodeType() == NodeType.Text) {
      if (present(node.getContent())) {
        texts.add(node.getContent());
      }
      return;
    }
    for (XhtmlNode child : node.getChildNodes()) {
      collect(child, texts);
    }
  }

  // ---------------------------------------------------------------------------------------------

  /** One medicament per MedicationStatement (M23-REV-11). */
  @MappingRule("M23-REV-11")
  private static Medicament statement(MedicationStatement st, String path, ReverseScope scope) {
    if (st.getStatus() == MedicationStatement.MedicationStatementStatus.ENTEREDINERROR) {
      scope.unmapped(path + ".status", "Medication excluded: it was entered in error");
      return null;
    }
    if (st.getStatus() == MedicationStatement.MedicationStatementStatus.STOPPED
        || st.getStatus() == MedicationStatement.MedicationStatementStatus.NOTTAKEN) {
      scope.unmappedWarning(
          path + ".status", "Medication excluded: its status is not representable", true);
      return null;
    }
    String[] identification =
        identification(st.getContained(), st.getMedication(), path, true, scope);
    if (identification == null) {
      return null;
    }
    Posology pos = PosologyFromFhirMapper.map(st.getDosage(), path + ".dosage", true, scope);
    boolean autoMed =
        st.getCategory().getCoding().stream().anyMatch(c -> "patientspecified".equals(c.getCode()));
    return new Medicament(
        identification[0],
        Integer.valueOf(identification[1]),
        pos == null ? List.of() : List.of(pos),
        reason(st.getReasonCode()),
        autoMed,
        null,
        null,
        null,
        null,
        null,
        List.of());
  }

  /** One medicament per MedicationRequest; requests are not merged (M23-REV-11, M23-REV-15). */
  @MappingRule({"M23-REV-15", "M23-REV-16"})
  private static Medicament request(
      MedicationRequest r, String path, String dt, ReverseScope scope) {
    if (r.getStatus() == MedicationRequest.MedicationRequestStatus.ENTEREDINERROR
        || r.getStatus() == MedicationRequest.MedicationRequestStatus.CANCELLED
        || r.getStatus() == MedicationRequest.MedicationRequestStatus.STOPPED) {
      scope.unmappedWarning(
          path + ".status", "Medication excluded: its status is not representable", true);
      return null;
    }
    String[] identification =
        identification(r.getContained(), r.getMedication(), path, false, scope);
    if (identification == null) {
      return null;
    }
    Posology pos =
        PosologyFromFhirMapper.map(
            r.getDosageInstruction(), path + ".dosageInstruction", false, scope);
    MedicationRequest.MedicationRequestDispenseRequestComponent dr = r.getDispenseRequest();
    BigDecimal nbPack = null;
    if (dr.hasQuantity()) {
      Quantity q = dr.getQuantity();
      if (PrescriptionDetailsMapper.PACKAGE_CODE.equals(q.getCode())) {
        nbPack = q.getValue();
      } else {
        scope.unmappedWarning(
            path + ".dispenseRequest.quantity", "Only a number of packages is carried over", true);
      }
    }
    Boolean isNotSub = null;
    if (r.hasSubstitution() && r.getSubstitution().hasAllowedCodeableConcept()) {
      String code = r.getSubstitution().getAllowedCodeableConcept().getCodingFirstRep().getCode();
      isNotSub = "N".equals(code);
    } else if (r.hasSubstitution() && r.getSubstitution().hasAllowedBooleanType()) {
      isNotSub = !r.getSubstitution().getAllowedBooleanType().booleanValue();
    }
    return new Medicament(
        identification[0],
        Integer.valueOf(identification[1]),
        pos == null ? List.of() : List.of(pos),
        reason(r.getReasonCode()),
        null,
        null,
        repetition(dr, dt, path, scope),
        isNotSub,
        null,
        nbPack,
        List.of());
  }

  /** Repeats and validity as a repetition object (M23-REV-15). */
  private static Repetition repetition(
      MedicationRequest.MedicationRequestDispenseRequestComponent dr,
      String dt,
      String path,
      ReverseScope scope) {
    Integer v = dr.hasNumberOfRepeatsAllowed() ? dr.getNumberOfRepeatsAllowed() : null;
    int[] duration = null;
    if (dr.hasValidityPeriod()) {
      duration = duration(dr.getValidityPeriod(), dt);
      if (duration == null) {
        scope.unmappedWarning(
            path + ".dispenseRequest.validityPeriod",
            "The validity period does not start on the prescription date and is not carried over",
            true);
      }
    }
    if (v != null && duration != null) {
      return new Repetition.NumberAndDuration(v, duration[0], duration[1]);
    }
    if (duration != null) {
      return new Repetition.Duration(duration[0], duration[1]);
    }
    return v == null ? null : new Repetition.Number(v);
  }

  /** {d, u} of a validity period that starts on the prescription date, end inclusive; or null. */
  private static int[] duration(Period period, String dt) {
    if (!period.hasStart() || !period.hasEnd() || dt == null || dt.length() < 10) {
      return null;
    }
    LocalDate start = LocalDate.parse(period.getStartElement().getValueAsString().substring(0, 10));
    LocalDate end =
        LocalDate.parse(period.getEndElement().getValueAsString().substring(0, 10)).plusDays(1);
    if (!start.equals(LocalDate.parse(dt.substring(0, 10))) || !end.isAfter(start)) {
      return null;
    }
    long years = ChronoUnit.YEARS.between(start, end);
    if (years > 0 && start.plusYears(years).equals(end)) {
      return new int[] {(int) years, TimeUnit.YEAR.code()};
    }
    long months = ChronoUnit.MONTHS.between(start, end);
    if (months > 0 && start.plusMonths(months).equals(end)) {
      return new int[] {(int) months, TimeUnit.MONTH.code()};
    }
    long days = ChronoUnit.DAYS.between(start, end);
    if (days % 7 == 0) {
      return new int[] {(int) (days / 7), TimeUnit.WEEK.code()};
    }
    return new int[] {(int) days, TimeUnit.DAY.code()};
  }

  private static String performer(MedicationRequest r) {
    Reference performer = r.getDispenseRequest().getPerformer();
    if (performer != null
        && performer.hasIdentifier()
        && Profiles.GLN.equals(performer.getIdentifier().getSystem())) {
      return performer.getIdentifier().getValue();
    }
    return null;
  }

  /**
   * {id, idType} of the contained Medication: GTIN coding → 2, text "Pharmacode …" → 3, "Product
   * number …" → 4 (plans only), other text → 1.
   */
  private static String[] identification(
      List<org.hl7.fhir.r4.model.Resource> contained,
      org.hl7.fhir.r4.model.Type medication,
      String path,
      boolean plan,
      ReverseScope scope) {
    org.hl7.fhir.r4.model.Medication med = null;
    CodeableConcept code = null;
    if (medication instanceof CodeableConcept cc) {
      code = cc;
    } else if (medication instanceof Reference ref && ref.hasReference()) {
      String id = ref.getReference().startsWith("#") ? ref.getReference().substring(1) : null;
      for (org.hl7.fhir.r4.model.Resource r : contained) {
        if (r instanceof org.hl7.fhir.r4.model.Medication m && r.getIdPart().equals(id)) {
          med = m;
        }
      }
      if (med == null) {
        med = scope.document().resolve(ref, org.hl7.fhir.r4.model.Medication.class);
      }
      code = med == null ? null : med.getCode();
    }
    if (code == null) {
      scope.missing(path + ".medication", "The product identification is required by CHMED23A");
      return null;
    }
    for (Coding c : code.getCoding()) {
      if (Profiles.GTIN.equals(c.getSystem()) && present(c.getCode())) {
        return new String[] {c.getCode(), String.valueOf(Medicament.ID_GTIN)};
      }
    }
    String text = code.getText();
    if (!present(text)) {
      scope.missing(
          path + ".medication.code", "The product identification is required by CHMED23A");
      return null;
    }
    var pharmacode = PHARMACODE.matcher(text);
    if (pharmacode.matches()) {
      return new String[] {pharmacode.group(1), String.valueOf(Medicament.ID_PHARMACODE)};
    }
    var product = PRODUCT_NUMBER.matcher(text);
    if (product.matches() && plan) {
      return new String[] {product.group(1), String.valueOf(Medicament.ID_PRODUCT_NUMBER)};
    }
    if (med != null && (med.hasForm() || med.hasIngredient() || med.hasAmount())) {
      scope.unmapped(
          path + ".medication", "Form, ingredients and amount of the product are not carried over");
    }
    return new String[] {text, String.valueOf(Medicament.ID_NONE)};
  }

  private static String reason(List<CodeableConcept> reasons) {
    for (CodeableConcept c : reasons) {
      if (present(c.getText())) {
        return c.getText();
      }
    }
    return null;
  }
}
