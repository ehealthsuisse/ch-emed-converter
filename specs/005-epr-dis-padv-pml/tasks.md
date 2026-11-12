---

description: "Task list for feature 005 - CH EMED EPR, further document types (DIS, PADV, PML)"
---

# Tasks: CH EMED EPR, further document types (DIS, PADV, PML)

**Input**: Design documents from `/specs/005-epr-dis-padv-pml/`

**Prerequisites**:
- plan.md, spec.md, research.md (R1–R11), data-model.md, contracts/ (library-api.md, cli.md,
  rest-api.md), quickstart.md.
- Feature 004 builds green on version 1.2.0-SNAPSHOT.

**Tests**: INCLUDED. Constitution principle III requires:
- test-first;
- fixture tests per mapping rule;
- the official examples in the corpus;
- round trips.

Write each test first and confirm that it fails before implementing.

**Organization**: Tasks are grouped by user story (spec US1–US3), so each story can be implemented
and tested independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story (US1–US3) the task belongs to
- All paths are relative to the repository root

## Path Conventions

- **EPR module**: `format-ch-emed-epr/src/main/java/ch/emed/converter/epr/<package>/`.
- **EPR tests**: `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/<package>/`, with
  end-to-end tests in `.../epr/it/`.
- **Fixtures**: `format-ch-emed-epr/src/test/resources/fixtures/`:
  - `official/`: EPR examples;
  - `chemed/`: CH EMED examples and synthetic CH EMED inputs;
  - `expected/`: byte-exact expected outputs.
- **Never in logs, reports or problem details**: patient data, document content or identifiers of
  references (FR-008, FR-016).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Test corpus and the new reason code

- [X] T001 [P] Copy the missing official EPR examples unchanged from `package/example/` of
  `converter-validation/src/main/resources/packages/ch.fhir.ig.ch-emed-epr-3.0.0.tgz` to
  `format-ch-emed-epr/src/test/resources/fixtures/official/` (research R8):
  - `Bundle-DocumentUCSF4CARAPMP004DISDafalganWithoutPrescription.json`;
  - `Bundle-DocumentPadv2.json`;
  - `Bundle-DocumentPadvCancelParacetamolCARAPMP004.json`;
  - `Bundle-DocumentPadvChangeParacetamolCARAPMP004.json`;
  - `Bundle-BundleUtc1Pml.json`;
  - `Bundle-BundleUtc6bPml.json`.

  Extend `official/README.md`. `Dis1`, `Padv1` and `Pml1` are already there.
- [X] T002 [P] Copy the missing CH EMED examples unchanged from `package/example/` of
  `ch.fhir.ig.ch-emed-6.0.0.tgz` to `fixtures/chemed/`:
  - `Bundle-2-4-MedicationDispense.json`;
  - `Bundle-3-1-MedicationDispense.json`;
  - `Bundle-3-2-MedicationDispense.json`;
  - `Bundle-PharmaceuticalAdvice-ChangeDosage.json`.

  In `chemed/README.md`:
  - note that 1-2, 2-2 and 2-1 are no longer refusal cases but inputs of this feature;
  - note that 3-1 and 3-2 have no treatment-plan reference.
- [X] T003 [P] Add `REFERENCE_INCONSISTENT` (WARNING, research R5) to
  `converter-core/src/main/java/ch/emed/converter/core/report/ReasonCode.java`, with Javadoc. Extend
  the reason-code tests of `converter-core` that list all codes.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Recognise the three types and stop refusing them. Every story depends on this.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 Write tests first in
  `format-ch-emed-epr/src/test/java/ch/emed/converter/epr/EprDocumentTypeTest.java`:
  - `Dis1`, `UCSF4`, the four PADV examples and the three PML examples are recognised as
    DIS/PADV/PML;
  - the CH EMED examples 1-2, 2-4, 3-1, 3-2 (DIS), 2-2 and ChangeDosage (PADV) and 2-1 (PML) are
    recognised as well;
  - 2-1 is recognised as PML although it also carries the card's LOINC `56445-0`;
  - an unknown type gives a message naming all six types.

  Remove the cases that expected "not yet supported".
- [X] T005 Add `DIS`, `PADV` and `PML` with their SNOMED codes and profile suffixes to
  `format-ch-emed-epr/src/main/java/ch/emed/converter/epr/EprDocumentType.java` and remove `LATER`.
  The message for other types lists `PMLC, PRE, MTP, DIS, PADV, PML` (research R1, rule M-EPR-02).
- [X] T006 Adjust the tests that expected the refusal of these types:
  - `it/EdgeCasesIT.java`: edge case 1 now uses a document of an unknown type, built
    synthetically from a CH EMED example with another Composition type;
  - `ChEmedEprFormatModuleTest.java`;
  - `converter-cli/src/test/java/ch/emed/converter/cli/EprCliTest.java`;
  - `converter-rest/src/test/java/ch/emed/converter/rest/contract/EprConversionContractTest.java`.
- [X] T007 Write tests first in `mapping/OriginalRepresentationTest.java` for DIS, PADV and PML:
  - a supplied PDF/A is used;
  - a source PDF/A is kept;
  - a non-PDF/A is removed and reported;
  - without a PDF, none is rendered.

  Then change the `MTP` branch of `mapping/OriginalRepresentation.java` to "every type except PRE
  and PMLC" (research R6, rule M-EPR-25).

**Checkpoint**: The six types are recognised; nothing refuses DIS, PADV or PML any more.

---

## Phase 3: User Story 1 - Read DIS, PADV and PML from the EPR (Priority: P1) 🎯 MVP

**Goal**: Official DIS, PADV and PML documents convert to valid CH EMED documents (SC-001).

**Independent Test**: `OfficialExamplesToChEmedIT` over all DIS, PADV and PML examples; the CH
EMED documents validate.

### Tests for User Story 1 ⚠️

- [X] T008 [P] [US1] Extend `it/OfficialExamplesToChEmedIT.java` to the types `DIS`, `PADV`,
  `PML` (`EprFixtures.official`). For every example:
  - the outcome is not FAILED;
  - the output validates against CH EMED;
  - no clinical loss;
  - no `REFERENCE_INCONSISTENT`.

  Add one case for the empty list `Bundle-BundleUtc1Pml.json`: a valid Medication List without
  entries (US1.4).
- [X] T009 [P] [US1] Extend `mapping/ToChEmedMapperTest.java` with one example per type
  (`Dis1`, `PadvChange…`, `Pml1`), compared byte for byte against new files in
  `fixtures/expected/`. Only the profiles may change, including contained resources and the
  changed statement or request of the advice (rules M-EPR-01, M-EPR-12).
- [X] T010 [P] [US1] Extend `EdgeCasesIT` or `OfficialExamplesToChEmedIT` with US1.5: a PADV with
  one element that violates the EPR profile fails with the validation findings and has no output.

### Implementation for User Story 1

- [X] T011 [US1] Make T008–T010 pass. Expected changes:
  - none in `mapping/ToChEmedMapper.java` and `mapping/ProfileMap.java` (research R2);
  - any profile the examples show missing is added to `ProfileMap` and recorded as a finding in
    `research.md`.

  Generate the expected files with `-Depr.regenerate=true` and review them.
  - *Done 2026-11-10: no code change needed; all 9 official DIS, PADV and PML examples convert by
    the profile change alone (research R2 confirmed).*

**Checkpoint**: US1 is complete; the EPR documents of the three types are readable.

---

## Phase 4: User Story 2 - Create EPR dispenses, advice and lists (Priority: P1)

**Goal**: CH EMED dispenses, advice and Device-authored lists become valid EPR documents (SC-002).
Required data comes from the source or options. References are checked for consistency (FR-008,
SC-008).

**Independent Test**: `ChEmedToEprIT` over the CH EMED examples of T002 with the required
options; every output validates against CH EMED EPR.

### Tests for User Story 2 ⚠️

- [X] T012 [P] [US2] Write tests first in `mapping/RequiredDataTest.java` (research R4, rules
  M-EPR-23, M-EPR-28, M-EPR-29):
  - **Dispense with a treatment-plan reference**: unchanged.
  - **Dispense without a reference (3-1)**:
    - without option: `MISSING_REQUIRED_DATA` at `Bundle.entry[i].resource.extension`, with no
      identifier in the message;
    - with one reference: the extension is added and `DERIVED_VALUE` is reported;
    - with two references: `TREATMENT_PLAN_REFERENCES_MISMATCH`.
  - **Dispense with a reference and the option**: `TREATMENT_PLAN_REFERENCES_MISMATCH`.
  - **PADV without a treatment-plan reference**: no error.
  - **PML**:
    - with a Device author: OK;
    - with a PractitionerRole author (synthetic `fixtures/chemed/MedicationList-PersonAuthor.json`):
      `MISSING_REQUIRED_DATA` at `Composition.author`;
    - the option is ignored for lists and reported as not used.
- [X] T013 [P] [US2] Extend `mapping/UnsupportedElementsTest.java`:
  - one unsupported element in the changed MedicationStatement of a PADV, contained and as a
    Bundle entry;
  - one in a MedicationDispense and its contained Medication.

  Each is removed and reported with its path; clinical ones carry the clinical-loss flag (FR-006).
- [X] T014 [P] [US2] Extend `mapping/FromChEmedMapperTest.java` with one fixture per new rule
  (M-EPR-28, M-EPR-29) and per type (1-2 → DIS, ChangeDosage → PADV, 2-1 → PML). Compare byte for
  byte against new files in `fixtures/expected/`.
- [X] T015 [P] [US2] Write tests first in a new `mapping/ReferenceConsistencyTest.java` (research
  R5, data-model §3, rule M-EPR-04). Add synthetic CH EMED and EPR inputs in `fixtures/chemed/`,
  one contradiction each:
  - C1: the same entry `id` with two document ids;
  - C2: a PML reference to an own entry with another document;
  - C3: a PADV whose changed statement belongs to another treatment plan;
  - C4: a PML dispense whose prescription names another treatment plan.

  For each case: exactly one WARNING `REFERENCE_INCONSISTENT`, with the paths of both extensions,
  no identifier in the message, output byte-identical to the run without the check, and no
  clinical loss. Also: no entry for any official example of both IGs (SC-008).
- [X] T016 [US2] Extend `it/ChEmedToEprIT.java` (SC-002) over 1-2, 2-4, 3-1 and 3-2 (with option),
  2-2, ChangeDosage and 2-1:
  - every output validates against the EPR profiles;
  - the report lists every removed element;
  - with strict mode, clinical loss fails the conversion (US2.4);
  - without the required options, the conversion fails only with documented `MISSING_REQUIRED_DATA`.

  Replace the invalid feature 001 GLN as before (`INVALID_GLN` → `VALID_GLN`).

### Implementation for User Story 2

- [X] T017 [US2] Extend `mapping/RequiredData.java` per data-model §2 (research R4):
  - `treatmentPlans` selects the MedicationDispense for DIS;
  - PADV skips the treatment-plan step;
  - PML checks the Device author, like the PMLC check of F8, and does not use the option.

  Rules M-EPR-23, -28 and -29 are annotated with `@MappingRule`. Make T012 pass.
- [X] T018 [US2] Make T013 and T014 pass. Expected changes:
  - none in `mapping/UnsupportedElements.java` (research R3);
  - in `mapping/FromChEmedMapper.java`, only the type-dependent branches, if any.

  Record any deviation as a finding in `research.md`.
- [X] T019 [US2] Implement `mapping/ReferenceConsistency.java` (rules C1–C4, `@MappingRule`
  M-EPR-04).
  - Call it in `ChEmedEprFormatModule` after mapping, in both directions, before output
    validation.
  - Before fixing the rules, run them over all official examples. A rule that reports an official
    example is narrowed, and the narrowing is recorded as a finding in `research.md`.
  - Make T015 pass.
- [X] T020 [US2] Make T016 pass. For any official CH EMED example that cannot become an EPR
  document even with options, document the reason in `docs/mapping/ch-emed-epr.md` and test it
  as a documented failure.

**Checkpoint**: US1 and US2 work; all three types convert in both directions.

---

## Phase 5: User Story 3 - Same behaviour through all interfaces (Priority: P3)

**Goal**: Library, CLI and REST give identical results. The capabilities name the six types
(FR-014, FR-015, SC-005).

**Independent Test**: The parity test over the new corpus; capabilities and CLI `formats` list
`PMLC, PRE, MTP, DIS, PADV, PML`.

### Tests for User Story 3 ⚠️

- [X] T021 [P] [US3] Extend the expected document types to the six in:
  - `converter-rest/src/test/java/ch/emed/converter/rest/contract/CapabilitiesContractTest.java`;
  - `converter-cli/src/test/java/ch/emed/converter/cli/FormatsCommandTest.java`;
  - `ChEmedEprFormatModuleTest`.
- [X] T022 [P] [US3] Extend to the corpus of T001/T002 and the synthetic inputs of T012/T015:
  - `ServiceLibraryParityTest`;
  - `LogLeakTest`;
  - `ProblemLeakTest`;
  - `NoOutboundNetworkTest`;
  - `PhiLeakTest`.

  Include a DIS with `treatmentPlanReferences` through CLI (`--treatment-plan`) and REST.
- [X] T023 [P] [US3] Extend `EprCliTest` and `EprConversionContractTest` with one DIS, PADV and PML
  conversion in each direction, and with a contradiction (WARNING in the report, exit `0` / HTTP
  `200`).

### Implementation for User Story 3

- [X] T024 [US3] `ChEmedEprFormatModule.documentTypes()` returns the six types. The description
  of `treatmentPlanReferences` names the dispense in:
  - `CapabilitiesController.options()`;
  - the CLI help of `--treatment-plan` in `ConvertCommand`;
  - `specs/002-rest-conversion-service/contracts/openapi.yaml`, which goes to 1.2.1 with the
    `documentTypes` description and example (contracts/rest-api.md).

  Make T021–T023 pass.

**Checkpoint**: All stories work through all interfaces.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T025 [P] CHMED16A refusal (research R7):
  - Test first in `format-chmed16a`: the CH EMED examples 1-2 (DIS), 2-2 (PADV) and 2-1 (PML),
    converted to `chmed16a`, fail with `UNSUPPORTED_DOCUMENT_TYPE`, and the message names
    "medication card" and "medication prescription".
  - Then change the message in `format-chmed16a/.../io/ChEmedDocumentReader.java` and
    `Chmed16aFormatModule.java`.
  - Check that no other test of features 001–003 depends on the old text.
- [X] T026 [P] Extend `it/RoundTripIT.java` (FR-011, SC-003):
  - EPR → CH EMED → EPR for every official DIS, PADV and PML example;
  - CH EMED → EPR → CH EMED for the CH EMED examples of T002 (3-1 and 3-2 with option).

  Only the documented changes are allowed.
- [X] T027 [P] Extend `it/DeterminismIT.java` (FR-012) to the new corpus: byte-identical output
  and report, twice and in a fresh converter.
- [X] T028 Extend `it/PerformanceTest.java` (SC-004, research R11) with a PML of 100 entries of
  each kind, generated synthetically, converted to CH EMED and back, including validation. Run it
  on andromeda with taskset on 4 vCPU and record the times in `docs/operations/rest-service.md`.
- [X] T029 [P] Documentation (research R10):
  - `docs/mapping/ch-emed-epr.md`:
    - rules M-EPR-04, -28 and -29;
    - M-EPR-02, -23 and -25 extended;
    - required data per type;
    - new reason code;
    - CHMED16A gap.
  - `docs/mapping/ch-emed-epr-glossary.md`: dispense, pharmaceutical advice, medication list,
    document reference.
  - `docs/mapping/chmed16a-ch-emed.md`: capability gap DIS/PADV/PML.
  - `docs/architecture.md`: six document types.
  - Spec 004, assumption "DIS, PADV and PML documents follow in a later feature": add a pointer
    to feature 005.

  `MappingTraceabilityTest` passes.
- [X] T030 [P] `CHANGELOG.md` `[Unreleased]`:
  - DIS, PADV and PML in both directions;
  - `REFERENCE_INCONSISTENT`;
  - the CHMED16A message names the supported types;
  - OpenAPI 1.2.1;
  - `treatmentPlanReferences` also for dispenses.
- [X] T031 Run the scenarios of `quickstart.md`. Then run the full build
  (`tools/remote-build.sh build`) and the time tests (`tools/remote-build.sh load`) on andromeda.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies; T001–T003 in parallel.
- **Foundational (Phase 2)**: depends on Setup and blocks all stories. T004 → T005 → T006; T007
  after T005.
- **US1 (Phase 3)**: after Phase 2.
- **US2 (Phase 4)**: after Phase 2. Independent of US1, except that the reference check (T019)
  runs in both directions and is verified against the US1 outputs as well.
- **US3 (Phase 5)**: after US1 and US2, whose conversions it checks.
- **Polish (Phase 6)**: after US3. T025 depends only on Phase 2 and can start earlier.

### Within Each User Story

- Tests first; they must fail before the implementation.
- Rule fixtures before end-to-end tests.
- Run Spotless (`./mvnw -q -B spotless:apply -pl …`) before each build.

### Parallel Opportunities

- T001, T002 and T003.
- In US1: T008, T009 and T010.
- In US2: T012, T013, T014 and T015 (different test files).
- In US3: T021, T022 and T023.
- In Polish: T025, T026, T027, T029 and T030.

## Parallel Example: User Story 2

```text
Task: "RequiredDataTest for DIS, PADV and PML (T012)"
Task: "UnsupportedElementsTest in changed resources (T013)"
Task: "FromChEmedMapperTest per type and rule (T014)"
Task: "ReferenceConsistencyTest C1–C4 (T015)"
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 and Phase 2.
2. Phase 3 (US1). Stop and validate: the EPR documents of the three types are readable.

### Incremental Delivery

1. Setup + Foundational.
2. US1 (read).
3. US2 (create, reference check).
4. US3 (interfaces).
5. Polish (round trips, determinism, performance, docs, CHANGELOG).

Commit after each phase, once approved.

---

## Phase 7: Convergence

- [X] T032 Align FR-014 with FR-018 (full URLs of all six types) and research R7 (CHMED16A refusal message and type detection) per FR-014 (contradicts)
- [X] T033 State in FR-007 that references CH EMED EPR does not support (the advice reference of a dispense, research F2) are removed and reported per FR-007 (partial)
- [X] T034 Name the documented exception of SC-002 (dispense documents with an administration, research F3) per SC-002 (partial)
- [X] T035 Update the edge case "Very large medication list" and plan.md performance goals to SC-004 as clarified (100 entries in total) per SC-004 (contradicts)
- [X] T036 Name the changed prescription of a PADV in the `treatmentPlanReferences` description of contracts/rest-api.md per FR-014/research F1 (partial)
