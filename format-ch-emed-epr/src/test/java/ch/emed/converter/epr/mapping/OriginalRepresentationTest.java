package ch.emed.converter.epr.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.render.OriginalRepresentationRenderer;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.testsupport.EprFixtures;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.xml.XmpSerializer;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.SectionComponent;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.UriType;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T027 (feature 004, research R8, R16): the original representation of CH EMED EPR documents is a
 * PDF/A. The PDF fixtures are made with PDFBox here; they hold no patient data.
 */
class OriginalRepresentationTest {

  @TempDir Path tmp;

  // ---- PDF fixtures --------------------------------------------------------------------------

  /** A one-page PDF; with {@code part} > 0 its XMP claims PDF/A of that part. */
  static byte[] pdf(int part) {
    try (PDDocument doc = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      doc.addPage(new PDPage());
      if (part > 0) {
        XMPMetadata xmp = XMPMetadata.createXMPMetadata();
        var id = xmp.createAndAddPDFAIdentificationSchema();
        id.setPart(part);
        id.setConformance("B");
        ByteArrayOutputStream xmpBytes = new ByteArrayOutputStream();
        new XmpSerializer().serialize(xmp, xmpBytes, true);
        PDMetadata metadata = new PDMetadata(doc);
        metadata.importXMPMetadata(xmpBytes.toByteArray());
        doc.getDocumentCatalog().setMetadata(metadata);
      }
      doc.save(out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static final byte[] PLACEHOLDER = "not a valid binary".getBytes(StandardCharsets.UTF_8);

  // ---- documents -----------------------------------------------------------------------------

  private static String fixture(EprDocumentType type) {
    return switch (type) {
      case PMLC -> "official/Bundle-DocumentPmlcCARAPMP004AxapharmParacetamol.json";
      case PRE -> "official/Bundle-DocumentPreParacetamolAxapharmCARAPMP004.json";
      case MTP -> "official/Bundle-DocumentMtpParacetamolCARAPMP004.json";
      case DIS -> "official/Bundle-DocumentDis1.json";
      case PADV -> "official/Bundle-DocumentPadvChangeParacetamolCARAPMP004.json";
      case PML -> "official/Bundle-BundlePml1.json";
    };
  }

  private static Bundle document(EprDocumentType type) {
    return ChEmedJson.parse(EprFixtures.bytes(fixture(type)));
  }

  private static Composition composition(Bundle bundle) {
    return (Composition) bundle.getEntryFirstRep().getResource();
  }

  private static SectionComponent section(Bundle bundle) {
    return composition(bundle).getSection().stream()
        .filter(s -> s.getCode().getCodingFirstRep().getCode().equals(OriginalRepresentation.CODE))
        .findFirst()
        .orElse(null);
  }

  private static List<Binary> binaries(Bundle bundle) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(Binary.class::isInstance)
        .map(Binary.class::cast)
        .toList();
  }

  /** Puts {@code pdf} as data of the section's Binary, adding section and Binary if needed. */
  private static Bundle withSource(EprDocumentType type, byte[] pdf) {
    Bundle bundle = document(type);
    SectionComponent section = section(bundle);
    if (section == null || binaries(bundle).isEmpty()) {
      Binary binary = new Binary().setContentType("application/pdf");
      binary.setId("src");
      bundle
          .addEntry()
          .setFullUrl("urn:uuid:00000000-0000-4000-8000-000000000001")
          .setResource(binary);
      if (section == null) {
        section = composition(bundle).addSection();
        section.getCode().addCoding().setSystem(OriginalRepresentation.LOINC).setCode("55108-5");
      }
      section.getEntry().clear();
      section.addEntry().setReference("urn:uuid:00000000-0000-4000-8000-000000000001");
    }
    binaries(bundle).forEach(b -> b.setData(pdf));
    return bundle;
  }

  private static ReportCollector apply(Bundle bundle, EprDocumentType type, ConversionOptions o) {
    ReportCollector report = new ReportCollector();
    new OriginalRepresentation().apply(bundle, type, new MappingContext(o, report));
    return report;
  }

  private static byte[] binaryData(Bundle bundle) {
    List<Binary> list = binaries(bundle);
    assertThat(list).hasSize(1);
    return list.get(0).getData();
  }

  private boolean preflight(byte[] pdf) throws IOException {
    Path file = Files.createTempFile(tmp, "or", ".pdf");
    Files.write(file, pdf);
    return PreflightParser.validate(file.toFile()).isValid();
  }

  // ---- tests ---------------------------------------------------------------------------------

  @ParameterizedTest
  @EnumSource(EprDocumentType.class)
  void aSuppliedPdfATakesPrecedenceOverTheSource(EprDocumentType type) {
    Bundle bundle = withSource(type, pdf(2));
    byte[] supplied = pdf(1);

    ReportCollector report =
        apply(bundle, type, ConversionOptions.defaults().withOriginalRepresentation(supplied));

    assertThat(binaryData(bundle)).isEqualTo(supplied);
    assertThat(report.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.DERIVED_VALUE);
              assertThat(e.severity()).isEqualTo(Severity.INFORMATION);
            });
    String url = section(bundle).getEntryFirstRep().getReference();
    Extension link =
        section(bundle).getText().getExtensionByUrl(OriginalRepresentation.EXT_TEXT_LINK);
    assertThat(((UriType) link.getExtensionByUrl("data").getValue()).getValue()).isEqualTo(url);
  }

  @ParameterizedTest
  @EnumSource(
      value = EprDocumentType.class,
      names = {"PRE", "PMLC"})
  void aSuppliedPdfThatIsNotPdfAIsReportedAndTheNextRuleApplies(EprDocumentType type)
      throws IOException {
    Bundle bundle = withSource(type, PLACEHOLDER);

    ReportCollector report =
        apply(bundle, type, ConversionOptions.defaults().withOriginalRepresentation(pdf(0)));

    assertThat(report.entries())
        .filteredOn(e -> e.severity() == Severity.WARNING)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
              assertThat(e.clinicalLoss()).isFalse();
            });
    byte[] rendered = binaryData(bundle);
    assertThat(OriginalRepresentation.isPdfA(rendered)).isTrue();
    assertThat(preflight(rendered)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void aPdfAOfTheSourceIsKept(int part) {
    for (EprDocumentType type : EprDocumentType.values()) {
      byte[] source = pdf(part);
      Bundle bundle = withSource(type, source);

      ReportCollector report = apply(bundle, type, ConversionOptions.defaults());

      assertThat(binaryData(bundle)).as(type.name()).isEqualTo(source);
      assertThat(report.entries()).as(type.name()).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"plain", "pdfa3", "feature001", "placeholder", "noSection"})
  void preAndPmlcGetARenderedPdfAInsteadOf(String kind) throws IOException {
    for (EprDocumentType type : List.of(EprDocumentType.PRE, EprDocumentType.PMLC)) {
      Bundle bundle =
          switch (kind) {
            case "plain" -> withSource(type, pdf(0));
            case "pdfa3" -> withSource(type, pdf(3));
            case "feature001" ->
                withSource(
                    type,
                    OriginalRepresentationRenderer.render(document(type), new ReportCollector()));
            case "placeholder" -> withSource(type, PLACEHOLDER);
            default -> {
              Bundle b = document(type);
              composition(b).getSection().remove(section(b));
              b.getEntry().removeIf(e -> e.getResource() instanceof Binary);
              yield b;
            }
          };

      ReportCollector report = apply(bundle, type, ConversionOptions.defaults());

      byte[] rendered = binaryData(bundle);
      assertThat(OriginalRepresentation.isPdfA(rendered)).as(kind + " " + type).isTrue();
      assertThat(preflight(rendered)).as(kind + " " + type).isTrue();
      assertThat(section(bundle)).isNotNull();
      assertThat(section(bundle).getEntry()).hasSize(1);
      assertThat(report.entries())
          .filteredOn(e -> e.code().equals(ReasonCode.DERIVED_VALUE))
          .singleElement()
          .satisfies(e -> assertThat(e.message()).contains("PDF/A"));
    }
  }

  /** MTP (feature 004) and DIS, PADV, PML (feature 005, research R6): nothing is rendered. */
  @ParameterizedTest
  @EnumSource(
      value = EprDocumentType.class,
      names = {"MTP", "DIS", "PADV", "PML"})
  void otherTypesLeaveOutANonPdfAOriginalRepresentation(EprDocumentType type) {
    for (byte[] source : List.of(pdf(0), PLACEHOLDER)) {
      Bundle bundle = withSource(type, source);

      ReportCollector report = apply(bundle, type, ConversionOptions.defaults());

      assertThat(section(bundle)).as(type.name()).isNull();
      assertThat(binaries(bundle)).as(type.name()).isEmpty();
      assertThat(report.entries())
          .as(type.name())
          .singleElement()
          .satisfies(
              e -> {
                assertThat(e.code()).isEqualTo(ReasonCode.UNMAPPABLE_ELEMENT);
                assertThat(e.severity()).isEqualTo(Severity.INFORMATION);
                assertThat(e.message()).contains("EPR requires PDF/A");
              });
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = EprDocumentType.class,
      names = {"MTP", "DIS", "PADV", "PML"})
  void otherTypesWithoutOriginalRepresentationGetNone(EprDocumentType type) {
    Bundle bundle = document(type);
    if (section(bundle) != null) {
      composition(bundle).getSection().remove(section(bundle));
    }
    bundle.getEntry().removeIf(e -> e.getResource() instanceof Binary);

    ReportCollector report = apply(bundle, type, ConversionOptions.defaults());

    assertThat(section(bundle)).isNull();
    assertThat(binaries(bundle)).isEmpty();
    assertThat(report.entries()).isEmpty();
  }

  @org.junit.jupiter.api.Test
  void detectsPdfAOnlyByTheXmpClaim() {
    assertThat(OriginalRepresentation.isPdfA(pdf(1))).isTrue();
    assertThat(OriginalRepresentation.isPdfA(pdf(2))).isTrue();
    assertThat(OriginalRepresentation.isPdfA(pdf(3))).isFalse();
    assertThat(OriginalRepresentation.isPdfA(pdf(0))).isFalse();
    assertThat(OriginalRepresentation.isPdfA(PLACEHOLDER)).isFalse();
    assertThat(OriginalRepresentation.isPdfA(new byte[0])).isFalse();
  }
}
