package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.mapping.MappingRule;
import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.chmed16a.model.Medication;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.SectionComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

/** Builds the CHMED16A medication plan from a CH EMED Medication Card (mapping spec M-REV-01..). */
public final class MedicationPlanBuilder {

  private static final Pattern UUID =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
  private static final String ANNOTATION_CODE = "48767-8";
  private static final String ANNOTATION_PATH =
      "Composition.section.where(code='" + ANNOTATION_CODE + "').text";
  private static final Set<String> CONSUMED_TYPES =
      Set.of(
          "Composition",
          "Patient",
          "PractitionerRole",
          "Practitioner",
          "Organization",
          "MedicationStatement",
          "Medication",
          "Binary");

  private MedicationPlanBuilder() {}

  /**
   * Builds the plan. Problems are reported in the scope.
   *
   * @return the plan, or null if required data is missing (ERROR entries)
   */
  @MappingRule({"M-REV-01", "M-REV-02", "M-REV-03", "M-REV-05", "M-REV-13", "M-REV-25"})
  public static Medication build(ReverseScope scope) {
    Composition composition = scope.document().composition();

    String id = id(scope);
    String dt =
        composition.hasDateElement() ? composition.getDateElement().getValueAsString() : null;
    if (!present(dt)) {
      scope.missing("Composition.date", "The document date is required by CHMED16A");
    }

    AuthorReverseMapper.Author author = AuthorReverseMapper.map(scope);

    Patient source = scope.document().resolve(composition.getSubject(), Patient.class);
    ch.emed.converter.chmed16a.model.Patient patient = null;
    if (source == null) {
      scope.missing("Composition.subject", "The patient of the document cannot be resolved");
    } else {
      patient = PatientReverseMapper.map(source, scope);
    }

    List<Medicament> medicaments = new ArrayList<>();
    List<MedicationStatement> statements = scope.document().all(MedicationStatement.class);
    for (int i = 0; i < statements.size(); i++) {
      Medicament medicament =
          MedicationReverseMapper.map(statements.get(i), "MedicationStatement[" + i + "]", scope);
      if (medicament != null) {
        medicaments.add(medicament);
      }
    }
    long derivedAutoMed =
        medicaments.stream().filter(m -> m.autoMed() != null && m.autoMed() == 0).count();
    if (derivedAutoMed > 0) {
      scope.derived(
          "MedicationStatement.category",
          "AutoMed is set to 0 for "
              + derivedAutoMed
              + " medicament(s) without a patient-specified category (required by CHMED16A)");
    }
    otherResources(scope);

    if (scope.report().hasErrors() || author == null) {
      return null;
    }
    return new Medication(
        patient,
        medicaments,
        false,
        Medication.MED_TYPE_PLAN,
        id,
        author.auth(),
        author.zsr(),
        dt,
        remarks(composition, scope),
        null,
        null,
        null,
        null,
        null,
        null,
        List.of());
  }

  /** M-REV-02: Bundle.identifier, else Composition.identifier; {@code urn:uuid:} prefix removed. */
  static String id(ReverseScope scope) {
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
    scope.missing(
        "Bundle.identifier", "The document identifier (a UUID) is required by CHMED16A as Id");
    return null;
  }

  /** M-REV-05: text content of the annotation sections. */
  static String remarks(Composition composition, ReverseScope scope) {
    List<String> texts = new ArrayList<>();
    for (SectionComponent section : composition.getSection()) {
      boolean annotation =
          section.getCode().getCoding().stream().anyMatch(c -> ANNOTATION_CODE.equals(c.getCode()));
      if (!annotation || !section.hasText() || !section.getText().hasDiv()) {
        continue;
      }
      List<String> nodes = new ArrayList<>();
      collect(section.getText().getDiv(), nodes);
      if (nodes.size() > 1) {
        scope.derived(ANNOTATION_PATH, "The markup of the annotation is removed");
      }
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

  /** Resources the mapping does not consume are reported. */
  private static void otherResources(ReverseScope scope) {
    List<BundleEntryComponent> entries = scope.document().bundle().getEntry();
    for (BundleEntryComponent entry : entries) {
      if (entry.hasResource() && !CONSUMED_TYPES.contains(entry.getResource().fhirType())) {
        scope.unmapped(
            "Bundle.entry.resource.ofType(" + entry.getResource().fhirType() + ")",
            "Resources of this type are not carried over");
      }
    }
  }
}
