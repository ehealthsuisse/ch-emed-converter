package ch.emed.converter.chmed16a.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.chmed16a.model.Medicament;
import ch.emed.converter.chmed16a.model.Medication;
import ch.emed.converter.chmed16a.model.Patient;
import ch.emed.converter.chmed16a.model.Posology;
import ch.emed.converter.chmed16a.model.TakingTime;
import ch.emed.converter.chmed16a.testsupport.Fixtures;
import ch.emed.converter.chmed16a.validation.Chmed16aValidator;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.report.ReportCollector;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class Chmed16aJsonWriterTest {

  private static Medication read(String fixture) {
    ReportCollector report = new ReportCollector();
    Medication med =
        Chmed16aJsonReader.read(
            Fixtures.bytes("chmed16a/" + fixture), ConversionOptions.defaults(), report);
    assertThat(report.hasErrors()).isFalse();
    return med;
  }

  private static String write(Medication med) {
    return new String(Chmed16aJsonWriter.write(med), StandardCharsets.UTF_8);
  }

  @Test
  void propertiesFollowTheOrderOfTheSpecificationTables() {
    String json = write(read("plan-basic.json"));

    assertThat(json)
        .startsWith(
            "{\"Patient\":{\"FName\":\"Hans\",\"LName\":\"Muster\",\"BDt\":\"1950-03-21\","
                + "\"Gender\":1,\"Street\":\"Testweg 1\",\"Zip\":\"3000\",\"City\":\"Bern\","
                + "\"Lng\":\"de\",\"Phone\":\"+41 31 000 00 00\","
                + "\"Ids\":[{\"Type\":1,\"Val\":\"80756000000000000001\"}]},"
                + "\"Medicaments\":[{\"Id\":\"7680521101306\",\"IdType\":2,"
                + "\"Pos\":[{\"DtFrom\":\"2026-10-01\",\"D\":[1,0,0.5,0]}],"
                + "\"Unit\":\"STK\",\"TkgRsn\":\"Bluthochdruck\","
                + "\"AppInstr\":\"Mit Wasser einnehmen\",\"AutoMed\":0,\"Roa\":\"PO\"}")
        .endsWith(
            "\"MedType\":1,\"Id\":\"0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e01\","
                + "\"Auth\":\"7601000000001\",\"Dt\":\"2026-10-01T09:30:00+02:00\"}");
  }

  @Test
  void defaultsAreNotMaterialised() {
    Medicament medicament =
        new Medicament(
            "7680521101306",
            2,
            List.of(
                new Posology("2026-10-01", null, null, null, List.of(BigDecimal.ONE), List.of())),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    Medication med =
        new Medication(
            new Patient(
                "A",
                "B",
                "1950-01-01",
                1,
                null,
                null,
                null,
                "de",
                null,
                null,
                null,
                List.of(),
                null,
                List.of()),
            List.of(medicament),
            false,
            1,
            "0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e01",
            "7601000000001",
            null,
            "2026-10-01T09:30:00+02:00",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of());

    String json = write(med);

    assertThat(json)
        .doesNotContain("CyDu")
        .doesNotContain("NbPack")
        .doesNotContain("AutoMed")
        .doesNotContain("InRes")
        .doesNotContain("Recoms")
        .doesNotContain("\"rev\"");
  }

  @Test
  void explicitValuesArePreserved() {
    String json = write(read("plan-cycle.json"));

    assertThat(json).contains("\"CyDu\":");
    assertThat(write(read("plan-reserve.json"))).contains("\"InRes\":1");
  }

  @Test
  void numbersAreWrittenWithoutTrailingZerosOrExponent() {
    Posology pos =
        new Posology(
            "2026-10-01",
            null,
            null,
            null,
            List.of(
                new BigDecimal("1.50"),
                new BigDecimal("10"),
                new BigDecimal("0.0"),
                new BigDecimal("1E+1")),
            List.of(new TakingTime(3600L, null, new BigDecimal("2.0"), null, null, null)));
    Medication med = withPosology(pos);

    assertThat(write(med)).contains("\"D\":[1.5,10,0,10]").contains("\"DoFrom\":2");
  }

  @Test
  void nonAsciiTextIsWrittenAsUtf8NotEscaped() {
    byte[] bytes = Chmed16aJsonWriter.write(read("plan-non-ascii.json"));
    String json = new String(bytes, StandardCharsets.UTF_8);

    assertThat(json).doesNotContain("\\u").containsPattern("[äöüéèàçÉ]");
    assertThat(read(bytes))
        .usingRecursiveComparison()
        .withComparatorForType(Comparator.<BigDecimal>naturalOrder(), BigDecimal.class)
        .isEqualTo(read("plan-non-ascii.json"));
  }

  @Test
  void anEmptyMedicamentListIsStillWrittenAndEmptyOptionalListsAreOmitted() {
    String json = write(read("plan-empty-meds.json"));

    assertThat(json)
        .contains("\"Medicaments\":[]")
        .doesNotContain("\"Ids\":[]")
        .doesNotContain("\"Pos\":[]");
  }

  @Test
  void writtenPlansPassTheChmed16aValidatorAndReadBackEqual() {
    for (String fixture :
        List.of(
            "plan-basic.json",
            "plan-taking-times.json",
            "plan-medical-data.json",
            "plan-reserve.json")) {
      Medication original = read(fixture);

      Medication again = read(Chmed16aJsonWriter.write(original));

      assertThat(again)
          .as(fixture)
          .usingRecursiveComparison()
          .withComparatorForType(Comparator.<BigDecimal>naturalOrder(), BigDecimal.class)
          .isEqualTo(original);
      ReportCollector report = new ReportCollector();
      Chmed16aValidator.validate(again, report);
      assertThat(report.hasErrors()).as(fixture).isFalse();
    }
  }

  @Test
  void outputIsByteDeterministic() {
    Medication med = read("plan-basic.json");

    assertThat(Chmed16aJsonWriter.write(med)).isEqualTo(Chmed16aJsonWriter.write(med));
  }

  private static Medication read(byte[] bytes) {
    ReportCollector report = new ReportCollector();
    Medication med = Chmed16aJsonReader.read(bytes, ConversionOptions.defaults(), report);
    assertThat(report.hasErrors()).isFalse();
    return med;
  }

  private static Medication withPosology(Posology pos) {
    Medicament medicament =
        new Medicament(
            "7680521101306",
            2,
            List.of(pos),
            "STK",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of());
    return new Medication(
        new Patient(
            "A",
            "B",
            "1950-01-01",
            1,
            null,
            null,
            null,
            "de",
            null,
            null,
            null,
            List.of(),
            null,
            List.of()),
        List.of(medicament),
        false,
        1,
        "0c7f0f7e-3b1c-4c1e-9a55-1a2b3c4d5e01",
        "7601000000001",
        null,
        "2026-10-01T09:30:00+02:00",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of());
  }
}
