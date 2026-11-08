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

Reused, with the uses of this feature:

| Code | Severity | Use in this feature |
|------|----------|---------------------|
| `UNMAPPABLE_ELEMENT` | WARNING with clinical loss, or INFORMATION | element not supported by CH EMED EPR removed (R5); non-PDF/A original representation left out (R8) |
| `MISSING_REQUIRED_DATA` | ERROR | data required by EPR missing in source and options (R9) |
| `DERIVED_VALUE` | INFORMATION | data taken from an option (R9) |
| `FHIR_VALIDATION` | per finding | validation of EPR input and output (R3) |
| `UNSUPPORTED_DOCUMENT_TYPE` | ERROR | document type outside §2 |
| `UNSUPPORTED_CONVERSION` | ERROR | direct pair with CHMED16A (FR-004) |
| `UNSUPPORTED_OUTPUT_FORM` | ERROR | `treatment-plans` with a target other than `ch-emed-epr` (existing check) |
| `PARSE_ERROR` | ERROR | not a FHIR JSON document Bundle; hint "use source ch-emed" for a general CH EMED document declared as `ch-emed-epr` (R11) |

## 4. Unsupported element list (R5)

Resource `format-ch-emed-epr/src/main/resources/epr/unsupported-elements.json`, extracted from the
vendored package and checked against it by a test:

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

Each MTP document validates on its own against the MTP document profile. The collection holds no
other resources, and the report covers all MTP documents.

## 6. Data required by CH EMED EPR (R9)

| EPR requirement | Source | Option | Otherwise |
|-----------------|--------|--------|-----------|
| Patient identifier with `system` and `value` | Patient | `patientIdentifier` | `MISSING_REQUIRED_DATA` |
| Patient data of CH Core Patient EPR (name and further required elements) | Patient | — | `MISSING_REQUIRED_DATA` |
| Person author as PractitionerRole, Patient or RelatedPerson | Composition author | `author` | `MISSING_REQUIRED_DATA` |
| EPR confidentiality extension | Composition | `confidentiality` | `MISSING_REQUIRED_DATA` |
| Original representation as PDF/A, where the document type requires it | section LOINC `55108-5` | — | left out (optional) or `MISSING_REQUIRED_DATA` (required) |

The exact list per document type is taken from the profiles in the first implementation task and
recorded in `docs/mapping/ch-emed-epr.md`.

## 7. Library API changes (summary; details in [contracts/library-api.md](./contracts/library-api.md))

- `FormatId.CH_EMED_EPR`.
- `OutputForm.TREATMENT_PLANS` (external name `treatment-plans`).
- `MappingContext.validator()`: `Optional<ChEmedValidator>`, set by `DefaultConverter`.
- `ReasonCode.NO_TREATMENTS` and `ReasonCode.TREATMENT_PLAN_EXISTS`.
- Unchanged: `ConversionRequest`, `ConversionOptions` and `ConversionResult`.

## 8. State and lifecycle

Stateless, as before. The unsupported-element list and the profile map are loaded once per module
instance. The validator context is built once by `converter-validation`, now with the EPR package.
