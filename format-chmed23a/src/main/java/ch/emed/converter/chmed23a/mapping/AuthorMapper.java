package ch.emed.converter.chmed23a.mapping;

import static ch.emed.converter.chmed23a.mapping.MappingScope.present;

import ch.emed.converter.chmed23a.model.HealthcareOrganization;
import ch.emed.converter.chmed23a.model.HealthcarePerson;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.core.api.AuthorDetails;
import ch.emed.converter.core.chemed.Narratives;
import ch.emed.converter.core.chemed.Profiles;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/** CHMED23A author → CH EMED author (mapping spec M23-AUT-*, research R8). */
public final class AuthorMapper {

  /**
   * Mapped author.
   *
   * @param reference reference to use as Composition.author, informationSource and requester
   * @param resources resources to add to the Bundle (PractitionerRole, Practitioner, Organization)
   */
  public record Author(Reference reference, List<Resource> resources) {}

  private AuthorMapper() {}

  /**
   * Maps the author of the document.
   *
   * @return the author, or null if required data is missing (ERROR entries)
   */
  @MappingRule({"M23-AUT-01", "M23-AUT-02", "M23-AUT-03"})
  public static Author map(Medication med, Reference patient, MappingScope scope) {
    Optional<AuthorDetails> option = scope.options().author();
    if (med.auth() != null && med.auth() == Medication.AUTH_PATIENT) {
      return new Author(new Reference(patient.getReference()), List.of());
    }
    HealthcarePerson person = med.hcPerson();
    HealthcareOrganization org = med.hcOrg();
    if (person == null || org == null) {
      return null; // reported by the validator (C23-OBJ-01)
    }
    String gln = person.gln();
    if (!present(gln) && option.isPresent() && present(option.get().gln())) {
      gln = option.get().gln();
      scope
          .report()
          .add(
              ReportEntry.information(
                      ReasonCode.DERIVED_VALUE,
                      Phase.MAP,
                      "/hcPerson/gln",
                      "Practitioner GLN taken from the option author")
                  .withTargetPath("Practitioner.identifier"));
    } else if (option.isPresent()) {
      scope
          .report()
          .add(
              ReportEntry.information(
                  ReasonCode.DERIVED_VALUE,
                  Phase.MAP,
                  "/hcPerson",
                  "Author taken from the document; the option author is not used"));
    }
    if (!present(gln)) {
      scope
          .report()
          .add(
              ReportEntry.error(
                  ReasonCode.MISSING_REQUIRED_DATA,
                  Phase.MAP,
                  "/hcPerson/gln",
                  "A practitioner GLN is required by CH Core; supply it with the option author"));
      return null;
    }

    Practitioner practitioner = new Practitioner();
    practitioner.setId(scope.id("/hcPerson"));
    practitioner.getMeta().addProfile(Profiles.PRACTITIONER);
    practitioner.addIdentifier().setSystem(Profiles.GLN).setValue(gln);
    if (present(person.zsr())) {
      practitioner.addIdentifier().setSystem(Profiles.ZSR).setValue(person.zsr());
    }
    var name = practitioner.addName().setFamily(person.lName());
    if (present(person.fName())) {
      name.addGiven(person.fName());
    }

    Organization organization = new Organization();
    organization.setId(scope.id("/hcOrg"));
    organization.getMeta().addProfile(Profiles.ORGANIZATION);
    if (present(org.gln())) {
      organization.addIdentifier().setSystem(Profiles.GLN).setValue(org.gln());
    }
    if (present(org.zsr())) {
      organization.addIdentifier().setSystem(Profiles.ZSR).setValue(org.zsr());
    }
    organization.setName(org.name());
    Address address = organization.addAddress().addLine(org.street());
    address.setCity(org.city()).setPostalCode(org.zip());
    if (present(org.country())) {
      address.setCountry(org.country().toUpperCase(Locale.ROOT));
    }

    PractitionerRole role = new PractitionerRole();
    role.setId(scope.id("/auth"));
    role.getMeta().addProfile(Profiles.PRACTITIONER_ROLE);
    role.setPractitioner(new Reference(scope.ref("/hcPerson")));
    role.setOrganization(new Reference(scope.ref("/hcOrg")));

    Narratives.practitioner(practitioner);
    Narratives.organization(organization);
    Narratives.practitionerRole(role, practitioner, organization);
    return new Author(new Reference(scope.ref("/auth")), List.of(role, practitioner, organization));
  }
}
