package ch.emed.converter.epr.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.testsupport.PhiScanner;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.OutputForm;
import ch.emed.converter.core.api.TreatmentPlanReference;
import ch.emed.converter.core.report.ReportJsonWriter;
import ch.emed.converter.epr.testsupport.EprFixtures;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T049 (feature 004, SC-007): no report of a CH EMED EPR conversion contains a patient value of its
 * input, in either direction, as treatment-plans, and for refused or failing inputs.
 */
class PhiLeakTest {

  private static final List<String> CH_EMED =
      List.of(
          "chemed/medication-card-example.json",
          "chemed/medication-prescription-example.json",
          "chemed/card-epr-elements.json",
          "chemed/card-complex-timing.json");

  @Test
  void reportsContainNoPatientValues() {
    Converter converter = Converters.create(ChEmedAssertions.validator());
    List<String> leaks = new ArrayList<>();

    List<String> official = new ArrayList<>(EprFixtures.names("official", "Bundle-"));
    for (String name : official) {
      byte[] input = EprFixtures.bytes("official/" + name);
      PhiScanner scanner =
          new PhiScanner().addChEmedJson(new String(input, StandardCharsets.UTF_8));
      ConversionResult result =
          converter.convert(
              new ConversionRequest(
                  FormatId.CH_EMED_EPR, FormatId.CH_EMED, input, ConversionOptions.defaults()));
      check(name, scanner, result, leaks);
      if (result.output().isPresent()) {
        check(
            name + " back",
            scanner,
            converter.convert(
                new ConversionRequest(
                    FormatId.CH_EMED,
                    FormatId.CH_EMED_EPR,
                    result.output().get(),
                    ConversionOptions.defaults())),
            leaks);
      }
    }

    ConversionOptions references =
        ConversionOptions.defaults()
            .withTreatmentPlanReferences(
                List.of(
                    new TreatmentPlanReference(
                        "urn:uuid:11111111-1111-4111-8111-111111111111",
                        "urn:uuid:22222222-2222-4222-8222-222222222222")));
    for (String fixture : CH_EMED) {
      byte[] input = Fixtures.bytes(fixture);
      PhiScanner scanner =
          new PhiScanner().addChEmedJson(new String(input, StandardCharsets.UTF_8));
      for (ConversionOptions options :
          List.of(
              ConversionOptions.defaults(),
              references,
              ConversionOptions.defaults().withOutputForm(OutputForm.TREATMENT_PLANS))) {
        check(
            fixture,
            scanner,
            converter.convert(
                new ConversionRequest(FormatId.CH_EMED, FormatId.CH_EMED_EPR, input, options)),
            leaks);
      }
    }

    assertThat(leaks).isEmpty();
  }

  private static void check(
      String name, PhiScanner scanner, ConversionResult result, List<String> leaks) {
    String report = new String(ReportJsonWriter.write(result.report()), StandardCharsets.UTF_8);
    for (String value : scanner.findIn(report)) {
      leaks.add(name + ": " + value);
    }
  }
}
