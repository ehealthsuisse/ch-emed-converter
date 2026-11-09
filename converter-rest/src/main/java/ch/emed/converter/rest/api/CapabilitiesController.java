package ch.emed.converter.rest.api;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.OutputForm;
import ch.emed.converter.rest.config.ConverterHolder;
import ch.emed.converter.rest.config.ConverterRestProperties;
import ch.emed.converter.rest.error.ErrorType;
import ch.emed.converter.rest.error.ProblemFactory;
import ch.emed.converter.rest.model.CapabilitiesResponse;
import ch.emed.converter.rest.model.CapabilitiesResponse.Format;
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

  /**
   * Pixel limit of QR images, the library default {@code QrImageReader.DEFAULT_MAX_PIXELS} (feature
   * 003); fixed, not an operator setting.
   */
  static final long MAX_IMAGE_PIXELS = 40_000_000L;

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
            (timeoutMillis + 999) / 1000,
            MAX_IMAGE_PIXELS);
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
            formats(converter),
            options(),
            converter.pinnedSpecifications(),
            limits));
  }

  /** Input and output forms per format, from the library (feature 003). */
  static List<Format> formats(Converter converter) {
    return converter.formats().stream()
        .map(
            f ->
                new Format(
                    f.format().externalName(),
                    f.inputForms(),
                    f.outputForms().stream().map(OutputForm::externalName).toList()))
        .toList();
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
                + " what CH EMED requires."),
        new Option(
            "outputForm",
            "string",
            defaults.outputForm().externalName(),
            "Output form: document, qr-payload, qr-image (qr-* only for target chmed16a,"
                + " qr-image only for medication plans) or treatment-plans (only for target"
                + " ch-emed-epr and a medication card)."),
        new Option(
            "treatmentPlanReferences",
            "array",
            null,
            "Target ch-emed-epr: [{documentId, statementId}] (urn:uuid), one per prescribed"
                + " medication or card entry without a treatment-plan reference, in document"
                + " order."),
        new Option(
            "originalRepresentation",
            "string",
            null,
            "Target ch-emed-epr: PDF/A in base64 used as original representation; not used with"
                + " treatment-plans."));
  }
}
