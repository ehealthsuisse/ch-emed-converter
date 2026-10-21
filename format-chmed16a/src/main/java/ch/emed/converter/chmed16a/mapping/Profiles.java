package ch.emed.converter.chmed16a.mapping;

/** Canonical URLs and code systems of the pinned CH EMED 6.0.0 / CH Core 6.0.0 packages. */
public final class Profiles {

  public static final String DOCUMENT_CARD =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-document-medicationcard";
  public static final String COMPOSITION_CARD =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-composition-medicationcard";
  public static final String DOCUMENT_PRESCRIPTION =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-document-medicationprescription";
  public static final String COMPOSITION_PRESCRIPTION =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-composition-medicationprescription";
  public static final String MEDICATION_REQUEST =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-medicationrequest";
  public static final String STATEMENT_CARD =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-medicationstatement-card";
  public static final String MEDICATION =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-medication";
  public static final String PATIENT =
      "http://fhir.ch/ig/ch-core/StructureDefinition/ch-core-patient-epr";
  public static final String PRACTITIONER =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-practitioner";
  public static final String PRACTITIONER_ROLE =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-practitionerrole";
  public static final String ORGANIZATION =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-organization";
  public static final String EXT_CONFIDENTIALITY =
      "http://fhir.ch/ig/ch-core/StructureDefinition/ch-ext-epr-confidentialitycode";
  public static final String EXT_TEXT_LINK = "http://hl7.org/fhir/StructureDefinition/textLink";

  public static final String URI = "urn:ietf:rfc:3986";
  public static final String GLN = "urn:oid:2.51.1.3";
  public static final String GTIN = "urn:oid:2.51.1.1";
  public static final String ZSR = "urn:oid:2.16.756.5.30.1.123.100.2.1.1";
  public static final String VEKA = "urn:oid:2.16.756.5.30.1.123.100.1.1.1";
  public static final String EDQM = "urn:oid:0.4.0.127.0.16.1.1.2.1";
  public static final String LOINC = "http://loinc.org";
  public static final String SNOMED = "http://snomed.info/sct";
  public static final String BCP47 = "urn:ietf:bcp:47";
  public static final String STATEMENT_CATEGORY =
      "http://terminology.hl7.org/CodeSystem/medication-statement-category";
  public static final String SUBSTITUTION =
      "http://terminology.hl7.org/CodeSystem/v3-substanceAdminSubstitution";
  public static final String LIST_EMPTY_REASON =
      "http://terminology.hl7.org/CodeSystem/list-empty-reason";

  private Profiles() {}
}
