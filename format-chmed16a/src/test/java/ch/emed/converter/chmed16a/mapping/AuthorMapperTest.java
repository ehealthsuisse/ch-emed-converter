package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.read;
import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.Severity;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

class AuthorMapperTest {

  private static final Reference PATIENT = new Reference("urn:uuid:patient");

  @Test
  void authGlnWithAuthorOptionBuildsPractitionerRole() {
    Medication med = read("plan-basic.json");
    MappingScope scope = scope(med, TestOptions.withAuthor());

    AuthorMapper.Author author = AuthorMapper.map(med, PATIENT, scope);

    assertThat(author.reference().getReference())
        .isEqualTo("urn:uuid:" + scope.id("/Author/PractitionerRole"));
    PractitionerRole role = (PractitionerRole) author.resources().get(0);
    Practitioner practitioner = (Practitioner) author.resources().get(1);
    Organization organization = (Organization) author.resources().get(2);
    assertThat(role.getPractitioner().getReference())
        .isEqualTo("urn:uuid:" + practitioner.getIdPart());
    assertThat(role.getOrganization().getReference())
        .isEqualTo("urn:uuid:" + organization.getIdPart());
    assertThat(practitioner.getIdentifierFirstRep().getSystem()).isEqualTo("urn:oid:2.51.1.3");
    assertThat(practitioner.getIdentifierFirstRep().getValue()).isEqualTo("7601000000001");
    assertThat(practitioner.getNameFirstRep().getFamily()).isEqualTo("Test");
    assertThat(organization.getName()).isEqualTo("Praxis Test");
    assertThat(organization.getAddressFirstRep().getCity()).isEqualTo("Bern");
    assertThat(organization.getAddressFirstRep().getCountry()).isEqualTo("CH");
    assertThat(scope.report().hasErrors()).isFalse();
  }

  @Test
  void withoutAuthorOptionIsMissingRequiredData() {
    Medication med = read("plan-basic.json");
    MappingScope scope = scope(med, ConversionOptions.defaults());

    assertThat(AuthorMapper.map(med, PATIENT, scope)).isNull();
    assertThat(scope.report().entries())
        .anyMatch(
            e ->
                e.severity() == Severity.ERROR
                    && e.code().equals(ReasonCode.MISSING_REQUIRED_DATA)
                    && e.path().equals("/Auth"));
  }

  @Test
  void patientAsAuthor() {
    Medication med =
        MappingTestSupport.parse(
            """
            {"Id":"0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e01","MedType":1,"Auth":"Patient",
             "Dt":"2026-10-01T09:30:00+02:00",
             "Patient":{"FName":"Hans","LName":"Muster","BDt":"1950-03-21","Gender":1,"Lng":"de"}}
            """);
    MappingScope scope = scope(med, ConversionOptions.defaults());

    AuthorMapper.Author author = AuthorMapper.map(med, PATIENT, scope);

    assertThat(author.reference().getReference()).isEqualTo("urn:uuid:patient");
    assertThat(author.resources()).isEmpty();
    assertThat(scope.report().hasErrors()).isFalse();
  }

  @Test
  void authorNameWithoutGlnNeedsGlnFromOption() {
    Medication med =
        MappingTestSupport.parse(
            """
            {"Id":"0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e01","MedType":1,"Auth":"Dr. Test",
             "Dt":"2026-10-01T09:30:00+02:00",
             "Patient":{"FName":"Hans","LName":"Muster","BDt":"1950-03-21","Gender":1,"Lng":"de"}}
            """);
    MappingScope scope = scope(med, TestOptions.withAuthor());

    assertThat(AuthorMapper.map(med, PATIENT, scope)).isNull();
    assertThat(scope.report().entries())
        .anyMatch(e -> e.code().equals(ReasonCode.MISSING_REQUIRED_DATA));
  }
}
