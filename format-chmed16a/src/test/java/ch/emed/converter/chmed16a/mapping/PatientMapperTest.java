package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.read;
import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.Severity;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class PatientMapperTest {

  @Test
  void mapsDemographicsContactAndInsuranceCardNumber() {
    Medication med = read("plan-basic.json");
    MappingScope scope = scope(med, ConversionOptions.defaults());

    Patient patient = PatientMapper.map(med.patient(), med, scope);

    assertThat(patient.getIdPart()).isEqualTo(scope.id("/Patient"));
    assertThat(patient.getNameFirstRep().getFamily()).isEqualTo("Muster");
    assertThat(patient.getNameFirstRep().getGivenAsSingleString()).isEqualTo("Hans");
    assertThat(patient.getGender()).isEqualTo(AdministrativeGender.MALE);
    assertThat(patient.getBirthDateElement().getValueAsString()).isEqualTo("1950-03-21");
    assertThat(patient.getAddressFirstRep().getLine().get(0).getValue()).isEqualTo("Testweg 1");
    assertThat(patient.getAddressFirstRep().getPostalCode()).isEqualTo("3000");
    assertThat(patient.getAddressFirstRep().getCity()).isEqualTo("Bern");
    assertThat(patient.getAddressFirstRep().hasCountry()).isFalse();
    assertThat(patient.getTelecomFirstRep().getValue()).isEqualTo("+41 31 000 00 00");
    assertThat(patient.getIdentifierFirstRep().getSystem())
        .isEqualTo("urn:oid:2.16.756.5.30.1.123.100.1.1.1");
    assertThat(patient.getIdentifierFirstRep().getValue()).isEqualTo("80756000000000000001");
    assertThat(patient.getCommunicationFirstRep().getLanguage().getCodingFirstRep().getCode())
        .isEqualTo("de");
    assertThat(scope.report().hasErrors()).isFalse();
  }

  @Test
  void femaleGender() {
    Medication med = read("plan-medical-data.json");
    assertThat(
            PatientMapper.map(med.patient(), med, scope(med, ConversionOptions.defaults()))
                .getGender())
        .isEqualTo(AdministrativeGender.FEMALE);
  }

  @Test
  void missingBirthDateIsMissingRequiredDataWithoutPlaceholder() {
    Medication med = read("plan-missing-birthdate.json");
    MappingScope scope = scope(med, ConversionOptions.defaults());

    Patient patient = PatientMapper.map(med.patient(), med, scope);

    assertThat(patient.hasBirthDate()).isFalse();
    assertThat(scope.report().entries())
        .anyMatch(
            e ->
                e.severity() == Severity.ERROR
                    && e.code().equals(ReasonCode.MISSING_REQUIRED_DATA)
                    && e.path().equals("/Patient/BDt"));
  }

  @Test
  void missingGenderIsMissingRequiredData() {
    Medication med =
        MappingTestSupport.parse(
            """
            {"Id":"0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e01","MedType":1,"Auth":"7601000000001",
             "Dt":"2026-10-01T09:30:00+02:00",
             "Patient":{"FName":"Hans","LName":"Muster","BDt":"1950-03-21","Lng":"de",
                        "Ids":[{"Type":1,"Val":"1"}]}}
            """);
    MappingScope scope = scope(med, ConversionOptions.defaults());

    PatientMapper.map(med.patient(), med, scope);

    assertThat(scope.report().entries())
        .anyMatch(
            e ->
                e.code().equals(ReasonCode.MISSING_REQUIRED_DATA)
                    && e.path().equals("/Patient/Gender"));
  }

  @Test
  void withoutIdsTheCallerIdentifierIsUsed() {
    Medication med = read("official/readme-example-medication-plan.json");
    MappingScope scope = scope(med, TestOptions.withAuthorAndPatientId());

    Patient patient = PatientMapper.map(med.patient(), med, scope);

    assertThat(patient.getIdentifierFirstRep().getSystem()).isEqualTo("urn:oid:2.999.1");
    assertThat(patient.getIdentifierFirstRep().getValue()).isEqualTo("TEST-0001");
    assertThat(scope.report().hasErrors()).isFalse();
  }

  @Test
  void withoutIdsAndWithoutOptionIsMissingRequiredData() {
    Medication med = read("official/readme-example-medication-plan.json");
    MappingScope scope = scope(med, ConversionOptions.defaults());

    PatientMapper.map(med.patient(), med, scope);

    assertThat(scope.report().entries())
        .anyMatch(
            e ->
                e.severity() == Severity.ERROR
                    && e.code().equals(ReasonCode.MISSING_REQUIRED_DATA)
                    && e.path().equals("/Patient/Ids"));
  }
}
