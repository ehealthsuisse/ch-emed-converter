package ch.emed.converter.epr.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.epr.testsupport.EprFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** T072 and T073 (feature 004, spec edge cases 1 and 4). */
class EdgeCasesIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult convert(FormatId from, FormatId to, byte[] input) {
    return converter.convert(new ConversionRequest(from, to, input, ConversionOptions.defaults()));
  }

  private static List<ReportEntry> errors(ConversionResult result) {
    return result.report().entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }

  /** Edge case 1: a CH EMED document of a type outside this feature is refused. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "Bundle-1-2-MedicationDispense.json",
        "Bundle-2-2-PharmaceuticalAdvice.json",
        "Bundle-2-1-MedicationList.json"
      })
  void chEmedDocumentsOfOtherTypesAreRefusedNamingTheSupportedTypes(String name) {
    ConversionResult result =
        convert(FormatId.CH_EMED, FormatId.CH_EMED_EPR, EprFixtures.bytes("chemed/" + name));

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    assertThat(result.output()).isEmpty();
    assertThat(errors(result))
        .as(name)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNSUPPORTED_DOCUMENT_TYPE);
              assertThat(e.message())
                  .contains("not yet supported")
                  .contains("PMLC")
                  .contains("PRE")
                  .contains("MTP");
            });
  }

  /**
   * Edge case 4: a CH EMED EPR document declared as general CH EMED is converted as CH EMED; every
   * CH EMED EPR document is also a CH EMED document.
   */
  @Test
  void anEprDocumentDeclaredAsChEmedConvertsAsChEmed() {
    byte[] pre = EprFixtures.bytes("official/Bundle-DocumentPreParacetamolAxapharmCARAPMP004.json");

    ConversionResult toEpr = convert(FormatId.CH_EMED, FormatId.CH_EMED_EPR, pre);
    assertThat(errors(toEpr)).isEmpty();
    assertThat(toEpr.outcome()).isNotEqualTo(Outcome.FAILED);

    ConversionResult toChmed16a = convert(FormatId.CH_EMED, FormatId.CHMED16A, pre);
    assertThat(errors(toChmed16a))
        .extracting(ReportEntry::code)
        .doesNotContain(ReasonCode.FHIR_VALIDATION, ReasonCode.PARSE_ERROR);
  }
}
