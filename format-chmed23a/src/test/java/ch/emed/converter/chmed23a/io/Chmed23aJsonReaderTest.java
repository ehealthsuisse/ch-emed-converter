package ch.emed.converter.chmed23a.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed23a.model.DosageObject;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.chmed23a.model.PosologyDetail;
import ch.emed.converter.chmed23a.model.Repetition;
import ch.emed.converter.chmed23a.model.TimedDosage;
import ch.emed.converter.chmed23a.testsupport.Corpus;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T013: CHMED23A JSON reader, rules M23-IN-02, C23-OBJ-13, C23-OBJ-14, C23-POS-12. */
class Chmed23aJsonReaderTest {

  private final ReportCollector report = new ReportCollector();

  private Medication read(String json) {
    return Chmed23aJsonReader.read(json.getBytes(StandardCharsets.UTF_8), report);
  }

  private List<ReportEntry> entries(String code) {
    return report.entries().stream().filter(e -> e.code().equals(code)).toList();
  }

  @Test
  void everyFixtureOfTheCorpusIsRead() {
    for (String fixture : Corpus.conforming()) {
      ReportCollector r = new ReportCollector();
      assertThat(Chmed23aJsonReader.read(Fixtures.bytes(fixture), r)).as(fixture).isNotNull();
      assertThat(r.hasErrors()).as(fixture + ": " + r.entries()).isFalse();
    }
  }

  @Test
  void officialExample4IsReadCompletely() {
    Medication med =
        Chmed23aJsonReader.read(Fixtures.bytes("chmed23a/official/Example4.json"), report);
    assertThat(med.isPrescription()).isTrue();
    assertThat(med.meds()).hasSize(4);
    assertThat(med.meds().get(0).pos()).hasSize(4);
    assertThat(med.meds().get(0).reps()).isEqualTo(new Repetition.Duration(6, 6));
    assertThat(med.meds().get(1).pos().get(0).po())
        .isInstanceOfSatisfying(
            PosologyDetail.Cyclic.class,
            c ->
                assertThat(c.tdo())
                    .isInstanceOfSatisfying(
                        TimedDosage.DaySegments.class,
                        s ->
                            assertThat(s.ss().get(0).dose())
                                .isEqualTo(
                                    new DosageObject.Range(
                                        new BigDecimal("1.0"), new BigDecimal("2.0")))));
    assertThat(med.hcOrg().zsr()).isEqualTo("XX.1254");
  }

  @Test
  void nonAsciiTextIsKept() {
    Medication med =
        Chmed23aJsonReader.read(Fixtures.bytes("chmed23a/official/Example2.json"), report);
    assertThat(((PosologyDetail.FreeText) med.meds().get(5).pos().get(0).po()).text())
        .contains("½");
  }

  @Test
  void unknownPropertiesAreInformation() {
    read("{\"medType\":1,\"secret\":\"x\",\"patient\":{\"extra\":1}}");
    assertThat(entries(ReasonCode.UNKNOWN_FIELD_IGNORED))
        .extracting(ReportEntry::path)
        .containsExactlyInAnyOrder("/secret", "/patient/extra");
    assertThat(report.hasErrors()).isFalse();
  }

  @Test
  void wrongTypesAreInvalidWithoutValues() {
    read("{\"medType\":\"one\",\"patient\":{\"fName\":42,\"ids\":{}},\"dt\":true}");
    assertThat(entries(ReasonCode.CHMED_INVALID_FIELD))
        .extracting(ReportEntry::path)
        .containsExactlyInAnyOrder("/medType", "/patient/fName", "/patient/ids", "/dt");
    assertThat(report.entries()).allSatisfy(e -> assertThat(e.message()).doesNotContain("42"));
  }

  @Test
  void unknownOrMissingObjectTypeIsInvalid() {
    read("{\"medType\":1,\"meds\":[{\"pos\":[{\"po\":{\"t\":9}},{\"po\":{\"ds\":[1,0,0,0]}}]}]}");
    assertThat(entries(ReasonCode.CHMED_INVALID_FIELD))
        .extracting(ReportEntry::path)
        .containsExactlyInAnyOrder("/meds/0/pos/0/po/t", "/meds/0/pos/1/po/t");
  }

  @Test
  void integersWithADecimalPartAreRounded() {
    Medication med = read("{\"medType\":1.0,\"auth\":1.6}");
    assertThat(med.medType()).isEqualTo(1);
    assertThat(med.auth()).isEqualTo(2);
    assertThat(entries(ReasonCode.DERIVED_VALUE))
        .extracting(ReportEntry::path)
        .containsExactly("/auth");
  }

  @Test
  void invalidJsonIsParseErrorWithoutContent() {
    assertThat(read("{\"patient\": {\"fName\": \"Secret\"")).isNull();
    assertThat(report.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.PARSE_ERROR);
              assertThat(e.severity()).isEqualTo(Severity.ERROR);
              assertThat(e.message()).doesNotContain("Secret");
            });
  }

  @Test
  void draftPropertyNamesOfPosologyMdAreUnknown() {
    for (String fixture : Corpus.DRAFT_NAMES) {
      ReportCollector r = new ReportCollector();
      Chmed23aJsonReader.read(Fixtures.bytes(fixture), r);
      assertThat(r.entries())
          .as(fixture)
          .anySatisfy(e -> assertThat(e.code()).isEqualTo(ReasonCode.UNKNOWN_FIELD_IGNORED));
    }
  }
}
