package ch.emed.converter.epr;

import ch.emed.converter.epr.mapping.ProfileMap;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;

/**
 * Document types of features 004 and 005 (data-model.md), recognised by the SNOMED CT code of the
 * Composition {@code type}, which CH EMED fixes per document profile.
 */
public enum EprDocumentType {
  /** Medication Card ↔ PMLC. */
  PMLC("736378000", "medicationcard"),
  /** Medication Prescription ↔ PRE. */
  PRE("761938008", "medicationprescription"),
  /** Medication Treatment Plan ↔ MTP. */
  MTP("761931002", "medicationtreatmentplan"),
  /** Medication Dispense ↔ DIS (feature 005). */
  DIS("82291000195104", "medicationdispense"),
  /** Pharmaceutical Advice ↔ PADV (feature 005). */
  PADV("419891008", "pharmaceuticaladvice"),
  /** Medication List ↔ PML (feature 005). */
  PML("721912009", "medicationlist");

  private static final String SNOMED = "http://snomed.info/sct";

  private final String snomedCode;
  private final String profileSuffix;

  EprDocumentType(String snomedCode, String profileSuffix) {
    this.snomedCode = snomedCode;
    this.profileSuffix = profileSuffix;
  }

  public String snomedCode() {
    return snomedCode;
  }

  public String chEmedDocumentProfile() {
    return ProfileMap.CH_EMED + "ch-emed-document-" + profileSuffix;
  }

  public String eprDocumentProfile() {
    return ProfileMap.EPR + "ch-emed-epr-document-" + profileSuffix;
  }

  public String chEmedCompositionProfile() {
    return ProfileMap.CH_EMED + "ch-emed-composition-" + profileSuffix;
  }

  public String eprCompositionProfile() {
    return ProfileMap.EPR + "ch-emed-epr-composition-" + profileSuffix;
  }

  /**
   * Recognises the document type of a document Bundle.
   *
   * @param bundle document Bundle
   * @return the type, or empty if the Composition is missing or of another type
   */
  @MappingRule("M-EPR-02")
  public static Optional<EprDocumentType> of(Bundle bundle) {
    return snomedType(bundle)
        .flatMap(
            code -> {
              for (EprDocumentType type : values()) {
                if (type.snomedCode.equals(code)) {
                  return Optional.of(type);
                }
              }
              return Optional.empty();
            });
  }

  /**
   * Message for a document of another type, naming the supported types; without content.
   *
   * @param bundle document Bundle
   * @return message
   */
  public static String unsupportedMessage(Bundle bundle) {
    return "Document type not recognised; supported: medication card (PMLC), prescription (PRE),"
        + " treatment plan (MTP), dispense (DIS), pharmaceutical advice (PADV), medication list"
        + " (PML)";
  }

  private static Optional<String> snomedType(Bundle bundle) {
    if (bundle.getEntry().isEmpty()
        || !(bundle.getEntryFirstRep().getResource() instanceof Composition composition)) {
      return Optional.empty();
    }
    return composition.getType().getCoding().stream()
        .filter(c -> SNOMED.equals(c.getSystem()))
        .map(Coding::getCode)
        .findFirst();
  }
}
