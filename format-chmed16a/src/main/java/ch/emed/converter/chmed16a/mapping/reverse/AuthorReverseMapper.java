package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.mapping.MappingRule;
import ch.emed.converter.chmed16a.mapping.Profiles;
import ch.emed.converter.chmed16a.model.HcOrg;
import ch.emed.converter.chmed16a.model.HcPerson;
import java.util.List;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/** Composition author → CHMED16A {@code Auth} and {@code Zsr} (mapping spec M-REV-04). */
public final class AuthorReverseMapper {

  private static final String PATH = "Composition.author";
  private static final int ZSR_LENGTH = 7;

  /**
   * Mapped author.
   *
   * @param auth GLN of the author or {@code patient}
   * @param zsr ZSR number of the author's organisation, or null
   */
  public record Author(String auth, String zsr) {}

  private AuthorReverseMapper() {}

  /**
   * Maps the first author of the Composition.
   *
   * @return the author, or null if no GLN is available (ERROR entry)
   */
  @MappingRule({"M-REV-04"})
  public static Author map(ReverseScope scope) {
    List<Reference> authors = scope.document().composition().getAuthor();
    if (authors.isEmpty()) {
      scope.missing(PATH, "The document author is required by CHMED16A");
      return null;
    }
    for (int i = 1; i < authors.size(); i++) {
      scope.unmapped(PATH + "[" + i + "]", "Further authors are not carried over");
    }
    Resource author = scope.document().resolve(authors.get(0));
    if (author instanceof Patient) {
      return new Author("patient", null);
    }
    Practitioner practitioner = null;
    Organization organization = null;
    if (author instanceof PractitionerRole role) {
      practitioner = scope.document().resolve(role.getPractitioner(), Practitioner.class);
      organization = scope.document().resolve(role.getOrganization(), Organization.class);
    } else if (author instanceof Practitioner direct) {
      practitioner = direct;
    }
    String gln =
        practitioner == null ? null : identifier(practitioner.getIdentifier(), Profiles.GLN);
    if (!present(gln)) {
      scope.missing(PATH, "The GLN of the document author is required by CHMED16A");
      return null;
    }
    return new Author(gln, organization == null ? null : zsr(organization, scope));
  }

  /**
   * The prescriber of a prescription.
   *
   * @param auth GLN of the prescriber
   * @param zsr root ZSR number (of the person, else of the organisation), or null
   * @param person {@code HcPerson}
   * @param org {@code HcOrg}
   */
  public record PrescriptionAuthor(String auth, String zsr, HcPerson person, HcOrg org) {}

  /**
   * Maps the author of a prescription (M-REV-31): the prescribing person with GLN and the
   * organisation with address.
   *
   * @return the author, or null if data is missing (ERROR entries)
   */
  @MappingRule({"M-REV-31"})
  public static PrescriptionAuthor mapPrescription(ReverseScope scope) {
    List<Reference> authors = scope.document().composition().getAuthor();
    if (authors.isEmpty()) {
      scope.erezept(PATH, "A prescription needs the prescribing person as author");
      return null;
    }
    for (int i = 1; i < authors.size(); i++) {
      scope.unmapped(PATH + "[" + i + "]", "Further authors are not carried over");
    }
    Resource author = scope.document().resolve(authors.get(0));
    if (author instanceof Patient) {
      scope.erezept(PATH, "The author of a prescription must be the prescribing person");
      return null;
    }
    Practitioner practitioner = null;
    Organization organization = null;
    if (author instanceof PractitionerRole role) {
      practitioner = scope.document().resolve(role.getPractitioner(), Practitioner.class);
      organization = scope.document().resolve(role.getOrganization(), Organization.class);
      if (organization == null) {
        scope.missing(
            "PractitionerRole.organization", "The organisation of the prescriber is required");
      }
    } else if (author instanceof Practitioner direct) {
      practitioner = direct;
      scope.missing(
          "PractitionerRole.organization", "The organisation of the prescriber is required");
    }
    String gln =
        practitioner == null ? null : identifier(practitioner.getIdentifier(), Profiles.GLN);
    if (!present(gln)) {
      scope.erezept(PATH, "The GLN of the prescribing person is required");
      return null;
    }
    boolean ok = organization != null;
    String given = given(practitioner);
    String family = practitioner.getNameFirstRep().getFamily();
    if (!present(family)) {
      scope.missing("Practitioner.name.family", "The prescriber family name is required");
      ok = false;
    }
    if (!present(given)) {
      scope.missing("Practitioner.name.given", "The prescriber first name is required");
      ok = false;
    }
    String personZsr = zsr(practitioner.getIdentifier(), "Practitioner", scope);
    HcOrg org = organization == null ? null : organization(organization, scope);
    if (org == null || !ok) {
      return null;
    }
    String orgZsr = org.zsr();
    if (personZsr != null && orgZsr != null) {
      scope.unmapped(
          "Organization.identifier",
          "The ZSR number may only be set once; the one of the person is kept and the one of the"
              + " organisation is not carried over");
      org =
          new HcOrg(
              org.name(),
              org.nameAffix(),
              org.street(),
              org.zip(),
              org.city(),
              org.country(),
              null,
              org.phone(),
              org.email());
    }
    HcPerson person = new HcPerson(gln, given, family, personZsr);
    return new PrescriptionAuthor(gln, personZsr != null ? personZsr : orgZsr, person, org);
  }

  /** {@code HcOrg}; null if required data is missing (ERROR entries). */
  private static HcOrg organization(Organization o, ReverseScope scope) {
    boolean ok = true;
    var address = o.hasAddress() ? o.getAddressFirstRep() : null;
    String street =
        address == null
            ? null
            : address.getLine().stream()
                .map(l -> l.getValue())
                .filter(l -> present(l))
                .collect(java.util.stream.Collectors.joining(", "));
    if (!present(o.getName())) {
      scope.missing("Organization.name", "The organisation name is required");
      ok = false;
    }
    if (!present(street)) {
      scope.missing("Organization.address.line", "The organisation street is required");
      ok = false;
    }
    if (address == null || !present(address.getPostalCode())) {
      scope.missing("Organization.address.postalCode", "The organisation postcode is required");
      ok = false;
    }
    if (address == null || !present(address.getCity())) {
      scope.missing("Organization.address.city", "The organisation city is required");
      ok = false;
    }
    for (int i = 0; i < o.getIdentifier().size(); i++) {
      if (Profiles.GLN.equals(o.getIdentifier().get(i).getSystem())) {
        scope.unmapped(
            "Organization.identifier[" + i + "]",
            "The GLN of the organisation has no CHMED16A counterpart and is not carried over");
      }
    }
    String phone = null;
    String email = null;
    var telecoms = o.getTelecom();
    for (int i = 0; i < telecoms.size(); i++) {
      var t = telecoms.get(i);
      if (t.getSystem() == org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem.PHONE
          && phone == null) {
        phone = t.getValue();
      } else if (t.getSystem() == org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem.EMAIL
          && email == null) {
        email = t.getValue();
      } else {
        scope.unmapped("Organization.telecom[" + i + "]", "This contact point is not carried over");
      }
    }
    if (o.getAlias().size() > 1) {
      scope.unmapped("Organization.alias[1]", "Further names are not carried over");
    }
    if (!ok) {
      return null;
    }
    return new HcOrg(
        o.getName(),
        o.hasAlias() ? o.getAlias().get(0).getValue() : null,
        street,
        address.getPostalCode(),
        address.getCity(),
        present(address.getCountry()) ? address.getCountry() : null,
        zsr(o.getIdentifier(), "Organization", scope),
        phone,
        email);
  }

  private static String given(Practitioner practitioner) {
    String given =
        practitioner.getNameFirstRep().getGiven().stream()
            .map(g -> g.getValue())
            .filter(g -> present(g))
            .collect(java.util.stream.Collectors.joining(" "));
    return present(given) ? given : null;
  }

  /** ZSR number of exactly 7 characters; other values are reported and not carried over. */
  private static String zsr(List<Identifier> identifiers, String resource, ReverseScope scope) {
    for (int i = 0; i < identifiers.size(); i++) {
      Identifier identifier = identifiers.get(i);
      if (!Profiles.ZSR.equals(identifier.getSystem())) {
        continue;
      }
      if (present(identifier.getValue()) && identifier.getValue().length() == ZSR_LENGTH) {
        return identifier.getValue();
      }
      scope.unmapped(
          resource + ".identifier[" + i + "]",
          "The ZSR number does not have the 7 characters required by CHMED16A; not carried over");
    }
    return null;
  }

  private static String zsr(Organization organization, ReverseScope scope) {
    List<Identifier> identifiers = organization.getIdentifier();
    for (int i = 0; i < identifiers.size(); i++) {
      Identifier identifier = identifiers.get(i);
      if (!Profiles.ZSR.equals(identifier.getSystem())) {
        continue;
      }
      if (present(identifier.getValue()) && identifier.getValue().length() == ZSR_LENGTH) {
        return identifier.getValue();
      }
      scope.unmapped(
          "Organization.identifier[" + i + "]",
          "The ZSR number does not have the 7 characters required by CHMED16A; not carried over");
    }
    return null;
  }

  private static String identifier(List<Identifier> identifiers, String system) {
    return identifiers.stream()
        .filter(i -> system.equals(i.getSystem()) && present(i.getValue()))
        .map(Identifier::getValue)
        .findFirst()
        .orElse(null);
  }
}
