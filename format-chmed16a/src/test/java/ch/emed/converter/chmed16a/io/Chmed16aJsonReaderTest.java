package ch.emed.converter.chmed16a.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class Chmed16aJsonReaderTest {

  private final ReportCollector report = new ReportCollector();

  private Medication read(String fixture) {
    return Chmed16aJsonReader.read(
        Fixtures.bytes("chmed16a/" + fixture), ConversionOptions.defaults(), report);
  }

  private List<ReportEntry> errors() {
    return report.entries().stream().filter(e -> e.severity() == Severity.ERROR).toList();
  }

  @Test
  void parsesValidPlan() {
    Medication med = read("plan-basic.json");

    assertThat(errors()).isEmpty();
    assertThat(med.medType()).isEqualTo(1);
    assertThat(med.id()).isEqualTo("0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e01");
    assertThat(med.patient().lName()).isEqualTo("Muster");
    assertThat(med.medicaments()).hasSize(2);
    assertThat(med.medicaments().get(0).pos().get(0).d()).hasSize(4);
    assertThat(med.medicaments().get(0).pos().get(0).d().get(2)).isEqualByComparingTo("0.5");
  }

  @Test
  void invalidJsonIsParseError() {
    assertThat(read("invalid-json.txt")).isNull();
    assertThat(errors())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.PARSE_ERROR);
              assertThat(e.phase()).isEqualTo(Phase.PARSE);
            });
  }

  @Test
  void chmed23aIsUnsupportedVersion() {
    assertThat(read("chmed23a-plan.json")).isNull();
    assertThat(errors())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNSUPPORTED_VERSION);
              assertThat(e.message()).contains("CHMED23A");
            });
  }

  @Test
  void qrPayloadParsesToSameModelAsJson() {
    Medication fromJson = read("plan-basic.json");
    Medication fromPayload = read("plan-basic.qr.txt");

    assertThat(errors()).isEmpty();
    assertThat(fromPayload).isEqualTo(fromJson);
  }

  @Test
  void corruptPayloadsAreParseErrorsWithoutEchoingThePayload() {
    for (String fixture : List.of("qr-corrupt-base64.txt", "qr-corrupt-gzip.txt")) {
      ReportCollector own = new ReportCollector();
      String payload = Fixtures.text("chmed16a/" + fixture).trim();

      Medication med =
          Chmed16aJsonReader.read(
              Fixtures.bytes("chmed16a/" + fixture), ConversionOptions.defaults(), own);

      assertThat(med).isNull();
      assertThat(own.entries())
          .singleElement()
          .satisfies(
              e -> {
                assertThat(e.code()).isEqualTo(ReasonCode.PARSE_ERROR);
                assertThat(e.message()).doesNotContain(payload.substring(9));
              });
    }
  }

  @Test
  void oversizedPayloadIsParseError() {
    assertThat(read("qr-oversized.txt")).isNull();
    assertThat(errors())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.PARSE_ERROR);
              assertThat(e.message()).containsIgnoringCase("size");
            });
  }

  @Test
  void erezeptUrlPointsToQrFeature() {
    assertThat(read("erezept-url.txt")).isNull();
    assertThat(errors())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.PARSE_ERROR);
              assertThat(e.message()).contains("eRezept").doesNotContain("s=");
            });
  }

  @Test
  void unknownFieldsAndPrivateFieldsAreReportedAsInformation() {
    Medication med = read("plan-unknown-fields.json");

    assertThat(med).isNotNull();
    assertThat(errors()).isEmpty();
    assertThat(report.entries())
        .filteredOn(e -> e.code().equals(ReasonCode.UNKNOWN_FIELD_IGNORED))
        .extracting(ReportEntry::path)
        .contains("/FutureField", "/PFields", "/Medicaments/0/NewMedField");
    assertThat(report.entries())
        .filteredOn(e -> e.code().equals(ReasonCode.UNKNOWN_FIELD_IGNORED))
        .allMatch(e -> e.severity() == Severity.INFORMATION);
  }

  @Test
  void wrongTypeIsInvalidField() {
    Medication med =
        Chmed16aJsonReader.read(
            "{\"Id\":\"x\",\"MedType\":\"one\",\"Patient\":{}}".getBytes(),
            ConversionOptions.defaults(),
            report);

    assertThat(med).isNull();
    assertThat(errors())
        .anySatisfy(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.CHMED_INVALID_FIELD);
              assertThat(e.path()).isEqualTo("/MedType");
            });
  }
}
