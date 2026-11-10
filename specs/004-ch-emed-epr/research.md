# Research: CH EMED EPR (CARA EMED)

Phase 0 of [plan.md](plan.md). Each item: decision, rationale, alternatives considered.

Sources: CH EMED EPR 3.0.0 (<https://fhir.ch/ig/ch-emed-epr/3.0.0/>, published 2026-02-11,
repository <https://github.com/CARA-ch/ch-emed-epr>), constitution 1.5.0, the code of features
001–003.

## R1 — CH EMED EPR as a format module of its own

**Decision**: New module `format-ch-emed-epr` with the format id `CH_EMED_EPR` (external name
`ch-emed-epr`). It implements `FormatModule`: `toChEmed` turns a CH EMED EPR document into a general
CH EMED document, `fromChEmed` the reverse. The converter core and its pipeline stay as they are.

**Rationale**:
- The constitution lists CH EMED EPR as a row of its own in the Conversion Scope, converted to and
  from the CH EMED hub (FR-001).
- `DefaultConverter` already offers exactly the pairs "format ↔ CH EMED". A direct
  `chmed16a → ch-emed-epr` pair is therefore refused without new code (FR-004); only the message
  must name the path through CH EMED (R11).
- Capabilities, CLI and REST pick up a new format module automatically (`Converter.formats()`).

**Alternatives considered**:
- *A profile option on CH EMED* (for example `profile=epr` for source and target `ch-emed`):
  rejected. One name would stand for two specifications with different validation; capabilities
  and reports could not tell them apart.
- *Extending `format-chmed16a`*: rejected. CH EMED EPR has nothing to do with CHMED16A, and the
  decision against composed conversions (clarification 2026-11-08) keeps them apart.

## R2 — Pinning and vendoring

**Decision**: Pin `ch.fhir.ig.ch-emed-epr#3.0.0`. Its dependencies are the ones already vendored
(CH EMED 6.0.0, CH Core 6.0.0, HL7 Terminology 7.0.1 and 6.5.0, Extensions 5.2.0 and
5.3.0-ballot-tc1) plus `hl7.fhir.uv.tools.r4#0.9.0`, which the IG declares "for example
references".
- Both new packages are fetched by `tools/fetch-packages.sh` and recorded with SHA-256 in
  `converter-validation/src/main/resources/packages/manifest.json`.
- `ch.fhir.ig.ch-emed-epr` is loaded into the validator.
- `hl7.fhir.uv.tools.r4` is vendored but loaded only if the official examples need it to validate
  (checked in the first implementation task).
- The format module reports `ch.fhir.ig.ch-emed-epr#3.0.0` as its pinned specification;
  `docs/specifications.md` gains a CH EMED EPR section.

**Rationale**: Constitution I requires pinned IG packages and local validation. 3.0.0 is the
current published version and builds on exactly the CH EMED and CH Core versions of features
001–003, so no existing pin changes.

**Alternatives considered**:
- *2.0.0* (2025-01-21): rejected. It is superseded and builds on older CH EMED versions; mixing
  versions of CH EMED in one validator is not possible (one definition per canonical URL).
- *CI build 3.0.1*: rejected. Not a published version.

## R3 — Validation of CH EMED EPR input and output

**Decision**:
- `MappingContext` gains access to the `ChEmedValidator` of the converter.
- The EPR module validates its input (`VALIDATE_INPUT`) before mapping and its output
  (`VALIDATE_OUTPUT`) after mapping.
- It uses the same validator instance, with the EPR package loaded. The validator checks each
  resource against the profiles declared in `meta.profile`, so EPR documents are checked against
  the EPR profiles and general CH EMED documents against the CH EMED profiles, as before.
- Input is always validated (FR-007). Output follows the existing option for output validation
  (`validateOutput`, default on), as for CH EMED output.
- A regression test runs the whole corpus of features 001–003 with the EPR package loaded. Output
  and reports must stay byte-identical.

**Rationale**: FR-006/FR-007 need the official validator (constitution I); a second validator
instance would double the start-up time and memory of the validation context.

**Alternatives considered**:
- *Validation in `DefaultConverter` for "FHIR-based" target modules*: rejected. It needs a new
  SPI concept just for one module; the module knows best which phase it is in.
- *The module loading its own validator through `ServiceLoader`*: rejected. A second context
  costs start-up time and memory, and it bypasses the converter's choice of validator.

## R4 — Profile mapping

**Decision**: The mapping is a change of the declared profiles plus the rules of R5–R9.

| CH EMED 6.0.0 | CH EMED EPR 3.0.0 |
|---------------|-------------------|
| Medication Card document, composition, MedicationStatement (card) | PMLC document, composition, MedicationStatement |
| Medication Prescription document, composition, MedicationRequest | PRE document, composition, MedicationRequest |
| Medication Treatment Plan document, composition, MedicationStatement (treatment plan) | MTP document, composition, MedicationStatement |
| Patient, Practitioner, PractitionerRole, Organization, RelatedPerson, Medication | CH EMED EPR Patient, Practitioner, PractitionerRole, Organization, RelatedPerson, Medication |

- Profiles of other resources (for example Observation for body weight) are mapped where the EPR
  IG defines them.
- Data-type profiles (Dosage, Quantity, Ratio) are not declared in `meta.profile`; they are checked
  through the resource profiles.

The table and every rule get rule ids `M-EPR-*` in the new mapping specification
`docs/mapping/ch-emed-epr.md` (constitution II, FR-012).

**Rationale**: CH EMED EPR profiles CH EMED. A conforming CH EMED EPR document is therefore a CH
EMED document with stricter constraints. The direction EPR → CH EMED only changes declarations.
The direction CH EMED → EPR must also remove what EPR does not support (R5), fill what EPR requires
(R9) and, for treatment plans, split the card (R6/R7).

**To verify in the first implementation task**: all official PMLC, PRE and MTP examples of the IG
validate as CH EMED after the profile change, without clinical changes (spec assumption,
SC-001). If an example needs more than the profile change, the extra rule is added to the mapping
specification with its reason.

## R5 — Elements not supported by CH EMED EPR

**Decision**: An element is "not supported" when the CH EMED EPR snapshot marks it with the
standards-status *draft* extension (shown as ✕ "This element is not supported" in the IG). The
elements marked "will be removed in PML(C) documents" (for example `Patient.identifier.assigner`)
are treated as unsupported for PMLC output only.
- The list per profile is extracted once from the vendored package by a test utility and stored in
  the module as a pinned resource. A test fails when the resource and the package disagree.
- `docs/mapping/ch-emed-epr.md` lists each element with its clinical relevance.
- On CH EMED → EPR, present elements of the list are removed and reported as `UNMAPPABLE_ELEMENT`
  with path and reason. For clinically relevant elements the entry carries the clinical-loss flag,
  so strict mode fails (FR-008).
- On EPR → CH EMED, nothing is removed.

**Rationale**:
- These elements are allowed by the validator (cardinality unchanged) but have "no clear use
  case" in CH EMED EPR, and implementers should not set them.
- Leaving them in would pass validation but break the IG's intent.
- Removing them silently would break constitution II.
- A pinned list makes the behaviour reviewable and stable.

**Alternatives considered**:
- *Reading the flags from the package at run time*: rejected. The mapping would be invisible in
  the mapping specification, and a package change would change behaviour without review.
- *Keeping the elements*: rejected for the reason above.

## R6 — Treatment plans from a medication card

**Decision**:
- A new output form `TREATMENT_PLANS` (external name `treatment-plans`), offered by `ch-emed-epr`
  for a CH EMED Medication Card as input.
- The output is one FHIR Bundle of type `collection`. Its entries are the MTP document Bundles,
  one per MedicationStatement of the card, in the order of the card's section entries.
- Each MTP document is validated on its own against the MTP document profile. The collection
  itself is validated against FHIR R4 core.
- An input that is not a Medication Card is refused with `UNSUPPORTED_DOCUMENT_TYPE`. A card
  without treatments is refused with a new code `NO_TREATMENTS` (spec US3.3).
- Without `treatment-plans`, a card becomes a PMLC document (FR-003).

**Rationale**:
- `outputForm` already exists in the library, CLI and REST (feature 003). It is validated against
  the forms a target offers and appears in the capabilities, so the request of FR-003 needs no new
  option.
- A `collection` Bundle is the standard FHIR way to deliver several resources as one; library,
  CLI and REST keep a single output document, and the result stays byte-deterministic.
- The primary system splits the collection into documents before submitting each through IHE MHD,
  which is outside the converter's scope.

**Alternatives considered**:
- *Several outputs in `ConversionResult`*: rejected. It changes the result type in all three
  interfaces (REST array, CLI output directory), and only one conversion needs it.
- *One call per treatment with an index option*: rejected. The caller would need to know the
  number of treatments first, and there would be n reports for one plan.
- *A new option `eprDocumentType`*: rejected. It duplicates what `outputForm` already does and is
  checked for.

## R7 — Building an MTP document from a card entry

**Decision**: For entry *i* of the card:
- **Composition**: MTP type (SNOMED `761931002`, LOINC `77603-9` as in CH EMED), localized title
  as the existing titles (M-DOC-08), and one `treatmentPlan` section with the single
  MedicationStatement mapped to the MTP profile.
- **Copied from the card**: `language`, `confidentiality` and its EPR extension, the patient,
  `author` and `date`.
- **Identifiers**: Composition and Bundle identifiers are UUID v5 from the card's Composition
  identifier and *i* (existing `IdStrategy`, FR-011). Resource ids are deterministic in the same
  way.
- **Existing treatment plan**: a card entry that already references a treatment plan (CH EMED
  treatment-plan extension) still yields an MTP, and the report gets a WARNING with the path. Such
  an MTP would record the treatment decision a second time in the EPR. The new reason code is
  `TREATMENT_PLAN_EXISTS` (data model §3).
- **No original representation** is generated for the MTP documents (it is optional in MTP; R8).

**Rationale**: The MTP holds exactly one treatment decision (spec key entity). Copying the
document-level data of the card is the only source available, and it applies no new defaults
(constitution II).

**Alternatives considered**:
- *Skipping entries with an existing treatment plan*: rejected. That would silently drop
  treatments; the caller decides with the report.

## R8 — Original representation (PDF)

*Superseded in part by R16 (2026-11-09): a missing or non-PDF/A original representation of a PRE
or PMLC is rendered anew as PDF/A instead of being left out.*

**Decision**:
- **CH EMED → EPR**: an original representation is kept only if the PDF declares PDF/A-1 or
  PDF/A-2 conformance (XMP `pdfaid:part` 1 or 2). Otherwise the section is left out and reported
  as INFORMATION `UNMAPPABLE_ELEMENT` with the reason "EPR requires PDF/A". The PDFs produced by
  feature 001 are not PDF/A, so they are left out.
- **EPR → CH EMED**: the section is kept unchanged.
- Whether an EPR document type requires the section is checked against the profiles. If PMLC or
  PRE require it, a document without a PDF/A representation is refused with
  `MISSING_REQUIRED_DATA` rather than given a generated PDF.

**Rationale**: The EPR ordinance requires PDF/A for the original representation, as stated in the
EPR profiles. The validator does not check PDF/A. Generating a PDF/A rendering would be a new
feature of the renderer with its own conformance checks (fonts, colour profile, XMP).

**Alternatives considered**:
- *Rendering a new PDF/A with PDFBox*: deferred. It is possible, but outside the spec. It can
  follow when an EPR document type needs it.

## R9 — Data the EPR requires

*Extended by R15 (2026-11-09): the treatment-plan references are a further option.*

**Decision**: The EPR profiles require data that a general CH EMED document may lack:
- the patient with an identifier (system and value), name and the data of CH Core Patient EPR;
- the author as PractitionerRole, Patient, RelatedPerson or Device, with a person author;
- the EPR confidentiality extension.

Missing data is filled only from the existing options of feature 001 (`patientIdentifier`,
`author`, `confidentiality`), as INFORMATION `DERIVED_VALUE`. Otherwise the conversion fails with
`MISSING_REQUIRED_DATA` naming the element path (FR-009). A Practitioner author without a role
needs the option `author`; the converter never invents an organisation.

The confidentiality is never missing: the option `confidentiality` has the default `NORMAL`, and a
document without confidentiality gets it with `DERIVED_VALUE`, exactly as M-DOC-09 does for
CHMED16A.

**Rationale**: Feature 001 already produces these elements from CHMED16A, with the options
(mapping M-DOC-09, M-AUT-02). This reuses the same options and codes, so callers see one
behaviour.

## R10 — Determinism

**Decision**: All generated ids use the existing `NameBasedIdStrategy` (UUID v5). No wall-clock
time: dates are copied from the source. Entries keep the order of the source. A JSON
serialisation through `ChEmedJson` gives byte-identical output for identical input and options
(FR-011).

## R11 — Interfaces and messages

**Decision**:
- **Library**: `FormatId.CH_EMED_EPR` and `OutputForm.TREATMENT_PLANS`. `Converter.formats()`
  lists `ch-emed-epr` with input form `json` and output forms `document` and `treatment-plans`.
- **CLI**: `--from ch-emed-epr`, `--to ch-emed-epr` and `--output-form treatment-plans`. No new
  options.
- **REST**: the existing route `POST /api/v1/conversions/{from}/{to}` with the new names. OpenAPI
  1.1.0 → 1.2.0 is additive: the `outputForm` enum gains `treatment-plans`, plus examples and
  description. The capabilities list the format through `formats`.
- **Unsupported pairs**: `chmed16a ↔ ch-emed-epr` is refused with `UNSUPPORTED_CONVERSION`. The
  message lists the supported pairs and adds "convert through ch-emed: chmed16a → ch-emed →
  ch-emed-epr" (FR-004).
- **Wrong declaration**: input declared as `ch-emed-epr` whose Bundle does not declare an EPR
  document profile fails with the validation findings plus the hint "probably general CH EMED;
  use source ch-emed" (edge case). Input declared as `ch-emed` that is an EPR document is accepted
  (spec edge case: every EPR document is a CH EMED document).
- **Parity**: the parity test of feature 002 is extended to the new pairs and the
  `treatment-plans` form (SC-006).

## R12 — Test corpus and tests

**Decision**:
- **Official examples**: the PMLC, PRE and MTP Bundle examples of the vendored package
  (`package/example/`), copied as fixtures with a README naming their source. They are officially
  published example data; the patient "CARAPMP004" is a test patient of CARA (constitution IV).
- **Feature corpus**: the CH EMED reference documents of feature 001 and the CH EMED documents
  produced from its CHMED16A reference plans and prescriptions (SC-002).
- **Tests**, written before the code (constitution III):
  - one fixture test per `M-EPR-*` rule;
  - corpus tests in both directions;
  - round trips in both directions, with the differences checked against the documented changes
    (FR-010);
  - MTP count and determinism (SC-004, FR-011);
  - refusal of the unsupported pairs and forms;
  - the regression test of R3;
  - extended privacy and no-network tests (SC-007, SC-008);
  - a time test for SC-005.
- **Mapping traceability**: `MappingTraceabilityTest` gains the new mapping specification.

## R13 — Documentation

**Decision**:
- `docs/specifications.md`: CH EMED EPR section (package, date, dependencies, source).
- `docs/mapping/ch-emed-epr.md` (new).
- `docs/architecture.md`: module diagram, and the planned items "CARA EMED" and "CH EMED EPR"
  merged into one implemented item.
- `docs/third-party-licenses.md`: the IG packages are CC0-1.0.
- `README.md`, `CHANGELOG.md` and `docs/operations/rest-service.md`: new names and form.
- The contracts of features 001–003 point to the deltas of this feature.

## R14 — Performance

**Decision**: Measure the build time of the validation context with the EPR package loaded, and
the conversion time of a card with 20 medications to PMLC and to `treatment-plans`, on the
reference hardware with 4 vCPU (SC-005). The limits of feature 001 (SC-005b: validation < 30 s
warm; SC-005c: 20 medications converted and validated < 5 s) apply. Twenty MTP documents mean
twenty document validations, so this case is measured separately. If the limit is missed,
validation of the MTP documents in parallel is considered before relaxing the criterion.

## R15 — Treatment-plan references as an option (FR-017, added 2026-11-09)

**Decision**: A new conversion option `treatmentPlanReferences`, an ordered list of
`TreatmentPlanReference(documentId, statementId)`.
- Both values are URIs of the form `urn:uuid:<uuid>`, written as `Identifier` with system
  `urn:ietf:rfc:3986` into the extension `ch-emed-ext-treatmentplan` (`externalDocumentId` and
  `id`). Other forms are a malformed option (CLI exit 2, REST `400 MALFORMED_REQUEST`).
- **PRE**: one reference per MedicationRequest without the extension, in section order.
- **PMLC**: one reference per card MedicationStatement without the extension, in section order. A
  card entry without `ch-emed-ext-last-considered-document` gets the reference's `documentId`,
  reported as INFORMATION `DERIVED_VALUE`.
- References already in the source are kept and never overwritten.
- Entries without a reference and no option → `MISSING_REQUIRED_DATA` with the entry paths. A
  list whose length differs from the number of entries without a reference → new ERROR
  `TREATMENT_PLAN_REFERENCES_MISMATCH`, naming both counts, never the values.
- MTP and `treatment-plans` do not use the option. Other targets ignore it, as they ignore the
  other options they do not need.
- The MTP documents of `treatment-plans` (R7) have stable identifiers, so a caller can create the
  MTPs first and pass their identifiers for the PRE or PMLC. The order of the MTP collection
  matches the order of the card entries.

**Interfaces**: library `ConversionOptions.withTreatmentPlanReferences(List)`; CLI
`--treatment-plan <documentId>,<statementId>`, repeatable, in order; REST
`options.treatmentPlanReferences: [{documentId, statementId}]`.

**Rationale**: The references identify documents in the EPR that only the caller knows
(constitution II: no invented data). One flat ordered list keeps the option the same in all three
interfaces.

**Alternatives considered**:
- *Deriving the references from the MTPs the converter would produce from the same card*:
  rejected. It works only for cards, not for prescriptions, and states that MTPs exist in the EPR
  which the converter cannot know.
- *Matching references to entries by medication code*: rejected. A card may hold the same
  medication twice; the order is unambiguous.

## R16 — Original representation as PDF/A (FR-018, added 2026-11-09)

**Decision**:
- **Detection**: a source PDF is PDF/A if its XMP metadata declares `pdfaid:part` 1 or 2 (R8).
- **Option** (added 2026-11-09): `originalRepresentation`, the bytes of a PDF supplied by the caller
  (library `withOriginalRepresentation(byte[])`, CLI `--original-representation <file>`, REST
  `options.originalRepresentation` in base64). Order: a supplied PDF/A, then a PDF/A of the source,
  then a rendered PDF/A. A supplied PDF that is not PDF/A (no `pdfaid:part` 1 or 2, or not a PDF)
  is not used: WARNING `UNMAPPABLE_ELEMENT` "supplied original representation is not PDF/A",
  without clinical loss (strict mode does not fail on it), then the next step applies. A used supplied PDF is reported as INFORMATION `DERIVED_VALUE`. The
  converter checks only the PDF/A claim in the XMP, not full conformance; the request size limit
  of feature 002 applies.
- **With `treatment-plans`**: the option is not used, because one PDF would be attached to every
  MTP of the card; WARNING `UNMAPPABLE_ELEMENT` "original representation not used for
  treatment-plans", without clinical loss. The MTP documents get no original representation (R7).
- **Section**: when the source has no `originalRepresentation` section, the module creates it with
  its `Binary` and `textLink` as feature 001 does, for a supplied PDF/A as for a rendered one.
- **PRE and PMLC**: a supplied PDF/A, else a PDF/A of the source is used. Otherwise, and when the section is missing, the
  module renders the converted EPR document with `OriginalRepresentationRenderer.renderPdfA`, sets
  it as the `Binary` of the `originalRepresentation` section (creating the section with its
  `textLink` as feature 001 does), and reports INFORMATION `DERIVED_VALUE` "original representation
  rendered as PDF/A". A non-PDF/A source PDF is reported as replaced.
- **MTP**: a supplied PDF/A, else a PDF/A of the source is used; a non-PDF/A one is left out and
  reported (R8); none is rendered (the section is optional).
- **Renderer**: a second entry point beside `render` in `converter-core`:
  - PDF/A-1b (ISO 19005-1, level B), which the EPR accepts and which PDF 1.4 tools read;
  - font Liberation Sans Regular, shipped inside PDFBox (SIL OFL 1.1), embedded as subset;
    headings in a larger size instead of a bold face;
  - output intent with a vendored sRGB ICC profile (version 2, as PDF/A-1 requires), checked by
    SHA-256 like the IG packages; source and licence are verified in T064 and recorded in
    `docs/third-party-licenses.md`;
  - XMP metadata with `xmpbox` (Apache PDFBox, Apache-2.0): `pdfaid:part=1`, `conformance=B`,
    title, and creation and modification dates from the Composition `date`;
  - document id from the content hash, no clock (FR-011);
  - characters missing in the font are replaced and reported as `PDF_CHARACTER_REPLACED`, as in
    feature 001.
  - `render` of feature 001 stays unchanged, so existing PDFs stay byte-identical (FR-013).
- **Tests**: PDFBox Preflight (`org.apache.pdfbox:preflight`, Apache-2.0, test scope only)
  validates every rendered PDF/A-1b.

**Rationale**: The EPR ordinance requires PDF/A for the original representation, and PRE and
PMLC require the section. Rendering from the converted document is what feature 001 does for
CH EMED, so the PDF shows the same content as the structured data.

**Alternatives considered**:
- *PDF/A-2b*: possible, but Preflight checks only PDF/A-1b; veraPDF would add a large test
  dependency under MPL-2.0/GPL. PDF/A-1b is enough for the EPR.
- *Switching feature 001 to PDF/A as well*: rejected for now. It changes existing output; it can
  follow as its own change.
- *Leaving the section out*: not possible, PRE and PMLC require it.

## Findings of the first implementation task (T014, 2026-11-09)

Checked against the vendored package `ch.fhir.ig.ch-emed-epr#3.0.0` (SHA-256 `6fb0c9dc…a9508c78`).

### F1 — The core composed pairs of two formats (R1 corrected)

R1 assumed that `DefaultConverter` offers only "format ↔ CH EMED". In fact it offered and
composed every pair of two format modules through CH EMED (feature 001, test
`pairWithoutChEmedIsComposedViaChEmed`). With only one module this never showed.

**Decision (Stefan, 2026-11-09)**: composition removed. `DefaultConverter` offers only
"format ↔ CH EMED"; a pair of two other formats fails with `UNSUPPORTED_CONVERSION`, and the
message adds "convert through ch-emed: a -> ch-emed -> b" (FR-004). The feature 001 test now
asserts the refusal; the library contract of feature 001 gets a pointer (T058).

### F2 — Tools package not needed (R2 corrected)

`hl7.fhir.uv.tools.r4#0.9.0` is not in the `package.json` dependencies of the EPR package. The IG
names it only as an `ig-internal-dependency` for the IG publisher. It is not vendored. All
dependencies of the EPR package were already vendored; only the EPR package itself is new. It is
appended to `manifest.json` after the CH EMED closure, so the manifest root (reported as pinned
specification of CH EMED) and the load flags of the existing packages stay unchanged.
`tools/fetch-packages.sh` now takes several root packages and processes them one after another.

### F3 — Profile map (R4 confirmed)

Every EPR profile names its CH EMED (or CH Core) base in `baseDefinition`:
- document, Composition, MedicationStatement (card, list, changed), MedicationRequest,
  MedicationDispense, Medication, Observation, Organization, Practitioner, PractitionerRole,
  Dosage → the CH EMED profile of the same name;
- `ch-emed-epr-medicationstatement-treatmentplan` → `ch-emed-medicationstatement`;
- `ch-emed-epr-patient` → `ch-core-patient-epr` (the profile feature 001 already declares);
- `ch-emed-epr-related-person` → `ch-core-relatedperson`;
- `ch-emed-epr-device` and `ch-emed-epr-observation-body-weight` → FHIR core (no CH EMED profile).

Official examples copied to `format-ch-emed-epr/src/test/resources/fixtures/official/`: 6 PMLC,
5 PRE, 6 MTP, and one each of DIS, PADV and PML for the refusal tests. Whether EPR → CH EMED needs
more than the profile change is still to be shown by validation (T021).

### F4 — Data CH EMED EPR requires beyond CH EMED (R8, R9: open decision)

From the differentials of the EPR profiles:

| Document | Requirement | In general CH EMED |
|----------|-------------|--------------------|
| PRE | `MedicationRequest.extension:treatmentplan` 1..1: reference to the MTP document and its MedicationStatement in the EPR | 0..1; documents from CHMED16A never have it |
| PRE | section `originalRepresentation` 1..1 with `Binary` 1..* (PDF/A by the EPR ordinance) | optional; feature 001 renders a PDF that is not PDF/A |
| PMLC | `MedicationStatement.extension:treatmentPlan` 1..1 and `extension:lastConsideredDocument` 1..1 per card entry | optional; documents from CHMED16A never have them |
| PMLC | section `originalRepresentation` 1..1 (`Binary` 0..*) | optional |
| MTP | none beyond CH EMED; `originalRepresentation` 0..1 | — |
| all | elements that are optional but must have text when present (`reasonCode.text`, `Dosage.route.text`, `Medication.form.text`, …), and `max = 0` on `implicitRules`, `modifierExtension`, `section:card.author` | — |

Consequence: a PRE or PMLC can be produced only from a CH EMED document that already carries
these EPR references (for example a document read from the EPR), never from eMediplan data. The
converter must not invent the references (constitution II). Towards the EPR, the MTP is the only
document type the converter can produce from eMediplan data; in the EPR workflow the MTPs come
first, the PRE references them, and the community's aggregator produces the PMLC.

**Decision (Stefan, 2026-11-09)**: the spec is extended: the references come from a new option
(R15, FR-017), and the original representation is rendered as PDF/A when the source has none in
PDF/A (R16, FR-018).

### F5 — Elements not supported by CH EMED EPR (R5 confirmed, larger than expected)

956 snapshot elements carry the standards-status `draft` flag (shown as "✕ This element is not
supported"), between 17 and 50 per profile, mostly meta, identifier details, extensions on codings
and `encounter`. The pinned list of R5 is therefore generated, not written by hand; the `clinical`
flags need a review.

### F6 — Results of US1 (2026-11-09)

- All 17 official PMLC, PRE and MTP examples convert to valid CH EMED by the profile change alone
  (`OfficialExamplesToChEmedIT`); F3 is confirmed, and the assumption of the spec holds.
- Feature 001 recognised a CH EMED Medication Card only by LOINC `56445-0`, but CH EMED fixes only
  the SNOMED CT code `736378000`; the PMLC examples carry SNOMED only. Fixed in
  `ChEmedDocumentReader` with a regression test (M-REV-01). The prescription requires LOINC
  `57833-6` in CH EMED, so nothing changes there.
- The author of a PMLC is the aggregation service (Device without GLN). CHMED16A requires the
  author's GLN, so a PMLC cannot become an eMediplan without a person author. **Decision (Stefan,
  2026-11-09)**: documented as capability gap; the author option is not used for it (spec US1.2).

### F7 — Marker of unsupported elements (R5 refined, 2026-11-09)

Of the 956 elements with the standards-status `draft` extension, 954 have a `short` that starts
with "✕" ("This element is not supported", "This element will be removed in PML(C) documents",
"Do not use…", "The note author is ignored…", "This is only meaningful when the dispense was not
performed"). The other two are `Dosage.text` of the base dosage profiles, which the IG asks for
("SHOULD be provided"; the official examples set it). An element is therefore unsupported only
when it is `draft` **and** its `short` starts with "✕". The EPR datatype profiles (dosage,
quantities, ratios) are resolved into the resource profiles that use them, and every slice gets a
matcher from its discriminator, so removal works generically on the JSON of a document. Result:
1750 paths in 34 resource profiles; 271 are flagged clinical. Notable: the base dosage of CH EMED
EPR supports no structured timing beyond `when` and `boundsPeriod`; frequency, period, weekdays
and times of day belong to split dosages, so they are lost from a base dosage (clinical loss,
documented in `docs/mapping/ch-emed-epr.md`).

### F8 — Author of a PMLC (R9 extended, 2026-11-09)

The EPR card Composition allows exactly one author, `ch-emed-epr-device` (the aggregation service
of the community); CH EMED allows a PractitionerRole, Device, Patient or RelatedPerson. Cards
made from an eMediplan have a person as author. Patient identifier and EPR confidentiality are
already required by CH EMED (`ch-core-patient-epr`, `ch-ext-epr-confidentialitycode`), so a valid
CH EMED input has them; the options remain as fallback. PRE and MTP require a person author
(PractitionerRole, Patient or RelatedPerson), where CH EMED also allows a Practitioner or an
Organization.

**Decision (Stefan, 2026-11-09)**: a card whose author is not a Device is refused with
`MISSING_REQUIRED_DATA` at the author, as a documented capability gap. The converter does not
create a device (constitution II). Cards read from the EPR convert.

### F9 — Substitution (found by T029, 2026-11-09)

CH EMED EPR fixes `MedicationRequest.substitution.allowedCodeableConcept.coding` and the value of
`ch-emed-ext-substitution` on card and treatment-plan statements to the pattern
`v3-substanceAdminSubstitution#N` ("If absent, substitution is assumed allowed. If present, the
value is fixed to none"). CH EMED allows `E` (equivalent), as in the CH EMED prescription example
and feature 001 output. The converter leaves an "allowed" statement out (same meaning) and writes
`allowedBoolean` false as code `N` (mapping rule M-EPR-27).

### F10 — MTP documents from a card (found by T038, 2026-11-09)

- CH EMED EPR requires that an MTP Bundle and its Composition have the same identifier
  (`bundle-composition-same-id`), and the `treatmentPlan` section needs a text. Both get the UUID
  v5 of the card's Composition identifier and `/treatment-plans/<i>/Composition`; the section text
  names the medication and its dosage text.
- The MTP needs a person author (PractitionerRole, Patient or RelatedPerson). A card from the EPR
  is authored by the aggregation service (F8); its MTPs get the `informationSource` of their entry
  as author, the person who recorded the treatment (`DERIVED_VALUE`). In the EPR cards this
  PractitionerRole is contained in the statement; it moves into the MTP document as an entry with
  a deterministic `urn:uuid`, since an author cannot reference a contained resource.
- References inside an MTP point to the full URL of their target, because the new Composition has
  a `urn:uuid` full URL against which the relative references of the card would not resolve.

### F11 — Resource ids in validator paths (found by T049, 2026-11-09)

The HL7 validator writes each location with the resource type and id in a comment, for example
`Bundle.entry[1].resource/*Patient/PatientDupont*/.identifier[0].value`. Resource ids are free text;
in the official CH EMED EPR example `Bundle-DocumentMtp1` the patient's id is built from the name.
`PhiLeakTest` found the name in a report. `ValidationMessageSanitizer.location` now keeps only the
type (`/*Patient*/`); the indexed path stays unique. This applies to every validation report, also
those of feature 001 (to be noted in the CHANGELOG, task T057).

### F12 — Interfaces (T046–T052, 2026-11-09)

- The REST service now names the path through `ch-emed` for a pair of two non-hub formats in the
  `404` detail, as the library does (FR-004).
- An unsupported document type is reported in phase `PARSE` in both directions, so REST answers
  `422 INVALID_INPUT` as the contract says.
- The CLI `formats` command lists the input and output forms of every format.
- Both distributions (CLI and REST ZIP) contain `format-ch-emed-epr`; the OCI image was not built
  here (needs a Docker daemon).
