package ch.emed.converter.chmed23a.mapping.reverse;

import static ch.emed.converter.chmed23a.mapping.reverse.ReverseScope.present;

import ch.emed.converter.chmed23a.mapping.MappingRule;
import ch.emed.converter.chmed23a.model.HealthcareOrganization;
import ch.emed.converter.chmed23a.model.HealthcarePerson;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.chmed23a.model.PatientId;
import ch.emed.converter.core.api.AuthorDetails;
import ch.emed.converter.core.api.OrganizationDetails;
import ch.emed.converter.core.chemed.Profiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/** CH EMED patient and author → CHMED23A (mapping spec M23-REV-05..10, research R8, R11). */
public final class PartiesReverseMapper {

  /**
   * The author.
   *
   * @param auth 1 healthcare person, 2 patient
   * @param person {@code hcPerson}, or null
   * @param org {@code hcOrg}, or null
   */
  public record Author(int auth, HealthcarePerson person, HealthcareOrganization org) {}

  private PartiesReverseMapper() {}

  /**
   * Maps the first author of the Composition.
   *
   * @param prescription true for a prescription (needs a healthcare person)
   * @return the author, or null after ERROR entries
   */
  @MappingRule({"M23-REV-05", "M23-REV-06"})
  public static Author author(ReverseScope scope, boolean prescription) {
    List<Reference> authors = scope.document().composition().getAuthor();
    if (authors.isEmpty()) {
      scope.missing("Composition.author", "The document author is required by CHMED23A");
      return null;
    }
    for (int i = 1; i < authors.size(); i++) {
      scope.unmapped("Composition.author[" + i + "]", "Further authors are not carried over");
    }
    Resource author = scope.document().resolve(authors.get(0));
    if (author instanceof Patient) {
      if (prescription) {
        scope.missing(
            "Composition.author",
            "A CHMED23A prescription must be authored by a healthcare person");
        return null;
      }
      return new Author(Medication.AUTH_PATIENT, null, null);
    }
    Practitioner practitioner = null;
    Organization organization = null;
    if (author instanceof PractitionerRole role) {
      practitioner = scope.document().resolve(role.getPractitioner(), Practitioner.class);
      organization = scope.document().resolve(role.getOrganization(), Organization.class);
    } else if (author instanceof Practitioner direct) {
      practitioner = direct;
    }
    if (practitioner == null) {
      scope.missing("Composition.author", "The author must be a patient or a practitioner");
      return null;
    }
    Optional<AuthorDetails> option = scope.options().author();
    HumanName name = practitioner.getNameFirstRep();
    String family = name.getFamily();
    String given = given(name);
    if (!present(family) && option.isPresent()) {
      family = option.get().family();
      given = option.get().given();
      scope.derived("Practitioner.name", "The practitioner name is taken from the option author");
    }
    if (!present(given) && option.isPresent() && present(option.get().given())) {
      given = option.get().given();
      scope.derived("Practitioner.name.given", "The first name is taken from the option author");
    }
    boolean ok = true;
    if (!present(family)) {
      scope.missing("Practitioner.name.family", "The practitioner family name is required");
      ok = false;
    }
    if (!present(given)) {
      scope.missing("Practitioner.name.given", "The practitioner first name is required");
      ok = false;
    }
    String gln = identifier(practitioner.getIdentifier(), Profiles.GLN);
    String personZsr = identifier(practitioner.getIdentifier(), Profiles.ZSR);

    String orgName = organization == null ? null : organization.getName();
    Address address =
        organization == null || !organization.hasAddress()
            ? null
            : organization.getAddressFirstRep();
    String street = address == null ? null : line(address);
    String zip = address == null ? null : address.getPostalCode();
    String city = address == null ? null : address.getCity();
    String country = address == null ? null : address.getCountry();
    if ((!present(orgName) || !present(street) || !present(zip) || !present(city))
        && option.isPresent()
        && option.get().organization() != null) {
      OrganizationDetails o = option.get().organization();
      orgName = o.name();
      street = o.street();
      zip = o.zip();
      city = o.city();
      country = o.country();
      scope.derived("PractitionerRole.organization", "The organization is taken from the option");
    }
    if (!present(orgName) || !present(street) || !present(zip) || !present(city)) {
      scope.missing(
          "PractitionerRole.organization",
          "Name and address of the organization are required by CHMED23A");
      ok = false;
    }
    if (!ok) {
      return null;
    }
    String orgGln =
        organization == null ? null : identifier(organization.getIdentifier(), Profiles.GLN);
    String orgZsr =
        organization == null ? null : identifier(organization.getIdentifier(), Profiles.ZSR);
    // fields with usage "-" for the document type may be set (README "Usage"); they are kept so
    // that nothing is dropped; the ZSR number may only be set once
    if (present(personZsr) && present(orgZsr)) {
      scope.unmapped(
          "Organization.identifier",
          "The ZSR number may only be set once; the one of the person is kept");
      orgZsr = null;
    }
    return new Author(
        Medication.AUTH_HEALTHCARE_PERSON,
        new HealthcarePerson(gln, given, family, personZsr),
        new HealthcareOrganization(
            orgGln,
            orgName,
            street,
            zip,
            city,
            present(country) ? country.toUpperCase(Locale.ROOT) : null,
            orgZsr));
  }

  /** Maps the patient (M23-REV-07..10); missing required data is reported. */
  @MappingRule({"M23-REV-07", "M23-REV-08", "M23-REV-09", "M23-REV-10"})
  public static ch.emed.converter.chmed23a.model.Patient patient(
      Patient source, String language, boolean plan, ReverseScope scope) {
    HumanName name = source.getNameFirstRep();
    String family = name.getFamily();
    String given = given(name);
    if (!present(family)) {
      scope.missing("Patient.name.family", "The patient family name is required by CHMED23A");
    }
    if (!present(given)) {
      scope.missing("Patient.name.given", "The patient first name is required by CHMED23A");
    }
    String birthDate =
        source.hasBirthDateElement() ? source.getBirthDateElement().getValueAsString() : null;
    if (!present(birthDate)) {
      scope.missing("Patient.birthDate", "The patient birth date is required by CHMED23A");
    }
    Integer gender = null;
    if (source.hasGender()) {
      switch (source.getGender()) {
        case MALE -> gender = ch.emed.converter.chmed23a.model.Patient.MALE;
        case FEMALE -> gender = ch.emed.converter.chmed23a.model.Patient.FEMALE;
        case OTHER -> gender = ch.emed.converter.chmed23a.model.Patient.OTHER;
        default -> {}
      }
    }
    if (gender == null) {
      scope.missing("Patient.gender", "A gender male, female or other is required by CHMED23A");
    }
    List<PatientId> ids = new ArrayList<>();
    for (Identifier identifier : source.getIdentifier()) {
      if (!present(identifier.getValue()) || !present(identifier.getSystem())) {
        continue;
      }
      if (Profiles.VEKA.equals(identifier.getSystem())) {
        ids.add(new PatientId(PatientId.INSURANCE_CARD, null, identifier.getValue()));
      } else {
        String system = identifier.getSystem();
        String sId = system.startsWith("urn:oid:") ? system.substring(8) : system;
        ids.add(new PatientId(PatientId.LOCAL_PID, sId, identifier.getValue()));
      }
    }
    if (ids.isEmpty()) {
      scope.missing("Patient.identifier", "At least one patient identifier is required");
    }
    Address address = source.hasAddress() ? source.getAddressFirstRep() : null;
    if (source.getAddress().size() > 1) {
      scope.unmapped("Patient.address", "Further addresses are not carried over");
    }
    List<String> phones = new ArrayList<>();
    List<String> emails = new ArrayList<>();
    for (ContactPoint cp : source.getTelecom()) {
      if (cp.getSystem() == ContactPoint.ContactPointSystem.PHONE && present(cp.getValue())) {
        phones.add(cp.getValue());
      } else if (cp.getSystem() == ContactPoint.ContactPointSystem.EMAIL
          && present(cp.getValue())) {
        emails.add(cp.getValue());
      } else {
        scope.unmapped(
            "Patient.telecom", "Contact points other than phone and e-mail are not carried over");
      }
    }
    return new ch.emed.converter.chmed23a.model.Patient(
        given,
        family,
        birthDate,
        gender,
        address == null ? null : line(address),
        address == null ? null : address.getPostalCode(),
        address == null ? null : address.getCity(),
        address == null || !present(address.getCountry())
            ? null
            : address.getCountry().toUpperCase(Locale.ROOT),
        language,
        ids,
        List.of(),
        null,
        phones,
        emails);
  }

  private static String given(HumanName name) {
    String given =
        name.getGiven().stream()
            .map(g -> g.getValue())
            .filter(ReverseScope::present)
            .collect(Collectors.joining(" "));
    return present(given) ? given : null;
  }

  private static String line(Address address) {
    String line =
        address.getLine().stream()
            .map(l -> l.getValue())
            .filter(ReverseScope::present)
            .collect(Collectors.joining(", "));
    return present(line) ? line : null;
  }

  static String identifier(List<Identifier> identifiers, String system) {
    for (Identifier identifier : identifiers) {
      if (system.equals(identifier.getSystem()) && present(identifier.getValue())) {
        return identifier.getValue();
      }
    }
    return null;
  }
}
