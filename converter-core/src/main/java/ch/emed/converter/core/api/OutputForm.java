package ch.emed.converter.core.api;

import java.util.Locale;

/**
 * Form of the conversion output (feature 003, data-model.md §2). Which forms a target offers is
 * listed by its format module; CH EMED offers {@link #DOCUMENT} only.
 */
public enum OutputForm {
  /** The document itself, e.g. CHMED16A JSON or FHIR JSON. */
  DOCUMENT,
  /** The textual QR payload, e.g. {@code CHMED16A1…}. */
  QR_PAYLOAD,
  /** A PNG image of the QR code. */
  QR_IMAGE,
  /**
   * A FHIR {@code collection} Bundle with one medication treatment plan document per treatment of a
   * medication card (feature 004, CH EMED EPR only).
   */
  TREATMENT_PLANS;

  /**
   * External (CLI/REST) name, e.g. {@code qr-payload}.
   *
   * @return the external name
   */
  public String externalName() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  /**
   * Parses an external name such as {@code qr-image}.
   *
   * @param name external name
   * @return the output form
   * @throws IllegalArgumentException if the name is unknown
   */
  public static OutputForm fromExternalName(String name) {
    for (OutputForm form : values()) {
      if (form.externalName().equals(name)) {
        return form;
      }
    }
    throw new IllegalArgumentException("Unknown output form: " + name);
  }
}
