# Implementation Plan: CH EMED EPR (CARA EMED)

**Branch**: `004-ch-emed-epr` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-ch-emed-epr/spec.md`

## Summary

CH EMED EPR 3.0.0, the FHIR IG that CARA maintains for the eMedication service of its EPR
community, becomes a format of its own. It converts only to and from the CH EMED hub
(clarification 2026-11-08). It covers three document types:
- PMLC ↔ Medication Card;
- PRE ↔ Medication Prescription;
- MTP ↔ medication treatment plan.

On request (output form `treatment-plans`), a card becomes one MTP document per treatment,
delivered as a FHIR `collection` Bundle (R6).

**Approach**:
- A new module `format-ch-emed-epr` implements the existing `FormatModule` SPI (R1).
- The mapping is a change of the declared profiles. CH EMED → EPR additionally:
  - removes the elements CH EMED EPR does not support, as a pinned list taken from the package
    (R5);
  - keeps only a PDF/A original representation (R8);
  - fills data the EPR requires, from the existing options only (R9).
- Input and output are validated against the pinned CH EMED EPR package with the existing
  validator, reached through `MappingContext` (R3).
- Library, CLI and REST pick the format up through `Converter.formats()`. The only interface
  changes are additive: the new form `treatment-plans` and OpenAPI 1.2.0 (R11).

## Technical Context

**Language/Version**: Java 21 LTS

**Primary Dependencies**:
- Existing modules: `converter-core`, `converter-validation`, `converter-cli` and `converter-rest`
  (features 001–003).
- HAPI FHIR 8.12.1 (R4 structures, JSON, validator).
- New vendored IG packages, not libraries: `ch.fhir.ig.ch-emed-epr#3.0.0` and
  `hl7.fhir.uv.tools.r4#0.9.0`, both CC0-1.0 (R2).
- No new library.

**Storage**: N/A. Nothing is stored beyond a request (FR-016).

**Testing**:
- JUnit 5 and AssertJ, as before.
- One fixture test per `M-EPR-*` rule.
- Official examples of the IG and the feature 001 corpus, in both directions.
- Round trips, MTP count and determinism.
- Regression of the existing corpus with the EPR package loaded (R3).
- Extended parity, privacy and no-network tests (R12).

**Target Platform**: JVM 21 (Linux, Windows). CLI thin JAR with `lib/`; REST thin JAR and OCI image.

**Project Type**: Library with CLI and web service (Maven multi-module); one new module.

**Performance Goals**:
- SC-005: a card with 20 medications is converted and validated to PMLC within the limits of
  feature 001 (SC-005b < 30 s warm; SC-005c < 5 s for 20 medications).
- The same card to `treatment-plans` (20 MTP validations) is measured separately (R14).
- The start-up time of the validation context with the EPR package is measured and recorded.

**Constraints**:
- Byte-identical output (FR-011).
- No network, no PHI in logs or reports (FR-015/016).
- No direct conversion between CH EMED EPR and CHMED16A (FR-004).
- Existing operations byte-identical with the EPR package loaded (FR-013, R3).
- No generated PDFs (R8).

**Scale/Scope**:
- 1 format, 3 document types in both directions, 1 output form.
- 2 new reason codes (`NO_TREATMENTS`, `TREATMENT_PLAN_EXISTS`).
- About 20 mapping rules `M-EPR-*`.
- About 17 official examples.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Gate (constitution v1.5.0) | Status | Evidence |
|---|----------------------------|--------|----------|
| I | CH EMED EPR output validates without errors against the pinned CH EMED EPR profiles and those of CH EMED and CH Core; versions pinned; official validator | PASS | R2: `ch.fhir.ig.ch-emed-epr#3.0.0` pinned with SHA-256, on the already pinned CH EMED 6.0.0 / CH Core 6.0.0. R3: same HL7 validator, input and output validated, output with errors not returned |
| II | No silent loss; capability gaps per direction documented; single mapping specification; no defaults on clinical data | PASS | R5: unsupported elements removed and reported, clinical ones flagged as clinical loss. R8: non-PDF/A representation reported. R9: missing data only from existing options, otherwise `MISSING_REQUIRED_DATA`. R4: `docs/mapping/ch-emed-epr.md` with rule ids, checked by `MappingTraceabilityTest` |
| III | Test-first; fixture tests per rule; official examples in the corpus; round trips | PASS | R12: tests before code, official PMLC/PRE/MTP examples of the IG as fixtures, round trips in both directions against the documented changes |
| IV | Local only; no PHI in logs; synthetic or official example data; no contact with EPR services | PASS | Offline validation as before. Report entries carry paths only. Fixtures: official IG examples and the synthetic corpus of feature 001. No MHD or CARA service calls (FR-016); no-network test extended |
| V | Hub model; deterministic; layers; simplest design | PASS | R1: format ↔ CH EMED only; direct pairs refused (FR-004). R10: UUID v5, no clock. The module is a mapping layer; `converter-core` gains one enum value, one format id constant and a validator accessor on `MappingContext` |
| Scope | Every conversion via library, CLI and REST with identical behaviour | PASS | R11: no new options; `treatment-plans` uses the existing `outputForm`; parity test extended (SC-006) |
| Tech | Java 21; HAPI FHIR; official validator; FHIR JSON | PASS | No new library; EPR serialised as FHIR JSON (constitution "Domain & Technical Constraints") |
| Workflow | Changelog for output/code changes | PASS | New format, form and reason codes recorded in `CHANGELOG.md` under 1.1.0 (R13) |

Post-design re-check (after Phase 1): **PASS**. No violations. One design addition is listed under
Complexity Tracking for review.

## Project Structure

### Documentation (this feature)

```text
specs/004-ch-emed-epr/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── library-api.md   # additions to the library API (features 001/003)
│   ├── cli.md           # additions to the CLI contract
│   └── rest-api.md      # changes to specs/002-rest-conversion-service/contracts/openapi.yaml (1.1.0 → 1.2.0)
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
pom.xml                                   # + module format-ch-emed-epr
tools/fetch-packages.sh                   # + ch.fhir.ig.ch-emed-epr#3.0.0, hl7.fhir.uv.tools.r4#0.9.0
docs/
├── specifications.md                     # + CH EMED EPR section
├── mapping/ch-emed-epr.md                # NEW: rules M-EPR-*, unsupported elements, reason codes
├── architecture.md                       # CARA EMED / CH EMED EPR merged and implemented, module diagram
├── third-party-licenses.md               # + IG packages (CC0-1.0)
└── operations/rest-service.md            # new format and form

converter-core/src/main/java/ch/emed/converter/core/
├── api/FormatId.java                     # + CH_EMED_EPR
├── api/OutputForm.java                   # + TREATMENT_PLANS ("treatment-plans")
├── api/DefaultConverter.java             # validator into MappingContext; hint "convert through ch-emed" for pairs of two modules
├── spi/MappingContext.java               # + validator() (Optional<ChEmedValidator>)
└── report/ReasonCode.java                # + NO_TREATMENTS, TREATMENT_PLAN_EXISTS

converter-validation/src/main/resources/packages/
├── manifest.json                         # + 2 packages with SHA-256 and load flag
└── *.tgz                                 # + 2 vendored packages

format-ch-emed-epr/                       # NEW
├── pom.xml                               # converter-core, HAPI structures; enforcer framework-free
└── src/main/java/ch/emed/converter/epr/
    ├── ChEmedEprFormatModule.java        # FormatModule: forms json / document, treatment-plans; validation via ctx
    ├── EprDocumentType.java              # PMLC, PRE, MTP ↔ CH EMED document types; profile URLs
    ├── mapping/ProfileMap.java           # CH EMED ↔ EPR profile URLs per resource (R4)
    ├── mapping/ToChEmedMapper.java       # EPR → CH EMED (M-EPR-1x)
    ├── mapping/FromChEmedMapper.java     # CH EMED → EPR (M-EPR-0x)
    ├── mapping/UnsupportedElements.java  # pinned list of R5, removal and report
    ├── mapping/RequiredData.java         # R9: patient, author, confidentiality from options
    ├── mapping/OriginalRepresentation.java   # R8: PDF/A detection
    └── mapping/TreatmentPlanSplitter.java    # R6/R7: card → collection of MTP documents
    src/main/resources/epr/unsupported-elements.json   # pinned list (R5)
    src/main/resources/META-INF/services/ch.emed.converter.core.spi.FormatModule

tests (per module, src/test/...):
├── converter-core:       MappingContext validator, hint for module pairs, new reason codes
├── format-ch-emed-epr:   rule fixtures, official examples (fixtures/official/), feature 001 corpus,
│                         round trips, splitter, unsupported-list check against the package,
│                         regression of the existing corpus, PerformanceTest SC-005
├── converter-cli:        --from/--to ch-emed-epr, --output-form treatment-plans
└── converter-rest:       contract, capabilities, parity (SC-006), privacy (SC-007), no network (SC-008)
```

**Structure Decision**:
- One new module `format-ch-emed-epr`, beside `format-chmed16a`, depending on `converter-core`
  only. It gets the validator through `MappingContext`, so it does not depend on
  `converter-validation`.
- `converter-cli` and `converter-rest` add it as a runtime dependency, like `format-chmed16a`.
- The EPR package is loaded by `converter-validation`.
- No module gains a framework dependency.

## Complexity Tracking

| Addition | Why Needed | Simpler Alternative Rejected Because |
|----------|------------|--------------------------------------|
| `MappingContext.validator()` | The EPR module must validate its input and output against the EPR profiles (FR-006/007) with the converter's validator | A second validator in the module doubles the start-up time and memory; validation in the core for "FHIR-based modules" needs a new SPI concept (R3) |
| Output form `treatment-plans` with a `collection` Bundle | FR-003 needs several documents from one card | Several outputs in `ConversionResult` change the result type in all three interfaces; one call per treatment needs an index option and gives n reports (R6) |
