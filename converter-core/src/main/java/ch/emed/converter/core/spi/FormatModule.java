package ch.emed.converter.core.spi;

import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.api.OutputForm;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;

/**
 * A format plugged in around the CH EMED hub (constitution V). Registered via {@code
 * META-INF/services/ch.emed.converter.core.spi.FormatModule}. Implementations MUST be stateless and
 * thread-safe and MUST NOT access the network.
 */
public interface FormatModule {

  /**
   * Names of the accepted input forms, e.g. {@code json}, {@code qr-payload}, {@code qr-image}.
   *
   * @return input forms
   */
  default List<String> inputForms() {
    return List.of("json");
  }

  /**
   * Offered output forms; {@code fromChEmed} reads the requested one from the options.
   *
   * @return output forms
   */
  default List<OutputForm> outputForms() {
    return List.of(OutputForm.DOCUMENT);
  }

  /**
   * Names of the document types the format converts, listed in the capabilities (feature 004,
   * FR-014); empty by default.
   *
   * @return document types
   */
  default List<String> documentTypes() {
    return List.of();
  }

  FormatId format();

  /**
   * Pinned specification version of this format, e.g. {@code CHMED16A1 (ig-emediplan@0b579aa)}.
   *
   * @return pinned specification
   */
  String pinnedSpecification();

  /**
   * Parses and validates the input and maps it to a CH EMED document Bundle.
   *
   * @param input UTF-8 input
   * @param ctx mapping context; problems are reported there
   * @return the CH EMED Bundle, or null if the input could not be converted (ERROR entries)
   */
  Bundle toChEmed(byte[] input, MappingContext ctx);

  /**
   * Maps a validated CH EMED document Bundle to this format.
   *
   * @param document validated CH EMED Bundle
   * @param ctx mapping context; problems are reported there
   * @return serialized output, or null if the document could not be converted (ERROR entries)
   */
  byte[] fromChEmed(Bundle document, MappingContext ctx);
}
