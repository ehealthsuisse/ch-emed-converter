package ch.emed.converter.cli;

import static ch.emed.converter.cli.CliTestSupport.run;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FormatsCommandTest {

  @Test
  void listsPairsAndPinnedVersions() {
    CliTestSupport.Run result = run("formats");

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdoutText())
        .contains("chmed16a -> ch-emed")
        .contains("ch-emed -> chmed16a")
        .contains("ch.fhir.ig.ch-emed#6.0.0")
        .contains("CHMED16A1")
        .contains("ch-emed-epr -> ch-emed")
        .contains("ch-emed -> ch-emed-epr")
        .contains("ch.fhir.ig.ch-emed-epr#3.0.0")
        .contains("treatment-plans")
        .contains("document types PMLC, PRE, MTP, DIS, PADV, PML");
  }

  /** T062 (feature 006): CHMED23A with its forms and the deprecated mark; others unmarked. */
  @Test
  void listsChmed23aAsDeprecated() {
    String out = run("formats").stdoutText();

    assertThat(out)
        .contains("chmed23a -> ch-emed")
        .contains("ch-emed -> chmed23a")
        .contains(
            "chmed23a: input json, qr-payload, qr-image; output document, qr-payload, qr-image;"
                + " document types MP, Rx; deprecated by IG eMediplan")
        .contains("CHMED23A (ig-emediplan/specification@0b579aa6)");
    assertThat(out.lines().filter(l -> l.contains("deprecated"))).hasSize(1);
  }

  @Test
  void versionPrintsConverterAndSpecificationVersions() {
    CliTestSupport.Run result = run("--version");

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdoutText())
        .contains("ch-emed-converter")
        .contains("ch.fhir.ig.ch-emed#6.0.0");
  }
}
