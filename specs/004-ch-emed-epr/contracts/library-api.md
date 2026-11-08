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
```

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
