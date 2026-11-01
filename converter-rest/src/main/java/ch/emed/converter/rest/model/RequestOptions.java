package ch.emed.converter.rest.model;

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
 */
public record RequestOptions(
    Boolean strict,
    String language,
    String confidentiality,
    PatientIdentifier patientIdentifier,
    Author author) {

  /** Patient identifier, e.g. {@code urn:oid:…} and the number. */
  public record PatientIdentifier(String system, String value) {}

  /** Same structure as the CLI {@code --author} file. */
  public record Author(String gln, String given, String family, Organization organization) {}

  /** Organisation of the author. */
  public record Organization(String name, String street, String zip, String city, String country) {}
}
