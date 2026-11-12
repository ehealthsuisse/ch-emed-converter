package ch.emed.converter.cli;

import static ch.emed.converter.cli.CliTestSupport.converter;
import static ch.emed.converter.cli.CliTestSupport.resource;
import static ch.emed.converter.cli.CliTestSupport.run;
import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.OutputForm;
import ch.emed.converter.core.api.TreatmentPlanReference;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.render.OriginalRepresentationRenderer;
import ch.emed.converter.core.report.ReportCollector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** T046 and T066 (feature 004, US5), T023 (feature 005): CH EMED EPR through the CLI. */
class EprCliTest {

  private static final String DOC = "urn:uuid:11111111-1111-4111-8111-111111111111";
  private static final String MS = "urn:uuid:22222222-2222-4222-8222-222222222222";
  private static final String TREATMENT_PLAN =
      "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-ext-treatmentplan";

  @TempDir Path tmp;

  private static ConversionResult library(
      FormatId from, FormatId to, byte[] input, ConversionOptions options) {
    return converter().convert(new ConversionRequest(from, to, input, options));
  }

  /** The CH EMED prescription example without its treatment-plan reference. */
  private Path prescriptionWithoutReference() throws IOException {
    Bundle bundle = ChEmedJson.parse(Files.readAllBytes(resource("medication-prescription.json")));
    bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(DomainResource.class::isInstance)
        .map(DomainResource.class::cast)
        .forEach(r -> r.getExtension().removeIf(e -> e.getUrl().equals(TREATMENT_PLAN)));
    Path file = tmp.resolve("rx.json");
    Files.write(file, ChEmedJson.serialize(bundle));
    return file;
  }

  @Test
  void eprToChEmedGivesTheLibraryOutput() throws IOException {
    Path pmlc = resource("epr/pmlc.json");
    byte[] expected =
        library(
                FormatId.CH_EMED_EPR,
                FormatId.CH_EMED,
                Files.readAllBytes(pmlc),
                ConversionOptions.defaults())
            .output()
            .orElseThrow();

    CliTestSupport.Run result =
        run("convert", "--from", "ch-emed-epr", "--to", "ch-emed", pmlc.toString());

    assertThat(result.exitCode()).as(result.stderr()).isZero();
    assertThat(result.stdout()).isEqualTo(expected);
  }

  @Test
  void chEmedToEprGivesTheLibraryOutput() throws IOException {
    byte[] chEmed =
        library(
                FormatId.CH_EMED_EPR,
                FormatId.CH_EMED,
                Files.readAllBytes(resource("epr/pre.json")),
                ConversionOptions.defaults())
            .output()
            .orElseThrow();
    Path input = tmp.resolve("pre-chemed.json");
    Files.write(input, chEmed);
    byte[] expected =
        library(FormatId.CH_EMED, FormatId.CH_EMED_EPR, chEmed, ConversionOptions.defaults())
            .output()
            .orElseThrow();

    CliTestSupport.Run result =
        run("convert", "--from", "ch-emed", "--to", "ch-emed-epr", input.toString());

    assertThat(result.exitCode()).as(result.stderr()).isZero();
    assertThat(result.stdout()).isEqualTo(expected);
  }

  @Test
  void treatmentPlansWriteTheCollection() throws IOException {
    Path card = resource("medication-card.json");
    Path out = tmp.resolve("mtp-set.json");

    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "ch-emed",
            "--to",
            "ch-emed-epr",
            card.toString(),
            "--output-form",
            "treatment-plans",
            "-o",
            out.toString());

    assertThat(result.exitCode()).as(result.stderr()).isZero();
    byte[] expected =
        library(
                FormatId.CH_EMED,
                FormatId.CH_EMED_EPR,
                Files.readAllBytes(card),
                ConversionOptions.defaults().withOutputForm(OutputForm.TREATMENT_PLANS))
            .output()
            .orElseThrow();
    assertThat(Files.readAllBytes(out)).isEqualTo(expected);
    assertThat(ChEmedJson.parse(expected).getType()).isEqualTo(Bundle.BundleType.COLLECTION);
  }

  @Test
  void aDirectPairWithChmed16aFailsNamingTheTwoSteps() {
    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "chmed16a",
            "--to",
            "ch-emed-epr",
            resource("plan-basic.json").toString());

    assertThat(result.exitCode()).isEqualTo(CliMain.EXIT_FAILED);
    assertThat(result.stderr()).contains("UNSUPPORTED_CONVERSION").contains("ch-emed");
  }

  @Test
  void treatmentPlansForChmed16aAreAUsageError() {
    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "ch-emed",
            "--to",
            "chmed16a",
            resource("medication-card.json").toString(),
            "--output-form",
            "treatment-plans");

    assertThat(result.exitCode()).isEqualTo(CliMain.EXIT_USAGE);
  }

  @Test
  void treatmentPlanReferencesGiveTheLibraryPre() throws IOException {
    Path rx = prescriptionWithoutReference();
    byte[] expected =
        library(
                FormatId.CH_EMED,
                FormatId.CH_EMED_EPR,
                Files.readAllBytes(rx),
                ConversionOptions.defaults()
                    .withTreatmentPlanReferences(List.of(new TreatmentPlanReference(DOC, MS))))
            .output()
            .orElseThrow();

    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "ch-emed",
            "--to",
            "ch-emed-epr",
            rx.toString(),
            "--treatment-plan",
            DOC + "," + MS);

    assertThat(result.exitCode()).as(result.stderr()).isZero();
    assertThat(result.stdout()).isEqualTo(expected);
  }

  @Test
  void aMalformedReferenceIsAUsageErrorWithoutEcho() throws IOException {
    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "ch-emed",
            "--to",
            "ch-emed-epr",
            prescriptionWithoutReference().toString(),
            "--treatment-plan",
            "secret-doc," + MS);

    assertThat(result.exitCode()).isEqualTo(CliMain.EXIT_USAGE);
    assertThat(result.stderr()).contains("--treatment-plan").doesNotContain("secret-doc");
  }

  @Test
  void aSuppliedOriginalRepresentationGivesTheLibraryPre() throws IOException {
    Path rx = prescriptionWithoutReference();
    byte[] pdfA =
        OriginalRepresentationRenderer.renderPdfA(
            ChEmedJson.parse(Files.readAllBytes(rx)), new ReportCollector());
    Path pdf = tmp.resolve("original.pdf");
    Files.write(pdf, pdfA);
    byte[] expected =
        library(
                FormatId.CH_EMED,
                FormatId.CH_EMED_EPR,
                Files.readAllBytes(rx),
                ConversionOptions.defaults()
                    .withTreatmentPlanReferences(List.of(new TreatmentPlanReference(DOC, MS)))
                    .withOriginalRepresentation(pdfA))
            .output()
            .orElseThrow();

    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "ch-emed",
            "--to",
            "ch-emed-epr",
            rx.toString(),
            "--treatment-plan",
            DOC + "," + MS,
            "--original-representation",
            pdf.toString());

    assertThat(result.exitCode()).as(result.stderr()).isZero();
    assertThat(result.stdout()).isEqualTo(expected);
  }

  @Test
  void aMissingOriginalRepresentationFileIsAUsageError() throws IOException {
    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "ch-emed",
            "--to",
            "ch-emed-epr",
            prescriptionWithoutReference().toString(),
            "--original-representation",
            tmp.resolve("missing.pdf").toString());

    assertThat(result.exitCode()).isEqualTo(CliMain.EXIT_USAGE);
  }

  /** Feature 005: DIS, PADV and PML in both directions give the library output. */
  @ParameterizedTest
  @ValueSource(strings = {"epr/dis.json", "epr/padv.json", "epr/pml.json"})
  void disPadvPmlInBothDirectionsGiveTheLibraryOutput(String name) throws IOException {
    Path epr = resource(name);
    byte[] chEmed =
        library(
                FormatId.CH_EMED_EPR,
                FormatId.CH_EMED,
                Files.readAllBytes(epr),
                ConversionOptions.defaults())
            .output()
            .orElseThrow();

    CliTestSupport.Run read =
        run("convert", "--from", "ch-emed-epr", "--to", "ch-emed", epr.toString());

    assertThat(read.exitCode()).as(read.stderr()).isZero();
    assertThat(read.stdout()).isEqualTo(chEmed);

    Path input = tmp.resolve("chemed-" + epr.getFileName());
    Files.write(input, chEmed);
    byte[] back =
        library(FormatId.CH_EMED, FormatId.CH_EMED_EPR, chEmed, ConversionOptions.defaults())
            .output()
            .orElseThrow();

    CliTestSupport.Run write =
        run("convert", "--from", "ch-emed", "--to", "ch-emed-epr", input.toString());

    assertThat(write.exitCode()).as(write.stderr()).isZero();
    assertThat(write.stdout()).isEqualTo(back);
  }

  /** Feature 005, M-EPR-04: a contradiction is a warning in the report; exit code 0. */
  @Test
  void contradictingReferencesAreAWarning() throws IOException {
    String dis = Files.readString(resource("epr/dis.json"));
    Bundle bundle = ChEmedJson.parse(dis.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    org.hl7.fhir.r4.model.MedicationDispense dispense =
        bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(org.hl7.fhir.r4.model.MedicationDispense.class::isInstance)
            .map(org.hl7.fhir.r4.model.MedicationDispense.class::cast)
            .findFirst()
            .orElseThrow();
    org.hl7.fhir.r4.model.Extension plan = dispense.getExtensionByUrl(TREATMENT_PLAN);
    org.hl7.fhir.r4.model.Extension prescription =
        dispense.getExtensionByUrl(
            "http://fhir.ch/ig/ch-emed/StructureDefinition/ch-emed-ext-prescription");
    prescription.getExtensionByUrl("id").setValue(plan.getExtensionByUrl("id").getValue().copy());
    ((org.hl7.fhir.r4.model.Identifier)
            prescription.getExtensionByUrl("externalDocumentId").getValue())
        .setValue(DOC);
    Path input = tmp.resolve("dis-contradiction.json");
    Files.write(input, ChEmedJson.serialize(bundle));
    Path report = tmp.resolve("report.json");

    CliTestSupport.Run result =
        run(
            "convert",
            "--from",
            "ch-emed-epr",
            "--to",
            "ch-emed",
            "--report",
            report.toString(),
            input.toString());

    assertThat(result.exitCode()).as(result.stderr()).isZero();
    assertThat(Files.readString(report)).contains("REFERENCE_INCONSISTENT").doesNotContain(DOC);
  }
}
