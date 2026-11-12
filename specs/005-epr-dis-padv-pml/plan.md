# Implementation Plan: CH EMED EPR, further document types (DIS, PADV, PML)

**Branch**: `005-epr-dis-padv-pml` | **Date**: 2026-11-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-epr-dis-padv-pml/spec.md`

## Summary

The format `ch-emed-epr` of feature 004 gains the three remaining document types of CH EMED EPR
3.0.0:
- DIS ↔ Medication Dispense;
- PADV ↔ Pharmaceutical Advice;
- PML ↔ Medication List.

All three convert in both directions. CH EMED → PML applies only to lists with a Device author
(clarification 2026-11-10).

**Approach**: Everything is reused from feature 004:
- the profile map, which already covers all 34 EPR profiles (R2);
- the pinned list of unsupported elements (R3);
- validation through `MappingContext`;
- the options, without new ones.

New are:
- the three types in `EprDocumentType` (R1);
- per-type rules for required data: the treatment-plan reference of a dispense, the Device
  author of a list (R4);
- a consistency check of the references within one document, which only reports, with WARNING
  `REFERENCE_INCONSISTENT` (R5);
- the original representation of the new types: kept, never rendered (R6).

Outside the EPR module, the only behaviour change is the message of the existing CHMED16A refusal,
which now names the supported types (R7).

## Technical Context

**Language/Version**: Java 21 LTS

**Primary Dependencies**:
- Existing modules `converter-core`, `converter-validation`, `format-ch-emed-epr`,
  `format-chmed16a`, `converter-cli` and `converter-rest`.
- HAPI FHIR 8.12.1.
- No new library and no new IG package.

**Storage**: N/A (FR-017).

**Testing**:
- JUnit 5 and AssertJ.
- Fixture tests per rule.
- Official DIS, PADV and PML examples of both IGs (R8) and synthetic variants.
- Round trips and determinism.
- Parity, privacy and no-network tests extended to the new corpus.
- `PerformanceTest` for SC-004.

**Target Platform**: JVM 21 (Linux, Windows); CLI and REST thin JARs with `lib/`, OCI image.

**Project Type**: Library with CLI and web service (Maven multi-module); no new module.

**Performance Goals**: SC-004 is a PML with 100 entries in total (clarified 2026-11-12, research F7), to CH EMED and back,
including validation, within SC-005b of feature 001 (< 30 s warm), measured on 4 vCPUs (R11).

**Constraints**:
- Byte-identical output (FR-012).
- No network, no PHI in logs or reports (FR-016/017).
- No direct pair with CHMED16A (feature 004 FR-004).
- Existing conversions unchanged, except the refusal message of R7 and the turning of the former
  refusals of DIS, PADV and PML into conversions (FR-014).

**Scale/Scope**:
- 3 document types in both directions.
- 1 new reason code (`REFERENCE_INCONSISTENT`).
- 3 new rules (`M-EPR-04`, `-28`, `-29`) and 3 extended rules (`-02`, `-23`, `-25`).
- 9 official EPR examples and 7 CH EMED examples.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Gate (constitution v1.5.0) | Status | Evidence |
|---|----------------------------|--------|----------|
| I | Output validates without errors against the pinned CH EMED EPR profiles; versions pinned; official validator | PASS | No new package; input and output validated as in feature 004 (FR-004/005) |
| II | No silent loss; capability gaps documented; single mapping specification; no defaults on clinical data | PASS | R3: unsupported elements removed and reported, also in changed resources. R4: treatment-plan reference only from source or option; PML author never invented. R5: contradictions reported, data unchanged. R7: CHMED16A gap documented. R10: rules in `docs/mapping/ch-emed-epr.md`, traceability test |
| III | Test-first; fixture tests per rule; official examples; round trips | PASS | R8: all official DIS/PADV/PML examples of both IGs plus synthetic cases; round trips in both directions (FR-011) |
| IV | Local only; no PHI; synthetic or official data | PASS | No external calls (FR-017); reports carry paths only, also for reference contradictions (FR-008); no-network and leak tests extended |
| V | Hub model; deterministic; layers; simplest design | PASS | Still only `ch-emed ↔ ch-emed-epr`; no new option, module or form; the reference check is one class in the EPR module |
| Scope | Same behaviour in library, CLI and REST | PASS | R9: types picked up through `documentTypes()`; parity test over the new corpus |
| Tech | Java 21; HAPI FHIR; FHIR JSON | PASS | No new dependency |
| Workflow | Changelog for output, code and message changes | PASS | R10: new types, `REFERENCE_INCONSISTENT`, CHMED16A message, OpenAPI 1.2.1 |

Post-design re-check (after Phase 1): **PASS**, no violations.

## Project Structure

### Documentation (this feature)

```text
specs/005-epr-dis-padv-pml/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── library-api.md   # additions (one reason code, document types)
│   ├── cli.md           # no new options; formats output
│   └── rest-api.md      # openapi.yaml 1.2.0 → 1.2.1
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
converter-core/src/main/java/ch/emed/converter/core/report/ReasonCode.java   # + REFERENCE_INCONSISTENT

format-ch-emed-epr/src/main/java/ch/emed/converter/epr/
├── EprDocumentType.java              # + DIS, PADV, PML; LATER removed (R1)
├── ChEmedEprFormatModule.java        # documentTypes() six types; reference check after mapping
└── mapping/
    ├── RequiredData.java             # per-type rules: DIS treatment plan (option), PML Device author (R4)
    ├── OriginalRepresentation.java   # render only for PRE and PMLC (R6)
    ├── ReferenceConsistency.java     # NEW: checks of R5, WARNING REFERENCE_INCONSISTENT
    ├── ProfileMap.java               # unchanged unless the examples show otherwise (R2)
    └── UnsupportedElements.java      # unchanged; covered by tests on the new types (R3)

format-chmed16a/src/main/java/ch/emed/converter/chmed16a/
├── io/ChEmedDocumentReader.java      # refusal names the supported types (R7)
└── Chmed16aFormatModule.java         # same message (R7)

specs/002-rest-conversion-service/contracts/openapi.yaml   # 1.2.1: documentTypes description and example
docs/mapping/ch-emed-epr.md           # M-EPR-04, -28, -29; -02, -23, -25 extended; required data per type
docs/mapping/ch-emed-epr-glossary.md  # dispense, pharmaceutical advice, medication list, document reference
docs/mapping/chmed16a-ch-emed.md      # capability gap: DIS, PADV, PML do not reach CHMED16A
docs/architecture.md                  # six document types
docs/operations/rest-service.md       # SC-004 measurement
CHANGELOG.md                          # [Unreleased]

tests:
├── format-ch-emed-epr:   fixtures/official (+9 EPR examples), fixtures/chemed (+4 CH EMED examples),
│                         per-rule fixtures, ReferenceConsistencyTest, round trips, determinism,
│                         EdgeCasesIT adjusted (DIS/PADV/PML now convert), PerformanceTest SC-004
├── format-chmed16a:      refusal message names the supported types
├── converter-cli:        formats lists six document types
└── converter-rest:       capabilities, parity, leak and no-network tests over the new corpus
```

**Structure Decision**: No new module. All mapping changes stay in `format-ch-emed-epr`. The core
gains one reason code; `format-chmed16a` gets one message change.

## Complexity Tracking

No violations or additions requiring justification.
