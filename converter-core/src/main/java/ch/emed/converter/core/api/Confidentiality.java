package ch.emed.converter.core.api;

/** CH EMED document confidentiality (FR-022), mapped to the CH Core EPR confidentiality codes. */
public enum Confidentiality {
  NORMAL("N", "17621005", "Normal (qualifier value)"),
  RESTRICTED("R", "263856008", "Restricted (qualifier value)"),
  SECRET("V", "1141000195107", "Secret (qualifier value)");

  private final String hl7Code;
  private final String snomedCode;
  private final String snomedDisplay;

  Confidentiality(String hl7Code, String snomedCode, String snomedDisplay) {
    this.hl7Code = hl7Code;
    this.snomedCode = snomedCode;
    this.snomedDisplay = snomedDisplay;
  }

  /** Code for {@code Composition.confidentiality} (HL7 v3 Confidentiality). */
  public String hl7Code() {
    return hl7Code;
  }

  /** SNOMED CT code for the CH Core EPR confidentiality extension. */
  public String snomedCode() {
    return snomedCode;
  }

  public String snomedDisplay() {
    return snomedDisplay;
  }
}
