package ch.emed.converter.core.api;

/**
 * Organisation of the document author, supplied by the caller (FR-023).
 *
 * @param name organisation name
 * @param street street and number
 * @param zip postcode
 * @param city city
 * @param country ISO 3166 alpha-2 country code, may be null
 */
public record OrganizationDetails(
    String name, String street, String zip, String city, String country) {}
