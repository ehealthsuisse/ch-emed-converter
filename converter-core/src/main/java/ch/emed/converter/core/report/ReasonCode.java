package ch.emed.converter.core.report;

/**
 * Stable reason codes of report entries, documented in the mapping specification's reason code
 * catalogue. Format modules may define additional codes following the same pattern.
 */
public final class ReasonCode {

  public static final String PARSE_ERROR = "PARSE_ERROR";
  public static final String CHMED_INVALID_FIELD = "CHMED_INVALID_FIELD";
  public static final String CHMED_REVISION_FIELD_MISSING = "CHMED_REVISION_FIELD_MISSING";
  public static final String UNMAPPABLE_ELEMENT = "UNMAPPABLE_ELEMENT";
  public static final String DOSAGE_FREE_TEXT_FALLBACK = "DOSAGE_FREE_TEXT_FALLBACK";
  public static final String UNSUPPORTED_DOCUMENT_TYPE = "UNSUPPORTED_DOCUMENT_TYPE";
  public static final String UNSUPPORTED_VERSION = "UNSUPPORTED_VERSION";
  public static final String UNSUPPORTED_CONVERSION = "UNSUPPORTED_CONVERSION";
  public static final String MISSING_REQUIRED_DATA = "MISSING_REQUIRED_DATA";
  public static final String UNKNOWN_FIELD_IGNORED = "UNKNOWN_FIELD_IGNORED";
  public static final String DERIVED_VALUE = "DERIVED_VALUE";
  public static final String PDF_CHARACTER_REPLACED = "PDF_CHARACTER_REPLACED";
  public static final String FHIR_VALIDATION = "FHIR_VALIDATION";
  public static final String EREZEPT_REQUIREMENT = "EREZEPT_REQUIREMENT";

  /**
   * The CH EMED validation could not be completed because of system load (the HL7 validator's
   * pattern checks have a fixed time limit), even after retries. Not a statement about the
   * document; callers should retry later.
   */
  public static final String VALIDATION_INCOMPLETE = "VALIDATION_INCOMPLETE";

  private ReasonCode() {}
}
