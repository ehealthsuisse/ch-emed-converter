package ch.emed.converter.chmed23a.mapping;

import static ch.emed.converter.chmed23a.mapping.MappingScope.present;

import ch.emed.converter.chmed23a.model.Medication;
import ch.emed.converter.core.api.Confidentiality;
import ch.emed.converter.core.chemed.Labels;
import ch.emed.converter.core.chemed.Narratives;
import ch.emed.converter.core.chemed.Profiles;
import ch.emed.converter.core.render.OriginalRepresentationRenderer;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportEntry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.DocumentConfidentiality;
import org.hl7.fhir.r4.model.Composition.SectionComponent;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;

/** Document-level parts shared by the Medication Card and the Medication Prescription. */
public final class Documents {

  static final Map<String, String> LANGUAGES =
      Map.of("de", "de-CH", "fr", "fr-CH", "it", "it-CH", "en", "en-CH");
  static final String ORIGINAL_PATH = "/originalRepresentation";
  private static final String HTML_ID = "original-representation";

  private Documents() {}

  /**
   * The document UUID: {@code id} if it is a UUID, otherwise derived from the content (UUID v5 of
   * the model), so that every input form of the same document gives the same id.
   */
  @MappingRule("M23-DOC-04")
  public static String documentId(Medication med, ch.emed.converter.core.spi.MappingContext ctx) {
    String id = med.id();
    if (id != null) {
      try {
        return UUID.fromString(id.trim()).toString().equals(id.trim().toLowerCase(Locale.ROOT))
            ? id.trim().toLowerCase(Locale.ROOT)
            : derived(med, ctx, true);
      } catch (IllegalArgumentException e) {
        return derived(med, ctx, true);
      }
    }
    return derived(med, ctx, false);
  }

  private static String derived(
      Medication med, ch.emed.converter.core.spi.MappingContext ctx, boolean idGiven) {
    String hash;
    try {
      hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(med.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
    String id = ctx.options().idStrategy().id("chmed23a:" + hash, "/");
    ctx.report()
        .add(
            ReportEntry.information(
                    ReasonCode.DERIVED_VALUE,
                    Phase.MAP,
                    "/id",
                    idGiven
                        ? "The document id is not a UUID; a UUID derived from the content is used"
                        : "The document has no id; a UUID derived from the content is used")
                .withTargetPath("Bundle.identifier"));
    if (idGiven) {
      ctx.report()
          .add(
              ReportEntry.information(
                  ReasonCode.UNMAPPABLE_ELEMENT,
                  Phase.MAP,
                  "/id",
                  "A document id that is not a UUID cannot be kept in CH EMED"));
    }
    return id;
  }

  /** Document language (M23-DOC-07). */
  @MappingRule("M23-DOC-07")
  public static String language(Medication med, MappingScope scope) {
    String lng = med.patient() == null ? null : med.patient().lng();
    String mapped = lng == null ? null : LANGUAGES.get(lng.trim().toLowerCase(Locale.ROOT));
    scope
        .report()
        .add(
            ReportEntry.information(
                    ReasonCode.DERIVED_VALUE,
                    Phase.MAP,
                    "/patient/lng",
                    mapped != null
                        ? "Document language derived from the patient language"
                        : "Patient language absent or not a CH EMED language; default document"
                            + " language used")
                .withTargetPath("Composition.language"));
    return mapped != null ? mapped : scope.options().defaultLanguage();
  }

  /** Confidentiality from the options (M23-DOC-08). */
  @MappingRule("M23-DOC-08")
  public static void confidentiality(Composition c, MappingScope scope) {
    Confidentiality confidentiality = scope.options().confidentiality();
    c.setConfidentiality(DocumentConfidentiality.fromCode(confidentiality.hl7Code()));
    c.getConfidentialityElement()
        .addExtension(
            new Extension(
                Profiles.EXT_CONFIDENTIALITY,
                new CodeableConcept(
                    new Coding(
                        Profiles.SNOMED,
                        confidentiality.snomedCode(),
                        confidentiality.snomedDisplay()))));
    scope
        .report()
        .add(
            ReportEntry.information(
                    ReasonCode.DERIVED_VALUE,
                    Phase.MAP,
                    "/",
                    "Document confidentiality set from the conversion options")
                .withTargetPath("Composition.confidentiality"));
  }

  /** Section {@code originalRepresentation} with the link to the PDF Binary (M23-DOC-09). */
  @MappingRule("M23-DOC-09")
  public static void originalRepresentationSection(
      Composition c, String language, MappingScope scope) {
    SectionComponent original = c.addSection();
    original.setTitle(Labels.get(language, Labels.Key.SECTION_ORIGINAL));
    original.setCode(
        new CodeableConcept(
            new Coding(Profiles.LOINC, "55108-5", "Clinical presentation Document")));
    Narrative text = new Narrative();
    Extension link = new Extension(Profiles.EXT_TEXT_LINK);
    link.addExtension(new Extension("htmlid", new StringType(HTML_ID)));
    link.addExtension(new Extension("data", new UriType(scope.ref(ORIGINAL_PATH))));
    text.addExtension(link);
    text.setStatus(Narrative.NarrativeStatus.GENERATED);
    text.setDivAsString(
        "<div xmlns=\"http://www.w3.org/1999/xhtml\">Representation of the original view: <span"
            + " id=\""
            + HTML_ID
            + "\">PDF</span></div>");
    original.setText(text);
    original.addEntry(new org.hl7.fhir.r4.model.Reference(scope.ref(ORIGINAL_PATH)));
  }

  /** The rendered PDF of the document (M23-DOC-09). */
  public static void addOriginalRepresentation(Bundle bundle, MappingScope scope) {
    Binary binary = new Binary();
    binary.setId(scope.id(ORIGINAL_PATH));
    binary.setContentType("application/pdf");
    binary.setData(OriginalRepresentationRenderer.render(bundle, scope.report()));
    add(bundle, binary);
  }

  /** Section {@code annotation} for a non-empty remark (M23-DOC-06). */
  @MappingRule("M23-DOC-06")
  public static void annotationSection(Composition c, String language, String remark) {
    if (!present(remark)) {
      return;
    }
    SectionComponent annotation = c.addSection();
    annotation.setTitle(Labels.get(language, Labels.Key.SECTION_ANNOTATION));
    annotation.setCode(
        new CodeableConcept(
            new Coding(
                Profiles.LOINC, "48767-8", "Annotation comment [Interpretation] Narrative")));
    annotation.setText(Narratives.generated(remark));
  }

  public static void add(Bundle bundle, Resource resource) {
    bundle.addEntry().setFullUrl("urn:uuid:" + resource.getIdPart()).setResource(resource);
  }
}
