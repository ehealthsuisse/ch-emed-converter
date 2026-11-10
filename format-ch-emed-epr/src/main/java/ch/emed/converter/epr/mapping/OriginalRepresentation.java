package ch.emed.converter.epr.mapping;

import ch.emed.converter.core.render.OriginalRepresentationRenderer;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.MappingContext;
import ch.emed.converter.epr.EprDocumentType;
import ch.emed.converter.epr.MappingRule;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.xml.DomXmpParser;
import org.apache.xmpbox.xml.XmpParsingException;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.SectionComponent;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;

/**
 * The original representation of a CH EMED EPR document as PDF/A (mapping rule M-EPR-25, research
 * R8 and R16). Order: a PDF/A supplied with the option {@code originalRepresentation}, then a PDF/A
 * of the source, then for PRE and PMLC a PDF/A-1b rendered from the converted document. MTP, DIS,
 * PADV and PML (feature 005) get none rendered; a non-PDF/A one is left out. The converter checks
 * only the PDF/A claim in the XMP metadata ({@code pdfaid:part} 1 or 2), not full conformance.
 */
public final class OriginalRepresentation {

  static final String LOINC = "http://loinc.org";
  static final String CODE = "55108-5";
  static final String EXT_TEXT_LINK = "http://hl7.org/fhir/StructureDefinition/textLink";
  static final String HTML_ID = "original-representation";

  private static final Map<String, String> TITLES =
      Map.of(
          "de", "Original Darstellung",
          "fr", "Représentation originale",
          "it", "Rappresentazione originale",
          "en", "Original representation");

  /**
   * Sets the original representation of the EPR document in place. Run it last, after the required
   * data, so a rendered PDF shows the same content as the structured data.
   *
   * @param epr the document
   * @param type its document type
   * @param ctx mapping context with the option and the report
   */
  @MappingRule("M-EPR-25")
  public void apply(Bundle epr, EprDocumentType type, MappingContext ctx) {
    Composition composition = (Composition) epr.getEntryFirstRep().getResource();
    SectionComponent section = section(composition);
    List<Binary> source = section == null ? List.of() : binaries(epr, section);
    String binaryPath = "Bundle.entry[0].resource.section";

    Optional<byte[]> supplied = ctx.options().originalRepresentation();
    if (supplied.isPresent()) {
      if (isPdfA(supplied.get())) {
        replace(epr, composition, section, source, supplied.get(), ctx);
        derived(ctx, binaryPath, "Original representation taken from the conversion options");
        return;
      }
      ctx.report()
          .add(
              ReportEntry.warning(
                  ReasonCode.UNMAPPABLE_ELEMENT,
                  Phase.MAP,
                  "options.originalRepresentation",
                  "Supplied original representation is not PDF/A and is not used"));
    }

    if (!source.isEmpty() && source.stream().allMatch(b -> isPdfA(b.getData()))) {
      return;
    }

    if (type != EprDocumentType.PRE && type != EprDocumentType.PMLC) {
      // MTP, DIS, PADV and PML: the section is optional; nothing is rendered (feature 005, R6)
      if (section != null) {
        removeBinaries(epr, source);
        composition.getSection().remove(section);
        ctx.report()
            .add(
                ReportEntry.information(
                    ReasonCode.UNMAPPABLE_ELEMENT,
                    Phase.MAP,
                    binaryPath,
                    "Original representation left out: EPR requires PDF/A"));
      }
      return;
    }

    removeBinaries(epr, source);
    if (section != null) {
      section.getEntry().clear();
    }
    byte[] pdf = OriginalRepresentationRenderer.renderPdfA(epr, ctx.report());
    replace(epr, composition, section, List.of(), pdf, ctx);
    derived(
        ctx,
        binaryPath,
        source.isEmpty()
            ? "Original representation rendered as PDF/A"
            : "Original representation is not PDF/A; replaced by a PDF/A rendered from the"
                + " document");
  }

  /**
   * @param pdf bytes
   * @return true if the bytes are a PDF whose XMP declares PDF/A-1 or PDF/A-2
   */
  public static boolean isPdfA(byte[] pdf) {
    if (pdf == null || pdf.length < 5) {
      return false;
    }
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      PDMetadata metadata = doc.getDocumentCatalog().getMetadata();
      if (metadata == null) {
        return false;
      }
      try (InputStream xmpStream = metadata.exportXMPMetadata()) {
        DomXmpParser parser = new DomXmpParser();
        parser.setStrictParsing(false);
        XMPMetadata xmp = parser.parse(xmpStream);
        PDFAIdentificationSchema id = xmp.getPDFAIdentificationSchema();
        Integer part = id == null ? null : id.getPart();
        return part != null && (part == 1 || part == 2);
      }
    } catch (IOException | XmpParsingException | RuntimeException e) {
      return false;
    }
  }

  private static SectionComponent section(Composition composition) {
    for (SectionComponent section : composition.getSection()) {
      for (Coding coding : section.getCode().getCoding()) {
        if (LOINC.equals(coding.getSystem()) && CODE.equals(coding.getCode())) {
          return section;
        }
      }
    }
    return null;
  }

  private static List<Binary> binaries(Bundle epr, SectionComponent section) {
    List<Binary> result = new ArrayList<>();
    for (Reference ref : section.getEntry()) {
      int index = RequiredData.index(epr, ref);
      if (index >= 0 && epr.getEntry().get(index).getResource() instanceof Binary binary) {
        result.add(binary);
      }
    }
    return result;
  }

  private static void removeBinaries(Bundle epr, List<Binary> binaries) {
    epr.getEntry().removeIf(e -> binaries.contains(e.getResource()));
  }

  private static void replace(
      Bundle epr,
      Composition composition,
      SectionComponent existing,
      List<Binary> source,
      byte[] pdf,
      MappingContext ctx) {
    removeBinaries(epr, source);
    String documentId = epr.getIdentifier().getValue();
    String id =
        ctx.options()
            .idStrategy()
            .id(documentId == null ? "" : documentId, "/OriginalRepresentation/Binary");
    Binary binary = new Binary();
    binary.setId(id);
    binary.setContentType("application/pdf");
    binary.setData(pdf);
    String url = "urn:uuid:" + id;
    epr.addEntry().setFullUrl(url).setResource(binary);

    SectionComponent section = existing;
    if (section == null) {
      section = composition.addSection();
      String language =
          composition.hasLanguage()
              ? composition.getLanguage().substring(0, 2).toLowerCase(Locale.ROOT)
              : "de";
      section.setTitle(TITLES.getOrDefault(language, TITLES.get("de")));
      section.setCode(
          new CodeableConcept(new Coding(LOINC, CODE, "Clinical presentation Document")));
    }
    section.getEntry().clear();
    section.addEntry(new Reference(url));
    Narrative text = new Narrative();
    Extension link = new Extension(EXT_TEXT_LINK);
    link.addExtension(new Extension("htmlid", new StringType(HTML_ID)));
    link.addExtension(new Extension("data", new UriType(url)));
    text.addExtension(link);
    text.setStatus(Narrative.NarrativeStatus.GENERATED);
    text.setDivAsString(
        "<div xmlns=\"http://www.w3.org/1999/xhtml\">Representation of the original view: <span"
            + " id=\""
            + HTML_ID
            + "\">PDF</span></div>");
    section.setText(text);
  }

  private static void derived(MappingContext ctx, String path, String message) {
    ctx.report()
        .add(
            ReportEntry.information(ReasonCode.DERIVED_VALUE, Phase.MAP, path, message)
                .withTargetPath("Binary.data"));
  }
}
