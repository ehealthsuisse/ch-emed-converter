package ch.emed.converter.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.Severity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.preflight.ValidationResult;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.xml.DomXmpParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T065 (feature 004, research R16): the PDF/A-1b original representation for CH EMED EPR. Every
 * rendered PDF passes PDFBox Preflight. {@code render} of feature 001 is unchanged; its bytes are
 * pinned by the expected outputs of {@code format-chmed16a}.
 */
class OriginalRepresentationRendererPdfATest {

  private static final String CARD = "/examples/Bundle-2-7-MedicationCard.json";
  private static final String PRESCRIPTION = "/examples/Bundle-2-6-MedicationPrescription.json";

  @TempDir Path tmp;

  private static Bundle example(String name) throws IOException {
    try (InputStream in = OriginalRepresentationRendererPdfATest.class.getResourceAsStream(name)) {
      return ChEmedJson.parse(in.readAllBytes());
    }
  }

  private static Patient patient(Bundle bundle) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(Patient.class::isInstance)
        .map(Patient.class::cast)
        .findFirst()
        .orElseThrow();
  }

  private ValidationResult preflight(byte[] pdf) throws IOException {
    Path file = Files.createTempFile(tmp, "pdfa", ".pdf");
    Files.write(file, pdf);
    return PreflightParser.validate(file.toFile());
  }

  private static String text(byte[] pdf) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(doc);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {CARD, PRESCRIPTION})
  void passesPreflightAsPdfA1b(String example) throws IOException {
    byte[] pdf = OriginalRepresentationRenderer.renderPdfA(example(example), new ReportCollector());

    ValidationResult result = preflight(pdf);

    List<String> errors = new ArrayList<>();
    result.getErrorsList().forEach(e -> errors.add(e.getErrorCode() + " " + e.getDetails()));
    assertThat(errors).as(example).isEmpty();
    assertThat(result.isValid()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {CARD, PRESCRIPTION})
  void xmpDeclaresPdfA1bWithTheDocumentDate(String example) throws Exception {
    Bundle bundle = example(example);
    Composition composition = (Composition) bundle.getEntryFirstRep().getResource();

    byte[] pdf = OriginalRepresentationRenderer.renderPdfA(bundle, new ReportCollector());

    try (PDDocument doc = Loader.loadPDF(pdf)) {
      XMPMetadata xmp =
          new DomXmpParser().parse(doc.getDocumentCatalog().getMetadata().exportXMPMetadata());
      PDFAIdentificationSchema pdfa = xmp.getPDFAIdentificationSchema();
      assertThat(pdfa.getPart()).isEqualTo(1);
      assertThat(pdfa.getConformance()).isEqualTo("B");
      assertThat(xmp.getDublinCoreSchema().getTitle()).isEqualTo(composition.getTitle());
      Calendar created = xmp.getXMPBasicSchema().getCreateDate();
      Calendar modified = xmp.getXMPBasicSchema().getModifyDate();
      assertThat(created.getTimeInMillis()).isEqualTo(composition.getDate().getTime());
      assertThat(modified.getTimeInMillis()).isEqualTo(composition.getDate().getTime());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {CARD, PRESCRIPTION})
  void embedsLiberationSansAndAnSrgbOutputIntent(String example) throws IOException {
    byte[] pdf = OriginalRepresentationRenderer.renderPdfA(example(example), new ReportCollector());

    try (PDDocument doc = Loader.loadPDF(pdf)) {
      List<PDFont> fonts = new ArrayList<>();
      for (PDPage page : doc.getPages()) {
        PDResources resources = page.getResources();
        for (COSName name : resources.getFontNames()) {
          fonts.add(resources.getFont(name));
        }
      }
      assertThat(fonts).isNotEmpty();
      assertThat(fonts)
          .allSatisfy(
              font -> {
                assertThat(font.isEmbedded()).as(font.getName()).isTrue();
                assertThat(font.isStandard14()).as(font.getName()).isFalse();
                assertThat(font.getName()).contains("LiberationSans");
              });
      List<PDOutputIntent> intents = doc.getDocumentCatalog().getOutputIntents();
      assertThat(intents).hasSize(1);
      assertThat(intents.get(0).getOutputConditionIdentifier()).contains("sRGB");
    }
  }

  @org.junit.jupiter.api.Test
  void keepsGermanFrenchAndItalianLettersAndReplacesMissingOnes() throws IOException {
    Bundle bundle = example(CARD);
    patient(bundle).getNameFirstRep().setFamily("Müller-Hélène Niccolò Łukasz 漢");
    ReportCollector report = new ReportCollector();

    byte[] pdf = OriginalRepresentationRenderer.renderPdfA(bundle, report);

    assertThat(text(pdf)).contains("Müller-Hélène Niccolò Łukasz ?");
    assertThat(report.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.PDF_CHARACTER_REPLACED);
              assertThat(e.severity()).isEqualTo(Severity.INFORMATION);
              assertThat(e.message()).doesNotContain("Müller").doesNotContain("漢");
            });
    assertThat(preflight(pdf).isValid()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {CARD, PRESCRIPTION})
  void twoRunsGiveIdenticalBytes(String example) throws IOException {
    byte[] first =
        OriginalRepresentationRenderer.renderPdfA(example(example), new ReportCollector());
    byte[] second =
        OriginalRepresentationRenderer.renderPdfA(example(example), new ReportCollector());

    assertThat(first).isEqualTo(second);
  }

  @ParameterizedTest
  @ValueSource(strings = {CARD, PRESCRIPTION})
  void showsTheDocumentContent(String example) throws IOException {
    Bundle bundle = example(example);
    Composition composition = (Composition) bundle.getEntryFirstRep().getResource();

    String text = text(OriginalRepresentationRenderer.renderPdfA(bundle, new ReportCollector()));

    assertThat(text)
        .contains(composition.getTitle())
        .contains(patient(bundle).getNameFirstRep().getFamily());
  }

  @org.junit.jupiter.api.Test
  void colourProfileIsTheVendoredOne() {
    assertThat(SrgbProfile.bytes()).hasSize(3024);
    assertThat(SrgbProfile.SHA256)
        .isEqualTo("384b832de3412066743b52a75ee906b6fb9fb8d9e09e936fc2c43223815c6e0a");
  }
}
