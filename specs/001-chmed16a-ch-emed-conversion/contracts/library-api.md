# Contract: Java Library API (converter-core)

Public, framework-free API used by the CLI, the REST service (feature 002) and embedding
systems. Entity semantics: [data-model.md](../data-model.md) §1.

## Entry point

```java
public interface Converter {
    ConversionResult convert(ConversionRequest request);
    Set<ConversionPair> supportedConversions();          // e.g. CHMED16A→CH_EMED, CH_EMED→CHMED16A
    Map<FormatId, String> pinnedSpecifications();        // e.g. CH_EMED → "ch.fhir.ig.ch-emed#6.0.0"
}

public final class Converters {
    /** Discovers FormatModules via ServiceLoader and builds the validation context once. */
    public static Converter create();
    public static Converter create(ChEmedValidator validator);   // e.g. shared/warm context
}
```

## Request / result

```java
public record ConversionRequest(FormatId source, FormatId target, byte[] input,
                                ConversionOptions options) {}

public record ConversionOptions(boolean strict, boolean validateOutput,
                                IdStrategy idStrategy, Optional<Clock> clock,
                                long maxDecodedSize, String defaultLanguage,
                                Confidentiality confidentiality) {
    // strict=false, validateOutput=true, maxDecodedSize=5 MB, defaultLanguage="de-CH",
    // confidentiality=NORMAL
    public static ConversionOptions defaults();
}

public record ConversionResult(Outcome outcome, Optional<byte[]> output,
                               ConversionReport report) {}
```

## Behavioural contract

1. `convert` never throws for invalid input or unsupported conversions; these are reported as
   `FAILED` with ERROR entries. It throws only for programming errors (null request) or an
   unusable validation context (`IllegalStateException`).
2. `output` is present iff `outcome != FAILED`.
3. Same request bytes + options + pinned versions → byte-identical `output` and equal `report`.
4. Thread-safe: one `Converter` instance may serve concurrent requests (needed by 002).
5. No network access, no file writes, no static mutable state.
6. A pair not involving `CH_EMED` is executed as source→CH_EMED→target (Principle V); the
   report contains entries of both legs.

## Format SPI (for format modules)

```java
public interface FormatModule {
    FormatId format();
    /** Parse + validate input; returns CH EMED Bundle or FAILED report entries. */
    MappingResult<Bundle> toChEmed(byte[] input, MappingContext ctx);
    /** Map a validated CH EMED Bundle to this format's serialized form. */
    MappingResult<byte[]> fromChEmed(Bundle document, MappingContext ctx);
}
```

`MappingContext` carries options, `IdStrategy`, optional `Clock` and the `ReportCollector`.
Modules are registered in `META-INF/services/ch.emed.converter.core.spi.FormatModule`.
