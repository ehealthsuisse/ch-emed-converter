# Feature Specification: CHMED16A ↔ CH EMED Conversion

**Feature Branch**: `001-chmed16a-ch-emed-conversion`

**Created**: 2026-10-18

**Status**: Draft

**Input**: User description: "CHMED23A ↔ CH EMED Konvertierung"; re-scoped to CHMED16A on
2026-10-18 (see Clarifications).

## Clarifications

### Session 2026-10-18

- Q: Is the CHMED16A prescription in scope, or only the medication plan? → A: Both: medication
  plan ↔ MedicationCard and prescription ↔ MedicationPrescription, in both directions.
- Q: Which CH EMED profile set is the target? → A: The general CH EMED IG (not CH EMED EPR).
- Q: CHMED23A was withdrawn by IG eMediplan (development stopped, move to CH EMED); which format
  first? → A: Re-scope this feature to CHMED16A, the widely used format that eMediplan and
  eRezept Schweiz are based on; CHMED23A stays in the constitution and follows later.
- Q: How far does eRezept Schweiz support go? → A: Read and prepare only: CHMED16A
  prescriptions produced here MUST be signable by the HIN erxproxy unchanged; reading signed
  eRezept QR codes/URLs is part of the QR/eRezept feature; no signing or status checks.

### Session 2026-10-18 (implementation)

- Q: CH EMED 6.0.0 requires an "original representation" PDF (Binary, 1..1) in every document;
  CHMED16A has none. Where does it come from? → A: The converter renders a plain, deterministic
  PDF of the document from the converted data.
- Q: `Composition.language` and `confidentiality` are mandatory; CHMED16A has only the optional
  `Patient.Lng` and no confidentiality. → A: Derive the language from `Patient.Lng`, otherwise
  use a converter option (default `de-CH`); confidentiality from an option (default normal);
  every derived or defaulted value is reported as INFORMATION.
- Q: CH Core requires `Patient.identifier` (1..*); CHMED16A plans often have no `Ids`. → A: The
  caller supplies a patient identifier (system + value) as option; without CHMED `Ids` and
  without the option the conversion fails with `MISSING_REQUIRED_DATA`.
- Q: CH EMED requires a PractitionerRole author (Practitioner with GLN and name, Organization with
  address) or a Patient/Device; CHMED16A plans only carry `Auth`. → A: The caller supplies author
  details (practitioner name/GLN, organisation name/address) as option; without them (and unless
  `Auth` is "patient" or a prescription carries `HcPerson`/`HcOrg`) the conversion fails with
  `MISSING_REQUIRED_DATA`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Convert a CHMED16A medication plan to CH EMED (Priority: P1)

A primary system (e.g. a practice software, pharmacy system or EPR integration) holds an
eMediplan in CHMED16A format and needs the same medication information as a CH EMED document,
for example to publish it to the patient's electronic patient record (EPR/EPD). The user hands
the CHMED16A document to the converter and receives a conformant CH EMED document plus a
conversion report listing everything that could not be carried over completely.

**Why this priority**: Bringing existing eMediplan data into the CH EMED ecosystem is the main
reason for the converter; without this direction no other story delivers value.

**Independent Test**: Convert the official CHMED16A example documents; every output validates
without errors against the targeted CH EMED profiles, and every source medication, dosage and
note is either present in the output or listed in the conversion report.

**Acceptance Scenarios**:

1. **Given** a valid CHMED16A medication plan with coded medications (GTIN or Pharmacode) and
   structured dosages, **When** it is converted, **Then** the result is a CH EMED document of
   the corresponding document type that validates without errors and contains every
   medication with the same product identification, dosage, timing, route, reason and
   application instructions.
2. **Given** a CHMED16A medication plan containing an element that CH EMED cannot represent,
   **When** it is converted, **Then** a valid CH EMED document is still produced (default mode)
   and the conversion report contains one entry for that element with its source path, the
   reason, and a severity.
3. **Given** a document that is not valid CHMED16A (malformed, schema violations, or a different
   version such as CHMED23A), **When** conversion is requested, **Then** no output document is
   produced and the user receives error entries that identify the problem and its location.

---

### User Story 2 - Convert a CH EMED document to CHMED16A (Priority: P2)

A system holds medication information as a CH EMED document (e.g. retrieved from the EPR) and
needs an eMediplan in CHMED16A format, for example to print the patient's medication plan or to
hand it to software that only understands eMediplan. The user hands the CH EMED document to the
converter and receives a conformant CHMED16A document plus a conversion report.

**Why this priority**: Completes the bidirectional requirement of the constitution and enables
eMediplan-based workflows (printing, QR code in a later feature) from EPR data; it depends on the
same mapping knowledge as Story 1 and is therefore second.

**Independent Test**: Convert the official CH EMED example documents of the supported document
types; every output conforms to the CHMED16A specification and every source medication is
either present or reported.

**Acceptance Scenarios**:

1. **Given** a valid CH EMED document of a supported document type, **When** it is converted,
   **Then** the result conforms to the CHMED16A specification and contains every active
   medication with its identification, dosage, timing, route, reason and instructions.
2. **Given** a CH EMED document using dosage or timing constructs that CHMED16A cannot express
   exactly, **When** it is converted, **Then** the closest representation allowed by the mapping
   specification is produced (never a clinically different dosage) and a report entry
   documents the loss; if no faithful representation exists the dosage is carried as free text
   and reported.
3. **Given** a CH EMED document of a document type outside the supported set, **When** conversion
   is requested, **Then** conversion is refused with an error that names the unsupported type.

---

### User Story 3 - Convert prescriptions in both directions (Priority: P3)

A prescriber's system issues an eMediplan prescription in CHMED16A format, and the
receiving side (e.g. an EPR integration or a pharmacy system) needs it as a CH EMED
MedicationPrescription document, or vice versa. The user converts the prescription in either
direction and receives a conformant document plus a conversion report.

**Why this priority**: Prescriptions are the second CHMED16A document type and needed for complete
CHMED16A coverage; they reuse the medication and dosage mapping of Stories 1 and 2 and add
prescription-specific content, so they follow those stories.

**Independent Test**: Convert the official CHMED16A prescription examples to CH EMED and the
official CH EMED MedicationPrescription examples to CHMED16A; all outputs pass the respective
conformance checks and every prescribed item is present or reported.

**Acceptance Scenarios**:

1. **Given** a valid CHMED16A prescription, **When** it is converted, **Then** the result is a
   CH EMED MedicationPrescription document that validates without errors and contains every
   prescribed medication with its dosage, prescribed quantity or duration, repeats/validity,
   substitution rule and prescriber.
2. **Given** a valid CH EMED MedicationPrescription document, **When** it is converted, **Then**
   the result conforms to the CHMED16A prescription specification and contains every prescribed
   medication with the same prescription-specific information, or a report entry for each
   element that cannot be represented.
3. **Given** a CHMED16A document, **When** it is converted, **Then** the document type of the
   output (MedicationCard or MedicationPrescription) is determined by the document type of the
   input, never by a guess.

---

### User Story 4 - Control lossy conversions via the conversion report (Priority: P4)

An integrator wants to decide automatically whether a lossy result is acceptable. They read the
machine-readable conversion report and can request a strict mode in which any loss of
clinically relevant information causes the conversion to fail instead of returning a document.

**Why this priority**: The report is already produced by Stories 1–3; strict mode and
automated evaluation add safety for unattended integrations but are not needed for a first
usable version.

**Independent Test**: Convert fixtures with known capability gaps in default and in strict mode
and compare the report entries and the outcome (document vs. failure) with the expected results.

**Acceptance Scenarios**:

1. **Given** an input that converts with warnings only, **When** strict mode is off, **Then** the
   output document is returned together with the report and the outcome is "success with
   warnings".
2. **Given** an input whose conversion loses clinically relevant information, **When** strict
   mode is on, **Then** no output document is returned and the outcome is "failed", with the
   report naming every element that caused the failure.
3. **Given** any conversion, **When** the report is inspected, **Then** it contains no
   patient-identifying data, only element paths, codes, reasons and severities.

---

### User Story 5 - Round-trip fidelity (Priority: P5)

A quality engineer or domain reviewer wants evidence that converting CHMED16A → CH EMED →
CHMED16A (and CH EMED → CHMED16A → CH EMED) preserves, for medication plans and
prescriptions alike, the clinical content. They run the round
trip on the reference corpus and see that every difference is a documented capability gap.

**Why this priority**: Builds trust in the mapping and is required by the constitution's test
principle, but it is verification of Stories 1–3 rather than new user-facing capability.

**Independent Test**: Run the round trip on all reference fixtures and compare start and end
documents; the only differences are those listed in the mapping specification's capability gaps.

**Acceptance Scenarios**:

1. **Given** an official CHMED16A example (medication plan or prescription), **When** it is converted to CH EMED and back,
   **Then** the resulting CHMED16A document is clinically equivalent to the original, and every
   difference is listed as a documented capability gap.

---

### Edge Cases

- Input is empty, not JSON, or truncated → error, no output.
- Input is CHMED23A or another CHMED version → error stating the detected version; CHMED23A is
  handled by a separate feature.
- Input is a CHMED16A QR payload (`CHMED16A1…`) → decoded and converted like plain JSON; a
  corrupt payload (bad Base64, bad GZip, unknown prefix) → `PARSE_ERROR`, the payload itself is
  never echoed in the report or logs.
- Input is a signed eRezept URL (`https://eprescription.hin.ch/#CHMED16A1…`) or a QR image →
  error pointing to the QR/eRezept feature.
- CHMED16A document of type PolymedicationCheck → refused with a specific "document type not
  supported" error.
- Unknown JSON fields and private fields (`PFields`) → ignored for the mapping as required by the
  CHMED16A specification, and listed as information entries in the report.
- CHMED16A prescription of an older revision (`rev` absent or 1) lacking fields that later
  revisions require (e.g. `HcPerson`/`HcOrg`, required since revision 2) → accepted; each missing
  field is reported as a WARNING (`CHMED_REVISION_FIELD_MISSING`). CHMED16A output always conforms
  to the latest revision.
- Medication plan with an empty medication list → a valid document with no medication entries,
  if both specifications allow it; otherwise an error.
- Medication identified only by free text (no GTIN/Pharmacode) → carried as a non-coded product
  name, never matched to a code by guessing.
- Unknown or invalid codes (e.g. a GTIN with an invalid check digit, an unknown route code) →
  carried as given and reported as a warning; not silently corrected.
- Missing data that the target format requires (e.g. patient birth date, author) → error; no
  placeholder values are invented.
- Dosage with ranges, tapering schedules, cyclic or "as needed" (PRN) intake → mapped if both
  formats support it, otherwise free text plus report entry.
- Stopped, paused or inactive medications in CH EMED → handled according to the mapping
  specification (included with status, or excluded and reported), never silently dropped.
- Dates without time vs. date-times with time zones → precision and time zone preserved; no
  shifting of the calendar date.
- Patient-entered information (self-medication) and patient medical data (weight, allergies,
  risk factors) → mapped where the target format supports it, otherwise reported.
- Non-ASCII text (umlauts, accents, Italian/French content) → preserved unchanged.
- Prescription without quantity, duration or validity → mapped as given if the target allows it,
  otherwise an error; no default quantity or validity is assumed.
- Substitution rule or repeat count not representable in the target → reported, never dropped.
- CH EMED document conforming only to CH EMED EPR-specific constraints → accepted if it also
  conforms to CH EMED; EPR-specific elements not covered by CH EMED are reported.
- Characters that the PDF font cannot render → replaced in the PDF only (structured data stays
  unchanged) and reported as INFORMATION.
- Very large plans (e.g. 100+ medications) → converted completely within the performance target.

## Requirements *(mandatory)*

### Functional Requirements

**Input handling**

- **FR-001**: The system MUST accept a CHMED16A document as input for the CHMED16A → CH EMED
  direction both in its plain JSON form and in its textual QR payload form (prefix `CHMED16A`
  followed by the compression flag and the Base64-encoded, GZip-compressed JSON as defined by the
  ChTransmissionFormat); the form is detected automatically.
- **FR-002**: The system MUST accept a CH EMED document in FHIR JSON form as input for the
  CH EMED → CHMED16A direction.
- **FR-003**: The system MUST validate every input against its format specification before
  converting and MUST reject invalid input with error entries that state the problem and the
  location in the input.
- **FR-004**: The system MUST detect and reject a CHMED version other than CHMED16A with a
  specific error naming the detected version, and MUST reject the CHMED16A document type
  PolymedicationCheck with a "document type not supported" error.

**Conversion scope**

- **FR-005**: The system MUST convert, in both directions, CHMED16A medication plans ↔ CH EMED
  MedicationCard documents and CHMED16A prescriptions ↔ CH EMED MedicationPrescription
  documents; the output document type MUST follow from the input document type.
- **FR-006**: The system MUST produce CH EMED output that validates against the CH EMED profiles
  (the general CH EMED IG, not CH EMED EPR) and MUST accept CH EMED input conforming to them.
- **FR-007**: The system MUST refuse conversion of CH EMED document types outside the supported
  set with an error naming the type.

**Content mapping**

- **FR-008**: The system MUST map patient demographics, document author/organisation, document
  date, and every medication entry including product identification (GTIN, Pharmacode or free
  text), dosage and timing, route, start and end dates, reason for treatment, application
  instructions, prescriber, and self-medication indication, in both directions.
- **FR-008a**: For prescriptions, the system MUST additionally map the number of packages,
  repetition period, substitution rule, indication code, intended receiver, prescriber and
  prescriber organisation identification (GLN, ZSR number), and prescription date, in both
  directions.
- **FR-008b**: CHMED16A prescriptions produced by the system MUST meet the data requirements of
  eRezept Schweiz so that a primary system can have them signed by the HIN erxproxy without
  modification; if the CH EMED input lacks data required for this (e.g. prescriber GLN), the
  conversion MUST fail with an error naming the missing data.
- **FR-009**: The system MUST map structured posology to structured dosage wherever both formats
  can express the same regimen, and MUST fall back to free-text dosage plus a report entry
  otherwise; it MUST NOT produce a dosage that is clinically different from the source.
- **FR-010**: The system MUST map notes and patient medical data (e.g. weight,
  allergies, risk factors) where the target format supports them and MUST report them otherwise.
- **FR-011**: The system MUST NOT invent values for clinically relevant fields; missing required
  data MUST result in an error, missing optional data MUST be left absent.
- **FR-012**: The system MUST carry codes unchanged and MUST NOT translate, correct or look up
  codes beyond the mappings defined in the mapping specification.
- **FR-013**: Every mapping rule applied by the system MUST be documented in the versioned
  mapping specification for this format pair, including the known capability gaps per direction.

**Conversion report and outcome**

- **FR-014**: Every conversion MUST produce a machine-readable conversion report listing each
  error and each element that could not be represented exactly, with source element path, reason
  code, human-readable message and severity (error, warning, information).
- **FR-015**: The conversion outcome MUST be one of "success", "success with warnings" or
  "failed", and failed conversions MUST NOT return an output document.
- **FR-016**: Users MUST be able to request strict mode, in which any loss of clinically relevant
  information results in the outcome "failed".
- **FR-017**: Reports, error messages and logs MUST NOT contain patient-identifying data.

**Determinism and operation**

- **FR-018**: Given identical input, options and specification versions, the system MUST produce
  byte-identical output; generated identifiers and timestamps MUST be derivable from input or
  supplied by the caller.
- **FR-019**: The system MUST perform conversions locally without sending any data to external
  services.
- **FR-020**: The conversion MUST be usable both as a reusable component and from the command
  line; the command line MUST return a non-zero exit status for failed conversions and MUST be
  able to emit the report in machine-readable form.
- **FR-021**: Every CH EMED document produced MUST contain the original representation required
  by CH EMED as a PDF rendered by the converter from the converted document content (patient,
  author, date, every medication with dosage and instructions); the PDF MUST be byte-identical for
  identical input and options. When converting CH EMED → CHMED16A, the original representation is
  not carried over and is reported as INFORMATION.
- **FR-023**: Callers MUST be able to supply a patient identifier (system, value) and author
  details (practitioner GLN, given and family name; organisation name, street, postcode, city,
  country) as conversion options. They are used only where the CHMED16A source lacks the data
  required by CH EMED (`Patient.Ids`; author details beyond `Auth`); if neither source nor options
  provide the data, the conversion MUST fail with `MISSING_REQUIRED_DATA` naming the missing data.
- **FR-022**: The system MUST set the mandatory CH EMED document language from `Patient.Lng`
  (`de`, `fr`, `it`, `en` → `de-CH`, `fr-CH`, `it-CH`, `en-CH`) or, if absent, from a caller
  option (default `de-CH`), and the document confidentiality from a caller option (default
  normal); each derived or defaulted value MUST be reported as an INFORMATION entry.

### Key Entities

- **CHMED16A Medication Plan**: eMediplan document containing patient, author, creation date,
  medication entries with posology, and optional patient medical data and remarks.
- **CHMED16A Prescription**: eMediplan prescription containing patient, prescriber person and
  organisation, date and prescribed medications with posology and prescription-specific
  information; basis of eRezept Schweiz.
- **CH EMED Document**: FHIR document of a supported CH EMED document type (MedicationCard or
  MedicationPrescription) containing patient, author, composition metadata and medication
  statements or medication requests with dosages.
- **Patient**: the person the medication information belongs to; demographics and identifiers.
- **Author / Organisation**: the healthcare professional, organisation or patient who created the
  document or entry.
- **Prescription Details**: number of packages, repetition period, substitution rule,
  indication code and receiver attached to a prescribed medication.
- **Medication Entry**: one medication with product identification, dosage/posology, route,
  period, reason, instructions, prescriber and self-medication indication.
- **Dosage / Posology**: the intake regimen (amounts, times of day, frequency, ranges, as-needed,
  free text).
- **Conversion Report**: outcome plus list of entries (path, reason code, message, severity).
- **Mapping Specification**: versioned document defining every mapping rule and capability gap
  between CHMED16A and CH EMED.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the official CHMED16A example documents (medication plans and
  prescriptions) convert to CH EMED documents that
  pass official CH EMED validation with zero errors.
- **SC-002**: 100% of the official CH EMED example documents of the supported document types
  (MedicationCard, MedicationPrescription) convert to CHMED16A documents that conform to the CHMED16A specification.
- **SC-003**: On the reference corpus, 100% of source medication entries are either present in
  the output or listed in the conversion report (zero silent losses).
- **SC-004**: Round trips of all reference fixtures show no differences other than documented
  capability gaps.
- **SC-005a**: The conversion of a medication plan with 100 medication entries (parsing, mapping,
  original-representation PDF, serialization) takes under 2 seconds on a standard workstation.
- **SC-005b**: The official validation of the CH EMED document produced from such a plan takes
  under 15 seconds with a warm validation context (measured about 10 s, linear in the number of
  entries); the cold start (loading the packages) is reported separately and is not part of
  SC-005a/b. (SC-005 was split on 2026-10-21 after measuring: the HL7 validator needs about 80 ms
  per MedicationStatement and neither the HL7 ValidationEngine nor parallel validation of
  sub-documents is fast enough for a joint limit of 2 seconds.)
- **SC-006**: An automated scan of reports and logs produced from the test corpus finds zero
  occurrences of patient-identifying data.
- **SC-007**: 100% of the CHMED16A prescriptions produced from the reference corpus meet the
  eRezept Schweiz data requirements (verified against the published eRezept examples and rules).
- **SC-008**: A domain reviewer can trace 100% of mapped elements in a sample conversion to a rule
  in the mapping specification.

## Assumptions

- The latest released versions of the CH EMED IG and of the eMediplan CHMED16A specification
  (including its prescription specification) at planning time are targeted; the exact versions
  and validation artefacts are pinned in the plan.
- The official CHMED20AF mapping (CHMED16A → FHIR, a superset of CH EMED) is used as the
  reference for mapping rules wherever it is consistent with CH EMED; deviations are documented
  in the mapping specification.
- Users are integrators and primary systems (practice, pharmacy, hospital, EPR integration), not
  patients; there is no graphical user interface in this feature.
- Decoding the textual CHMED16A QR payload is part of this feature (FR-001); QR images,
  producing QR payloads and signed eRezept URLs/QR codes are handled by the separate eMediplan QR
  code / eRezept feature, which builds on this one.
- CHMED23A conversion is a separate, later feature; the CHMED16A PolymedicationCheck document
  type is out of scope.
- Default mode returns lossy-but-valid results with warnings; strict mode is opt-in.
- Output targets the general CH EMED IG; CH EMED EPR is out of scope for this feature and may be
  added later as a separate feature.
- Access via REST service is provided by feature 002 (REST Conversion Service) on top of the
  same core; per constitution v1.3.0+ this feature is complete only once available there too.
- Only FHIR JSON is required for CH EMED in this feature; FHIR XML is out of scope.
- Test data is synthetic or officially published example data only.
