package ch.emed.converter.epr.it;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.ChEmedAssertions;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T054 (feature 004, FR-011): every CH EMED EPR conversion of the corpus, in both directions and as
 * treatment-plans, gives byte-identical output and report when run twice and in a fresh converter.
 * Feature 005 (T027): also the CH EMED dispenses, advice and lists.
 */
class DeterminismIT {

  private record Case(String name, FormatId from, FormatId to, byte[] input, ConversionOptions o) {}

  private static List<Case> cases(Converter converter) {
    List<Case> cases = new ArrayList<>();
    ConversionOptions defaults = ConversionOptions.defaults();
    for (String name : EprFixtures.names("official", "Bundle-")) {
      byte[] epr = EprFixtures.bytes("official/" + name);
      cases.add(new Case(name, FormatId.CH_EMED_EPR, FormatId.CH_EMED, epr, defaults));
      converter
          .convert(new ConversionRequest(FormatId.CH_EMED_EPR, FormatId.CH_EMED, epr, defaults))
          .output()
          .ifPresent(
              chEmed ->
                  cases.add(
                      new Case(
                          name + " back",
                          FormatId.CH_EMED,
                          FormatId.CH_EMED_EPR,
                          chEmed,
                          defaults)));
    }
    ConversionOptions references =
        defaults.withTreatmentPlanReferences(
            List.of(
                new TreatmentPlanReference(
                    "urn:uuid:11111111-1111-4111-8111-111111111111",
                    "urn:uuid:22222222-2222-4222-8222-222222222222")));
    for (String fixture :
        List.of(
            "chemed/medication-card-example.json",
            "chemed/medication-prescription-example.json",
            "chemed/card-complex-timing.json",
            "chemed/card-epr-elements.json")) {
      byte[] chEmed = Fixtures.bytes(fixture);
      cases.add(new Case(fixture, FormatId.CH_EMED, FormatId.CH_EMED_EPR, chEmed, defaults));
      cases.add(
          new Case(fixture + " refs", FormatId.CH_EMED, FormatId.CH_EMED_EPR, chEmed, references));
      cases.add(
          new Case(
              fixture + " plans",
              FormatId.CH_EMED,
              FormatId.CH_EMED_EPR,
              chEmed,
              defaults.withOutputForm(OutputForm.TREATMENT_PLANS)));
    }
    for (String fixture :
        List.of(
            "chemed/Bundle-1-2-MedicationDispense.json",
            "chemed/Bundle-2-4-MedicationDispense.json",
            "chemed/Bundle-3-1-MedicationDispense.json",
            "chemed/Bundle-2-2-PharmaceuticalAdvice.json",
            "chemed/Bundle-PharmaceuticalAdvice-ChangeDosage.json",
            "chemed/Bundle-2-1-MedicationList.json",
            "synthetic/Bundle-DispenseWithoutTreatmentPlan.json")) {
      byte[] chEmed = EprFixtures.bytes(fixture);
      cases.add(new Case(fixture, FormatId.CH_EMED, FormatId.CH_EMED_EPR, chEmed, defaults));
      cases.add(
          new Case(fixture + " refs", FormatId.CH_EMED, FormatId.CH_EMED_EPR, chEmed, references));
    }
    return cases;
  }

  private static String run(Converter converter, Case c) {
    ConversionResult result =
        converter.convert(new ConversionRequest(c.from(), c.to(), c.input(), c.o()));
    return new String(result.output().orElse(new byte[0]), java.nio.charset.StandardCharsets.UTF_8)
        + "\n--report--\n"
        + new String(
            ReportJsonWriter.write(result.report()), java.nio.charset.StandardCharsets.UTF_8);
  }

  @Test
  void twiceAndInAFreshConverterTheSameBytes() {
    Converter first = Converters.create(ChEmedAssertions.validator());
    List<Case> cases = cases(first);
    List<String> once = cases.stream().map(c -> run(first, c)).toList();
    List<String> twice = cases.stream().map(c -> run(first, c)).toList();
    Converter fresh = Converters.create(ChEmedAssertions.validator());
    List<String> again = cases.stream().map(c -> run(fresh, c)).toList();

    assertThat(cases).hasSizeGreaterThan(40);
    for (int i = 0; i < cases.size(); i++) {
      assertThat(twice.get(i)).as(cases.get(i).name()).isEqualTo(once.get(i));
      assertThat(again.get(i)).as(cases.get(i).name() + " fresh").isEqualTo(once.get(i));
    }
  }
}
