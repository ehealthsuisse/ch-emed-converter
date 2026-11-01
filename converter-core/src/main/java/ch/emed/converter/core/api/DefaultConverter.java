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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hub pipeline: source → CH EMED → target (constitution V). Stateless and thread-safe; logs only
 * phase, outcome, entry counts, duration and input size (data-model.md §5).
 */
public final class DefaultConverter implements Converter {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultConverter.class);

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
    MappingContext ctx = new MappingContext(request.options(), report);

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
                entries));
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
                  "Conversion " + source.id() + " -> " + target.id() + " is not supported"));
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

  @Override
  public Set<ConversionPair> supportedConversions() {
    Set<ConversionPair> pairs = new LinkedHashSet<>();
    List<FormatModule> modules = registry.modules();
    for (FormatModule module : modules) {
      pairs.add(new ConversionPair(module.format(), FormatId.CH_EMED));
      pairs.add(new ConversionPair(FormatId.CH_EMED, module.format()));
    }
    for (FormatModule from : modules) {
      for (FormatModule to : modules) {
        if (!from.format().equals(to.format())) {
          pairs.add(new ConversionPair(from.format(), to.format()));
        }
      }
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
