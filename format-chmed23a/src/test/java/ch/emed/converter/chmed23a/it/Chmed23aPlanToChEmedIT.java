package ch.emed.converter.chmed23a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed23a.testsupport.Corpus;
import ch.emed.converter.chmed23a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** T031 (US1, SC-001): CHMED23A medication plans to CH EMED through the library. */
class Chmed23aPlanToChEmedIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  static ConversionResult convert(byte[] input, ConversionOptions options) {
    return converter.convert(
        new ConversionRequest(FormatId.CHMED23A, FormatId.CH_EMED, input, options));
  }

  static List<ReportEntry> errors(ConversionResult result) {
    return result.report().entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }

  static Stream<String> plans() {
    return Corpus.plans().stream();
  }

  @ParameterizedTest
  @MethodSource("plans")
  void everyPlanConvertsToAValidMedicationCard(String fixture) {
    ConversionResult result =
        convert(Fixtures.bytes(fixture), TestOptions.withAuthorAndPatientId());

    assertThat(errors(result)).as(fixture).isEmpty();
    byte[] output = result.output().orElseThrow();
    ChEmedAssertions.assertValid(output);
    Bundle bundle = ChEmedJson.parse(output);
    long statements =
        bundle.getEntry().stream()
            .filter(e -> e.getResource() instanceof MedicationStatement)
            .count();
    assertThat(statements).as(fixture).isGreaterThan(0);
  }

  @Test
  void example2NeedsThePractitionerGlnFromTheOption() {
    ConversionResult result =
        convert(Fixtures.bytes("chmed23a/official/Example2.json"), ConversionOptions.defaults());

    assertThat(errors(result))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.MISSING_REQUIRED_DATA);
              assertThat(e.path()).isEqualTo("/hcPerson/gln");
            });
  }

  @Test
  void example1ReportsTheExpectedLossesAndDerivedValues() {
    ConversionResult result =
        convert(Fixtures.bytes("chmed23a/official/Example1.json"), ConversionOptions.defaults());

    assertThat(errors(result)).isEmpty();
    List<String> entries =
        result.report().entries().stream()
            .map(
                e ->
                    e.severity()
                        + " "
                        + e.code()
                        + " "
                        + e.path()
                        + (e.clinicalLoss() ? " loss" : ""))
            .toList();
    assertThat(entries)
        .contains(
            "INFORMATION DERIVED_VALUE /id",
            "WARNING UNMAPPABLE_ELEMENT /patient/mData/w loss",
            "WARNING UNMAPPABLE_ELEMENT /patient/mData/rcs loss",
            "INFORMATION UNMAPPABLE_ELEMENT /meds/0/nbPack",
            "WARNING UNMAPPABLE_ELEMENT /meds/0/prscbBy",
            "WARNING UNMAPPABLE_ELEMENT /meds/0/idType",
            "WARNING CHMED_INVALID_FIELD /meds/2/pos/0/moa",
            "WARNING UNMAPPABLE_ELEMENT /meds/2/pos/0/moa loss");
  }

  @Test
  void draftFragmentsOfPosologyMdAreRefused() {
    for (String fixture : Corpus.DRAFT_NAMES) {
      ConversionResult result = convert(Fixtures.bytes(fixture), ConversionOptions.defaults());
      assertThat(errors(result)).as(fixture).isNotEmpty();
      assertThat(result.output()).as(fixture).isEmpty();
    }
  }
}
