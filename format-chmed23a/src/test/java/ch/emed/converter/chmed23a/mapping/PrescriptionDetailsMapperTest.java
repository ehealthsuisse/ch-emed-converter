package ch.emed.converter.chmed23a.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed23a.model.Medicament;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.chmed23a.model.Repetition;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.spi.MappingContext;
import java.math.BigDecimal;
import java.util.List;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.Test;

/** T042: prescription details M23-RX-03..09, one test per row of research R10. */
class PrescriptionDetailsMapperTest {

  private final ReportCollector report = new ReportCollector();
  private final MappingScope scope =
      new MappingScope(
          "0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e23",
          new MappingContext(ConversionOptions.defaults(), report));

  private static Medicament medicament(
      Repetition reps, Boolean isNotSub, Boolean sic, BigDecimal nbPack) {
    return new Medicament(
        "7680388400376", 2, List.of(), null, null, null, reps, isNotSub, sic, nbPack, List.of());
  }

  private static Medication rx(Medicament m, String rec) {
    return new Medication(
        null,
        null,
        null,
        List.of(m),
        List.of(),
        3,
        null,
        1,
        rec,
        "2024-02-08T10:01:23+01:00",
        null);
  }

  private MedicationRequest apply(Medicament m, String rec) {
    MedicationRequest request = new MedicationRequest();
    PrescriptionDetailsMapper.apply(request, m, rx(m, rec), "/meds/0", scope);
    return request;
  }

  private static String period(MedicationRequest r) {
    return r.getDispenseRequest().getValidityPeriod().getStartElement().getValueAsString()
        + "/"
        + r.getDispenseRequest().getValidityPeriod().getEndElement().getValueAsString();
  }

  @Test
  void numberIsRepeatsAllowed() {
    MedicationRequest r = apply(medicament(new Repetition.Number(2), null, null, null), null);
    assertThat(r.getDispenseRequest().getNumberOfRepeatsAllowed()).isEqualTo(2);
    assertThat(r.getDispenseRequest().hasValidityPeriod()).isFalse();
  }

  @Test
  void durationIsAnInclusiveValidityPeriod() {
    assertThat(period(apply(medicament(new Repetition.Duration(6, 6), null, null, null), null)))
        .isEqualTo("2024-02-08/2024-08-07");
    assertThat(period(apply(medicament(new Repetition.Duration(2, 5), null, null, null), null)))
        .isEqualTo("2024-02-08/2024-02-21");
    assertThat(period(apply(medicament(new Repetition.Duration(10, 4), null, null, null), null)))
        .isEqualTo("2024-02-08/2024-02-17");
    assertThat(period(apply(medicament(new Repetition.Duration(1, 7), null, null, null), null)))
        .isEqualTo("2024-02-08/2025-02-07");
    assertThat(report.entries()).isEmpty();
  }

  @Test
  void durationUnderOneDayIsReportedWithClinicalLoss() {
    MedicationRequest r = apply(medicament(new Repetition.Duration(12, 3), null, null, null), null);
    assertThat(r.getDispenseRequest().hasValidityPeriod()).isFalse();
    assertThat(report.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
              assertThat(e.clinicalLoss()).isTrue();
            });
  }

  @Test
  void numberAndDurationGiveBoth() {
    MedicationRequest r =
        apply(medicament(new Repetition.NumberAndDuration(3, 1, 6), null, null, null), null);
    assertThat(r.getDispenseRequest().getNumberOfRepeatsAllowed()).isEqualTo(3);
    assertThat(period(r)).isEqualTo("2024-02-08/2024-03-07");
  }

  @Test
  void noRepetitionGivesNothing() {
    MedicationRequest r = apply(medicament(null, null, null, null), null);
    assertThat(r.hasDispenseRequest()).isFalse();
    assertThat(r.hasSubstitution()).isFalse();
  }

  @Test
  void packagesSubstitutionSicAndRecipient() {
    MedicationRequest r = apply(medicament(null, true, true, new BigDecimal("2")), "7601000000002");
    assertThat(((Quantity) r.getDispenseRequest().getQuantity()).getValue())
        .isEqualByComparingTo("2");
    assertThat(r.getSubstitution().getAllowedCodeableConcept().getCodingFirstRep().getCode())
        .isEqualTo("N");
    assertThat(r.getDispenseRequest().getPerformer().getIdentifier().getValue())
        .isEqualTo("7601000000002");
    assertThat(report.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.path()).isEqualTo("/meds/0/sic");
              assertThat(e.clinicalLoss()).isTrue();
            });
    assertThat(
            apply(medicament(null, false, null, null), null)
                .getSubstitution()
                .getAllowedCodeableConcept()
                .getCodingFirstRep()
                .getCode())
        .isEqualTo("E");
  }
}
