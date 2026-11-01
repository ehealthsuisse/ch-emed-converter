package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.read;
import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.Severity;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.junit.jupiter.api.Test;

class MedicationMapperTest {

  @Test
  void gtinBecomesGtinCodingWithDerivedText() {
    Medication med = read("plan-basic.json");
    MappingScope scope = scope(med, ConversionOptions.defaults());

    org.hl7.fhir.r4.model.Medication medication =
        MedicationMapper.mapMedication(med.medicaments().get(0), "/Medicaments/0", scope);

    assertThat(medication.getIdPart()).isEqualTo("med");
    assertThat(medication.getCode().getCodingFirstRep().getSystem()).isEqualTo("urn:oid:2.51.1.1");
    assertThat(medication.getCode().getCodingFirstRep().getCode()).isEqualTo("7680521101306");
    assertThat(medication.getCode().getText()).isEqualTo("GTIN 7680521101306");
    assertThat(scope.report().entries())
        .anyMatch(
            e -> e.code().equals(ReasonCode.DERIVED_VALUE) && e.severity() == Severity.INFORMATION);
  }

  @Test
  void freeTextHasNoCoding() {
    Medication med = read("plan-free-text-med.json");
    MappingScope scope = scope(med, ConversionOptions.defaults());

    org.hl7.fhir.r4.model.Medication medication =
        MedicationMapper.mapMedication(med.medicaments().get(0), "/Medicaments/0", scope);

    assertThat(medication.getCode().hasCoding()).isFalse();
    assertThat(medication.getCode().getText()).isEqualTo("Magnesium Brausetablette 300 mg");
  }

  @Test
  void pharmacodeIsCarriedAsTextAndReported() {
    Medication med = read("plan-pharmacode.json");
    MappingScope scope = scope(med, ConversionOptions.defaults());

    org.hl7.fhir.r4.model.Medication medication =
        MedicationMapper.mapMedication(med.medicaments().get(0), "/Medicaments/0", scope);

    assertThat(medication.getCode().hasCoding()).isFalse();
    assertThat(medication.getCode().getText()).isEqualTo("Pharmacode 4461382");
    assertThat(scope.report().entries())
        .anyMatch(
            e ->
                e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT)
                    && e.severity() == Severity.WARNING
                    && e.path().equals("/Medicaments/0/IdType"));
  }

  @Test
  void invalidGtinIsCarriedUnchanged() {
    Medication med = read("plan-invalid-gtin.json");
    org.hl7.fhir.r4.model.Medication medication =
        MedicationMapper.mapMedication(
            med.medicaments().get(0), "/Medicaments/0", scope(med, ConversionOptions.defaults()));

    assertThat(medication.getCode().getCodingFirstRep().getCode()).isEqualTo("7680521101307");
  }

  @Test
  void statementFieldsReasonSelfMedicationAndPrescriber() {
    Medication med = read("plan-free-text-med.json");
    MappingScope scope = scope(med, ConversionOptions.defaults());
    MedicationStatement statement = new MedicationStatement();

    MedicationMapper.applyStatementFields(
        statement, med.medicaments().get(0), "/Medicaments/0", scope);

    assertThat(statement.getCategory().getCodingFirstRep().getCode()).isEqualTo("patientspecified");

    Medication basic = read("plan-basic.json");
    MedicationStatement withReason = new MedicationStatement();
    MedicationMapper.applyStatementFields(
        withReason,
        basic.medicaments().get(0),
        "/Medicaments/0",
        scope(basic, ConversionOptions.defaults()));
    assertThat(withReason.getReasonCodeFirstRep().getText()).isEqualTo("Bluthochdruck");
    assertThat(withReason.hasCategory()).isFalse();
  }
}
