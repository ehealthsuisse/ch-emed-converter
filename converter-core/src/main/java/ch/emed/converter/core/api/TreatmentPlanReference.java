package ch.emed.converter.core.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Treatment plan in the EPR that a prescribed medication or a card entry belongs to (feature 004,
 * research R15): the identifiers of the treatment plan document and of its MedicationStatement.
 *
 * @param documentId identifier of the treatment plan document, {@code urn:uuid:<uuid>}
 * @param statementId identifier of its MedicationStatement, {@code urn:uuid:<uuid>}
 */
public record TreatmentPlanReference(String documentId, String statementId) {

  private static final Pattern URN_UUID =
      Pattern.compile(
          "urn:uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  public TreatmentPlanReference {
    check(documentId, "documentId");
    check(statementId, "statementId");
  }

  private static void check(String value, String name) {
    Objects.requireNonNull(value, name);
    if (!URN_UUID.matcher(value).matches()) {
      // the value itself is not repeated (FR-015)
      throw new IllegalArgumentException(name + " must have the form urn:uuid:<uuid>");
    }
  }
}
