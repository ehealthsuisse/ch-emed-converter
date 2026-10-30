package ch.emed.converter.rest.api;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.rest.config.ConverterHolder;
import ch.emed.converter.rest.config.ConverterRestProperties;
import ch.emed.converter.rest.error.ErrorType;
import ch.emed.converter.rest.error.ProblemFactory;
import ch.emed.converter.rest.model.CapabilitiesResponse;
import ch.emed.converter.rest.model.CapabilitiesResponse.Limits;
import ch.emed.converter.rest.model.CapabilitiesResponse.Option;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/capabilities} (research R9). Pairs and specifications come from the library,
 * so new format modules appear without changes here.
 */
@RestController
public class CapabilitiesController {

  private final ConverterHolder converters;
  private final ProblemFactory problems;
  private final Limits limits;

  public CapabilitiesController(
      ConverterHolder converters, ProblemFactory problems, ConverterRestProperties properties) {
    this.converters = converters;
    this.problems = problems;
    long timeoutMillis = properties.timeout().toMillis();
    this.limits =
        new Limits(
            properties.maxRequestSize().toBytes(),
            properties.maxDocumentSize().toBytes(),
            // whole seconds, rounded up so that a sub-second limit is not shown as 0
            (timeoutMillis + 999) / 1000);
  }

  @GetMapping(path = "/api/v1/capabilities", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> capabilities(HttpServletRequest request) {
    Optional<Converter> ready = converters.converter();
    if (ready.isEmpty()) {
      return problems.retryLater(
          ErrorType.NOT_READY, "The service is not ready to accept conversions.", request);
    }
    Converter converter = ready.get();
    return ResponseEntity.ok(
        new CapabilitiesResponse(
            converter.version(),
            ConversionController.supported(converter),
            options(),
            converter.pinnedSpecifications(),
            limits));
  }

  /** The options of {@link RequestOptionsMapper}, with the library defaults. */
  static List<Option> options() {
    ConversionOptions defaults = ConversionOptions.defaults();
    return List.of(
        new Option(
            "strict",
            "boolean",
            defaults.strict(),
            "Fail if clinically relevant information cannot be represented exactly."),
        new Option(
            "language",
            "string",
            defaults.defaultLanguage(),
            "Document language if the source has none: "
                + String.join(", ", ConversionOptions.LANGUAGES.stream().sorted().toList())
                + "."),
        new Option(
            "confidentiality",
            "string",
            defaults.confidentiality().name().toLowerCase(Locale.ROOT),
            "Document confidentiality: normal, restricted or secret."),
        new Option(
            "patientIdentifier",
            "object",
            null,
            "Patient identifier {system, value}, used only if the source has none."),
        new Option(
            "author",
            "object",
            null,
            "Author details {gln, given, family, organization}, used only where the source lacks"
                + " what CH EMED requires."));
  }
}
