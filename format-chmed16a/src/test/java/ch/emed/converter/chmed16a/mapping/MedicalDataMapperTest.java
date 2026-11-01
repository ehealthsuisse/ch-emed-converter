package ch.emed.converter.chmed16a.mapping;

import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.read;
import static ch.emed.converter.chmed16a.mapping.MappingTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import org.junit.jupiter.api.Test;

/**
 * The CH EMED Medication Card has no place for CHMED16A medical data (weight, height, risks); every
 * element is reported (mapping spec M-MED-DATA, capability gap).
 */
class MedicalDataMapperTest {

  @Test
  void everyMedicalDataElementIsReported() {
    Medication med = read("plan-medical-data.json");
    MappingScope scope = scope(med, ConversionOptions.defaults());

    MedicalDataMapper.map(med.patient().med(), scope);

    assertThat(scope.report().entries())
        .filteredOn(e -> e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT))
        .extracting(ReportEntry::path)
        .containsExactlyInAnyOrder(
            "/Patient/Med/DLstMen",
            "/Patient/Med/Meas/0",
            "/Patient/Med/Meas/1",
            "/Patient/Med/Rc/0",
            "/Patient/Med/Rc/1",
            "/Patient/Med/Rc/2");
    assertThat(scope.report().entries()).allMatch(ReportEntry::clinicalLoss);
    assertThat(scope.report().entries())
        .allSatisfy(e -> assertThat(e.message()).doesNotContain("60").doesNotContain("170"));
  }

  @Test
  void emptyStringsAreTreatedAsAbsent() {
    Medication med = read("official/readme-example-medication-plan.json");
    MappingScope scope = scope(med, ConversionOptions.defaults());

    MedicalDataMapper.map(med.patient().med(), scope);

    assertThat(scope.report().entries())
        .extracting(ReportEntry::path)
        .doesNotContain("/Patient/Med/DLstMen");
  }

  @Test
  void noMedicalDataNoEntries() {
    Medication med = read("plan-basic.json");
    MappingScope scope = scope(med, ConversionOptions.defaults());

    MedicalDataMapper.map(med.patient().med(), scope);

    assertThat(scope.report().entries()).isEmpty();
  }
}
