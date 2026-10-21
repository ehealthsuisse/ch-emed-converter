package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.mapping.MappingRule;
import ch.emed.converter.chmed16a.mapping.Profiles;
import ch.emed.converter.chmed16a.model.PatientId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.HumanName.NameUse;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Patient.PatientCommunicationComponent;

/** CH Core Patient → CHMED16A Patient (mapping spec M-REV-06..M-REV-09). */
public final class PatientReverseMapper {

  private static final String PATH = "Patient";

  private PatientReverseMapper() {}

  /**
   * Maps the patient. Required data that is missing is reported as {@code MISSING_REQUIRED_DATA};
   * the returned model is then incomplete and must not be written.
   */
  @MappingRule({"M-REV-06", "M-REV-07", "M-REV-08", "M-REV-09"})
  public static ch.emed.converter.chmed16a.model.Patient map(Patient source, ReverseScope scope) {
    return map(source, scope, false);
  }

  /**
   * Maps the patient of a prescription ({@code prescription} true): the first name is required and
   * there is no patient language.
   */
  public static ch.emed.converter.chmed16a.model.Patient map(
      Patient source, ReverseScope scope, boolean prescription) {
    HumanName name = name(source, scope);
    String family = name == null ? null : name.getFamily();
    if (!present(family)) {
      scope.missing(PATH + ".name.family", "The patient family name is required by CHMED16A");
    }
    String given =
        name == null
            ? null
            : name.getGiven().stream()
                .map(g -> g.getValue())
                .filter(g -> present(g))
                .collect(Collectors.joining(" "));
    if (prescription && !present(given)) {
      scope.missing(PATH + ".name.given", "The patient first name is required by CHMED16A");
    }
    if (name != null && name.getGiven().size() > 1) {
      scope.derived(PATH + ".name", "Several given names are joined by a space");
    }

    String birthDate =
        source.hasBirthDateElement() ? source.getBirthDateElement().getValueAsString() : null;
    if (!present(birthDate)) {
      scope.missing(PATH + ".birthDate", "The patient birth date is required by CHMED16A");
    }

    Integer gender = null;
    if (source.hasGender()) {
      switch (source.getGender()) {
        case MALE -> gender = ch.emed.converter.chmed16a.model.Patient.GENDER_MALE;
        case FEMALE -> gender = ch.emed.converter.chmed16a.model.Patient.GENDER_FEMALE;
        default ->
            scope.unmappedWarning(
                PATH + ".gender",
                "CHMED16A knows only male and female; the gender is not carried over",
                false);
      }
    }

    String street = null;
    String zip = null;
    String city = null;
    if (source.hasAddress()) {
      Address address = source.getAddressFirstRep();
      street =
          address.getLine().stream()
              .map(l -> l.getValue())
              .filter(l -> present(l))
              .collect(Collectors.joining(", "));
      street = present(street) ? street : null;
      zip = address.getPostalCode();
      city = address.getCity();
      if (present(address.getCountry()) && !"CH".equalsIgnoreCase(address.getCountry())) {
        scope.unmapped(PATH + ".address[0].country", "The country is not carried over");
      }
      for (int i = 1; i < source.getAddress().size(); i++) {
        scope.unmapped(PATH + ".address[" + i + "]", "Further addresses are not carried over");
      }
    }

    String phone = null;
    String email = null;
    List<ContactPoint> telecoms = source.getTelecom();
    for (int i = 0; i < telecoms.size(); i++) {
      ContactPoint telecom = telecoms.get(i);
      if (telecom.getSystem() == ContactPointSystem.PHONE && phone == null) {
        phone = telecom.getValue();
      } else if (telecom.getSystem() == ContactPointSystem.EMAIL && email == null) {
        email = telecom.getValue();
      } else {
        scope.unmapped(PATH + ".telecom[" + i + "]", "This contact point is not carried over");
      }
    }

    List<PatientId> ids = new ArrayList<>();
    List<Identifier> identifiers = source.getIdentifier();
    for (int i = 0; i < identifiers.size(); i++) {
      Identifier identifier = identifiers.get(i);
      if (Profiles.VEKA.equals(identifier.getSystem()) && present(identifier.getValue())) {
        ids.add(new PatientId(PatientId.TYPE_INSURANCE_CARD, identifier.getValue()));
      } else {
        scope.unmappedWarning(
            PATH + ".identifier[" + i + "]",
            "An identifier of another system cannot be carried over",
            false);
      }
    }

    String lng = prescription ? null : language(source, scope);

    return new ch.emed.converter.chmed16a.model.Patient(
        present(given) ? given : null,
        family,
        birthDate,
        gender,
        street,
        zip,
        city,
        lng,
        phone,
        email,
        null,
        ids,
        null,
        List.of());
  }

  private static HumanName name(Patient source, ReverseScope scope) {
    List<HumanName> names = source.getName();
    if (names.isEmpty()) {
      return null;
    }
    int chosen = indexOfUse(names, NameUse.OFFICIAL);
    if (chosen < 0) {
      chosen = indexOfUse(names, NameUse.USUAL);
    }
    if (chosen < 0) {
      chosen = 0;
    }
    for (int i = 0; i < names.size(); i++) {
      if (i != chosen) {
        scope.unmapped(PATH + ".name[" + i + "]", "Further names are not carried over");
      }
    }
    return names.get(chosen);
  }

  private static int indexOfUse(List<HumanName> names, NameUse use) {
    for (int i = 0; i < names.size(); i++) {
      if (names.get(i).getUse() == use) {
        return i;
      }
    }
    return -1;
  }

  /** Preferred communication language, else the first; falls back to the document language. */
  private static String language(Patient source, ReverseScope scope) {
    PatientCommunicationComponent chosen = null;
    for (PatientCommunicationComponent c : source.getCommunication()) {
      if (chosen == null || (c.getPreferred() && !chosen.getPreferred())) {
        chosen = c;
      }
    }
    String code = chosen == null ? null : chosen.getLanguage().getCodingFirstRep().getCode();
    if (present(code)) {
      return primarySubtag(code);
    }
    String documentLanguage = scope.document().composition().getLanguage();
    if (present(documentLanguage)) {
      scope.derived(
          PATH + ".communication",
          "The patient language is derived from the document language (required by CHMED16A)");
      return primarySubtag(documentLanguage);
    }
    scope.missing(PATH + ".communication", "The patient language is required by CHMED16A");
    return null;
  }

  private static String primarySubtag(String code) {
    int dash = code.indexOf('-');
    return (dash < 0 ? code : code.substring(0, dash)).toLowerCase(Locale.ROOT);
  }
}
