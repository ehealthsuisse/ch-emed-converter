# Data Model: CHMED23A ↔ CH EMED

Phase 1 of [plan.md](plan.md). Decisions R1–R15 are in [research.md](research.md). The data models
of features 001 and 003 stay in force for CH EMED, the report and the options.

## 1. Format, forms and document types

| Item | Value |
|------|-------|
| Format | `chmed23a` (`FormatId.CHMED23A`), deprecated (R12) |
| Pinned specification | `CHMED23A (ig-emediplan/specification@0b579aa6)` |
| Pairs | `chmed23a → ch-emed`, `ch-emed → chmed23a` only |
| Input forms | `json`, `qr-payload` (including chunked, text only), `qr-image` |
| Output forms | `document` (JSON), `qr-payload`, `qr-image` |
| Document types | `MP` (`medType` 1) ↔ Medication Card; `Rx` (`medType` 3) ↔ Medication Prescription |
| Refused | `medType` 2 and any other value: `UNSUPPORTED_DOCUMENT_TYPE` naming MP and Rx |

CH EMED documents of other types (treatment plan, dispense, advice, list) are refused for target
`chmed23a` with `UNSUPPORTED_DOCUMENT_TYPE`, naming the two supported types.

## 2. CHMED23A object model (`format-chmed23a/model`)

The model consists of immutable records, one per object of the pinned README and posology.md.
Property names are those of the JSON. "Usage" gives MP / Rx (R required, O optional, - not used,
`x-N` list with minimum x).

### 2.1 Document

| Record | Fields (usage MP / Rx) | Rules (C23-*) |
|--------|------------------------|---------------|
| `Medication` | `patient` R/R; `hcPerson`, `hcOrg` R if `auth`=1; `meds` 0-N/1-N; `exts`; `medType` R; `id` O; `auth` R; `rec` -/O; `dt` R; `rmk` O | `medType` ∈ {1, 3}; `auth` ∈ {1, 2}; `auth` 2 only for MP; `dt` date-time with offset |
| `Patient` | `fName`, `lName`, `bdt`, `gender` R; `street`, `zip`, `city`, `country` O; `lng` R/-; `ids` 1-N; `exts`; `mData` O/-; `phones`, `emails` 0-N | `bdt` date; `gender` ∈ {1, 2, 3}; `country` ISO 3166 alpha-2; `lng` ISO 639-1, either case |
| `PatientId` | `type` R; `sId` R if `type`=2, - if 1; `val` R | `type` ∈ {1, 2} |
| `MedicalData` (MP) | `dLstMen`, `prem`, `toG`, `rcs`, `w`, `h`, `exts` | `toG` `{week}-{day}`; `dLstMen` date |
| `RiskCategory` (MP) | `id` R; `rIds` 0-N | `id` 1–7; `rIds` in the risk table of terminology.md |
| `Extension` | `nm` R; `val` O; `schema` R; `exts` 0-N | – |
| `HealthcarePerson` | `gln` O/R; `fName`, `lName` R; `zsr` -/O | ZSR at most once in person and organization |
| `HealthcareOrganization` | `gln` R if the person has no GLN / -; `name`, `street`, `zip`, `city` R; `country` O; `zsr` -/O | as above |

### 2.2 Medicament and posology

| Record | Fields (usage MP / Rx) | Rules |
|--------|------------------------|-------|
| `Medicament` | `id` R; `idType` R; `pos` 0-N; `rsn` O; `autoMed` R/-; `prscbBy` O/-; `reps` -/O; `isNotSub` O; `sic` -/O; `nbPack` -/O; `exts` | `idType` ∈ {1, 2, 3, 4}, 4 not for Rx |
| `Posology` | `dtFrom`, `dtTo` O; `inRes` O; `po` R; `relMeal` O; `unit` R/O; `appInstr`, `roa`, `moa` O | `dtTo` ≥ `dtFrom`; `relMeal` 1–7; `unit` CDTYP 9; `roa` CDTYP 61; `moa` CDTYP 62 |
| `Repetition` (sealed, Rx) | `Number(v)` t=1; `Duration(d, u)` t=2; `NumberAndDuration(v, d, u)` t=3 | `v` ≥ 0; `d` > 0; `u` time unit 1–7 |

### 2.3 Posology objects

| Sealed type | Variants (`t`) and fields |
|-------------|---------------------------|
| `PosologyDetail` | `Daily` 1 (`ds`[4]); `FreeText` 2 (`text`); `Single` 3 (`tdo`); `Cyclic` 4 (`cyDu`, `cyDuU`, `tdo`, `tdpc`); `Sequence` 5 (`sos`) |
| `TimedDosage` | `DosageOnly` 1 (`do`); `Times` 2 (`ts`); `DaySegments` 3 (`ss`); `WeekDays` 4 (`wds`, `tdo`); `DaysOfMonth` 5 (`doms`, `tdo`); `Interval` 6 (`do`, `miDu`, `miDuU`) |
| `DosageObject` | `DosageSimple` 1 (`a`); `DosageFromTo` 2 (`aFrom`, `aTo`, `du`, `duU`); `DosageRange` 3 (`aMin`, `aMax`) |
| `SequenceObject` | `PosologySequence` 1 (`du`, `duU`, `po`); `Pause` 2 (`du`, `duU`) |
| Application (no `t`) | `ApplicationAtTime` (`dt` hh:mm[:ss], `do`); `ApplicationInSegment` (`s` 1–4, `do`) |

**Nesting rules** (C23, the overview tables of posology.md):
- `Single` takes DosageOnly, Times or DaySegments.
- `Cyclic` takes DosageOnly, Times, DaySegments or Interval; WeekDays only with `cyDuU` week;
  DaysOfMonth only with `cyDuU` month.
- WeekDays and DaysOfMonth take DosageOnly, Times or DaySegments.
- `PosologySequence.po` must not be a `Sequence`.

**Value rules**:
- `ds`: exactly 4 values, each ≥ 0.
- `a` > 0; `aFrom` ≥ 0 and `aTo` > `aFrom`; `aMin` > 0 and `aMax` > `aMin`.
- `cyDu`, `tdpc`, `miDu`, `du` > 0.
- `wds` 1–7, unique, at least one.
- `doms` 1–28, unique, at least one.
- `sos` has at least one element.
- `dt` lies between 00:00:00 and 23:59:59.

Integers given with a decimal part are rounded (conventions.md, "Value types") and reported as
INFORMATION `DERIVED_VALUE`.

## 3. Transmission format (`io/TransmissionFormat`)

| Item | Rule |
|------|------|
| Payload | `CHMED23` + subversion + `.` + Base64(GZip(JSON)); read case-insensitively, written as `CHMED23A.` |
| Subversion | `A` only; otherwise `UNSUPPORTED_VERSION` |
| Chunk | `CHMED23A.{i}/{n}.{data}`, 1 ≤ i ≤ n, n > 1; joined in index order, then read as a payload |
| Limit | decompressed size ≤ `maxDecodedSize` (feature 003) |
| GZip | no timestamp, no file name (deterministic) |
| QR image | feature 003 parameters; payload ≤ 2331 bytes, otherwise `QR_CAPACITY_EXCEEDED` |

## 4. Mapping overview

The mapping specification `docs/mapping/chmed23a-ch-emed.md` holds the rule tables. Their content
is fixed in the research:

| Area | Rule ids | Research |
|------|----------|----------|
| Conformance | `C23-OBJ-*`, `C23-POS-*`, `C23-TERM-*` | R7 |
| Input forms, payload, chunks, image | `M23-IN-*`, `M23-QR-*` | R3–R6 |
| Document, author | `M23-DOC-*`, `M23-AUT-*` | R8 |
| Patient, medical data, extensions | `M23-PAT-*`, `M23-MDT-*`, `M23-EXT-*` | R11 |
| Medicament | `M23-MED-*` (idType, `rsn`, `autoMed`, `prscbBy`, one statement per posology) | R8, R9 |
| Posology | `M23-DOS-*` (table of R9) | R9 |
| Prescription | `M23-RX-*` (table of R10) | R10 |
| Reverse | `M23-REV-*` | R8–R11 |

The medicament rules follow CHMED16A M-MED-01..09, with these differences:
- the reason is `rsn`;
- `idType` 4 (product number) is allowed in plans only;
- `prscbBy` is WARNING `UNMAPPABLE_ELEMENT`, as in CHMED16A.

## 5. Reason codes

New:

| Code | Severity | When |
|------|----------|------|
| `CHUNK_INVALID` | ERROR | chunked payload incomplete, duplicated, out of range, mixed or `n` = 1 (R5); message names the index, never data |

Reused with new uses:

| Code | Use for CHMED23A |
|------|------------------|
| `CHMED_INVALID_FIELD` | a C23 rule is violated (input: `PARSE`/`VALIDATE_INPUT`; output: `VALIDATE_OUTPUT`) |
| `UNSUPPORTED_VERSION` | subversion other than `A`; CHMED16A payload or JSON declared as `chmed23a` (and the reverse in the CHMED16A module) |
| `UNSUPPORTED_DOCUMENT_TYPE` | `medType` 2 or other; CH EMED document other than card or prescription |
| `DOSAGE_FREE_TEXT_FALLBACK` | posology without exact CH EMED equivalent, in either direction (R9) |
| `UNMAPPABLE_ELEMENT` | medical data, extensions, `sic`, `moa` without mapping, sub-day durations, non-UUID `id`, `relMeal` as text, usage `-` fields (INFORMATION) |
| `UNKNOWN_FIELD_IGNORED` | unknown JSON properties (INFORMATION) |
| `DERIVED_VALUE` | derived document id, language from option, rounded integers, split of a prescribed medicament with several posologies |
| `MISSING_REQUIRED_DATA` | reverse: no gender, no identifier, author parts missing and not given as option |
| `QR_CAPACITY_EXCEEDED`, `QR_NOT_FOUND`, `QR_MULTIPLE`, `IMAGE_*` | as in feature 003 |

`QR_IMAGE_NOT_ALLOWED` does not apply to CHMED23A (R6).

## 6. Options

No new options. The following are used:

| Option | Use for CHMED23A |
|--------|------------------|
| `outputForm` | `document`, `qr-payload`, `qr-image` for target `chmed23a` |
| `maxDecodedSize` | image size and decompressed payload |
| `patientIdentifier` | only if the document has no usable patient id (research F5) |
| `author`, `organization` | reverse: missing required parts of `hcPerson`/`hcOrg`; ignored when reading (R8) |
| `defaultLanguage` | reverse: `lng` of a plan when the CH EMED document has no language |
| `confidentiality` | CH EMED Composition, as for CHMED16A |
| `idStrategy` (internal) | derived identifiers (R8) |

## 7. Capability gaps (summary)

The full list is in the mapping specification and is asserted by the round-trip tests (FR-011).
- **Posology without an exact equivalent** (R9), carried as text: DaysOfMonth, Interval, Sequence,
  DosageFromTo, non-daily Times or DaySegments, and different doses at clock times. It returns as
  FreeText.
- **Medical data** (weight, height, risks, …) and **extensions**: not kept.
- **`sic`**, `moa` without mapping, repetition durations under one day: not kept.
- **Non-UUID document id**: replaced.
- **Prescribed medicament with several posologies**: returns as several medicaments.
- **`relMeal` 4–7 and with clock times**: kept as text in `additionalInstruction`.
