package ch.emed.converter.chmed23a.model;

/** The author's organization (README "HealthcareOrganization"). */
public record HealthcareOrganization(
    String gln, String name, String street, String zip, String city, String country, String zsr) {}
