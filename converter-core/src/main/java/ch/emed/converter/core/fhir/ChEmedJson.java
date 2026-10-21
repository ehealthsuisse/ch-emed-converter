package ch.emed.converter.core.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.nio.charset.StandardCharsets;
import org.hl7.fhir.r4.model.Bundle;

/**
 * Shared FHIR R4 context and deterministic JSON (de)serialization of CH EMED documents (research
 * R8: pretty-print off, HAPI's stable element order).
 */
public final class ChEmedJson {

  private static final FhirContext CONTEXT = FhirContext.forR4Cached();

  private ChEmedJson() {}

  public static FhirContext context() {
    return CONTEXT;
  }

  /**
   * Parses a CH EMED document.
   *
   * @param json UTF-8 FHIR JSON
   * @return the Bundle
   * @throws ca.uhn.fhir.parser.DataFormatException if the input is not a FHIR Bundle
   */
  public static Bundle parse(byte[] json) {
    IParser parser = CONTEXT.newJsonParser();
    return parser.parseResource(Bundle.class, new String(json, StandardCharsets.UTF_8));
  }

  public static byte[] serialize(Bundle bundle) {
    IParser parser = CONTEXT.newJsonParser().setPrettyPrint(false);
    return parser.encodeResourceToString(bundle).getBytes(StandardCharsets.UTF_8);
  }
}
