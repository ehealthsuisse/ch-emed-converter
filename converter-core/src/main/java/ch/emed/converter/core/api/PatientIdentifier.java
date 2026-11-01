package ch.emed.converter.core.api;

import java.util.Objects;

/**
 * Patient identifier supplied by the caller when the source has none (FR-023).
 *
 * @param system identifier system URI, e.g. {@code urn:oid:2.16.756.5.30.1.123.100.1.1.1}
 * @param value identifier value
 */
public record PatientIdentifier(String system, String value) {

  public PatientIdentifier {
    Objects.requireNonNull(system, "system");
    Objects.requireNonNull(value, "value");
    if (system.isBlank() || value.isBlank()) {
      throw new IllegalArgumentException("system and value must not be blank");
    }
  }
}
