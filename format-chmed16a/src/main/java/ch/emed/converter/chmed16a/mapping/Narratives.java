package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

/**
 * Generated narratives (FHIR dom-6) for the resources of the Medication Card. Contained resources
 * get none (dom-1).
 */
final class Narratives {

  private Narratives() {}

  static void composition(Composition composition) {
    set(composition, composition.getTitle());
    if (composition.hasLanguage()) {
      // a resource with a language needs lang and xml:lang on its narrative
      XhtmlNode div = composition.getText().getDiv();
      div.setAttribute("xml:lang", composition.getLanguage());
      div.setAttribute("lang", composition.getLanguage());
    }
  }

  static void patient(Patient patient) {
    String name = patient.hasName() ? name(patient.getNameFirstRep()) : "";
    String birthDate =
        patient.hasBirthDate() ? patient.getBirthDateElement().getValueAsString() : "";
    set(patient, join(", ", name, birthDate));
  }

  static void statement(MedicationStatement statement) {
    String medication =
        statement.getContained().stream()
            .filter(Medication.class::isInstance)
            .map(r -> ((Medication) r).getCode().getText())
            .findFirst()
            .orElse("");
    String dosage =
        statement.getDosage().stream()
            .map(Dosage::getText)
            .filter(PatientMapper::present)
            .collect(Collectors.joining("; "));
    set(statement, join(": ", medication, dosage));
  }

  static void request(MedicationRequest request) {
    String medication =
        request.getContained().stream()
            .filter(Medication.class::isInstance)
            .map(r -> ((Medication) r).getCode().getText())
            .findFirst()
            .orElse("");
    String dosage =
        request.getDosageInstruction().stream()
            .map(Dosage::getText)
            .filter(PatientMapper::present)
            .collect(Collectors.joining("; "));
    set(request, join(": ", medication, dosage));
  }

  static void practitioner(Practitioner practitioner) {
    set(practitioner, practitioner.hasName() ? name(practitioner.getNameFirstRep()) : "");
  }

  static void organization(Organization organization) {
    String address =
        organization.hasAddress()
            ? join(
                ", ",
                organization.getAddressFirstRep().getLine().isEmpty()
                    ? ""
                    : organization.getAddressFirstRep().getLine().get(0).getValue(),
                join(
                    " ",
                    organization.getAddressFirstRep().getPostalCode(),
                    organization.getAddressFirstRep().getCity()))
            : "";
    set(organization, join(", ", organization.getName(), address));
  }

  static void practitionerRole(
      PractitionerRole role, Practitioner practitioner, Organization organization) {
    set(
        role,
        join(
            ", ",
            practitioner.hasName() ? name(practitioner.getNameFirstRep()) : "",
            organization.getName()));
  }

  private static String name(HumanName name) {
    String given = name.getGiven().stream().map(g -> g.getValue()).collect(Collectors.joining(" "));
    return join(" ", given, name.getFamily());
  }

  private static String join(String separator, String... parts) {
    return Stream.of(parts).filter(p -> present(p)).collect(Collectors.joining(separator));
  }

  private static void set(DomainResource resource, String text) {
    resource.setText(generated(present(text) ? text : resource.fhirType()));
  }

  /** Generated narrative with the text in a single XHTML div. */
  static Narrative generated(String text) {
    XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
    div.setAttribute("xmlns", "http://www.w3.org/1999/xhtml");
    div.addText(text);
    Narrative narrative = new Narrative();
    narrative.setStatus(Narrative.NarrativeStatus.GENERATED);
    narrative.setDiv(div);
    return narrative;
  }
}
