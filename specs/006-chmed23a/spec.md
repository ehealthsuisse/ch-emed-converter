# Feature Specification: CHMED23A ↔ CH EMED

**Feature Branch**: `006-chmed23a`

**Created**: 2026-11-12

**Status**: Implemented

**Input**: User description: "CHMED23A ↔ CH EMED: Der Converter soll eMediplan-Dokumente im Format CHMED23A in beide Richtungen mit CH EMED 6.0.0 umwandeln, gemäss Conversion Scope der Constitution 1.5.0 und analog zu CHMED16A (Features 001 und 003): Medikationsplan (medType 1) ↔ Medication Card und Rezept (medType 3) ↔ Medication Prescription; PolymedicationCheck (medType 2, auch in CHMED23A deprecated) wird abgelehnt. Quelle ist die veröffentlichte, eingefrorene Spezifikation im Repository ig-emediplan/specification (chmed23a/, chtransmissionformat/, gepinnter Commit); ein JSON-Schema gibt es nicht, die Konformität richtet sich nach Objektmodell, Posologie und Terminologie der Spezifikation. Die IG eMediplan hat CHMED23A eingestellt (Status Deprecated, Strategie 2.12.2025); das Format wird in Doku und Capabilities als deprecated ausgewiesen. Übertragungsformen: Klartext-JSON, QR-Payload im ChTransmissionFormat (Präfix CHMED23A., Base64 über GZip, inklusive gestückelter Payloads CHMED23A.1/4.…) und QR-Bild lesen und schreiben. Die erweiterte Posologie von CHMED23A wird so genau wie möglich in CH EMED abgebildet; was CH EMED oder CHMED23A nicht darstellen kann, wird im Report ausgewiesen, nie still verworfen. CHMED23A erreicht CH EMED EPR und CHMED16A nur über CH EMED (Hub). Verfügbar über Library, CLI und REST mit identischem Verhalten. Testkorpus: die Beispiele der Spezifikation (examples.md, Beispiele der .NET-Referenzimplementierung)."

## Clarifications

### Session 2026-11-12

- Q: CHMED23A is deprecated by IG eMediplan (development stopped, strategy 2025-12-02). Which scope? → A: Both directions, medication plan and prescription, like CHMED16A; the deprecated status is shown in documentation and capabilities.
- Q: Which transmission forms? → A: Plain JSON, QR payload in the ChTransmissionFormat (including chunked payloads) and QR image, read and write.
- Q: What happens when a document does not fit into one QR code? → A: As for CHMED16A: `QR_CAPACITY_EXCEEDED`, the payload stays available; chunked payloads are read, never written.
- Q: Is a QR image of a CHMED23A prescription allowed? → A: Yes; CHMED23A has no eRezept signature, so an image cannot be taken for a signed eRezept (unlike CHMED16A).
- Q: How do chunked payloads arrive? → A: As text only (several chunk payloads in one input); a QR image must hold the whole document, several codes in one image stay `QR_MULTIPLE`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read a CHMED23A medication plan (Priority: P1)

A primary system (practice software, pharmacy system) receives an eMediplan medication plan in the CHMED23A format, as JSON, as QR payload or as the QR code printed on the paper plan. It converts the plan to a CH EMED Medication Card to use it in the system, pass it on, or bring it into the EPR through the existing conversion to CH EMED EPR.

**Why this priority**: CHMED23A plans that exist in the field can otherwise not be read at all; reading them is the main reason to support a withdrawn format.

**Independent Test**: Convert the medication plan examples of the specification to CH EMED; the results validate against the pinned CH EMED profiles and keep every medication with its dosage, or report what cannot be kept.

**Acceptance Scenarios**:

1. **Given** a valid CHMED23A medication plan as JSON, **When** it is converted to CH EMED, **Then** the result is a valid Medication Card with the same patient, author, medications and dosages, and the report lists every element that was changed, derived or not represented.
2. **Given** the same plan as QR payload (prefix `CHMED23A.`), or split into several chunks (`CHMED23A.1/n.` … `CHMED23A.n/n.`), or as a QR image, **When** it is converted, **Then** the result equals the result from the plain JSON.
3. **Given** a plan whose dosage uses a CHMED23A posology that CH EMED can express (for example daily, interval, cyclic or sequential dosing), **When** it is converted, **Then** the dosage is represented in structured form; **Given** a posology CH EMED cannot express exactly, **Then** it is carried as dosage text and reported, with clinical loss where the structure is lost.
4. **Given** a document of type PolymedicationCheck (medType 2), **When** it is converted, **Then** the conversion fails as unsupported document type.
5. **Given** a document that does not conform to the CHMED23A specification, **When** it is converted, **Then** the conversion fails with findings that name the element paths, never the values.

---

### User Story 2 - Read a CHMED23A prescription (Priority: P1)

A pharmacy system receives a CHMED23A prescription and converts it to a CH EMED Medication Prescription.

**Why this priority**: Prescriptions are the second document type of CHMED23A; reading them completes the read direction.

**Independent Test**: Convert the prescription examples of the specification to CH EMED; the results validate, and the repetition rules (number, duration, both) are kept or reported.

**Acceptance Scenarios**:

1. **Given** a valid CHMED23A prescription, **When** it is converted to CH EMED, **Then** the result is a valid Medication Prescription with the same prescribed medications, dosages and repetitions, and the report lists what changed.
2. **Given** a prescription whose repetition (number, duration or both) cannot be represented exactly, **When** it is converted, **Then** the difference is reported as clinical loss.

---

### User Story 3 - Write CHMED23A from CH EMED (Priority: P2)

A system that holds CH EMED documents (for example read from the EPR) needs a CHMED23A plan or prescription for a partner that still processes CHMED23A, as JSON, QR payload or QR image.

**Why this priority**: Needed for round trips and for partners on CHMED23A; less important than reading because the format is withdrawn.

**Independent Test**: Convert the CH EMED documents of story 1 and 2 back to CHMED23A; the results conform to the specification, and the round trip CHMED23A → CH EMED → CHMED23A differs only by documented changes.

**Acceptance Scenarios**:

1. **Given** a CH EMED Medication Card, **When** it is converted to CHMED23A, **Then** the result is a CHMED23A medication plan conforming to the specification, and everything CHMED23A cannot express is reported.
2. **Given** a CH EMED Medication Prescription, **When** it is converted to CHMED23A, **Then** the result is a CHMED23A prescription conforming to the specification.
3. **Given** a document that fits into one QR code, **When** the output form QR payload or QR image is requested, **Then** the payload carries the prefix `CHMED23A.` and decodes to the same JSON; **Given** a document too large for one QR code, **Then** the QR image fails with `QR_CAPACITY_EXCEEDED` naming the limit, as for CHMED16A, and the QR payload stays available; chunked payloads are not written (clarification 2026-11-12).
4. **Given** a CH EMED prescription, **When** a QR image is requested, **Then** the QR image is written like that of a plan; CHMED23A has no eRezept signature (clarification 2026-11-12).

---

### User Story 4 - Same behaviour through all interfaces (Priority: P3)

The conversions of stories 1 to 3 are available through library, CLI and REST with identical documents and reports. The capability information names CHMED23A with its forms and marks it as deprecated.

**Why this priority**: Required by the constitution for every conversion; follows once the conversions exist.

**Independent Test**: Convert the test corpus through library, CLI and REST and compare documents and reports byte for byte; the capabilities list CHMED23A as deprecated.

**Acceptance Scenarios**:

1. **Given** any input of the test corpus, **When** it is converted through library, CLI and REST with the same options, **Then** documents and reports are identical.
2. **Given** the running service, **When** its capabilities are requested, **Then** CHMED23A is listed with its input and output forms, its conversions and the mark that IG eMediplan has deprecated it.

---

### Edge Cases

- QR payload of another version (prefix `CHMED16A1`) declared as CHMED23A, or the reverse → refused with a hint naming the detected format.
- Chunked payload with a missing, duplicated or out-of-range chunk → refused naming the problem, not the content.
- Chunked payload as QR images → chunks are read as text only; a QR image must hold the whole document, several codes in one image are refused as `QR_MULTIPLE` (clarification 2026-11-12).
- CHMED23A JSON without the transmission wrapper → accepted as plain JSON; the format is known from the declared source.
- Medication without product code (free text), or with codes CH EMED does not know → kept as text and reported, as for CHMED16A.
- Posology that combines several forms or refers to time of day, weekdays or meal relations → represented as far as CH EMED allows, rest as text with report.
- Private or unknown fields → reported, not dropped silently.
- Conversion between CHMED23A and CHMED16A or CH EMED EPR requested directly → refused, naming the path through CH EMED.
- Non-ASCII content (umlauts, accents, French and Italian texts) → preserved.

## Requirements *(mandatory)*

### Functional Requirements

**Conversions**

- **FR-001**: The system MUST offer CHMED23A as a source and target of its own, distinct from CHMED16A.
- **FR-002**: The system MUST convert in both directions between the CHMED23A medication plan (medType 1) and the CH EMED Medication Card, and between the CHMED23A prescription (medType 3) and the CH EMED Medication Prescription.
- **FR-003**: PolymedicationCheck (medType 2) and any other document type MUST be refused with a specific error naming the supported types.
- **FR-004**: The system MUST NOT offer direct conversions between CHMED23A and other formats (CHMED16A, CH EMED EPR); they are refused naming the path through CH EMED.

**Transmission forms**

- **FR-005**: The system MUST read CHMED23A as plain JSON, as QR payload in the ChTransmissionFormat (prefix `CHMED23A.`, Base64 over GZip), as chunked payload (`CHMED23A.i/n.`, all chunks as text in one input) and as QR image holding the whole document, and MUST detect the form by content.
- **FR-006**: The system MUST write CHMED23A as plain JSON, as QR payload and as QR image, deterministically, for medication plans and prescriptions; a document that does not fit into one QR code MUST fail for the QR image with `QR_CAPACITY_EXCEEDED`, while the payload stays available; chunked payloads are not written.

**Conformance and fidelity**

- **FR-007**: CHMED23A input and output MUST conform to the object model, posology and terminology of the pinned CHMED23A specification; non-conforming input MUST be refused with findings naming element paths; non-conforming output MUST NOT be returned.
- **FR-008**: Every CH EMED output MUST validate without errors against the pinned CH EMED profiles, as for CHMED16A.
- **FR-009**: The posology of CHMED23A MUST be mapped to structured CH EMED dosage wherever CH EMED can express it; otherwise it MUST be carried as dosage text and reported, with clinical loss where structure is lost.
- **FR-010**: Data required by CH EMED but missing in CHMED23A MUST come from the existing options (patient identifier, author, confidentiality, language); otherwise the conversion MUST fail with `MISSING_REQUIRED_DATA` naming the element, never its value.
- **FR-011**: A round trip CHMED23A → CH EMED → CHMED23A MUST give the original document apart from changes documented in the mapping specification, and the reverse round trip likewise.
- **FR-012**: For identical input and options, output MUST be byte-identical, including QR payloads and images.
- **FR-013**: The rules of both directions MUST be documented in a mapping specification with rule ids and covered by fixture tests, including all examples of the pinned specification.

**Interfaces**

- **FR-014**: All conversions, forms and options MUST be available through library, CLI and REST with identical behaviour and report, without changing the behaviour of existing conversions, except that CHMED16A input that is CHMED23A is refused naming the source `chmed23a` (plan, research R3).
- **FR-015**: The capability information MUST list CHMED23A with its forms and conversions and mark it as deprecated by IG eMediplan; the documentation MUST say so too.

**Data protection**

- **FR-016**: Logs, error messages and reports MUST NOT contain patient-identifying data or document content.
- **FR-017**: The system MUST NOT contact any external service.

### Key Entities

- **CHMED23A document**: an eMediplan JSON document of medType 1 (medication plan) or 3 (prescription), as defined by the frozen specification of IG eMediplan.
- **ChTransmissionFormat payload**: the QR text `CHMED23A.` followed by Base64 over GZip of the JSON, optionally split into numbered chunks.
- **CHMED23A posology**: the dosage model of CHMED23A, richer than CHMED16A (several posology forms, repetitions for prescriptions).
- **Conversion report**: as in the existing features.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the medication plan and prescription examples of the pinned CHMED23A specification are converted to CH EMED documents that validate without errors.
- **SC-002**: 100% of these CH EMED documents are converted back to CHMED23A documents that conform to the specification, and the round trips differ only by documented changes.
- **SC-003**: For every example, the results from plain JSON, QR payload, chunked payload and QR image are identical.
- **SC-004**: For 100% of the inputs of the test corpus, library, CLI and REST give identical documents and reports.
- **SC-005**: Converting a plan with 20 medications, including validation, stays within the time limits of feature 001 on the reference hardware.
- **SC-006**: An automated scan of logs and reports from all test runs finds zero patient-identifying data.
- **SC-007**: During all conversions, no connection to an external service is attempted.

## Assumptions

- The CHMED23A specification is frozen: IG eMediplan has stopped its development and no longer maintains it. The converter pins the commit of `ig-emediplan/specification` already pinned for CHMED16A (or a newer one, decided in the plan) and does not follow later changes.
- No JSON schema exists for CHMED23A; conformance checks are derived from the object model, posology and terminology of the specification and from the .NET reference implementation, and are documented in the mapping specification.
- The examples of the specification (`examples.md`, examples of the .NET reference implementation) are synthetic and may be used as fixtures (constitution IV); their licence is checked in the plan.
- The QR symbol parameters of feature 003 (error correction, module size, quiet zone) apply to CHMED23A as well, provisionally as for CHMED16A.
- Signed eRezept URLs exist only for CHMED16A; CHMED23A has no signature.
- Writing chunked payloads follows in a later feature, together with a possible QR export of CH
  EMED documents, which would almost always need several codes. No QR format for CH EMED is
  published yet; IG eMediplan is to be asked whether one is planned. Reading chunks is built so that
  writing can build on it.
- Production data in CHMED23A is rare; the feature serves existing documents and partners that still use the format.
- The reference hardware is the one of feature 002.
