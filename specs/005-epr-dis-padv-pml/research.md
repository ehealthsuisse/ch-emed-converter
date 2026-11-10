# Research: CH EMED EPR, further document types (DIS, PADV, PML)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-10

This feature extends the format `ch-emed-epr` of feature 004. Decisions of feature 004
(`specs/004-ch-emed-epr/research.md`, R1–R16 and F1–F12) stay in force unless a section below
changes them. The facts below were checked against the vendored packages
`ch.fhir.ig.ch-emed-epr#3.0.0` and `ch.fhir.ig.ch-emed#6.0.0`.

## R1 — Document types and their recognition

**Decision**: `EprDocumentType` gains `DIS`, `PADV` and `PML`, recognised like the others by the
SNOMED CT code of the Composition type:

| Type | SNOMED CT | CH EMED document | CH EMED EPR document |
|------|-----------|------------------|----------------------|
| DIS | `82291000195104` | `ch-emed-document-medicationdispense` | `ch-emed-epr-document-medicationdispense` |
| PADV | `419891008` | `ch-emed-document-pharmaceuticaladvice` | `ch-emed-epr-document-pharmaceuticaladvice` |
| PML | `721912009` | `ch-emed-document-medicationlist` | `ch-emed-epr-document-medicationlist` |

The map `LATER` of not-yet-supported types disappears. A document of any other type is refused
with `UNSUPPORTED_DOCUMENT_TYPE`, listing the six supported types (FR-003).

**Rationale**: Same mechanism as feature 004 (rule M-EPR-02). The CH EMED medication list also
carries LOINC `56445-0`, the code of the medication card, so only the SNOMED code identifies the
type unambiguously.

**Alternatives considered**: Recognition by declared profile. Rejected because general CH EMED
input often declares no document profile.

## R2 — Profile mapping

**Decision**: `ProfileMap` needs no change. It already lists every EPR profile of the three types:
- the compositions and documents;
- `medicationdispense` and `medication-medicationdispense`;
- `observation` and the `*-changed` profiles;
- all `*-list` profiles, including the differently named `ch-emed-epr-medication-request-changed-list`.

What remains is the rule for profiles that depend on the document type (`toEpr(profile, type)`):
- a plain CH EMED MedicationStatement stays without an EPR profile outside MTP, as today;
- in PADV and PML the statements carry the `-changed` and `-list` profiles of CH EMED, which map
  one to one.

This is verified against all examples (R8).

**Alternatives considered**: None; the map was built for all 34 profiles in feature 004.

## R3 — Unsupported elements

**Decision**: The pinned list `epr/unsupported-elements.json` already covers all 34 EPR profiles,
including those of DIS, PADV and PML. Two things are new:
- Removal now also runs on resources referenced from these documents.
- It also runs inside the changed statement or request of an advice, which is a contained
  resource or a Bundle entry. The existing walk over entries and contained resources covers
  both cases.

The clinical flags of the new profiles fall under the open review T036 of feature 004.

**Rationale**: The extractor of feature 004 already read every profile of the package.

**Alternatives considered**: A separate list per document type. Rejected; the list is per profile,
not per document.

## R4 — Required data per type

**Decision**: `RequiredData.complete` gets one rule set per type:

| Type | Author | Treatment-plan reference | Last considered document |
|------|--------|--------------------------|--------------------------|
| DIS | person, patient, related person or device (as CH EMED) | required on the MedicationDispense (EPR 1..1); from the source, else from option `treatmentPlanReferences` (exactly one reference), else `MISSING_REQUIRED_DATA` | – |
| PADV | as DIS | optional (EPR 0..1); kept | – |
| PML | Device only (EPR 1..1 `ch-emed-epr-device`); otherwise `MISSING_REQUIRED_DATA` at the author (clarification 2026-11-10, as F8) | as required by the `-list` profiles; from the source only, the option does not apply to lists | – |

Patient and confidentiality are handled as in feature 004.

The option counts the entries without reference in document order (feature 004, R15). For DIS
this is the single dispense, so a wrong number of references gives
`TREATMENT_PLAN_REFERENCES_MISMATCH` as before.

**Rationale**:
- The official CH EMED dispenses 3-1 and 3-2 have no treatment-plan reference; they are the test
  case for the option.
- A PML comes from the EPR, where every entry already carries its references, so an option for
  lists would only invent structure.

**Alternatives considered**: Reusing the option for PML entries. Rejected; there is no use case,
and a list with a Device author always comes from the EPR.

## R5 — Consistency of references within one document (FR-008)

**Decision**: A new check `ReferenceConsistency` runs after mapping, in both directions. It
reports contradictions as WARNING with a new reason code `REFERENCE_INCONSISTENT`. The report
names the paths of the extensions concerned, never identifiers. The output stays unchanged and
there is no clinical-loss flag.

The checks:
1. **One document per entry.** Every reference extension (`ch-emed-ext-treatmentplan`,
   `-prescription`, `-dispense`, `-pharmaceuticaladvice`) gives an entry `id` and its
   `externalDocumentId`. The same entry `id` MUST NOT appear with two different document
   identifiers within one document.
2. **References to the own entries.** In a PML, a reference whose entry `id` equals the
   identifier of a statement, request or dispense in the same list MUST name the document the
   list gives for that entry.
3. **The changed resource of an advice.** In a PADV, the changed statement or request MUST belong
   to the treatment plan or prescription the observation references. It belongs when its own
   treatment-plan or prescription reference, or else its identifier, equals the referenced
   entry `id`. The official example `Padv1` shows the second case: the identifier of the changed
   statement equals the treatment-plan `id`.
4. **Dispense and prescription.** A dispense that references both a prescription and a treatment
   plan MUST NOT contradict a prescription of the same document with another treatment plan.
   This can only happen in a PML.

Only references inside the document are compared. Nothing is resolved outside it (FR-007).
Before the rules are fixed, they are run against all official examples of both IGs; a rule that
reports an official example is narrowed and recorded as a finding.

**Rationale**: The decision of 2026-11-10 asks for a check without changing data. Warnings keep
valid documents convertible, and strict mode is unaffected because nothing is lost.

**Alternatives considered**:
- Errors that fail the conversion. Rejected: the source is authoritative and validates against
  the profiles.
- Checking references against other documents. Impossible without the EPR (FR-017).

## R6 — Original representation

**Decision**: DIS, PADV and PML are handled like MTP in feature 004:
- a PDF/A-1 or PDF/A-2 supplied by the caller or present in the source is kept, the supplied
  one taking precedence;
- a PDF that is not PDF/A is removed and reported;
- nothing is rendered.

In `OriginalRepresentation`, the branch for `MTP` becomes "every type except PRE and PMLC".

**Rationale**: The section is optional (0..1) in all three EPR compositions (checked in the
package). Rendering is only needed where the EPR requires it.

## R7 — CHMED16A refuses these types and names the supported ones

**Decision**: The conversion `ch-emed → chmed16a` already refuses other document types with
`UNSUPPORTED_DOCUMENT_TYPE` ("Document type … is not supported", in `ChEmedDocumentReader`). Per
the clarification of 2026-11-10, the message also names the supported types: "supported: medication
card, medication prescription". The two places involved are `ChEmedDocumentReader` and
`Chmed16aFormatModule`.

This changes the text of an existing report entry, which is recorded in the changelog. The
capability gap is documented in `docs/mapping/chmed16a-ch-emed.md` and
`docs/mapping/ch-emed-epr.md`.

**Alternatives considered**: Leaving the message unchanged. Rejected because the option chosen in
the clarification includes the hint on the supported types.

## R8 — Test corpus

**Decision**:

| Source | DIS | PADV | PML |
|--------|-----|------|-----|
| CH EMED EPR 3.0.0 | `DocumentDis1`, `DocumentUCSF4…DISDafalganWithoutPrescription` | `DocumentPadv1`, `DocumentPadv2`, `DocumentPadvCancel…`, `DocumentPadvChange…` | `BundlePml1`, `BundleUtc1Pml` (empty list), `BundleUtc6bPml` |
| CH EMED 6.0.0 | 1-2, 2-4, 3-1, 3-2 (3-1/3-2 without treatment-plan reference) | 2-2, `PharmaceuticalAdvice-ChangeDosage` | 2-1 (Device author) |

Synthetic variants cover the cases the examples miss:
- a list with a person as author;
- a dispense with a reference in the source and in the option;
- one contradiction per rule of R5;
- a list with 100 entries of each kind for SC-004.

The fixture tests per rule `M-EPR-*` follow feature 004.

**Rationale**: SC-001/SC-002 name exactly these examples. The 3-1/3-2 dispenses test the option,
and 2-1 tests CH EMED → PML.

## R9 — Interfaces

**Decision**:
- No new option, output form or endpoint.
- `ChEmedEprFormatModule.documentTypes()` returns the six types.
- OpenAPI goes to 1.2.1, with only the description and example of `documentTypes` changed.
- CLI `formats` shows the types automatically.
- The parity, privacy and no-network tests of feature 004 run over the new corpus.

**Rationale**: FR-014/FR-015; the interfaces of feature 004 already carry everything needed.

## R10 — Mapping rules and documentation

**Decision**:
- New rules go into `docs/mapping/ch-emed-epr.md`:
  - `M-EPR-04`: reference consistency, general and in both directions (R5);
  - `M-EPR-28`: treatment-plan reference of a dispense (R4);
  - `M-EPR-29`: author of a PML (R4).
- The existing rules M-EPR-02 (types), M-EPR-23 (required data) and M-EPR-25 (original
  representation, R6) are extended.
- The rules are checked by `MappingTraceabilityTest`.
- The glossary gets dispense, pharmaceutical advice, medication list and document reference.
- `docs/architecture.md` lists the six types.
- The CHANGELOG gets entries for the new types, the new reason code and the CHMED16A message.

## R11 — Performance

**Decision**: SC-004 is measured like feature 004 (R14), with taskset on 4 vCPUs, for a PML with
100 entries of each kind (400 entries), to CH EMED and back, including validation. It is recorded
in `docs/operations/rest-service.md`. The limit is that of feature 004 (SC-005b < 30 s warm).

**Rationale**: Lists are the largest documents of the EPR. Feature 004 measured 9.8 s for 100 MTPs,
which is 100 validations; one list is a single validation of a larger document.
