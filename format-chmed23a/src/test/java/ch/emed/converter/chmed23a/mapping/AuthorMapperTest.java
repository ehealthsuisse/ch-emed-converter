package ch.emed.converter.chmed23a.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed23a.model.HealthcareOrganization;
import ch.emed.converter.chmed23a.model.HealthcarePerson;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.core.api.AuthorDetails;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.OrganizationDetails;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.spi.MappingContext;
import java.util.List;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

/** T027: author rules M23-AUT-* (research R8). */
class AuthorMapperTest {

  private static final AuthorDetails OPTION =
      new AuthorDetails(
          "7601000000002",
          "Thea",
          "Test",
          new OrganizationDetails("Praxis Test", "Testgasse 1", "3000", "Bern", "CH"));

  private final ReportCollector report = new ReportCollector();

  private AuthorMapper.Author map(Medication med, ConversionOptions options) {
    return AuthorMapper.map(
        med,
        new Reference("urn:uuid:patient"),
        new MappingScope(
            "0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e23", new MappingContext(options, report)));
  }

  private static Medication med(int auth, HealthcarePerson person) {
    return new Medication(
        null,
        person,
        new HealthcareOrganization("7601001362383", "Praxis", "Weg 1", "3000", "Bern", "ch", "Z1"),
        List.of(),
        List.of(),
        1,
        null,
        auth,
        null,
        "2024-01-09T09:14:36+01:00",
        null);
  }

  @Test
  void healthcarePersonFromTheDocument() {
    AuthorMapper.Author author =
        map(
            med(1, new HealthcarePerson("7601000000001", "Hans", "Muster", "P1")),
            ConversionOptions.defaults());
    Practitioner p = (Practitioner) author.resources().get(1);
    Organization o = (Organization) author.resources().get(2);
    assertThat(p.getIdentifier())
        .extracting(i -> i.getValue())
        .containsExactly("7601000000001", "P1");
    assertThat(o.getIdentifier())
        .extracting(i -> i.getValue())
        .containsExactly("7601001362383", "Z1");
    assertThat(o.getAddressFirstRep().getCountry()).isEqualTo("CH");
    assertThat(report.entries()).isEmpty();
  }

  @Test
  void theOptionIsNotUsedWhenTheDocumentHasTheData() {
    map(
        med(1, new HealthcarePerson("7601000000001", "Hans", "Muster", null)),
        ConversionOptions.defaults().withAuthor(OPTION));
    assertThat(report.entries())
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo(ReasonCode.DERIVED_VALUE));
  }

  @Test
  void aMissingPractitionerGlnComesFromTheOptionOrIsMissing() {
    AuthorMapper.Author author =
        map(
            med(1, new HealthcarePerson(null, "Hans", "Beispiel", null)),
            ConversionOptions.defaults().withAuthor(OPTION));
    assertThat(((Practitioner) author.resources().get(1)).getIdentifierFirstRep().getValue())
        .isEqualTo("7601000000002");

    ReportCollector other = new ReportCollector();
    AuthorMapper.Author none =
        AuthorMapper.map(
            med(1, new HealthcarePerson(null, "Hans", "Beispiel", null)),
            new Reference("urn:uuid:patient"),
            new MappingScope("x", new MappingContext(ConversionOptions.defaults(), other)));
    assertThat(none).isNull();
    assertThat(other.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.MISSING_REQUIRED_DATA);
              assertThat(e.path()).isEqualTo("/hcPerson/gln");
            });
  }

  @Test
  void thePatientAsAuthor() {
    AuthorMapper.Author author = map(med(2, null), ConversionOptions.defaults());
    assertThat(author.reference().getReference()).isEqualTo("urn:uuid:patient");
    assertThat(author.resources()).isEmpty();
  }
}
