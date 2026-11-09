package ch.emed.converter.epr.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.testsupport.LargePlanGenerator;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.OutputForm;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.render.OriginalRepresentationRenderer;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.epr.testsupport.EprFixtures;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T038 (feature 004, spec US3, SC-004): a medication card becomes one valid MTP per treatment, end
 * to end through the converter with the validator of the distribution.
 */
class TreatmentPlansIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult convert(
      FormatId source, FormatId target, byte[] input, ConversionOptions options) {
    return converter.convert(new ConversionRequest(source, target, input, options));
  }

  private static ConversionOptions plans() {
    return ConversionOptions.defaults().withOutputForm(OutputForm.TREATMENT_PLANS);
  }

  private static List<String> errors(ConversionResult result) {
    return result.report().entries().stream()
        .filter(e -> e.severity() == Severity.ERROR)
        .map(e -> e.code() + " " + e.path() + " " + e.message())
        .toList();
  }

  private static int treatments(byte[] card) {
    return (int)
        ChEmedJson.parse(card).getEntry().stream()
            .filter(e -> e.getResource() instanceof MedicationStatement)
            .count();
  }

  /** CH EMED cards: IG example, feature 001 plans (valid fictitious GLN), official PMLC. */
  static Stream<String> cards() {
    return Stream.concat(
        Stream.of(
            "chemed/medication-card-example.json",
            "chmed16a/plan-basic.json",
            "chmed16a/plan-cycle.json",
            "chmed16a/plan-free-text-med.json",
            "chmed16a/plan-medical-data.json",
            "chmed16a/plan-non-ascii.json",
            "chmed16a/plan-pharmacode.json",
            "chmed16a/plan-reserve.json",
            "chmed16a/plan-taking-times.json"),
        EprFixtures.official("PMLC").stream()
            .filter(n -> !n.contains("Empty"))
            .map(n -> "official/" + n));
  }

  static byte[] card(String fixture) {
    if (fixture.startsWith("chemed/")) {
      return Fixtures.bytes(fixture);
    }
    if (fixture.startsWith("official/")) {
      ConversionResult r =
          convert(
              FormatId.CH_EMED_EPR,
              FormatId.CH_EMED,
              EprFixtures.bytes(fixture),
              ConversionOptions.defaults());
      return r.output().orElseThrow();
    }
    ConversionResult r =
        convert(
            FormatId.CHMED16A,
            FormatId.CH_EMED,
            Fixtures.bytes(fixture),
            TestOptions.withAuthorAndPatientId());
    return new String(r.output().orElseThrow(), StandardCharsets.UTF_8)
        .replace(ChEmedToEprIT.INVALID_GLN, ChEmedToEprIT.VALID_GLN)
        .getBytes(StandardCharsets.UTF_8);
  }

  @ParameterizedTest
  @MethodSource("cards")
  void everyTreatmentBecomesAValidMtp(String fixture) {
    byte[] card = card(fixture);

    ConversionResult first = convert(FormatId.CH_EMED, FormatId.CH_EMED_EPR, card, plans());

    assertThat(errors(first)).as(fixture).isEmpty();
    Bundle collection = ChEmedJson.parse(first.output().orElseThrow());
    assertThat(collection.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
    assertThat(collection.getEntry()).as(fixture).hasSize(treatments(card));
    for (Bundle.BundleEntryComponent entry : collection.getEntry()) {
      Bundle mtp = (Bundle) entry.getResource();
      assertThat(mtp.getType()).isEqualTo(Bundle.BundleType.DOCUMENT);
      Composition composition = (Composition) mtp.getEntryFirstRep().getResource();
      assertThat(composition.getType().getCoding()).anyMatch(c -> c.getCode().equals("761931002"));
      assertThat(
              mtp.getEntry().stream().filter(e -> e.getResource() instanceof MedicationStatement))
          .hasSize(1);
      assertThat(mtp.getEntry()).noneMatch(e -> e.getResource() instanceof Binary);
    }

    ConversionResult second = convert(FormatId.CH_EMED, FormatId.CH_EMED_EPR, card, plans());
    assertThat(second.output().orElseThrow()).isEqualTo(first.output().orElseThrow());
  }

  @Test
  void aCardWithoutTreatmentsFails() {
    byte[] card = card("official/Bundle-DocumentUCSF1CARAPMP004PMLCEmpty.json");

    ConversionResult result = convert(FormatId.CH_EMED, FormatId.CH_EMED_EPR, card, plans());

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(result.report().entries())
        .extracting(ReportEntry::code)
        .contains(ReasonCode.NO_TREATMENTS);
  }

  @Test
  void onlyACardIsAccepted() {
    for (String other :
        List.of(
            "chemed/medication-prescription-example.json", "official/Bundle-DocumentMtp1.json")) {
      byte[] input =
          other.startsWith("chemed/")
              ? Fixtures.bytes(other)
              : convert(
                      FormatId.CH_EMED_EPR,
                      FormatId.CH_EMED,
                      EprFixtures.bytes(other),
                      ConversionOptions.defaults())
                  .output()
                  .orElseThrow();

      ConversionResult result = convert(FormatId.CH_EMED, FormatId.CH_EMED_EPR, input, plans());

      assertThat(result.outcome()).as(other).isEqualTo(Outcome.FAILED);
      assertThat(result.report().entries())
          .extracting(ReportEntry::code)
          .as(other)
          .contains(ReasonCode.UNSUPPORTED_DOCUMENT_TYPE);
    }
  }

  @Test
  void treatmentPlansAreNotOfferedForChmed16a() {
    ConversionResult result =
        convert(
            FormatId.CH_EMED,
            FormatId.CHMED16A,
            Fixtures.bytes("chemed/medication-card-example.json"),
            plans());

    assertThat(result.report().entries())
        .extracting(ReportEntry::code)
        .contains(ReasonCode.UNSUPPORTED_OUTPUT_FORM);
  }

  @Test
  void theOriginalRepresentationOptionIsNotUsedButReported() {
    byte[] card = card("chmed16a/plan-basic.json");
    byte[] pdfA =
        OriginalRepresentationRenderer.renderPdfA(ChEmedJson.parse(card), new ReportCollector());

    ConversionResult without = convert(FormatId.CH_EMED, FormatId.CH_EMED_EPR, card, plans());
    ConversionResult with =
        convert(
            FormatId.CH_EMED, FormatId.CH_EMED_EPR, card, plans().withOriginalRepresentation(pdfA));

    assertThat(with.output().orElseThrow()).isEqualTo(without.output().orElseThrow());
    assertThat(with.report().entries())
        .anyMatch(
            e ->
                e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT)
                    && e.severity() == Severity.WARNING
                    && !e.clinicalLoss()
                    && e.message().contains("treatment-plans"));
  }

  @Test
  void anEntryWithAnExistingTreatmentPlanIsReported() {
    byte[] card = Fixtures.bytes("chemed/medication-card-example.json");

    ConversionResult result = convert(FormatId.CH_EMED, FormatId.CH_EMED_EPR, card, plans());

    assertThat(result.report().entries())
        .filteredOn(e -> e.code().equals(ReasonCode.TREATMENT_PLAN_EXISTS))
        .hasSize(treatments(card))
        .allSatisfy(e -> assertThat(e.severity()).isEqualTo(Severity.WARNING));
  }

  @Test
  void aSingleTreatmentPlanBecomesOneMtp() {
    byte[] mtp =
        convert(
                FormatId.CH_EMED_EPR,
                FormatId.CH_EMED,
                EprFixtures.bytes("official/Bundle-DocumentMtp1.json"),
                ConversionOptions.defaults())
            .output()
            .orElseThrow();

    ConversionResult result =
        convert(FormatId.CH_EMED, FormatId.CH_EMED_EPR, mtp, ConversionOptions.defaults());

    assertThat(errors(result)).isEmpty();
    Composition composition =
        (Composition)
            ChEmedJson.parse(result.output().orElseThrow()).getEntryFirstRep().getResource();
    assertThat(composition.getType().getCoding()).anyMatch(c -> c.getCode().equals("761931002"));
  }

  @Test
  void aCardWithHundredTreatmentsGivesHundredMtps() {
    byte[] plan = LargePlanGenerator.plan(100);
    byte[] card =
        new String(
                convert(
                        FormatId.CHMED16A,
                        FormatId.CH_EMED,
                        plan,
                        TestOptions.withAuthorAndPatientId())
                    .output()
                    .orElseThrow(),
                StandardCharsets.UTF_8)
            .replace(ChEmedToEprIT.INVALID_GLN, ChEmedToEprIT.VALID_GLN)
            .getBytes(StandardCharsets.UTF_8);

    ConversionResult result = convert(FormatId.CH_EMED, FormatId.CH_EMED_EPR, card, plans());

    assertThat(errors(result)).isEmpty();
    assertThat(ChEmedJson.parse(result.output().orElseThrow()).getEntry()).hasSize(100);
  }
}
