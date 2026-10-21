package ch.emed.converter.chmed16a.model;

/** CHMED16A HealthcareOrganization (prescriptions, since revision 2). Country defaults to CH. */
public record HcOrg(
    String name,
    String nameAffix,
    String street,
    String zip,
    String city,
    String country,
    String zsr,
    String phone,
    String email) {}
