---

description: "Task list for feature 006 - CHMED23A ↔ CH EMED"
---

# Tasks: CHMED23A ↔ CH EMED

**Input**: Design documents from `/specs/006-chmed23a/`

**Prerequisites**:
- plan.md, spec.md, research.md (R1–R15, F1–F9), data-model.md, contracts/ (library-api.md,
  cli.md, rest-api.md), quickstart.md.
- `dev` builds green on version 1.2.0-SNAPSHOT.

**Tests**: INCLUDED. Constitution principle III requires:
- test-first;
- fixture tests per mapping rule;
- the official examples in the corpus;
- round trips;
- QR tests at payload and image level.

Write each test first and confirm that it fails before implementing.

**Organization**: Tasks are grouped by user story (spec US1–US4), so each story can be implemented
and tested independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story (US1–US4) the task belongs to
- All paths are relative to the repository root

## Path Conventions

- **Main code** (`M`): `format-chmed23a/src/main/java/ch/emed/converter/chmed23a/<package>/`.
- **Tests** (`T`): `format-chmed23a/src/test/java/ch/emed/converter/chmed23a/<package>/`, with
  end-to-end tests in `.../chmed23a/it/`.
- **Fixtures** (`FX`): `format-chmed23a/src/test/resources/fixtures/`:
  - `chmed23a/official/`: the four documents of `examples.md` (R13.1);
  - `chmed23a/posology/`: the posology.md fragments in carrier documents (R13.2);
  - `chmed23a/dotnet/`: transcribed .NET examples (R13.3);
  - `chmed23a/synthetic/`: rule and edge-case inputs, including chunk files (R13.4);
  - `chemed/`: CH EMED inputs for the reverse direction;
  - `qr/`: QR images;
  - `expected/`: byte-exact expected outputs.
- **Moved CH EMED helpers**: `converter-core/src/main/java/ch/emed/converter/core/chemed/` (R2).
- **Never in logs, reports or problem details**: patient data, document content, Base64 data of
  payloads or chunks, extension names or values (FR-016).
- **Rule ids**: `C23-*` (conformance), `M23-*` (mapping), defined in
  `docs/mapping/chmed23a-ch-emed.md`. Every class implementing a rule carries `@MappingRule`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: The new module, the test corpus and the vendored terminology

- [X] T001 Create the Maven module `format-chmed23a/pom.xml`:
  - parent `ch-emed-converter-parent`;
  - main dependencies `converter-core`, `converter-qr` and `jackson-databind`;
  - test dependencies `converter-validation`, JUnit, AssertJ, `jsonassert`, `logback-classic`, and
    the `format-chmed16a` test-jar for `PhiScanner` and `ChEmedAssertions`.

  Add `<module>format-chmed23a</module>` after `format-chmed16a` in `pom.xml`. Add an empty
  `META-INF/services/ch.emed.converter.core.spi.FormatModule` under
  `format-chmed23a/src/main/resources/`.
- [X] T002 [P] Copy the four JSON blocks of `chmed23a/examples.md`, at commit
  `0b579aa6f3aff10a017498db57048c6504091653`, verbatim to
  `FX/chmed23a/official/Example1.json` … `Example4.json`.
  - Write `FX/chmed23a/official/README.md` with the repository, commit, file, the MIT notice
    "© 2024 ig-emediplan" and the findings F1–F3, F5, F7 of research R13.
  - Check that the files are strict JSON with LF line endings.
- [X] T003 [P] Wrap every example block of `chmed23a/posology.md` (pinned commit) into a minimal
  carrier document and store each in `FX/chmed23a/posology/<Object>-<n>.json`, e.g.
  `Daily-1.json`, `Cyclic-1.json`, `Sequence-1.json` (research R13.2).
  - The carrier is a plan (`medType` 1, `auth` 2) with patient "Dora Graber" from the README
    example and one medicament `Med1`, `idType` 1, unit `Stk`.
  - `FX/chmed23a/posology/README.md` lists source line and object for each file.
- [X] T004 [P] Transcribe the .NET examples that add cases beyond T002 and T003 to JSON in
  `FX/chmed23a/dotnet/`, sources under
  `chmed23a/client-implementations/dotnet/Source/Emediplan.ChMed23A.Examples/` (research R13.3).
  - Name each file after its source test, e.g.
    `WeeklyPosologyExamples.Weekly_MondayWednesdayFriday.json`.
  - Replace `DateTimeOffset.Now` by `2024-01-09T09:14:36+01:00`.
  - Together with T003, the fixtures must cover every "Yes" cell of both compatibility tables of
    posology.md. `FX/chmed23a/dotnet/README.md` holds the coverage matrix.
- [X] T005 [P] Generate `format-chmed23a/src/main/resources/chmed23a/terminology/` once from the
  pinned `chmed23a/terminology.md` (research R7):
  - `unit.csv` (CDTYP 9);
  - `route.csv` (CDTYP 61);
  - `method.csv` (CDTYP 62);
  - `risk.csv` (category id, risk id).

  Each has columns `code;de;fr`, is UTF-8 with LF, and has a header comment naming the commit.
  Record the generator command in `format-chmed23a/src/main/resources/chmed23a/terminology/README.md`.
- [X] T006 [P] Add `FormatId.CHMED23A` to
  `converter-core/src/main/java/ch/emed/converter/core/api/FormatId.java`, with Javadoc
  "eMediplan CHMED23A (deprecated by IG eMediplan)".
- [X] T007 [P] Add `ReasonCode.CHUNK_INVALID` to
  `converter-core/src/main/java/ch/emed/converter/core/report/ReasonCode.java`: ERROR, a chunked
  payload that is incomplete or inconsistent (research R5).
- [X] T008 Create `docs/mapping/chmed23a-ch-emed.md` with the sections of research R15:
  - header naming the pinned commit and the deprecation (strategy 2025-12-02);
  - Conformance (`C23-OBJ-*`, `C23-POS-*`, `C23-TERM-*`);
  - Input (`M23-IN-*`) and QR (`M23-QR-*`);
  - Document (`M23-DOC-*`), Author (`M23-AUT-*`), Patient (`M23-PAT-*`, `M23-MDT-*`,
    `M23-EXT-*`), Medicament (`M23-MED-*`), Dosage (`M23-DOS-*`), Prescription (`M23-RX-*`);
  - Reverse (`M23-REV-*`);
  - Capability gaps (data-model §7).

  Fill the rule tables from research R3–R11; the rows are the reference for every later test.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The CH EMED helpers in the core, the deprecated mark in the SPI, and the CHMED23A
model, reader, validator and transmission format that every story needs

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Move the CH EMED helpers (R2)

- [X] T009 Move these classes from `format-chmed16a/src/main/java/ch/emed/converter/chmed16a/` to
  `converter-core/src/main/java/ch/emed/converter/core/chemed/`, without changing their behaviour:
  - `io/ChEmedDocumentReader.java`;
  - `mapping/Profiles.java`;
  - `mapping/Labels.java`;
  - `mapping/Narratives.java`;
  - `mapping/MappingRule.java`.

  Then:
  - extract `DosageToFhirMapper.UNITS` into `converter-core/.../core/chemed/Units.java`, with a
    case-insensitive lookup;
  - update all imports in `format-chmed16a` (main and test) and `format-ch-emed-epr` (test);
  - move `ChEmedDocumentReaderTest.java` to `converter-core/src/test/java/ch/emed/converter/core/chemed/`;
  - make `MappingTraceabilityTest` (format-chmed16a) scan the new annotation.
- [X] T010 Run the full `format-chmed16a` and `format-ch-emed-epr` test suites after T009. All
  expected outputs must be byte-identical, with no fixture changed. Fix the move until they pass.
  This is the regression gate of research R2.
- [X] T011 [P] Write `converter-core/src/test/java/ch/emed/converter/core/api/FormatInfoTest.java`:
  - the existing constructors give `deprecated` = `false`;
  - the new canonical constructor keeps the value;
  - `DefaultConverter.formats()` takes `deprecated()` from the module.

  Then add the default method `FormatModule.deprecated()` (`false`), the component
  `FormatInfo.deprecated`, and its use in `DefaultConverter.formats()`
  (contracts/library-api.md).

### CHMED23A model, reader and validator

- [X] T012 [P] Create the model records in `M/model/` as in data-model §2, using sealed interfaces
  with records for each `t`:
  - `Medication`, `Patient`, `PatientId`, `MedicalData`, `RiskCategory`, `Extension`,
    `HealthcarePerson`, `HealthcareOrganization`, `Medicament`, `Posology`;
  - `Repetition`: `Number`, `Duration`, `NumberAndDuration`;
  - `PosologyDetail`: `Daily`, `FreeText`, `Single`, `Cyclic`, `Sequence`;
  - `TimedDosage`: `DosageOnly`, `Times`, `DaySegments`, `WeekDays`, `DaysOfMonth`, `Interval`;
  - `DosageObject`: `DosageSimple`, `DosageFromTo`, `DosageRange`;
  - `SequenceObject`: `PosologySequence`, `Pause`;
  - `ApplicationAtTime`, `ApplicationInSegment`;
  - `TimeUnit`.

  Decimals are `BigDecimal`. Lists are immutable and never null.
- [X] T013 [P] Write `T/io/Chmed23aJsonReaderTest.java`:
  - all fixtures of T002–T004 parse;
  - unknown properties give INFORMATION `UNKNOWN_FIELD_IGNORED` with the path (M23-IN rule);
  - an unknown `t` value gives `CHMED_INVALID_FIELD` at the path of `t`;
  - integers given as decimals are rounded with INFORMATION `DERIVED_VALUE` (data-model §2.3);
  - invalid JSON gives `PARSE_ERROR` without content;
  - non-ASCII text is kept.
- [X] T014 Implement `M/io/Chmed23aJsonReader.java` with Jackson tree parsing into the T012
  records. Dispatch the polymorphic objects by `t`. Report paths in the form
  `/meds/0/pos/0/po/tdo/ts/1/dt`, never values. Make T013 pass.
- [X] T015 [P] Write `T/validation/TerminologyTest.java`:
  - the CSVs of T005 contain every code of the pinned `terminology.md`, with a copy under
    `FX/chmed23a/terminology.md` as test input;
  - a lookup with any letter case finds the unit code, e.g. `STK` and `Stk`.

  Then implement `M/validation/Terminology.java`, which loads the CSVs once and is immutable.
- [X] T016 [P] Write `T/validation/Chmed23aValidatorTest.java`, with one synthetic fixture per C23
  rule in `FX/chmed23a/synthetic/invalid-*.json`:
  - every "R" and conditional usage per medType (README tables);
  - value sets;
  - date, time and `toG` formats;
  - each "Limitations and validations" item of posology.md;
  - the nesting rules (data-model §2.3);
  - terminology codes;
  - usage `-` fields → INFORMATION `UNMAPPABLE_ELEMENT` "not used for this document type"
    (research R7);
  - the official fixtures T002–T004 → no ERROR.

  Assert codes and paths, and that messages contain no values.
- [X] T017 [P] Write `T/validation/DotnetValidationTraceTest.java`. It reads the table "C23 ↔ .NET
  validation tests" in `docs/mapping/chmed23a-ch-emed.md`, which lists every test method of
  `Emediplan.ChMed23A.Tests.Unit/Validation/*`, and asserts that each row names a C23 rule with a
  fixture of T016 or "not applicable" with a reason (research R7.4).
- [X] T018 Implement `M/validation/Chmed23aValidator.java` with `validate(Medication,
  ReportCollector, Phase)`, one method per rule group, each annotated with its C23 ids. Make T016
  and T017 pass.

### Transmission format and input detection

- [X] T019 [P] Write `T/io/TransmissionFormatTest.java`:
  - decode a payload, case-insensitively (`chmed23a.` is accepted);
  - subversion `B` → `UNSUPPORTED_VERSION`;
  - decompressed size above `maxDecodedSize` → the feature 003 code;
  - invalid Base64 or GZip → `PARSE_ERROR`;
  - the spec example payload of `chtransmissionformat/README.md` decodes to the draft JSON
    (research F6);
  - chunks (research R5):
    - 2, 3 and 4 chunks in order and shuffled give the same JSON as the single payload;
    - missing, duplicated, out-of-range, mixed `Total`, mixed subversion, `1/1`, and a chunk mixed
      with a plain payload → `CHUNK_INVALID`, with the index in the message and no data;
  - encoding: `CHMED23A.` + Base64 of GZip without timestamp; decoding the encoded value gives
    the input bytes; two encodings are byte-identical.
- [X] T020 Implement `M/io/TransmissionFormat.java` with `decode(String, long maxDecodedSize,
  ReportCollector)`, `join(List<String>)` and `encode(byte[])`, carrying `@MappingRule` M23-QR
  ids. Writing chunks is not offered (R5). Make T019 pass.
- [X] T021 [P] Write `T/io/InputFormDetectorTest.java` for every row of the table in research R3,
  including whitespace-separated chunks, a leading BOM, CHMED16A JSON and payload, and FHIR JSON.
- [X] T022 Implement `M/io/InputFormDetector.java` (R3). Make T021 pass.

### Module skeleton

- [X] T023 Create `M/Chmed23aFormatModule.java` and register it in `META-INF/services`:
  - `format()` returns `CHMED23A`;
  - `pinnedSpecification()` returns `CHMED23A (ig-emediplan/specification@0b579aa6)`;
  - `inputForms()` returns `json, qr-payload, qr-image`;
  - `outputForms()` returns `DOCUMENT, QR_PAYLOAD, QR_IMAGE`;
  - `documentTypes()` returns `MP, Rx`;
  - `deprecated()` returns `true`.

  `toChEmed` reads all input forms through T020/T022 and `QrImageReader` (M23-QR rules as in
  feature 003 M-QR-01..04), validates with T018, and then delegates to builders that the stories
  add. `fromChEmed` opens the document with `ChEmedDocumentReader` and delegates likewise. Until a
  story adds its builder, the corresponding path fails with `UNSUPPORTED_DOCUMENT_TYPE`.
- [X] T024 [P] Create test support in `T/testsupport/`:
  - `Fixtures.java`;
  - `TestOptions.java`;
  - `MappingTestSupport.java` (convert, validate against CH EMED, assert report entries);
  - `CapabilityGaps.java`, which reads the gap list of the mapping specification.

  Reuse `PhiScanner` and `ChEmedAssertions` from the `format-chmed16a` test-jar.
- [X] T025 Write `T/MappingTraceabilityTest.java`, as in CHMED16A. Every rule id in
  `docs/mapping/chmed23a-ch-emed.md` must have an `@MappingRule` in main code and a test that names
  it; every `@MappingRule` id must exist in the document. The test may list rules of later phases as
  pending until the end of Phase 7, where the list must be empty.

**Checkpoint**: The helpers have moved and CHMED16A is unchanged. CHMED23A input in every form is
read and validated; mapping is not wired yet.

---

## Phase 3: User Story 1 - Read a CHMED23A medication plan (Priority: P1) 🎯 MVP

**Goal**: A CHMED23A medication plan as JSON, payload, chunked payload or QR image becomes a valid
CH EMED Medication Card. Everything that changes or is lost is reported.

**Independent Test**: Convert `Example1`–`Example3`, the posology fixtures and the .NET plan
fixtures to CH EMED. Each validates, and its report matches the expected rules (spec US1,
quickstart Scenarios 1–3, 5).

### Tests for User Story 1 ⚠️

> Write these tests FIRST and make sure they FAIL before implementation.

- [X] T026 [P] [US1] Write `T/mapping/PatientMapperTest.java` for M23-PAT and M23-MDT
  (research R11):
  - names, birth date, gender 1/2/3 → `male`/`female`/`other`;
  - address without invented country;
  - phones and emails;
  - `lng` → `communication` and `Composition.language`;
  - ids type 1 → VeKa; type 2 with an OID and with a URL in `sId`;
  - no usable id → option `patientIdentifier`, else `MISSING_REQUIRED_DATA` (F5);
  - each `mData` element → WARNING `UNMAPPABLE_ELEMENT` with clinical loss;
  - extensions → WARNING per path, without name or value.
- [X] T027 [P] [US1] Write `T/mapping/AuthorMapperTest.java` for M23-AUT (research R8):
  - `auth` 1 → PractitionerRole, Practitioner (GLN, names, ZSR) and Organization (name, address,
    GLN, ZSR) from the document;
  - option `author` given → not used, INFORMATION;
  - `auth` 2 → Patient as `Composition.author`.
- [X] T028 [P] [US1] Write `T/mapping/PosologyToFhirMapperTest.java`, one test per row of the
  research R9 table, using the posology fixtures (T003, T004):
  - each exact row gives the expected `Dosage` (structured `timing`, `doseQuantity`/`doseRange`,
    base and split entries);
  - each text row gives `Dosage.text` from `PosologyText` and WARNING `DOSAGE_FREE_TEXT_FALLBACK`
    with clinical loss;
  - FreeText → `Dosage.text` without a report entry;
  - `dtFrom`/`dtTo` → `boundsPeriod` with precision;
  - `inRes`, `appInstr`;
  - `roa` in and outside the CH Term route value set;
  - `moa` mapped and unmapped (WARNING with clinical loss);
  - `unit` mapped (case-insensitive), and unmapped → text;
  - `relMeal` 1–3 with segments and without time → `when` AC*/C*/PC*; with times and 4–7 →
    `additionalInstruction.text` and WARNING without clinical loss.
- [X] T029 [P] [US1] Write `T/mapping/PosologyTextTest.java`. It checks the localized descriptions
  (de, fr, it, en) of every posology type, including nested sequences, intervals and days of the
  month. Equal input gives equal text, and the text contains no patient data.
- [X] T030 [P] [US1] Write `T/mapping/MedicationCardBuilderTest.java` for M23-DOC and M23-MED:
  - `medType` 1 → Medication Card profiles and composition type;
  - `id` UUID → identifier;
  - `id` other text or absent → derived identifier, `DERIVED_VALUE` and `UNMAPPABLE_ELEMENT` (F3);
  - `dt` with offset;
  - `rmk` → annotation section;
  - confidentiality from the option;
  - original representation rendered;
  - no medicaments → `emptyReason`;
  - `idType` 1–4, `rsn`, `autoMed` → `patientspecified`, `prscbBy` → WARNING;
  - one MedicationStatement per posology;
  - medicament without posology (as M-MED-09).
- [X] T031 [P] [US1] Write `T/it/Chmed23aPlanToChEmedIT.java`:
  - `Example1`–`Example3` and every plan fixture of `posology/` and `dotnet/` convert with outcome
    `SUCCESS` or `SUCCESS_WITH_WARNINGS`, and validate against CH EMED 6.0.0 (FR-008);
  - expected outputs for `Example1`–`Example3` in `FX/expected/` are compared byte-exactly;
  - expected report entries per example: F1 non-ASCII text kept, F2 INFORMATION for usage `-`
    fields, F3 derived id, `mData` loss, the text-fallback posologies;
  - write outputs to `format-chmed23a/target/it-outputs/` for `tools/validate-outputs.sh`.
- [X] T032 [P] [US1] Write `T/it/InputFormsIT.java` (SC-003): for each of `Example1`–`Example3`, the
  JSON, the encoded payload, the payload in 2/3/4 shuffled chunks (generated in the test) and a QR
  image (`FX/qr/Example{n}.png`, generated once with `QrImageWriter`) give byte-identical CH EMED
  documents and reports.
- [X] T033 [P] [US1] Write `T/it/EdgeCasesIT.java` for the spec edge cases on the read side:
  - `medType` 2 → `UNSUPPORTED_DOCUMENT_TYPE` naming MP and Rx (FR-003);
  - CHMED16A JSON and payload declared as `chmed23a` → `UNSUPPORTED_VERSION` "use source
    chmed16a";
  - FHIR input → `PARSE_ERROR`;
  - chunk errors → `CHUNK_INVALID`;
  - an image with two codes → `QR_MULTIPLE`;
  - non-conforming input → `CHMED_INVALID_FIELD` with path only;
  - free-text medicament and unknown codes → kept and reported;
  - unknown fields;
  - umlauts and French and Italian texts preserved (`FX/chmed23a/synthetic/non-ascii.json`);
  - `chmed23a → chmed16a` and `chmed23a → ch-emed-epr` → `UNSUPPORTED_CONVERSION` naming the path
    through `ch-emed` (FR-004).
- [X] T034 [P] [US1] Extend
  `format-chmed16a/src/test/java/ch/emed/converter/chmed16a/io/InputFormDetectorTest.java` and
  `.../it/ImageInputIT.java` with a test that CHMED23A JSON, a payload and an image declared as
  `chmed16a` give `UNSUPPORTED_VERSION` with a message naming `chmed23a` (research R3).

### Implementation for User Story 1

- [X] T035 [P] [US1] Implement `M/mapping/PatientMapper.java` (M23-PAT, M23-MDT, M23-EXT for
  patient and medical data). Make T026 pass.
- [X] T036 [P] [US1] Implement `M/mapping/AuthorMapper.java` (M23-AUT). Make T027 pass.
- [X] T037 [P] [US1] Implement `M/mapping/PosologyText.java`, with localized phrases next to the
  moved `Labels` and no invented codes. Make T029 pass.
- [X] T038 [US1] Implement `M/mapping/PosologyToFhirMapper.java` (M23-DOS, research R9). Use
  `core.chemed.Units` and `Terminology`, and add the method table `moa → EDQM` as
  `format-chmed23a/src/main/resources/chmed23a/method-edqm.csv` with only unambiguous rows, marked
  for domain review. Make T028 pass (depends on T037).
- [X] T039 [US1] Implement `M/mapping/MedicationCardBuilder.java` and `M/mapping/MappingScope.java`
  (M23-DOC, M23-MED; derived ids through the `IdStrategy`; original representation through
  `OriginalRepresentationRenderer`). Make T030 pass (depends on T035, T036, T038).
- [X] T040 [US1] Wire plans into `Chmed23aFormatModule.toChEmed`: `medType` 1 →
  `MedicationCardBuilder`; `medType` 2 and others → `UNSUPPORTED_DOCUMENT_TYPE`. Make T031–T033
  pass, and create the expected outputs of T031 after manual review against the mapping
  specification.
- [X] T041 [US1] Change the CHMED16A refusal messages for CHMED23A input in
  `format-chmed16a/src/main/java/ch/emed/converter/chmed16a/io/Chmed16aJsonReader.java` and
  `.../Chmed16aFormatModule.java` to name the source `chmed23a`. Update rules M-IN-04 and M-QR-04 in
  `docs/mapping/chmed16a-ch-emed.md`. Make T034 pass.

**Checkpoint**: Medication plans are read in every form. This is the MVP.

---

## Phase 4: User Story 2 - Read a CHMED23A prescription (Priority: P1)

**Goal**: A CHMED23A prescription becomes a valid CH EMED Medication Prescription, with
repetitions, dispense data and every posology kept or reported.

**Independent Test**: Convert `Example4` and the prescription fixtures to CH EMED. They validate,
and the repetition rules are kept or reported (spec US2).

### Tests for User Story 2 ⚠️

- [X] T042 [P] [US2] Write `T/mapping/PrescriptionDetailsMapperTest.java`, one test per row of
  research R10:
  - `reps` Number, Duration in days/weeks/months/years (inclusive end), Duration in
    seconds/minutes/hours (WARNING with clinical loss), NumberAndDuration;
  - no `reps` → nothing;
  - `nbPack`;
  - `isNotSub`;
  - `sic` → WARNING with clinical loss;
  - `rec` → logical performer reference.

  Synthetic fixtures go in `FX/chmed23a/synthetic/rx-*.json`.
- [X] T043 [P] [US2] Write `T/mapping/MedicationPrescriptionBuilderTest.java` (M23-RX):
  - `medType` 3 → Medication Prescription profiles;
  - each medicament × posology → one MedicationRequest, with dispense fields copied and
    INFORMATION `DERIVED_VALUE` naming the split (F4);
  - medicament without posology → dosage text "dosage not given" and `DERIVED_VALUE`;
  - `auth` 2 in a prescription → `CHMED_INVALID_FIELD`, from the validator;
  - `idType` 4 in a prescription → `CHMED_INVALID_FIELD`.
- [X] T044 [P] [US2] Write `T/it/Chmed23aPrescriptionToChEmedIT.java`:
  - `Example4` and the prescription fixtures convert and validate;
  - the expected output in `FX/expected/` is compared byte-exactly;
  - the report has four MedicationRequests for the tapering medicament (F4) and INFORMATION for
    `autoMed` and `mData` (F2);
  - the payload, chunk and image forms give the same result (SC-003).

### Implementation for User Story 2

- [X] T045 [P] [US2] Implement `M/mapping/PrescriptionDetailsMapper.java` (M23-RX, research R10).
  Make T042 pass.
- [X] T046 [US2] Implement `M/mapping/MedicationPrescriptionBuilder.java`, reusing T035–T039.
  Make T043 pass (depends on T045).
- [X] T047 [US2] Wire `medType` 3 into `Chmed23aFormatModule.toChEmed`, and create the expected
  output of T044 after review. Make T044 pass.

**Checkpoint**: Both CHMED23A document types are read.

---

## Phase 5: User Story 3 - Write CHMED23A from CH EMED (Priority: P2)

**Goal**: CH EMED Medication Cards and Prescriptions become conforming CHMED23A documents as JSON,
payload or QR image. Round trips differ only by documented gaps.

**Independent Test**: Convert the CH EMED outputs of US1 and US2 back. The results pass C23, and
the round trips in both directions match the capability gaps (spec US3, quickstart Scenario 4).

### Tests for User Story 3 ⚠️

- [X] T048 [P] [US3] Write `T/io/Chmed23aJsonWriterTest.java`:
  - property order as in the README tables and the posology.md tables;
  - empty lists and absent values omitted;
  - defaults not materialised;
  - decimals without trailing zeros;
  - UTF-8 without escaping of non-ASCII;
  - no line breaks;
  - reading the written JSON gives back an equal model.
- [X] T049 [P] [US3] Write `T/mapping/reverse/PatientReverseMapperTest.java` (M23-REV, research
  R11):
  - `lng` from the Composition language, then patient communication, then option
    `defaultLanguage` with `DERIVED_VALUE`;
  - gender `unknown` or absent → `MISSING_REQUIRED_DATA`;
  - identifiers: VeKa → type 1, any other system → type 2 with `sId`; none →
    `MISSING_REQUIRED_DATA`;
  - address and telecom.
- [X] T050 [P] [US3] Write `T/mapping/reverse/AuthorReverseMapperTest.java` (research R8):
  - Patient author → `auth` 2 (plan); for a prescription → `MISSING_REQUIRED_DATA`;
  - PractitionerRole → `auth` 1 with `hcPerson` and `hcOrg`;
  - missing street/zip/city/name/GLN → options `author`/`organization`, else
    `MISSING_REQUIRED_DATA` at the path;
  - ZSR only once.
- [X] T051 [P] [US3] Write `T/mapping/reverse/PosologyFromFhirMapperTest.java`:
  - the inverse of every exact row of research R9;
  - a text-only dosage → FreeText without a report entry;
  - an uncovered structure → FreeText with the generated text and WARNING
    `DOSAGE_FREE_TEXT_FALLBACK`;
  - `asNeeded`, `patientInstruction`, `boundsPeriod`, `route`, `method`, `additionalInstruction`
    text of a `relMeal` label → `relMeal`;
  - units back to CDTYP 9 codes.
- [X] T052 [P] [US3] Write `T/mapping/reverse/MedicationPlanBuilderTest.java` and
  `T/mapping/reverse/PrescriptionBuilderTest.java`:
  - document id from `Composition.identifier`;
  - `dt`, `rmk`;
  - medicaments, one per MedicationStatement or MedicationRequest, requests not merged
    (research R10);
  - `reps`, `nbPack`, `isNotSub` and `rec` inverted;
  - CH EMED documents of other types → `UNSUPPORTED_DOCUMENT_TYPE` naming MP and Rx;
  - the CH EMED inputs in `FX/chemed/`: the outputs of T031/T044 and copies of the CHMED16A CH EMED
    fixtures.
- [X] T053 [P] [US3] Write `T/it/ChEmedToChmed23aIT.java`:
  - every input of `FX/chemed/` converts;
  - the output passes `Chmed23aValidator` (FR-007);
  - the expected JSON in `FX/expected/` is compared byte-exactly.
- [X] T054 [P] [US3] Write `T/it/QrOutputIT.java`:
  - `qr-payload` starts with `CHMED23A.` and decodes, by the specification's rules, to the
    `document` output;
  - `qr-image` of a plan and of a prescription is written, read back, and gives the same payload
    (clarification 2026-11-12);
  - a synthetic large card (`FX/chemed/large-card.json`, generated like the CHMED16A
    `LargePlanGenerator`) → `QR_CAPACITY_EXCEEDED` naming 2331 bytes, while `qr-payload` works;
  - no chunked output.
- [X] T055 [P] [US3] Write `T/it/RoundTripIT.java` (FR-011, SC-002):
  - CHMED23A → CH EMED → CHMED23A for every fixture of T002–T004;
  - CH EMED → CHMED23A → CH EMED for every input of `FX/chemed/`;
  - the differences must be exactly those listed by `CapabilityGaps` (data-model §7) and asserted
    per fixture: no `mData`, no extensions, text posologies as FreeText, derived id, split
    prescribed medicaments.
- [X] T056 [P] [US3] Write `T/it/DeterminismIT.java` (FR-012): two runs with identical input and
  options give byte-identical JSON, payload and PNG for every fixture, in both directions.

### Implementation for User Story 3

- [X] T057 [P] [US3] Implement `M/io/Chmed23aJsonWriter.java`. Make T048 pass.
- [X] T058 [P] [US3] Implement `M/mapping/reverse/PatientReverseMapper.java` and
  `M/mapping/reverse/AuthorReverseMapper.java`, plus `ReverseScope`. Make T049 and T050 pass.
- [X] T059 [US3] Implement `M/mapping/reverse/PosologyFromFhirMapper.java`, reusing
  `PosologyText` for the fallback. Make T051 pass.
- [X] T060 [US3] Implement `M/mapping/reverse/MedicationPlanBuilder.java` and
  `M/mapping/reverse/PrescriptionBuilder.java`. Make T052 pass (depends on T058, T059).
- [X] T061 [US3] Wire `Chmed23aFormatModule.fromChEmed`:
  - read the document;
  - build the plan or prescription;
  - run `Chmed23aValidator` in phase `VALIDATE_OUTPUT`;
  - write the JSON;
  - produce the output form: `document`, `qr-payload` through `TransmissionFormat.encode`, or
    `qr-image` through `QrImageWriter` with capacity → `QR_CAPACITY_EXCEEDED`.

  Make T053–T056 pass, and create the expected outputs after review.

**Checkpoint**: Both directions work, round trips are documented, and outputs are deterministic.

---

## Phase 6: User Story 4 - Same behaviour through all interfaces (Priority: P3)

**Goal**: Library, CLI and REST give identical documents and reports for CHMED23A. The
capabilities mark the format as deprecated.

**Independent Test**: The parity test over the CHMED23A corpus is green, and the capabilities show
`chmed23a` with `deprecated` (spec US4, quickstart Scenario 6).

### Tests for User Story 4 ⚠️

- [X] T062 [P] [US4] Extend `converter-cli/src/test/java/ch/emed/converter/cli/FormatsCommandTest.java`:
  the line for `chmed23a` with input and output forms, document types `MP, Rx` and `; deprecated by
  IG eMediplan`; the other lines are unchanged.
- [X] T063 [P] [US4] Write `converter-cli/src/test/java/ch/emed/converter/cli/Chmed23aCliTest.java`:
  - `--from chmed23a` with a JSON file, a chunk text file and a PNG;
  - `--to chmed23a` with `--output-form document|qr-payload|qr-image`;
  - exit `1` for `CHUNK_INVALID` and `QR_CAPACITY_EXCEEDED`;
  - exit `2` for `--output-form qr-image --to ch-emed`;
  - the help text of `--output-form` names `chmed23a`.
- [X] T064 [P] [US4] Extend `converter-rest/src/test/java/ch/emed/converter/rest/contract/CapabilitiesContractTest.java`:
  - the `chmed23a` entry with forms, document types and `"deprecated": true`;
  - no `deprecated` property on other formats;
  - the pairs `chmed23a ↔ ch-emed`;
  - the option text of `outputForm`.
- [X] T065 [P] [US4] Write `converter-rest/src/test/java/ch/emed/converter/rest/contract/Chmed23aConversionContractTest.java`:
  - both pairs with each input form (Base64 `document`) and output form;
  - `chmed23a → chmed16a` → 404 `UNSUPPORTED_CONVERSION`, detail naming `ch-emed`;
  - `CHUNK_INVALID` → 422;
  - responses validate against `openapi.yaml` 1.3.0.
- [X] T066 [P] [US4] Extend `ServiceLibraryParityTest`, `LogLeakTest`,
  `ProblemLeakTest`, `NoOutboundNetworkTest` and `NonAsciiPreservationTest` in `converter-rest`
  with the CHMED23A corpus. Cover SC-004, SC-006 and SC-007
  with the fixtures of T002–T004 and the CH EMED inputs of T052.

### Implementation for User Story 4

- [X] T067 [US4] Add the dependency `format-chmed23a` to `converter-cli/pom.xml` and
  `converter-rest/pom.xml`, and check that the assemblies (thin JAR `lib/`, OCI image) contain it.
  Update `converter-cli/.../FormatsCommand.java` (deprecated suffix) and the `--output-form` help in
  `converter-cli/.../ConvertCommand.java`. Make T062 and T063 pass.
- [X] T068 [US4] Add `deprecated` to `converter-rest/.../model/CapabilitiesResponse.java`, with
  `@JsonInclude(NON_DEFAULT)` so that it is absent when `false`, and fill it in
  `CapabilitiesController.java`. Update the `outputForm` option text there.
- [X] T069 [US4] Update `specs/002-rest-conversion-service/contracts/openapi.yaml` to 1.3.0 as in
  contracts/rest-api.md:
  - `deprecated` property;
  - `FormatName` examples;
  - `requestBody` and `outputForm` descriptions.

  Regenerate the client used by `GeneratedClientIT` if the build requires it. Make T064–T066 pass;
  `OpenApiServedTest` must stay green, so write with LF.

**Checkpoint**: All interfaces behave identically, and the format is marked as deprecated.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, performance, changelog and the final checks

- [X] T070 [P] Write `T/it/PerformanceTest.java` (SC-005, research R14): a synthetic plan with 20
  medicaments of mixed posologies from the posology fixtures, converted to CH EMED and back
  including validation. Assert < 30 s warm. Add the test to the `load` step in
  `tools/remote-build.sh` (`-pl format-chmed16a,format-chmed23a,converter-rest`).
- [X] T071 [P] Update `docs/specifications.md` with a CHMED23A row (repository, commit, files, MIT,
  deprecated with strategy date) and `docs/third-party-licenses.md` with the example fixtures
  (MIT, © 2024 ig-emediplan).
- [X] T072 [P] Update `docs/architecture.md`:
  - the module `format-chmed23a` in the module diagram;
  - the package `core.chemed`;
  - the hub with CHMED23A marked as deprecated.

  Update `README.md` with the formats list and a note on the deprecation.
- [X] T073 [P] Extend `tools/validate-outputs.sh` with
  `format-chmed23a/target/it-outputs/*.json` for the official HL7 validator.
- [X] T074 Complete `docs/mapping/chmed23a-ch-emed.md`:
  - every rule with its final wording;
  - the table "C23 ↔ .NET validation tests" (T017);
  - capability gaps;
  - a domain-review list: `moa` table, units `gtt`/`Appl`, `relMeal` 4–7, `sic`, prescriptions
    with several posologies (R10).

  The traceability test T025 must have no pending rules.
- [X] T075 Update `CHANGELOG.md` `[Unreleased]`:
  - Added: format `chmed23a` (deprecated), `ReasonCode.CHUNK_INVALID`, `FormatInfo.deprecated`
    and `FormatModule.deprecated()`, OpenAPI 1.3.0;
  - Changed: CH EMED helpers moved to `converter-core` (`core.chemed`); CHMED16A refusal message
    for CHMED23A input.
- [X] T076 Run the full build `./mvnw -B clean verify` on the Linux machine
  (`tools/remote-build.sh all`). Report the duration, and fix failures with a regression test
  first.
- [X] T077 Run the time tests on 4 vCPUs (`tools/remote-build.sh load`). Report the duration and
  record the SC-005 measurement in `docs/operations/rest-service.md`.
- [X] T078 Walk through quickstart.md Scenarios 1–7 with the built CLI and REST service, and
  correct the quickstart where the real output differs.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies. T002–T007 can run in parallel after T001; T008 can run
  alongside them.
- **Foundational (Phase 2)**: depends on Phase 1.
  - T009 → T010 must finish before any task that imports `core.chemed`.
  - T011 is independent.
  - T012 → T014; T015 and T016 → T018; T019 → T020; T021 → T022.
  - T023 needs T014, T018, T020, T022.
- **US1 (Phase 3)**: depends on Phase 2.
- **US2 (Phase 4)**: depends on Phase 2. It reuses the US1 mappers (T035–T039), so in practice it
  runs after US1.
- **US3 (Phase 5)**: depends on Phase 2. For the CH EMED inputs it uses the outputs of US1 and US2
  (T031, T044); the copied CHMED16A CH EMED fixtures allow starting earlier.
- **US4 (Phase 6)**: depends on US1 (read) and US3 (write) for the parity corpus; T062, T064 and
  T068 only need T011.
- **Polish (Phase 7)**: depends on all stories.

### User Story Dependencies

- **US1 (P1)**: no other story. This is the MVP.
- **US2 (P1)**: shares the patient, author and posology mappers of US1.
- **US3 (P2)**: independent of US1/US2 in code (reverse mappers), uses their outputs as test input.
- **US4 (P3)**: needs the conversions of US1–US3 to compare interfaces.

### Within Each User Story

- Tests first, failing.
- Mappers, then builders, then the wiring into `Chmed23aFormatModule`.
- Expected outputs are created only after the rule tests pass, and are reviewed against the mapping
  specification.

### Parallel Opportunities

- Phase 1: T002, T003, T004, T005, T006, T007.
- Phase 2: T011, T012, T013, T015, T016, T017, T019, T021, T024, in parallel with the move
  T009/T010.
- US1: all tests T026–T034; then T035, T036, T037 in parallel.
- US2: T042, T043, T044.
- US3: all tests T048–T056; then T057 and T058 in parallel.
- US4: T062–T066.
- Polish: T070–T073.

---

## Parallel Example: User Story 1

```bash
# Tests first, in parallel:
Task: "PatientMapperTest in format-chmed23a/src/test/java/ch/emed/converter/chmed23a/mapping/PatientMapperTest.java"
Task: "AuthorMapperTest in format-chmed23a/src/test/java/ch/emed/converter/chmed23a/mapping/AuthorMapperTest.java"
Task: "PosologyToFhirMapperTest in format-chmed23a/src/test/java/ch/emed/converter/chmed23a/mapping/PosologyToFhirMapperTest.java"
Task: "InputFormsIT in format-chmed23a/src/test/java/ch/emed/converter/chmed23a/it/InputFormsIT.java"

# Then the independent mappers:
Task: "PatientMapper in format-chmed23a/src/main/java/ch/emed/converter/chmed23a/mapping/PatientMapper.java"
Task: "AuthorMapper in format-chmed23a/src/main/java/ch/emed/converter/chmed23a/mapping/AuthorMapper.java"
Task: "PosologyText in format-chmed23a/src/main/java/ch/emed/converter/chmed23a/mapping/PosologyText.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 and Phase 2, including the regression gate T010.
2. Phase 3: plans are read in every form.
3. **Stop and validate**: T031–T033 green, and the CHMED16A suites unchanged.

### Incremental Delivery

1. Setup and Foundational: corpus, helpers moved, reader, validator, transmission format.
2. US1: plans read (MVP).
3. US2: prescriptions read.
4. US3: write and round trips.
5. US4: CLI, REST, capabilities.
6. Polish: documentation, performance, full build on the Linux machine.

Each step ends green. A commit is proposed at each checkpoint.

---

## Notes

- [P] tasks touch different files and do not depend on incomplete tasks.
- Expected outputs are never adjusted to make a test pass without checking the mapping
  specification first.
- Files written by scripts use LF line endings.
- Builds and time tests run on the Linux machine. Ask before long runs, and report each duration.
