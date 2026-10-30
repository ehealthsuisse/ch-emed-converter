# Architecture

Overview of the CH EMED converter. The diagrams are [Mermaid](https://mermaid.js.org/) and render
on GitHub and in most Markdown viewers. Contracts: `specs/001-chmed16a-ch-emed-conversion/contracts/`,
mapping rules: [mapping/chmed16a-ch-emed.md](mapping/chmed16a-ch-emed.md).

## Principles

- **CH EMED (FHIR R4) is the hub.** Every external format converts to and from CH EMED; formats never
  convert directly into each other.
- **Core knows no format.** `converter-core` defines the API, the report and two SPIs; formats and
  the validator are plugged in with `ServiceLoader`.
- **Framework-free and offline.** No web or application framework, no network call at runtime
  (enforced by the Maven enforcer rules); the CH EMED packages are vendored.
- **Nothing is dropped silently.** Every loss, default or derived value ends up in the
  `ConversionReport`; `--strict` turns clinical loss into a failure.

## Modules

```mermaid
flowchart TB
    subgraph clients["Clients"]
        CLI["converter-cli<br/>picocli command line<br/>ch-emed-converter.jar + lib/"]
        EMB["Embedding systems<br/>(Java library)"]
        REST["converter-rest<br/>Spring Boot REST/JSON service<br/>ch-emed-converter-rest.jar"]
    end

    subgraph core["converter-core (framework-free)"]
        API["API<br/>Converter · Converters<br/>ConversionRequest / Result / Options"]
        PIPE["DefaultConverter<br/>pipeline + report"]
        REP["Report<br/>ConversionReport · ReasonCode<br/>OutcomeCalculator"]
        SPI["SPI<br/>FormatModule · ChEmedValidator"]
        UTIL["Shared<br/>IdStrategy (deterministic ids)<br/>PDF renderer (PDFBox)<br/>ChEmedJson (HAPI R4)"]
    end

    subgraph fmt["format-chmed16a"]
        FM["Chmed16aFormatModule"]
        IO["io<br/>JSON / QR reader and writer"]
        FWD["mapping<br/>CHMED16A → CH EMED"]
        REV["mapping.reverse<br/>CH EMED → CHMED16A"]
        VAL16["validation<br/>CHMED16A schema + eRezept rules"]
    end

    subgraph val["converter-validation"]
        HV["HapiChEmedValidator<br/>(HAPI / HL7 validator, offline)"]
        PKG[("Vendored IG packages<br/>ch-core · ch-emed · ch-term<br/>hl7 extensions")]
    end

    CLI --> API
    EMB --> API
    REST --> API
    API --> PIPE
    PIPE --> REP
    PIPE --> SPI
    PIPE --> UTIL
    FM -. "implements via ServiceLoader" .-> SPI
    HV -. "implements via ServiceLoader" .-> SPI
    FM --> IO
    FM --> FWD
    FM --> REV
    FM --> VAL16
    HV --> PKG
```

Dependencies in the POMs: `format-chmed16a` and `converter-validation` depend on `converter-core`;
`converter-cli` depends on all three (the format module at runtime). `converter-rest` depends on
`converter-core` and, at runtime, on the format and validation modules; it is the only module with
Spring dependencies (Spring Boot BOM imported in its own POM, enforced by the parent's
`enforce-framework-free` rule for all other modules). `converter-core` depends on neither.
Operation of the service: [operations/rest-service.md](operations/rest-service.md).

## Conversion pipeline

`DefaultConverter` runs one pipeline for both directions; every step can add entries to the report.
CH EMED is always one end of a conversion, the other end is a format module (here CHMED16A). The two
directions are shown separately.

### Format → CH EMED (e.g. CHMED16A → CH EMED)

```mermaid
sequenceDiagram
    autonumber
    actor Caller as CLI / library caller
    participant C as DefaultConverter
    participant F as FormatModule (CHMED16A)
    participant V as ChEmedValidator
    participant R as ReportCollector

    Caller->>C: convert(request: CHMED16A → CH_EMED, bytes, options)
    C->>F: toChEmed(bytes, ctx)
    Note over F: PARSE: decode JSON / QR payload, check schema<br/>MAP: build Medication Card or Prescription Bundle
    F-->>R: DERIVED_VALUE, UNMAPPABLE_ELEMENT, ...
    F-->>C: Bundle
    Note over C: errors so far ⇒ stop, no output
    opt validateOutput
        C->>V: validate(bundle, VALIDATE_OUTPUT)
        V-->>R: FHIR_VALIDATION entries
    end
    C->>C: serialize Bundle as FHIR JSON
    C->>R: OutcomeCalculator(entries, strict)
    C-->>Caller: ConversionResult(outcome, CH EMED JSON, report)
```

### CH EMED → Format (e.g. CH EMED → CHMED16A)

```mermaid
sequenceDiagram
    autonumber
    actor Caller as CLI / library caller
    participant C as DefaultConverter
    participant V as ChEmedValidator
    participant F as FormatModule (CHMED16A)
    participant R as ReportCollector

    Caller->>C: convert(request: CH_EMED → CHMED16A, bytes, options)
    C->>C: PARSE: read FHIR JSON Bundle
    C->>V: validate(bundle, VALIDATE_INPUT)
    V-->>R: FHIR_VALIDATION entries
    Note over C: errors so far ⇒ stop, no output
    C->>F: fromChEmed(bundle, ctx)
    Note over F: MAP: reverse mapping to Medication Plan or Prescription,<br/>CHMED16A schema and eRezept rules on the result
    F-->>R: reverse mapping entries (capability gaps, eRezept requirements)
    F-->>C: CHMED16A JSON bytes
    C->>R: OutcomeCalculator(entries, strict)
    C-->>Caller: ConversionResult(outcome, CHMED16A JSON, report)
```

The outcome is `SUCCESS`, `SUCCESS_WITH_WARNINGS` or `FAILED`; no output is returned for `FAILED`.
With `strict`, clinical loss turns the outcome into `FAILED`. Reports carry paths and codes only,
never patient values (privacy, see README "Logging (privacy)").

## Formats and extensions: implemented and planned

CH EMED is the hub; each format is a `FormatModule` of its own. Conversions between two non-CH-EMED
formats, if ever offered, are composed through CH EMED (constitution, "hub model"). Items marked
*planned* are not implemented; they are named in the constitution and in the feature specs, the
scope and timing are not decided yet.

```mermaid
flowchart LR
    CHMED16A["CHMED16A<br/>eMediplan plan and prescription<br/>(implemented, feature 001)"]
    HUB(("CH EMED 6.0.0<br/>HL7 FHIR R4<br/>hub model"))
    CHMED23A["CHMED23A<br/>(planned, separate feature)"]
    CARA["CARA EMED<br/>(planned)"]
    ERX["eRezept Schweiz QR code / URL<br/>(planned, builds on feature 001)"]
    EPR["CH EMED EPR<br/>profile variant<br/>(planned)"]

    CHMED16A <--> HUB
    CHMED23A <-.-> HUB
    CARA <-.-> HUB
    ERX -.-> CHMED16A
    HUB -.-> EPR

    classDef planned stroke-dasharray: 5 5
    class CHMED23A,CARA,ERX,EPR planned
```

| Item | Status | Notes |
|---|---|---|
| CHMED16A ↔ CH EMED | implemented (feature 001) | medication plan (MedType 1) and prescription (MedType 3); the prescription output is eRezept-ready |
| REST service | implemented (feature 002) | plain REST/JSON API on top of `converter-core` (`converter-rest`); documents base64-encoded, same options as the CLI; no access control or TLS in the service |
| CHMED23A ↔ CH EMED | planned | separate, later feature; CHMED23A was withdrawn by IG eMediplan but stays in the constitution's scope |
| CARA EMED | planned | named in the constitution as a format whose official examples join the test corpus |
| eRezept QR code / URL | planned | QR images, QR payload creation and signed eRezept URLs (`https://eprescription.hin.ch/#CHMED16A1…`); feature 001 only decodes the textual `CHMED16A1…` payload |
| CH EMED EPR | planned | feature 001 targets the general CH EMED IG; the EPR variant would be a separate feature |

Out of scope for now: the CHMED16A PolymedicationCheck document type.

## Adding a format

1. New module `format-<name>` that depends on `converter-core`.
2. Implement `FormatModule` (`toChEmed`, `fromChEmed`, `pinnedSpecification`) and register it in
   `META-INF/services/ch.emed.converter.core.spi.FormatModule`.
3. Document the rules in `docs/mapping/` and pin the specification version.
4. Put the module on the classpath of the client (CLI, REST service, embedding application);
   `Converters.create()` discovers it.

No change to `converter-core` is needed.
