package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.model.HcOrg;
import ch.emed.converter.chmed16a.model.HcPerson;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.core.api.AuthorDetails;
import ch.emed.converter.core.api.OrganizationDetails;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/** CHMED16A Auth (+ caller author details, FR-023) → CH EMED author (mapping spec M-AUT-*). */
public final class AuthorMapper {

  private static final Pattern GLN = Pattern.compile("^\\d{13}$");

  /**
   * Mapped author.
   *
   * @param reference reference to use as Composition.author and informationSource
   * @param resources resources to add to the Bundle (PractitionerRole, Practitioner, Organization)
   */
  public record Author(Reference reference, List<Resource> resources) {}

  private AuthorMapper() {}

  /**
   * Maps the author.
   *
   * @return the author, or null if required data is missing (ERROR entries)
   */
  @MappingRule({"M-AUT-01", "M-AUT-02", "M-AUT-03"})
  public static Author map(Medication med, Reference patient, MappingScope scope) {
    String auth = med.auth() == null ? "" : med.auth().trim();
    if (auth.toLowerCase(Locale.ROOT).startsWith("patient")) {
      return new Author(new Reference(patient.getReference()), List.of());
    }
    Optional<AuthorDetails> option = scope.options().author();
    if (option.isEmpty()) {
      missing(
          scope,
          "/Auth",
          "Author details (practitioner name, organisation with address) are required by CH EMED;"
              + " supply them as option");
      return null;
    }
    AuthorDetails details = option.get();
    String gln = GLN.matcher(auth).matches() ? auth : details.gln();
    boolean ok = true;
    if (!present(gln)) {
      missing(scope, "/Auth", "A practitioner GLN is required by CH EMED");
      ok = false;
    }
    if (!present(details.family())) {
      missing(scope, "/Auth", "The practitioner family name is required by CH EMED");
      ok = false;
    }
    OrganizationDetails org = details.organization();
    if (org == null
        || !present(org.name())
        || !present(org.street())
        || !present(org.zip())
        || !present(org.city())) {
      missing(scope, "/Auth", "The organisation name and address are required by CH EMED");
      ok = false;
    }
    if (!ok) {
      return null;
    }
    return build(gln, details.given(), details.family(), org, scope);
  }

  static Author build(
      String gln, String given, String family, OrganizationDetails org, MappingScope scope) {
    return build(
        new AuthorData(
            gln,
            given,
            family,
            null,
            org.name(),
            null,
            org.street(),
            org.zip(),
            org.city(),
            org.country(),
            List.of(),
            null,
            null),
        scope);
  }

  /** Everything needed to build the author resources. */
  record AuthorData(
      String gln,
      String given,
      String family,
      String personZsr,
      String orgName,
      String orgNameAffix,
      String street,
      String zip,
      String city,
      String country,
      List<String> orgZsr,
      String phone,
      String email) {}

  static Author build(AuthorData d, MappingScope scope) {
    Practitioner practitioner = new Practitioner();
    practitioner.setId(scope.id("/Author/Practitioner"));
    practitioner.getMeta().addProfile(Profiles.PRACTITIONER);
    practitioner.addIdentifier().setSystem(Profiles.GLN).setValue(d.gln());
    if (present(d.personZsr())) {
      practitioner.addIdentifier().setSystem(Profiles.ZSR).setValue(d.personZsr());
    }
    var name = practitioner.addName().setFamily(d.family());
    if (present(d.given())) {
      name.addGiven(d.given());
    }

    Organization organization = new Organization();
    organization.setId(scope.id("/Author/Organization"));
    organization.getMeta().addProfile(Profiles.ORGANIZATION);
    for (String zsr : d.orgZsr()) {
      organization.addIdentifier().setSystem(Profiles.ZSR).setValue(zsr);
    }
    organization.setName(d.orgName());
    if (present(d.orgNameAffix())) {
      organization.addAlias(d.orgNameAffix());
    }
    if (present(d.phone())) {
      organization
          .addTelecom()
          .setSystem(ContactPoint.ContactPointSystem.PHONE)
          .setValue(d.phone());
    }
    if (present(d.email())) {
      organization
          .addTelecom()
          .setSystem(ContactPoint.ContactPointSystem.EMAIL)
          .setValue(d.email());
    }
    Address address = organization.addAddress().addLine(d.street());
    address.setCity(d.city()).setPostalCode(d.zip());
    if (present(d.country())) {
      address.setCountry(d.country());
    }

    PractitionerRole role = new PractitionerRole();
    role.setId(scope.id("/Author/PractitionerRole"));
    role.getMeta().addProfile(Profiles.PRACTITIONER_ROLE);
    role.setPractitioner(new Reference(scope.ref("/Author/Practitioner")));
    role.setOrganization(new Reference(scope.ref("/Author/Organization")));

    Narratives.practitioner(practitioner);
    Narratives.organization(organization);
    Narratives.practitionerRole(role, practitioner, organization);

    return new Author(
        new Reference(scope.ref("/Author/PractitionerRole")),
        List.of(role, practitioner, organization));
  }

  /**
   * Maps the prescriber of a prescription: {@code HcPerson}, {@code HcOrg} and {@code Zsr}; the
   * option {@code author} supplies what a revision 1 prescription lacks (FR-023).
   *
   * @return the author, or null if required data is missing (ERROR entries)
   */
  @MappingRule({"M-RX-02"})
  public static Author mapPrescription(Medication med, MappingScope scope) {
    Optional<AuthorDetails> option = scope.options().author();
    HcPerson person = med.hcPerson();
    HcOrg org = med.hcOrg();
    String auth = med.auth() == null ? "" : med.auth().trim();
    boolean ok = true;

    String gln;
    String given;
    String family;
    String personZsr = null;
    if (person != null) {
      gln = person.gln();
      given = person.fName();
      family = person.lName();
      personZsr = person.zsr();
      if (GLN.matcher(auth).matches() && present(gln) && !auth.equals(gln)) {
        scope
            .report()
            .add(
                ReportEntry.warning(
                    ReasonCode.CHMED_INVALID_FIELD,
                    Phase.MAP,
                    "/Auth",
                    "Auth differs from the GLN of HcPerson; the GLN of HcPerson is used"));
      }
    } else if (option.isPresent()) {
      AuthorDetails details = option.get();
      gln = GLN.matcher(auth).matches() ? auth : details.gln();
      given = details.given();
      family = details.family();
    } else {
      missing(
          scope,
          "/HcPerson",
          "The prescriber (HcPerson, or the option author) is required by CH EMED");
      gln = null;
      given = null;
      family = null;
      ok = false;
    }
    if (person != null || option.isPresent()) {
      if (!present(gln)) {
        missing(scope, "/HcPerson/Gln", "A practitioner GLN is required by CH EMED");
        ok = false;
      }
      if (!present(family)) {
        missing(scope, "/HcPerson/LName", "The practitioner family name is required by CH EMED");
        ok = false;
      }
    }

    String orgName;
    String nameAffix = null;
    String street;
    String zip;
    String city;
    String country;
    String orgZsr = null;
    String phone = null;
    String email = null;
    if (org != null) {
      orgName = org.name();
      nameAffix = org.nameAffix();
      street = org.street();
      zip = org.zip();
      city = org.city();
      country = org.country();
      orgZsr = org.zsr();
      phone = org.phone();
      email = org.email();
    } else if (option.isPresent() && option.get().organization() != null) {
      OrganizationDetails details = option.get().organization();
      orgName = details.name();
      street = details.street();
      zip = details.zip();
      city = details.city();
      country = details.country();
    } else {
      missing(
          scope,
          "/HcOrg",
          "The prescriber organisation (HcOrg, or the option author) is required by CH EMED");
      return null;
    }
    if (!present(orgName) || !present(street) || !present(zip) || !present(city)) {
      missing(scope, "/HcOrg", "The organisation name and address are required by CH EMED");
      ok = false;
    }
    if (!ok) {
      return null;
    }

    List<String> zsrs = new ArrayList<>();
    if (present(orgZsr)) {
      zsrs.add(orgZsr);
    }
    if (present(med.zsr()) && !med.zsr().equals(personZsr) && !med.zsr().equals(orgZsr)) {
      zsrs.add(med.zsr());
      scope
          .report()
          .add(
              ReportEntry.information(
                  ReasonCode.DERIVED_VALUE,
                  Phase.MAP,
                  "/Zsr",
                  "The ZSR number matches neither the person nor the organisation and is attached"
                      + " to the organisation"));
    }
    return build(
        new AuthorData(
            gln, given, family, personZsr, orgName, nameAffix, street, zip, city, country, zsrs,
            phone, email),
        scope);
  }

  private static void missing(MappingScope scope, String path, String message) {
    scope
        .report()
        .add(ReportEntry.error(ReasonCode.MISSING_REQUIRED_DATA, Phase.MAP, path, message));
  }
}
