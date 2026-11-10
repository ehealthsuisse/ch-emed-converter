package ch.emed.converter.epr.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.ClinicalEquivalence;
import ch.emed.converter.chmed16a.testsupport.ErezeptSchema;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.epr.testsupport.EprFixtures;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T044 (feature 004, spec US4.1–US4.4) and the round trip of US3.4: PRE and MTP documents from the
 * EPR become CH EMED, prescriptions go on to CHMED16A with the existing conversion.
 */
class PreMtpToChEmedIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult convert(FormatId source, FormatId target, byte[] input) {
    return converter.convert(
        new ConversionRequest(source, target, input, ConversionOptions.defaults()));
  }

  private static List<String> errors(ConversionResult result) {
    return result.report().entries().stream()
        .filter(e -> e.severity() == Severity.ERROR)
        .map(e -> e.code() + " " + e.path() + " " + e.message())
        .toList();
  }

  private static String snomedType(byte[] document) {
    Composition c = (Composition) ChEmedJson.parse(document).getEntryFirstRep().getResource();
    return c.getType().getCoding().stream()
        .filter(x -> "http://snomed.info/sct".equals(x.getSystem()))
        .findFirst()
        .orElseThrow()
        .getCode();
  }

  static Stream<String> prescriptions() {
    return EprFixtures.official("PRE").stream();
  }

  static Stream<String> treatmentPlans() {
    return EprFixtures.official("MTP").stream();
  }

  @ParameterizedTest
  @MethodSource("prescriptions")
  void aPrescriptionBecomesChEmedAndThenAnERezept(String name) {
    ConversionResult chEmed =
        convert(FormatId.CH_EMED_EPR, FormatId.CH_EMED, EprFixtures.bytes("official/" + name));

    assertThat(errors(chEmed)).as(name).isEmpty();
    byte[] prescription = chEmed.output().orElseThrow();
    assertThat(snomedType(prescription)).isEqualTo("761938008");

    ConversionResult erezept = convert(FormatId.CH_EMED, FormatId.CHMED16A, prescription);
    if (erezept.outcome() == Outcome.FAILED) {
      // the existing conversion names what is missing (US4.2), never a validation of its own
      assertThat(erezept.report().entries())
          .as(name)
          .filteredOn(e -> e.severity() == Severity.ERROR)
          .isNotEmpty()
          .extracting(ReportEntry::code)
          .doesNotContain(ReasonCode.FHIR_VALIDATION);
    } else {
      assertThat(ErezeptSchema.validate(erezept.output().orElseThrow())).as(name).isEmpty();
    }
  }

  @ParameterizedTest
  @MethodSource("treatmentPlans")
  void aTreatmentPlanBecomesAChEmedTreatmentPlan(String name) {
    ConversionResult chEmed =
        convert(FormatId.CH_EMED_EPR, FormatId.CH_EMED, EprFixtures.bytes("official/" + name));

    assertThat(errors(chEmed)).as(name).isEmpty();
    assertThat(snomedType(chEmed.output().orElseThrow())).isEqualTo("761931002");
  }

  @Test
  void aDirectRequestToChmed16aNamesThePathThroughChEmed() {
    ConversionResult result =
        convert(
            FormatId.CH_EMED_EPR,
            FormatId.CHMED16A,
            EprFixtures.bytes("official/" + EprFixtures.official("PRE").get(0)));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.report().entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNSUPPORTED_CONVERSION);
              assertThat(e.message()).contains("ch-emed");
            });
  }

  @Test
  void aChEmedTreatmentPlanGoesToMtpAndBackClinicallyUnchanged() {
    byte[] original = EprFixtures.bytes("chemed/Bundle-1-1-MedicationTreatmentPlan.json");

    ConversionResult mtp = convert(FormatId.CH_EMED, FormatId.CH_EMED_EPR, original);
    assertThat(errors(mtp)).isEmpty();
    ConversionResult back =
        convert(FormatId.CH_EMED_EPR, FormatId.CH_EMED, mtp.output().orElseThrow());
    assertThat(errors(back)).isEmpty();

    // the documented changes (M-EPR-22): Dosage.method is reported as clinical loss,
    // Patient.identifier.type as technical; apart from them the plan comes back unchanged
    assertThat(mtp.report().entries())
        .filteredOn(ReportEntry::clinicalLoss)
        .extracting(ReportEntry::path)
        .singleElement()
        .satisfies(p -> assertThat(p).endsWith(".dosage[0].method"));
    assertThat(mtp.report().entries())
        .anyMatch(
            e -> e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT) && e.path().endsWith(".type"));
    Bundle expected = ChEmedJson.parse(original);
    for (Bundle.BundleEntryComponent entry : expected.getEntry()) {
      if (entry.getResource() instanceof MedicationStatement s) {
        s.getDosage().forEach(d -> d.setMethod(null));
      } else if (entry.getResource() instanceof org.hl7.fhir.r4.model.Patient p) {
        p.getIdentifier().forEach(i -> i.setType(null));
      }
    }
    assertThat(
            ClinicalEquivalence.chEmed(ChEmedJson.serialize(expected), back.output().orElseThrow()))
        .isEmpty();
  }
}
