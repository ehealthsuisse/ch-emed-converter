package ch.emed.converter.chmed23a.model;

/** The author as healthcare person (README "HealthcarePerson"). */
public record HealthcarePerson(String gln, String fName, String lName, String zsr) {}
