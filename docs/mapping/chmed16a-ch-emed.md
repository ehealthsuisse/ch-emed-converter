# Mapping specification: CHMED16A ↔ CH EMED

**Version**: 1.0.0 (complete for medication plans and prescriptions in both directions; domain review pending, task T118) | **CHMED16A**: commit `0b579aa`
| **CH EMED**: 6.0.0 — see [pinned specifications](../specifications.md)

This document is the single, versioned mapping specification required by constitution
principle II. Every mapping rule has a stable id `M-<AREA>-<NN>`; code annotated with
`@MappingRule` references these ids, and a test checks that both sides match (SC-008).

Conventions: CHMED16A paths are JSON Pointers (`/Patient/FName`), CH EMED paths are FHIRPath
expressions. Empty strings in CHMED16A are treated as absent. Generated resource ids are UUID v5
of `<CHMED Id>#<path>` (research R8); `fullUrl` and references use `urn:uuid:<id>`.

**Domain review pending (task T118)**: a person with CH EMED / eMediplan knowledge has to confirm the
interpretation points of this specification. They are marked "domain review" in the rules and
collected here: the route and unit tables (M-DOS-04, M-DOS-08), SNOMED `767525000` "Unit" for `STK`
and `732936001` "Tablet" read back as `STK`, the Pharmacode handling (M-MED-03), the meaning of
`Rep` as validity in months (M-RX-06, M-REV-36), the substitution codes (M-RX-07, M-REV-37), the
indication code as text (M-RX-08), the receiver as logical reference (M-RX-09), the localized titles
of the prescription document, the reading of statuses (M-REV-10, M-REV-32), the eRezept zip code
pattern for foreign addresses (M-ERX-04) and the treatment of `Rep` above 24 months.

## Input forms

| Id | Rule |
|----|------|
| M-IN-01 | Input starting with `{` is plain CHMED16A JSON. |
| M-IN-02 | Input starting with `CHMED` (case-insensitive) is the textual QR payload (ChTransmissionFormat ReleaseYear 16): `CHMED16A`, compression flag `1` = Base64 GZip JSON, `0` = plain JSON. Decompressed size is limited by `maxDecodedSize` (default 5 MB). Corrupt payload → `PARSE_ERROR`; the payload is never echoed. Another release year/sub-version → `UNSUPPORTED_VERSION`. |
| M-IN-03 | Input starting with `http` (e.g. signed eRezept URL) → `PARSE_ERROR` pointing to the QR/eRezept feature. |
| M-IN-04 | JSON with camelCase CHMED23A keys (`patient`, `meds`, `medType`) and without `Patient` → `UNSUPPORTED_VERSION`. |
| M-IN-05 | Medication plans carry no revision marker. Prescriptions carry `rev` (absent = 1, pinned = 3); a field required only by a later revision than the declared one and missing → WARNING `CHMED_REVISION_FIELD_MISSING`. Output always conforms to the latest revision (prescriptions `rev` = 3). |
| M-IN-06 | Unknown fields → INFORMATION `UNKNOWN_FIELD_IGNORED` (CHMED16A: parsers MUST ignore unknown fields); non-empty `PFields` → INFORMATION, not mapped. |

## Document metadata (Medication Card)

| Id | CHMED16A | CH EMED | Notes |
|----|----------|---------|-------|
| M-DOC-01 | `/MedType` 1 | Bundle `ch-emed-document-medicationcard`, Composition `ch-emed-composition-medicationcard`, type LOINC `56445-0` + SNOMED `736378000` | |
| M-DOC-02 | `/MedType` 3 | Medication Prescription document | see M-RX-01 |
| M-DOC-03 | `/MedType` 2 | — | `UNSUPPORTED_DOCUMENT_TYPE` |
| M-DOC-04 | `/Id` | `Bundle.identifier`, `Composition.identifier` (`urn:ietf:rfc:3986`, `urn:uuid:<Id>`) | Id must be a UUID |
| M-DOC-05 | `/Dt` | `Bundle.timestamp`, `Composition.date`, `MedicationStatement.dateAsserted` | string preserved incl. offset |
| M-DOC-06 | `/Rmk` | section `annotation` (LOINC `48767-8`), title localized, narrative = text | only if non-empty |
| M-DOC-07 | `/ValBy`, `/ValDt` | — | WARNING `UNMAPPABLE_ELEMENT` (no clinical loss) |
| M-DOC-08 | `/Patient/Lng` (de/fr/it/en, case-insensitive) | `Composition.language` `de-CH`/`fr-CH`/`it-CH`/`en-CH`; otherwise option `defaultLanguage` | INFORMATION `DERIVED_VALUE`; title and section titles localized (FR-022) |
| M-DOC-09 | — | `Composition.confidentiality` + CH Core EPR confidentiality extension from option `confidentiality` (`NORMAL` = `N` / SNOMED `17621005`) | INFORMATION `DERIVED_VALUE` (FR-022) |
| M-DOC-10 | — | section `originalRepresentation` (LOINC `55108-5`) → `Binary` PDF rendered from the document (FR-021, research R12) | |
| M-DOC-11 | no medicaments | section `card` with `emptyReason` `nilknown` and localized narrative | cmp-1 requires text |

## Patient

| Id | CHMED16A | CH EMED (CH Core Patient EPR) | Notes |
|----|----------|------------------------------|-------|
| M-PAT-01 | `/Patient/FName`, `/Patient/LName` | `name.given`, `name.family` | family required → `MISSING_REQUIRED_DATA` |
| M-PAT-02 | `/Patient/BDt` | `birthDate` | required → `MISSING_REQUIRED_DATA`, no placeholder |
| M-PAT-03 | `/Patient/Gender` 1 / 2 | `gender` `male` / `female` | required → `MISSING_REQUIRED_DATA` |
| M-PAT-04 | `/Patient/Ids` (Type 1) | `identifier` system VeKa `urn:oid:2.16.756.5.30.1.123.100.1.1.1` | none → option `patientIdentifier`, else `MISSING_REQUIRED_DATA` (FR-023) |
| M-PAT-05 | `/Patient/Street`, `/Zip`, `/City`, `/Phone`, `/Email` | `address.line`, `.postalCode`, `.city`; `telecom` phone/email | country is not invented |
| M-PAT-06 | `/Patient/Lng` | `communication.language` (BCP 47, lower case), `preferred` true | |

## Author

| Id | CHMED16A | CH EMED | Notes |
|----|----------|---------|-------|
| M-AUT-01 | `/Auth` starting with "patient" | `Composition.author` = the Patient | |
| M-AUT-02 | `/Auth` (GLN) + option `author` | PractitionerRole → Practitioner (GLN `urn:oid:2.51.1.3`, name) + Organization (name, address) | option required (FR-023); GLN from option if `Auth` is not a GLN |
| M-AUT-03 | missing author details | — | `MISSING_REQUIRED_DATA` at `/Auth` |

## Medication

| Id | CHMED16A | CH EMED | Notes |
|----|----------|---------|-------|
| M-MED-01 | `IdType` 2 (GTIN) | contained Medication `code.coding` system `urn:oid:2.51.1.1`; `code.text` = "GTIN <Id>" | `code.text` is 1..1 and CHMED16A has no product name → INFORMATION `DERIVED_VALUE` |
| M-MED-02 | `IdType` 1 (none) | `code.text` = `Id` (free text) | never matched to a code |
| M-MED-03 | `IdType` 3 (Pharmacode) | `code.text` = "Pharmacode <Id>" | no Pharmacode code system in the pinned packages → WARNING `UNMAPPABLE_ELEMENT` (no clinical loss) |
| M-MED-04 | `IdType` 4 (product number) | `code.text` = "Product number <Id>" | WARNING `UNMAPPABLE_ELEMENT` |
| M-MED-05 | `TkgRsn` | `MedicationStatement.reasonCode.text` | |
| M-MED-06 | `AutoMed` 1 | `MedicationStatement.category` `patientspecified` | |
| M-MED-07 | `PrscbBy` | — | WARNING `UNMAPPABLE_ELEMENT` |
| M-MED-08 | each Medicament × Posology | one MedicationStatement (`ch-emed-medicationstatement-card`), status `active` (`completed` if `DtTo` before `Dt`), identifier `urn:uuid:<id of path/identifier>`, `informationSource` = author | several posologies → one statement each (INFORMATION); none → dosage text "Dosierung nicht angegeben" (localized, INFORMATION) |

| M-MED-09 | `Unit` without `Pos` | — | a unit has no dose to attach to → INFORMATION `UNMAPPABLE_ELEMENT`; instruction (`AppInstr`), route (`Roa`), reason and self-medication of a medicament without `Pos` are kept (the dosage text is the localized note of M-MED-08) |

## Dosage / posology

| Id | CHMED16A | CH EMED | Notes |
|----|----------|---------|-------|
| M-DOS-01 | `DtFrom` / `DtTo` | base entry `timing.repeat.boundsPeriod.start` / `.end` | date precision preserved; `DtTo` inclusive |
| M-DOS-02 | `D` [m, n, e, night] | non-zero doses → `when` `MORN`/`NOON`/`EVE`/`NIGHT` with `doseQuantity`; one dose → single entry without `sequence`; several → base entry `sequence` 1 + split entries `sequence` 2.. (`ch-emed-dosage-split`) | all zero → no dose (INFORMATION) |
| M-DOS-03 | `InRes` 1 | `asNeededBoolean` true | |
| M-DOS-04 | `Roa` | `route` EDQM: `PO` → `20053000` "Oral use"; `route.text` = `Roa` | other codes → WARNING `UNMAPPABLE_ELEMENT` with clinical loss (route binding is required) |
| M-DOS-05 | `AppInstr` | base entry `patientInstruction` | |
| M-DOS-06 | `TT` with daily cycle, equal doses, no duration/range/max | `timing.repeat.timeOfDay` + one `doseQuantity` | |
| M-DOS-07 | other `TT`, `CyDu` ≠ 1 day | base entry `text` (generated description) | WARNING `DOSAGE_FREE_TEXT_FALLBACK` with clinical loss; `CyDu` without `TT` ignored (INFORMATION) |
| M-DOS-08 | `Unit` (INDEX CDTYP 9, case-insensitive) | `doseQuantity.unit` = `Unit`; system/code: `STK` → SNOMED `767525000`; `MG` `G` `UG`/`MCG` `ML` `L` `IE` `MMOL` → UCUM `mg` `g` `ug` `mL` `L` `[iU]` `mmol`; `TR` → SNOMED `732994000`; `HUB` → SNOMED `732981002` | other units → dosage as text, WARNING `DOSAGE_FREE_TEXT_FALLBACK` with clinical loss |

## Medical data

| Id | CHMED16A | CH EMED | Notes |
|----|----------|---------|-------|
| M-MDT-01 | `/Patient/Med` (`DLstMen`, `Prem`, `ToG`, `Meas`, `Rc`) | — | the Medication Card has no place for it → WARNING `UNMAPPABLE_ELEMENT` with clinical loss per element |

## CH EMED → CHMED16A (Medication Card)

Input is a CH EMED document Bundle already validated against the pinned profiles (phase
`VALIDATE_INPUT`). References are resolved by `fullUrl` and by `<Type>/<id>`; `urn:uuid` and
relative references both work. The output is a CHMED16A medication plan that passes
`Chmed16aValidator` (phase `VALIDATE_OUTPUT`). Defaults of CHMED16A (`CyDu` 86400, `AutoMed` 0) are
never materialised.

| Id | CH EMED | CHMED16A | Notes |
|----|---------|----------|-------|
| M-REV-01 | `Composition.type` LOINC `56445-0` | `/MedType` 1 | LOINC `57833-6` is a prescription (M-REV-30); every other type → `UNSUPPORTED_DOCUMENT_TYPE` ERROR naming the code (FR-007) |
| M-REV-02 | `Bundle.identifier` (`urn:uuid:<uuid>`) | `/Id` | else `Composition.identifier`; neither → `MISSING_REQUIRED_DATA` |
| M-REV-03 | `Composition.date` | `/Dt` | string preserved; a value that is not an ISO 8601 date-time with offset fails the output validation |
| M-REV-04 | `Composition.author` → PractitionerRole → Practitioner identifier GLN (`urn:oid:2.51.1.3`) | `/Auth` | author = Patient → `Auth` `patient`; no GLN → `MISSING_REQUIRED_DATA`; further authors → INFORMATION `UNMAPPABLE_ELEMENT`; Organization identifier ZSR (`urn:oid:2.16.756.5.30.1.123.100.2.1.1`) of exactly 7 characters → `/Zsr`, otherwise INFORMATION |
| M-REV-05 | section LOINC `48767-8` narrative | `/Rmk` | text content of the XHTML div (tags removed, whitespace collapsed); several sections joined by a line feed; removed markup → INFORMATION `DERIVED_VALUE` |
| M-REV-06 | `Patient.name`, `birthDate`, `gender` | `FName`, `LName`, `BDt`, `Gender` | name with `use` official/usual preferred; several given names joined by a space (INFORMATION); further names → INFORMATION; family name or birth date missing → `MISSING_REQUIRED_DATA`; gender `other`/`unknown` → `Gender` absent, WARNING `UNMAPPABLE_ELEMENT` (no clinical loss) |
| M-REV-07 | `Patient.address`, `telecom` | `Street` (lines joined by `, `), `Zip`, `City`, `Phone`, `Email` | first address / first telecom of each system; further ones and a country other than `CH` → INFORMATION `UNMAPPABLE_ELEMENT` |
| M-REV-08 | `Patient.communication` (preferred, else first) | `/Patient/Lng` (ISO 639-1, lower case primary subtag) | absent → `Composition.language` primary subtag (INFORMATION `DERIVED_VALUE`); both absent → `MISSING_REQUIRED_DATA` (required in plans) |
| M-REV-09 | `Patient.identifier` system VeKa `urn:oid:2.16.756.5.30.1.123.100.1.1.1` | `Ids` `{Type 1, Val}` | other identifiers → WARNING `UNMAPPABLE_ELEMENT` (no clinical loss) |
| M-REV-10 | `MedicationStatement.status` | included or excluded | `active`, `intended`, `completed` → included; `on-hold`, `unknown` → included, WARNING `UNMAPPABLE_ELEMENT` with clinical loss (status not representable); `stopped`, `not-taken` → excluded, WARNING `UNMAPPABLE_ELEMENT` with clinical loss; `entered-in-error` → excluded, INFORMATION. Never dropped silently |
| M-REV-11 | contained or bundled `Medication.code` | `Id` + `IdType` | coding system `urn:oid:2.51.1.1` → GTIN, `IdType` 2; `code.text` `Pharmacode <digits>` → `IdType` 3, `Product number <value>` → `IdType` 4 (inverse of M-MED-03/04); otherwise `code.text` (else the first coding display) → `IdType` 1, never matched to a code; no usable text → `MISSING_REQUIRED_DATA`; other codings (ATC, SNOMED …) → INFORMATION `UNMAPPABLE_ELEMENT` |
| M-REV-12 | `reasonCode` | `TkgRsn` | `text`, else first coding display; several joined by `; ` (INFORMATION) |
| M-REV-13 | `category` `patientspecified` | `AutoMed` 1 | otherwise `AutoMed` 0 (required in medication plans); one aggregated INFORMATION `DERIVED_VALUE` at `MedicationStatement.category` |
| M-REV-14 | `dosage.patientInstruction` | `AppInstr` | equal texts once, different texts joined by `; ` (INFORMATION) |
| M-REV-15 | `dosage.route` EDQM `20053000` | `Roa` `PO` | other route → `Roa` absent, WARNING `UNMAPPABLE_ELEMENT` with clinical loss (inverse of M-DOS-04) |
| M-REV-16 | `dosage` with `timing.repeat.when` MORN/NOON/EVE/NIGHT and one `doseQuantity` | `Pos.D` [morning, noon, evening, night] + `Unit` | inverse of M-DOS-02/M-DOS-08; units: SNOMED `767525000` and `732936001` → `STK`, UCUM/SNOMED as in M-DOS-08 inverted; entries with the same `boundsPeriod` form one posology; each slot at most once |
| M-REV-17 | `timing.repeat.boundsPeriod` | `DtFrom`, `DtTo` | date precision preserved; a date-time loses its time of day (WARNING `UNMAPPABLE_ELEMENT`, no clinical loss); a posology is only produced if the `Unit` is known (CHMED16A requires `Unit` with `Pos`) — otherwise, and without a start date, the dates are carried as `from=<date>` / `to=<date>` in the description of M-REV-19 |
| M-REV-18 | `asNeededBoolean` true / `asNeededCodeableConcept` | `Pos.InRes` 1 | the reason of a CodeableConcept is not carried (INFORMATION) |
| M-REV-19 | dosage that cannot be expressed (`dayOfWeek`, `offset`, `count`, `duration`, `period` ≠ 1 d, `frequency` ≠ 1, `timeOfDay` with several doses, `doseRange`, `rateQuantity`, unknown unit, a slot used twice, `text` only) | posology with `DtFrom`/`DtTo` only (if start and `Unit` are known); description appended to `AppInstr` after the instruction (`; `) | `Dosage.text` if present, otherwise a language-neutral description `dose=<value>[-<value>] <unit>; when=<codes>; dayOfWeek=<codes>[; from=<date>][; to=<date>]` (several dosages separated by ` / `, unit = CHMED16A unit, else the code of the quantity); one WARNING per dosage that caused the fallback, all dosages of the statement are then carried as text; WARNING `DOSAGE_FREE_TEXT_FALLBACK` with clinical loss (US2 scenario 2) |
| M-REV-20 | no dosage, or only the localized note "dosage not given" (M-MED-08) | medicament without `Pos` | note recognised in all four languages (INFORMATION `DERIVED_VALUE`) |
| M-REV-21 | `timing.repeat.timeOfDay` with equal doses, daily cycle | `Pos.TT` `{Off, DoFrom}` | `Off` = seconds after midnight (inverse of M-DOS-06) |
| M-REV-22 | original representation (section LOINC `55108-5`, `Binary`) | — | INFORMATION `UNMAPPABLE_ELEMENT`, not carried over (FR-021) |
| M-REV-23 | `Composition.confidentiality` and the CH Core EPR confidentiality extension | — | INFORMATION `UNMAPPABLE_ELEMENT` (no CHMED16A element) |
| M-REV-24 | `MedicationStatement`/`MedicationRequest` extension `ch-emed-ext-treatmentplan`, `Medication.form`/`amount`/`ingredient`, `note` | — | extension and product details: INFORMATION `UNMAPPABLE_ELEMENT` when the medicament is GTIN-coded, otherwise WARNING with clinical loss (the product is identified by free text only); `note` → WARNING with clinical loss |
| M-REV-25 | — | `ValBy`, `ValDt`, `Patient.Med`, `Recoms` | the Medication Card has no source element; never produced |

## Prescription details

CHMED16A prescriptions (`MedType` 3, revision 3 of `prescription.md`) have fewer fields than medication
plans: the medicament carries only `Id`, `IdType`, `Pos` (at most one; only `DtTo` and `D`), `Unit`,
`AppInstr`, `Rep`, `Subs`, `NbPack`, `IndC`; the patient has no `Lng` and no medical data. The CH EMED
Medication Prescription document has one `MedicationRequest` per medicament. The rules marked
"domain review" below are interpretations of the specifications that a person with CH EMED /
eMediplan knowledge has to confirm (task T118).

The `clinicalLoss` flag of every report entry was reviewed against the capability-gap lists below
(task T104): entries with clinical loss are exactly the gaps marked "yes".

### CHMED16A → CH EMED

| Id | CHMED16A | CH EMED | Notes |
|----|----------|---------|-------|
| M-RX-01 | `/MedType` 3 | Bundle `ch-emed-document-medicationprescription`, Composition `ch-emed-composition-medicationprescription` type LOINC `57833-6` + SNOMED `761938008`; sections: annotation (M-DOC-06), original representation (M-DOC-10), `prescription` (LOINC `57828-6`) with one entry per MedicationRequest | titles localized (M-DOC-08); no medicament → `MISSING_REQUIRED_DATA` (the section needs an entry) |
| M-RX-02 | `/HcPerson`, `/HcOrg`, `/Zsr`, `/Auth` | PractitionerRole → Practitioner (GLN `Gln`, name, ZSR) + Organization (name, `NameAffix` → `alias`, address, ZSR, phone/email as `telecom`) | without `HcPerson`/`HcOrg` (revision 1) the option `author` supplies the details (FR-023, `MISSING_REQUIRED_DATA` otherwise); `Auth` ≠ `HcPerson.Gln` → WARNING `CHMED_INVALID_FIELD`, `HcPerson.Gln` wins; root `Zsr` not equal to a person/organisation ZSR is attached to the Organization (INFORMATION `DERIVED_VALUE`) |
| M-RX-03 | each `Medicaments` item | one MedicationRequest `ch-emed-medicationrequest`: status `active`, intent `order`, `authoredOn` = `Dt`, `requester` = author role, contained Medication (M-MED-01..04), identifier `urn:uuid:<id of path/identifier>` | `TkgRsn` → `reasonCode.text`; `PrscbBy` → WARNING `UNMAPPABLE_ELEMENT` |
| M-RX-04 | `Pos[0]` (`DtTo`, `D`), `Unit`, `AppInstr` | `dosageInstruction` as M-DOS-01..08 (no `DtFrom`, the period has only an end) | no `Pos` → dosage text "dosage not given" (the profile requires a dosage; INFORMATION `DERIVED_VALUE`) |
| M-RX-05 | `NbPack` | `dispenseRequest.quantity` (value, SNOMED `1681000175101` "Package - unit of product usage") | absent → absent, the default 1 is not materialised (domain review) |
| M-RX-06 | `Rep` (months) | `dispenseRequest.validityPeriod`: start = date of `Dt`, end = start + `Rep` months − 1 day (inclusive end as in the official example) | `Rep` 0 or absent → none (domain review) |
| M-RX-07 | `Subs` | `substitution.allowedCodeableConcept` `v3-substanceAdminSubstitution`: 1 → `N` (none), 0 → `E` (equivalent) | absent → no `substitution`, the default 0 is not materialised (domain review) |
| M-RX-08 | `IndC` | `reasonCode.text` `SL indication code <IndC>` | the pinned packages have no code system for the SL indication code → WARNING `UNMAPPABLE_ELEMENT` (no clinical loss, carried as text; domain review) |
| M-RX-09 | `/Patient/Rcv` (GLN) | `dispenseRequest.performer` as logical reference (`identifier` system `urn:oid:2.51.1.3`) | the receiver is not described further in CHMED16A (domain review) |

### CH EMED → CHMED16A

Input document type LOINC `57833-6`. The patient is mapped as in M-REV-06..M-REV-09 without `Lng`;
`rev` is always 3; `ValBy`, `ValDt`, `Patient.Med` are never produced. Fields the prescription does
not define (`TkgRsn`, `Roa`, `AutoMed`, `PrscbBy`, `DtFrom`, `TT`, `InRes`, `CyDu`) are not written.

| Id | CH EMED | CHMED16A | Notes |
|----|---------|----------|-------|
| M-REV-30 | `Composition.type` LOINC `57833-6` | `/MedType` 3, `/rev` 3, `Id`/`Dt` as M-REV-02/03 | `Id` is written in lower case (eRezept schema); a changed value → INFORMATION `DERIVED_VALUE` |
| M-REV-31 | `Composition.author` → PractitionerRole → Practitioner/Organization | `/Auth` = `HcPerson.Gln`, `/HcPerson` (`Gln`, `FName`, `LName`, `Zsr`), `/HcOrg` (`Name`, `NameAffix` ← `alias[0]`, `Street`, `Zip`, `City`, `Country`, `Zsr`, `Phone`, `Email`), `/Zsr` = the ZSR of person or organisation | prescriber without GLN, no author or the patient as author → `EREZEPT_REQUIREMENT` ERROR (FR-008b); missing names or organisation address → `MISSING_REQUIRED_DATA`; ZSR on both person and organisation → the person's is kept, INFORMATION for the other |
| M-REV-32 | `MedicationRequest.status` | included or excluded | `active`, `completed` → included; `draft`, `on-hold`, `unknown` → included, WARNING `UNMAPPABLE_ELEMENT` with clinical loss; `stopped`, `cancelled` → excluded, WARNING with clinical loss; `entered-in-error` → excluded, INFORMATION |
| M-REV-33 | contained or bundled `Medication.code` | `Id` + `IdType` | as M-REV-11 and M-REV-24 |
| M-REV-34 | `dosageInstruction` | `Pos[0]` `{DtTo, D}` + `Unit`, `AppInstr` | as M-REV-14..M-REV-21, but without `DtFrom`: a start date equal to the date of `Dt` → INFORMATION `UNMAPPABLE_ELEMENT`, another start date → WARNING with clinical loss; time of day, as needed, several periods and everything else the prescription cannot express → text in `AppInstr` (M-REV-19, dates as `from=`/`to=`) |
| M-REV-35 | `dispenseRequest.quantity` (SNOMED `1681000175101`) | `NbPack` | other unit or a non-integer value → WARNING with clinical loss |
| M-REV-36 | `dispenseRequest.validityPeriod` | `Rep` | only if the end equals start + n months − 1 day with n = 1..24, otherwise WARNING with clinical loss; `numberOfRepeatsAllowed` has no counterpart → WARNING with clinical loss |
| M-REV-37 | `substitution` | `Subs` | `N` or `allowedBoolean` false → 1; `E` or `allowedBoolean` true → 0; any other code → 1 (the safer reading) and WARNING with clinical loss |
| M-REV-38 | `reasonCode.text` `SL indication code <00000.00>` | `IndC` | inverse of M-RX-08; every other reason → WARNING `UNMAPPABLE_ELEMENT` with clinical loss (a prescription has no treatment reason) |
| M-REV-39 | `dispenseRequest.performer` (GLN identifier or a resolvable resource with GLN) | `/Patient/Rcv` | several different receivers → the first, INFORMATION for the others |
| M-REV-40 | route, patient instruction elements without counterpart | — | route → WARNING `UNMAPPABLE_ELEMENT` with clinical loss |

## eRezept readiness

Applied to every CHMED16A prescription produced (violation → `EREZEPT_REQUIREMENT` ERROR naming the
path, FR-008b) and reported for CHMED16A prescriptions read as input (WARNING, phase
`VALIDATE_INPUT`). The rules follow the eRezept Schweiz documentation 0.10.2 (`rezdat.html`,
`precon.html`) and its published JSON schema `schema/schemav7.json` (a copy is used as test
cross-check, `format-chmed16a/src/test/resources/schemas/`).

| Id | Rule |
|----|------|
| M-ERX-01 | `MedType` 3; `Id` a lower-case UUID (schema pattern); `Dt` an ISO 8601 date-time with offset. |
| M-ERX-02 | `Auth` is a 13-digit GLN and equals `HcPerson.Gln`; `HcPerson` (`Gln` 13 digits, `FName`, `LName`) and `HcOrg` (`Name`, `Street`, `Zip`, `City`) are present (revision 3 on output); `Patient.FName`, `LName`, `BDt` present. |
| M-ERX-03 | At least one medicament; `IdType` is 1, 2 or 3 — product numbers (`IdType` 4) are not allowed. **Finding (T094):** the published schema accepts free-text medicaments (`IdType` 1); the documentation states that the service does not validate GTIN/Pharmacode, so the correctness of the identifier is the sender's responsibility. |
| M-ERX-04 | `Rep` 0..24; `Subs` 0 or 1; `IndC` `00000.00`; `Pos` only with `Unit` and with `D` (1..4 numbers); `Patient.Gender` 1 or 2; `Patient.Zip` and `HcOrg.Zip` 4 or 5 digits; `Zsr` at most 7 characters. |
| M-ERX-05 | Only the fields of the prescription specification are written (the schema forbids additional root properties). |

## Reason code catalogue

| Code | Severity | Meaning |
|------|----------|---------|
| `PARSE_ERROR` | ERROR | input is not parseable (JSON, QR payload, URL) |
| `UNSUPPORTED_VERSION` | ERROR | input is another CHMED version |
| `UNSUPPORTED_DOCUMENT_TYPE` | ERROR | document type not supported |
| `UNSUPPORTED_CONVERSION` | ERROR | source/target pair not supported |
| `CHMED_INVALID_FIELD` | ERROR/WARNING | field violates the CHMED16A specification |
| `CHMED_REVISION_FIELD_MISSING` | WARNING | field required only by a later revision is missing |
| `UNKNOWN_FIELD_IGNORED` | INFORMATION | unknown field or `PFields` ignored |
| `MISSING_REQUIRED_DATA` | ERROR | data required by the target format is missing |
| `UNMAPPABLE_ELEMENT` | WARNING/INFORMATION | element cannot be represented in the target |
| `DOSAGE_FREE_TEXT_FALLBACK` | WARNING | posology carried as free text |
| `DERIVED_VALUE` | INFORMATION | value derived or defaulted (language, confidentiality, code text, dosage note) |
| `PDF_CHARACTER_REPLACED` | INFORMATION | characters replaced in the original-representation PDF only |
| `FHIR_VALIDATION` | per validator | CH EMED validation message (terminology limitations as INFORMATION) |
| `EREZEPT_REQUIREMENT` | ERROR / WARNING | produced prescription violates eRezept data requirements (ERROR); a prescription read as input does (WARNING) |

## Capability gaps

### CHMED16A → CH EMED

| Gap | Rule | Clinical loss |
|-----|------|---------------|
| Medical data (weight, height, risks, menstruation, gestation) | M-MDT-01 | yes |
| Prescriber per medicament (`PrscbBy`) | M-MED-07 | no |
| Validation (`ValBy`, `ValDt`) | M-DOC-07 | no |
| Pharmacode / product number not coded | M-MED-03, M-MED-04 | no (carried as text) |
| Complex taking times, non-daily cycles | M-DOS-07 | yes (carried as text) |
| Routes and units without confirmed mapping | M-DOS-04, M-DOS-08 | yes |
| Private fields, unknown fields | M-IN-06 | no |
| Unit without posology | M-MED-09 | no |
| Indication code `IndC` has no code system in the pinned packages (carried as text) | M-RX-08 | no |
| Prescription `PrscbBy` of a medicament | M-RX-03 | no |

### CH EMED → CHMED16A

| Gap | Rule | Clinical loss |
|-----|------|---------------|
| Stopped, not-taken and on-hold medications | M-REV-10 | yes |
| Dosage constructs CHMED16A cannot express (day of week, ranges, offsets, other periods, text only) | M-REV-19 | yes (carried as text) |
| Routes other than oral, units without mapping | M-REV-15, M-REV-16 | yes |
| Medication details (form, amount, ingredient) of free-text products, statement/request notes | M-REV-24 | yes |
| Time of day of start/end date | M-REV-17 | no |
| Patient identifiers other than VeKa, gender other/unknown | M-REV-09, M-REV-06 | no |
| Original representation, confidentiality, treatment plan ids, further authors | M-REV-22, M-REV-23, M-REV-24, M-REV-04 | no |
| Prescription: route, treatment reason | M-REV-34, M-REV-38, M-REV-40 | yes |
| Prescription: start date different from the prescription date | M-REV-34 | yes |
| Prescription: as needed, time of day, several periods (carried as text) | M-REV-34, M-REV-19 | yes |
| Prescription: quantity in another unit than packages, validity not in whole months, `numberOfRepeatsAllowed` | M-REV-35, M-REV-36 | yes |
| Prescription: specific substitution codes other than `N`/`E` (read as "do not substitute") | M-REV-37 | yes |
| Prescription: start date equal to the prescription date, organisation GLN, ZSR on person and organisation, further receivers | M-REV-34, M-REV-31, M-REV-39 | no |

## Validation limitations

Validation runs offline without a terminology server (research R4). Membership of codes in large
external code systems (e.g. SNOMED CT) cannot be fully checked; the validator reports these as
warnings or information. Codes are carried unchanged (FR-012).

Validator WARNINGs with the following message ids are caused solely by this limitation and are
reported as INFORMATION (ERRORs are never downgraded):

| Message id | Cause |
|------------|-------|
| `Terminology_PassThrough_TX_Message` | code system (e.g. LOINC, GTIN `urn:oid:2.51.1.1`) not available offline |
| `TERMINOLOGY_TX_SYSTEM_UNSUPPORTED` | SNOMED CT not available offline |
| `Terminology_TX_NoValid_2_CC` | value set cannot be expanded offline (extensible/preferred binding) |

## Logging (privacy)

The converter logs only phase, outcome, entry counts, duration and input size (constitution IV,
data-model.md §5); reports and log messages never contain patient values, raw QR payloads or eRezept
signature parameters (`PhiLeakTest` scans the whole fixture corpus in both directions). The
third-party libraries HAPI FHIR and the HL7 validator write resource content at DEBUG/TRACE level:
operators MUST NOT enable DEBUG for `org.hl7.fhir` or `ca.uhn.fhir`. The shipped CLI configuration
keeps both at WARN.
