package ch.emed.converter.rest.api;

import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.ConversionPair;
import ch.emed.converter.core.api.ConversionRequest;
import ch.emed.converter.core.api.ConversionResult;
import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.FormatId;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.ReportJsonWriter;
import ch.emed.converter.rest.config.ConverterHolder;
import ch.emed.converter.rest.config.ConverterRestProperties;
import ch.emed.converter.rest.error.ErrorType;
import ch.emed.converter.rest.error.FailureClassifier;
import ch.emed.converter.rest.error.MalformedRequestException;
import ch.emed.converter.rest.error.ProblemFactory;
import ch.emed.converter.rest.execution.BoundedConversionExecutor;
import ch.emed.converter.rest.model.ConversionPairDto;
import ch.emed.converter.rest.model.ConversionRequestMessage;
import ch.emed.converter.rest.model.ConversionResponse;
import ch.emed.converter.rest.web.RequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/conversions/{from}/{to}} (contracts/openapi.yaml). Thin layer over the
 * library: documents are handled as bytes only, never parsed or re-serialized (research R2/R3).
 */
@RestController
@RequestMapping("/api/v1/conversions")
public class ConversionController {

  private final ConverterHolder converters;
  private final BoundedConversionExecutor executor;
  private final RequestOptionsMapper optionsMapper;
  private final ProblemFactory problems;
  private final long maxDocumentSize;

  public ConversionController(
      ConverterHolder converters,
      BoundedConversionExecutor executor,
      RequestOptionsMapper optionsMapper,
      ProblemFactory problems,
      ConverterRestProperties properties) {
    this.converters = converters;
    this.executor = executor;
    this.optionsMapper = optionsMapper;
    this.problems = problems;
    this.maxDocumentSize = properties.maxDocumentSize().toBytes();
  }

  @PostMapping(
      path = "/{from}/{to}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> convert(
      @PathVariable("from") String from,
      @PathVariable("to") String to,
      @RequestBody ConversionRequestMessage message,
      HttpServletRequest request) {
    Optional<Converter> ready = converters.converter();
    if (ready.isEmpty()) {
      // no unvalidated output while the validation resources are not available (FR-016)
      return problems.retryLater(
          ErrorType.NOT_READY, "The service is not ready to accept conversions.", request);
    }
    Converter converter = ready.get();
    Optional<ConversionPair> pair = pair(converter, from, to);
    if (pair.isEmpty()) {
      return problems.unsupported(
          "The requested conversion is not supported; see supportedConversions.",
          supported(converter),
          request);
    }
    byte[] document = decode(message);
    request.setAttribute(RequestAttributes.DOCUMENT_BYTES, document.length);
    if (document.length > maxDocumentSize) {
      return problems.create(
          ErrorType.REQUEST_TOO_LARGE,
          "The decoded document exceeds the configured maximum size.",
          request);
    }
    ConversionOptions options = optionsMapper.map(message.options());

    FormatId source = pair.get().source();
    FormatId target = pair.get().target();
    ConversionResult result =
        executor.execute(
            () -> converter.convert(new ConversionRequest(source, target, document, options)));
    request.setAttribute(RequestAttributes.OUTCOME, result.outcome());
    String report = new String(ReportJsonWriter.write(result.report()), StandardCharsets.UTF_8);

    if (result.outcome() == Outcome.FAILED) {
      ErrorType type = FailureClassifier.classify(result.report());
      return problems.withReport(type, FailureClassifier.detail(type), report, request);
    }
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ConversionResponse(
                result.outcome().name(),
                RequestAttributes.correlationId(request),
                report,
                Base64.getEncoder().encodeToString(result.output().orElseThrow()),
                mediaType(target)));
  }

  /** The pair if both names are valid format names and the conversion is offered. */
  private static Optional<ConversionPair> pair(Converter converter, String from, String to) {
    ConversionPair pair;
    try {
      pair = new ConversionPair(FormatId.fromExternalName(from), FormatId.fromExternalName(to));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    return converter.supportedConversions().contains(pair) ? Optional.of(pair) : Optional.empty();
  }

  static List<ConversionPairDto> supported(Converter converter) {
    return converter.supportedConversions().stream()
        .map(ConversionPairDto::of)
        .sorted(Comparator.comparing(ConversionPairDto::from).thenComparing(ConversionPairDto::to))
        .toList();
  }

  private static byte[] decode(ConversionRequestMessage message) {
    if (message == null || message.document() == null || message.document().isEmpty()) {
      throw new MalformedRequestException("Missing or empty member: document");
    }
    try {
      return Base64.getDecoder().decode(message.document());
    } catch (IllegalArgumentException e) {
      throw new MalformedRequestException("Member is not valid base64: document");
    }
  }

  /** Media type of a document in the given format. */
  static String mediaType(FormatId format) {
    return format.isChEmed() ? "application/fhir+json" : MediaType.APPLICATION_JSON_VALUE;
  }
}
