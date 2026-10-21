---

description: "Task list for feature 001 — CHMED16A ↔ CH EMED Conversion"
---

# Tasks: CHMED16A ↔ CH EMED Conversion

**Input**: Design documents from `/specs/001-chmed16a-ch-emed-conversion/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: REQUIRED. Constitution principle III (Test-First, NON-NEGOTIABLE) mandates that tests
are written first and fail before implementation. Every test task precedes the implementation
task it covers.

**Organization**: Tasks are grouped by user story (US1–US5 from spec.md) so each story can be
implemented and tested independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story the task belongs to (US1 … US5)

## Path Conventions

Maven multi-module project at the repository root (see plan.md "Project Structure"). Abbreviations
used below:

- `CORE` = `converter-core/src/main/java/ch/emed/converter/core`
- `CORE_T` = `converter-core/src/test/java/ch/emed/converter/core`
- `VAL` = `converter-validation/src/main/java/ch/emed/converter/validation`
- `VAL_T` = `converter-validation/src/test/java/ch/emed/converter/validation`
- `C16` = `format-chmed16a/src/main/java/ch/emed/converter/chmed16a`
- `C16_T` = `format-chmed16a/src/test/java/ch/emed/converter/chmed16a`
- `FIX` = `format-chmed16a/src/test/resources/fixtures`
- `CLI` = `converter-cli/src/main/java/ch/emed/converter/cli`
- `CLI_T` = `converter-cli/src/test/java/ch/emed/converter/cli`

General rules for every task (from constitution and data-model.md §5): no `UUID.randomUUID()` or
`Instant.now()` in conversion code; no network access; log only phase, outcome, entry counts,
duration and input size — never content, patient values or eRezept signature data; report messages
never contain patient field values.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Build skeleton, pinned versions, vendored packages, mapping document skeleton

- [X] T001 Create parent `pom.xml` at repository root: groupId `ch.emed.converter`, Java 21 (`maven.compiler.release=21`), modules `converter-core`, `converter-validation`, `format-chmed16a`, `converter-cli`; dependencyManagement pinning exact versions of HAPI FHIR 8.12.x (`hapi-fhir-structures-r4`, `hapi-fhir-validation`, `hapi-fhir-caching-caffeine`), Jackson 2 (`jackson-databind`), picocli, SLF4J API, Logback, JUnit 5, AssertJ, JSONAssert, networknt `json-schema-validator` (test); surefire/failsafe configuration
- [X] T002 Add Maven Wrapper (Maven 3.9.x): `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
- [X] T003 Configure `maven-enforcer-plugin` in `pom.xml`: require Java 21 and Maven 3.9; ban dependencies `org.springframework*`, `jakarta.ws.rs*` and HTTP client libraries in modules `converter-core`, `converter-validation`, `format-chmed16a`, `converter-cli` (constitution V: core free of web/application frameworks)
- [X] T004 [P] Create `converter-core/pom.xml` with dependencies `hapi-fhir-structures-r4`, `slf4j-api`, Apache PDFBox 3.0.x (original-representation PDF, research R12) (+ test deps)
- [X] T005 [P] Create `converter-validation/pom.xml` with dependencies `converter-core`, `hapi-fhir-validation`, `hapi-fhir-caching-caffeine` (+ test deps)
- [X] T006 [P] Create `format-chmed16a/pom.xml` with dependencies `converter-core`, `jackson-databind`; test dependencies `converter-validation`, JSONAssert, AssertJ, JUnit 5
- [X] T007 [P] Create `converter-cli/pom.xml` with dependencies `converter-core`, `converter-validation`, `format-chmed16a` (runtime), picocli, Logback; thin runnable `converter-cli/target/ch-emed-converter.jar` (`Main-Class` `ch.emed.converter.cli.CliMain`, manifest `Class-Path` `lib/`) with dependencies copied to `converter-cli/target/lib/` (maven-jar-plugin + maven-dependency-plugin; originally a shaded JAR, changed 2026-10-21)
- [X] T008 [P] Create `.gitignore` (target/, IDE files) and `.editorconfig` (UTF-8, LF, 4-space Java indent) at repository root
- [X] T009 [P] Configure Spotless with google-java-format in `pom.xml` (`spotless:check` bound to `verify`)
- [X] T010 [P] Create `docs/specifications.md` listing pinned versions per research R2: `ch.fhir.ig.ch-emed#6.0.0`, `ch.fhir.ig.ch-core#6.0.0`, every package of the CH EMED 6.0.0 `package.json` dependency closure with exact version, FHIR 4.0.1, CHMED16A = `ig-emediplan/specification` commit SHA of `main` at implementation start (files `chmed16a/README.md`, `chmed16a/prescription.md`), eRezept Schweiz developer documentation 0.10.2, HAPI FHIR exact version
- [X] T011 Create `tools/fetch-packages.sh` that downloads `ch.fhir.ig.ch-emed#6.0.0` and its full dependency closure as `.tgz` from the FHIR package registry into `converter-validation/src/main/resources/packages/` and writes `converter-validation/src/main/resources/packages/manifest.json` (`name`, `version`, `sha256` per package); run it once and commit the packages (only step with network access)
- [X] T012 [P] Create mapping specification skeleton `docs/mapping/chmed16a-ch-emed.md`: version header, sections Patient, Author/Organisation, Document metadata, Medication identification, Dosage/Posology, Medical data, Prescription details, eRezept readiness, Reason code catalogue, Capability gaps (CHMED16A→CH EMED and CH EMED→CHMED16A), Validation limitations (no terminology server, research R4); each rule gets a stable id `M-<AREA>-<NN>`; add open item "verify whether eRezept accepts free-text medicaments (`IdType` 1)"

**Checkpoint**: `./mvnw verify` builds four empty modules; packages vendored and checksummed

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Hub API, report model, determinism, CH EMED validation, format SPI, CHMED16A model and
test infrastructure — required by every user story

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundational (write first, must fail)

- [X] T013 [P] Write `CORE_T/report/OutcomeCalculatorTest.java` for the non-strict rules verbatim from data-model.md §1: "any ERROR entry → FAILED", "any WARNING entry → SUCCESS_WITH_WARNINGS", "otherwise (INFORMATION only or none) → SUCCESS"
- [X] T014 [P] Write `CORE_T/report/ReportJsonWriterTest.java`: serialized report validates against `converter-core/src/main/resources/schemas/conversion-report.schema.json`; entries ordered "by phase (parse, validate-input, map, validate-output), then path"; same report serializes to identical bytes twice
- [X] T015 [P] Write `CORE_T/determinism/NameBasedIdStrategyTest.java`: UUID v5 from (source document id, element path) is stable across runs and differs per path
- [X] T016 [P] Write `CORE_T/api/DefaultConverterTest.java` with a fake `FormatModule` and fake `ChEmedValidator`: `convert` never throws for invalid input; "`output` is present iff `outcome != FAILED`"; unsupported pair → `FAILED` with `UNSUPPORTED_CONVERSION` ERROR; a pair without `CH_EMED` runs source→CH_EMED→target and the report contains entries of both legs; output validation ERROR → `FAILED`
- [X] T017 [P] Write `CORE_T/spi/FormatRegistryTest.java`: modules discovered via `ServiceLoader`; duplicate `FormatId` → `IllegalStateException`
- [X] T018 [P] Write `VAL_T/PackageLoaderTest.java`: loads every package listed in `manifest.json`; checksum mismatch → `IllegalStateException`
- [X] T019 [P] Write `VAL_T/HapiChEmedValidatorTest.java`: an official CH EMED 6.0.0 Medication Card example from the vendored package validates with 0 errors; a bundle with the Composition removed yields ERROR entries with phase `VALIDATE_OUTPUT`/`VALIDATE_INPUT` and code `FHIR_VALIDATION`; no remote terminology server is configured (assert chain contents)
- [X] T020 [P] Write `VAL_T/ValidationMessageSanitizerTest.java`: validator messages containing quoted element values (e.g. a family name) are reduced to message id + FHIRPath location, no values (FR-017)

### Implementation for Foundational

- [X] T021 [P] Create records/enums in `CORE/api/`: `FormatId` (`CH_EMED`, `CHMED16A`), `ConversionPair`, `ConversionRequest(source, target, input, options)`, `ConversionOptions(strict, validateOutput, idStrategy, clock, maxDecodedSize, defaultLanguage, confidentiality, patientIdentifier, author)` with `defaults()` = "strict=false, validateOutput=true, maxDecodedSize=5 MB, defaultLanguage=de-CH, confidentiality=NORMAL, no patientIdentifier, no author" and records `PatientIdentifier(system, value)`, `AuthorDetails(gln, given, family, organization)`, `OrganizationDetails(name, street, zip, city, country)` (FR-023), `ConversionResult(outcome, output, report)`
- [X] T022 [P] Create report model in `CORE/report/`: `Severity` (`ERROR`, `WARNING`, `INFORMATION`), `Phase` (`PARSE`, `VALIDATE_INPUT`, `MAP`, `VALIDATE_OUTPUT`), `Outcome` (`SUCCESS`, `SUCCESS_WITH_WARNINGS`, `FAILED`), `ReasonCode` constants (`CHMED_INVALID_FIELD`, `UNMAPPABLE_ELEMENT`, `DOSAGE_FREE_TEXT_FALLBACK`, `UNSUPPORTED_DOCUMENT_TYPE`, `UNSUPPORTED_VERSION`, `UNSUPPORTED_CONVERSION`, `MISSING_REQUIRED_DATA`, `UNKNOWN_FIELD_IGNORED`, `CHMED_REVISION_FIELD_MISSING`, `DERIVED_VALUE`, `PDF_CHARACTER_REPLACED`, `FHIR_VALIDATION`, `EREZEPT_REQUIREMENT`, `PARSE_ERROR`), `ReportEntry(severity, code, phase, path, targetPath, message, clinicalLoss)`, `ConversionReport`, `ReportCollector`
- [X] T023 Implement `CORE/report/OutcomeCalculator.java` (non-strict rules; strict rule added in US4) to pass T013
- [X] T024 Copy `specs/001-chmed16a-ch-emed-conversion/contracts/conversion-report.schema.json` to `converter-core/src/main/resources/schemas/conversion-report.schema.json` and implement `CORE/report/ReportJsonWriter.java` (fixed field order, `reportVersion` "1.0.0") to pass T014
- [X] T025 [P] Implement `CORE/determinism/IdStrategy.java` and `NameBasedIdStrategy.java` (UUID v5) to pass T015
- [X] T026 [P] Create SPI in `CORE/spi/`: `FormatModule` (`format()`, `toChEmed(byte[], MappingContext)`, `fromChEmed(Bundle, MappingContext)`), `MappingContext` (options, IdStrategy, optional Clock, ReportCollector), `MappingResult<T>`, `ChEmedValidator` (`validate(Bundle, Phase)` → list of `ReportEntry`)
- [X] T027 Implement `CORE/spi/FormatRegistry.java` (ServiceLoader) to pass T017
- [X] T028 Implement `CORE/fhir/ChEmedJson.java`: shared R4 `FhirContext`, HAPI JSON parser/serializer with fixed settings (pretty-print off, stable element order) for parsing input and serializing output deterministically
- [X] T029 Implement `CORE/api/DefaultConverter.java` and `CORE/api/Converters.java` (`create()`, `create(ChEmedValidator)`; `supportedConversions()`, `pinnedSpecifications()` read from `converter-core/src/main/resources/specifications.properties`); pipeline per contracts/library-api.md "Behavioural contract" items 1–6; thread-safe, no static mutable state; SLF4J logging per data-model.md §5 — to pass T016
- [X] T030 Implement `VAL/PackageLoader.java` (reads `manifest.json`, verifies sha256, loads `.tgz` via `NpmPackageValidationSupport`) to pass T018
- [X] T031 Implement `VAL/ValidationMessageSanitizer.java` to pass T020
- [X] T032 Implement `VAL/HapiChEmedValidator.java`: `FhirInstanceValidator` with `ValidationSupportChain` of `DefaultProfileValidationSupport`, `NpmPackageValidationSupport`, `InMemoryTerminologyServerValidationSupport`, `CommonCodeSystemsTerminologyService`; context built once and reused; maps messages to `ReportEntry` (`FHIR_VALIDATION`, sanitized) — to pass T019
- [X] T033 Register `HapiChEmedValidator` via `converter-validation/src/main/resources/META-INF/services/ch.emed.converter.core.spi.ChEmedValidator` and make `Converters.create()` discover it
- [X] T034 [P] Create CHMED16A model records in `C16/model/` with field names as in the specification (PascalCase JSON keys): `Medication` (Patient, Medicaments, Recoms, MedType, Id, Auth, Zsr, Dt, Rmk, ValBy, ValDt, HcPerson, HcOrg, PFields), `Patient` (FName, LName, BDt, Gender, Street, Zip, City, Lng, Phone, Email, Rcv, Ids, Med, PFields), `PatientId` (Type, Val), `MedicalData` (DLstMen, Prem, ToG, Rc, Meas, PFields), `RiskCategory`, `RiskItem`, `Measurement` (Type, Val, Unit), `Medicament` (Id, IdType, Pos, Unit, TkgRsn, AppInstr, Roa, AutoMed, PrscbBy, Rep, Subs, NbPack, IndC, PFields), `Posology` (DtFrom, DtTo, CyDu, InRes, D, TT), `TakingTime`, `HcPerson`, `HcOrg`, `PField`; optional fields as nullable/`Optional`, no defaults materialised
- [X] T035 [P] Create `C16/Chmed16aFormatModule.java` skeleton (`format()` = `CHMED16A`; `toChEmed`/`fromChEmed` return `UNSUPPORTED_DOCUMENT_TYPE` until implemented) and register it in `format-chmed16a/src/main/resources/META-INF/services/ch.emed.converter.core.spi.FormatModule`
- [X] T036 [P] Create test support in `C16_T/testsupport/`: `Fixtures.java` (load fixtures by relative path), `ChEmedAssertions.java` (validate with `HapiChEmedValidator`, assert 0 errors; `assertMatchesExpected(actual, expectedFile)` resolves `{{id:<path>}}` placeholders via `NameBasedIdStrategy` before a strict JSONAssert comparison), `PhiScanner.java` (collects all patient field values, raw QR payload strings and eRezept signature parameters `i=`/`t=`/`s=` from a fixture and scans strings/log output for them)
- [X] T037 Extract the official CH EMED 6.0.0 example instances (Medication Card and Medication Prescription documents) from the vendored package into `FIX/chemed/` (e.g. `medication-card-example.json`, `medication-prescription-example.json`) with a `FIX/chemed/README.md` naming source package and file

**Checkpoint**: Hub API, report, validation and SPI work with fakes; CHMED16A model compiles —
user stories can start

---

## Phase 3: User Story 1 — Convert a CHMED16A medication plan to CH EMED (Priority: P1) 🎯 MVP

**Goal**: A valid CHMED16A medication plan (MedType 1) becomes a CH EMED 6.0.0 Medication Card
document plus conversion report, via library and CLI.

**Independent Test**: Convert the CHMED16A plan fixtures; every output validates with 0 errors and
every source medication, dosage and note is present or reported (quickstart Scenarios 1 and 5).

### Fixtures for User Story 1

- [X] T038 [P] [US1] Create synthetic CHMED16A plan fixtures in `FIX/chmed16a/` (fictitious persons, test GLNs): `plan-basic.json` (GTIN-coded, `D` dosing), `plan-free-text-med.json` (`IdType` 1), `plan-pharmacode.json` (`IdType` 3), `plan-taking-times.json` (`TT`), `plan-reserve.json` (`InRes` 1), `plan-cycle.json` (`CyDu` ≠ 86400), `plan-medical-data.json` (`Meas`, `Rc`, `DLstMen`, `Prem`, `ToG`), `plan-empty-meds.json`, `plan-invalid-gtin.json`, `plan-older-revision.json`, `plan-unknown-fields.json` (unknown keys + `PFields`), `plan-non-ascii.json` (umlauts/accents, IT/FR text), `plan-remarks-validation.json` (`Rmk`, `ValBy`, `ValDt`)
- [X] T039 [P] [US1] Create negative fixtures in `FIX/chmed16a/`: `invalid-json.txt`, `chmed23a-plan.json` (CHMED23A camelCase structure), `chmed16a-pmc.json` (MedType 2), `plan-missing-birthdate.json`
- [X] T040 [P] [US1] Transcribe every JSON example from `chmed16a/README.md` and `chmed16a/prescription.md` (pinned commit per docs/specifications.md) into `FIX/chmed16a/official/` (one file per example, file name = section of origin) with `FIX/chmed16a/official/README.md` naming source file, section and commit SHA; examples are kept as published (official example data, constitution IV)
- [X] T041 [P] [US1] Create QR payload fixtures in `FIX/chmed16a/`: `plan-basic.qr.txt` (textual QR payload `CHMED16A1…` of `plan-basic.json`, generated once with a documented one-off command and committed), `qr-corrupt-base64.txt`, `qr-corrupt-gzip.txt`, `qr-oversized.txt` (valid payload whose decompressed JSON exceeds the decoder limit), `erezept-url.txt` (synthetic `https://eprescription.hin.ch/#CHMED16A1…&i=…&t=…&s=…`)
- [ ] T042 [US1] Create expected CH EMED outputs in `FIX/expected/chmed16a-to-chemed/` for every positive fixture of T038, authored by hand from the mapping specification BEFORE the implementation (constitution III); generated resource ids and references are written as placeholders `{{id:<path>}}`, which `ChEmedAssertions` (T036) resolves with `NameBasedIdStrategy` before a strict JSONAssert comparison — PARTIAL: hand-authored expected output exists for `plan-basic.json`; the other fixtures are verified by official validation + targeted assertions (T050, T051)

### Tests for User Story 1 (write first, must fail)

- [X] T043 [P] [US1] Write `C16_T/io/Chmed16aJsonReaderTest.java`: valid plan parses; `invalid-json.txt` → `PARSE_ERROR` (phase `PARSE`); `chmed23a-plan.json` → `UNSUPPORTED_VERSION` naming CHMED23A; `plan-basic.qr.txt` parses to the same model as `plan-basic.json`; `qr-corrupt-base64.txt`/`qr-corrupt-gzip.txt` → `PARSE_ERROR` without the payload in the message; `qr-oversized.txt` → `PARSE_ERROR` (decompressed size above `maxDecodedSize`), decoding stops at the limit without reading the whole stream; `erezept-url.txt` → ERROR pointing to the QR/eRezept feature; unknown fields and `PFields` → `UNKNOWN_FIELD_IGNORED` INFORMATION with JSON Pointer path
- [X] T044 [P] [US1] Write `C16_T/validation/Chmed16aValidatorTest.java` covering data-model.md §2 verbatim: `Id` "UUID"; `MedType` "1 = MedicationPlan, 3 = Prescription; 2 → `UNSUPPORTED_DOCUMENT_TYPE`"; Patient "FName, LName, BDt (required)"; Gender "1 male, 2 female"; `Dt` "ISO 8601"; `Zsr` "7 chars"; Medicament `IdType` "1 free text, 2 GTIN, 3 Pharmacode, 4 product number"; GTIN "13 digits with valid check digit (invalid → WARNING, carried as is)"; Pharmacode "7 digits"; Posology `DtFrom` required; "DtTo ≥ DtFrom"; `D` "0..4 numbers [morning, noon, evening, night]; mutually exclusive with TT"; `InRes`/`AutoMed`/`Prem` "0/1"; fields required only since a later revision missing on input → `CHMED_REVISION_FIELD_MISSING` WARNING, not ERROR (data-model.md §2 "Revisions")
- [X] T045 [P] [US1] Write `C16_T/mapping/PatientMapperTest.java` (FName/LName/BDt/Gender 1/2 → male/female/address/Lng/Ids; missing BDt or Gender → `MISSING_REQUIRED_DATA` ERROR, no placeholder; `Ids` type 1 → identifier system VeKa `urn:oid:2.16.756.5.30.1.123.100.1.1.1`; no `Ids` → `patientIdentifier` option, else `MISSING_REQUIRED_DATA` (FR-023))
- [X] T046 [P] [US1] Write `C16_T/mapping/AuthorMapperTest.java` (`Auth` GLN + `author` option → Practitioner (GLN, name)/PractitionerRole/Organization (name, address), ZSR identifier system `urn:oid:2.16.756.5.30.1.123.100.2.1.1`; `Auth` "patient" → author is the Patient; no `author` option and no `HcPerson`/`HcOrg` → `MISSING_REQUIRED_DATA` (FR-023))
- [X] T047 [P] [US1] Write `C16_T/mapping/MedicationMapperTest.java` (`IdType` 2 → GTIN coding, 3 → Pharmacode coding, 1 → `code.text` only, never a guessed code; invalid GTIN carried unchanged; `TkgRsn`, `AppInstr`, `Roa`, `Unit`, `AutoMed`, `PrscbBy` mapped per mapping spec)
- [X] T048 [P] [US1] Write `C16_T/mapping/DosageToFhirMapperTest.java` (`D` → split dosage with `timing.repeat.when` MORN/NOON/EVE/NIGHT; `TT` → `timeOfDay`; `CyDu` → `period/periodUnit`; `InRes` → `asNeeded`; `DtFrom`/`DtTo` → bounds with date precision preserved; unrepresentable posology → free-text dosage + `DOSAGE_FREE_TEXT_FALLBACK` WARNING with `clinicalLoss` true; never a clinically different dosage)
- [X] T049 [P] [US1] Write `C16_T/mapping/MedicalDataMapperTest.java` (Meas, Rc, DLstMen, Prem, ToG → CH EMED representation where supported, else `UNMAPPABLE_ELEMENT` entry)
- [X] T050 [P] [US1] Write `C16_T/mapping/MedicationCardBuilderTest.java` (Bundle type document, `Bundle.identifier` from `Id`, `Composition.date` and `Bundle.timestamp` from `Dt`, Medication Card Composition type/sections, one MedicationStatement per Medicament, ids from `IdStrategy`, empty medication list handled per both specs; `Rmk` → Composition note/annotation per mapping spec; `ValBy`/`ValDt` → mapped per mapping spec or reported as `UNMAPPABLE_ELEMENT`; `Composition.language` from `Patient.Lng` or `defaultLanguage`, localized `title`, `confidentiality` from options, each as `DERIVED_VALUE` INFORMATION (FR-022); section `originalRepresentation` with a PDF `Binary` (FR-021))
- [X] T051 [P] [US1] Write `C16_T/it/Chmed16aToChEmedIT.java`: every positive fixture of T038 → output matches `FIX/expected/chmed16a-to-chemed/` via `ChEmedAssertions.assertMatchesExpected`; every positive fixture of T038, every file in `FIX/chmed16a/official/` with MedType 1 and `plan-basic.qr.txt` → validates with 0 errors (SC-001); every source medicament present or reported (SC-003); negative fixtures of T039 and the corrupt/eRezept fixtures of T041 → `FAILED`, no output
- [X] T052 [P] [US1] Write `CLI_T/ConvertCommandTest.java` per contracts/cli.md: `convert --from chmed16a --to ch-emed <file>` writes only the document to stdout and exits 0; `--report <file>` writes schema-valid JSON; invalid input exits 1 with nothing on stdout; unknown option/format exits 2; stderr never contains fixture patient values
- [X] T053 [P] [US1] Write `CLI_T/FormatsCommandTest.java` (`formats` lists pairs and pinned versions; `--version` prints converter and specification versions)
- [X] T054 [P] [US1] Write `CORE_T/render/OriginalRepresentationRendererTest.java`: renders a PDF (`%PDF-` header, parseable by PDFBox) from the official Medication Card example containing patient name, document date and every medication's display text; rendering the same Bundle twice yields byte-identical PDFs (FR-021); a name with a character outside WinAnsi is replaced in the PDF and reported as `PDF_CHARACTER_REPLACED` INFORMATION

### Implementation for User Story 1

- [X] T055 [US1] Implement `C16/io/Chmed16aPayloadDecoder.java` (prefix check `CHMED16A`, compression flag, Base64 decode, GZip inflate with an output size limit against decompression bombs, taken from `ConversionOptions.maxDecodedSize` (default 5 MB decompressed); errors as `PARSE_ERROR` without echoing the payload)
- [X] T056 [US1] Implement `C16/io/Chmed16aJsonReader.java` (Jackson tree parsing; inputs not starting with `{` go through `Chmed16aPayloadDecoder`; eRezept URL detection; CHMED23A detection by camelCase top-level keys; unknown-field collection with JSON Pointer) to pass T043
- [X] T057 [US1] Implement `C16/validation/Chmed16aValidator.java` with revision-aware rules from data-model.md §2 to pass T044
- [X] T058 [P] [US1] Implement `C16/mapping/PatientMapper.java` to pass T045
- [X] T059 [P] [US1] Implement `C16/mapping/AuthorMapper.java` (GLN system `urn:oid:2.51.1.3`, ZSR system as defined in the pinned CH Core) to pass T046
- [X] T060 [P] [US1] Implement `C16/mapping/MedicationMapper.java` (code systems for GTIN/Pharmacode taken from the pinned CH Term package, documented in the mapping spec) to pass T047
- [X] T061 [P] [US1] Implement `C16/mapping/DosageToFhirMapper.java` to pass T048
- [X] T062 [P] [US1] Implement `C16/mapping/MedicalDataMapper.java` to pass T049
- [X] T063 [US1] Implement `CORE/render/OriginalRepresentationRenderer.java` (PDFBox, Helvetica/WinAnsi, trailer `/ID` from Bundle identifier, dates = `Composition.date`, no producer metadata) to pass the renderer test; expose `MappingContext.renderer()`
- [X] T064 [US1] Implement `C16/mapping/MedicationCardBuilder.java` (depends on T058–T062) to pass T050
- [X] T065 [US1] Wire `Chmed16aFormatModule.toChEmed` for MedType 1 (reader → validator → mappers → builder; MedType 3 still `UNSUPPORTED_DOCUMENT_TYPE` until US3) to pass T051
- [X] T066 [US1] Implement CLI in `CLI/`: `CliMain.java` (picocli root), `ConvertCommand.java` (`--from`, `--to`, `<input-file>|-`, `-o/--output`, `--report`, `--report-format json|text`, `--language de-CH|fr-CH|it-CH|en-CH`, `--confidentiality normal|restricted|secret`, `--patient-id <system>|<value>`, `--author <file>`), `FormatsCommand.java`, `VersionProvider.java`, `TextReportRenderer.java`, exit codes verbatim from contracts/cli.md: "0 SUCCESS or SUCCESS_WITH_WARNINGS", "1 FAILED", "2 usage error", "3 internal error"; stdout only the document — to pass T052, T053
- [X] T067 [US1] Add `converter-cli/src/main/resources/logback.xml` (stderr only, pattern without MDC content, level INFO) and verify no input content is logged
- [X] T068 [US1] Document all rules implemented in US1 with ids `M-…` (incl. `M-DOC-…` rules for `Rmk`, `ValBy`, `ValDt`, the QR payload input form and the revision rule) and the CHMED16A→CH EMED capability gaps in `docs/mapping/chmed16a-ch-emed.md`

**Checkpoint**: MVP — `ch-emed-converter.jar convert --from chmed16a --to ch-emed` works for
medication plans (quickstart Scenarios 1 and 5)

---

## Phase 4: User Story 2 — Convert a CH EMED document to CHMED16A (Priority: P2)

**Goal**: A valid CH EMED Medication Card document becomes a conformant CHMED16A medication plan
plus report.

**Independent Test**: Convert the official CH EMED Medication Card examples; outputs pass the
CHMED16A validator, every medication present or reported (quickstart Scenario 2).

### Fixtures for User Story 2

- [X] T069 [P] [US2] Create CH EMED input fixtures in `FIX/chemed/`: `card-inactive-medication.json` (stopped/paused statements), `card-complex-timing.json` (constructs CHMED16A cannot express exactly), `card-epr-elements.json` (CH EMED EPR-only elements), `card-unsupported-type.json` (Medication Treatment Plan document), `card-invalid-profile.json`
- [X] T070 [US2] Create expected CHMED16A outputs in `FIX/expected/chemed-to-chmed16a/` for the official Medication Card example and the positive fixtures of T069, authored by hand before the implementation (same approach as T042; CHMED16A `Id` taken from `Bundle.identifier`, so no placeholders needed unless ids are generated)

### Tests for User Story 2 (write first, must fail)

- [X] T071 [P] [US2] Write `C16_T/io/ChEmedDocumentReaderTest.java`: non-JSON / non-Bundle → `PARSE_ERROR`; `card-invalid-profile.json` → `FHIR_VALIDATION` ERRORs (phase `VALIDATE_INPUT`); `card-unsupported-type.json` → `UNSUPPORTED_DOCUMENT_TYPE` naming the type (FR-007); `card-epr-elements.json` accepted, EPR-only elements reported
- [X] T072 [P] [US2] Write `C16_T/mapping/reverse/PatientReverseMapperTest.java`, `AuthorReverseMapperTest.java`, `MedicationReverseMapperTest.java` (inverse of US1 rules; missing data required by CHMED16A → `MISSING_REQUIRED_DATA` ERROR)
- [X] T073 [P] [US2] Write `C16_T/mapping/reverse/DosageFromFhirMapperTest.java`: exact mapping where CHMED16A can express the regimen; otherwise the closest representation allowed by the mapping spec, never clinically different; free text + report entry if no faithful representation (US2 scenario 2)
- [X] T074 [P] [US2] Write `C16_T/mapping/reverse/MedicationPlanBuilderTest.java`: `Id` from `Bundle.identifier`, `Dt` from `Composition.date`, MedType 1, inactive medications handled per mapping spec (included with status or excluded and reported, never silently dropped); Composition note → `Rmk`; validation information → `ValBy`/`ValDt` where present
- [X] T075 [P] [US2] Write `C16_T/io/Chmed16aJsonWriterTest.java`: property order as in the specification tables; defaults (`NbPack` 1, `CyDu` 86400) not materialised; non-ASCII preserved byte-exact; output passes `Chmed16aValidator`
- [X] T076 [P] [US2] Write `C16_T/it/ChEmedToChmed16aIT.java`: official Medication Card example and T069 fixtures → expected outputs (SC-002), validator passes, every medication present or reported
- [X] T077 [P] [US2] Extend `CLI_T/ConvertCommandTest.java` with `--from ch-emed --to chmed16a` (stdin input, exit 0, stdout CHMED16A JSON)

### Implementation for User Story 2

- [X] T078 [US2] Implement `C16/io/ChEmedDocumentReader.java` (parse via `ChEmedJson`, validate input via `ChEmedValidator` phase `VALIDATE_INPUT`, detect document type from `Composition.type` per CH EMED 6.0.0) to pass T071
- [X] T079 [P] [US2] Implement `C16/mapping/reverse/PatientReverseMapper.java`, `AuthorReverseMapper.java`, `MedicationReverseMapper.java` to pass T072
- [X] T080 [P] [US2] Implement `C16/mapping/reverse/DosageFromFhirMapper.java` to pass T073
- [X] T081 [US2] Implement `C16/mapping/reverse/MedicationPlanBuilder.java` to pass T074
- [X] T082 [US2] Implement `C16/io/Chmed16aJsonWriter.java` to pass T075
- [X] T083 [US2] Wire `Chmed16aFormatModule.fromChEmed` for Medication Card documents to pass T076, T077
- [X] T084 [US2] Document CH EMED→CHMED16A rules and capability gaps (incl. inactive medication handling and `M-DOC-…` rules for `Rmk`, `ValBy`, `ValDt`) in `docs/mapping/chmed16a-ch-emed.md`

**Checkpoint**: Both directions work for medication plans

---

## Phase 5: User Story 3 — Convert prescriptions in both directions (Priority: P3)

**Goal**: CHMED16A prescriptions (MedType 3) ↔ CH EMED Medication Prescription documents;
produced CHMED16A prescriptions meet eRezept Schweiz data requirements (FR-008b).

**Independent Test**: Convert prescription fixtures and official CH EMED prescription examples in
both directions; outputs pass conformance checks; missing prescriber GLN fails with
`EREZEPT_REQUIREMENT` (quickstart Scenario 3).

### Fixtures for User Story 3

- [X] T085 [P] [US3] Create CHMED16A prescription fixtures in `FIX/chmed16a/`: `rx-basic.json` (`Rep`, `Subs`, `NbPack`, `IndC`, `Rcv`, `HcPerson`, `HcOrg`, `Zsr`), `rx-erezept-example.json` (synthetic, structured like the example on developers.e-rezept.ch `rezdat.html`), `rx-missing-hcperson.json`, `rx-idtype4.json`, `rx-invalid-fields.json` (`Rep` 3 digits, `Subs` 2, `IndC` wrong format)
- [X] T086 [P] [US3] Create CH EMED prescription fixtures in `FIX/chemed/`: `rx-missing-prescriber-gln.json`, `rx-substitution-repeats.json`
- [X] T087 [US3] Create expected outputs in `FIX/expected/chmed16a-to-chemed/` and `FIX/expected/chemed-to-chmed16a/` for all positive prescription fixtures and the official Medication Prescription example, authored by hand before the implementation (same placeholder approach as T042)

### Tests for User Story 3 (write first, must fail)

- [X] T088 [P] [US3] Extend `C16_T/validation/Chmed16aValidatorTest.java` with prescription rules verbatim from data-model.md §2: `Auth` "prescription: GLN of a person (13 digits)"; `HcPerson / HcOrg` "missing on **input** → WARNING `CHMED_REVISION_FIELD_MISSING`, missing on **output** → ERROR" (`rx-missing-hcperson.json` → WARNING); `Rep` "prescription only; ≤ 2 digits (months)"; `Subs` "prescription only; 0/1 (1 = must not be substituted)"; `NbPack` "prescription only; ≤ 10 digits"; `IndC` "prescription only; format `00000.00`"; `IdType` 4 "not allowed in prescriptions"
- [X] T089 [P] [US3] Write `C16_T/mapping/PrescriptionDetailsMapperTest.java` (to and from CH EMED: `NbPack` → dispense quantity, `Rep` → validity/repeats, `Subs` → substitution, `IndC` → indication, `Rcv` → intended receiver, prescriber/organisation GLN and ZSR, prescription date — exact elements per mapping spec)
- [X] T090 [P] [US3] Write `C16_T/validation/ErezeptPrescriptionRulesTest.java` per data-model.md §4: "MedType = 3, `Id` UUID, `Dt` present"; "`Auth` = GLN of the prescribing person; `HcPerson` and `HcOrg` present"; no `IdType` 4; strict CHMED16A conformance; `rx-missing-prescriber-gln.json` → `EREZEPT_REQUIREMENT` ERROR naming the path, no output (FR-008b)
- [X] T091 [P] [US3] Write `C16_T/it/PrescriptionIT.java`: CHMED16A prescriptions of T085 and every file in `FIX/chmed16a/official/` with MedType 3 → CH EMED Medication Prescription (0 validation errors); official CH EMED prescription example → CHMED16A meeting eRezept rules (SC-007); output document type follows input type (US3 scenario 3)

### Implementation for User Story 3

- [X] T092 [US3] Add prescription rules to `C16/validation/Chmed16aValidator.java` to pass T088
- [X] T093 [P] [US3] Implement `C16/mapping/PrescriptionDetailsMapper.java` (both directions) to pass T089
- [X] T094 [P] [US3] Implement `C16/validation/ErezeptPrescriptionRules.java`; verify against the pinned eRezept documentation whether free-text medicaments (`IdType` 1) are accepted and encode the result (rule + mapping spec note) — to pass T090
- [X] T095 [US3] Implement `C16/mapping/MedicationPrescriptionBuilder.java` (MedicationRequest entries) and `C16/mapping/reverse/PrescriptionBuilder.java` (MedType 3)
- [X] T096 [US3] Wire MedType 3 in `Chmed16aFormatModule.toChEmed` and Medication Prescription documents in `fromChEmed`, applying `ErezeptPrescriptionRules` to every produced prescription — to pass T091
- [X] T097 [US3] Document prescription and eRezept rules (incl. the `IdType` 1 finding) in `docs/mapping/chmed16a-ch-emed.md`

**Checkpoint**: Plans and prescriptions convert in both directions

---

## Phase 6: User Story 4 — Control lossy conversions via the conversion report (Priority: P4)

**Goal**: Strict mode turns any loss of clinically relevant information into `FAILED`; reports never
contain patient data.

**Independent Test**: Convert fixtures with known gaps in default and strict mode and compare
outcome and entries (quickstart Scenarios 4 and 7).

### Tests for User Story 4 (write first, must fail)

- [X] T098 [P] [US4] Create `FIX/chmed16a/plan-unmappable-element.json` (contains an element CH EMED cannot represent, flagged `clinicalLoss` true)
- [X] T099 [P] [US4] Extend `CORE_T/report/OutcomeCalculatorTest.java` with the strict rule verbatim: "strict && any entry with clinicalLoss = true → FAILED"
- [X] T100 [P] [US4] Write `C16_T/it/StrictModeIT.java`: default → `SUCCESS_WITH_WARNINGS` with document; strict → `FAILED`, no document, report names every element that caused the failure
- [X] T101 [P] [US4] Write `C16_T/it/PhiLeakTest.java`: run the full fixture corpus (including QR payload and eRezept URL fixtures), collect all reports and captured log output (Logback list appender) and scan with `PhiScanner` for (a) every patient field value of the fixtures, (b) every raw QR payload string and any substring of ≥ 20 characters of it, (c) eRezept signature parameters (`i=`, `t=`, `s=` values) → zero matches (FR-017, SC-006, constitution IV)
- [X] T102 [P] [US4] Extend `CLI_T/ConvertCommandTest.java`: `--strict` with `plan-unmappable-element.json` exits 1 with empty stdout; without `--strict` exits 0

### Implementation for User Story 4

- [X] T103 [US4] Add the strict rule to `CORE/report/OutcomeCalculator.java` and pass `ConversionOptions.strict` through `DefaultConverter` to pass T099, T100
- [X] T104 [US4] Review every report entry emitted in `C16/mapping/` and `C16/mapping/reverse/` and set `clinicalLoss` consistently with the mapping spec's capability-gap list
- [X] T105 [US4] Add `--strict` option to `CLI/ConvertCommand.java` to pass T102
- [X] T106 [US4] Fix any leak found by T101 (messages, logging) in the affected classes

**Checkpoint**: Report-driven automation and strict mode work; privacy scan green

---

## Phase 7: User Story 5 — Round-trip fidelity (Priority: P5)

**Goal**: Round trips preserve clinical content; every difference is a documented capability gap;
output is byte-deterministic.

**Independent Test**: Run round-trip and determinism tests over all fixtures (quickstart Scenario 6).

### Tests for User Story 5 (write first, must fail)

- [X] T107 [P] [US5] Create machine-readable gap list `format-chmed16a/src/test/resources/capability-gaps.json` mirroring the capability-gap section of `docs/mapping/chmed16a-ch-emed.md` (rule id, direction, affected path)
- [X] T108 [P] [US5] Write `C16_T/testsupport/ClinicalEquivalence.java` (compares two CHMED16A or two CH EMED documents ignoring generated ids; returns differences as paths)
- [X] T109 [P] [US5] Write `C16_T/it/RoundTripIT.java`: every CHMED16A fixture (plans and prescriptions) → CH EMED → CHMED16A, and every official/synthetic CH EMED fixture → CHMED16A → CH EMED; all differences must be listed in `capability-gaps.json` (SC-004)
- [X] T110 [P] [US5] Write `C16_T/it/DeterminismIT.java`: each fixture converted twice in both directions yields byte-identical output and equal reports (FR-018)
- [X] T111 [P] [US5] Write `C16_T/it/MappingTraceabilityTest.java`: every `@MappingRule("M-…")` id used in `C16/mapping/**` exists in `docs/mapping/chmed16a-ch-emed.md` and vice versa (SC-008)

### Implementation for User Story 5

- [X] T112 [US5] Create annotation `C16/mapping/MappingRule.java` and annotate every mapper method with its rule id
- [X] T113 [US5] Fix mapping asymmetries found by T109/T110 in `C16/mapping/` and `C16/mapping/reverse/`, or document them as capability gaps in `docs/mapping/chmed16a-ch-emed.md` and `capability-gaps.json`

**Checkpoint**: All user stories complete and verified

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Performance, official cross-check, documentation, licences, quickstart run

- [X] T114 [P] Create generator `C16_T/testsupport/LargePlanGenerator.java` and write `C16_T/it/PerformanceTest.java`: 100-medication plan converts in < 2 s with a warm validation context; cold start time logged separately (SC-005, research R10) — SC-005 was split into SC-005a (conversion < 2 s) and SC-005b (official validation < 15 s) on 2026-10-21 after measuring; both are asserted by `PerformanceTest`
- [X] T115 [P] Create `tools/validate-outputs.sh`: runs HL7 `validator_cli.jar` (`-version 4.0.1 -tx n/a`, vendored packages as `-ig`) over all CH EMED outputs produced by the integration tests; expects `Errors: 0` (constitution I cross-check)
- [X] T116 [P] Write `README.md` at repository root: purpose, build (`./mvnw verify`), CLI usage per contracts/cli.md, library usage per contracts/library-api.md, pinned versions link to `docs/specifications.md`
- [X] T117 [P] Add licence check (`license-maven-plugin` third-party report) in `pom.xml` and review that all dependencies are Apache-2.0/MIT/EPL-compatible; record result in `docs/third-party-licenses.md`
- [ ] T118 Finalise `docs/mapping/chmed16a-ch-emed.md` (version 1.0.0, all rules, reason code catalogue, capability gaps, validation limitations) and have it reviewed by a person with CH EMED / eMediplan domain knowledge (constitution quality gate 4) — PARTIAL: the specification is complete (version 1.0.0: all rules, reason code catalogue, capability gaps, validation limitations, logging) and checked against the code by `MappingTraceabilityTest`; the review by a person with CH EMED / eMediplan domain knowledge is still pending, the interpretation points are listed at the top of the document
- [X] T119 [P] Create `CHANGELOG.md` at repository root (Keep a Changelog format, semantic versioning) with entry 1.0.0 listing: supported conversions (CHMED16A ↔ CH EMED for medication plans and prescriptions), accepted input forms (JSON, textual QR payload), pinned specification versions (link `docs/specifications.md`), report schema version 1.0.0, CLI exit codes; note that later changes to output structure, codes or report format MUST be recorded here (constitution "Development Workflow")
- [X] T120 Run all scenarios of `specs/001-chmed16a-ch-emed-conversion/quickstart.md` against the packaged JAR and record results in `specs/001-chmed16a-ch-emed-conversion/checklists/quickstart-run.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies; T011 needs network once
- **Foundational (Phase 2)**: depends on Setup — BLOCKS all user stories
- **US1 (Phase 3)**: depends on Foundational
- **US2 (Phase 4)**: depends on Foundational; reuses US1 mapping knowledge but is testable on its own (official CH EMED examples as input)
- **US3 (Phase 5)**: depends on US1 (T058–T065) and US2 (T078–T083), because prescriptions reuse patient, author, medication and dosage mappers of both directions
- **US4 (Phase 6)**: depends on US1; extends US2/US3 entries in T104 if those are done
- **US5 (Phase 7)**: depends on US1 and US2 (round trips need both directions); covers US3 fixtures if US3 is done
- **Polish (Phase 8)**: depends on all desired stories

### User Story Dependencies

```text
Setup → Foundational → US1 ─┬─▶ US2 ─┬─▶ US3
                            │        └─▶ US5
                            └─▶ US4
```

### Within Each Story

- Fixtures → tests (must fail) → models/readers → mappers → builders → module wiring → CLI → mapping documentation

### Parallel Opportunities

- Setup: T004–T010 and T012 in parallel after T001
- Foundational tests T013–T020 in parallel; implementation T021, T022, T025, T026, T034–T036 in parallel
- US1: fixtures T038–T041 in parallel; tests T043–T054 in parallel; mappers T058–T062 in parallel
- US2: tests T071–T077 in parallel; mappers T079/T080 in parallel
- US3: T085/T086, T088–T091, T093/T094 in parallel
- US4 can run in parallel with US2 once US1 is done (different files except T104)
- Polish T114–T117 and T119 in parallel

---

## Parallel Example: User Story 1

```bash
# All US1 tests together (after fixtures T038–T042):
Task: "Chmed16aJsonReaderTest in format-chmed16a/src/test/java/ch/emed/converter/chmed16a/io/"
Task: "Chmed16aValidatorTest in .../chmed16a/validation/"
Task: "PatientMapperTest, AuthorMapperTest, MedicationMapperTest, DosageToFhirMapperTest, MedicalDataMapperTest in .../chmed16a/mapping/"
Task: "ConvertCommandTest, FormatsCommandTest in converter-cli/src/test/java/ch/emed/converter/cli/"

# Then all independent mappers together:
Task: "PatientMapper", "AuthorMapper", "MedicationMapper", "DosageToFhirMapper", "MedicalDataMapper" in format-chmed16a/src/main/java/ch/emed/converter/chmed16a/mapping/
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup → Phase 2 Foundational
2. Phase 3 US1: CHMED16A medication plan → CH EMED via library and CLI
3. **STOP and VALIDATE**: quickstart Scenarios 1 and 5
4. Demo: import of existing eMediplans into CH EMED

### Incremental Delivery

1. US1 → MVP (plan import)
2. US2 → plan export (e.g. printing an eMediplan from EPR data)
3. US3 → prescriptions, eRezept-ready
4. US4 → strict mode and privacy scan for unattended integrations
5. US5 → round-trip evidence for domain review
6. Feature 002 (REST service) can start as soon as the core API (Phase 2) and US1 exist; feature 001
   counts as complete only once its conversions are also available via REST (constitution
   "Delivery interfaces")

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- Verify each test fails before implementing (constitution III)
- Commit after each task or logical group; stop at any checkpoint to validate the story
- Real patient data or real eRezept QR codes MUST NEVER be committed (constitution IV)
