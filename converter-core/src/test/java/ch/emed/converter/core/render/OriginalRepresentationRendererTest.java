package ch.emed.converter.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.Severity;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class OriginalRepresentationRendererTest {

  private static Bundle example() throws IOException {
    try (InputStream in =
        OriginalRepresentationRendererTest.class.getResourceAsStream(
            "/examples/Bundle-2-7-MedicationCard.json")) {
      return ChEmedJson.parse(in.readAllBytes());
    }
  }

  private static String text(byte[] pdf) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(doc);
    }
  }

  @Test
  void rendersParseablePdfWithDocumentContent() throws IOException {
    ReportCollector report = new ReportCollector();

    byte[] pdf = OriginalRepresentationRenderer.render(example(), report);

    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    String text = text(pdf);
    assertThat(text)
        .contains("Medikationsplan")
        .contains("Wegmüller")
        .contains("1943-05-15")
        .contains("BELOC ZOK Ret Tabl 50 mg")
        .contains("NORVASC Tabl 10 mg");
    assertThat(report.entries()).isEmpty();
  }

  @Test
  void renderingIsByteIdentical() throws IOException {
    Bundle bundle = example();

    byte[] first = OriginalRepresentationRenderer.render(bundle, new ReportCollector());
    byte[] second = OriginalRepresentationRenderer.render(example(), new ReportCollector());

    assertThat(first).isEqualTo(second);
  }

  @Test
  void charactersOutsideWinAnsiAreReplacedAndReported() throws IOException {
    Bundle bundle = example();
    bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(Patient.class::isInstance)
        .map(Patient.class::cast)
        .findFirst()
        .orElseThrow()
        .getNameFirstRep()
        .setFamily("Łukaszewicz");
    ReportCollector report = new ReportCollector();

    byte[] pdf = OriginalRepresentationRenderer.render(bundle, report);

    assertThat(text(pdf)).contains("?ukaszewicz");
    assertThat(report.entries())
        .anyMatch(
            e ->
                e.code().equals(ReasonCode.PDF_CHARACTER_REPLACED)
                    && e.severity() == Severity.INFORMATION
                    && !e.message().contains("ukaszewicz"));
  }
}
