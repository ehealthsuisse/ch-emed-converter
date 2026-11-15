package ch.emed.converter.chmed23a.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed23a.io.Chmed23aJsonReader;
import ch.emed.converter.chmed23a.model.Medicament;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.chmed23a.model.Posology;
import ch.emed.converter.chmed23a.testsupport.Corpus;
import ch.emed.converter.core.report.ReportCollector;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T029: localized descriptions of every posology of the corpus. */
class PosologyTextTest {

  private static final List<String> LANGUAGES = List.of("de-CH", "fr-CH", "it-CH", "en-CH");

  private static List<Posology> posologies(String fixture) {
    Medication med = Chmed23aJsonReader.read(Fixtures.bytes(fixture), new ReportCollector());
    return med.meds().stream().map(Medicament::pos).flatMap(List::stream).toList();
  }

  @Test
  void everyPosologyOfTheCorpusIsDescribedInEveryLanguage() {
    for (String fixture : Corpus.conforming()) {
      for (Posology pos : posologies(fixture)) {
        for (String language : LANGUAGES) {
          String text = PosologyText.describe(pos, language);
          assertThat(text).as(fixture + " " + language).isNotBlank().doesNotContain("?");
          assertThat(PosologyText.describe(pos, language)).isEqualTo(text);
        }
      }
    }
  }

  @Test
  void sequencesIntervalsAndDaysOfMonth() {
    List<Posology> example2 = posologies("chmed23a/official/Example2.json");
    assertThat(PosologyText.describe(example2.get(2), "de-CH"))
        .contains("während 2 Tag(e)")
        .contains("Pause 1 Tag(e)")
        .contains("(wiederholen)")
        .contains("vor dem Essen");
    assertThat(PosologyText.describe(example2.get(8), "fr-CH"))
        .contains("le jour du mois 10")
        .contains("10-20 gtt");
    List<Posology> example3 = posologies("chmed23a/official/Example3.json");
    assertThat(PosologyText.describe(example3.get(2), "en-CH"))
        .contains("at least 6 hour(s)")
        .contains("as needed");
  }

  @Test
  void theTextHoldsNoPatientData() {
    for (Posology pos : posologies("chmed23a/official/Example1.json")) {
      assertThat(PosologyText.describe(pos, "de-CH")).doesNotContain("Louloui", "Alice");
    }
  }
}
