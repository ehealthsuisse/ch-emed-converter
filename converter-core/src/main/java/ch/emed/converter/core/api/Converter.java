package ch.emed.converter.core.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Entry point of the conversion library (contracts/library-api.md). Thread-safe. */
public interface Converter {

  /**
   * Converts a document. Never throws for invalid input or unsupported conversions; these are
   * reported as FAILED with ERROR entries.
   *
   * @param request the request
   * @return the result
   */
  ConversionResult convert(ConversionRequest request);

  /**
   * Supported conversions, including pairs composed via CH EMED.
   *
   * @return supported pairs
   */
  Set<ConversionPair> supportedConversions();

  /**
   * Pinned specification version per format id, e.g. {@code CH_EMED → ch.fhir.ig.ch-emed#6.0.0}.
   *
   * @return pinned specifications
   */
  Map<String, String> pinnedSpecifications();

  /**
   * Input and output forms per format, including CH EMED, sorted by format id.
   *
   * @return format capabilities
   */
  List<FormatInfo> formats();

  /**
   * Converter version.
   *
   * @return semantic version
   */
  String version();
}
