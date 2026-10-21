package ch.emed.converter.chmed16a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReportEntry;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** US4: strict mode turns clinical loss into a failure (FR-016, quickstart scenario 4). */
class StrictModeIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult toChEmed(String fixture, ConversionOptions options) {
    return converter.convert(
        new ConversionRequest(
            FormatId.CHMED16A, FormatId.CH_EMED, Fixtures.bytes("chmed16a/" + fixture), options));
  }

  private static ConversionResult toChmed16a(String fixture, boolean strict) {
    return converter.convert(
        new ConversionRequest(
            FormatId.CH_EMED,
            FormatId.CHMED16A,
            Fixtures.bytes("chemed/" + fixture),
            ConversionOptions.defaults().withStrict(strict)));
  }

  private static List<String> lossPaths(ConversionResult result) {
    return result.report().entries().stream()
        .filter(ReportEntry::clinicalLoss)
        .map(ReportEntry::path)
        .sorted()
        .toList();
  }

  @Test
  void byDefaultALossyConversionDeliversTheDocumentWithWarnings() {
    ConversionResult result = toChEmed("plan-unmappable-element.json", TestOptions.withAuthor());

    assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS_WITH_WARNINGS);
    assertThat(result.output()).isPresent();
    assertThat(lossPaths(result)).containsExactly("/Medicaments/1/Roa");
  }

  @Test
  void inStrictModeTheSameInputFailsAndTheReportNamesEveryCause() {
    ConversionResult lenient = toChEmed("plan-unmappable-element.json", TestOptions.withAuthor());
    ConversionResult strict =
        toChEmed("plan-unmappable-element.json", TestOptions.withAuthor().withStrict(true));

    assertThat(strict.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(strict.output()).isEmpty();
    assertThat(lossPaths(strict)).isNotEmpty().isEqualTo(lossPaths(lenient));
  }

  @Test
  void strictModeDoesNotAffectConversionsWithoutClinicalLoss() {
    ConversionResult result =
        toChEmed("plan-basic.json", TestOptions.withAuthor().withStrict(true));

    assertThat(lossPaths(result)).isEmpty();
    assertThat(result.outcome()).isNotEqualTo(Outcome.FAILED);
    assertThat(result.output()).isPresent();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "card-inactive-medication.json",
        "card-complex-timing.json",
        "medication-prescription-example.json"
      })
  void chEmedDocumentsWithKnownGapsFailInStrictModeAndPassWithout(String fixture) {
    ConversionResult lenient = toChmed16a(fixture, false);
    ConversionResult strict = toChmed16a(fixture, true);

    assertThat(lenient.outcome()).isNotEqualTo(Outcome.FAILED);
    assertThat(lenient.output()).isPresent();
    assertThat(strict.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(strict.output()).isEmpty();
    assertThat(lossPaths(strict)).isNotEmpty().isEqualTo(lossPaths(lenient));
  }

  @Test
  void theOfficialMedicationCardHasNoClinicalLossInThisDirection() {
    ConversionResult strict = toChmed16a("medication-card-example.json", true);

    assertThat(strict.outcome()).isNotEqualTo(Outcome.FAILED);
    assertThat(strict.output()).isPresent();
  }
}
