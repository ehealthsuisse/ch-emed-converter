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

  // Feature 003 (QR code and eRezept), data-model.md §4.

  /** Unsupported image format, or a PNG/JPEG that cannot be decoded. */
  public static final String IMAGE_UNREADABLE = "IMAGE_UNREADABLE";

  /** Image above the byte or pixel limit. */
  public static final String IMAGE_TOO_LARGE = "IMAGE_TOO_LARGE";

  /** No readable QR code in the image. */
  public static final String QR_NOT_FOUND = "QR_NOT_FOUND";

  /** More than one QR code in the image. */
  public static final String QR_MULTIPLE = "QR_MULTIPLE";

  /** Not a valid eRezept URL: foreign host, no payload, signature parameter missing or repeated. */
  public static final String EREZEPT_URL_INVALID = "EREZEPT_URL_INVALID";

  /** Signature and prescription status of a signed eRezept were not checked. */
  public static final String SIGNATURE_NOT_VERIFIED = "SIGNATURE_NOT_VERIFIED";

  /** A signature parameter does not have the expected form; passed on unchanged. */
  public static final String SIGNATURE_FORM_UNRECOGNISED = "SIGNATURE_FORM_UNRECOGNISED";

  /** An option supplied data that the signature of a signed eRezept does not cover. */
  public static final String NOT_COVERED_BY_SIGNATURE = "NOT_COVERED_BY_SIGNATURE";

  /** Payload larger than one QR code holds. */
  public static final String QR_CAPACITY_EXCEEDED = "QR_CAPACITY_EXCEEDED";

  /** QR image requested for a prescription, which is output as unsigned payload only. */
  public static final String QR_IMAGE_NOT_ALLOWED = "QR_IMAGE_NOT_ALLOWED";

  /** Output form not offered for the target format. */
  public static final String UNSUPPORTED_OUTPUT_FORM = "UNSUPPORTED_OUTPUT_FORM";

  private ReasonCode() {}
}
