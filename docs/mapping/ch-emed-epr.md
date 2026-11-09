# Mapping specification: CH EMED EPR ↔ CH EMED

**Version**: 0.1.0 (in progress, feature 004: CH EMED EPR → CH EMED done; CH EMED → CH EMED EPR
and treatment plans follow) | **CH EMED EPR**: 3.0.0 | **CH EMED**: 6.0.0 — see
[pinned specifications](../specifications.md)

This is the mapping specification of the format `ch-emed-epr` (CARA EMED), required by
constitution principle II. Every rule has a stable id `M-EPR-<NN>`; code of the module
`format-ch-emed-epr` annotated with `@MappingRule` references these ids, and
`MappingTraceabilityTest` of the module checks that both sides match.

Paths are FHIRPath expressions on the document Bundle, for example
`Bundle.entry[3].resource.meta.profile`. CH EMED EPR converts only to and from CH EMED; a pair with
another format (for example `chmed16a`) is refused by the converter with `UNSUPPORTED_CONVERSION`
and the hint "convert through ch-emed" (FR-004).

## Document types

| CH EMED 6.0.0 | CH EMED EPR 3.0.0 | Composition `type` (SNOMED CT) |
|---------------|-------------------|-------------------------------|
| Medication Card | PMLC | `736378000` |
| Medication Prescription | PRE | `761938008` |
| Medication Treatment Plan | MTP | `761931002` |

Dispense (DIS, `82291000195104`), pharmaceutical advice (PADV, `419891008`) and medication list
(PML, `721912009`) follow in a later feature.

## General rules

| Id | Rule |
|----|------|
| M-EPR-01 | Profiles: every CH EMED EPR profile is replaced by the profile named in its `baseDefinition`, and back. Document, Composition, MedicationStatement (card, list, changed), MedicationRequest, MedicationDispense, Medication, Observation, Organization, Practitioner and PractitionerRole map to the CH EMED profile of the same name; `ch-emed-epr-medicationstatement-treatmentplan` ↔ `ch-emed-medicationstatement` (MTP only); `ch-emed-epr-patient` ↔ `ch-core-patient-epr`; `ch-emed-epr-related-person` ↔ `ch-core-relatedperson`; `ch-core-organization` → `ch-emed-epr-organization` (towards EPR only). `ch-emed-epr-device` and `ch-emed-epr-observation-body-weight` have a FHIR core base: towards CH EMED the profile is dropped. Other profiles are kept. Each resource whose profiles change → INFORMATION `PROFILE_CHANGED`. |
| M-EPR-02 | The document type is recognised by the SNOMED CT code of the Composition `type`. DIS, PADV and PML → `UNSUPPORTED_DOCUMENT_TYPE` "not yet supported"; any other → `UNSUPPORTED_DOCUMENT_TYPE`; both name the supported types. |
| M-EPR-03 | CH EMED EPR input and output are validated with the converter's validator, which loads `ch.fhir.ig.ch-emed-epr#3.0.0` (research R3). Input is always validated (FR-007); errors → `FHIR_VALIDATION` in phase `VALIDATE_INPUT`, no output. Without a validator → `VALIDATION_INCOMPLETE`. |

## CH EMED EPR → CH EMED

| Id | Rule |
|----|------|
| M-EPR-11 | The input must be a FHIR JSON document Bundle (else `PARSE_ERROR`) and must declare a CH EMED EPR document profile. A Bundle without one is probably general CH EMED, which the validator would accept against its own profiles → `PARSE_ERROR` "use source ch-emed". |
| M-EPR-12 | A valid CH EMED EPR document is a CH EMED document with stricter constraints: only the declared profiles change (M-EPR-01); all other content is copied unchanged. Verified for all 17 official PMLC, PRE and MTP examples (research F3). The result is validated as CH EMED output by the converter. |

## Capability gaps

- A PMLC converted to CH EMED keeps its author, the aggregation service of the community (a Device
  without GLN). The conversion of this card to CHMED16A requires the GLN of the author (M-REV-04 of
  `chmed16a-ch-emed.md`) and fails with `MISSING_REQUIRED_DATA` at `Composition.author`. The
  author option is not used for this (decision 2026-11-09).

## Reason codes

| Code | Severity | Use |
|------|----------|-----|
| `PROFILE_CHANGED` | INFORMATION | declared profiles of a resource replaced (M-EPR-01); defined by this module |
| `UNSUPPORTED_DOCUMENT_TYPE` | ERROR | document type outside this feature (M-EPR-02) |
| `PARSE_ERROR` | ERROR | not a document Bundle, or no CH EMED EPR document profile (M-EPR-11) |
| `FHIR_VALIDATION` | per finding | validation of input and output (M-EPR-03) |
| `VALIDATION_INCOMPLETE` | ERROR | no validator available (M-EPR-03) |
