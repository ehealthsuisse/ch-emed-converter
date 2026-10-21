# Implementation Plan: CHMED16A ↔ CH EMED Conversion

**Branch**: `001-chmed16a-ch-emed-conversion` | **Date**: 2026-10-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-chmed16a-ch-emed-conversion/spec.md`

## Summary

Bidirectional conversion between eMediplan CHMED16A (medication plan, prescription) and
CH EMED 6.0.0 (Medication Card, Medication Prescription document), delivered as a Java 21
library and CLI. The converter is built around CH EMED as the canonical hub model: a
framework-free core defines the conversion API, conversion report and format SPI; the
`format-chmed16a` module parses, validates, maps and serializes CHMED16A; the
`converter-validation` module validates CH EMED output offline with HAPI FHIR's wrapper of the
official HL7 validator and vendored IG packages. CHMED16A prescriptions produced are
eRezept-Schweiz-ready (signable by the HIN erxproxy unchanged). The REST service (feature 002)
will reuse the same core.

## Technical Context

**Language/Version**: Java 21 LTS

**Primary Dependencies**: HAPI FHIR 8.12.x (structures-r4, validation, caching-caffeine),
Jackson (CHMED16A JSON), Apache PDFBox 3.0.x (original-representation PDF, in converter-core),
picocli (CLI), SLF4J (+ Logback in CLI only)

**Storage**: N/A (stateless; vendored FHIR NPM packages as read-only classpath resources)

**Testing**: JUnit 5, AssertJ, JSONAssert; official `validator_cli.jar` as CI cross-check

**Target Platform**: Any JVM 21 platform (Linux, Windows, macOS); CLI as runnable JAR

**Project Type**: Library + CLI (Maven multi-module)

**Performance Goals**: 100-medication plan converted in < 2 s (SC-005a) and its CH EMED output
validated in < 15 s with a warm validation context (SC-005b); cold start measured separately
(research R10)

**Constraints**: Fully offline (no tx server, no HIN/eRezept calls); byte-deterministic output;
no PHI in logs/reports; core free of web/application frameworks

**Scale/Scope**: 2 document types × 2 directions; ~40 CHMED16A fields; reference corpus of
official examples plus ≥ 1 synthetic fixture per mapping rule and edge case

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Gate (constitution v1.4.0) | Status | Evidence |
|---|----------------------------|--------|----------|
| I | Output conforms to target spec; versions pinned; official validators authoritative | PASS | R2 pins CH EMED 6.0.0 + dependency closure, CHMED16A commit SHA, eRezept 0.10.2; R4 uses HL7 validator core offline; R5 hand-written CHMED16A checks justified (no official schema) |
| II | No silent loss; structured report; capability gaps per direction; single mapping spec; no defaults | PASS | Report model in [data-model.md](./data-model.md); `docs/mapping/chmed16a-ch-emed.md` (R6); FR-009/011 enforced via strict mode and error rules |
| III | Test-first; fixture per rule; official examples; round trips | PASS | R11 corpus; round-trip and determinism tests in [quickstart.md](./quickstart.md); tasks will order tests before implementation |
| IV | Local only; no PHI in logs/reports; synthetic fixtures; no HIN/eRezept calls | PASS | R4 (no tx server), report uses paths only, logging rules in data model; no network client dependency |
| V | CH EMED hub; deterministic; stateless core; thin CLI; YAGNI | PASS | Format SPI converts only to/from CH EMED; R8 determinism; core has no framework deps; 4 modules justified below |
| Scope | Delivery via library, CLI and REST | PASS (partial by design) | Library + CLI here; REST in feature 002 — feature counts as complete only after 002 (constitution "Delivery interfaces") |
| Tech | Java 21, HAPI FHIR, official validator, licences reviewed | PASS | Technical Context; all deps Apache-2.0/MIT/EPL-compatible |

Post-design re-check (after Phase 1): **PASS** — no violations; Complexity Tracking documents the
module split only for transparency.

## Project Structure

### Documentation (this feature)

```text
specs/001-chmed16a-ch-emed-conversion/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── library-api.md
│   ├── cli.md
│   └── conversion-report.schema.json
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks - not created here)
```

### Source Code (repository root)

```text
pom.xml                                  # parent: Java 21, dependency BOM, plugin config
mvnw, .mvn/                              # Maven Wrapper
docs/
├── specifications.md                    # pinned versions / commit SHAs (R2)
└── mapping/
    └── chmed16a-ch-emed.md              # mapping specification incl. capability gaps (R6)

converter-core/                          # framework-free API, hub model, report, SPI
├── src/main/java/ch/emed/converter/core/
│   ├── api/          # Converter, ConversionRequest, ConversionResult, ConversionOptions, FormatId
│   ├── report/       # ConversionReport, ReportEntry, Severity, Outcome, ReasonCode
│   ├── spi/          # FormatModule, ChEmedValidator (interfaces), FormatRegistry (ServiceLoader)
│   └── determinism/  # IdStrategy (UUID v5), Clock injection
└── src/test/java/...

converter-validation/                    # CH EMED validation (HAPI FhirInstanceValidator)
├── src/main/java/ch/emed/converter/validation/
├── src/main/resources/packages/         # vendored .tgz of CH EMED 6.0.0 dependency closure
└── src/test/java/...

format-chmed16a/                         # CHMED16A model, parser, validator, mappers, serializer
├── src/main/java/ch/emed/converter/chmed16a/
│   ├── model/        # Medication, Patient, MedicalData, Medicament, Posology, TakingTime, …
│   ├── io/           # Jackson parser/serializer (spec field order)
│   ├── validation/   # CHMED16A rules, eRezept prescription rules
│   └── mapping/      # ToChEmed (plan→Card, Rx→Prescription), FromChEmed (reverse), DosageMapper
└── src/test/
    ├── java/...      # unit tests per mapping rule, round-trip, determinism, PHI-in-report scan
    └── resources/fixtures/{chmed16a,chemed,expected}/

converter-cli/                           # picocli entry point, thin layer over core
├── src/main/java/ch/emed/converter/cli/
└── src/test/java/...                    # CLI contract tests (exit codes, report JSON)
```

**Structure Decision**: Maven multi-module project at the repository root. `converter-core`
holds only the hub API and SPI (depends on HAPI structures for the CH EMED model, nothing else);
format modules plug in via `ServiceLoader`, so CHMED23A, CARA EMED and QR/eRezept become new
modules without touching existing ones; feature 002 adds `converter-rest` on the same core.
Java base package `ch.emed.converter` (confirmed for now, 2026-10-18). CLI exit codes confirmed:
warnings → 0, failures → 1 (see [contracts/cli.md](./contracts/cli.md)).

## Complexity Tracking

No constitution violations. For transparency:

| Decision | Why Needed | Simpler Alternative Rejected Because |
|----------|------------|-------------------------------------|
| 4 Maven modules instead of 1 | Core must stay framework-free and reusable by REST (002); heavy validator deps isolated; formats added as plug-ins | Single module would couple CLI/Logback and validator packages into every consumer and make the hub boundary unenforced |
