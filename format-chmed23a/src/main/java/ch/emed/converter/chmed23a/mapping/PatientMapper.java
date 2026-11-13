package ch.emed.converter.chmed23a.mapping;

import static ch.emed.converter.chmed23a.mapping.MappingScope.present;

import ch.emed.converter.chmed23a.model.Extension;
import ch.emed.converter.chmed23a.model.MedicalData;
import ch.emed.converter.chmed23a.model.PatientId;
import ch.emed.converter.core.api.PatientIdentifier;
import ch.emed.converter.core.chemed.Profiles;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Patient;

/**
 * CHMED23A Patient → CH Core Patient EPR (mapping spec M23-PAT-*), medical data and extensions
 * (M23-MDT-01, M23-EXT-01).
 */
public final class PatientMapper {

  private static final Pattern DOTTED_NUMBER = Pattern.compile("\\d+(\\.\\d+)+");

  /** OID as FHIR requires it (type oid): first arc 0-2, no leading zeros. */
  private static final Pattern OID = Pattern.compile("[0-2](\\.(0|[1-9][0-9]*))+");

  /** Absolute URI: a scheme followed by a colon and no whitespace. */
  private static final Pattern URI = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*:\\S+");

  private PatientMapper() {}

  @MappingRule({
    "M23-PAT-01",
    "M23-PAT-02",
    "M23-PAT-03",
    "M23-PAT-04",
    "M23-PAT-05",
    "M23-PAT-06",
    "M23-PAT-07",
    "M23-PAT-08"
  })
  public static Patient map(ch.emed.converter.chmed23a.model.Patient source, MappingScope scope) {
    Patient patient = new Patient();
    patient.setId(scope.id("/patient"));
    patient.getMeta().addProfile(Profiles.PATIENT);

    boolean hasId = false;
    for (PatientId id : source.ids()) {
      if (id.type() == null || !present(id.val())) {
        continue;
      }
      if (id.type() == PatientId.INSURANCE_CARD) {
        patient.addIdentifier().setSystem(Profiles.VEKA).setValue(id.val());
        hasId = true;
      } else if (id.type() == PatientId.LOCAL_PID && present(id.sId())) {
        String system = system(id.sId());
        if (system == null) {
          scope
              .report()
              .add(
                  ReportEntry.warning(
                          ReasonCode.UNMAPPABLE_ELEMENT,
                          Phase.MAP,
                          "/patient/ids/" + source.ids().indexOf(id) + "/sId",
                          "The system identifier is neither a valid OID nor a URI; the patient id"
                              + " is not carried over")
                      .withTargetPath("Patient.identifier.system"));
          continue;
        }
        patient.addIdentifier().setSystem(system).setValue(id.val());
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
          "/patient/ids",
          "A patient identifier is required by CH Core; supply one as option");
    }

    var name = patient.addName().setFamily(source.lName());
    if (present(source.fName())) {
      name.addGiven(source.fName());
    }
    if (source.gender() != null) {
      patient.setGender(
          switch (source.gender()) {
            case ch.emed.converter.chmed23a.model.Patient.MALE -> AdministrativeGender.MALE;
            case ch.emed.converter.chmed23a.model.Patient.FEMALE -> AdministrativeGender.FEMALE;
            default -> AdministrativeGender.OTHER;
          });
    }
    if (present(source.bdt())) {
      patient.setBirthDateElement(new DateType(source.bdt()));
    }
    for (String phone : source.phones()) {
      patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue(phone);
    }
    for (String email : source.emails()) {
      patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.EMAIL).setValue(email);
    }
    if (present(source.street())
        || present(source.zip())
        || present(source.city())
        || present(source.country())) {
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
      if (present(source.country())) {
        address.setCountry(source.country().toUpperCase(Locale.ROOT));
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
    medicalData(source.mData(), scope);
    extensions(source.exts(), "/patient/exts", scope);
    return patient;
  }

  /**
   * {@code sId} as identifier system: a valid OID becomes {@code urn:oid:}, an absolute URI is
   * kept; anything else, e.g. a dotted number that is not a valid OID (finding F7), gives null and
   * is not repaired.
   */
  static String system(String sId) {
    String trimmed = sId.trim();
    if (DOTTED_NUMBER.matcher(trimmed).matches()) {
      return OID.matcher(trimmed).matches() ? "urn:oid:" + trimmed : null;
    }
    if (trimmed.regionMatches(true, 0, "urn:oid:", 0, 8)) {
      return OID.matcher(trimmed.substring(8)).matches() ? trimmed : null;
    }
    return URI.matcher(trimmed).matches() ? trimmed : null;
  }

  /** The Medication Card has no place for medical data (M23-MDT-01). */
  @MappingRule("M23-MDT-01")
  static void medicalData(MedicalData data, MappingScope scope) {
    if (data == null) {
      return;
    }
    String path = "/patient/mData";
    lost(scope, data.w() != null, path + "/w", "Weight");
    lost(scope, data.h() != null, path + "/h", "Height");
    lost(scope, !data.rcs().isEmpty(), path + "/rcs", "Risk categories");
    lost(scope, data.dLstMen() != null, path + "/dLstMen", "First day of last menstruation");
    lost(scope, data.prem() != null, path + "/prem", "Premature birth");
    lost(scope, data.toG() != null, path + "/toG", "Time of gestation");
    extensions(data.exts(), path + "/exts", scope);
  }

  /** Extensions have no CH EMED counterpart; reported by path only (M23-EXT-01). */
  @MappingRule("M23-EXT-01")
  public static void extensions(List<Extension> exts, String path, MappingScope scope) {
    for (int i = 0; i < exts.size(); i++) {
      scope
          .report()
          .add(
              ReportEntry.warning(
                  ReasonCode.UNMAPPABLE_ELEMENT,
                  Phase.MAP,
                  path + "/" + i,
                  "Extension has no CH EMED counterpart and is not carried over"));
    }
  }

  private static void lost(MappingScope scope, boolean present, String path, String what) {
    if (present) {
      scope
          .report()
          .add(
              ReportEntry.warning(
                      ReasonCode.UNMAPPABLE_ELEMENT,
                      Phase.MAP,
                      path,
                      what + " has no place in the CH EMED document and is not carried over")
                  .withClinicalLoss(true));
    }
  }

  static void missing(MappingScope scope, String path, String message) {
    scope
        .report()
        .add(ReportEntry.error(ReasonCode.MISSING_REQUIRED_DATA, Phase.MAP, path, message));
  }
}
