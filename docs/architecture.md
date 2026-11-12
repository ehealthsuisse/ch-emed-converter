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
%%{init: {"flowchart": {"curve": "linear", "wrappingWidth": 400}, "markdownAutoWrap": false, "themeVariables": {"fontFamily": "Arial, Helvetica, sans-serif"}}}%%
flowchart TB
    CLI["<i>             client             </i><br/>converter-cli<br/>command line"]
    REST["<i>             client             </i><br/>converter-rest<br/>REST service"]
    EMB["<i>             client             </i><br/>embedding system<br/>Java library"]

    subgraph core[" "]
        API["<b>API</b><br/>Converter<br/>for clients"]
        PIPE["<i>core</i><br/><b>converter-core</b><br/>pipeline, report<br/>no format logic"]
        SPIF["<b>                   SPI                   </b><br/>FormatModule<br/>via ServiceLoader"]
        SPIV["<b>                   SPI                   </b><br/>ChEmedValidator<br/>via ServiceLoader"]
    end

    FMT["<i>                plug-in                </i><br/>format-chmed16a<br/>CHMED16A ↔ CH EMED"]
    EPRM["<i>                plug-in                </i><br/>format-ch-emed-epr<br/>CH EMED EPR ↔ CH EMED"]
    VAL["<i>                plug-in                </i><br/>converter-validation<br/>CH EMED, offline"]
    QR["<i>        supporting module        </i><br/>converter-qr<br/>QR image read/write"]
    PKG[("<i>bundled data</i><br/>IG packages<br/>ch-core, ch-emed, ch-term,<br/>ch-emed-epr")]

    CLI --> API
    REST --> API
    EMB --> API
    API --> PIPE
    PIPE --> SPIF
    PIPE --> SPIV
    SPIF --> FMT
    SPIF --> EPRM
    SPIV --> VAL
    FMT --> QR
    VAL --> PKG

    classDef client fill:#e8f5e9,stroke:#4c9a5b,color:#1b4332
    classDef port fill:#dbeafe,stroke:#1d4ed8,stroke-width:2px,color:#1e3a8a
    classDef corenode fill:#1d4ed8,stroke:#1e3a8a,stroke-width:3px,color:#ffffff
    classDef plugin fill:#fef3c7,stroke:#b45309,stroke-width:2px,color:#78350f
    classDef ext fill:#f3f4f6,stroke:#9ca3af,color:#374151

    class CLI,REST,EMB client
    class API,SPIF,SPIV port
    class PIPE corenode
    class FMT,EPRM,VAL plugin
    class QR,PKG ext
    style core fill:#eff6ff,stroke:#1d4ed8,stroke-width:2px
```

### Build dependencies

The diagram above shows who calls whom at runtime. The build dependencies in the POMs point
the other way: the plug-ins depend on `converter-core` because they implement its SPIs, while
`converter-core` depends on no other module of the project. This is why a new format can be
added without changing the core.

| Module | Depends on (project modules) | Notes |
|---|---|---|
| `converter-core` | none | HAPI FHIR structures, PDFBox |
| `converter-qr` | none | ZXing only |
| `format-chmed16a` | `converter-core`, `converter-qr` | QR images and eRezept URLs (feature 003) |
| `format-ch-emed-epr` | `converter-core` | validates through the validator of the mapping context and renders the PDF/A with `converter-core` |
| `converter-validation` | `converter-core` | HAPI FHIR validator, vendored IG packages |
| `converter-cli` | `converter-core`, `converter-validation`; `format-chmed16a` and `format-ch-emed-epr` at runtime | picocli |
| `converter-rest` | `converter-core`; `format-chmed16a`, `format-ch-emed-epr` and `converter-validation` at runtime | only module with Spring (Spring Boot BOM in its own POM) |

"At runtime" means the module is only put on the classpath, where `ServiceLoader` finds it;
the client's code does not refer to it. All modules except `converter-rest` are kept free of
web and application frameworks by the parent POM's `enforce-framework-free` rule.
Operation of the REST service: [operations/rest-service.md](operations/rest-service.md).

## Conversion pipeline

`DefaultConverter` runs one pipeline for both directions; every step can add entries to the report.
CH EMED is always one end of a conversion, the other end is a format module (here CHMED16A). The two
directions are shown separately.

### Format → CH EMED (e.g. CHMED16A → CH EMED)

```mermaid
%%{init: {"theme": "base", "look": "classic", "themeVariables": {"primaryColor": "#ffffff", "primaryBorderColor": "#6b7280", "primaryTextColor": "#111827", "lineColor": "#374151", "actorBkg": "#ffffff", "actorBorder": "#6b7280", "actorTextColor": "#111827", "actorLineColor": "#6b7280", "signalColor": "#374151", "signalTextColor": "#111827", "labelBoxBkgColor": "#ffffff", "labelBoxBorderColor": "#6b7280", "labelTextColor": "#111827", "loopTextColor": "#111827", "noteBkgColor": "#f9fafb", "noteBorderColor": "#9ca3af", "noteTextColor": "#111827", "sequenceNumberColor": "#ffffff"}}}%%
sequenceDiagram
    autonumber
    box rgb(232,245,233) client
        actor Caller as Client (CLI, REST service, library)
    end
    box rgb(239,246,255) converter-core
        participant C as DefaultConverter
        participant R as ReportCollector
    end
    box rgb(254,243,199) plug-ins
        participant F as FormatModule (format-chmed16a)
        participant V as ChEmedValidator (converter-validation)
    end

    rect rgb(255,255,255)
        Caller->>C: convert(CHMED16A → CH EMED, bytes, options)
        Note over C: CHECK: find FormatModule for CHMED16A,<br/>outputForm offered
        C->>F: toChEmed(bytes, ctx)
        Note over F: PARSE: detect input form (JSON, QR payload,<br/>QR image, eRezept URL)<br/>CHECK: CHMED16A schema, eRezept rules for prescriptions<br/>MAP: build Medication Card or Prescription Bundle
        F->>R: add entries (DERIVED_VALUE, UNMAPPABLE_ELEMENT, ...)
        F-->>C: Bundle (none if errors)
        opt no errors so far
            opt validateOutput (default on)
                C->>V: validate(Bundle, VALIDATE_OUTPUT)
                V-->>C: FHIR_VALIDATION entries
                C->>R: add entries
            end
            C->>C: serialize Bundle as FHIR JSON
        end
        C->>C: calculate outcome (strict), build ConversionReport
        C-->>Caller: ConversionResult
        Note over Caller,C: outcome, report, output (none if FAILED),<br/>raw eRezept signature data if any
    end
```

### CH EMED → Format (e.g. CH EMED → CHMED16A)

```mermaid
%%{init: {"theme": "base", "look": "classic", "themeVariables": {"primaryColor": "#ffffff", "primaryBorderColor": "#6b7280", "primaryTextColor": "#111827", "lineColor": "#374151", "actorBkg": "#ffffff", "actorBorder": "#6b7280", "actorTextColor": "#111827", "actorLineColor": "#6b7280", "signalColor": "#374151", "signalTextColor": "#111827", "labelBoxBkgColor": "#ffffff", "labelBoxBorderColor": "#6b7280", "labelTextColor": "#111827", "loopTextColor": "#111827", "noteBkgColor": "#f9fafb", "noteBorderColor": "#9ca3af", "noteTextColor": "#111827", "sequenceNumberColor": "#ffffff"}}}%%
sequenceDiagram
    autonumber
    box rgb(232,245,233) client
        actor Caller as Client (CLI, REST service, library)
    end
    box rgb(239,246,255) converter-core
        participant C as DefaultConverter
        participant R as ReportCollector
    end
    box rgb(254,243,199) plug-ins
        participant F as FormatModule (format-chmed16a)
        participant V as ChEmedValidator (converter-validation)
    end

    rect rgb(255,255,255)
        Caller->>C: convert(CH EMED → CHMED16A, bytes, options)
        Note over C: CHECK: find FormatModule for CHMED16A,<br/>outputForm offered<br/>PARSE: read FHIR JSON Bundle<br/>(QR image, QR payload or eRezept URL rejected with a hint)
        C->>V: validate(Bundle, VALIDATE_INPUT)
        V-->>C: FHIR_VALIDATION entries
        C->>R: add entries
        opt no errors so far
            C->>F: fromChEmed(Bundle, ctx)
            Note over F: MAP: reverse mapping to Medication Plan or Prescription<br/>CHECK: eRezept rules for prescriptions, CHMED16A schema<br/>WRITE: JSON, QR payload or QR image (outputForm,<br/>no QR image for prescriptions)
            F->>R: add entries (capability gaps, eRezept requirements)
            F-->>C: CHMED16A output (none if errors)
        end
        C->>C: calculate outcome (strict), build ConversionReport
        C-->>Caller: ConversionResult
        Note over Caller,C: outcome, report, output (none if FAILED)
    end
```

The outcome is `SUCCESS`, `SUCCESS_WITH_WARNINGS` or `FAILED`; no output is returned for `FAILED`.
With `strict`, clinical loss turns the outcome into `FAILED`. Reports carry paths and codes only,
never patient values (privacy, see README "Logging (privacy)").

The converter validates CH EMED input whenever a validator is available; the format module checks
its own input (for example the CHMED16A schema). CH EMED EPR input is always validated: without a
validator the conversion fails with `VALIDATION_INCOMPLETE` (feature 004, rule M-EPR-03). Output
validation is on by default and can only be switched off through the library API.

## Formats and extensions: implemented and planned

CH EMED is the hub; each format is a `FormatModule` of its own. Conversions between two non-CH-EMED
formats are not offered; the caller converts in two steps through CH EMED, and the converter names
that path when it refuses such a pair (constitution, "hub model"; feature 004 FR-004). Items marked
*planned* are not implemented; they are named in the constitution and in the feature specs, the
scope and timing are not decided yet.

```mermaid
%%{init: {"flowchart": {"curve": "linear"}}}%%
flowchart LR
    ERX["eMediplan QR code,<br/>eRezept Schweiz QR code / URL<br/>transmission forms of CHMED16A<br/>implemented, feature 003"]
    CHMED16A["CHMED16A<br/>eMediplan plan and prescription<br/>implemented, feature 001"]
    CHMED23A["CHMED23A<br/>planned, separate feature"]
    HUB(("CH EMED 6.0.0<br/>HL7 FHIR R4<br/>hub"))
    EPR["CH EMED EPR 3.0.0<br/>(CARA EMED)<br/>PMLC, PRE, MTP<br/>implemented, feature 004<br/>DIS, PADV, PML<br/>feature 005"]

    ERX <-->|read and write| CHMED16A
    CHMED16A <--> HUB
    CHMED23A <-.-> HUB
    HUB <--> EPR

    classDef hub fill:#1d4ed8,stroke:#1e3a8a,stroke-width:3px,color:#ffffff
    classDef plugin fill:#fef3c7,stroke:#b45309,stroke-width:2px,color:#78350f
    classDef form fill:#fffbeb,stroke:#b45309,color:#78350f
    classDef planned fill:#f3f4f6,stroke:#9ca3af,color:#374151,stroke-dasharray: 5 5

    class HUB hub
    class CHMED16A,EPR plugin
    class ERX form
    class CHMED23A planned
```

| Item | Status | Notes |
|---|---|---|
| CHMED16A ↔ CH EMED | implemented (feature 001) | medication plan (MedType 1) and prescription (MedType 3); the prescription output is eRezept-ready |
| REST service | implemented (feature 002) | plain REST/JSON API on top of `converter-core` (`converter-rest`); documents base64-encoded, same options as the CLI; no access control or TLS in the service |
| CHMED23A ↔ CH EMED | planned | separate, later feature; CHMED23A was withdrawn by IG eMediplan but stays in the constitution's scope |
| eMediplan QR code, eRezept QR code / URL | implemented (feature 003) | transmission forms of CHMED16A, not formats of their own: QR images (PNG/JPEG) and signed eRezept URLs (`https://eprescription.hin.ch/#CHMED16A1…`) are read, the signature data is returned raw and unverified beside the document; QR payloads and, for medication plans, QR images are written (option `outputForm`); prescriptions only as unsigned payload for the HIN erxproxy |
| CH EMED EPR (CARA EMED) ↔ CH EMED | implemented (features 004, 005) | CH EMED EPR 3.0.0 of CARA, the EPR variant of CH EMED; there is no separate CARA format (constitution 1.5.0). Medication card (PMLC), prescription (PRE) and treatment plan (MTP) in both directions; output form `treatment-plans` makes one MTP per treatment of a card; unsupported elements are removed and reported, the original representation is a PDF/A. Since feature 005 also dispense (DIS), pharmaceutical advice (PADV) and medication list (PML) in both directions; references to other documents are checked within each document and full URLs become `urn:uuid`. Rules: [mapping/ch-emed-epr.md](mapping/ch-emed-epr.md). |

Out of scope for now: the CHMED16A PolymedicationCheck document type.

## Adding a format

1. New module `format-<name>` that depends on `converter-core`.
2. Implement `FormatModule` (`toChEmed`, `fromChEmed`, `pinnedSpecification`) and register it in
   `META-INF/services/ch.emed.converter.core.spi.FormatModule`.
3. Document the rules in `docs/mapping/` and pin the specification version.
4. Put the module on the classpath of the client (CLI, REST service, embedding application);
   `Converters.create()` discovers it.

No change to `converter-core` is needed.
