# Mapping specification: CH EMED EPR ↔ CH EMED

**Version**: 0.2.0 (in progress, feature 004: both directions for PMLC, PRE and MTP documents;
treatment plans from a card follow) | **CH EMED EPR**: 3.0.0 | **CH EMED**: 6.0.0 — see
[pinned specifications](../specifications.md)

This is the mapping specification of the format `ch-emed-epr` (CARA EMED), required by
constitution principle II. Every rule has a stable id `M-EPR-<NN>`; code of the module
`format-ch-emed-epr` annotated with `@MappingRule` references these ids, and
`MappingTraceabilityTest` of the module checks that both sides match.

Paths are FHIRPath expressions on the document Bundle, for example
`Bundle.entry[3].resource.meta.profile`. CH EMED EPR converts only to and from CH EMED; a pair with
another format (for example `chmed16a`) is refused by the converter with `UNSUPPORTED_CONVERSION`
and the hint "convert through ch-emed" (FR-004). Abbreviations and the names of both IGs are
listed in the [glossary](ch-emed-epr-glossary.md).

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

## CH EMED → CH EMED EPR

The input is a CH EMED document that the converter has validated against CH EMED. The steps run in
this order; the original representation comes last, so a rendered PDF shows the completed document.

| Id | Rule |
|----|------|
| M-EPR-21 | The document is copied and every declared CH EMED or CH Core profile is replaced by its CH EMED EPR profile (M-EPR-01); a general `ch-emed-medicationstatement` becomes the treatment-plan statement in an MTP only. Each changed resource → INFORMATION `PROFILE_CHANGED`. |
| M-EPR-22 | Elements CH EMED EPR does not support are removed (see [Unsupported elements](#unsupported-elements)), per declared profile of the Bundle, its entries and their contained resources. Each removed element → `UNMAPPABLE_ELEMENT` with its path, never its value: WARNING with clinical loss for a clinically relevant element (strict mode fails, FR-008), else INFORMATION. |
| M-EPR-23 | Required data (see [Required data](#required-data-per-document-type)): taken from the source, else from the options `patientIdentifier`, `author` and `confidentiality`, reported as INFORMATION `DERIVED_VALUE`; otherwise ERROR `MISSING_REQUIRED_DATA` naming the element path. No organisation, device or reference is invented. |
| M-EPR-24 | Treatment-plan references (research R15): every MedicationRequest of a PRE and every card MedicationStatement of a PMLC without `ch-emed-ext-treatmentplan` gets the next reference of the option `treatmentPlanReferences`, in section order (`id` = statement, `externalDocumentId` = document, Identifier with system `urn:ietf:rfc:3986`), `DERIVED_VALUE`. References in the source are kept and not counted. No option → `MISSING_REQUIRED_DATA` per entry; a different count → `TREATMENT_PLAN_REFERENCES_MISMATCH` naming both counts. A PMLC entry without `ch-emed-ext-last-considered-document` gets the `externalDocumentId` of its reference, `DERIVED_VALUE`. MTP: not used. |
| M-EPR-25 | Original representation (research R8, R16), section LOINC `55108-5`: (1) a PDF/A of the option `originalRepresentation`, `DERIVED_VALUE`; a supplied PDF that is not PDF/A → WARNING `UNMAPPABLE_ELEMENT` without clinical loss, next step; (2) a PDF/A of the source is kept; (3) PRE and PMLC: a PDF/A-1b rendered from the document (`renderPdfA`), `DERIVED_VALUE`; MTP: a non-PDF/A one is left out, INFORMATION `UNMAPPABLE_ELEMENT` "EPR requires PDF/A", none is rendered. PDF/A means XMP `pdfaid:part` 1 or 2; full conformance is not checked. A missing section is created with its `Binary` and `textLink` as in M-DOC-10. |
| M-EPR-27 | Substitution (research F9): CH EMED EPR reads an absent substitution as "allowed" and allows only the code `N` (none) when present. `MedicationRequest.substitution` with `allowedBoolean` true or a code other than `N`, and the extension `ch-emed-ext-substitution` of a card or treatment-plan statement with a code other than `N`, are left out, INFORMATION `UNMAPPABLE_ELEMENT` (same meaning, no clinical loss); `allowedBoolean` false becomes the code `N`, `DERIVED_VALUE`. |
| M-EPR-26 | Only the output form `document` is offered here (`treatment-plans` follows). The result is validated against CH EMED EPR when `validateOutput` is on (default); with errors no output is returned (FR-006). |

### Unsupported elements

An element is not supported when its snapshot in `ch.fhir.ig.ch-emed-epr#3.0.0` carries the
standards-status `draft` extension **and** its `short` starts with "✕" (research R5, refined by
F7). `Dosage.text` of the base dosage is `draft` but the IG asks for it ("SHOULD be provided"), so
it is kept; the text of a split dosage is not supported. Elements marked "✕ This element will be
removed in PML(C) documents" are removed from PMLC documents only (scope `pmlc`).

The pinned list `format-ch-emed-epr/src/main/resources/epr/unsupported-elements.json` (1750
paths in 34 resource profiles) is generated from the package by
`UnsupportedElementExtractor`, with the EPR datatype profiles (dosage, quantities, ratios)
resolved into the resource profiles and a matcher for every slice; `UnsupportedElementsTest`
fails when list and package disagree.

Clinically relevant (`clinical: true`), so their removal is a clinical loss:

| Element (any profile) | Reason |
|-----------------------|--------|
| `Dosage.timing.event`, `.timing.code`, `.timing.repeat.count[Max]`, `.duration[Max]`, `.durationUnit`, `.frequency[Max]`, `.period[Max]`, `.periodUnit`, `.dayOfWeek`, `.timeOfDay`, `.offset` | dosage timing; CH EMED EPR keeps these only in split dosages, so in a base dosage they are lost |
| `Dosage.text` (split dosage only) | part of the dosage instructions |
| `Dosage.site`, `Dosage.method`, `Dosage.maxDosePerLifetime` | administration and maximum dose |
| `MedicationStatement.statusReason`, `.reasonReference`, `.effective[x]` | status, reason and period of the treatment |
| `MedicationRequest.statusReason`, `.reasonReference`, `.courseOfTherapyType`, `.detectedIssue` | status, reason and kind of the prescription, clinical issues |
| `Observation.value[x]`, `.component`, `.interpretation`, `.referenceRange`, `.effective[x]` | observed values (pharmaceutical advice, a later feature) |

All other unsupported elements are technical or organisational (`meta` details, `id`,
extensions on codings, identifier details, `encounter`, attester, organisational references,
note author and time, dispense details) and are reported as INFORMATION. The flags are set in
`UnsupportedElementExtractor.CLINICAL` and need the review of someone with domain knowledge (task
T036).

### Required data per document type

From the profiles (research F4, F8); everything else CH EMED EPR requires is already required by
CH EMED, so a valid CH EMED input has it.

| Document | Requirement | Source, option | Otherwise |
|----------|-------------|----------------|-----------|
| all | patient identifier with system and value | Patient; `patientIdentifier` | `MISSING_REQUIRED_DATA` |
| all | EPR confidentiality code and extension | Composition; `confidentiality` (default `NORMAL`) | never missing, `DERIVED_VALUE` |
| PRE, MTP | person author: PractitionerRole, Patient or RelatedPerson. A Practitioner author becomes its PractitionerRole of the document, or one built with the organisation of `author`; an Organization author gets the practitioner (GLN, name) of `author` | Composition `author`; `author` | `MISSING_REQUIRED_DATA` at `author[i]` |
| PMLC | exactly one author, the aggregation service of the community (a Device) | Composition `author` | `MISSING_REQUIRED_DATA` at `author` |
| PRE | `ch-emed-ext-treatmentplan` per MedicationRequest | source; `treatmentPlanReferences` | M-EPR-24 |
| PMLC | `ch-emed-ext-treatmentplan` and `ch-emed-ext-last-considered-document` per card entry | source; `treatmentPlanReferences` | M-EPR-24 |
| PRE, PMLC | section `originalRepresentation` with a PDF/A | source; `originalRepresentation` | rendered (M-EPR-25) |
| MTP | — (original representation optional) | | |

## Capability gaps

- A PMLC converted to CH EMED keeps its author, the aggregation service of the community (a Device
  without GLN). The conversion of this card to CHMED16A requires the GLN of the author (M-REV-04 of
  `chmed16a-ch-emed.md`) and fails with `MISSING_REQUIRED_DATA` at `Composition.author`. The
  author option is not used for this (decision 2026-11-09).
- A CH EMED medication card whose author is a person (every card made from an eMediplan) cannot
  become a PMLC: CH EMED EPR requires the community's aggregation service as the only author, and
  the converter does not create it → `MISSING_REQUIRED_DATA` at `Composition.author` (research F8,
  decision 2026-11-09). Cards read from the EPR (author a Device) convert. Towards the EPR, an
  eMediplan becomes treatment plans (MTP) and prescriptions (PRE).
- Dosage timing in a base dosage (for example weekdays or times of day) is removed as unsupported
  with clinical loss (M-EPR-22); CH EMED EPR expects it in split dosages. Moving it is not part of
  this feature.

## Reason codes

| Code | Severity | Use |
|------|----------|-----|
| `PROFILE_CHANGED` | INFORMATION | declared profiles of a resource replaced (M-EPR-01); defined by this module |
| `UNSUPPORTED_DOCUMENT_TYPE` | ERROR | document type outside this feature (M-EPR-02) |
| `PARSE_ERROR` | ERROR | not a document Bundle, or no CH EMED EPR document profile (M-EPR-11) |
| `UNMAPPABLE_ELEMENT` | WARNING with clinical loss, WARNING, or INFORMATION | unsupported element removed (M-EPR-22); supplied original representation not PDF/A, non-PDF/A one of an MTP left out (M-EPR-25) |
| `MISSING_REQUIRED_DATA` | ERROR | data CH EMED EPR requires is missing and no option supplies it (M-EPR-23, M-EPR-24) |
| `TREATMENT_PLAN_REFERENCES_MISMATCH` | ERROR | number of treatment-plan references differs from the entries without one (M-EPR-24) |
| `DERIVED_VALUE` | INFORMATION | value taken from an option or derived (M-EPR-23 to M-EPR-25) |
| `UNSUPPORTED_OUTPUT_FORM` | ERROR | output form other than `document` (M-EPR-26) |
| `FHIR_VALIDATION` | per finding | validation of input and output (M-EPR-03) |
| `VALIDATION_INCOMPLETE` | ERROR | no validator available (M-EPR-03) |
