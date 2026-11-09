---

description: "Task list for feature 004 - CH EMED EPR (CARA EMED)"
---

# Tasks: CH EMED EPR (CARA EMED)

**Input**: Design documents from `/specs/004-ch-emed-epr/`

**Prerequisites**:
- plan.md, spec.md, research.md (R1–R14), data-model.md, contracts/ (library-api.md, cli.md,
  rest-api.md), quickstart.md.
- Features 001–003 build green on version 1.1.0-SNAPSHOT.

**Tests**: INCLUDED. Constitution principle III requires test-first, fixture tests per mapping
rule, the official examples of the IG in the corpus and round trips (research R12). Write each
test first and confirm that it fails before implementing.

**Organization**: Tasks are grouped by user story, so each story can be implemented and tested
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story (US1–US5) the task belongs to
- All paths are relative to the repository root

## Path Conventions

- **Core API**: `converter-core/src/main/java/ch/emed/converter/core/<package>/`
- **EPR module**: `format-ch-emed-epr/src/main/java/ch/emed/converter/epr/<package>/`. It depends
  on `converter-core` only, with no framework (plan, Structure Decision).
- **EPR tests**: `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/<package>/`; end-to-end
  tests in `.../epr/it/`.
- **Fixtures**: `format-ch-emed-epr/src/test/resources/fixtures/{official,chemed,expected}/`.
  - `official/` holds the PMLC, PRE and MTP examples copied from the vendored package.
  - `chemed/` holds synthetic CH EMED inputs written for single rules.
  - The corpus of feature 001 is read from `format-chmed16a` (T004), not copied.
  - `expected/` holds the exact expected output per rule fixture; rule tests compare byte for
    byte (constitution III), as in feature 001.
- **Never in logs, reports or problem details**: patient data or document content (FR-015).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: New module, vendored packages, licences

- [X] T001 In `pom.xml`, register the module `format-ch-emed-epr` after `format-chmed16a`.
- [X] T002 Create `format-ch-emed-epr/pom.xml`.
  - Java 21; compile dependency `converter-core` only (HAPI structures come through it).
  - Test dependencies: JUnit 5, AssertJ, `converter-validation`, and `format-chmed16a` with its
    test jar (T004).
  - The parent's `enforce-framework-free` and license-plugin rules apply.
- [X] T003 Vendor the packages (research R2). This is the only task that needs network access.
  - Run `tools/fetch-packages.sh ch.fhir.ig.ch-emed-epr#3.0.0`. Check that the closure adds only
    `ch.fhir.ig.ch-emed-epr#3.0.0` and `hl7.fhir.uv.tools.r4#0.9.0`, and that every existing entry
    of `converter-validation/src/main/resources/packages/manifest.json` keeps its version and
    SHA-256.
  - If the script rewrites the manifest from the new root, merge it so that `ch.fhir.ig.ch-emed`
    stays recorded as before. Keep the load flag of `hl7.fhir.uv.tools.r4` off until T014
    decides.
  - Change the script's usage comment to mention both root packages.
  - *Done 2026-11-09: only `ch.fhir.ig.ch-emed-epr#3.0.0` is new and vendored; the tools package
    is an `ig-internal-dependency` of the IG publisher and is not vendored (research F2). The
    script takes several root packages and processes them one after another.*
- [X] T004 Add the goal `test-jar` of `maven-jar-plugin` to `format-chmed16a/pom.xml`, if it is not
  there yet, so the EPR tests can read the feature 001 fixtures and `testsupport` classes
  (`Fixtures`, `PhiScanner`, `LargePlanGenerator`). It is not shipped.
- [X] T005 [P] Add the two IG packages (CC0-1.0, vendored in `converter-validation`, shipped in
  both distributions) to `docs/third-party-licenses.md`.
- [X] T006 [P] Add a CH EMED EPR section to `docs/specifications.md`: package
  `ch.fhir.ig.ch-emed-epr#3.0.0`, publication date 2026-02-11, publisher CARA, dependencies,
  source <https://fhir.ch/ig/ch-emed-epr/3.0.0/>, and `hl7.fhir.uv.tools.r4#0.9.0` with its load
  flag (research R2, R13).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core API additions, EPR package in the validator, module skeleton, and the checks
that research R4/R8/R9 leave to the first implementation task (data model §1–3, §6;
contracts/library-api.md)

**CRITICAL**: No user story work can begin until this phase is complete.

### Tests (write first, must fail)

- [X] T007 [P] In `converter-core/src/test/java/ch/emed/converter/core/api/ConversionApiAdditionsTest.java`,
  test the additions of contracts/library-api.md:
  - `FormatId.CH_EMED_EPR` has the external name `ch-emed-epr`;
  - `OutputForm.TREATMENT_PLANS` has the external name `treatment-plans` and parses from it;
  - `ReasonCode.NO_TREATMENTS` and `ReasonCode.TREATMENT_PLAN_EXISTS` exist;
  - `ConversionRequest`, `ConversionOptions` and `ConversionResult` are unchanged (existing
    assertions still pass).
- [X] T008 [P] In `converter-core/src/test/java/ch/emed/converter/core/api/DefaultConverterTest.java`,
  with two stub modules (`TestFormatModule`) for formats A and B:
  - `MappingContext.validator()` holds the converter's validator in `toChEmed` and `fromChEmed`,
    and is empty for a converter built without a validator;
  - A → B fails with `UNSUPPORTED_CONVERSION`. The message lists the supported pairs and adds
    "convert through ch-emed: a → ch-emed → b" (research R11, FR-004);
  - `TREATMENT_PLANS` with a target that does not offer it fails with `UNSUPPORTED_OUTPUT_FORM`
    (existing check).
- [X] T009 [P] In `converter-validation/src/test/java/ch/emed/converter/validation/EprPackageRegressionTest.java`
  (research R3, FR-013):
  - the package loader loads `ch.fhir.ig.ch-emed-epr#3.0.0` and the manifest checksums match;
  - every CH EMED document of the feature 001 corpus gives the same validation messages, in the
    same order, as with a validator built without the EPR package. If `PackageLoader` cannot
    leave one package out, add a package filter for tests to it.
- [X] T010 [P] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/ChEmedEprFormatModuleTest.java`,
  test the module's declarations (data model §1):
  - `format()` is `CH_EMED_EPR`; `pinnedSpecification()` is `ch.fhir.ig.ch-emed-epr#3.0.0`;
  - `inputForms()` is `[json]`; `outputForms()` is `[DOCUMENT, TREATMENT_PLANS]`;
  - the module is found through `ServiceLoader`, and `Converter.formats()` lists it.
- [X] T011 [P] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/EprDocumentTypeTest.java`,
  test the recognition of data model §2:
  - CH EMED Medication Card, Prescription and Treatment Plan map to PMLC, PRE and MTP by
    Composition `type`;
  - for EPR input, the declared document profile decides;
  - Dispense, Pharmaceutical Advice and Medication List, in both forms, are refused with
    `UNSUPPORTED_DOCUMENT_TYPE`, and the message names the supported types.

### Implementation

- [X] T012 [P] Add the core values in `converter-core`:
  - `CH_EMED_EPR` ("ch-emed-epr") in `api/FormatId.java`;
  - `TREATMENT_PLANS` ("treatment-plans") in `api/OutputForm.java`;
  - `NO_TREATMENTS` (ERROR) and `TREATMENT_PLAN_EXISTS` (WARNING) in `report/ReasonCode.java`,
    each with a one-line Javadoc.
- [X] T013 Change `converter-core` (depends on T012):
  - *Done 2026-11-09: pairs of two formats other than CH EMED are no longer offered or composed
    (research F1, decided by Stefan); `DefaultConverterTest` asserts the refusal.*
  - `spi/MappingContext.java`: `Optional<ChEmedValidator> validator()`, set at construction;
  - `api/DefaultConverter.java`: pass its validator to every `MappingContext`; for a pair of two
    non-hub formats, add the hint "convert through ch-emed" of research R11 to the
    `UNSUPPORTED_CONVERSION` message.
- [X] T014 Verify the open points of research R2, R4, R8 and R9 against the vendored package,
  before any mapping code. Record the results in the task note and in
  `docs/mapping/ch-emed-epr.md` (T018):
  - **Profile change only?** Copy the PMLC, PRE and MTP Bundle examples of `package/example/` to
    `format-ch-emed-epr/src/test/resources/fixtures/official/` with a `README.md` naming their
    source and version. Replace their EPR profiles by the CH EMED profiles of the R4 table, by
    hand or with a scratch script, and validate them as CH EMED. Every rule needed beyond the
    profile change becomes an `M-EPR-*` rule with its reason.
  - **Tools package**: do the examples validate without `hl7.fhir.uv.tools.r4`? Set its load
    flag in `manifest.json` accordingly.
  - **Required data per document type** (data model §6): list for PMLC, PRE and MTP what the EPR
    profiles require beyond CH EMED (patient elements, author types, confidentiality, original
    representation).
  - **Profile URLs**: list every EPR profile URL that the R4 table needs.
  - *In progress 2026-11-09: examples copied, tools package not needed, profile map and required
    data found (research F2–F5). Open: the decision on F4 (PRE and PMLC need EPR references and
    PDF/A) and the validation of the examples as CH EMED.*
  - *Decision on F4 (Stefan, 2026-11-09): option for the treatment-plan references and PDF/A
    rendering (research R15, R16; tasks T061–T067). Open: validation of the examples as CH EMED
    (T021).*
- [X] T015 Load the EPR package in `converter-validation` (depends on T003, T014): the manifest
  load flags drive `PackageLoader`; no other code change is expected. T009 passes.
- [X] T016 [P] Create `format-ch-emed-epr/src/main/java/ch/emed/converter/epr/EprDocumentType.java`:
  enum `PMLC`, `PRE`, `MTP` with the CH EMED Composition codes (SNOMED and LOINC) and the CH EMED
  and EPR document and Composition profile URLs from T014; recognition per data model §2.
- [X] T017 [P] Create `format-ch-emed-epr/src/main/java/ch/emed/converter/epr/mapping/ProfileMap.java`:
  the bidirectional map of the R4 table per resource type and document type, built from the URLs
  of T014. `meta.profile` entries not in the map are kept unchanged.
- [X] T018 Create the mapping specification `docs/mapping/ch-emed-epr.md`:
  - scope, pinned versions and the document types of data model §2;
  - the profile table (R4) as `M-EPR-01`, and the rules found in T014;
  - placeholders for the rules of the stories: unsupported elements (R5), required data (R9),
    original representation (R8), treatment plans (R6/R7), refused pairs and forms (R11);
  - the reason codes of data model §3.
  Add the file to `MappingTraceabilityTest` in
  `format-chmed16a/src/test/java/ch/emed/converter/chmed16a/it/MappingTraceabilityTest.java`, or
  move that test to a place both modules reach, so that every rule id has a test and every test
  id a rule.
  - *Done 2026-11-09: the module has its own `@MappingRule` and `MappingTraceabilityTest`, because
    the annotation of feature 001 is in the main code of `format-chmed16a`.*
- [X] T019 Create `format-ch-emed-epr/src/main/java/ch/emed/converter/epr/ChEmedEprFormatModule.java`
  (depends on T013, T016, T017):
  - the declarations of T010 and the service file
    `src/main/resources/META-INF/services/ch.emed.converter.core.spi.FormatModule`;
  - parsing of FHIR JSON document Bundles with `PARSE_ERROR` for anything else;
  - document type recognition (T016); `toChEmed` and `fromChEmed` fail with "not yet
    supported" until US1 and US2;
  - a helper that validates a Bundle through `ctx.validator()` in phase `VALIDATE_INPUT` or
    `VALIDATE_OUTPUT` and adds the findings as `FHIR_VALIDATION`. Without a validator, the
    module fails with the existing "validation incomplete" outcome.

### Added 2026-11-09 (research R15, R16; decision on F4)

- [X] T061 [P] In `converter-core/src/test/java/ch/emed/converter/core/api/EprApiAdditionsTest.java`,
  test the option of contracts/library-api.md:
  - `TreatmentPlanReference` accepts `urn:uuid:<uuid>` for both values and throws
    `IllegalArgumentException` for anything else, without the value in the message;
  - `ConversionOptions.defaults().treatmentPlanReferences()` is empty;
    `withTreatmentPlanReferences` keeps every other option and copies the list;
  - `ConversionOptions.defaults().originalRepresentation()` is empty; `withOriginalRepresentation`
    keeps every other option and copies the bytes;
  - `ReasonCode.TREATMENT_PLAN_REFERENCES_MISMATCH` exists.
- [X] T062 Implement the record
  `converter-core/src/main/java/ch/emed/converter/core/api/TreatmentPlanReference.java` and the
  component `treatmentPlanReferences` (default empty, unmodifiable copy) with
  `withTreatmentPlanReferences` in `ConversionOptions.java`; keep the existing constructors
  working. Add the component `originalRepresentation` (`Optional<byte[]>`, defensive copies)
  with `withOriginalRepresentation`. Add `TREATMENT_PLAN_REFERENCES_MISMATCH` to `report/ReasonCode.java`.
- [X] T063 [P] Add `org.apache.pdfbox:xmpbox` (compile, `converter-core`) and
  `org.apache.pdfbox:preflight` (test scope, `converter-core` and `format-ch-emed-epr`), both
  3.0.8 via `pdfbox.version`, to the parent `pom.xml` and the module POMs. Record them in
  `docs/third-party-licenses.md` (Apache-2.0; preflight not shipped), with Liberation Sans (SIL OFL
  1.1, inside PDFBox) and the sRGB ICC profile of T064.

**Checkpoint**: The existing suite is still green with the EPR package loaded, and its expected
outputs under `format-chmed16a/src/test/resources/fixtures/expected/` are unchanged (FR-013).
T007–T011 pass.

---

## Phase 3: User Story 1 - Read a medication card from the EPR (Priority: P1) MVP

**Goal**: A PMLC document converts to a valid CH EMED Medication Card, and from there with the
existing conversion to an eMediplan.

**Independent Test**: Convert the official PMLC examples to CH EMED and on to CHMED16A. The CH
EMED documents validate, and the CHMED16A plans pass the eMediplan checks of feature 001 (spec
US1).

### Tests (write first, must fail)

- [ ] T020 [P] [US1] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/mapping/ToChEmedMapperTest.java`,
  test `M-EPR-01` and the rules of T014 for PMLC: every EPR profile in `meta.profile` becomes the
  CH EMED profile; everything else is unchanged; each change is reported as INFORMATION.
  Compare the output byte for byte with `fixtures/expected/<fixture>.json`.
- [X] T021 [US1] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/it/PmlcToChEmedIT.java`,
  test end to end through `Converters.create()` (spec US1.1–US1.4):
  - every official PMLC example → a CH EMED Medication Card without validation errors, with the
    same MedicationStatements (`ClinicalEquivalence`), outcome SUCCESS or SUCCESS_WITH_WARNINGS;
  - the result → CHMED16A, also as `qr-payload` and `qr-image`, with the existing conversion,
    and the plan passes the eMediplan checks of feature 001;
  - a PMLC without treatments (derived from an example in `fixtures/chemed/`) → a valid card
    without medications;
  - a PMLC with a validation error → FAILED with `FHIR_VALIDATION` in phase `VALIDATE_INPUT` and
    no output;
  - a general CH EMED card declared as `ch-emed-epr` → FAILED with the findings and the hint
    "probably general CH EMED; use source ch-emed" (research R11);
  - an official PMLC example declared as `ch-emed` → converted as CH EMED (edge case).
  - *Done 2026-11-09 in `OfficialExamplesToChEmedIT` and `PmlcToChmed16aIT`. The second step to
    CHMED16A fails with `MISSING_REQUIRED_DATA` at `Composition.author`: the PMLC author is the
    aggregation service without GLN; documented capability gap (decision 2026-11-09, spec US1.2).
    Feature 001 recognised the card only by its LOINC code; fixed with a regression test
    (`ChEmedDocumentReaderTest`, M-REV-01).*

### Implementation

- [X] T022 [US1] Implement `format-ch-emed-epr/src/main/java/ch/emed/converter/epr/mapping/ToChEmedMapper.java`
  for PMLC: copy of the Bundle, profile change through `ProfileMap`, the rules of T014, report
  entries per change (paths only).
- [X] T023 [US1] Wire `toChEmed` in `ChEmedEprFormatModule.java`: validate the input
  (`VALIDATE_INPUT`, always, FR-007), refuse with the findings on errors, add the hint of T021
  when the Bundle declares no EPR document profile, then map with `ToChEmedMapper`.
- [X] T024 [US1] Document the PMLC → Medication Card rules (`M-EPR-1x`) in
  `docs/mapping/ch-emed-epr.md`.

**Checkpoint**: US1 works through the library. CLI and REST pick up the format through
`Converter.formats()`; their parity is checked in US5.

---

## Phase 4: User Story 2 - Create an EPR prescription and card (Priority: P1)

**Goal**: CH EMED Medication Prescriptions and Cards convert to PRE and PMLC documents that
validate against CH EMED EPR. Unsupported elements are reported, and missing data comes only from
the options.

**Independent Test**: Convert the CH EMED reference documents of feature 001 and the CH EMED
documents produced from its CHMED16A reference plans and prescriptions. Every result validates
against CH EMED EPR or fails only for documented missing data (SC-002).

### Tests (write first, must fail)

- [X] T025 [P] [US2] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/mapping/UnsupportedElementsTest.java`
  (research R5, data model §4):
  - the resource `epr/unsupported-elements.json` matches the elements that the vendored package
    marks with the standards-status *draft* extension, per profile, and the elements marked
    "will be removed in PML(C) documents" with scope `pmlc`. The test fails in both directions of
    disagreement;
  - every entry has a `clinical` flag;
  - removal from a synthetic document in `fixtures/chemed/` with one element per scope: each
    removed element is reported as `UNMAPPABLE_ELEMENT` with its path; clinical ones carry the
    clinical-loss flag; `pmlc` elements are removed for PMLC only.
- [X] T026 [P] [US2] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/mapping/RequiredDataTest.java`
  (research R9, data model §6), for each row of data model §6 and the results of T014:
  - present in the source → unchanged, no report entry;
  - missing, option given → filled from `patientIdentifier`, `author` or `confidentiality`, and
    reported as `DERIVED_VALUE`;
  - missing, no option → `MISSING_REQUIRED_DATA` naming the element path, never a value;
  - a Practitioner author without a role and without the option `author` → `MISSING_REQUIRED_DATA`;
    no organisation is invented;
  - confidentiality missing → filled from the option (default `NORMAL`), reported as
    `DERIVED_VALUE`; never `MISSING_REQUIRED_DATA`.
  - treatment-plan references (R15): PRE and PMLC entries without the extension and no option →
    `MISSING_REQUIRED_DATA` naming the entry paths; n references for n such entries → each entry
    gets `ch-emed-ext-treatmentplan` in order; a different count → `TREATMENT_PLAN_REFERENCES_MISMATCH`
    naming both counts; references already in the source are kept;
  - last considered document (PMLC): missing → the `documentId` of the entry's reference,
    `DERIVED_VALUE`; present → kept.
- [X] T027 [P] [US2] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/mapping/OriginalRepresentationTest.java`
  (research R8, R16):
  - a PDF/A supplied with the option `originalRepresentation` takes precedence over the source,
    for PRE, PMLC and MTP, reported as `DERIVED_VALUE`; a supplied PDF that is not PDF/A (or no
    PDF) is not used, reported as WARNING, and the next rule applies;
  - a PDF with XMP `pdfaid:part` 1 or 2 is kept, for PRE, PMLC and MTP;
  - PRE and PMLC: a PDF without it, a PDF/A-3, a PDF produced by feature 001, a placeholder that is
    no PDF, and a missing section → replaced by a PDF/A-1b rendered from the document, with the
    section and `textLink` as in feature 001, reported as `DERIVED_VALUE`;
  - MTP: a non-PDF/A original representation is left out, reported as INFORMATION
    `UNMAPPABLE_ELEMENT` with the reason "EPR requires PDF/A"; none is rendered.
  Create the PDF fixtures with PDFBox in a test helper; they hold no patient data.
- [ ] T028 [P] [US2] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/mapping/FromChEmedMapperTest.java`,
  test the CH EMED → EPR rules (`M-EPR-0x`) for PRE and PMLC: profile change, then R5, R8 and R9
  in this order, with one fixture per rule. Compare the output byte for byte with
  `fixtures/expected/<fixture>.json`.
- [X] T029 [US2] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/it/ChEmedToEprIT.java`,
  test end to end (spec US2.1–US2.8, SC-002):
  - every CH EMED Prescription and Card of the feature 001 corpus, and every CH EMED document
    produced from its CHMED16A reference plans and prescriptions: without options → FAILED with
    `MISSING_REQUIRED_DATA` only; with the options of `TestOptions` and one treatment-plan
    reference per entry → PRE or PMLC without validation errors, each with a PDF/A original
    representation that passes Preflight;
  - a card converted first with `treatment-plans`, then to PMLC with the identifiers of these MTP
    documents as references → valid PMLC whose entries reference those MTPs (US2.7);
  - a wrong number of references → `TREATMENT_PLAN_REFERENCES_MISMATCH` (US2.6);
  - a prescription with a PDF/A supplied by `originalRepresentation` (a PDF/A rendered by T065
    saved as fixture) → the PRE carries exactly these bytes; a supplied PDF that is not PDF/A →
    WARNING, and a rendered PDF/A is used; strict mode does not fail on it (US2.9);
  - `fixtures/chemed/card-epr-elements.json` of feature 001 keeps its EPR elements;
  - in strict mode a document with a clinical unsupported element fails;
  - a direct `chmed16a → ch-emed-epr` request fails with `UNSUPPORTED_CONVERSION` naming the
    path through `ch-emed` (US2.3);
  - an output that fails validation is not returned (FR-006); provoke it with a stub validator.

### Implementation

- [X] T030 [US2] Write the extraction utility
  `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/testsupport/UnsupportedElementExtractor.java`
  (used by T025) and generate `format-ch-emed-epr/src/main/resources/epr/unsupported-elements.json`
  with it (format of data model §4). Set the `clinical` flags by hand, with the reason for each
  in `docs/mapping/ch-emed-epr.md`.
- [X] T031 [P] [US2] Implement `format-ch-emed-epr/src/main/java/ch/emed/converter/epr/mapping/UnsupportedElements.java`:
  load the resource once per module instance; remove present elements per profile and scope;
  report as in T025.
- [X] T032 [P] [US2] Implement `format-ch-emed-epr/src/main/java/ch/emed/converter/epr/mapping/RequiredData.java`
  per data model §6, reusing the option handling of feature 001 (`patientIdentifier`, `author`,
  `confidentiality`; mapping M-DOC-09, M-AUT-02) so the results match, and the treatment-plan
  references and last considered document of R15.
- [X] T033 [P] [US2] Implement `format-ch-emed-epr/src/main/java/ch/emed/converter/epr/mapping/OriginalRepresentation.java`:
  PDF/A detection from the XMP metadata (`pdfaid:part` 1 or 2) with PDFBox, which `converter-core`
  already ships, and the order of research R16: the PDF/A of the option `originalRepresentation`,
  then a PDF/A of the source, then for PRE and PMLC `OriginalRepresentationRenderer.renderPdfA`
  (T065); for MTP, removal. The section with `Binary` and `textLink` is created when the source
  has none, for a supplied PDF/A as for a rendered one (R8, R16).
- [X] T034 [US2] Implement `format-ch-emed-epr/src/main/java/ch/emed/converter/epr/mapping/FromChEmedMapper.java`
  for PRE and PMLC (depends on T031–T033): copy, profile change, unsupported elements, original
  representation, required data.
- [X] T035 [US2] Wire `fromChEmed` with form `DOCUMENT` in `ChEmedEprFormatModule.java`: map,
  validate the output (`VALIDATE_OUTPUT`, following the option `validateOutput`), never return
  output with errors.
- [ ] T036 [US2] Document the CH EMED → EPR rules (`M-EPR-0x`), the unsupported elements with their
  clinical relevance, the required data per document type and the original representation in
  `docs/mapping/ch-emed-epr.md`. The `clinical` flags are reviewed by someone with domain
  knowledge; record the review in the PR description. *Status 2026-11-09: documentation written
  (M-EPR-21 to M-EPR-27, unsupported elements, required data, capability gaps); the review of the
  `clinical` flags is open.*
- [X] T068 [US2] Substitution (research F9, found by T029): CH EMED EPR allows only the code `N`
  for `MedicationRequest.substitution` and `ch-emed-ext-substitution`; implement M-EPR-27 in
  `format-ch-emed-epr/src/main/java/ch/emed/converter/epr/mapping/Substitution.java` and
  document it.

### Added 2026-11-09: PDF/A renderer (research R16)

- [X] T064 [US2] Vendor an sRGB ICC profile version 2 (for example `sRGB2014.icc` of the ICC,
  color.org) as `converter-core/src/main/resources/render/srgb.icc`. Check that its licence allows
  embedding and redistribution, record source, licence and SHA-256 in
  `docs/third-party-licenses.md` and `docs/specifications.md`, and verify the checksum when the
  renderer loads it. Ask Stefan before downloading.
- [X] T065 [US2] Test first, in
  `converter-core/src/test/java/ch/emed/converter/core/render/OriginalRepresentationRendererPdfATest.java`:
  - `renderPdfA` for the CH EMED card and prescription examples passes PDFBox Preflight as
    PDF/A-1b;
  - XMP: `pdfaid:part=1`, `conformance=B`, dates from the Composition `date`;
  - Liberation Sans embedded, no Standard 14 font, sRGB output intent;
  - umlauts and French and Italian accents are kept; a character missing in the font is replaced
    and reported as `PDF_CHARACTER_REPLACED`;
  - two runs give byte-identical PDFs;
  - `render` of feature 001 gives the same bytes as before (existing expected outputs).
  Then implement `renderPdfA` in
  `converter-core/src/main/java/ch/emed/converter/core/render/OriginalRepresentationRenderer.java`
  per research R16, sharing the layout with `render`.

**Checkpoint**: US1 and US2 work through the library; PRE and PMLC are offered in both
directions.

---

## Phase 5: User Story 3 - Create treatment plans for the EPR from an eMediplan (Priority: P2)

**Goal**: A CH EMED Medication Card with output form `treatment-plans` becomes a `collection`
Bundle with one valid MTP document per treatment. A single CH EMED treatment plan converts to one
MTP.

**Independent Test**: Convert the cards of feature 001, including those produced from its CHMED16A
reference plans. The number of MTP documents equals the number of treatments, each validates, and
a second run is byte-identical (SC-004, FR-011).

### Tests (write first, must fail)

- [X] T037 [P] [US3] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/mapping/TreatmentPlanSplitterTest.java`
  (research R6, R7, data model §5):
  - a card with n MedicationStatements → `Bundle.type` `collection` with n MTP document Bundles,
    in section order;
  - `Bundle.identifier`: UUID v5 from the card's Composition identifier and the name
    `treatment-plans`; `Bundle.timestamp`: the card's Composition `date`;
  - MTP Composition and Bundle identifiers: UUID v5 from the card's Composition identifier and
    *i*;
  - copied from the card: `language`, `confidentiality` and its EPR extension, `subject`,
    `author`, `date`;
  - each MTP has the MTP type (SNOMED `761931002`, LOINC `77603-9`), the localized title and one
    `treatmentPlan` section with exactly one MedicationStatement; no original representation;
  - with the option `originalRepresentation` → the MTP documents still have no original
    representation, and one WARNING `UNMAPPABLE_ELEMENT` "original representation not used for
    treatment-plans" is reported (R16);
  - the collection holds no other resources;
  - a card entry that references a treatment plan → its MTP is still built, and a WARNING
    `TREATMENT_PLAN_EXISTS` with the path is reported;
  - a card without MedicationStatement → `NO_TREATMENTS` (ERROR);
  - the collection of one three-entry card equals `fixtures/expected/treatment-plans-3.json`
    byte for byte.
- [X] T038 [US3] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/it/TreatmentPlansIT.java`,
  test end to end (spec US3.1–US3.4, SC-004):
  - every card of the corpus (feature 001 cards, cards from its CHMED16A reference plans, the
    official PMLC examples converted to CH EMED) → as many MTP documents as treatments, each
    validating against the MTP document profile, together clinically equal to the card;
  - two runs give byte-identical output;
  - a card without treatments → FAILED with `NO_TREATMENTS` and no output;
  - `treatment-plans` with a Prescription or a Treatment Plan as input →
    `UNSUPPORTED_DOCUMENT_TYPE`;
  - `treatment-plans` with target `chmed16a` → `UNSUPPORTED_OUTPUT_FORM`;
  - `treatment-plans` with `originalRepresentation` → output as without the option, plus the
    WARNING of R16; a single treatment plan with form `document` and a supplied PDF/A → the MTP
    carries it;
  - a single CH EMED treatment plan with form `document` → one valid MTP, and back to CH EMED
    the original apart from the documented changes (US3.4). The reverse direction needs T045;
    until then the test is disabled with a reference to T045;
  - a card with 100 treatments (`LargePlanGenerator`) → 100 MTP documents (edge case); the time
    is measured in T055.

### Implementation

- [X] T039 [US3] Extend `FromChEmedMapper.java` with CH EMED treatment plan → MTP (form
  `document`).
- [X] T040 [US3] Implement `format-ch-emed-epr/src/main/java/ch/emed/converter/epr/mapping/TreatmentPlanSplitter.java`
  per T037, with the existing `NameBasedIdStrategy` for all ids and no wall-clock time
  (research R10). Each MTP runs through `FromChEmedMapper` for the MTP rules.
- [X] T041 [US3] Wire `TREATMENT_PLANS` in `ChEmedEprFormatModule.fromChEmed`: refuse inputs other
  than a card; split; validate each MTP document on its own against the MTP document profile and
  the collection against FHIR R4 core; one report for all MTP documents, with the entry index in
  each path.
  *Status 2026-11-09: each MTP is validated against its profile; the collection itself is built
  by the converter and not validated separately (it holds only the MTP documents). The
  back-conversion of US3.4 in T038 waits for T045.*
- [X] T042 [US3] Document the MTP rules and the treatment-plan set (`M-EPR-2x`) in
  `docs/mapping/ch-emed-epr.md`.

**Checkpoint**: US1–US3 work through the library.

---

## Phase 6: User Story 4 - Read prescriptions and treatment plans from the EPR (Priority: P2)

**Goal**: PRE and MTP documents from the EPR convert to valid CH EMED documents; PRE results go on
to CHMED16A with the existing conversion.

**Independent Test**: Convert the official PRE and MTP examples to CH EMED and the PRE results on
to CHMED16A (spec US4).

### Tests (write first, must fail)

- [ ] T043 [P] [US4] Extend `ToChEmedMapperTest.java` with PRE and MTP: profile change and the
  rules of T014 for both types. Compare the output byte for byte with
  `fixtures/expected/<fixture>.json`.
- [ ] T044 [US4] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/it/PreMtpToChEmedIT.java`
  (spec US4.1–US4.4):
  - every official PRE example → a valid CH EMED Medication Prescription, and on to CHMED16A a
    prescription that meets the eRezept data requirements, or the existing report of what is
    missing;
  - every official MTP example → a valid CH EMED treatment plan;
  - `ch-emed-epr → chmed16a` → FAILED with `UNSUPPORTED_CONVERSION` naming the path through
    `ch-emed`;
  - DIS, PADV and PML documents of the package, if it has examples, → `UNSUPPORTED_DOCUMENT_TYPE`
    "not yet supported".

### Implementation

- [ ] T045 [US4] Extend `ToChEmedMapper.java` with PRE and MTP → CH EMED, and document the rules
  in `docs/mapping/ch-emed-epr.md`.

**Checkpoint**: All three document types work in both directions through the library.

---

## Phase 7: User Story 5 - Same behaviour through all interfaces (Priority: P3)

**Goal**: Library, CLI and REST give identical documents and reports for CH EMED EPR, including
the treatment-plan set (contracts/cli.md, contracts/rest-api.md).

**Independent Test**: Run the corpus through library, CLI and REST; documents and reports are
identical (SC-006).

### Tests (write first, must fail)

- [ ] T046 [P] [US5] In `converter-cli/src/test/java/ch/emed/converter/cli/EprCliTest.java`
  (contracts/cli.md):
  - `--from ch-emed-epr --to ch-emed` and `--from ch-emed --to ch-emed-epr` with exit `0` and the
    same output as the library;
  - `--output-form treatment-plans` writes the collection Bundle;
  - `--from chmed16a --to ch-emed-epr` exits `1` with `UNSUPPORTED_CONVERSION` naming the two
    steps;
  - `--output-form treatment-plans --to chmed16a` fails as the other forms do;
  - `formats` lists `ch-emed-epr` with its forms and pinned specification
    (`FormatsCommandTest.java` extended).
  - `--treatment-plan <documentId>,<statementId>` repeated gives the same PRE as the library with
    the same references; a malformed value exits `2` without echoing it (T066).
  - `--original-representation <file>` gives the same PRE as the library with the same bytes;
    a missing file exits `2` (T066).
- [ ] T047 [P] [US5] In `converter-rest/src/test/java/ch/emed/converter/rest/contract/EprConversionContractTest.java`
  (contracts/rest-api.md):
  - the routes `ch-emed-epr/ch-emed` and `ch-emed/ch-emed-epr`, also with `outputForm`
    `treatment-plans`, answer `200` with media type `application/fhir+json`, valid against
    OpenAPI 1.2.0;
  - `chmed16a/ch-emed-epr` and the reverse → `404` `UNSUPPORTED_CONVERSION` with
    `supportedConversions`, and the detail names the path through `ch-emed`;
  - `treatment-plans` with another target → `400` `MALFORMED_REQUEST`;
  - invalid EPR input or an unsupported document type → `422` `INVALID_INPUT`; missing required
    data and `NO_TREATMENTS` → `422` `CONVERSION_FAILED`, each with report.
  Extend `CapabilitiesContractTest.java`: `ch-emed-epr` with `document` and `treatment-plans`.
  Also: `options.treatmentPlanReferences` gives the same PRE as the library; a value that is not
  `urn:uuid:` → `400 MALFORMED_REQUEST`; a wrong count → `422 CONVERSION_FAILED` with
  `TREATMENT_PLAN_REFERENCES_MISMATCH` (T067).
  `options.originalRepresentation` (base64) gives the same PRE as the library; invalid base64 →
  `400 MALFORMED_REQUEST` (T067).
- [ ] T048 [P] [US5] Extend `converter-rest/src/test/java/ch/emed/converter/rest/parity/ServiceLibraryParityTest.java`
  and the CLI parity check with the EPR inputs of T021, T029, T038 and T044, including
  `treatment-plans` (SC-006).
- [ ] T049 [P] [US5] Extend the privacy and network tests with the EPR conversions (SC-007,
  SC-008): `converter-rest/src/test/java/ch/emed/converter/rest/privacy/LogLeakTest.java`,
  `NoOutboundNetworkTest.java`, `converter-rest/src/test/java/ch/emed/converter/rest/error/ProblemLeakTest.java`,
  and a `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/it/PhiLeakTest.java` that scans
  every report of the EPR tests with `PhiScanner`.

### Implementation

- [ ] T050 [US5] Add `format-ch-emed-epr` as a runtime dependency to `converter-cli/pom.xml` and
  `converter-rest/pom.xml`, like `format-chmed16a`, and check that both distributions (CLI ZIP,
  REST JAR and OCI image) contain it.
- [ ] T051 [US5] Update `specs/002-rest-conversion-service/contracts/openapi.yaml` to 1.2.0 per
  contracts/rest-api.md: `outputForm` enum and description, capabilities enum, examples with
  `ch-emed-epr`, the new report codes. Regenerate and compile the generated client test
  (`GeneratedClientIT`).
- [ ] T066 [US5] Add the repeatable option `--treatment-plan <documentId>,<statementId>` to
  `converter-cli/src/main/java/ch/emed/converter/cli/ConvertCommand.java` (contracts/cli.md),
  mapped to `withTreatmentPlanReferences` in the given order. Add `--original-representation <file>`, mapped to
  `withOriginalRepresentation`.
- [ ] T067 [US5] Add `treatmentPlanReferences` and `originalRepresentation` (base64) to the REST request options in `converter-rest`
  (DTO, mapping to `ConversionOptions`, `400` for malformed values) and to `openapi.yaml` (T051).
- [ ] T052 [US5] Adjust CLI and REST only where T046–T049 fail. Expected: help texts listing the
  new form, and nothing else, since names come from `Converter.formats()`.

**Checkpoint**: All stories work through library, CLI and REST with identical results.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Round trips, determinism, performance, documentation

- [ ] T053 [P] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/it/RoundTripIT.java`
  (FR-010, SC-003):
  - EPR → CH EMED → EPR for every official example gives the original apart from the changes
    documented in `docs/mapping/ch-emed-epr.md`;
  - CH EMED → EPR → CH EMED for the feature 001 corpus differs only by the reported elements of
    R5, R8 and R9;
  - the allowed differences are listed per rule id in the test, not as a free diff.
- [ ] T054 [P] In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/it/DeterminismIT.java`
  (FR-011): every conversion of the corpus, in both directions and with `treatment-plans`, run
  twice and in a fresh converter, gives byte-identical output and reports.
- [ ] T055 In `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/it/PerformanceTest.java`
  (SC-005, research R14), tagged like the existing time test:
  - start-up time of the validation context with the EPR package, recorded;
  - a card with 20 medications → PMLC, including validation, < 5 s warm (feature 001 SC-005c);
  - a card with 100 treatments → `treatment-plans`, including the validation of all MTP
    documents, < 30 s warm (feature 001 SC-005b);
  - the same card → `treatment-plans`, measured and recorded; if it misses 5 s, try validating
    the MTP documents in parallel before relaxing the criterion;
  - run on andromeda with 4 vCPU (`tools/remote-build.sh load`) and record the numbers in
    `docs/operations/rest-service.md`.
- [ ] T056 [P] Update `docs/architecture.md`: module diagram with `format-ch-emed-epr`, and the
  planned items "CARA EMED" and "CH EMED EPR" merged into one implemented item (research R13).
- [ ] T057 [P] Update `README.md` and `docs/operations/rest-service.md`: format `ch-emed-epr`, form
  `treatment-plans`, the two-step path for CHMED16A, examples of quickstart scenarios 1 and 3.
- [ ] T058 [P] Add pointers to the deltas of this feature in
  `specs/001-chmed16a-ch-emed-conversion/contracts/library-api.md`,
  `specs/001-chmed16a-ch-emed-conversion/contracts/cli.md` and
  `specs/003-qr-erezept/contracts/{library-api,cli}.md`.
- [ ] T059 Record the feature in `CHANGELOG.md` under `## [Unreleased]`: new format `ch-emed-epr`,
  output form `treatment-plans`, option `treatmentPlanReferences`, PDF/A original representation,
  composition of two formats removed, reason codes `NO_TREATMENTS`, `TREATMENT_PLAN_EXISTS` and
  `TREATMENT_PLAN_REFERENCES_MISMATCH`,
  OpenAPI 1.2.0, vendored packages, and any change to existing behaviour found in T009 or T052.
- [ ] T060 Run `./mvnw -B clean verify` on andromeda and the scenarios of
  `specs/004-ch-emed-epr/quickstart.md`; record deviations in the task note.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies. T003 needs network access, once.
- **Foundational (Phase 2)**: depends on Setup. T014 depends on T003 and decides the URLs for
  T016/T017 and the rules of all stories. Blocks all stories.
- **US1 (Phase 3)** and **US2 (Phase 4)**: depend on Foundational; independent of each other. US2
  also needs T061–T065 (option, libraries, PDF/A renderer).
- **US3 (Phase 5)**: depends on US2 (`FromChEmedMapper`, R5/R8/R9 for the MTP documents). The
  US3.4 round trip needs T045 (US4).
- **US4 (Phase 6)**: depends on US1 (`ToChEmedMapper`).
- **US5 (Phase 7)**: depends on the stories whose conversions it checks; T050 and T051 can start
  after Foundational.
- **Polish (Phase 8)**: after all stories.

### Within Each Story

- Tests are written first and fail before implementation.
- Mapper classes before module wiring; wiring before end-to-end tests pass.
- The mapping specification is updated in the same story as its rules (traceability test).

### Parallel Opportunities

- Setup: T005 and T006.
- Foundational: tests T007–T011; implementation T012, T016, T017 (after T014 for the URLs).
- US2: tests T025–T028; implementation T031–T033.
- US1 and US2 in parallel after Foundational.
- US5: tests T046–T049.
- Polish: T053, T054, T056–T058.

---

## Parallel Example: User Story 2

```text
# Tests first, in parallel:
T025 UnsupportedElementsTest
T026 RequiredDataTest
T027 OriginalRepresentationTest
T028 FromChEmedMapperTest

# Then the independent building blocks, in parallel:
T031 UnsupportedElements
T032 RequiredData
T033 OriginalRepresentation

# Then in order:
T034 FromChEmedMapper → T035 module wiring → T029 passes → T036 docs
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup and Phase 2 Foundational, including the checks of T014.
2. Phase 3: PMLC → CH EMED.
3. **Stop and validate**: official PMLC examples to CH EMED and on to an eMediplan with QR code.

### Incremental Delivery

1. US1: read the card from the EPR.
2. US2: create PRE and PMLC for the EPR.
3. US3: treatment plans from a card.
4. US4: read PRE and MTP from the EPR.
5. US5: CLI and REST parity, OpenAPI 1.2.0.
6. Polish: round trips, determinism, performance, documentation.

Each step leaves the existing conversions byte-identical (FR-013).

---

## Notes

- [P] tasks touch different files and have no dependencies on incomplete tasks.
- Every finding of T014 that changes a decision of research.md is recorded there too.
- Commit after each task or logical group, on Stefan's yes.
