# Data Model: CHMED16A ↔ CH EMED Conversion

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

Field-level mapping rules live in `docs/mapping/chmed16a-ch-emed.md` (R6); this document defines
the entities, their fields, validation rules and state.

## 1. Conversion API entities (converter-core)

### FormatId

Enumerated identifier of a supported format: `CH_EMED`, `CHMED16A` (later: `CHMED23A`,
`CARA_EMED`, `EMEDIPLAN_QR`, `EREZEPT`).

### ConversionRequest

| Field | Type | Rules |
|-------|------|-------|
| source | FormatId | required |
| target | FormatId | required; exactly one of source/target is `CH_EMED` or the pair is composed via CH EMED (Principle V) |
| input | byte[] (UTF-8: JSON, or textual CHMED16A QR payload when source = `CHMED16A`) | required, non-empty, ≤ configurable max size |
| options | ConversionOptions | defaults apply |

### ConversionOptions

| Field | Type | Default | Rules |
|-------|------|---------|-------|
| strict | boolean | false | true → any `clinicalLoss` entry makes outcome `FAILED` (FR-016) |
| validateOutput | boolean | true | may be false only via library API; CLI always validates |
| idStrategy | IdStrategy | UUID v5 from source id + path | must be deterministic (R8) |
| clock | Clock | none | only used if a target timestamp cannot be taken from the source; if absent and needed → error |
| maxDecodedSize | long (bytes) | 5 MB | limit for the decompressed CHMED16A QR payload; exceeded → `PARSE_ERROR` |
| defaultLanguage | string (BCP 47) | `de-CH` | used for `Composition.language` when `Patient.Lng` is absent (FR-022); one of `de-CH`, `fr-CH`, `it-CH`, `en-CH` |
| confidentiality | enum `NORMAL` / `RESTRICTED` / `SECRET` | `NORMAL` | CH EMED document confidentiality (FR-022) |
| patientIdentifier | optional `(system, value)` | none | used only if the source has no patient identifier (FR-023); system is a URI, e.g. `urn:oid:…` |
| author | optional `AuthorDetails` (practitioner GLN, given, family; organisation name, street, zip, city, country) | none | used only if the source lacks author details required by CH EMED (FR-023); practitioner GLN defaults to CHMED `Auth` when that is a GLN |

### ConversionResult

| Field | Type | Rules |
|-------|------|-------|
| outcome | Outcome | `SUCCESS`, `SUCCESS_WITH_WARNINGS`, `FAILED` |
| output | byte[] (UTF-8 JSON) or empty | present iff outcome ≠ `FAILED` (FR-015) |
| report | ConversionReport | always present |

**Outcome state rules**

```text
any ERROR entry                                    → FAILED
strict && any entry with clinicalLoss = true       → FAILED
any WARNING entry                                  → SUCCESS_WITH_WARNINGS
otherwise (INFORMATION only or none)               → SUCCESS
```

### ConversionReport

| Field | Type | Rules |
|-------|------|-------|
| converterVersion | string | semantic version of the converter |
| source / target | FormatId | |
| specifications | map FormatId → pinned version | from `docs/specifications.md` (R2) |
| outcome | Outcome | as above |
| entries | list of ReportEntry | ordered by phase (parse, validate-input, map, validate-output), then path |

### ReportEntry

| Field | Type | Rules |
|-------|------|-------|
| severity | `ERROR` / `WARNING` / `INFORMATION` | |
| code | ReasonCode (string, e.g. `CHMED_INVALID_FIELD`, `UNMAPPABLE_ELEMENT`, `DOSAGE_FREE_TEXT_FALLBACK`, `UNSUPPORTED_DOCUMENT_TYPE`, `UNSUPPORTED_VERSION`, `UNSUPPORTED_CONVERSION`, `PARSE_ERROR`, `MISSING_REQUIRED_DATA`, `UNKNOWN_FIELD_IGNORED`, `CHMED_REVISION_FIELD_MISSING`, `DERIVED_VALUE`, `PDF_CHARACTER_REPLACED`, `FHIR_VALIDATION`, `EREZEPT_REQUIREMENT`) | stable, documented in mapping spec |
| phase | `PARSE` / `VALIDATE_INPUT` / `MAP` / `VALIDATE_OUTPUT` | |
| path | string | source path (JSON Pointer for CHMED16A, FHIRPath for CH EMED); target path optional |
| message | string | human-readable; MUST NOT contain values of patient fields (FR-017) |
| clinicalLoss | boolean | true if clinically relevant information is not represented exactly |

JSON form: [contracts/conversion-report.schema.json](./contracts/conversion-report.schema.json).

## 2. CHMED16A model (format-chmed16a)

Field names as in the specification (PascalCase JSON keys). Revision-specific rules per R5.

**Input forms**: plain JSON (starts with `{`) or textual QR payload (prefix `CHMED16A`, compression
flag, Base64-encoded GZip JSON per ChTransmissionFormat), detected automatically (FR-001). A
corrupt payload → `PARSE_ERROR`; the payload is never echoed in messages or logs. Signed eRezept
URLs and QR images are out of scope (QR/eRezept feature).

**Revisions**: medication plans carry no revision marker and are validated against the pinned
README. Prescriptions carry `rev` (absent = revision 1; pinned specification = revision 3): input
is validated against its declared revision; a field required only by a later revision and missing
→ WARNING `CHMED_REVISION_FIELD_MISSING` (not ERROR); `rev` above 3 → accepted, unknown fields
ignored. Output always conforms to the latest revision (prescriptions: `rev` = 3).

### Medication (root)

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| Patient | Patient | yes | |
| Medicaments | list Medicament | no (0..n) | |
| Recoms | list | no | only valid for MedType 2 → MedType 2 rejected |
| MedType | int | yes | 1 = MedicationPlan, 3 = Prescription; 2 → `UNSUPPORTED_DOCUMENT_TYPE` |
| Id | string | yes | UUID |
| Auth | string | yes | prescription: GLN of a person (13 digits) |
| Zsr | string | no | 7 chars |
| Dt | date-time | yes | ISO 8601 |
| Rmk | string | no | |
| ValBy / ValDt | string / date-time | no | |
| rev | int | prescription: yes since revision 2 | absent = 1; output always 3 |
| HcPerson / HcOrg | object | prescription: yes since revision 2 | GLN, name, address; missing on input with `rev` ≥ 2 → ERROR; with `rev` absent/1 → WARNING `CHMED_REVISION_FIELD_MISSING`; missing on **output** → ERROR (latest revision + FR-008b) |
| PFields | list PField | no | ignored, reported as `UNKNOWN_FIELD_IGNORED` |

### Patient

FName, LName, BDt (required); Gender (1 male, 2 female); Street, Zip, City; Lng (ISO 639-1);
Phone, Email; Rcv; Ids (list PatientId: Type, Val); Med (MedicalData); PFields.

### MedicalData

DLstMen (date), Prem (0/1), ToG (weeks), Rc (list RiskCategory: Id, list RiskItem), Meas (list
Measurement: Type, Val, Unit), PFields.

### Medicament

| Field | Required | Validation |
|-------|----------|------------|
| Id | yes | GTIN: 13 digits with valid check digit (invalid → WARNING, carried as is); Pharmacode: up to 7 digits (leading zeros may be omitted, e.g. the official example uses 6 digits) |
| IdType | yes | 1 free text, 2 GTIN, 3 Pharmacode, 4 product number (not allowed in prescriptions) |
| Pos | no | list Posology |
| Unit, TkgRsn, AppInstr, Roa | no | |
| AutoMed | no | 0/1 |
| PrscbBy | no | |
| Rep | no | prescription only; ≤ 2 digits (months) |
| Subs | no | prescription only; 0/1 (1 = must not be substituted) |
| NbPack | no | prescription only; ≤ 10 digits; default 1 per spec (default is **not** materialised on output) |
| IndC | no | prescription only; format `00000.00` |
| PFields | no | ignored, reported |

### Posology

| Field | Required | Validation |
|-------|----------|------------|
| DtFrom | yes | date |
| DtTo | no | date ≥ DtFrom |
| CyDu | no | seconds; default 86 400 (not materialised on output) |
| InRes | no | 0/1 (reserve / as needed) |
| D | no | 0..4 numbers [morning, noon, evening, night]; ignored if TT is specified (WARNING when both are present) |
| TT | no | list TakingTime (Off seconds, Du, A dose, …) |

## 3. CH EMED side (HAPI R4 model)

No own classes: HAPI `Bundle` (type `document`) with `Composition` conforming to CH EMED 6.0.0
Medication Card or Medication Prescription document profiles, plus `Patient`, `Practitioner`,
`PractitionerRole`, `Organization`, `Medication`, `MedicationStatement` (card) /
`MedicationRequest` (prescription), `Observation` where used for medical data.

**Document metadata (FR-022)**: `Composition.language` from `Patient.Lng` (`de`/`fr`/`it`/`en` →
`de-CH`/`fr-CH`/`it-CH`/`en-CH`) or `ConversionOptions.defaultLanguage`; `Composition.title` in that
language (`Medikationsplan`, `Plan de médication`, `Piano farmacologico`, `Medication Plan`; for
prescriptions the corresponding prescription titles); `confidentiality` from
`ConversionOptions.confidentiality` with the CH Core EPR confidentiality extension. Each derived or
defaulted value → INFORMATION entry `DERIVED_VALUE`.

**Original representation (FR-021)**: section `originalRepresentation` (LOINC 55108-5) with a
`Binary` (`application/pdf`) rendered by `OriginalRepresentationRenderer` (converter-core) from the
finished Bundle: patient, author, date, every medication with dosage text and instructions.
Deterministic: fixed PDF document id derived from the Bundle identifier, creation date = document
date, no producer timestamps; standard font Helvetica (WinAnsi), unencodable characters replaced
in the PDF only → INFORMATION `PDF_CHARACTER_REPLACED`.

Validation: `converter-validation` (R4). Supported input document types are detected from
`Composition.type`; others → `UNSUPPORTED_DOCUMENT_TYPE` (FR-007).

## 4. eRezept readiness rules (prescriptions, FR-008b)

Applied to every CHMED16A prescription produced (and reported for CHMED16A input):

- MedType = 3, `Id` UUID, `Dt` present.
- `Auth` = GLN of the prescribing person; `HcPerson` and `HcOrg` present.
- No Medicament has `IdType` 4 (product number); whether free text (`IdType` 1) is accepted by
  eRezept is verified against the pinned eRezept documentation when writing the mapping spec.
- Strict CHMED16A conformance (no extra fields except `PFields`).
- Violations on output → `EREZEPT_REQUIREMENT` ERROR naming the missing data (by path).

## 5. Logging rules

Libraries log via SLF4J only: phase, outcome, entry counts, duration, input size. Never input or
output content, patient fields, report messages containing values, or eRezept signature data.
