package ch.emed.converter.chmed23a.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.chmed23a.testsupport.Corpus;
import ch.emed.converter.core.report.ReportCollector;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** T048: the CHMED23A JSON writer. */
class Chmed23aJsonWriterTest {

  private static Medication read(byte[] json) {
    ReportCollector report = new ReportCollector();
    Medication med = Chmed23aJsonReader.read(json, report);
    assertThat(report.hasErrors()).as(report.entries().toString()).isFalse();
    return med;
  }

  @Test
  void writingAndReadingGivesAnEqualModelForTheWholeCorpus() {
    for (String fixture : Corpus.conforming()) {
      Medication med = read(Fixtures.bytes(fixture));
      assertThat(read(Chmed23aJsonWriter.write(med))).as(fixture).isEqualTo(med);
    }
  }

  @Test
  void outputIsCompactUtf8InTheOrderOfTheExamples() {
    Medication med = read(Fixtures.bytes("chmed23a/official/Example2.json"));
    String json = new String(Chmed23aJsonWriter.write(med), StandardCharsets.UTF_8);
    assertThat(json).doesNotContain("\n").contains("½").startsWith("{\"patient\":{\"fName\":");
    assertThat(json.indexOf("\"meds\"")).isLessThan(json.indexOf("\"medType\""));
    assertThat(json.indexOf("\"dt\"")).isLessThan(json.indexOf("\"hcPerson\""));
    assertThat(json).contains("\"a\":1.0").doesNotContain("E+");
  }

  @Test
  void absentValuesAreOmittedAndDefaultsNotMaterialised() {
    Medication med = read(Fixtures.bytes("chmed23a/posology/Daily-1.json"));
    String json = new String(Chmed23aJsonWriter.write(med), StandardCharsets.UTF_8);
    assertThat(json).doesNotContain("null").doesNotContain("\"exts\"").doesNotContain("\"inRes\"");
  }

  @Test
  void equalModelsGiveEqualBytes() {
    Medication med = read(Fixtures.bytes("chmed23a/official/Example4.json"));
    assertThat(Chmed23aJsonWriter.write(med)).isEqualTo(Chmed23aJsonWriter.write(med));
  }
}
