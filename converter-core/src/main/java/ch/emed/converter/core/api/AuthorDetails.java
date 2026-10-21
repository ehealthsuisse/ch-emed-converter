package ch.emed.converter.core.api;

/**
 * Author details supplied by the caller when the source lacks the data CH EMED requires (FR-023).
 *
 * @param gln practitioner GLN; may be null if the source author is a GLN
 * @param given practitioner given name, may be null
 * @param family practitioner family name
 * @param organization organisation the practitioner works for
 */
public record AuthorDetails(
    String gln, String given, String family, OrganizationDetails organization) {}
