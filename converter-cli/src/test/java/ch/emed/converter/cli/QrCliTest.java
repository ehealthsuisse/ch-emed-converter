package ch.emed.converter.cli;

import static ch.emed.converter.cli.CliTestSupport.resource;
import static ch.emed.converter.cli.CliTestSupport.run;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.api.AuthorDetails;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.OrganizationDetails;
import ch.emed.converter.core.api.OutputForm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T046 (feature 003, US5): QR and eRezept forms through the CLI (contracts/cli.md of 003). */
class QrCliTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String[] SIGNATURE_VALUES = {"Erika", "1767225600", "3066023100"};

  @TempDir Path tmp;

  private static ConversionResult library(Path input, FormatId from, FormatId to, OutputForm form)
      throws Exception {
    ConversionOptions options = ConversionOptions.defaults().withOutputForm(form);
    if (from.equals(FormatId.CHMED16A)) {
      options =
          options.withAuthor(
              new AuthorDetails(
                  null,
                  "Thea",
                  "Test",
                  new OrganizationDetails("Praxis Test", "Testgasse 1", "3000", "Bern", "CH")));
    }
    return CliTestSupport.converter()
        .convert(new ConversionRequest(from, to, Files.readAllBytes(input), options));
  }

  @Test
  void writesThePayloadAndThePngImageOfTheLibrary() throws Exception {
    Path card = resource("medication-card.json");
    for (OutputForm form : new OutputForm[] {OutputForm.QR_PAYLOAD, OutputForm.QR_IMAGE}) {
      CliTestSupport.Run result =
          run(
              "convert",
              "--from",
              "ch-emed",
              "--to",
              "chmed16a",
              "--output-form",
              form.externalName(),
              card.toString());

      assertThat(result.exitCode()).as(form.externalName()).isZero();
      assertThat(result.stdout())
          .isEqualTo(
              library(card, FormatId.CH_EMED, FormatId.CHMED16A, form).output().orElseThrow());
    }
  }

  @Test
  void qrFormsForTargetChEmedAreAUsageError() {
    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "chmed16a",
            "--to",
            "ch-emed",
            "--output-form",
            "qr-image",
            resource("plan-basic.json").toString());

    assertThat(result.exitCode()).isEqualTo(2);
    assertThat(result.stdout()).isEmpty();
  }

  @Test
  void unknownOutputFormIsAUsageError() {
    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "ch-emed",
            "--to",
            "chmed16a",
            "--output-form",
            "pdf",
            resource("medication-card.json").toString());

    assertThat(result.exitCode()).isEqualTo(2);
  }

  @Test
  void readsAQrImage() throws Exception {
    Path image = resource("plan-basic.png");
    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "chmed16a",
            "--to",
            "ch-emed",
            "--author",
            resource("author.json").toString(),
            image.toString());

    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout())
        .isEqualTo(
            library(image, FormatId.CHMED16A, FormatId.CH_EMED, OutputForm.DOCUMENT)
                .output()
                .orElseThrow());
  }

  @Test
  void signedErezeptWritesTheSignatureFileBesideTheOutput() throws Exception {
    Path output = tmp.resolve("rx.json");

    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "chmed16a",
            "--to",
            "ch-emed",
            "-o",
            output.toString(),
            resource("rx-signed.url.txt").toString());

    assertThat(result.exitCode()).isZero();
    JsonNode signature = MAPPER.readTree(tmp.resolve("rx.json.signature.json").toFile());
    assertThat(signature.get("identity").asText()).isEqualTo("Erika+Muster+%28HIN%7Cemust1%29");
    assertThat(signature.get("timestamp").asText()).isEqualTo("1767225600");
    assertThat(signature.get("signature").asText()).startsWith("3066023100");
    assertThat(signature.get("verified").asBoolean()).isFalse();
    assertThat(signature.fieldNames())
        .toIterable()
        .containsExactly("identity", "timestamp", "signature", "verified");
    assertThat(result.stderr()).doesNotContain(SIGNATURE_VALUES);
  }

  @Test
  void signatureOutputOptionChoosesTheFile() throws Exception {
    Path signature = tmp.resolve("sig.json");

    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "chmed16a",
            "--to",
            "ch-emed",
            "--signature-output",
            signature.toString(),
            resource("rx-signed.url.txt").toString());

    assertThat(result.exitCode()).isZero();
    assertThat(MAPPER.readTree(signature.toFile()).get("verified").asBoolean()).isFalse();
    assertThat(new String(result.stdout(), java.nio.charset.StandardCharsets.UTF_8))
        .doesNotContain(SIGNATURE_VALUES);
  }

  @Test
  void signatureDataToStdoutIsNotWrittenButNoted() throws Exception {
    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "chmed16a",
            "--to",
            "ch-emed",
            resource("rx-signed.url.txt").toString());

    assertThat(result.exitCode()).isZero();
    assertThat(result.stderr())
        .contains("signature data")
        .contains("--signature-output")
        .doesNotContain(SIGNATURE_VALUES);
    try (var files = Files.list(tmp)) {
      assertThat(files).isEmpty();
    }
  }

  @Test
  void failuresExit1AndWriteNeitherOutputNorSignature() throws Exception {
    Path output = tmp.resolve("out.png");

    CliTestSupport.Run noCode =
        run(
            "convert",
            "--from",
            "chmed16a",
            "--to",
            "ch-emed",
            "-o",
            output.toString(),
            resource("no-code.png").toString());
    CliTestSupport.Run imageOfPrescription =
        run(
            "convert",
            "--from",
            "ch-emed",
            "--to",
            "chmed16a",
            "--output-form",
            "qr-image",
            "-o",
            output.toString(),
            resource("medication-prescription.json").toString());

    assertThat(noCode.exitCode()).isEqualTo(1);
    assertThat(noCode.stderr()).contains("QR_NOT_FOUND");
    assertThat(imageOfPrescription.exitCode()).isEqualTo(1);
    assertThat(imageOfPrescription.stderr()).contains("QR_IMAGE_NOT_ALLOWED");
    try (var files = Files.list(tmp)) {
      assertThat(files).isEmpty();
    }
  }
}
