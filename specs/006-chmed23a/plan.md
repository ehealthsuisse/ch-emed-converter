# Implementation Plan: CHMED23A ↔ CH EMED

**Branch**: `006-chmed23a` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-chmed23a/spec.md`

## Summary

CHMED23A, the second eMediplan format, is added as a format of its own and converts in both
directions with CH EMED 6.0.0:
- medication plan (`medType` 1) ↔ Medication Card;
- prescription (`medType` 3) ↔ Medication Prescription.

PolymedicationCheck is refused. The format is read as plain JSON, QR payload (`CHMED23A.`),
chunked payload (text only) and QR image. It is written as JSON, QR payload and QR image, never in
chunks. IG eMediplan has withdrawn the format, so it is marked as deprecated in the capabilities
and the documentation.

**Approach**:
- A new module `format-chmed23a` with its own object model, reader and writer, conformance
  validator, transmission format and mappings (R2).
- The CH EMED side that CHMED16A already has moves to `converter-core`, unchanged, so that both
  eMediplan modules use it:
  - reading CH EMED documents;
  - profiles and labels;
  - the unit table.
- The specification has no JSON schema. Conformance is a rule list derived from the pinned
  object model, posology and terminology, and traced against the .NET reference implementation
  (R7).
- The richer posology is structured in CH EMED only where an exact equivalent exists. Everything
  else is kept as text and reported with clinical loss (R9).

## Technical Context

**Language/Version**: Java 21 LTS

**Primary Dependencies**:
- Existing modules `converter-core`, `converter-qr`, `converter-validation`, `converter-cli` and
  `converter-rest`.
- `format-chmed16a`: shared helpers move out of it.
- HAPI FHIR 8.12.1, Jackson (already used for CHMED16A), ZXing/BoofCV through `converter-qr`.
- No new library and no new IG package.

**Storage**: N/A (FR-017).

**Testing**:
- JUnit 5 and AssertJ.
- Fixture tests per rule (C23-*, M23-*).
- The 4 official examples, the posology fragments, and transcribed .NET examples (R13).
- Round trips in both directions; determinism of JSON, payload and image.
- Parity, privacy and no-network tests extended to the new corpus.
- A traceability test for the mapping specification.
- `PerformanceTest` for SC-005.

**Target Platform**: JVM 21 (Linux, Windows); CLI and REST thin JARs with `lib/`, OCI image.

**Project Type**: Library with CLI and web service (Maven multi-module); one new format module.

**Performance Goals**: SC-005: a plan with 20 medicaments, to CH EMED and back including
validation, within SC-005b of feature 001 (< 30 s warm), measured on 4 vCPUs (R14).

**Constraints**:
- Byte-identical output, including GZip without timestamp (FR-012).
- No network, no PHI in logs or reports (FR-016/017).
- Only `chmed23a ↔ ch-emed`, no direct pair with other formats (FR-004).
- CHMED16A behaviour unchanged except the refusal message for CHMED23A input (R3).

**Scale/Scope**:
- 2 document types in both directions.
- 3 input forms plus chunks; 3 output forms.
- 5 posology detail types, 6 timed-dosage types, 3 dosage types, 2 sequence types.
- 1 new reason code (`CHUNK_INVALID`).
- About 30 conformance rules and 60 mapping rules (estimate).
- 4 official documents plus about 40 posology fixtures.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Gate (constitution v1.5.0) | Status | Evidence |
|---|----------------------------|--------|----------|
| I | Output conforms to the target specification, "including any schema"; versions pinned | PASS | Pinned commit `0b579aa6` (R1). No schema exists, so conformance is the rule list C23-* from object model, posology and terminology, checked on input and output (R7, FR-007). CH EMED output validated as for CHMED16A (FR-008). QR payload as in the ChTransmissionFormat (R4); symbol parameters as for CHMED16A, provisionally (R6) |
| II | No silent loss; gaps documented; one mapping specification; no clinical defaults | PASS | Inexact posology becomes text with clinical loss (R9); medical data and extensions reported (R11); `sic`, sub-day durations, non-UUID ids reported (R8, R10). Default "Number 1" not materialised (R10). Rules in `docs/mapping/chmed23a-ch-emed.md` (R15) |
| III | Test-first; fixture per rule; official examples; round trips; QR at payload and image level | PASS | R13: all 4 examples, all posology fragments, transcribed .NET examples; round trips both ways with asserted differences; payload, chunk and image fixtures |
| IV | Local only; no PHI; synthetic or official data | PASS | No external calls; paths only in reports, also for chunks (R5) and extensions (R11); the examples are synthetic and MIT-licensed (R1) |
| V | Hub model; deterministic; layers; simplest design | PASS, with note | Only `chmed23a ↔ ch-emed`. A new module is justified by the separate object model (R2). Moving CH EMED helpers to the core avoids a module-to-module dependency. The eMediplan QR input is not dispatched automatically by version prefix: each module recognises the other version and names the right source (R3), because the source format is part of the conversion pair in every interface |
| Scope | Same behaviour in library, CLI and REST | PASS | R12: picked up through `FormatModule`; parity test over the corpus |
| Tech | Java 21; HAPI FHIR; FHIR JSON; no invented codes | PASS | No new dependency. Code tables vendored from `terminology.md`. Route and method codes mapped only where unambiguous, and `relMeal` 4–7 as text without a code (R9) |
| Workflow | Changelog for outputs, codes, messages; contract versions | PASS | R15: new format, `CHUNK_INVALID`, `FormatInfo.deprecated`, OpenAPI 1.3.0, CHMED16A message |

Post-design re-check (after Phase 1): **PASS**, no violations. The note on V is a reading of the
eMediplan QR scope line, not a deviation: the caller still selects the CHMED version (constitution,
"target CHMED version … selectable").

## Project Structure

### Documentation (this feature)

```text
specs/006-chmed23a/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── library-api.md   # FormatId.CHMED23A, FormatInfo.deprecated, CHUNK_INVALID
│   ├── cli.md           # --from/--to chmed23a; formats output
│   └── rest-api.md      # openapi.yaml 1.2.1 → 1.3.0
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
pom.xml                                         # + module format-chmed23a (after format-chmed16a)

converter-core/src/main/java/ch/emed/converter/core/
├── api/FormatId.java                           # + CHMED23A
├── api/FormatInfo.java                         # + deprecated (R12)
├── spi/FormatModule.java                       # + default deprecated()
├── report/ReasonCode.java                      # + CHUNK_INVALID
└── chemed/                                     # NEW package, moved from format-chmed16a (R2)
    ├── ChEmedDocumentReader.java
    ├── Profiles.java
    ├── Labels.java
    ├── Narratives.java
    ├── Units.java                              # CDTYP 9 table, from DosageToFhirMapper.UNITS
    └── MappingRule.java

format-chmed16a/                                # imports changed; message of M-IN-04/M-QR-04 (R3)

format-chmed23a/src/main/java/ch/emed/converter/chmed23a/
├── Chmed23aFormatModule.java                   # forms, document types, deprecated, toChEmed/fromChEmed
├── io/
│   ├── InputFormDetector.java                  # R3
│   ├── TransmissionFormat.java                 # payload, chunks (R4, R5)
│   ├── Chmed23aJsonReader.java                 # JSON → model, unknown fields reported
│   └── Chmed23aJsonWriter.java                 # model → JSON, fixed property order, no empties
├── model/                                      # records: Medication, Patient, PatientId, MedicalData,
│                                               # RiskCategory, Medicament, Posology, PosologyDetail
│                                               # (sealed), TimedDosage (sealed), DosageObject (sealed),
│                                               # SequenceObject (sealed), Application*, Repetition (sealed),
│                                               # Extension, HealthcarePerson, HealthcareOrganization
├── validation/
│   ├── Chmed23aValidator.java                  # C23-* (R7)
│   └── Terminology.java                        # vendored CDTYP 9/61/62 and risks
└── mapping/
    ├── MedicationCardBuilder.java              # MP → Medication Card
    ├── MedicationPrescriptionBuilder.java      # Rx → Medication Prescription (R10)
    ├── PatientMapper.java, AuthorMapper.java   # R11, R8
    ├── PosologyToFhirMapper.java               # R9
    ├── PosologyText.java                       # localized description of any posology
    └── reverse/
        ├── MedicationPlanBuilder.java, PrescriptionBuilder.java
        ├── PatientReverseMapper.java, AuthorReverseMapper.java
        └── PosologyFromFhirMapper.java
format-chmed23a/src/main/resources/
├── META-INF/services/ch.emed.converter.core.spi.FormatModule
└── chmed23a/terminology/*.csv                  # generated once from terminology.md (R7)

converter-cli/pom.xml, converter-rest/pom.xml   # + dependency format-chmed23a
converter-cli/.../FormatsCommand.java           # "; deprecated by IG eMediplan"
converter-cli/.../ConvertCommand.java           # help text of --output-form
converter-rest/.../CapabilitiesResponse.java    # + deprecated
converter-rest/.../CapabilitiesController.java  # + deprecated, output-form text
specs/002-rest-conversion-service/contracts/openapi.yaml   # 1.3.0

docs/mapping/chmed23a-ch-emed.md                # NEW mapping specification (R15)
docs/mapping/chmed16a-ch-emed.md                # M-IN-04, M-QR-04 messages
docs/specifications.md, docs/architecture.md, docs/third-party-licenses.md,
docs/operations/rest-service.md, README.md, CHANGELOG.md

tests:
├── converter-core:     moved helper tests
├── format-chmed16a:    unchanged expectations (byte-identical), message test for CHMED23A input
├── format-chmed23a:    fixtures/chmed23a/{official,posology,dotnet,synthetic}, fixtures/qr,
│                       fixtures/expected; per-rule tests, validator tests, transmission tests,
│                       round trips, determinism, EdgeCasesIT, traceability, PerformanceTest
├── converter-cli:      formats line, convert with each form
└── converter-rest:     capabilities (deprecated), OpenAPI served, parity, leak, no-network
```

**Structure Decision**: The new module `format-chmed23a` holds everything specific to CHMED23A.
CH EMED-side helpers move to `converter-core` (package `chemed`), where both eMediplan modules use
them. The CLI and REST pick the module up through the service loader and gain only the deprecated
mark.

## Complexity Tracking

| Addition | Why needed | Simpler alternative rejected because |
|----------|------------|--------------------------------------|
| New module `format-chmed23a` | CHMED23A has its own object model, naming and posology | Adding it to `format-chmed16a` mixes two models and makes the withdrawn format hard to remove (R2) |
| Package `core.chemed` (moved code) | Both eMediplan modules read and write CH EMED the same way | Copying would let the two diverge; a dependency between format modules couples their releases (R2) |
| `FormatInfo.deprecated` | FR-015 requires the mark in the capabilities | A report entry per conversion adds noise without information (R12) |
