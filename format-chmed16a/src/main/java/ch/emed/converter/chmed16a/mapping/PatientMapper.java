package ch.emed.converter.chmed16a.mapping;

import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.model.PatientId;
import ch.emed.converter.core.api.PatientIdentifier;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.util.Locale;
import java.util.Optional;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Patient;

/** CHMED16A Patient → CH Core Patient EPR (mapping spec M-PAT-*). */
public final class PatientMapper {

  private PatientMapper() {}

  @MappingRule({"M-PAT-01", "M-PAT-02", "M-PAT-03", "M-PAT-04", "M-PAT-05", "M-PAT-06"})
  public static Patient map(
      ch.emed.converter.chmed16a.model.Patient source, Medication med, MappingScope scope) {
    Patient patient = new Patient();
    patient.setId(scope.id("/Patient"));
    patient.getMeta().addProfile(Profiles.PATIENT);

    boolean hasId = false;
    for (PatientId id : source.ids()) {
      if (id.type() != null && id.type() == PatientId.TYPE_INSURANCE_CARD && present(id.val())) {
        patient.addIdentifier().setSystem(Profiles.VEKA).setValue(id.val());
        hasId = true;
      }
    }
    Optional<PatientIdentifier> option = scope.options().patientIdentifier();
    if (!hasId && option.isPresent()) {
      patient.addIdentifier().setSystem(option.get().system()).setValue(option.get().value());
      hasId = true;
    }
    if (!hasId) {
      missing(
          scope,
          "/Patient/Ids",
          "A patient identifier is required by CH Core; supply one as option");
    }

    if (present(source.lName())) {
      var name = patient.addName().setFamily(source.lName());
      if (present(source.fName())) {
        name.addGiven(source.fName());
      }
    } else {
      missing(scope, "/Patient/LName", "The patient family name is required by CH Core");
    }

    if (source.gender() == null) {
      missing(scope, "/Patient/Gender", "The patient gender is required by CH Core");
    } else {
      patient.setGender(
          source.gender() == ch.emed.converter.chmed16a.model.Patient.GENDER_MALE
              ? AdministrativeGender.MALE
              : AdministrativeGender.FEMALE);
    }

    if (present(source.bDt())) {
      patient.setBirthDateElement(new DateType(source.bDt()));
    } else {
      missing(scope, "/Patient/BDt", "The patient birth date is required by CH Core");
    }

    if (present(source.phone())) {
      patient
          .addTelecom()
          .setSystem(ContactPoint.ContactPointSystem.PHONE)
          .setValue(source.phone());
    }
    if (present(source.email())) {
      patient
          .addTelecom()
          .setSystem(ContactPoint.ContactPointSystem.EMAIL)
          .setValue(source.email());
    }
    if (present(source.street()) || present(source.zip()) || present(source.city())) {
      Address address = patient.addAddress();
      if (present(source.street())) {
        address.addLine(source.street());
      }
      if (present(source.city())) {
        address.setCity(source.city());
      }
      if (present(source.zip())) {
        address.setPostalCode(source.zip());
      }
    }
    if (present(source.lng())) {
      patient
          .addCommunication()
          .setLanguage(
              new CodeableConcept(
                  new Coding()
                      .setSystem(Profiles.BCP47)
                      .setCode(source.lng().toLowerCase(Locale.ROOT))))
          .setPreferred(true);
    }
    return patient;
  }

  private static void missing(MappingScope scope, String path, String message) {
    scope
        .report()
        .add(ReportEntry.error(ReasonCode.MISSING_REQUIRED_DATA, Phase.MAP, path, message));
  }

  public static boolean present(String value) {
    return value != null && !value.isBlank();
  }
}
