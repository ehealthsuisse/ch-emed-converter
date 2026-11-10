package ch.emed.converter.epr.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.OutputForm;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.epr.testsupport.EprFixtures;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T021 (feature 004, US1.2): the documented capability gap of a PMLC on its way to an eMediplan.
 * The author of a PMLC is the community's PMP service (a Device without GLN); CHMED16A requires the
 * GLN of the author (M-REV-04), so the second step fails with {@code MISSING_REQUIRED_DATA} naming
 * the author, in every output form (decision 2026-11-09, mapping specification ch-emed-epr.md).
 */
class PmlcToChmed16aIT {

  private static Converter converter;

  @BeforeAll
  static void createConverter() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  static Stream<String> cards() {
    return EprFixtures.official("PMLC").stream();
  }

  @ParameterizedTest
  @MethodSource("cards")
  void secondStepNamesTheMissingAuthorGln(String name) {
    ConversionResult card =
        converter.convert(
            new ConversionRequest(
                FormatId.CH_EMED_EPR,
                FormatId.CH_EMED,
                EprFixtures.bytes("official/" + name),
                ConversionOptions.defaults()));
    assertThat(card.outcome()).as(name).isNotEqualTo(Outcome.FAILED);

    for (OutputForm form :
        new OutputForm[] {OutputForm.DOCUMENT, OutputForm.QR_PAYLOAD, OutputForm.QR_IMAGE}) {
      ConversionResult plan =
          converter.convert(
              new ConversionRequest(
                  FormatId.CH_EMED,
                  FormatId.CHMED16A,
                  card.output().orElseThrow(),
                  ConversionOptions.defaults().withOutputForm(form)));

      assertThat(plan.outcome()).as(name + " " + form).isEqualTo(Outcome.FAILED);
      assertThat(plan.report().entries())
          .filteredOn(e -> e.severity() == Severity.ERROR)
          .extracting(ReportEntry::code, ReportEntry::path)
          .containsExactly(tuple(ReasonCode.MISSING_REQUIRED_DATA, "Composition.author"));
    }
  }
}
