package ch.emed.converter.core.api;

import ca.uhn.fhir.parser.DataFormatException;
import ch.emed.converter.core.fhir.ChEmedJson;
import ch.emed.converter.core.report.ConversionReport;
import ch.emed.converter.core.report.Outcome;
import ch.emed.converter.core.report.OutcomeCalculator;
import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import ch.emed.converter.core.spi.ChEmedValidator;
import ch.emed.converter.core.spi.FormatModule;
import ch.emed.converter.core.spi.FormatRegistry;
import ch.emed.converter.core.spi.MappingContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hub pipeline: format → CH EMED or CH EMED → format (constitution V); pairs of two other formats
 * are refused, not composed (feature 004, FR-004). Stateless and thread-safe; logs only phase,
 * outcome, entry counts, duration and input size (data-model.md §5).
 */
public final class DefaultConverter implements Converter {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultConverter.class);

  /** Start of a signed eRezept URL (feature 003), recognised for the CH EMED source hint. */
  private static final String EREZEPT_URL_PREFIX = "https://eprescription.hin.ch/";

  private final FormatRegistry registry;
  private final ChEmedValidator validator;
  private final String version;

  /**
   * Creates a converter.
   *
   * @param registry format modules
   * @param validator CH EMED validator; may be null only if every request disables output
   *     validation
   */
  public DefaultConverter(FormatRegistry registry, ChEmedValidator validator) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.validator = validator;
    this.version = loadVersion();
  }

  @Override
  public ConversionResult convert(ConversionRequest request) {
    Objects.requireNonNull(request, "request");
    long start = System.nanoTime();
    ReportCollector report = new ReportCollector();
    MappingContext ctx = new MappingContext(request.options(), report, validator);

    byte[] output = run(request, ctx);

    List<ReportEntry> entries = report.entries();
    Outcome outcome = OutcomeCalculator.calculate(entries, request.options().strict());
    ConversionResult result =
        new ConversionResult(
            outcome,
            outcome == Outcome.FAILED ? Optional.empty() : Optional.ofNullable(output),
            ConversionReport.of(
                version,
                request.source(),
                request.target(),
                specificationsFor(request.source(), request.target()),
                outcome,
                entries),
            outcome == Outcome.FAILED || output == null ? Optional.empty() : ctx.signature());
    LOG.info(
        "conversion {}->{} outcome={} entries={} inputBytes={} durationMs={}",
        request.source().id(),
        request.target().id(),
        outcome,
        entries.size(),
        request.input().length,
        (System.nanoTime() - start) / 1_000_000);
    return result;
  }

  private byte[] run(ConversionRequest request, MappingContext ctx) {
    FormatId source = request.source();
    FormatId target = request.target();
    Optional<FormatModule> sourceModule =
        source.isChEmed() ? Optional.empty() : registry.find(source);
    Optional<FormatModule> targetModule =
        target.isChEmed() ? Optional.empty() : registry.find(target);
    if (source.equals(target)
        || (!source.isChEmed() && sourceModule.isEmpty())
        || (!target.isChEmed() && targetModule.isEmpty())) {
      ctx.report()
          .add(
              ReportEntry.error(
                  ReasonCode.UNSUPPORTED_CONVERSION,
                  Phase.PARSE,
                  "/",
                  "Conversion "
                      + source.id()
                      + " -> "
                      + target.id()
                      + " is not supported; supported: "
                      + supportedList()));
      return null;
    }
    if (!source.isChEmed() && !target.isChEmed()) {
      // Feature 004, FR-004: no composed conversions; the caller converts in two steps.
      ctx.report()
          .add(
              ReportEntry.error(
                  ReasonCode.UNSUPPORTED_CONVERSION,
                  Phase.PARSE,
                  "/",
                  "Conversion "
                      + source.id()
                      + " -> "
                      + target.id()
                      + " is not supported; supported: "
                      + supportedList()
                      + "; "
                      + throughChEmedHint(source, target)));
      return null;
    }
    OutputForm form = request.options().outputForm();
    List<OutputForm> offered =
        target.isChEmed() ? List.of(OutputForm.DOCUMENT) : targetModule.get().outputForms();
    if (!offered.contains(form)) {
      ctx.report()
          .add(
              ReportEntry.error(
                  ReasonCode.UNSUPPORTED_OUTPUT_FORM,
                  Phase.PARSE,
                  "/",
                  "Output form "
                      + form.externalName()
                      + " is not offered for target "
                      + target.id()));
      return null;
    }
    boolean validate = request.options().validateOutput();
    if (validate && validator == null) {
      throw new IllegalStateException("No ChEmedValidator available but output validation is on");
    }

    Bundle document;
    if (source.isChEmed()) {
      document = parseChEmed(request.input(), ctx);
      if (document == null) {
        return null;
      }
      if (validator != null) {
        ctx.report().addAll(validator.validate(document, Phase.VALIDATE_INPUT));
      }
    } else {
      document = sourceModule.get().toChEmed(request.input(), ctx);
    }
    if (document == null || ctx.report().hasErrors()) {
      return null;
    }

    if (target.isChEmed()) {
      if (validate) {
        ctx.report().addAll(validator.validate(document, Phase.VALIDATE_OUTPUT));
      }
      return ChEmedJson.serialize(document);
    }
    return targetModule.get().fromChEmed(document, ctx);
  }

  private static Bundle parseChEmed(byte[] input, MappingContext ctx) {
    String otherForm = otherInputForm(input);
    if (otherForm != null) {
      ctx.report()
          .add(
              ReportEntry.error(
                  ReasonCode.PARSE_ERROR,
                  Phase.PARSE,
                  "/",
                  "Input is a " + otherForm + ", not a FHIR JSON Bundle; use source chmed16a"));
      return null;
    }
    try {
      return ChEmedJson.parse(input);
    } catch (DataFormatException | ClassCastException e) {
      ctx.report()
          .add(
              ReportEntry.error(
                  ReasonCode.PARSE_ERROR, Phase.PARSE, "/", "Input is not a FHIR JSON Bundle"));
      return null;
    }
  }

  /**
   * Recognises the transmission forms of CHMED (feature 003, FR-013) by their first bytes, so that
   * a wrongly declared source gets a hint instead of a FHIR parse error. Never returns content.
   */
  private static String otherInputForm(byte[] input) {
    if (startsWith(input, 0x89, 'P', 'N', 'G') || startsWith(input, 0xFF, 0xD8, 0xFF)) {
      return "QR code image";
    }
    int from = 0;
    while (from < input.length && Character.isWhitespace(input[from])) {
      from++;
    }
    String head =
        new String(input, from, Math.min(input.length - from, 32), StandardCharsets.ISO_8859_1)
            .toLowerCase(Locale.ROOT);
    if (head.startsWith(EREZEPT_URL_PREFIX)) {
      return "eRezept URL";
    }
    if (head.startsWith("chmed")) {
      return "QR payload";
    }
    return null;
  }

  private static boolean startsWith(byte[] input, int... prefix) {
    if (input.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if ((input[i] & 0xFF) != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Hint for a pair of two formats other than CH EMED (feature 004, research R11), e.g. {@code
   * convert through ch-emed: chmed16a -> ch-emed -> ch-emed-epr}.
   *
   * @param source source format
   * @param target target format
   * @return the hint
   */
  public static String throughChEmedHint(FormatId source, FormatId target) {
    return "convert through ch-emed: "
        + source.externalName()
        + " -> "
        + FormatId.CH_EMED.externalName()
        + " -> "
        + target.externalName();
  }

  private String supportedList() {
    return supportedConversions().stream()
        .map(p -> p.source().id() + " -> " + p.target().id())
        .sorted()
        .collect(Collectors.joining(", "));
  }

  @Override
  public List<FormatInfo> formats() {
    List<FormatInfo> formats = new ArrayList<>();
    formats.add(new FormatInfo(FormatId.CH_EMED, List.of("json"), List.of(OutputForm.DOCUMENT)));
    for (FormatModule module : registry.modules()) {
      formats.add(new FormatInfo(module.format(), module.inputForms(), module.outputForms()));
    }
    formats.sort(Comparator.comparing(f -> f.format().id()));
    return List.copyOf(formats);
  }

  @Override
  public Set<ConversionPair> supportedConversions() {
    Set<ConversionPair> pairs = new LinkedHashSet<>();
    for (FormatModule module : registry.modules()) {
      pairs.add(new ConversionPair(module.format(), FormatId.CH_EMED));
      pairs.add(new ConversionPair(FormatId.CH_EMED, module.format()));
    }
    return Set.copyOf(pairs);
  }

  @Override
  public Map<String, String> pinnedSpecifications() {
    Map<String, String> specs = new TreeMap<>();
    if (validator != null) {
      specs.put(FormatId.CH_EMED.id(), validator.pinnedSpecification());
    }
    for (FormatModule module : registry.modules()) {
      specs.put(module.format().id(), module.pinnedSpecification());
    }
    return Map.copyOf(specs);
  }

  private Map<String, String> specificationsFor(FormatId source, FormatId target) {
    Map<String, String> all = pinnedSpecifications();
    Map<String, String> used = new TreeMap<>();
    for (FormatId format : List.of(source, target)) {
      String spec = all.get(format.id());
      if (spec != null) {
        used.put(format.id(), spec);
      }
    }
    if (!source.isChEmed() && !target.isChEmed() && all.containsKey(FormatId.CH_EMED.id())) {
      used.put(FormatId.CH_EMED.id(), all.get(FormatId.CH_EMED.id()));
    }
    return used;
  }

  @Override
  public String version() {
    return version;
  }

  private static String loadVersion() {
    Properties props = new Properties();
    try (InputStream in =
        DefaultConverter.class.getResourceAsStream("/specifications.properties")) {
      if (in != null) {
        props.load(in);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read specifications.properties", e);
    }
    return props.getProperty("converter.version", "0.0.0-UNKNOWN");
  }
}
