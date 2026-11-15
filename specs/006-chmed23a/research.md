# Research: CHMED23A ↔ CH EMED

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-12

The facts below were checked against `ig-emediplan/specification` at commit
`0b579aa6f3aff10a017498db57048c6504091653` (R1) and against the vendored package
`ch.fhir.ig.ch-emed#6.0.0`. Decisions of features 001 and 003 (`specs/001-…/research.md`,
`specs/003-qr-erezept/research.md`) stay in force for CHMED16A and are reused here where named.

## R1 — Pinned specification and licence

**Decision**: CHMED23A is pinned to the same commit as CHMED16A, `0b579aa6` (2026-05-22, "Mark
CHMED23A as deprecated"), which is also the head of `main` on 2026-11-12. The pinned files are:
- `chmed23a/README.md` (object model);
- `chmed23a/posology.md`;
- `chmed23a/terminology.md`;
- `chmed23a/conventions.md`;
- `chmed23a/examples.md`;
- `chmed23a/client-implementations/dotnet/` (reference implementation, validation rules);
- `chtransmissionformat/README.md` (ReleaseYear 23).

`pinnedSpecification()` returns `CHMED23A (ig-emediplan/specification@0b579aa6)`.
`docs/specifications.md` gets a CHMED23A row.

The repository is under the MIT licence (© 2024 ig-emediplan). The examples may therefore be copied
as fixtures, with the licence notice kept in the fixture README. They are synthetic (invented
patients, `fake-e-mail.ch` addresses), so constitution IV is met. `docs/third-party-licenses.md`
names the fixtures.

**Rationale**: The specification is frozen: IG eMediplan has stopped developing it. One commit for
both eMediplan formats keeps `docs/specifications.md` simple. The commit already carries the
deprecation note that FR-015 refers to.

**Alternatives considered**:
- An older commit from before the deprecation. Rejected: the content of `chmed23a/` did not change
  after the posology text revision of 2026-04-08, and the deprecation note belongs in the record.

## R2 — Module structure

**Decision**: A new Maven module `format-chmed23a` (package `ch.emed.converter.chmed23a`).
- It depends on `converter-core` and `converter-qr`. It is registered through
  `META-INF/services/…FormatModule` like CHMED16A.
- `converter-cli` and `converter-rest` add it as a dependency.

The CH EMED side that both eMediplan modules need moves from `format-chmed16a` to `converter-core`,
package `ch.emed.converter.core.chemed`. These classes are free of the CHMED16A model:
- `ChEmedDocumentReader` (with `DocumentType` and `ChEmedDocument`);
- `Profiles` (profile URLs, code systems);
- `Labels` and `Narratives` (localized titles and narratives);
- the CDTYP 9 unit table of `DosageToFhirMapper.UNITS`;
- the `@MappingRule` annotation.

The move does not change behaviour. The existing CHMED16A tests guard it, and its output stays
byte-identical.

**Rationale**:
- Constitution V asks for separate format modules around the hub. Today no format module depends
  on another in main scope; `format-ch-emed-epr` uses CHMED16A only in tests.
- A dependency of `format-chmed23a` on `format-chmed16a` would tie the withdrawn format to the
  maintained one, and it would put CHMED16A internals on the API surface.
- The moved classes describe CH EMED, the hub, so `converter-core` is their natural home. It
  already holds `ChEmedJson` and `OriginalRepresentationRenderer`.

**Alternatives considered**:
- One module `format-emediplan` for both versions. Rejected: it mixes two object models with
  different naming, posology and rules, and it makes CHMED23A harder to remove later.
- Copying the helpers into the new module. Rejected: two copies of the CH EMED reading logic would
  drift apart.
- Depending on `format-chmed16a`. Rejected for the reasons above.

## R3 — Input forms and their detection

**Decision**: `InputFormDetector` of the new module checks the input in this order (FR-005):

| Input starts with | Form | Then |
|-------------------|------|------|
| PNG or JPEG signature | `qr-image` | one QR code (feature 003 rules M-QR-01..03); its text is read as a payload |
| GIF, BMP, TIFF, WebP signature | – | `IMAGE_UNREADABLE` |
| `CHMED23` (case-insensitive) and one token | `qr-payload` | ChTransmissionFormat ReleaseYear 23 (R4) |
| `CHMED23` and several whitespace-separated tokens, each with chunk metadata | `qr-payload` (chunked) | chunks joined (R5) |
| `CHMED16` | – | `UNSUPPORTED_VERSION`, "CHMED16A payload; use source chmed16a" |
| `{` with `resourceType` | – | `PARSE_ERROR`, "Input is a FHIR resource; use source ch-emed" |
| `{` with the CHMED16A key `Patient` or `Medicaments` and no `patient` | – | `UNSUPPORTED_VERSION`, "CHMED16A document; use source chmed16a" |
| `{` | `json` | plain CHMED23A JSON |
| anything else | – | `PARSE_ERROR` |

Plain JSON carries no version marker (README, "Introduction"). The declared source `chmed23a`
supplies the version.

The CHMED16A module keeps refusing CHMED23A input with `UNSUPPORTED_VERSION` (rules M-IN-04,
M-QR-04). Its message now names the source `chmed23a`; this is a message change, recorded in the
changelog.

**Rationale**:
- The order matches CHMED16A (rule M-IN-07). Detection by content is required by FR-005.
- A wrong declared source is refused with a hint rather than silently redirected. The caller keeps
  control over which conversion runs, and the report names the right source.

**Alternatives considered**:
- Automatic dispatch of any `CHMED…` payload to the matching module, as the constitution's
  description of the eMediplan QR code suggests. Rejected for this feature: the source format is
  part of the conversion pair in all three interfaces. Each module still recognises the other
  version, and the hint makes the choice explicit. This is noted in the Constitution Check.

## R4 — ChTransmissionFormat ReleaseYear 23

**Decision**: These rules follow `chtransmissionformat/README.md`.

**Reading**:
- The input must match `^CHMED23([A-Z]+)(\.[1-9][0-9]*/[1-9][0-9]*)?\.(.*)$`, checked
  case-insensitively.
- `SubVersion` must be `A`. Another letter gives `UNSUPPORTED_VERSION`, naming the subversion.
- `Data` is Base64-decoded (standard alphabet) and then GZip-decompressed.
- The decompressed size is limited by the option `maxDecodedSize`, as for CHMED16A (feature 003).
- The result must be UTF-8 JSON.

**Writing**:
- The payload is `CHMED23A.` + Base64 (standard alphabet, with padding, no line breaks) of the
  GZip of exactly the bytes of the `document` output.
- The GZip header carries no timestamp or file name, as `gzip -n` does, so the output is
  deterministic (FR-012).
- The output is ASCII.

The specification requires that payloads can be round-tripped. A test decodes every written payload
with the rules above and compares the result with the `document` output.

**Finding**: The example payload in `chtransmissionformat/README.md` decodes to JSON of an earlier
draft: PascalCase `Meds`, `Ps`, `PO`, and no patient. It is used as a transport test only:
- decoding works;
- conversion is refused with `CHMED_INVALID_FIELD` at `/patient`.

## R5 — Chunked payloads (read only)

**Decision**: Chunked input is accepted as text only (clarification 2026-11-12).

**Form of the input**:
- All chunks come in one input, separated by whitespace (line breaks or spaces). Base64 contains no
  whitespace, so the split is unambiguous.
- The chunks may come in any order.

**Rules**, each failure reported as `CHUNK_INVALID` (ERROR) naming the problem and the index, never
the data:
- Every token must carry chunk metadata `.{Index}/{Total}`.
- All tokens must have the same subversion and the same `Total`, which must be greater than 1.
- Each index from 1 to `Total` must appear exactly once: none missing, none duplicated, none out of
  range.
- The `Data` parts are concatenated in index order and then read as in R4.
- A single token with metadata `1/1` breaks the rule "MUST be greater than 1": `CHUNK_INVALID`.

The format has no correlation id, so the converter cannot tell whether chunks belong together. It
accepts exactly one complete set per input. The caller is responsible for correlating the chunks,
as the specification says.

Chunks are never written (clarification 2026-11-12). Splitting and joining are kept in one class,
`TransmissionFormat`, so that a later feature can add writing.

**Alternatives considered**:
- Chunks as several QR images, or several codes in one image. Rejected by clarification
  2026-11-12; several codes in one image remain `QR_MULTIPLE` (feature 003, M-QR-02).
- A new reason code per problem. Rejected: one code with a precise message is enough for callers.

## R6 — QR image

**Decision**: The feature 003 rules are reused unchanged through `converter-qr`.
- **Reading**: an image of at most `maxDecodedSize` bytes and 40 megapixels holding exactly one QR
  code (M-QR-01..03).
- **Writing**: `QrImageWriter` with the parameters of M-QR-11 (byte mode, error correction M,
  smallest version, quiet zone 4, 8 px per module, 40 mm print width).
- **Capacity**: a payload above 2331 bytes (version 40-M) gives `QR_CAPACITY_EXCEEDED` naming the
  limit. `qr-payload` stays available (clarification 2026-11-12).

A prescription may be written as a QR image (clarification 2026-11-12), so M-QR-13 does not apply
to CHMED23A.

**Rationale**: The ChTransmissionFormat states no symbol parameters for ReleaseYear 23. The spec
assumption keeps the CHMED16A parameters, provisionally, until the external questions of feature 003
are answered.

**Finding**: As compact JSON, the four examples of `examples.md` give payloads of 1021 to 1557
bytes (Example 2, 7 medicaments: 1557). All fit into one code. The capacity case is therefore a
synthetic large plan; the test asserts the code, not the size.

## R7 — Conformance without a JSON schema

**Decision**: `Chmed23aValidator` checks input and output against rules taken from the pinned
specification and listed with rule ids in `docs/mapping/chmed23a-ch-emed.md` (section
"Conformance"). The rules come from:
1. **Object model** (README tables): type, usage `R`/`O`/`-` per medType (MP and Rx columns),
   conditional usage (`hcPerson`/`hcOrg` if `auth` = 1, `sId` if `type` = 2, `hcOrg.gln` if the
   person has none, ZSR only once), value sets (`medType` 1/3, `auth`, `gender`, `idType`, risk
   categories), formats (date-time with offset, date, `toG` `{week}-{day}`).
2. **Posology** (`posology.md`):
   - which object may contain which (the two overview tables);
   - the sections "Limitations and validations", e.g. `ds` has exactly 4 values ≥ 0; `doms` 1–28
     and unique; `aTo` > `aFrom`; no nested `Sequence`; `dtTo` ≥ `dtFrom`;
   - the value sets for days of week, day segments, relative to meal and time units.
3. **Terminology** (`terminology.md`):
   - units (CDTYP 9);
   - routes (CDTYP 61);
   - methods (CDTYP 62);
   - risks.

   The tables are vendored as a resource of the module, generated once from the pinned file and
   checked by a test against it. Units are compared case-insensitively, as for CHMED16A.
4. **.NET reference implementation**: `Emediplan.ChMed23A.Tests.Unit/Validation/*` is used as a
   cross-check. Every validation test there has a counterpart fixture or is named as not
   applicable in the mapping specification.

**Usage `-`** ("can be set, but won't be used"), e.g. `nbPack` in a plan or `mData` in a
prescription: INFORMATION `UNMAPPABLE_ELEMENT`, "not used for this document type". The input is
not refused.

**Unknown properties**: INFORMATION `UNKNOWN_FIELD_IGNORED`, as for CHMED16A (M-IN-06).

**Errors**: `CHMED_INVALID_FIELD` with the JSON path, never the value (FR-007, FR-016).

**Codes outside the terminology** (`unit`, `roa`, `moa`, risk ids): WARNING `CHMED_INVALID_FIELD`
with the path, not an error. The .NET reference examples themselves use units outside CDTYP 9
(finding F9), and the .NET validator does not check codes. The mapping then treats the code as
unmapped: a unit gives a text dosage with clinical loss (R9), so strict mode still fails.
Codes the converter writes always come from the tables.

**Output**: Output is validated with the same rules before it is returned. A violation means a bug
in the mapping: the conversion fails with `CHMED_INVALID_FIELD` in phase `VALIDATE_OUTPUT`.

**Rationale**: Constitution I requires conformance to the published specification "including any
schema provided by it". There is no schema, so the specification text is the artefact. The .NET
implementation is the closest official executable form. It cannot be run here (Java only, local
only), but its tests can be traced.

**Alternatives considered**:
- Writing a JSON schema of our own. Rejected: a locally invented artefact would look official. The
  conditional rules (per medType, per object type) would also need a large schema that is harder to
  review than the rule list.
- Running the .NET validator in tests. Rejected because of constitution IV (local only, Java
  platform).

## R8 — Document types and the document level

**Decision**: The document types are mapped as follows.

| CHMED23A | CH EMED 6.0.0 |
|----------|---------------|
| `medType` 1 (MedicationPlan) | Medication Card (`ch-emed-document-medicationcard`) |
| `medType` 3 (Prescription) | Medication Prescription (`ch-emed-document-medicationprescription`) |
| `medType` 2 (PolymedicationCheck, deprecated) and any other value | `UNSUPPORTED_DOCUMENT_TYPE`, message names MP (1) and Rx (3) |

**Document id**:
- An `id` that is a UUID becomes `urn:uuid:<id>` in `Bundle.identifier` and
  `Composition.identifier`.
- Any other `id` is optional free text set by the creator. It cannot be kept, because CH EMED
  requires a UUID: the identifier is derived with the `IdStrategy` from the content (UUID v5), the
  report says `DERIVED_VALUE`, and the original `id` is reported as `UNMAPPABLE_ELEMENT` (no
  clinical loss).
- A missing `id` (all four examples) gets the derived identifier with `DERIVED_VALUE`.

**Other document fields**:
- `dt`: as in CHMED16A (M-DOC-05), offset kept.
- `rmk`: the annotation section (M-DOC-06).
- `lng`: `Composition.language`.
- Confidentiality: from the option.
- Original representation: rendered as in CHMED16A (M-DOC-10), through the moved
  `OriginalRepresentationRenderer` path.

**Author**: Unlike CHMED16A, CHMED23A carries the author's details in the document.
- `auth` 1 → PractitionerRole with:
  - Practitioner from `hcPerson`: GLN, names, ZSR;
  - Organization from `hcOrg`: name, address, GLN, ZSR.
  The option `author` is not needed. If it is given anyway, the document wins, and INFORMATION
  `DERIVED_VALUE` says that the option was not used.
- `auth` 2 → `Composition.author` is the Patient. This is allowed for plans only; a prescription
  with `auth` 2 fails conformance.
- Reverse: Patient author → `auth` 2. PractitionerRole → `auth` 1, `hcPerson`, `hcOrg`. Missing
  required parts (e.g. the street of the organization) come from the options `author` and
  `organization` (feature 001); otherwise `MISSING_REQUIRED_DATA` at the path (FR-010).

**Rationale**: The structure follows CHMED16A (rules M-DOC-*, M-AUT-*) wherever the two formats
agree, so the mapping specifications stay comparable.

## R9 — Posology

**Decision**: Each `PosologyDetail` object is mapped as follows. "Text" means: the base entry
`Dosage.text` gets a generated, localized description of the full posology, and the report has
WARNING `DOSAGE_FREE_TEXT_FALLBACK` with clinical loss (as M-DOS-07).

| CHMED23A | CH EMED `Dosage` | Exact? |
|----------|------------------|--------|
| Daily (`ds` m/n/e/night) | `when` MORN/NOON/EVE/NIGHT with `doseQuantity`; several → base entry + split entries (as M-DOS-02) | yes |
| FreeText | `Dosage.text` = `text` | yes, no loss (the source is text) |
| Single + DosageOnly | `timing.repeat.count` 1, dose | yes |
| Single + Times (one time) | `count` 1, `timeOfDay` | yes |
| Single + DaySegments (one segment) | `count` 1, `when` | yes |
| Single with several times or segments | text | no |
| Cyclic + DosageOnly | `frequency` = `tdpc`, `period` = `cyDu`, `periodUnit` from `cyDuU` (s, min, h, d, wk, mo, a) | yes |
| Cyclic 1 day + Times, equal doses | `timeOfDay` list, one dose (as M-DOS-06) | yes |
| Cyclic 1 day + Times, different doses | text (split entries require `when`, `ch-emed-dosage-split`) | no |
| Cyclic 1 day + DaySegments | as Daily | yes |
| Cyclic 1 week + WeekDays + DosageOnly / Times (equal doses) / DaySegments | `dayOfWeek` + inner timing; split entries repeat `dayOfWeek` | yes |
| Cyclic ≠ 1 day with Times or DaySegments; WeekDays with `cyDu` > 1 | text (FHIR `period` plus `dayOfWeek` or `when` is ambiguous) | no |
| Cyclic + DaysOfMonth | text (FHIR R4 has no day of month) | no |
| Cyclic + Interval (minimal interval) | text (FHIR has no minimal interval) | no |
| Sequence (PosologySequence, Pause) | text (CH EMED `sequence` means split dosage, not phases) | no |

**Dosage objects**:
- DosageSimple → `doseQuantity`.
- DosageRange → `doseRange`.
- DosageFromTo (rate change, e.g. infusion) → text.

**Posology attributes**:
- `dtFrom`/`dtTo` → `boundsPeriod`, precision kept, `dtTo` inclusive (M-DOS-01).
- `inRes` → `asNeededBoolean`.
- `appInstr` → `patientInstruction`.
- `roa` (CDTYP 61, the EDQM route codes themselves) → `route` EDQM with the same code, if it is in
  the CH Term route value set; otherwise WARNING with clinical loss (route binding required).
- `moa` (CDTYP 62, HCI codes "based on" EDQM, e.g. `19` "schlucken") → `method` EDQM through a
  table from the German names. The table holds only unambiguous rows and is subject to domain
  review. Unmapped → WARNING `UNMAPPABLE_ELEMENT` with clinical loss. `method.coding` is required
  by the profile, so the code cannot be carried as text.
- `unit` (CDTYP 9) → the moved CHMED16A unit table, compared case-insensitively. It is extended for
  further CDTYP 9 codes: UCUM units, `UI` → `[iU]` and `gtt` → SNOMED CT `732994000` "Drop".
  `Appl`, used in Example 1, has no entry (domain review). An unmapped unit gives text, as M-DOS-08.

**`relMeal`**:
- 1/2/3 combined with day segments or no time → `when` ACM/ACD/ACV, CM/CD/CV, PCM/PCD/PCV, or
  AC/C/PC.
- With `timeOfDay`, or values 4–7 (empty stomach, high-fat, light, main meal) → the localized label
  in `additionalInstruction.text`, with no code invented. WARNING `UNMAPPABLE_ELEMENT` without
  clinical loss: the text keeps the meaning.

**Several posologies per medicament**: one MedicationStatement per posology, as CHMED16A does
(M-MED-08). For prescriptions see R10.

**Reverse direction**: The rows marked "yes" are inverted. Everything else goes as follows:
- A CH EMED dosage that has only `text` becomes FreeText. This is lossless, so it is not reported.
- A structure the reverse table does not cover becomes FreeText with the generated description,
  plus WARNING `DOSAGE_FREE_TEXT_FALLBACK`.

A round trip of a "text" posology therefore returns FreeText. This difference is documented in the
capability gaps and asserted by the round-trip tests (FR-011).

**Rationale**:
- Constitution II forbids silent loss and clinical defaults. Only exact equivalents are structured;
  everything else keeps its meaning as text and is reported.
- The `ch-emed-dosage-split` profile requires `when`, so different doses at clock times cannot be
  split.

**Alternatives considered**:
- `Dosage.sequence` for posology phases. Rejected: in CH EMED, `sequence` numbers the entries of a
  split dosage.
- Several MedicationStatements, one per sequence phase, with computed periods. Rejected: the dates
  would be derived values that the source does not state. A "repeat until `dtTo`" sequence cannot be
  unrolled without inventing an end.

## R10 — Prescription details

**Decision**: Each prescription field is mapped as follows.

| CHMED23A | CH EMED MedicationRequest |
|----------|---------------------------|
| each Medicament × posology | one MedicationRequest (`ch-emed-medicationrequest`), intent `order`, `authoredOn` = `dt`, `requester` = author role, contained Medication (as M-RX-03) |
| Medicament without posology | one MedicationRequest with dosage text "dosage not given", INFORMATION `DERIVED_VALUE` (as M-RX-04) |
| `reps` Number `v` | `dispenseRequest.numberOfRepeatsAllowed` = `v` (refills after the first dispense: same meaning) |
| `reps` Duration `d`,`u` (day, week, month, year) | `dispenseRequest.validityPeriod`: start = date of `dt`, end = start + `d` `u` − 1 day (inclusive, as M-RX-06) |
| `reps` Duration in seconds, minutes, hours | WARNING `UNMAPPABLE_ELEMENT` with clinical loss |
| `reps` NumberAndDuration | both of the above |
| no `reps` | none; the default "Number 1" is not materialised (as M-RX-05/-06) |
| `nbPack` | `dispenseRequest.quantity` (as M-RX-05) |
| `isNotSub` true | `substitution.allowedCodeableConcept` `N` (as M-RX-07) |
| `sic` | WARNING `UNMAPPABLE_ELEMENT` with clinical loss; CH EMED has no element (domain review) |
| `rec` (GLN) | `dispenseRequest.performer` as logical reference (as M-RX-09) |

**Several posologies per prescribed medicament**: Example 4 has a medicament with four posologies,
a tapering scheme by period. Each becomes its own MedicationRequest, and the dispense fields are
copied to each. The profile allows one base dosage per request
(`dosageInstruction` base slice 1..1).

In reverse, every MedicationRequest becomes one Medicament; requests are not merged.
- The round trip therefore gives four medicaments with one posology each.
- This difference is documented and asserted, with INFORMATION `DERIVED_VALUE` naming the split.

This is a domain-review item: a reviewer may prefer one request with a text dosage.

**Rationale**:
- One request per posology keeps every dosage structured and matches how the card side treats
  several posologies.
- Merging requests in reverse would rely on comparing medications and dispense data, a heuristic
  that could join two prescriptions the prescriber meant to keep apart.

## R11 — Patient and medical data

**Decision**: Each patient field is mapped as follows.

| CHMED23A | CH EMED Patient |
|----------|-----------------|
| `fName`, `lName`, `bdt` | `name.given`, `name.family`, `birthDate` |
| `gender` 1/2/3 | `male`/`female`/`other` |
| `street`, `zip`, `city`, `country` | `address`; absent `country` stays absent (the spec assumes CH, the converter does not invent it) |
| `phones`, `emails` | `telecom` |
| `lng` | `communication.language` and `Composition.language` (as M-DOC-08, M-PAT-06) |
| `ids` type 1 | `identifier` VeKa `urn:oid:2.16.756.5.30.1.123.100.1.1.1` |
| `ids` type 2 + `sId` | `identifier.system` = `sId` (`urn:oid:` added to a bare OID), `value` = `val` |
| `mData` (weight, height, risks, menstruation, prematurity, gestation) | none: the Medication Card has no place for it; WARNING `UNMAPPABLE_ELEMENT` with clinical loss per element (as M-MDT-01) |
| `exts` (all levels) | WARNING `UNMAPPABLE_ELEMENT` per extension, path only (no name or value) |

**Reverse direction**:
- `lng` is required for a plan. It comes from the Composition language or the patient's
  communication, otherwise from the option `defaultLanguage`, with `DERIVED_VALUE`.
- `gender` `unknown` or absent → `MISSING_REQUIRED_DATA`. No placeholder is set.
- At least one identifier is required: VeKa → type 1, any other system → type 2 with `sId`.

## R12 — Interfaces, capabilities and the deprecated mark

**Decision**: The format is offered through all three interfaces and marked as deprecated.

**Format and pairs**:
- Format name `chmed23a`, `FormatId.CHMED23A`.
- Pairs `chmed23a → ch-emed` and `ch-emed → chmed23a`.
- Direct pairs with `chmed16a` or `ch-emed-epr` are refused with `UNSUPPORTED_CONVERSION` by the
  existing hub rule, which names the path through CH EMED (FR-004).

**Forms**:
- Input forms: `json`, `qr-payload`, `qr-image`.
- Output forms: `document`, `qr-payload`, `qr-image`.
- Document types: `MP`, `Rx`.

**Deprecated mark** (FR-015):
- `FormatModule.deprecated()` (default `false`) and a new component `FormatInfo.deprecated`. The
  existing `FormatInfo` constructors stay and set `false`.
- CLI `formats`: the line ends in `; deprecated by IG eMediplan`.
- REST capabilities: the format entry gets `deprecated: true`. The property is optional and absent
  for other formats, so existing clients are not affected.
- OpenAPI: version `1.2.1` → `1.3.0`, with the new property, the new format name in examples, and
  the output-form text "qr-* only for targets chmed16a and chmed23a".
- Documentation: `README.md`, `docs/architecture.md` and the mapping specification say that IG
  eMediplan has withdrawn CHMED23A (strategy 2025-12-02).

**Rationale**: The mark is information for callers, not a behaviour change, so conversions are not
refused and no report entry is added.

**Alternatives considered**:
- An INFORMATION entry in every report. Rejected: it adds the same line to every conversion without
  telling the caller anything new, and the spec asks for documentation and capabilities only.

## R13 — Test corpus

**Decision**: The test corpus consists of the following parts.

1. **Official documents**: the 4 JSON documents of `examples.md`, copied verbatim to
   `format-chmed23a/src/test/resources/fixtures/chmed23a/official/Example{1..4}.json`. A README
   gives the commit and the MIT notice. They cover:
   - 3 medication plans, one of them by the patient (`auth` 2);
   - 1 prescription with repetitions and tapering.
   All are strict JSON despite the `json5` fence.
2. **Official posology fragments**: every example block of `posology.md` (one per object type)
   wrapped in a synthetic carrier document, `fixtures/chmed23a/posology/`.
3. **.NET examples**: The medication and posology examples are C# object initialisers, not JSON.
   - Those that add cases beyond 1 and 2 are transcribed to JSON. Each fixture names its source
     test, e.g. `WeeklyPosologyExamples.Weekly_MondayWednesdayFriday`.
   - Together, the fixtures must cover every row of the two compatibility tables in `posology.md`.
   - The validation tests are traced as described in R7.
4. **Synthetic cases**: one per rule and edge case:
   - chunked payloads cut from the official examples (2, 3 and 4 chunks, shuffled, missing,
     duplicated, `1/1`);
   - QR images written by our writer;
   - a CHMED16A payload and document declared as `chmed23a`;
   - `medType` 2;
   - non-conforming documents;
   - umlauts and French and Italian text;
   - a document too large for one QR code.
5. **CH EMED side**: the CH EMED documents produced from 1–4 (expected outputs) and the existing
   CH EMED Medication Card and Prescription fixtures of CHMED16A, for the reverse direction.

**Findings on the official examples** (to be asserted, not "fixed"):
- **F1**: Example 2 has FreeText with non-ASCII text (`take ½ pill`). It must be kept unchanged
  in both directions and in the payload.
- **F7**: The examples use identifiers that do not follow the usual formats:
  - GLNs with 11 or 12 digits (`123123123123`, `55255255255`);
  - `sId` `9.99.999.9.99`, which is not a valid OID (OIDs start with 0, 1 or 2);
  - ZSR `XX.1254`;
  - dates with 7 fractional digits (`…09:14:36.0000000+01:00`).

  CHMED23A does not constrain these formats, so the validator accepts them. How CH EMED
  validation treats them is settled in the first fixture test (T031). Where CH EMED rejects a
  value, the mapping must not repair it: it reports the element and, where the profile allows,
  leaves it out.
- **F2**: The plans carry `nbPack` and `prscbBy`. The prescription carries `autoMed` and `mData`.
  Both are usage `-` for their document type → INFORMATION (R7).
- **F3**: No example has `id` → derived document identifier (R8).
- **F4**: Example 4 has one medicament with four posologies → four MedicationRequests (R10).
- **F5**: Examples 3 and 4 use only patient id type 2 (local PID), with `sId`.
  - CHMED16A has no system for other ids, so it needs a VeKa number or the option
    `patientIdentifier` (M-PAT-04).
  - In CHMED23A, `sId` supplies the system, so a local PID fills the required identifier of the CH
    Core patient (R11), and the option is not needed.
  - The option is used only if a document has no usable id at all.
- **F6**: The payload example of the transmission format is a pre-release draft (R4).
- **F8**: Three examples of `posology.md` (Single, Cyclic, Sequence) use draft property names
  (`d` instead of `do`, `td` instead of `tdo`). They are kept verbatim as fixtures and are refused
  as non-conforming. The object tables and the .NET examples cover the same cases with the final
  names.
- **F9**: Most .NET examples use units outside CDTYP 9 (`TABL`, `Applikation`). They are reported
  as WARNING (R7) rather than refused.

## R14 — Performance

**Decision**: SC-005 is a synthetic plan with 20 medicaments of mixed posologies, built from the
official fragments. It is converted to CH EMED and back, including validation, within the limits of
feature 001 (SC-005b, < 30 s warm). It is measured in `PerformanceTest` of the new module on
4 vCPUs (`tools/remote-build.sh load`).

**Rationale**: The same machine and limits as features 001 and 005 keep the measurements
comparable.

## R15 — Documentation and changelog

**Decision**: The documentation and changelog are updated as follows.
- **New mapping specification** `docs/mapping/chmed23a-ch-emed.md`. It holds:
  - conformance rules `C23-*`;
  - input and QR rules `M23-IN-*` and `M23-QR-*`;
  - document, patient, author, medication, dosage and prescription rules `M23-DOC-*`,
    `M23-PAT-*`, `M23-AUT-*`, `M23-MED-*`, `M23-DOS-*`, `M23-RX-*`;
  - reverse rules `M23-REV-*`;
  - capability gaps.
  A traceability test checks that every rule id has an `@MappingRule` and a fixture test, as for
  CHMED16A.
- **`docs/specifications.md`**: CHMED23A row (R1).
- **`docs/architecture.md`**: new module and the moved CH EMED helpers.
- **`docs/operations/rest-service.md`**: SC-005 measurement.
- **`CHANGELOG.md`**, section `[Unreleased]`:
  - the new format;
  - `CHUNK_INVALID`;
  - `FormatInfo.deprecated`;
  - OpenAPI 1.3.0;
  - the CHMED16A message change (R3).
