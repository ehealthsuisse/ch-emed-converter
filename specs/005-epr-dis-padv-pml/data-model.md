# Data Model: CH EMED EPR, further document types

Phase 1 of [plan.md](plan.md). Decisions R1–R11 are in [research.md](research.md); the data model
of feature 004 ([data-model.md](../004-ch-emed-epr/data-model.md)) stays in force.

## 1. Document types (FR-001, FR-002)

| CH EMED 6.0.0 document | CH EMED EPR 3.0.0 | SNOMED CT | `document` | `treatment-plans` |
|------------------------|-------------------|-----------|------------|-------------------|
| Medication Card | PMLC | `736378000` | both directions (feature 004) | CH EMED → MTP collection |
| Medication Prescription | PRE | `761938008` | both directions (feature 004) | refused |
| Medication Treatment Plan | MTP | `761931002` | both directions (feature 004) | refused |
| Medication Dispense | DIS | `82291000195104` | both directions | refused |
| Pharmaceutical Advice | PADV | `419891008` | both directions | refused |
| Medication List | PML | `721912009` | both directions; CH EMED → PML only with a Device author | refused |

Any other type: `UNSUPPORTED_DOCUMENT_TYPE`, message lists the six types (FR-003).

`FormatInfo.documentTypes()` for `ch-emed-epr`: `PMLC, PRE, MTP, DIS, PADV, PML`.

## 2. Required data per type (R4)

| Type | Author (EPR) | Treatment-plan reference | Option used |
|------|--------------|--------------------------|-------------|
| DIS | PractitionerRole, Patient, RelatedPerson or Device | MedicationDispense: required | `treatmentPlanReferences`, exactly one when the dispense has none |
| PADV | as DIS | Observation: optional, kept | – |
| PML | Device only | as the `-list` profiles require, from the source | – |

Patient identifier, author details and confidentiality are handled as in feature 004.

Failure: `MISSING_REQUIRED_DATA` naming the path, never values. A wrong number of references gives
`TREATMENT_PLAN_REFERENCES_MISMATCH`.

## 3. Document references (FR-007, FR-008)

A **document reference** is one of the CH EMED extensions `ch-emed-ext-treatmentplan`,
`ch-emed-ext-prescription`, `ch-emed-ext-dispense` and `ch-emed-ext-pharmaceuticaladvice`. Each
holds:
- `id`: the identifier of the referenced entry (MedicationStatement, MedicationRequest,
  MedicationDispense, Observation);
- `externalDocumentId`: the identifier of the document that holds that entry.

Both are `urn:uuid` identifiers. They are copied unchanged in both directions.

**Consistency rules** (R5, rule M-EPR-04), checked within one document only:

| # | Rule | Applies to |
|---|------|-----------|
| C1 | One entry `id` appears with only one `externalDocumentId` | all types |
| C2 | A reference to an entry of the same list names the list's document for it | PML |
| C3 | The changed statement or request belongs to the referenced treatment plan or prescription | PADV |
| C4 | A dispense and the prescription it references name the same treatment plan | PML |

A violation is reported as WARNING `REFERENCE_INCONSISTENT`, with the paths of both extensions
concerned. The output does not change.

## 4. Reason codes

New:

| Code | Severity | When |
|------|----------|------|
| `REFERENCE_INCONSISTENT` | WARNING | a rule C1–C4 is violated (R5); no clinical loss |

Reused with new uses:

| Code | New use |
|------|---------|
| `MISSING_REQUIRED_DATA` | dispense without treatment-plan reference and without option; list without Device author |
| `TREATMENT_PLAN_REFERENCES_MISMATCH` | for a dispense, more than one reference given, or one given although the dispense has its own |
| `UNSUPPORTED_DOCUMENT_TYPE` | CHMED16A refusal of DIS, PADV and PML now names the supported types (R7) |
| `UNMAPPABLE_ELEMENT` | unsupported elements of the dispense, advice and list profiles, including changed resources |

## 5. Original representation (R6)

| Type | Supplied PDF/A | Source PDF/A | Neither |
|------|----------------|--------------|---------|
| PRE, PMLC | used | kept | rendered as PDF/A-1b (feature 004) |
| MTP, DIS, PADV, PML | used | kept | none |

A PDF that is not PDF/A is not used, and the report says so.
