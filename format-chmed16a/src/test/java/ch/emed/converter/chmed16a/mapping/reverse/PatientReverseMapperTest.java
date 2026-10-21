package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.CARD;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.document;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.entries;
import static ch.emed.converter.chmed16a.mapping.reverse.ReverseTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.io.ChEmedDocumentReader.ChEmedDocument;
import ch.emed.converter.chmed16a.model.PatientId;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.Severity;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class PatientReverseMapperTest {

  private final ChEmedDocument doc = document(CARD);
  private final ReverseScope scope = scope(doc);

  private Patient officialPatient() {
    return doc.resolve(doc.composition().getSubject(), Patient.class).copy();
  }

  private ch.emed.converter.chmed16a.model.Patient map(Patient patient) {
    return PatientReverseMapper.map(patient, scope);
  }

  @Test
  void mapsDemographicsOfTheOfficialExample() {
    var patient = map(officialPatient());

    assertThat(patient.fName()).isEqualTo("Monika");
    assertThat(patient.lName()).isEqualTo("Wegmüller");
    assertThat(patient.bDt()).isEqualTo("1943-05-15");
    assertThat(patient.gender()).isEqualTo(2);
    assertThat(patient.street()).isEqualTo("Wiesenstr. 12");
    assertThat(patient.zip()).isEqualTo("8003");
    assertThat(patient.city()).isEqualTo("Zürich");
  }

  @Test
  void languageIsDerivedFromTheCompositionWhenThePatientHasNone() {
    var patient = map(officialPatient());

    assertThat(patient.lng()).isEqualTo("de");
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.DERIVED_VALUE)))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.INFORMATION);
              assertThat(e.path()).isEqualTo("Patient.communication");
            });
  }

  @Test
  void preferredCommunicationLanguageWinsAndIsNotReportedAsDerived() {
    Patient source = officialPatient();
    source.addCommunication().getLanguage().addCoding().setSystem("urn:ietf:bcp:47").setCode("it");
    source
        .addCommunication()
        .setPreferred(true)
        .getLanguage()
        .addCoding()
        .setSystem("urn:ietf:bcp:47")
        .setCode("fr-CH");

    assertThat(map(source).lng()).isEqualTo("fr");
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.DERIVED_VALUE))).isEmpty();
  }

  @Test
  void insuranceCardIdentifierBecomesIdsOtherIdentifiersAreReported() {
    Patient source = officialPatient();
    source
        .addIdentifier()
        .setSystem("urn:oid:2.16.756.5.30.1.123.100.1.1.1")
        .setValue("80756000000000000001");

    var patient = map(source);

    assertThat(patient.ids()).singleElement().isEqualTo(new PatientId(1, "80756000000000000001"));
    assertThat(entries(scope, e -> e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT)))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.clinicalLoss()).isFalse();
              assertThat(e.path()).isEqualTo("Patient.identifier[0]");
              assertThat(e.message()).doesNotContain("11111111");
            });
  }

  @Test
  void severalGivenNamesAreJoinedAndFurtherNamesReported() {
    Patient source = officialPatient();
    source.getNameFirstRep().addGiven("Maria");
    source.addName().setFamily("Meier").addGiven("Moni");

    var patient = map(source);

    assertThat(patient.fName()).isEqualTo("Monika Maria");
    assertThat(entries(scope, e -> e.path().equals("Patient.name[1]"))).hasSize(1);
  }

  @Test
  void officialOrUsualNameIsPreferred() {
    Patient source = officialPatient();
    source.getNameFirstRep().setUse(org.hl7.fhir.r4.model.HumanName.NameUse.NICKNAME);
    source
        .addName()
        .setUse(org.hl7.fhir.r4.model.HumanName.NameUse.OFFICIAL)
        .setFamily("Amtlich")
        .addGiven("Anna");

    var patient = map(source);

    assertThat(patient.lName()).isEqualTo("Amtlich");
    assertThat(patient.fName()).isEqualTo("Anna");
  }

  @Test
  void unknownGenderIsOmittedAndReportedWithoutClinicalLoss() {
    Patient source = officialPatient();
    source.setGender(AdministrativeGender.OTHER);

    var patient = map(source);

    assertThat(patient.gender()).isNull();
    assertThat(entries(scope, e -> e.path().equals("Patient.gender")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
              assertThat(e.clinicalLoss()).isFalse();
            });
  }

  @Test
  void missingBirthDateOrFamilyNameIsMissingRequiredData() {
    Patient source = officialPatient();
    source.setBirthDate(null);
    source.getNameFirstRep().setFamily(null);

    map(source);

    assertThat(entries(scope, e -> e.severity() == Severity.ERROR))
        .extracting(e -> e.path())
        .containsExactlyInAnyOrder("Patient.birthDate", "Patient.name.family");
    assertThat(entries(scope, e -> e.severity() == Severity.ERROR))
        .allSatisfy(e -> assertThat(e.code()).isEqualTo(ReasonCode.MISSING_REQUIRED_DATA));
  }

  @Test
  void addressLinesAreJoinedAndTelecomsMapped() {
    Patient source = officialPatient();
    source.getAddressFirstRep().addLine("c/o Muster");
    source
        .addTelecom()
        .setSystem(ContactPoint.ContactPointSystem.PHONE)
        .setValue("+41 44 000 00 00");
    source.addTelecom().setSystem(ContactPoint.ContactPointSystem.EMAIL).setValue("m@example.org");
    source
        .addTelecom()
        .setSystem(ContactPoint.ContactPointSystem.PHONE)
        .setValue("+41 79 000 00 00");

    var patient = map(source);

    assertThat(patient.street()).isEqualTo("Wiesenstr. 12, c/o Muster");
    assertThat(patient.phone()).isEqualTo("+41 44 000 00 00");
    assertThat(patient.email()).isEqualTo("m@example.org");
    assertThat(entries(scope, e -> e.path().equals("Patient.telecom[2]"))).hasSize(1);
  }
}
