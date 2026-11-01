package ch.emed.converter.chmed16a.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.CapabilityGaps;
import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.ClinicalEquivalence;
import ch.emed.converter.chmed16a.testsupport.ClinicalEquivalence.Difference;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.testsupport.TestOptions;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.report.Outcome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * US5, SC-004: every round trip keeps the clinical content; every difference is a documented
 * capability gap ({@code capability-gaps.json}). All differences are also written to {@code
 * target/roundtrip-differences.txt} for review.
 */
class RoundTripIT {

  private static final Path REPORT = Path.of("target/roundtrip-differences.txt");
  private static Converter converter;

  @BeforeAll
  static void setUp() throws IOException {
    converter = Converters.create(ChEmedAssertions.validator());
    Files.createDirectories(REPORT.getParent());
    Files.writeString(REPORT, "", StandardCharsets.UTF_8);
  }

  private static ConversionResult convert(
      FormatId from, FormatId to, byte[] input, ConversionOptions options) {
    return converter.convert(new ConversionRequest(from, to, input, options));
  }

  private static void record(String direction, String fixture, List<Difference> differences)
      throws IOException {
    StringBuilder sb =
        new StringBuilder("== " + direction + " " + fixture + System.lineSeparator());
    differences.forEach(d -> sb.append("  ").append(d).append(System.lineSeparator()));
    Files.writeString(REPORT, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "plan-basic.json",
        "plan-free-text-med.json",
        "plan-pharmacode.json",
        "plan-taking-times.json",
        "plan-reserve.json",
        "plan-cycle.json",
        "plan-medical-data.json",
        "plan-empty-meds.json",
        "plan-invalid-gtin.json",
        "plan-older-revision.json",
        "plan-unknown-fields.json",
        "plan-non-ascii.json",
        "plan-remarks-validation.json",
        "official/readme-example-medication-plan.json",
        "rx-basic.json",
        "rx-erezept-example.json",
        "rx-missing-hcperson.json"
      })
  void chmed16aThroughChEmedBackToChmed16a(String fixture) throws IOException {
    byte[] original = Fixtures.bytes("chmed16a/" + fixture);
    ConversionOptions options = TestOptions.withAuthorAndPatientId();

    ConversionResult toChEmed = convert(FormatId.CHMED16A, FormatId.CH_EMED, original, options);
    assertThat(toChEmed.outcome()).as("to CH EMED").isNotEqualTo(Outcome.FAILED);
    ConversionResult back =
        convert(FormatId.CH_EMED, FormatId.CHMED16A, toChEmed.output().orElseThrow(), options);
    assertThat(back.outcome()).as("back to CHMED16A").isNotEqualTo(Outcome.FAILED);

    List<Difference> differences =
        ClinicalEquivalence.chmed16a(original, back.output().orElseThrow());
    record(CapabilityGaps.CHMED16A, fixture, differences);

    assertThat(CapabilityGaps.unexplained(CapabilityGaps.CHMED16A, differences))
        .as("differences no capability gap explains")
        .isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "medication-card-example.json",
        "medication-card-example-uuid-fullurl.json",
        "card-epr-elements.json",
        "card-inactive-medication.json",
        "card-complex-timing.json",
        "medication-prescription-example.json",
        "rx-substitution-repeats.json"
      })
  void chEmedThroughChmed16aBackToChEmed(String fixture) throws IOException {
    byte[] original = Fixtures.bytes("chemed/" + fixture);
    ConversionOptions options = TestOptions.withAuthorAndPatientId();

    ConversionResult toChmed =
        convert(FormatId.CH_EMED, FormatId.CHMED16A, original, ConversionOptions.defaults());
    assertThat(toChmed.outcome()).as("to CHMED16A").isNotEqualTo(Outcome.FAILED);
    ConversionResult back =
        convert(FormatId.CHMED16A, FormatId.CH_EMED, toChmed.output().orElseThrow(), options);
    assertThat(back.outcome()).as("back to CH EMED").isNotEqualTo(Outcome.FAILED);

    List<Difference> differences =
        ClinicalEquivalence.chEmed(original, back.output().orElseThrow());
    record(CapabilityGaps.CHEMED, fixture, differences);

    assertThat(CapabilityGaps.unexplained(CapabilityGaps.CHEMED, differences))
        .as("differences no capability gap explains")
        .isEmpty();
  }
}
