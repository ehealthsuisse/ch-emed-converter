# Mapping specification: CHMED23A ↔ CH EMED

**Version**: 0.1.0 (feature 006, in progress; domain review pending) | **CHMED23A**: commit
`0b579aa` of `ig-emediplan/specification` | **CH EMED**: 6.0.0. See
[pinned specifications](../specifications.md).

**CHMED23A is deprecated.** IG eMediplan stopped its development (strategy of 2025-12-02); the
specification is frozen and marked "Deprecated". The converter still reads and writes it, for
existing documents and for partners that still use it. Capabilities and documentation mark it as
deprecated (feature 006, FR-015).

This document is the single mapping specification for CHMED23A required by constitution principle
II. Every rule has a stable id:
- `C23-<AREA>-<NN>`: conformance rules (there is no JSON schema; research R7);
- `M23-<AREA>-<NN>`: mapping rules.

Code that implements a rule carries `@MappingRule` with its id, and a test checks that both sides
match (`MappingTraceabilityTest`, task T025).

**Conventions**:
- CHMED23A paths are JSON Pointers (`/meds/0/pos/1/po/tdo`).
- CH EMED paths are FHIRPath expressions.
- Empty strings are treated as absent.
- Decimals keep the precision of the source (`1.0` stays `1.0`).
- Messages name paths, never values (FR-016).
- Generated resource ids are UUID v5 of `<document id>#<path>`, as for CHMED16A; `fullUrl` and
  references use `urn:uuid:<id>`.

The rules follow CHMED16A (`chmed16a-ch-emed.md`) wherever the formats agree; such rows say "as
M-…".

## Conformance

The rules come from the object model (README.md), posology.md and terminology.md of the pinned
commit. They are checked on input (phase `VALIDATE_INPUT`; violations refuse the input) and on
output (phase `VALIDATE_OUTPUT`; a violation is a mapping bug and fails the conversion). Usage per
document type is MP (medication plan, `medType` 1) / Rx (prescription, `medType` 3). Unless stated
otherwise, a violation is ERROR `CHMED_INVALID_FIELD` at the path.

| Id | Rule |
|----|------|
| C23-OBJ-01 | Medication: `patient`, `medType`, `auth` and `dt` are required; Rx needs at least one medicament; `hcPerson` and `hcOrg` are required if `auth` = 1. |
| C23-OBJ-02 | `medType` 1 or 3 (2 and other values: M23-DOC-03); `auth` 1 or 2; `auth` 2 only for MP. |
| C23-OBJ-03 | `dt` is a date-time with offset (`YYYY-MM-DDThh:mm:ss[.f…]±hh:mm`). |
| C23-OBJ-04 | Patient: `fName`, `lName`, `bdt` and `gender` required; `lng` required for MP; `bdt` a date; `gender` 1, 2 or 3; `country` ISO 3166 alpha-2; `lng` ISO 639-1 in either case. |
| C23-OBJ-05 | Patient ids: at least one; `type` 1 or 2; `val` required; `sId` required for `type` 2. |
| C23-OBJ-06 | MedicalData: `dLstMen` a date; `toG` `{week}-{day}` with day > 0; risk category `id` 1–7; risk ids not in terminology.md or in another category → WARNING. |
| C23-OBJ-07 | HealthcarePerson: `fName`, `lName` required; `gln` required for Rx. |
| C23-OBJ-08 | HealthcareOrganization: `name`, `street`, `zip`, `city` required; `gln` required for MP when the person has none; a ZSR number in person and organization → ERROR (it may be set only once). |
| C23-OBJ-09 | Medicament: `id` and `idType` required; `idType` 1–4, 4 (product number) not for Rx; `autoMed` required for MP. |
| C23-OBJ-10 | Repetition: `t` 1–3; `v` ≥ 0; `d` > 0; `u` a time unit 1–7. |
| C23-OBJ-11 | Extension: `nm` and `schema` required. |
| C23-OBJ-12 | Fields with usage `-` for the document type ("can be set, but won't be used"): `rec`, `reps`, `sic`, `nbPack`, `zsr` in MP; `autoMed`, `prscbBy`, `mData`, `lng` in Rx → INFORMATION `UNMAPPABLE_ELEMENT` "not used for this document type". |
| C23-OBJ-13 | Unknown properties → INFORMATION `UNKNOWN_FIELD_IGNORED`. A property of the wrong JSON type → ERROR. |
| C23-OBJ-14 | An integer property given with a decimal part is rounded (conventions.md) → INFORMATION `DERIVED_VALUE`. |
| C23-POS-01 | Posology: `po` required; `unit` required for MP; `dtFrom`, `dtTo` a date or a date-time with offset; `dtTo` ≥ `dtFrom`; `relMeal` 1–7. |
| C23-POS-02 | Daily: `ds` has exactly 4 values, each ≥ 0. |
| C23-POS-03 | FreeText: `text` is not empty. |
| C23-POS-04 | Single: `tdo` is DosageOnly, Times or DaySegments. |
| C23-POS-05 | Cyclic: `cyDuU` a time unit; `cyDu` > 0; `tdpc` > 0; `tdo` DosageOnly, Times, DaySegments or Interval, WeekDays only with `cyDuU` week, DaysOfMonth only with `cyDuU` month. |
| C23-POS-06 | Sequence: `sos` has at least one element; durations > 0 with a time unit; `po` of a PosologySequence is not a Sequence. |
| C23-POS-07 | WeekDays: `wds` at least one, values 1–7, unique; `tdo` DosageOnly, Times or DaySegments. |
| C23-POS-08 | DaysOfMonth: `doms` at least one, values 1–28, unique; `tdo` DosageOnly, Times or DaySegments. |
| C23-POS-09 | Interval: `do` required; `miDu` > 0; `miDuU` a time unit. |
| C23-POS-10 | Times: at least one application; `dt` `hh:mm` or `hh:mm:ss` between 00:00:00 and 23:59:59. DaySegments: at least one application; `s` 1–4. Each application has a dosage. |
| C23-POS-11 | Dosage: Simple `a` > 0; FromTo `aFrom` ≥ 0, `aTo` > `aFrom`, `du` > 0 with a time unit; Range `aMin` > 0, `aMax` > `aMin`. |
| C23-POS-12 | Every polymorphic object has a known `t`; a missing or unknown `t` → ERROR at `…/t`. |
| C23-TERM-01 | `unit` is a code of CDTYP 9 (terminology.md "Unit", case-insensitive); otherwise WARNING (research R7, finding F9). |
| C23-TERM-02 | `roa` is a code of CDTYP 61; otherwise WARNING. |
| C23-TERM-03 | `moa` is a code of CDTYP 62; otherwise WARNING. |

**C23 ↔ .NET validation tests**: filled in by task T074.

## Input forms

| Id | Rule |
|----|------|
| M23-IN-01 | The form is detected by content (research R3), in this order: PNG or JPEG → QR image (M23-QR-01); GIF, BMP, TIFF, WebP → `IMAGE_UNREADABLE`; text starting with `CHMED23` (case-insensitive) → payload (M23-IN-03) or chunks (M23-IN-04); `CHMED16` → `UNSUPPORTED_VERSION` "use source chmed16a"; a JSON object with `resourceType` → `PARSE_ERROR` "use source ch-emed"; a JSON object with CHMED16A keys (`Patient`, `Medicaments`) and no `patient` → `UNSUPPORTED_VERSION` "use source chmed16a"; other JSON → M23-IN-02; anything else → `PARSE_ERROR`. A leading BOM and surrounding whitespace are ignored. |
| M23-IN-02 | Plain CHMED23A JSON (UTF-8). It carries no version marker; the declared source `chmed23a` supplies it. Invalid JSON → `PARSE_ERROR` without content. |
| M23-IN-03 | Payload (ChTransmissionFormat ReleaseYear 23): `CHMED23` + subversion + `.` + Base64 of GZip of the JSON; prefix and subversion case-insensitive; subversion other than `A` → `UNSUPPORTED_VERSION`; decompressed size ≤ `maxDecodedSize`; corrupt Base64 or GZip → `PARSE_ERROR`; the payload is never echoed. |
| M23-IN-04 | Chunks (research R5): several whitespace-separated tokens `CHMED23A.{i}/{n}.{data}`, in any order, joined in index order and then read as M23-IN-03. Every token must carry chunk metadata; same subversion and `n` in all; `n` > 1; each index 1…n exactly once. Otherwise `CHUNK_INVALID`, naming the index, never data. |

## QR code

| Id | Rule |
|----|------|
| M23-QR-01 | A PNG or JPEG image is read in memory, at most `maxDecodedSize` bytes and 40 megapixels → otherwise `IMAGE_TOO_LARGE`; undecodable → `IMAGE_UNREADABLE` (as M-QR-01). |
| M23-QR-02 | The image holds exactly one QR code: none → `QR_NOT_FOUND`; several → `QR_MULTIPLE` (as M-QR-02). |
| M23-QR-03 | The content is read as UTF-8; not valid UTF-8 → `PARSE_ERROR` (as M-QR-03). |
| M23-QR-04 | The content must be a whole CHMED23A payload (M23-IN-03). A chunk → `CHUNK_INVALID` "chunks are read as text only"; a CHMED16A payload → `UNSUPPORTED_VERSION`; anything else → `PARSE_ERROR`. |
| M23-QR-10 | `qr-payload`: `CHMED23A.` + Base64 (standard alphabet, padding, no line breaks) of GZip (no timestamp, no file name) of exactly the bytes of the `document` output; ASCII. Decoding gives back the `document` output. Chunks are never written. |
| M23-QR-11 | `qr-image`: PNG of that payload with the parameters of M-QR-11 (byte mode, error correction M, smallest version, quiet zone 4, 8 px per module, 40 mm wide). Also for prescriptions: CHMED23A has no signature (clarification 2026-11-12). |
| M23-QR-12 | A payload above 2331 bytes (version 40-M) → `QR_CAPACITY_EXCEEDED` naming the limit, no image; nothing is split, shortened or dropped; `qr-payload` stays available (as M-QR-12). |

## Document

| Id | CHMED23A | CH EMED | Notes |
|----|----------|---------|-------|
| M23-DOC-01 | `medType` 1 | Medication Card: Bundle `ch-emed-document-medicationcard`, Composition `ch-emed-composition-medicationcard`, type LOINC `56445-0` + SNOMED `736378000` | as M-DOC-01 |
| M23-DOC-02 | `medType` 3 | Medication Prescription (M23-RX-01) | |
| M23-DOC-03 | `medType` 2 (PolymedicationCheck, deprecated) or another value | — | `UNSUPPORTED_DOCUMENT_TYPE` naming MP (1) and Rx (3) |
| M23-DOC-04 | `id` | `Bundle.identifier`, `Composition.identifier` `urn:uuid:<id>` if `id` is a UUID | otherwise a UUID v5 derived from the content: INFORMATION `DERIVED_VALUE`; a non-UUID `id` is also INFORMATION `UNMAPPABLE_ELEMENT` (research R8) |
| M23-DOC-05 | `dt` | `Bundle.timestamp`, `Composition.date`, `MedicationStatement.dateAsserted` / `MedicationRequest.authoredOn` | string kept, including offset and fraction (as M-DOC-05) |
| M23-DOC-06 | `rmk` | section `annotation` (LOINC `48767-8`) | as M-DOC-06 |
| M23-DOC-07 | `patient/lng` (de/fr/it/en, either case) | `Composition.language` `de-CH`/`fr-CH`/`it-CH`/`en-CH`; otherwise option `defaultLanguage` | INFORMATION `DERIVED_VALUE`; titles localized (as M-DOC-08) |
| M23-DOC-08 | — | `Composition.confidentiality` and the CH Core EPR extension from option `confidentiality` | INFORMATION `DERIVED_VALUE` (as M-DOC-09) |
| M23-DOC-09 | — | section `originalRepresentation` → `Binary` PDF rendered from the document | as M-DOC-10 |
| M23-DOC-10 | no medicaments (MP) | section with `emptyReason` `nilknown` and localized narrative | as M-DOC-11 |

## Author

| Id | CHMED23A | CH EMED | Notes |
|----|----------|---------|-------|
| M23-AUT-01 | `auth` 1, `hcPerson`, `hcOrg` | `Composition.author` PractitionerRole → Practitioner (`gln` → identifier `urn:oid:2.51.1.3`, names, `zsr` → identifier ZSR) + Organization (`name`, address with `country`, `gln`, `zsr`) | from the document; GLN and ZSR are not checked for format (finding F7) |
| M23-AUT-02 | `auth` 2 | `Composition.author` = the Patient | plans only (C23-OBJ-02) |
| M23-AUT-03 | option `author` given | not used | INFORMATION `DERIVED_VALUE` "author taken from the document" |

## Patient

| Id | CHMED23A | CH EMED | Notes |
|----|----------|---------|-------|
| M23-PAT-01 | `fName`, `lName` | `name.given`, `name.family` | |
| M23-PAT-02 | `bdt` | `birthDate` | |
| M23-PAT-03 | `gender` 1 / 2 / 3 | `gender` `male` / `female` / `other` | |
| M23-PAT-04 | `ids` type 1 | `identifier` VeKa `urn:oid:2.16.756.5.30.1.123.100.1.1.1` | |
| M23-PAT-05 | `ids` type 2 + `sId` | `identifier.system` = `sId` (`urn:oid:` added to a bare dotted number), `value` = `val` | no usable id → option `patientIdentifier`, else `MISSING_REQUIRED_DATA` (research F5) |
| M23-PAT-06 | `street`, `zip`, `city`, `country` | `address.line`, `.postalCode`, `.city`, `.country` | absent `country` stays absent |
| M23-PAT-07 | `phones`, `emails` | `telecom` phone / email | |
| M23-PAT-08 | `lng` | `communication.language` (BCP 47, lower case), `preferred` true | as M-PAT-06 |
| M23-MDT-01 | `mData` (`w`, `h`, `rcs`, `dLstMen`, `prem`, `toG`) | — | the Medication Card has no place for it → WARNING `UNMAPPABLE_ELEMENT` with clinical loss per element (as M-MDT-01) |
| M23-EXT-01 | `exts` at any level | — | WARNING `UNMAPPABLE_ELEMENT` per extension, path only (no name, schema or value) |

## Medicament

| Id | CHMED23A | CH EMED | Notes |
|----|----------|---------|-------|
| M23-MED-01 | `idType` 2 (GTIN) | contained Medication `code.coding` `urn:oid:2.51.1.1`, `code.text` "GTIN <id>" | as M-MED-01 |
| M23-MED-02 | `idType` 1 (none) | `code.text` = `id` | as M-MED-02 |
| M23-MED-03 | `idType` 3 (Pharmacode) | `code.text` "Pharmacode <id>" | WARNING `UNMAPPABLE_ELEMENT` (as M-MED-03) |
| M23-MED-04 | `idType` 4 (product number) | `code.text` "Product number <id>" | WARNING `UNMAPPABLE_ELEMENT` (as M-MED-04) |
| M23-MED-05 | `rsn` | `reasonCode.text` | |
| M23-MED-06 | `autoMed` true | `MedicationStatement.category` `patientspecified` | as M-MED-06 |
| M23-MED-07 | `prscbBy` | — | WARNING `UNMAPPABLE_ELEMENT` (as M-MED-07) |
| M23-MED-08 | each medicament × posology | one MedicationStatement `ch-emed-medicationstatement-card`, as M-MED-08 | |
| M23-MED-09 | medicament without posology | one MedicationStatement with dosage text "dosage not given" | INFORMATION `DERIVED_VALUE` |

## Dosage

"Text" means: the base entry `Dosage.text` holds the generated, localized description of the whole
posology, and the report has WARNING `DOSAGE_FREE_TEXT_FALLBACK` with clinical loss (research R9).

| Id | CHMED23A | CH EMED `Dosage` | Notes |
|----|----------|------------------|-------|
| M23-DOS-01 | `dtFrom`, `dtTo` | base entry `timing.repeat.boundsPeriod.start` / `.end` | precision kept; `dtTo` inclusive |
| M23-DOS-02 | Daily `ds` | non-zero doses → `when` MORN/NOON/EVE/NIGHT with `doseQuantity`; one dose → single entry; several → base entry `sequence` 1 + split entries | as M-DOS-02; all zero → text |
| M23-DOS-03 | FreeText | `Dosage.text` = `text` | lossless, no report entry |
| M23-DOS-04 | Single + DosageOnly; + Times with one time; + DaySegments with one segment | `timing.repeat.count` 1 with dose; plus `timeOfDay` or `when` | several times or segments → text |
| M23-DOS-05 | Cyclic + DosageOnly | `timing.repeat.frequency` = `tdpc`, `period` = `cyDu`, `periodUnit` s/min/h/d/wk/mo/a | |
| M23-DOS-06 | Cyclic 1 day + Times, equal doses | `timing.repeat.timeOfDay` list with one dose | different doses → text (split entries require `when`) |
| M23-DOS-07 | Cyclic 1 day + DaySegments | as M23-DOS-02 | |
| M23-DOS-08 | Cyclic 1 week + WeekDays (+ DosageOnly, Times with equal doses, DaySegments) | `timing.repeat.dayOfWeek` plus the inner timing; split entries repeat `dayOfWeek` | `cyDu` > 1 → text |
| M23-DOS-09 | DaysOfMonth, Interval, Sequence, DosageFromTo, Cyclic other than 1 day with Times or DaySegments, `tdpc` > 1 with timed applications | text | |
| M23-DOS-10 | DosageSimple / DosageRange | `doseQuantity` / `doseRange` (low, high) | |
| M23-DOS-11 | `inRes` true | `asNeededBoolean` true | |
| M23-DOS-12 | `appInstr` | base entry `patientInstruction` | |
| M23-DOS-13 | `roa` (CDTYP 61) | `route` EDQM, same code, if in the CH Term route value set | otherwise WARNING `UNMAPPABLE_ELEMENT` with clinical loss |
| M23-DOS-14 | `moa` (CDTYP 62) | `method` EDQM through the table `method-edqm.csv` (unambiguous rows only; domain review) | unmapped → WARNING `UNMAPPABLE_ELEMENT` with clinical loss |
| M23-DOS-15 | `unit` (CDTYP 9) | `doseQuantity.unit` = `unit`, system/code from the CDTYP 9 table (as M-DOS-08) | unmapped → text |
| M23-DOS-16 | `relMeal` 1/2/3 | with day segments or without time: `when` ACM/ACD/ACV, CM/CD/CV, PCM/PCD/PCV, or AC/C/PC | with times of day, or 4–7: the localized label in `additionalInstruction.text`, WARNING `UNMAPPABLE_ELEMENT` without clinical loss |

## Prescription

| Id | CHMED23A | CH EMED | Notes |
|----|----------|---------|-------|
| M23-RX-01 | `medType` 3 | Bundle `ch-emed-document-medicationprescription`, Composition type LOINC `57833-6` + SNOMED `761938008`; sections as M-RX-01 | |
| M23-RX-02 | each medicament × posology | one MedicationRequest `ch-emed-medicationrequest`, status `active`, intent `order`, `authoredOn` = `dt`, `requester` = author role, contained Medication (M23-MED-01..04), dosage as M23-DOS-* | several posologies → INFORMATION `DERIVED_VALUE` naming the split (research R10) |
| M23-RX-03 | `reps` Number `v` | `dispenseRequest.numberOfRepeatsAllowed` = `v` | |
| M23-RX-04 | `reps` Duration `d`, `u` day/week/month/year | `dispenseRequest.validityPeriod`: start = date of `dt`, end = start + `d` `u` − 1 day | seconds, minutes, hours → WARNING `UNMAPPABLE_ELEMENT` with clinical loss |
| M23-RX-05 | `reps` NumberAndDuration | M23-RX-03 and M23-RX-04 | no `reps` → nothing; the default is not materialised |
| M23-RX-06 | `nbPack` | `dispenseRequest.quantity` (SNOMED `1681000175101`) | as M-RX-05 |
| M23-RX-07 | `isNotSub` true | `substitution.allowedCodeableConcept` `N`; false → `E` | absent → no substitution (as M-RX-07) |
| M23-RX-08 | `sic` true | — | WARNING `UNMAPPABLE_ELEMENT` with clinical loss (domain review) |
| M23-RX-09 | `rec` | `dispenseRequest.performer` logical reference, identifier GLN | as M-RX-09 |
| M23-RX-10 | medicament without posology | dosage text "dosage not given" | INFORMATION `DERIVED_VALUE` (as M-RX-04) |

## Reverse direction (CH EMED → CHMED23A)

| Id | CH EMED | CHMED23A | Notes |
|----|---------|----------|-------|
| M23-REV-01 | Medication Card / Medication Prescription (SNOMED type, LOINC only without SNOMED) | `medType` 1 / 3 | other document types → `UNSUPPORTED_DOCUMENT_TYPE` naming MP and Rx; confidentiality and original representation are not carried over (INFORMATION) |
| M23-REV-02 | `Composition.identifier` `urn:uuid:` | `id` (the UUID) | |
| M23-REV-03 | `Composition.date` | `dt` | |
| M23-REV-04 | section `annotation` | `rmk` | |
| M23-REV-05 | `Composition.author` Patient | `auth` 2 | Rx → `MISSING_REQUIRED_DATA` (a prescription needs a healthcare person) |
| M23-REV-06 | `Composition.author` PractitionerRole | `auth` 1, `hcPerson` (GLN, names, ZSR), `hcOrg` (GLN, name, address, ZSR) | missing required parts from options `author` / `organization`, else `MISSING_REQUIRED_DATA` at the path |
| M23-REV-07 | Patient names, birth date, address, telecom | `fName`, `lName`, `bdt`, `street`, `zip`, `city`, `country`, `phones`, `emails` | |
| M23-REV-08 | `Composition.language`, else `communication.language` | `lng` (two letters) | MP without language → option `defaultLanguage`, INFORMATION `DERIVED_VALUE` |
| M23-REV-09 | `gender` male / female / other | 1 / 2 / 3 | `unknown` or absent → `MISSING_REQUIRED_DATA` |
| M23-REV-10 | `identifier` VeKa / other system | `ids` type 1 / type 2 with `sId` | none → `MISSING_REQUIRED_DATA` |
| M23-REV-11 | MedicationStatement / MedicationRequest with contained Medication | one medicament each; `idType` from the coding (GTIN → 2) or `code.text` (1); `rsn`; `autoMed`; requests are not merged (research R10) | |
| M23-REV-12 | structured dosage | inverse of M23-DOS-02, -04 … -08, -10 … -16 | |
| M23-REV-13 | dosage with `text` only | FreeText | lossless |
| M23-REV-14 | dosage the reverse table does not cover | FreeText with the generated description | WARNING `DOSAGE_FREE_TEXT_FALLBACK` |
| M23-REV-15 | `numberOfRepeatsAllowed`, `validityPeriod` | `reps` Number / Duration (months if whole months, else days) / NumberAndDuration | |
| M23-REV-16 | `dispenseRequest.quantity`, `substitution`, `dispenseRequest.performer` | `nbPack`, `isNotSub`, `rec` | |

## Capability gaps

What one format cannot express and the converter does not keep (FR-011). The round-trip tests
assert exactly these differences.

| Gap | Direction | Rules | Clinical loss |
|-----|-----------|-------|---------------|
| Posology without exact CH EMED equivalent (DaysOfMonth, Interval, Sequence, DosageFromTo, non-daily times or segments, different doses at clock times); returns as FreeText | CHMED23A → CH EMED | M23-DOS-09, M23-REV-13 | yes (structure) |
| Medical data (weight, height, risks, menstruation, prematurity, gestation) | CHMED23A → CH EMED | M23-MDT-01 | yes |
| Extensions | CHMED23A → CH EMED | M23-EXT-01 | unknown, reported |
| `sic`; repetition durations under one day | CHMED23A → CH EMED | M23-RX-08, M23-RX-04 | yes |
| `moa` without confirmed EDQM mapping; `roa` outside the CH Term value set | CHMED23A → CH EMED | M23-DOS-13, M23-DOS-14 | yes |
| `prscbBy`; Pharmacode and product number as text only | CHMED23A → CH EMED | M23-MED-07, M23-MED-03, M23-MED-04 | no |
| Non-UUID document id | CHMED23A → CH EMED | M23-DOC-04 | no |
| Prescribed medicament with several posologies returns as several medicaments | round trip | M23-RX-02, M23-REV-11 | no |
| `relMeal` 4–7 or with clock times kept as text | round trip | M23-DOS-16 | no |
| CH EMED elements without CHMED23A counterpart (confidentiality, original representation, statuses, notes) | CH EMED → CHMED23A | M23-REV-01 | no |
