# Contract changes: Library API (converter-core)

Additions to the [feature 001 library contract](../../001-chmed16a-ch-emed-conversion/contracts/library-api.md)
and the [feature 003 changes](../../003-qr-erezept/contracts/library-api.md). Everything is
additive: existing types, constructors and behaviour stay as they are.

## New values

```java
FormatId.CH_EMED_EPR        // "ch-emed-epr"; provided by the module format-ch-emed-epr
OutputForm.TREATMENT_PLANS  // "treatment-plans"; offered only by CH_EMED_EPR
ReasonCode.NO_TREATMENTS          // ERROR
ReasonCode.TREATMENT_PLAN_EXISTS  // WARNING
ReasonCode.TREATMENT_PLAN_REFERENCES_MISMATCH  // ERROR
```

## New option (FR-017, research R15)

```java
/** Treatment plan in the EPR that a prescribed medication or card entry belongs to. */
public record TreatmentPlanReference(String documentId, String statementId) {}
// both "urn:uuid:<uuid>"; anything else throws IllegalArgumentException

// ConversionOptions
public List<TreatmentPlanReference> treatmentPlanReferences()   // default: empty list
public ConversionOptions withTreatmentPlanReferences(List<TreatmentPlanReference> value)
public Optional<byte[]> originalRepresentation()               // default: empty
public ConversionOptions withOriginalRepresentation(byte[] pdf)  // PDF/A-1 or -2 expected (R16)
```

`treatmentPlanReferences` is used only by target `CH_EMED_EPR` for PRE and PMLC: one reference
per entry without one, in section order. `originalRepresentation` is used only by target
`CH_EMED_EPR` for PRE, PMLC and MTP with form `DOCUMENT`; with `TREATMENT_PLANS` it is not used
and reported (research R16). Other targets ignore both options. Existing constructors and
`defaults()` keep working.

## Renderer (FR-018, research R16)

```java
// OriginalRepresentationRenderer
public static byte[] renderPdfA(Bundle bundle, ReportCollector report)   // PDF/A-1b
```

`render` (feature 001) is unchanged.

## MappingContext

```java
/** The converter's validator, if one is configured; used by modules whose format is FHIR. */
public Optional<ChEmedValidator> validator()
```

`DefaultConverter` passes its validator when it creates the context. Format modules that do not
need it ignore it.

## Behaviour

| Request | Result |
|---------|--------|
| `CH_EMED_EPR → CH_EMED`, PMLC/PRE/MTP | CH EMED document, validated as CH EMED output |
| `CH_EMED → CH_EMED_EPR`, Card/Prescription/MTP, `DOCUMENT` | PMLC/PRE/MTP document, validated against CH EMED EPR |
| `CH_EMED → CH_EMED_EPR`, Card, `TREATMENT_PLANS` | `collection` Bundle of MTP documents (data model §5) |
| `CH_EMED → CH_EMED_EPR`, Prescription or Card without references and without the option | `FAILED`, `MISSING_REQUIRED_DATA` naming the entries |
| … with a number of references that does not match | `FAILED`, `TREATMENT_PLAN_REFERENCES_MISMATCH` |
| … with a PDF/A supplied by `originalRepresentation` | supplied PDF/A used, `DERIVED_VALUE` |
| … with a supplied PDF that is not PDF/A | not used, WARNING `UNMAPPABLE_ELEMENT` without clinical loss; next step of research R16 |
| … with original representation missing or not PDF/A | PDF/A-1b rendered from the document, `DERIVED_VALUE` |
| `CH_EMED → CH_EMED_EPR`, `TREATMENT_PLANS` with `originalRepresentation` | option not used, WARNING `UNMAPPABLE_ELEMENT` |
| `CHMED16A ↔ CH_EMED_EPR` | `FAILED`, `UNSUPPORTED_CONVERSION`, message names the path through `CH_EMED` |
| `TREATMENT_PLANS` with another target | `FAILED`, `UNSUPPORTED_OUTPUT_FORM` (existing check) |

`Converter.formats()` lists `ch-emed-epr` with input forms `[json]` and output forms
`[document, treatment-plans]`. `pinnedSpecifications()` contains
`CH_EMED_EPR=ch.fhir.ig.ch-emed-epr#3.0.0` for conversions that involve the format.

## Example

```java
Converter converter = Converters.create();
ConversionResult plans = converter.convert(new ConversionRequest(
    FormatId.CH_EMED, FormatId.CH_EMED_EPR, cardJson,
    ConversionOptions.defaults().withOutputForm(OutputForm.TREATMENT_PLANS)));
```
