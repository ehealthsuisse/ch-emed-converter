package ch.emed.converter.chmed23a.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed23a.io.Chmed23aJsonReader;
import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.chmed23a.testsupport.Corpus;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** T016: conformance rules C23-* (research R7). */
class Chmed23aValidatorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static List<ReportEntry> validate(byte[] json) {
    ReportCollector report = new ReportCollector();
    Medication med = Chmed23aJsonReader.read(json, report);
    if (med != null) {
      Chmed23aValidator.validate(med, report);
    }
    return report.entries();
  }

  /** Example 1 (plan) or 4 (prescription), changed by {@code change}. */
  private static List<ReportEntry> validate(String example, Consumer<ObjectNode> change)
      throws Exception {
    ObjectNode root =
        (ObjectNode) MAPPER.readTree(Fixtures.bytes("chmed23a/official/" + example + ".json"));
    change.accept(root);
    return validate(MAPPER.writeValueAsBytes(root));
  }

  private static List<String> errors(List<ReportEntry> entries) {
    return entries.stream()
        .filter(e -> e.severity() == Severity.ERROR)
        .map(e -> e.code() + " " + e.path())
        .toList();
  }

  private static ObjectNode at(ObjectNode root, String pointer) {
    return (ObjectNode) root.at(pointer);
  }

  @Test
  void theConformingCorpusHasNoErrors() {
    for (String fixture : Corpus.conforming()) {
      assertThat(errors(validate(Fixtures.bytes(fixture)))).as(fixture).isEmpty();
    }
  }

  @Test
  void draftPropertyNamesAreRefused() {
    for (String fixture : Corpus.DRAFT_NAMES) {
      assertThat(errors(validate(Fixtures.bytes(fixture)))).as(fixture).isNotEmpty();
    }
  }

  @Test
  void usageDashFieldsAreInformation() throws Exception {
    List<ReportEntry> plan = validate(Fixtures.bytes("chmed23a/official/Example1.json"));
    assertThat(plan)
        .filteredOn(e -> e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT))
        .extracting(ReportEntry::path)
        .contains("/meds/0/nbPack");
    List<ReportEntry> rx = validate(Fixtures.bytes("chmed23a/official/Example4.json"));
    assertThat(rx)
        .filteredOn(e -> e.code().equals(ReasonCode.UNMAPPABLE_ELEMENT))
        .extracting(ReportEntry::path)
        .contains("/meds/0/autoMed", "/meds/0/prscbBy", "/patient/mData", "/patient/lng");
  }

  @Test
  void documentLevel() throws Exception {
    assertThat(errors(validate("Example1", r -> r.put("medType", 2))))
        .containsExactly("UNSUPPORTED_DOCUMENT_TYPE /medType");
    assertThat(errors(validate("Example1", r -> r.remove("dt"))))
        .containsExactly("CHMED_INVALID_FIELD /dt");
    assertThat(errors(validate("Example1", r -> r.put("dt", "2024-01-09T09:14:36"))))
        .containsExactly("CHMED_INVALID_FIELD /dt");
    assertThat(errors(validate("Example1", r -> r.remove("hcOrg"))))
        .containsExactly("CHMED_INVALID_FIELD /hcOrg");
    assertThat(errors(validate("Example4", r -> r.put("auth", 2))))
        .containsExactly("CHMED_INVALID_FIELD /auth");
    assertThat(errors(validate("Example4", r -> r.putArray("meds"))))
        .containsExactly("CHMED_INVALID_FIELD /meds");
  }

  @Test
  void patient() throws Exception {
    assertThat(errors(validate("Example1", r -> at(r, "/patient").put("gender", 4))))
        .containsExactly("CHMED_INVALID_FIELD /patient/gender");
    assertThat(errors(validate("Example1", r -> at(r, "/patient").put("bdt", "1945-02-30"))))
        .containsExactly("CHMED_INVALID_FIELD /patient/bdt");
    assertThat(errors(validate("Example1", r -> at(r, "/patient").remove("lng"))))
        .containsExactly("CHMED_INVALID_FIELD /patient/lng");
    assertThat(errors(validate("Example1", r -> at(r, "/patient").putArray("ids"))))
        .containsExactly("CHMED_INVALID_FIELD /patient/ids");
    assertThat(errors(validate("Example3", r -> at(r, "/patient/ids/0").remove("sId"))))
        .containsExactly("CHMED_INVALID_FIELD /patient/ids/0/sId");
    assertThat(errors(validate("Example1", r -> at(r, "/patient/mData").put("toG", "30"))))
        .containsExactly("CHMED_INVALID_FIELD /patient/mData/toG");
  }

  @Test
  void riskOfAnotherCategoryIsAWarning() throws Exception {
    List<ReportEntry> entries =
        validate("Example1", r -> ((ArrayNode) r.at("/patient/mData/rcs/0/rIds")).set(0, 780));
    assertThat(errors(entries)).isEmpty();
    assertThat(entries)
        .anySatisfy(
            e -> {
              assertThat(e.severity()).isEqualTo(Severity.WARNING);
              assertThat(e.path()).isEqualTo("/patient/mData/rcs/0/rIds/0");
            });
  }

  @Test
  void author() throws Exception {
    assertThat(errors(validate("Example4", r -> at(r, "/hcPerson").remove("gln"))))
        .containsExactly("CHMED_INVALID_FIELD /hcPerson/gln");
    assertThat(errors(validate("Example2", r -> at(r, "/hcOrg").remove("gln"))))
        .containsExactly("CHMED_INVALID_FIELD /hcOrg/gln");
    assertThat(errors(validate("Example4", r -> at(r, "/hcPerson").put("zsr", "Z1"))))
        .containsExactly("CHMED_INVALID_FIELD /hcOrg/zsr");
    assertThat(errors(validate("Example1", r -> at(r, "/hcOrg").remove("street"))))
        .containsExactly("CHMED_INVALID_FIELD /hcOrg/street");
  }

  @Test
  void medicament() throws Exception {
    assertThat(errors(validate("Example4", r -> at(r, "/meds/0").put("idType", 4))))
        .containsExactly("CHMED_INVALID_FIELD /meds/0/idType");
    assertThat(errors(validate("Example1", r -> at(r, "/meds/0").remove("autoMed"))))
        .containsExactly("CHMED_INVALID_FIELD /meds/0/autoMed");
    assertThat(errors(validate("Example4", r -> at(r, "/meds/0/reps").put("t", 1).put("v", -1))))
        .containsExactly("CHMED_INVALID_FIELD /meds/0/reps/v");
  }

  @Test
  void posology() throws Exception {
    assertThat(errors(validate("Example1", r -> at(r, "/meds/0/pos/0").remove("unit"))))
        .containsExactly("CHMED_INVALID_FIELD /meds/0/pos/0/unit");
    assertThat(
            errors(validate("Example1", r -> ((ArrayNode) r.at("/meds/0/pos/0/po/ds")).remove(3))))
        .containsExactly("CHMED_INVALID_FIELD /meds/0/pos/0/po/ds");
    assertThat(
            errors(validate("Example1", r -> ((ArrayNode) r.at("/meds/0/pos/0/po/ds")).set(1, -1))))
        .containsExactly("CHMED_INVALID_FIELD /meds/0/pos/0/po/ds/1");
    assertThat(errors(validate("Example1", r -> at(r, "/meds/2/pos/0").put("dtTo", "2023-01-01"))))
        .containsExactly("CHMED_INVALID_FIELD /meds/2/pos/0/dtTo");
    assertThat(errors(validate("Example1", r -> at(r, "/meds/0/pos/0").put("relMeal", 8))))
        .containsExactly("CHMED_INVALID_FIELD /meds/0/pos/0/relMeal");
  }

  @Test
  void posologyObjects() throws Exception {
    assertThat(errors(validate("Example2", r -> at(r, "/meds/0/pos/0/po").put("cyDuU", 4))))
        .containsExactly("CHMED_INVALID_FIELD /meds/0/pos/0/po/tdo/t");
    assertThat(errors(validate("Example2", r -> at(r, "/meds/0/pos/0/po").put("cyDu", 0))))
        .containsExactly("CHMED_INVALID_FIELD /meds/0/pos/0/po/cyDu");
    assertThat(
            errors(
                validate("Example2", r -> ((ArrayNode) r.at("/meds/0/pos/0/po/tdo/wds")).add(2))))
        .containsExactly("CHMED_INVALID_FIELD /meds/0/pos/0/po/tdo/wds/2");
    assertThat(
            errors(
                validate(
                    "Example2", r -> ((ArrayNode) r.at("/meds/6/pos/0/po/tdo/doms")).set(0, 29))))
        .containsExactly("CHMED_INVALID_FIELD /meds/6/pos/0/po/tdo/doms/0");
    assertThat(
            errors(
                validate("Example2", r -> at(r, "/meds/3/pos/0/po/tdo/ts/0").put("dt", "24:00"))))
        .containsExactly("CHMED_INVALID_FIELD /meds/3/pos/0/po/tdo/ts/0/dt");
    assertThat(
            errors(validate("Example4", r -> at(r, "/meds/1/pos/0/po/tdo/ss/0/do").put("aMax", 1))))
        .containsExactly("CHMED_INVALID_FIELD /meds/1/pos/0/po/tdo/ss/0/do/aMax");
    assertThat(errors(validate("Example3", r -> at(r, "/meds/1/pos/0/po/tdo").put("miDu", 0))))
        .containsExactly("CHMED_INVALID_FIELD /meds/1/pos/0/po/tdo/miDu");
    assertThat(
            errors(
                validate(
                    "Example3",
                    r -> {
                      JsonNode inner = r.at("/meds/0/pos/0/po").deepCopy();
                      at(r, "/meds/0/pos/0/po/sos/0").set("po", inner);
                    })))
        .contains("CHMED_INVALID_FIELD /meds/0/pos/0/po/sos/0/po/t");
  }

  @Test
  void codesOutsideTheTerminologyAreWarnings() throws Exception {
    List<ReportEntry> entries =
        validate(
            "Example1",
            r -> at(r, "/meds/1/pos/0").put("unit", "TABL").put("roa", "1").put("moa", "99"));
    assertThat(errors(entries)).isEmpty();
    assertThat(entries)
        .filteredOn(e -> e.severity() == Severity.WARNING)
        .extracting(ReportEntry::path)
        .contains("/meds/1/pos/0/unit", "/meds/1/pos/0/roa", "/meds/1/pos/0/moa");
  }

  /** C23-OBJ-15: GLN and GTIN check digits are warnings; the value is carried (finding F7). */
  @Test
  void glnAndGtinCheckDigitsAreWarnings() throws Exception {
    List<ReportEntry> example1 = validate(Fixtures.bytes("chmed23a/official/Example1.json"));
    assertThat(errors(example1)).isEmpty();
    assertThat(example1)
        .filteredOn(e -> e.severity() == Severity.WARNING && e.message().contains("GS1"))
        .extracting(ReportEntry::path)
        .containsExactly("/hcPerson/gln");

    List<ReportEntry> valid =
        validate(
            "Example4",
            r -> {
              at(r, "/hcPerson").put("gln", "7601000000002");
              at(r, "/hcOrg").remove("gln");
              r.put("rec", "7601001362383");
            });
    assertThat(valid).noneMatch(e -> e.message().contains("GS1"));

    List<ReportEntry> invalid =
        validate(
            "Example4",
            r -> {
              r.put("rec", "7601001362384");
              at(r, "/meds/0").put("id", "7680388400377");
            });
    assertThat(errors(invalid)).isEmpty();
    assertThat(invalid)
        .filteredOn(e -> e.severity() == Severity.WARNING && e.message().contains("GS1"))
        .extracting(ReportEntry::path)
        .containsExactlyInAnyOrder("/rec", "/hcPerson/gln", "/hcOrg/gln", "/meds/0/id");
    assertThat(invalid).allSatisfy(e -> assertThat(e.message()).doesNotContain("7601001362384"));
  }

  @Test
  void gs1CheckDigit() {
    assertThat(Chmed23aValidator.validGs1("7601001362383")).isTrue();
    assertThat(Chmed23aValidator.validGs1("7680334810013")).isTrue();
    assertThat(Chmed23aValidator.validGs1("7601001362384")).isFalse();
    assertThat(Chmed23aValidator.validGs1("123123123123")).isFalse();
    assertThat(Chmed23aValidator.validGs1("760100136238X")).isFalse();
  }

  @Test
  void messagesNeverContainValues() throws Exception {
    List<ReportEntry> entries =
        validate("Example1", r -> at(r, "/patient").put("bdt", "Louloui-1945"));
    assertThat(entries).allSatisfy(e -> assertThat(e.message()).doesNotContain("Louloui"));
  }
}
