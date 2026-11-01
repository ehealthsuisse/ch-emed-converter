package ch.emed.converter.cli;

import static ch.emed.converter.cli.CliTestSupport.resource;
import static ch.emed.converter.cli.CliTestSupport.run;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** contracts/cli.md for US1, US2 and US4. */
class ConvertCommandTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TempDir Path tmp;

  private String[] convertArgs(String... extra) {
    String[] base = {
      "convert",
      "--from",
      "chmed16a",
      "--to",
      "ch-emed",
      "--author",
      resource("author.json").toString()
    };
    String[] all = new String[base.length + extra.length];
    System.arraycopy(base, 0, all, 0, base.length);
    System.arraycopy(extra, 0, all, base.length, extra.length);
    return all;
  }

  @Test
  void convertsFileAndWritesOnlyTheDocumentToStdout() throws Exception {
    CliTestSupport.Run result = run(convertArgs(resource("plan-basic.json").toString()));

    assertThat(result.exitCode()).isEqualTo(0);
    JsonNode doc = MAPPER.readTree(result.stdout());
    assertThat(doc.get("resourceType").asText()).isEqualTo("Bundle");
    assertThat(result.stderr()).doesNotContain("Muster").doesNotContain("1950-03-21");
  }

  @Test
  void readsStdinAndWritesSchemaValidReport() throws Exception {
    Path report = tmp.resolve("report.json");
    Path output = tmp.resolve("out.json");

    CliTestSupport.Run result =
        run(
            Files.readAllBytes(resource("plan-basic.json")),
            convertArgs("-o", output.toString(), "--report", report.toString(), "-"));

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).isEmpty();
    assertThat(MAPPER.readTree(output.toFile()).get("type").asText()).isEqualTo("document");
    try (InputStream schema =
        getClass().getResourceAsStream("/schemas/conversion-report.schema.json")) {
      assertThat(
              SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                  .getSchema(schema)
                  .validate(MAPPER.readTree(report.toFile())))
          .isEmpty();
    }
  }

  @Test
  void invalidInputExitsOneWithNothingOnStdout() {
    CliTestSupport.Run result = run(convertArgs(resource("invalid-json.txt").toString()));

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stdout()).isEmpty();
    assertThat(result.stderr()).contains("PARSE_ERROR");
  }

  @Test
  void missingAuthorExitsOne() {
    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "chmed16a",
            "--to",
            "ch-emed",
            resource("plan-basic.json").toString());

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr()).contains("MISSING_REQUIRED_DATA");
  }

  @Test
  void jsonReportOnStderr() throws Exception {
    CliTestSupport.Run result =
        run(convertArgs("--report-format", "json", resource("invalid-json.txt").toString()));

    assertThat(MAPPER.readTree(result.stderr()).get("outcome").asText()).isEqualTo("FAILED");
  }

  @Test
  void usageErrorsExitTwo() {
    assertThat(run("convert", "--from", "chmed16a").exitCode()).isEqualTo(2);
    assertThat(run("convert", "--from", "unknown-format", "--to", "ch-emed", "x").exitCode())
        .isEqualTo(2);
    assertThat(run("convert", "--bogus").exitCode()).isEqualTo(2);
    assertThat(
            run(convertArgs("--language", "xx-XX", resource("plan-basic.json").toString()))
                .exitCode())
        .isEqualTo(2);
  }

  @Test
  void patientIdOptionIsParsed() {
    CliTestSupport.Run result =
        run(
            convertArgs(
                "--patient-id", "urn:oid:2.999.1|TEST-1", resource("plan-basic.json").toString()));

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(
            run(convertArgs("--patient-id", "no-separator", resource("plan-basic.json").toString()))
                .exitCode())
        .isEqualTo(2);
  }

  @Test
  void convertsChEmedFromStdinToChmed16a() throws Exception {
    CliTestSupport.Run result =
        run(
            Files.readAllBytes(resource("medication-card.json")),
            "convert",
            "--from",
            "ch-emed",
            "--to",
            "chmed16a",
            "-");

    assertThat(result.exitCode()).isEqualTo(0);
    JsonNode doc = MAPPER.readTree(result.stdout());
    assertThat(doc.get("MedType").asInt()).isEqualTo(1);
    assertThat(doc.get("Id").asText()).isEqualTo("b4bbd9ed-bada-41b5-a513-b4bc73ca0ebb");
    assertThat(doc.get("Medicaments")).hasSize(2);
    assertThat(result.stderr()).doesNotContain("Wegmüller").doesNotContain("1943-05-15");
  }

  @Test
  void invalidChEmedInputExitsOneWithNothingOnStdout() {
    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "ch-emed",
            "--to",
            "chmed16a",
            resource("invalid-json.txt").toString());

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stdout()).isEmpty();
    assertThat(result.stderr()).contains("PARSE_ERROR");
  }

  @Test
  void strictModeExitsOneWithEmptyStdoutOnClinicalLoss() {
    CliTestSupport.Run result =
        run(convertArgs("--strict", resource("plan-unmappable-element.json").toString()));

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stdout()).isEmpty();
    assertThat(result.stderr()).contains("FAILED").contains("/Medicaments/1/Roa");
    assertThat(result.stderr()).contains("strict mode");
  }

  @Test
  void withoutStrictModeTheSameInputExitsZero() throws Exception {
    CliTestSupport.Run result =
        run(convertArgs(resource("plan-unmappable-element.json").toString()));

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(MAPPER.readTree(result.stdout()).get("resourceType").asText()).isEqualTo("Bundle");
    assertThat(result.stderr()).contains("SUCCESS_WITH_WARNINGS").contains("[clinical loss]");
  }

  @Test
  void strictModeDoesNotAffectInputWithoutClinicalLoss() {
    CliTestSupport.Run result =
        run(convertArgs("--strict", resource("plan-basic.json").toString()));

    assertThat(result.exitCode()).isEqualTo(0);
  }
}
