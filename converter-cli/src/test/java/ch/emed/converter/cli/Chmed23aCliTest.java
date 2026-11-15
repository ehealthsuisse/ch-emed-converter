package ch.emed.converter.cli;

import static ch.emed.converter.cli.CliTestSupport.run;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T063 (feature 006, US4): CHMED23A through the CLI (contracts/cli.md of 006). */
class Chmed23aCliTest {

  private static final Path OFFICIAL =
      Path.of(
          "..", "format-chmed23a", "src", "test", "resources", "fixtures", "chmed23a", "official");

  @TempDir Path tmp;

  private Path toChEmed(String example) throws Exception {
    Path out = tmp.resolve(example + ".chemed.json");
    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "chmed23a",
            "--to",
            "ch-emed",
            "--patient-id",
            "urn:oid:2.999.1|TEST-0001",
            OFFICIAL.resolve(example).toString(),
            "-o",
            out.toString());
    assertThat(result.exitCode()).as(result.stderr()).isZero();
    return out;
  }

  @Test
  void convertsPlansAndPrescriptionsInEveryOutputForm() throws Exception {
    for (String example : new String[] {"Example1.json", "Example4.json"}) {
      Path chEmed = toChEmed(example);
      for (String form : new String[] {"document", "qr-payload", "qr-image"}) {
        CliTestSupport.Run result =
            run(
                "convert",
                "--from",
                "ch-emed",
                "--to",
                "chmed23a",
                "--output-form",
                form,
                chEmed.toString());
        assertThat(result.exitCode()).as(example + " " + form + ": " + result.stderr()).isZero();
        if (form.equals("qr-payload")) {
          assertThat(result.stdoutText()).startsWith("CHMED23A.");
        }
      }
    }
  }

  @Test
  void readsAPayloadFileAndChunks() throws Exception {
    Path chEmed = toChEmed("Example3.json");
    String payload =
        run(
                "convert",
                "--from",
                "ch-emed",
                "--to",
                "chmed23a",
                "--output-form",
                "qr-payload",
                chEmed.toString())
            .stdoutText();
    String data = payload.substring("CHMED23A.".length());
    int half = data.length() / 2;
    Path chunks =
        Files.writeString(
            tmp.resolve("chunks.txt"),
            "CHMED23A.2/2." + data.substring(half) + "\nCHMED23A.1/2." + data.substring(0, half),
            StandardCharsets.US_ASCII);
    CliTestSupport.Run result =
        run("convert", "--from", "chmed23a", "--to", "ch-emed", chunks.toString());
    assertThat(result.exitCode()).as(result.stderr()).isZero();
  }

  @Test
  void invalidChunksExitWithOne() throws Exception {
    Path chunk =
        Files.writeString(
            tmp.resolve("chunk.txt"), "CHMED23A.1/2.H4sIAAAA", StandardCharsets.US_ASCII);
    CliTestSupport.Run result =
        run("convert", "--from", "chmed23a", "--to", "ch-emed", chunk.toString());
    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr()).contains("CHUNK_INVALID").doesNotContain("H4sI");
  }

  @Test
  void qrFormWithTargetChEmedIsAUsageError() {
    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "chmed23a",
            "--to",
            "ch-emed",
            "--output-form",
            "qr-image",
            OFFICIAL.resolve("Example1.json").toString());
    assertThat(result.exitCode()).isEqualTo(2);
  }

  @Test
  void helpNamesChmed23aForTheQrForms() throws Exception {
    // convert has no own --help; the description of the picocli option is the help text
    picocli.CommandLine.Option option =
        ConvertCommand.class
            .getDeclaredField("outputForm")
            .getAnnotation(picocli.CommandLine.Option.class);
    assertThat(String.join(" ", option.description())).contains("chmed16a or chmed23a");
  }
}
