package ch.emed.converter.core.api;

import ch.emed.converter.core.determinism.IdStrategy;
import ch.emed.converter.core.determinism.NameBasedIdStrategy;
import java.time.Clock;
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
 * @param maxDecodedSize limit in bytes for decompressed QR payloads
 * @param defaultLanguage document language if the source has none (FR-022)
 * @param confidentiality CH EMED document confidentiality (FR-022)
 * @param patientIdentifier patient identifier if the source has none (FR-023)
 * @param author author details if the source lacks them (FR-023)
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
    Optional<AuthorDetails> author) {

  /** Languages allowed for CH EMED documents. */
  public static final Set<String> LANGUAGES = Set.of("de-CH", "fr-CH", "it-CH", "en-CH");

  public static final long DEFAULT_MAX_DECODED_SIZE = 5L * 1024 * 1024;

  public ConversionOptions {
    Objects.requireNonNull(idStrategy, "idStrategy");
    Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(confidentiality, "confidentiality");
    Objects.requireNonNull(patientIdentifier, "patientIdentifier");
    Objects.requireNonNull(author, "author");
    if (!LANGUAGES.contains(defaultLanguage)) {
      throw new IllegalArgumentException("Unsupported language: " + defaultLanguage);
    }
    if (maxDecodedSize <= 0) {
      throw new IllegalArgumentException("maxDecodedSize must be > 0");
    }
  }

  /**
   * Defaults: strict=false, validateOutput=true, maxDecodedSize=5 MB, defaultLanguage=de-CH,
   * confidentiality=NORMAL, no patient identifier, no author details.
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
        Optional.empty());
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
        author);
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
        author);
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
        author);
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
        author);
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
        author);
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
        author);
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
        author);
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
        Optional.of(value));
  }
}
