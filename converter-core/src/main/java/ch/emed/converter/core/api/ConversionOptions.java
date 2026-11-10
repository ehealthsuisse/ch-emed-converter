package ch.emed.converter.core.api;

import ch.emed.converter.core.determinism.IdStrategy;
import ch.emed.converter.core.determinism.NameBasedIdStrategy;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Options of a conversion (data-model.md §1).
 *
 * @param strict any entry with clinical loss makes the outcome FAILED (FR-016)
 * @param validateOutput validate CH EMED output with the official validator; may be false only via
 *     the library API
 * @param idStrategy deterministic id generation (research R8)
 * @param clock used only if a timestamp cannot be taken from the source
 * @param maxDecodedSize limit in bytes for decompressed QR payloads, QR images and eRezept URLs
 * @param defaultLanguage document language if the source has none (FR-022)
 * @param confidentiality CH EMED document confidentiality (FR-022)
 * @param patientIdentifier patient identifier if the source has none (FR-023)
 * @param author author details if the source lacks them (FR-023)
 * @param outputForm form of the output (feature 003); the target must offer it
 * @param treatmentPlanReferences treatment plans in the EPR for the entries of a prescription or
 *     card that have none, in document order (feature 004, research R15)
 * @param originalRepresentation PDF/A supplied as original representation of a CH EMED EPR document
 *     (feature 004, research R16)
 */
public record ConversionOptions(
    boolean strict,
    boolean validateOutput,
    IdStrategy idStrategy,
    Optional<Clock> clock,
    long maxDecodedSize,
    String defaultLanguage,
    Confidentiality confidentiality,
    Optional<PatientIdentifier> patientIdentifier,
    Optional<AuthorDetails> author,
    OutputForm outputForm,
    List<TreatmentPlanReference> treatmentPlanReferences,
    Optional<byte[]> originalRepresentation) {

  /** Languages allowed for CH EMED documents. */
  public static final Set<String> LANGUAGES = Set.of("de-CH", "fr-CH", "it-CH", "en-CH");

  public static final long DEFAULT_MAX_DECODED_SIZE = 5L * 1024 * 1024;

  public ConversionOptions {
    Objects.requireNonNull(idStrategy, "idStrategy");
    Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(confidentiality, "confidentiality");
    Objects.requireNonNull(patientIdentifier, "patientIdentifier");
    Objects.requireNonNull(author, "author");
    Objects.requireNonNull(outputForm, "outputForm");
    treatmentPlanReferences =
        List.copyOf(Objects.requireNonNull(treatmentPlanReferences, "treatmentPlanReferences"));
    originalRepresentation =
        Objects.requireNonNull(originalRepresentation, "originalRepresentation").map(byte[]::clone);
    if (!LANGUAGES.contains(defaultLanguage)) {
      throw new IllegalArgumentException("Unsupported language: " + defaultLanguage);
    }
    if (maxDecodedSize <= 0) {
      throw new IllegalArgumentException("maxDecodedSize must be > 0");
    }
  }

  /** Options of feature 003, without the options of feature 004. */
  public ConversionOptions(
      boolean strict,
      boolean validateOutput,
      IdStrategy idStrategy,
      Optional<Clock> clock,
      long maxDecodedSize,
      String defaultLanguage,
      Confidentiality confidentiality,
      Optional<PatientIdentifier> patientIdentifier,
      Optional<AuthorDetails> author,
      OutputForm outputForm) {
    this(
        strict,
        validateOutput,
        idStrategy,
        clock,
        maxDecodedSize,
        defaultLanguage,
        confidentiality,
        patientIdentifier,
        author,
        outputForm,
        List.of(),
        Optional.empty());
  }

  /**
   * Options without an output form (before feature 003); the output form is {@link
   * OutputForm#DOCUMENT}.
   */
  public ConversionOptions(
      boolean strict,
      boolean validateOutput,
      IdStrategy idStrategy,
      Optional<Clock> clock,
      long maxDecodedSize,
      String defaultLanguage,
      Confidentiality confidentiality,
      Optional<PatientIdentifier> patientIdentifier,
      Optional<AuthorDetails> author) {
    this(
        strict,
        validateOutput,
        idStrategy,
        clock,
        maxDecodedSize,
        defaultLanguage,
        confidentiality,
        patientIdentifier,
        author,
        OutputForm.DOCUMENT);
  }

  /**
   * Defaults: strict=false, validateOutput=true, maxDecodedSize=5 MB, defaultLanguage=de-CH,
   * confidentiality=NORMAL, no patient identifier, no author details, outputForm=DOCUMENT, no
   * treatment-plan references, no original representation.
   *
   * @return default options
   */
  public static ConversionOptions defaults() {
    return new ConversionOptions(
        false,
        true,
        new NameBasedIdStrategy(),
        Optional.empty(),
        DEFAULT_MAX_DECODED_SIZE,
        "de-CH",
        Confidentiality.NORMAL,
        Optional.empty(),
        Optional.empty(),
        OutputForm.DOCUMENT);
  }

  /**
   * The supplied original representation, as a copy.
   *
   * @return the PDF bytes, if supplied
   */
  @Override
  public Optional<byte[]> originalRepresentation() {
    return originalRepresentation.map(byte[]::clone);
  }

  public ConversionOptions withStrict(boolean value) {
    return new ConversionOptions(
        value,
        validateOutput,
        idStrategy,
        clock,
        maxDecodedSize,
        defaultLanguage,
        confidentiality,
        patientIdentifier,
        author,
        outputForm,
        treatmentPlanReferences,
        originalRepresentation);
  }

  public ConversionOptions withValidateOutput(boolean value) {
    return new ConversionOptions(
        strict,
        value,
        idStrategy,
        clock,
        maxDecodedSize,
        defaultLanguage,
        confidentiality,
        patientIdentifier,
        author,
        outputForm,
        treatmentPlanReferences,
        originalRepresentation);
  }

  public ConversionOptions withDefaultLanguage(String value) {
    return new ConversionOptions(
        strict,
        validateOutput,
        idStrategy,
        clock,
        maxDecodedSize,
        value,
        confidentiality,
        patientIdentifier,
        author,
        outputForm,
        treatmentPlanReferences,
        originalRepresentation);
  }

  public ConversionOptions withConfidentiality(Confidentiality value) {
    return new ConversionOptions(
        strict,
        validateOutput,
        idStrategy,
        clock,
        maxDecodedSize,
        defaultLanguage,
        value,
        patientIdentifier,
        author,
        outputForm,
        treatmentPlanReferences,
        originalRepresentation);
  }

  public ConversionOptions withMaxDecodedSize(long value) {
    return new ConversionOptions(
        strict,
        validateOutput,
        idStrategy,
        clock,
        value,
        defaultLanguage,
        confidentiality,
        patientIdentifier,
        author,
        outputForm,
        treatmentPlanReferences,
        originalRepresentation);
  }

  public ConversionOptions withClock(Clock value) {
    return new ConversionOptions(
        strict,
        validateOutput,
        idStrategy,
        Optional.of(value),
        maxDecodedSize,
        defaultLanguage,
        confidentiality,
        patientIdentifier,
        author,
        outputForm,
        treatmentPlanReferences,
        originalRepresentation);
  }

  public ConversionOptions withPatientIdentifier(PatientIdentifier value) {
    return new ConversionOptions(
        strict,
        validateOutput,
        idStrategy,
        clock,
        maxDecodedSize,
        defaultLanguage,
        confidentiality,
        Optional.of(value),
        author,
        outputForm,
        treatmentPlanReferences,
        originalRepresentation);
  }

  public ConversionOptions withAuthor(AuthorDetails value) {
    return new ConversionOptions(
        strict,
        validateOutput,
        idStrategy,
        clock,
        maxDecodedSize,
        defaultLanguage,
        confidentiality,
        patientIdentifier,
        Optional.of(value),
        outputForm,
        treatmentPlanReferences,
        originalRepresentation);
  }

  public ConversionOptions withOutputForm(OutputForm value) {
    return new ConversionOptions(
        strict,
        validateOutput,
        idStrategy,
        clock,
        maxDecodedSize,
        defaultLanguage,
        confidentiality,
        patientIdentifier,
        author,
        value,
        treatmentPlanReferences,
        originalRepresentation);
  }

  /**
   * Sets the treatment-plan references (feature 004, research R15).
   *
   * @param value references in document order
   * @return changed options
   */
  public ConversionOptions withTreatmentPlanReferences(List<TreatmentPlanReference> value) {
    return new ConversionOptions(
        strict,
        validateOutput,
        idStrategy,
        clock,
        maxDecodedSize,
        defaultLanguage,
        confidentiality,
        patientIdentifier,
        author,
        outputForm,
        value,
        originalRepresentation);
  }

  /**
   * Sets the original representation supplied by the caller (feature 004, research R16).
   *
   * @param pdf the PDF, expected to be PDF/A-1 or PDF/A-2; copied
   * @return changed options
   */
  public ConversionOptions withOriginalRepresentation(byte[] pdf) {
    return new ConversionOptions(
        strict,
        validateOutput,
        idStrategy,
        clock,
        maxDecodedSize,
        defaultLanguage,
        confidentiality,
        patientIdentifier,
        author,
        outputForm,
        treatmentPlanReferences,
        Optional.of(pdf));
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ConversionOptions other
        && strict == other.strict
        && validateOutput == other.validateOutput
        && maxDecodedSize == other.maxDecodedSize
        && idStrategy.equals(other.idStrategy)
        && clock.equals(other.clock)
        && defaultLanguage.equals(other.defaultLanguage)
        && confidentiality == other.confidentiality
        && patientIdentifier.equals(other.patientIdentifier)
        && author.equals(other.author)
        && outputForm == other.outputForm
        && treatmentPlanReferences.equals(other.treatmentPlanReferences)
        && originalRepresentation.isPresent() == other.originalRepresentation.isPresent()
        && Arrays.equals(
            originalRepresentation.orElse(null), other.originalRepresentation.orElse(null));
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        strict,
        validateOutput,
        idStrategy,
        clock,
        maxDecodedSize,
        defaultLanguage,
        confidentiality,
        patientIdentifier,
        author,
        outputForm,
        treatmentPlanReferences,
        Arrays.hashCode(originalRepresentation.orElse(null)));
  }

  /** Never prints the original representation, only its size. */
  @Override
  public String toString() {
    return "ConversionOptions[strict="
        + strict
        + ", validateOutput="
        + validateOutput
        + ", outputForm="
        + outputForm
        + ", treatmentPlanReferences="
        + treatmentPlanReferences.size()
        + ", originalRepresentationBytes="
        + originalRepresentation.map(b -> b.length).orElse(0)
        + "]";
  }
}
