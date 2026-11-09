package ch.emed.converter.epr.mapping;

import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.MappingRule;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.hl7.fhir.r4.model.Resource;

/**
 * Profiles of CH EMED EPR 3.0.0 and the CH EMED or CH Core profiles they are based on (research R4,
 * F3; mapping rule M-EPR-01). Taken from {@code baseDefinition} of the vendored package.
 */
@MappingRule("M-EPR-01")
public final class ProfileMap {

  /** Canonical base of the CH EMED EPR profiles. */
  public static final String EPR = "http://fhir.ch/ig/ch-emed-epr/StructureDefinition/";

  /** Canonical base of the CH EMED profiles. */
  public static final String CH_EMED = "http://fhir.ch/ig/ch-emed/StructureDefinition/";

  /** Canonical base of the CH Core profiles. */
  public static final String CH_CORE = "http://fhir.ch/ig/ch-core/StructureDefinition/";

  /** MedicationStatement profile of a treatment plan in CH EMED. */
  static final String CH_EMED_STATEMENT = CH_EMED + "ch-emed-medicationstatement";

  /** MedicationStatement profile of a treatment plan in CH EMED EPR. */
  static final String EPR_STATEMENT_TREATMENT_PLAN =
      EPR + "ch-emed-epr-medicationstatement-treatmentplan";

  /** EPR profile → base profile; an empty value means a FHIR core base (no profile declared). */
  private static final Map<String, String> TO_CH_EMED = new LinkedHashMap<>();

  /** CH EMED or CH Core profile → EPR profile, for the profiles independent of document type. */
  private static final Map<String, String> TO_EPR = new LinkedHashMap<>();

  static {
    for (String suffix :
        new String[] {
          "composition-medicationcard",
          "composition-medicationdispense",
          "composition-medicationlist",
          "composition-medicationprescription",
          "composition-medicationtreatmentplan",
          "composition-pharmaceuticaladvice",
          "document-medicationcard",
          "document-medicationdispense",
          "document-medicationlist",
          "document-medicationprescription",
          "document-medicationtreatmentplan",
          "document-pharmaceuticaladvice",
          "medication",
          "medication-medicationdispense",
          "medicationdispense",
          "medicationdispense-list",
          "medicationrequest",
          "medicationrequest-changed",
          "medicationrequest-list",
          "medicationstatement-card",
          "medicationstatement-changed",
          "medicationstatement-changed-list",
          "medicationstatement-list",
          "observation",
          "observation-list",
          "organization",
          "practitioner",
          "practitionerrole"
        }) {
      both(EPR + "ch-emed-epr-" + suffix, CH_EMED + "ch-emed-" + suffix);
    }
    // names that differ between the two IGs
    both(
        EPR + "ch-emed-epr-medication-request-changed-list",
        CH_EMED + "ch-emed-medicationrequest-changed-list");
    TO_CH_EMED.put(EPR_STATEMENT_TREATMENT_PLAN, CH_EMED_STATEMENT);
    both(EPR + "ch-emed-epr-patient", CH_CORE + "ch-core-patient-epr");
    both(EPR + "ch-emed-epr-related-person", CH_CORE + "ch-core-relatedperson");
    TO_EPR.put(CH_CORE + "ch-core-organization", EPR + "ch-emed-epr-organization");
    // FHIR core bases: the EPR profile is dropped towards CH EMED
    TO_CH_EMED.put(EPR + "ch-emed-epr-device", "");
    TO_CH_EMED.put(EPR + "ch-emed-epr-observation-body-weight", "");
  }

  private ProfileMap() {}

  private static void both(String epr, String chEmed) {
    TO_CH_EMED.put(epr, chEmed);
    TO_EPR.put(chEmed, epr);
  }

  /**
   * CH EMED or CH Core profile for an EPR profile.
   *
   * @param eprProfile profile URL
   * @return base profile URL, empty string for a FHIR core base, or empty if not an EPR profile
   */
  public static Optional<String> toChEmed(String eprProfile) {
    return Optional.ofNullable(TO_CH_EMED.get(eprProfile));
  }

  /**
   * EPR profile for a CH EMED or CH Core profile declared on a resource of a document of the given
   * type. A general CH EMED MedicationStatement becomes the treatment-plan statement in an MTP.
   *
   * @param profile profile URL
   * @param type document type
   * @return EPR profile URL, or empty if there is none
   */
  public static Optional<String> toEpr(String profile, EprDocumentType type) {
    if (CH_EMED_STATEMENT.equals(profile)) {
      return type == EprDocumentType.MTP
          ? Optional.of(EPR_STATEMENT_TREATMENT_PLAN)
          : Optional.empty();
    }
    return Optional.ofNullable(TO_EPR.get(profile));
  }

  /**
   * True if a resource declares at least one CH EMED EPR profile.
   *
   * @param resource resource
   * @return whether it declares an EPR profile
   */
  public static boolean declaresEpr(Resource resource) {
    return resource.getMeta().getProfile().stream()
        .anyMatch(p -> p.getValue() != null && p.getValue().startsWith(EPR));
  }
}
