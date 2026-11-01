package ch.emed.converter.chmed16a.model;

/** CHMED16A HealthcarePerson (prescriptions, since revision 2). */
public record HcPerson(String gln, String fName, String lName, String zsr) {}
