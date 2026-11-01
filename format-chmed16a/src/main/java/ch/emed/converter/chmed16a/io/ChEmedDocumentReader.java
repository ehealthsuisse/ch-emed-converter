package ch.emed.converter.chmed16a.io;

import ca.uhn.fhir.parser.DataFormatException;
import ch.emed.converter.chmed16a.mapping.MappingRule;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.report.Severity;
import ch.emed.converter.core.spi.ChEmedValidator;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * Opens CH EMED document Bundles for the reverse mapping (mapping spec M-REV-*): parses, validates
 * against the pinned profiles, detects the document type from {@code Composition.type} (FR-007) and
 * resolves references inside the Bundle.
 */
public final class ChEmedDocumentReader {

  static final String LOINC = "http://loinc.org";
  static final String CARD_CODE = "56445-0";
  static final String PRESCRIPTION_CODE = "57833-6";
  private static final String ORIGINAL_REPRESENTATION_CODE = "55108-5";

  /** Supported CH EMED document types. */
  public enum DocumentType {
    MEDICATION_CARD,
    MEDICATION_PRESCRIPTION
  }

  /**
   * An opened CH EMED document.
   *
   * @param bundle the document Bundle
   * @param composition its Composition
   * @param type the detected document type
   */
  public record ChEmedDocument(Bundle bundle, Composition composition, DocumentType type) {

    /**
     * Resolves a reference inside the Bundle by {@code fullUrl}, then by {@code <Type>/<id>}.
     * Contained references ({@code #id}) are not handled here.
     *
     * @return the resource, or null if it is not in the Bundle
     */
    public Resource resolve(Reference reference) {
      if (reference == null || !reference.hasReference()) {
        return null;
      }
      String ref = reference.getReference();
      if (ref.startsWith("#")) {
        return null;
      }
      for (BundleEntryComponent entry : bundle.getEntry()) {
        if (ref.equals(entry.getFullUrl()) && entry.hasResource()) {
          return entry.getResource();
        }
      }
      String relative = relative(ref);
      for (BundleEntryComponent entry : bundle.getEntry()) {
        Resource r = entry.getResource();
        if (r != null && r.hasIdElement() && relative.equals(r.fhirType() + "/" + r.getIdPart())) {
          return r;
        }
      }
      return null;
    }

    /** Typed variant of {@link #resolve(Reference)}; null if absent or of another type. */
    public <T extends Resource> T resolve(Reference reference, Class<T> type) {
      Resource resource = resolve(reference);
      return type.isInstance(resource) ? type.cast(resource) : null;
    }

    /** All resources of a type, in Bundle order. */
    public <T extends Resource> List<T> all(Class<T> type) {
      List<T> result = new ArrayList<>();
      for (BundleEntryComponent entry : bundle.getEntry()) {
        if (type.isInstance(entry.getResource())) {
          result.add(type.cast(entry.getResource()));
        }
      }
      return result;
    }

    private static String relative(String reference) {
      String[] parts = reference.split("/");
      return parts.length < 2 ? reference : parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }
  }

  private ChEmedDocumentReader() {}

  /**
   * Parses and validates a CH EMED document and detects its type.
   *
   * @param json UTF-8 FHIR JSON
   * @param validator CH EMED validator (phase {@code VALIDATE_INPUT})
   * @param report receives problems
   * @return the document, or null if it cannot be converted (ERROR entries)
   */
  public static ChEmedDocument read(
      byte[] json, ChEmedValidator validator, ReportCollector report) {
    Bundle bundle;
    try {
      bundle = ChEmedJson.parse(json);
    } catch (DataFormatException | ClassCastException e) {
      report.add(
          ReportEntry.error(
              ReasonCode.PARSE_ERROR, Phase.PARSE, "/", "Input is not a FHIR JSON Bundle"));
      return null;
    }
    List<ReportEntry> validation = validator.validate(bundle, Phase.VALIDATE_INPUT);
    report.addAll(validation);
    if (validation.stream().anyMatch(e -> e.severity() == Severity.ERROR)) {
      return null;
    }
    return open(bundle, report);
  }

  /**
   * Detects the document type of a validated Bundle and reports CH EMED elements without CHMED16A
   * counterpart (M-REV-22, M-REV-23).
   *
   * @return the document, or null if the type is not supported (ERROR entry)
   */
  @MappingRule({"M-REV-01", "M-REV-22", "M-REV-23"})
  public static ChEmedDocument open(Bundle bundle, ReportCollector report) {
    Composition composition =
        bundle.getEntry().stream()
            .map(BundleEntryComponent::getResource)
            .filter(Composition.class::isInstance)
            .map(Composition.class::cast)
            .findFirst()
            .orElse(null);
    if (composition == null) {
      report.add(
          ReportEntry.error(
              ReasonCode.PARSE_ERROR,
              Phase.PARSE,
              "Bundle.entry",
              "The document Bundle contains no Composition"));
      return null;
    }
    DocumentType type = type(composition.getType());
    if (type == null) {
      report.add(
          ReportEntry.error(
              ReasonCode.UNSUPPORTED_DOCUMENT_TYPE,
              Phase.VALIDATE_INPUT,
              "Composition.type",
              "Document type " + typeName(composition.getType()) + " is not supported"));
      return null;
    }
    if (composition.hasConfidentiality()) {
      report.add(
          ReportEntry.information(
              ReasonCode.UNMAPPABLE_ELEMENT,
              Phase.MAP,
              "Composition.confidentiality",
              "Document confidentiality has no CHMED16A counterpart and is not carried over"));
    }
    if (composition.getSection().stream()
        .anyMatch(s -> hasCode(s.getCode(), ORIGINAL_REPRESENTATION_CODE))) {
      report.add(
          ReportEntry.information(
              ReasonCode.UNMAPPABLE_ELEMENT,
              Phase.MAP,
              "Composition.section.where(code='" + ORIGINAL_REPRESENTATION_CODE + "')",
              "The original representation (PDF) is not carried over"));
    }
    return new ChEmedDocument(bundle, composition, type);
  }

  private static DocumentType type(CodeableConcept type) {
    if (hasCode(type, CARD_CODE)) {
      return DocumentType.MEDICATION_CARD;
    }
    if (hasCode(type, PRESCRIPTION_CODE)) {
      return DocumentType.MEDICATION_PRESCRIPTION;
    }
    return null;
  }

  private static boolean hasCode(CodeableConcept concept, String code) {
    return concept.getCoding().stream()
        .anyMatch(c -> LOINC.equals(c.getSystem()) && code.equals(c.getCode()));
  }

  private static String typeName(CodeableConcept type) {
    for (Coding coding : type.getCoding()) {
      if (LOINC.equals(coding.getSystem()) && coding.hasCode()) {
        return "LOINC " + coding.getCode();
      }
    }
    return type.hasCoding() && type.getCodingFirstRep().hasCode()
        ? type.getCodingFirstRep().getCode()
        : "(unknown)";
  }
}
