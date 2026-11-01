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
import ch.emed.converter.core.report.ReportJsonWriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** FR-018: the same input and options give byte-identical output and equal reports. */
class DeterminismIT {

  private static Converter converter;

  @BeforeAll
  static void setUp() {
    converter = Converters.create(ChEmedAssertions.validator());
  }

  private static ConversionResult convert(
      FormatId from, FormatId to, String fixture, ConversionOptions options) {
    return converter.convert(new ConversionRequest(from, to, Fixtures.bytes(fixture), options));
  }

  private static void assertIdentical(ConversionResult a, ConversionResult b, String fixture) {
    assertThat(a.outcome()).as("outcome of " + fixture).isEqualTo(b.outcome());
    assertThat(a.output().orElse(new byte[0]))
        .as("output of " + fixture)
        .isEqualTo(b.output().orElse(new byte[0]));
    assertThat(ReportJsonWriter.write(a.report()))
        .as("report of " + fixture)
        .isEqualTo(ReportJsonWriter.write(b.report()));
  }

  @Test
  void everyChmed16aFixtureConvertsToIdenticalChEmedTwice() {
    List<String> fixtures = new ArrayList<>();
    for (String suffix : List.of(".json", ".txt")) {
      fixtures.addAll(Fixtures.list("chmed16a", suffix));
      fixtures.addAll(Fixtures.list("chmed16a/official", suffix));
    }
    for (String fixture : fixtures) {
      ConversionOptions options = TestOptions.withAuthorAndPatientId();
      assertIdentical(
          convert(FormatId.CHMED16A, FormatId.CH_EMED, fixture, options),
          convert(FormatId.CHMED16A, FormatId.CH_EMED, fixture, options),
          fixture);
    }
    assertThat(fixtures).hasSizeGreaterThan(25);
  }

  @Test
  void everyChEmedFixtureConvertsToIdenticalChmed16aTwice() {
    List<String> fixtures = Fixtures.list("chemed", ".json");
    for (String fixture : fixtures) {
      assertIdentical(
          convert(FormatId.CH_EMED, FormatId.CHMED16A, fixture, ConversionOptions.defaults()),
          convert(FormatId.CH_EMED, FormatId.CHMED16A, fixture, ConversionOptions.defaults()),
          fixture);
    }
    assertThat(fixtures).hasSizeGreaterThan(8);
  }
}
