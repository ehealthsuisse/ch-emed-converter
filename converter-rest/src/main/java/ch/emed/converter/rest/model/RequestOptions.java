package ch.emed.converter.rest.model;

import java.util.List;

/**
 * Conversion options of a request, the same as the CLI options (data-model §3, research R12).
 * Absent members keep the library defaults. Patient identifier and author contain personal data and
 * travel only in the request body.
 *
 * @param strict fail if clinically relevant information cannot be represented exactly
 * @param language document language if the source has none: de-CH, fr-CH, it-CH, en-CH
 * @param confidentiality normal, restricted or secret
 * @param patientIdentifier used only if the source has no patient identifier
 * @param author used only where the source lacks author details required by CH EMED
 * @param outputForm document, qr-payload, qr-image (feature 003) or treatment-plans (feature 004)
 * @param treatmentPlanReferences treatment-plan references for target ch-emed-epr, in document
 *     order (feature 004, research R15)
 * @param originalRepresentation a PDF/A in base64 for target ch-emed-epr (feature 004, R16)
 */
public record RequestOptions(
    Boolean strict,
    String language,
    String confidentiality,
    PatientIdentifier patientIdentifier,
    Author author,
    String outputForm,
    List<TreatmentPlanReference> treatmentPlanReferences,
    String originalRepresentation) {

  /** Reference to an MTP document and its MedicationStatement, both {@code urn:uuid:}. */
  public record TreatmentPlanReference(String documentId, String statementId) {}

  /** Patient identifier, e.g. {@code urn:oid:…} and the number. */
  public record PatientIdentifier(String system, String value) {}

  /** Same structure as the CLI {@code --author} file. */
  public record Author(String gln, String given, String family, Organization organization) {}

  /** Organisation of the author. */
  public record Organization(String name, String street, String zip, String city, String country) {}
}
