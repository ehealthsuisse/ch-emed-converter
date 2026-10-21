package ch.emed.converter.core.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifier of a supported format. A value type rather than an enum, so that format modules can
 * add formats without changing the core (constitution V: formats plug in around the CH EMED hub).
 *
 * @param id upper-case identifier, e.g. {@code CH_EMED}
 */
public record FormatId(String id) {

  private static final Pattern ID = Pattern.compile("[A-Z][A-Z0-9_]*");

  /** CH EMED (HL7 FHIR R4), the canonical hub model. */
  public static final FormatId CH_EMED = new FormatId("CH_EMED");

  /** eMediplan CHMED16A. */
  public static final FormatId CHMED16A = new FormatId("CHMED16A");

  public FormatId {
    Objects.requireNonNull(id, "id");
    if (!ID.matcher(id).matches()) {
      throw new IllegalArgumentException("Invalid format id: " + id);
    }
  }

  public static FormatId of(String id) {
    return new FormatId(id);
  }

  /**
   * External (CLI/REST) name: lower case with dashes, e.g. {@code ch-emed}.
   *
   * @return the external name
   */
  public String externalName() {
    return id.toLowerCase(java.util.Locale.ROOT).replace('_', '-');
  }

  /**
   * Parses an external name such as {@code ch-emed} or {@code chmed16a}.
   *
   * @param name external name
   * @return the format id
   */
  public static FormatId fromExternalName(String name) {
    return new FormatId(name.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
  }

  public boolean isChEmed() {
    return CH_EMED.equals(this);
  }

  @Override
  public String toString() {
    return id;
  }
}
