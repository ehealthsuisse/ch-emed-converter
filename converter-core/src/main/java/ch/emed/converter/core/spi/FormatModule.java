package ch.emed.converter.core.spi;

import ch.emed.converter.core.api.FormatId;
import org.hl7.fhir.r4.model.Bundle;

/**
 * A format plugged in around the CH EMED hub (constitution V). Registered via {@code
 * META-INF/services/ch.emed.converter.core.spi.FormatModule}. Implementations MUST be stateless and
 * thread-safe and MUST NOT access the network.
 */
public interface FormatModule {

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
