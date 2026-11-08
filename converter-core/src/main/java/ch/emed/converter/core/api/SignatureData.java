package ch.emed.converter.core.api;

import java.util.Objects;

/**
 * Signature data of a signed input, e.g. the parameters {@code i}, {@code t} and {@code s} of an
 * eRezept URL (feature 003, data-model.md §3). The values are returned raw, exactly as in the
 * input, and are never verified. They never appear in the document, the report or logs.
 *
 * @param identity identity of the signer, raw
 * @param timestamp signing time, raw
 * @param signature signature, raw
 */
public record SignatureData(String identity, String timestamp, String signature) {

  public SignatureData {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(timestamp, "timestamp");
    Objects.requireNonNull(signature, "signature");
  }

  /**
   * The converter never verifies signatures (constitution IV, FR-005).
   *
   * @return always false
   */
  public boolean verified() {
    return false;
  }

  @Override
  public String toString() {
    return "SignatureData[not verified]";
  }
}
