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
        .contains("CHMED16A1");
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
