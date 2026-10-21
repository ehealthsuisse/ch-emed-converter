# Research: CHMED16A ↔ CH EMED Conversion

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-10-18

Each entry: Decision / Rationale / Alternatives considered. Sources at the end.

## R1 — Source format: CHMED16A instead of CHMED23A

- **Decision**: Implement CHMED16A (current specification revision, format identifier
  `CHMED16A1`) for medication plans (MedType 1) and prescriptions (MedType 3).
  PolymedicationCheck (MedType 2) is rejected.
- **Rationale**: IG eMediplan lists CHMED16A as "the only relevant and widely used version" and
  marks CHMED23A as deprecated ("Development has been stopped, effectively withdrawing this
  version"). The IG eMediplan strategy 2026 (2025-12-02) moves to HL7 FHIR CH EMED and does not
  implement CHMED23A. eRezept Schweiz is based on CHMED16A prescriptions.
- **Alternatives**: CHMED23A first (rejected: withdrawn, little production data); both at once
  (rejected: YAGNI, CHMED23A follows as its own feature).

## R2 — Pinned specification versions

- **Decision**:

  | Artefact | Pinned version |
  |----------|----------------|
  | CH EMED IG (`ch.fhir.ig.ch-emed`) | 6.0.0 (2025-12-16, trial-use, FHIR 4.0.1) |
  | CH Core (`ch.fhir.ig.ch-core`) | 6.0.0 (dependency of CH EMED 6.0.0) |
  | Further packages | exactly the versions declared in the CH EMED 6.0.0 `package.json` dependency closure (CH Term, HL7 Terminology, FHIR Extensions Pack, …), resolved once and vendored |
  | FHIR | R4 (4.0.1) |
  | CHMED16A | `ig-emediplan/specification` `chmed16a/README.md` and `chmed16a/prescription.md`, pinned to a git commit SHA recorded in `docs/specifications.md` at implementation start |
  | eRezept Schweiz | developer documentation version 0.10.2 (data requirements: `rezdat.html`, `precon.html`) |

- **Rationale**: Constitution I requires pinning; CH EMED 6.0.0 is the latest release. The
  CHMED16A specification is versioned only via git, so a commit SHA is the precise pin.
- **Alternatives**: CH EMED 5.0.0 (rejected: superseded); CH EMED `current` CI build
  (rejected: not a release).

## R3 — FHIR library and runtime

- **Decision**: Java 21 LTS; HAPI FHIR 8.12.x (`hapi-fhir-structures-r4`,
  `hapi-fhir-validation`, `hapi-fhir-caching-caffeine`), pinned to the exact patch version in the
  Maven BOM.
- **Rationale**: Mandated by constitution (Java 21, HAPI FHIR). 8.12.1 is the latest stable
  release line (2026-09); HAPI 8 requires Java 17+, so Java 21 is supported.
- **Alternatives**: Firely/.NET, plain JSON handling of FHIR (rejected by constitution).

## R4 — Conformance validation (Principle I) without network access (Principle IV)

- **Decision**: Use HAPI's `FhirInstanceValidator` (wraps the official HL7 FHIR validator core)
  with a `ValidationSupportChain` of `DefaultProfileValidationSupport`,
  `NpmPackageValidationSupport` (vendored `.tgz` packages from R2),
  `InMemoryTerminologyServerValidationSupport` and `CommonCodeSystemsTerminologyService`.
  No remote terminology server. The validation context is built once and reused.
  Output validation runs by default in CLI and library (option to disable for trusted bulk use
  in the library only); in tests it is always on. Additionally, CI runs the official
  `validator_cli.jar` (same pinned packages, `-tx n/a`) over all fixture outputs as a
  cross-check.
- **Rationale**: Uses the official validator logic locally; vendored packages make results
  reproducible and offline.
- **Known limitation**: Without a terminology server, SNOMED CT / large external code system
  membership cannot be fully checked; the validator reports these as warnings/information.
  Documented in the mapping specification; codes are carried unchanged (FR-012).
- **Alternatives**: matchbox server (rejected: separate service, network call); remote
  tx.fhir.org (rejected: Principle IV).

## R5 — CHMED16A validation

- **Decision**: Hand-written, rule-based validator in `format-chmed16a`, derived field by field
  from the pinned README/prescription.md (types, lengths, required fields, code values),
  plus the eRezept data requirements as an additional rule set for prescriptions.
  Unknown fields and `PFields` are ignored and reported as information.
- **Revisions**: medication plans carry no revision marker. Prescriptions carry `rev` (absent =
  1, pinned = 3, `prescription.md` "Revisions"); fields required only by a later revision than
  the declared one (e.g. `HcPerson`/`HcOrg`, since revision 2) missing → WARNING
  `CHMED_REVISION_FIELD_MISSING`. Output always conforms to the latest revision (decided
  2026-10-18, analysis finding U1; refined during implementation after reading the pinned
  prescription specification).
- **Input forms**: plain JSON or the textual QR payload (prefix `CHMED16A`, compression flag,
  Base64 + GZip per ChTransmissionFormat), decoded by `Chmed16aPayloadDecoder` with a size limit
  against decompression bombs (constitution Domain constraint; analysis finding C2). Signed
  eRezept URLs and QR images remain in the QR/eRezept feature.
- **Version detection**: CHMED23A documents are recognised by their camelCase top-level keys
  (`patient`, `meds`, `medType`) and rejected with `UNSUPPORTED_VERSION`.
- **Rationale**: IG eMediplan publishes no JSON schema for CHMED16A (only the README tables);
  constitution I allows hand-written checks where no official artefact exists.
- **Alternatives**: Self-authored JSON Schema + schema validator (possible later; the rules
  would be the same, schema adds a dependency without covering cross-field rules).

## R6 — Mapping reference

- **Decision**: Author the mapping specification `docs/mapping/chmed16a-ch-emed.md` (versioned
  with the code). Use CHMED20AF (FHIR representation of CHMED16A by ahdis/IG eMediplan, a
  superset of CH EMED) and the CH EMED mapping spreadsheet as references, documenting every
  deviation.
- **Rationale**: Constitution II requires a single mapping specification; CHMED20AF is the
  closest official prior art.
- **Caveat**: The `ahdis/chmed` repository was archived on 2026-06-24 (read-only); it is used as
  a reference only, not as a dependency.

## R7 — Key mapping decisions (summary; details in the mapping specification)

| CHMED16A | CH EMED | Note |
|----------|---------|------|
| MedType 1 (MedicationPlan) | Medication Card document (Composition + MedicationStatement entries) | |
| MedType 3 (Prescription) | Medication Prescription document (MedicationRequest entries) | |
| MedType 2 (PMC) | — | rejected (FR-004) |
| `Id` (UUID) | `Bundle.identifier` (urn:uuid) | reverse: Bundle.identifier → `Id` |
| `Dt` | `Composition.date`, `Bundle.timestamp` | no wall-clock time used |
| `Auth` (GLN) / `HcPerson` / `HcOrg` / `Zsr` | Practitioner, PractitionerRole, Organization with GLN and ZSR identifiers | |
| Patient `FName`, `LName`, `BDt`, `Gender`, address, `Lng`, `Ids` | Patient | Gender 1/2 → male/female |
| Patient `Med` (`Meas`, `Rc`, `DLstMen`, `Prem`, `ToG`) | Observations / sections where CH EMED supports them | otherwise report |
| `IdType` 2 (GTIN) / 3 (Pharmacode) / 1 (free text) / 4 (product no.) | `Medication.code` coding (system from CH Term) / `code.text` | IdType 4 not allowed in prescriptions |
| `Pos.D` [morning, noon, evening, night] | Dosage split by `timing.repeat.when` MORN/NOON/EVE/NIGHT | |
| `Pos.TT` (taking times with offsets) | Dosage with `timing.repeat.timeOfDay` | |
| `Pos.CyDu` | `timing.repeat.period/periodUnit` | |
| `Pos.InRes` | `asNeeded[x]` | |
| `Pos.DtFrom` / `DtTo` | `timing.repeat.boundsPeriod` / effective period | date precision preserved |
| `TkgRsn`, `AppInstr`, `Roa`, `Unit`, `AutoMed`, `PrscbBy` | reason, patientInstruction, route, dose unit, self-medication extension, informationSource/requester | |
| `Rep`, `Subs`, `NbPack`, `IndC`, `Rcv` | dispenseRequest (validity/repeats, quantity), substitution, indication, intended receiver | exact elements in mapping spec |
| `Rmk` | Composition annotation / note | |

## R8 — Determinism (Principle V)

- **Decision**: No `UUID.randomUUID()`, no `Instant.now()` in conversion code. Required ids are
  taken from the source (e.g. CHMED `Id` ↔ `Bundle.identifier`) or derived as name-based UUIDs
  (UUID v5) from the source document id plus the element path; callers may inject an
  `IdStrategy` and a `Clock`. JSON output uses HAPI's parser with fixed settings
  (pretty-print off by default, stable element order); CHMED16A JSON uses Jackson with
  property order defined by the specification tables. Determinism is asserted by tests that
  convert each fixture twice and compare bytes.
- **Alternatives**: Random UUIDs (rejected: not reproducible).

## R9 — Build, test and CLI tooling

- **Decision**: Maven multi-module build (Maven 3.9, Maven Wrapper), JUnit 5, AssertJ,
  JSONAssert for structural JSON comparison in addition to byte-equality tests; picocli for the
  CLI; Jackson for CHMED16A JSON; SLF4J API in libraries, Logback only in the CLI.
- **Rationale**: Mainstream, Apache-2.0/EPL/MIT-compatible licences; Maven is the HAPI
  ecosystem default; picocli keeps the CLI thin and testable.
- **Alternatives**: Gradle (equally viable; Maven chosen for convention in the HAPI ecosystem);
  Spring Shell (rejected: framework dependency in the CLI not needed).

## R10 — Performance target (SC-005a/b)

- **Decision**: Measure with a warm validation context (library and the future REST service reuse
  it). CLI cold start (loading packages, ~30–40 s) is reported separately and is not part of
  SC-005a/b. The target is split (2026-10-21): SC-005a conversion < 2 s, SC-005b official
  validation of the output < 15 s, both for 100 medication entries.
- **Rationale**: Loading NPM packages into the validator dominates cold start; conversion itself
  is linear in the number of medications (about 0.02–0.1 s for 100). The HL7 validator costs about
  80 ms per MedicationStatement plus about 0.6 s fixed (about 10 s for 100). Tried and rejected:
  the HL7 `ValidationEngine` (7.7–9.6 s warm, same engine) and validating sub-documents in
  parallel (identical messages, but only about 1.3× faster because the validator scales poorly
  across threads).
- **Alternatives**: Skipping or sampling validation (rejected: Principle I); caching validation
  results (not applicable, every document differs).

## R12 — Original representation PDF (FR-021)

- **Decision**: `OriginalRepresentationRenderer` in `converter-core` renders the PDF from the
  finished CH EMED Bundle (format-agnostic, reused by CHMED23A/CARA later) with Apache PDFBox
  3.0.x (Apache-2.0). Deterministic output: trailer `/ID` derived from the Bundle identifier,
  creation/modification date = `Composition.date`, no producer/time metadata, Standard-14 font
  Helvetica with WinAnsi encoding; characters outside WinAnsi are replaced in the PDF only and
  reported. Byte identity is asserted by tests.
- **Rationale**: CH EMED 6.0.0 requires `section:originalRepresentation` 1..1 with a Binary PDF;
  CHMED16A carries none (decided 2026-10-18).
- **Alternatives**: caller supplies the PDF (rejected by decision: extra burden on every
  caller); OpenPDF (equivalent, PDFBox chosen for Apache-2.0 licence and wide use).

## R13 — Document language and confidentiality (FR-022)

- **Decision**: language from `Patient.Lng`, else `ConversionOptions.defaultLanguage`
  (`de-CH`); confidentiality from `ConversionOptions.confidentiality` (`NORMAL`); reported as
  `DERIVED_VALUE` INFORMATION. CLI options `--language`, `--confidentiality`.
- **Rationale**: both elements are 1..1 in CH EMED; they are administrative metadata, not
  clinical data, so a documented, overridable default does not violate FR-011.

## R11 — Test corpus

- **Decision**: `src/test/resources/fixtures/` per module: official CH EMED 6.0.0 example
  instances (Medication Card, Medication Prescription) from the IG package; all JSON examples of
  `chmed16a/README.md` and `prescription.md` (pinned commit) in `fixtures/chmed16a/official/`;
  a synthetic prescription structured like the eRezept documentation example; synthetic fixtures
  for every mapping rule and every edge case of the spec. All synthetic data uses obviously
  fictitious persons and test GLNs.
- **Rationale**: Constitution III and IV.

## Sources

- IG eMediplan specification repository: https://github.com/ig-emediplan/specification
- CHMED16A: https://github.com/ig-emediplan/specification/tree/main/chmed16a
- CHMED23A: https://github.com/ig-emediplan/specification/tree/main/chmed23a
- IG eMediplan strategy 2026: https://www.emediplan.ch/aktuelles/strategische-ausrichtung-der-ig-emediplan-2026
- CH EMED IG: https://fhir.ch/ig/ch-emed/index.html and https://github.com/hl7ch/ch-emed
- CH EMED versions: https://fhir.ch/ig/ch-emed/package-list.json
- eRezept Schweiz developer documentation: https://developers.e-rezept.ch/erezept/prereq.html
- CHMED20AF (archived): https://github.com/ahdis/chmed
- HAPI FHIR releases: https://github.com/hapifhir/hapi-fhir/releases
