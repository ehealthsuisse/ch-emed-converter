# Data Model: CH EMED EPR (CARA EMED)

Phase 1 of [plan.md](plan.md). Decisions referenced as R1–R14 are in [research.md](research.md).

## 1. Format and conversion pairs

| Item | Value |
|------|-------|
| Format id | `CH_EMED_EPR`, external name `ch-emed-epr` |
| Pinned specification | `ch.fhir.ig.ch-emed-epr#3.0.0` |
| Input forms | `json` (FHIR JSON document Bundle) |
| Output forms | `document` (default), `treatment-plans` |
| Offered pairs | `ch-emed-epr → ch-emed`, `ch-emed → ch-emed-epr` |
| Refused pairs | `chmed16a ↔ ch-emed-epr`: `UNSUPPORTED_CONVERSION` with the hint "convert through ch-emed" (FR-004) |

## 2. Document types (FR-002)

| CH EMED 6.0.0 document | CH EMED EPR 3.0.0 document | `document` | `treatment-plans` |
|------------------------|----------------------------|------------|-------------------|
| Medication Card | PMLC | both directions | CH EMED → collection of MTP documents |
| Medication Prescription | PRE | both directions | refused: `UNSUPPORTED_DOCUMENT_TYPE` |
| Medication Treatment Plan | MTP | both directions | refused: `UNSUPPORTED_DOCUMENT_TYPE` |
| Dispense, Pharmaceutical Advice, Medication List | DIS, PADV, PML | refused: `UNSUPPORTED_DOCUMENT_TYPE` (later feature) | refused |

The document type is recognised by the Composition `type` (SNOMED and LOINC codes of CH EMED) and,
for EPR input, by the declared document profile.

## 3. Reason codes

New:

| Code | Severity | When |
|------|----------|------|
| `NO_TREATMENTS` | ERROR | `treatment-plans` requested for a card without MedicationStatement (spec US3.3) |
| `TREATMENT_PLAN_EXISTS` | WARNING | a card entry already references a treatment plan; its MTP records the decision again (R7) |
| `TREATMENT_PLAN_REFERENCES_MISMATCH` | ERROR | the number of treatment-plan references differs from the number of entries without one (R15) |
| `PROFILE_CHANGED` | INFORMATION | declared profiles of a resource replaced (M-EPR-01); defined in the module (`EprReasonCode`), not in the core |

Reused, with the uses of this feature:

| Code | Severity | Use in this feature |
|------|----------|---------------------|
| `UNMAPPABLE_ELEMENT` | WARNING with clinical loss, or INFORMATION | element not supported by CH EMED EPR removed (R5); non-PDF/A original representation of an MTP left out (R8); supplied original representation not PDF/A, or not used with `treatment-plans` (R16), WARNING without clinical loss |
| `MISSING_REQUIRED_DATA` | ERROR | data required by EPR missing in source and options (R9) |
| `DERIVED_VALUE` | INFORMATION | data taken from an option (R9, R15); last considered document from the treatment-plan reference (R15); original representation supplied by the option or rendered as PDF/A (R16) |
| `PDF_CHARACTER_REPLACED` | INFORMATION | character missing in the PDF/A font (R16), as in feature 001 |
| `FHIR_VALIDATION` | per finding | validation of EPR input and output (R3) |
| `UNSUPPORTED_DOCUMENT_TYPE` | ERROR | document type outside §2 |
| `UNSUPPORTED_CONVERSION` | ERROR | direct pair with CHMED16A (FR-004) |
| `UNSUPPORTED_OUTPUT_FORM` | ERROR | `treatment-plans` with a target other than `ch-emed-epr` (existing check) |
| `PARSE_ERROR` | ERROR | not a FHIR JSON document Bundle; hint "use source ch-emed" for a general CH EMED document declared as `ch-emed-epr` (R11) |

## 4. Unsupported element list (R5)

Resource `format-ch-emed-epr/src/main/resources/epr/unsupported-elements.json`, extracted from the
vendored package and checked against it by a test. An element is listed when it is `draft` and its `short` starts
with "✕" (research F7); the datatype profiles are resolved into the resource profiles, and each
profile also lists a `slices` matcher per slice on a listed path:

```text
{ "package": "ch.fhir.ig.ch-emed-epr#3.0.0",
  "profiles": [
    { "profile": "<EPR profile URL>",
      "elements": [
        { "path": "<element path, e.g. Composition.identifier.period>",
          "scope": "all" | "pmlc",             // "pmlc": marked "will be removed in PML(C) documents"
          "clinical": true | false } ] } ] }   // set by hand, reviewed in docs/mapping/ch-emed-epr.md
```

The `clinical` flag is the only part not taken from the package. A test fails if the package lists
an element that the resource does not have, or the other way round.

## 5. Treatment-plan set (R6, R7)

Output of `ch-emed → ch-emed-epr` with `outputForm = treatment-plans`:

| Element | Value |
|---------|-------|
| `Bundle.type` | `collection` |
| `Bundle.identifier` | UUID v5 from the card's Composition identifier and the name `treatment-plans` |
| `Bundle.timestamp` | the card's Composition `date` |
| `Bundle.entry[i].resource` | MTP document Bundle for the card's MedicationStatement *i*, in section order |
| MTP Composition and Bundle identifiers | UUID v5 from the card's Composition identifier and *i* |
| Copied from the card | `language`, `confidentiality` and EPR extension, `subject`, `author`, `date` |
| Original representation | none; the option `originalRepresentation` is not used (WARNING, R16) |

Each MTP document validates on its own against the MTP document profile. The collection holds no
other resources, and the report covers all MTP documents.

## 6. Data required by CH EMED EPR (R9)

| EPR requirement | Source | Option | Otherwise |
|-----------------|--------|--------|-----------|
| Patient identifier with `system` and `value` | Patient | `patientIdentifier` | `MISSING_REQUIRED_DATA` |
| Patient data of CH Core Patient EPR (name and further required elements) | Patient | — | `MISSING_REQUIRED_DATA` |
| Person author as PractitionerRole, Patient or RelatedPerson (PRE, MTP) | Composition author | `author` | `MISSING_REQUIRED_DATA` |
| Exactly one author, the aggregation service (Device) (PMLC, F8) | Composition author | — | `MISSING_REQUIRED_DATA` |
| EPR confidentiality extension | Composition | `confidentiality` (default `NORMAL`) | — (always filled; `DERIVED_VALUE` as M-DOC-09) |
| Treatment-plan reference per MedicationRequest (PRE) and card MedicationStatement (PMLC) | extension `ch-emed-ext-treatmentplan` | `treatmentPlanReferences`, in order | `MISSING_REQUIRED_DATA`; wrong count `TREATMENT_PLAN_REFERENCES_MISMATCH` |
| Last considered document per card MedicationStatement (PMLC) | extension `ch-emed-ext-last-considered-document` | — | `documentId` of the entry's treatment-plan reference, `DERIVED_VALUE` |
| Original representation as PDF/A (PRE, PMLC: section 1..1; PRE: `Binary` 1..*) | section LOINC `55108-5` | `originalRepresentation` (PDF/A, takes precedence) | rendered as PDF/A-1b, `DERIVED_VALUE` (R16) |
| Original representation of an MTP (optional) | section LOINC `55108-5` | `originalRepresentation` (PDF/A) | kept if PDF/A, otherwise left out (R8) |

The list per document type was taken from the profiles in T014 (research F4) and is recorded in
`docs/mapping/ch-emed-epr.md`. Elements that must have text when present (`reasonCode.text`,
`Dosage.route.text`, `Medication.form.text`, …) are checked by the output validation.

## 7. Library API changes (summary; details in [contracts/library-api.md](./contracts/library-api.md))

- `FormatId.CH_EMED_EPR`.
- `OutputForm.TREATMENT_PLANS` (external name `treatment-plans`).
- `MappingContext.validator()`: `Optional<ChEmedValidator>`, set by `DefaultConverter`.
- `ReasonCode.NO_TREATMENTS`, `ReasonCode.TREATMENT_PLAN_EXISTS` and
  `ReasonCode.TREATMENT_PLAN_REFERENCES_MISMATCH`.
- `TreatmentPlanReference(String documentId, String statementId)`, both `urn:uuid:<uuid>`, and the
  option `ConversionOptions.treatmentPlanReferences()` (default empty) with
  `withTreatmentPlanReferences(List)` (R15).
- The option `ConversionOptions.originalRepresentation()` (`Optional<byte[]>`, default empty) with
  `withOriginalRepresentation(byte[])` (R16).
- `OriginalRepresentationRenderer.renderPdfA(Bundle, ReportCollector)` (R16).
- Unchanged: `ConversionRequest` and `ConversionResult`; the existing components of
  `ConversionOptions` keep their meaning and defaults.

## 8. State and lifecycle

Stateless, as before. The unsupported-element list and the profile map are loaded once per module
instance. The validator context is built once by `converter-validation`, now with the EPR package.
