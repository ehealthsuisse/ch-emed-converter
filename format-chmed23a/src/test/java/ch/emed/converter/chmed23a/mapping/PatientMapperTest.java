package ch.emed.converter.chmed23a.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed23a.model.Extension;
import ch.emed.converter.chmed23a.model.MedicalData;
import ch.emed.converter.chmed23a.model.PatientId;
import ch.emed.converter.chmed23a.model.RiskCategory;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.PatientIdentifier;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.MappingContext;
import java.math.BigDecimal;
import java.util.List;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

/** T026: patient rules M23-PAT-*, M23-MDT-01, M23-EXT-01. */
class PatientMapperTest {

  private final ReportCollector report = new ReportCollector();

  private MappingScope scope(ConversionOptions options) {
    return new MappingScope(
        "0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e23", new MappingContext(options, report));
  }

  private static ch.emed.converter.chmed23a.model.Patient patient(
      Integer gender, List<PatientId> ids, MedicalData mData, List<Extension> exts) {
    return new ch.emed.converter.chmed23a.model.Patient(
        "Zoë",
        "Example",
        "1970-02-03",
        gender,
        "Weg 1",
        "3000",
        "Bern",
        "ch",
        "FR",
        ids,
        exts,
        mData,
        List.of("079 000 00 00"),
        List.of("a@example.org"));
  }

  @Test
  void demographicsAddressTelecomAndLanguage() {
    Patient p =
        PatientMapper.map(
            patient(3, List.of(new PatientId(1, null, "80756000000000000001")), null, List.of()),
            scope(ConversionOptions.defaults()));
    assertThat(p.getNameFirstRep().getFamily()).isEqualTo("Example");
    assertThat(p.getNameFirstRep().getGivenAsSingleString()).isEqualTo("Zoë");
    assertThat(p.getGender()).isEqualTo(AdministrativeGender.OTHER);
    assertThat(p.getBirthDateElement().getValueAsString()).isEqualTo("1970-02-03");
    assertThat(p.getAddressFirstRep().getCountry()).isEqualTo("CH");
    assertThat(p.getTelecom()).hasSize(2);
    assertThat(p.getCommunicationFirstRep().getLanguage().getCodingFirstRep().getCode())
        .isEqualTo("fr");
    assertThat(p.getIdentifierFirstRep().getSystem())
        .isEqualTo("urn:oid:2.16.756.5.30.1.123.100.1.1.1");
    assertThat(report.entries()).isEmpty();
  }

  @Test
  void localIdsWithOidOrUrlAndInvalidSystemIdentifiers() {
    Patient p =
        PatientMapper.map(
            patient(
                1,
                List.of(
                    new PatientId(2, "2.999.1", "A"),
                    new PatientId(2, "https://example.org/pid", "B"),
                    new PatientId(2, "9.99.1", "C")),
                null,
                List.of()),
            scope(ConversionOptions.defaults()));
    assertThat(p.getIdentifier())
        .extracting(i -> i.getSystem() + "|" + i.getValue())
        .containsExactly("urn:oid:2.999.1|A", "https://example.org/pid|B");
    assertThat(report.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
              assertThat(e.path()).isEqualTo("/patient/ids/2/sId");
            });
  }

  @Test
  void withoutUsableIdTheOptionOrMissingRequiredData() {
    PatientMapper.map(
        patient(2, List.of(new PatientId(2, "9.9", "X")), null, List.of()),
        scope(
            ConversionOptions.defaults()
                .withPatientIdentifier(new PatientIdentifier("urn:oid:2.999.1", "T"))));
    assertThat(report.hasErrors()).isFalse();

    ReportCollector other = new ReportCollector();
    PatientMapper.map(
        patient(2, List.of(), null, List.of()),
        new MappingScope("x", new MappingContext(ConversionOptions.defaults(), other)));
    assertThat(other.entries())
        .anySatisfy(e -> assertThat(e.code()).isEqualTo(ReasonCode.MISSING_REQUIRED_DATA));
  }

  @Test
  void medicalDataAndExtensionsAreReportedByPathOnly() {
    MedicalData data =
        new MedicalData(
            null,
            null,
            null,
            List.of(new RiskCategory(1, List.of(577))),
            new BigDecimal("70"),
            null,
            List.of());
    PatientMapper.map(
        patient(
            2,
            List.of(new PatientId(1, null, "1")),
            data,
            List.of(new Extension("secretName", "secretValue", "schema", List.of()))),
        scope(ConversionOptions.defaults()));
    assertThat(report.entries())
        .extracting(ReportEntry::path)
        .containsExactly("/patient/mData/w", "/patient/mData/rcs", "/patient/exts/0");
    assertThat(report.entries())
        .filteredOn(e -> e.path().startsWith("/patient/mData"))
        .allSatisfy(e -> assertThat(e.clinicalLoss()).isTrue());
    assertThat(report.entries()).allSatisfy(e -> assertThat(e.message()).doesNotContain("secret"));
  }
}
