package ch.emed.converter.core.determinism;

/** Generates deterministic resource ids (research R8). Implementations MUST be deterministic. */
@FunctionalInterface
public interface IdStrategy {

  /**
   * Returns the id for an element of a document.
   *
   * @param documentId stable id of the source document (e.g. CHMED {@code Id})
   * @param path path of the element in the source document
   * @return a stable id
   */
  String id(String documentId, String path);
}
