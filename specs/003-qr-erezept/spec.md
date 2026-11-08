# Feature Specification: eMediplan QR Code and eRezept Schweiz

**Feature Branch**: `003-qr-erezept`

**Created**: 2026-11-01

**Status**: Implemented

**Input**: User description: "eMediplan-QR-Code und eRezept Schweiz: Der Converter soll eMediplan-QR-Codes (Bild und textuelle Payload) und signierte eRezept-QR-Codes/URLs als Eingabe lesen und nach CH EMED konvertieren, und aus CH EMED CHMED-QR-Payloads, QR-Bilder und unsignierte CHMED16A-Rezept-Payloads für die Signierung via HIN erxproxy erzeugen, gemäss Conversion Scope der Constitution; verfügbar über Library, CLI und REST."

## Clarifications

### Session 2026-11-01

- Q: Should a QR image of an unsigned prescription be produced? → A: No. It is refused with a specific error; prescriptions are output as unsigned payload only, because an image could be mistaken for a valid signed eRezept.
- Q: Where is the signature data of a signed eRezept returned? → A: Beside the document, as a separate part of the result (field in library and REST response, separate output file in the CLI). The CH EMED document stays identical to the conversion without signature, and the report does not contain it.

### Session 2026-11-02

- Q: Do the conversion options for data that CHMED16A lacks also apply to QR images and eRezept URLs? → A: Yes. Patient identifier and author details (feature 001 FR-023) are accepted for these inputs as for plain JSON; without them, a conversion that needs them fails and names the missing data.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read a medication plan from a QR code image (Priority: P1)

A pharmacy or practice receives a patient's printed or digital eMediplan. Its primary system has an image of the QR code and needs the plan as a CH EMED document, for example to store it in the patient's record.

**Why this priority**: The QR code is how an eMediplan actually travels between patients and care providers. Feature 001 reads the text inside a QR code, but nobody can use that without first decoding the image.

**Independent Test**: Provide images of QR codes generated from the feature 001 reference plans; the resulting CH EMED documents are identical to those produced from the same plans as plain JSON.

**Acceptance Scenarios**:

1. **Given** an image with one QR code holding a CHMED16A medication plan, **When** it is converted to CH EMED with the same options, **Then** the result is the same CH EMED document and report as for the plain JSON of that plan.
2. **Given** an image without a readable QR code, **When** it is converted, **Then** the conversion fails with an input error stating that no QR code could be read, and no document is returned.
3. **Given** an image with a QR code that holds other content than a CHMED payload, **When** it is converted, **Then** the conversion fails with an input error, without repeating that content in the report or logs.
4. **Given** a QR code whose plan lacks the patient identifier or the author details required by CH EMED, **When** it is converted with these details as options, **Then** the options supply the missing data as for plain JSON; **When** it is converted without them, **Then** the conversion fails with `MISSING_REQUIRED_DATA` naming the missing data, and no document is returned.

---

### User Story 2 - Read a signed eRezept (Priority: P1)

A pharmacy receives a signed eRezept as a QR code or as its URL (`https://eprescription.hin.ch/#CHMED16A1…`). Its system needs the prescription as a CH EMED Medication Prescription document and keeps the signature data for its own further processing, such as checking the prescription with HIN.

**Why this priority**: eRezept Schweiz is the live exchange format for prescriptions in Switzerland; a signed prescription is currently rejected by the converter.

**Independent Test**: Convert synthetic signed eRezept URLs and QR code images; the CH EMED document equals the one from the embedded prescription alone, and the signature data is returned unchanged and clearly marked as not verified.

**Acceptance Scenarios**:

1. **Given** a signed eRezept URL, **When** it is converted to CH EMED, **Then** the result is a Medication Prescription document identical to the conversion of the embedded CHMED16A prescription with the same options, and the identity, timestamp and signature are returned unchanged, marked as not verified.
2. **Given** a QR code image holding a signed eRezept URL, **When** it is converted, **Then** the result is the same as for the URL itself.
3. **Given** a URL that is not an eRezept URL or lacks signature parameters, **When** it is converted, **Then** the conversion fails with an input error naming what is missing, and the converter contacts no external service.
4. **Given** any eRezept, **When** it is converted, **Then** the signature is not verified and the status of the prescription is not checked, and the report states this.
5. **Given** a signed eRezept whose prescription lacks data required by CH EMED, **When** it is converted without the matching options, **Then** the conversion fails with `MISSING_REQUIRED_DATA` naming the missing data, and neither a document nor signature data is returned.

---

### User Story 3 - Create a QR code for a medication plan (Priority: P2)

A primary system or an EPR integration holds a CH EMED Medication Card and needs a printable eMediplan QR code, for example to hand it to the patient or to print it on a paper plan.

**Why this priority**: It closes the round trip for medication plans, but systems first need to be able to read what they receive.

**Independent Test**: Create payloads and images from CH EMED documents; an independent QR reader recovers the payload, and converting the image back yields the expected plan with only the documented capability gaps.

**Acceptance Scenarios**:

1. **Given** a CH EMED Medication Card, **When** a QR payload is requested, **Then** the result is the textual CHMED16A payload (`CHMED16A1…`) and the conversion report.
2. **Given** a CH EMED Medication Card, **When** a QR image is requested, **Then** the result is an image meeting the symbol requirements of the eMediplan specification, which an independent QR reader decodes to exactly that payload.
3. **Given** a plan too large for one QR code, **When** an image is requested, **Then** the conversion fails with a specific error stating the size limit and no image is returned; the textual payload remains available.

---

### User Story 4 - Prepare a prescription for signing (Priority: P2)

A prescribing primary system holds a CH EMED Medication Prescription and needs a CHMED16A payload that it can have signed by the HIN erxproxy without modification.

**Why this priority**: Feature 001 already produces the prescription JSON that meets the eRezept data requirements; this adds the transmission form that signing expects.

**Independent Test**: Convert CH EMED prescriptions to the unsigned payload; decoding it yields the prescription that passes the eRezept data requirements and the published schema.

**Acceptance Scenarios**:

1. **Given** a CH EMED Medication Prescription, **When** an unsigned payload is requested, **Then** the result is a CHMED16A payload whose decoded prescription meets all eRezept data requirements, and no signature data is present.
2. **Given** a prescription violating the eRezept data requirements, **When** an unsigned payload is requested, **Then** the conversion fails with the violations listed in the report and no payload is returned.
3. **Given** a CH EMED Medication Prescription, **When** a QR image is requested, **Then** the request is refused with a specific error stating that prescriptions are output as unsigned payload only, and no image is returned.

---

### User Story 5 - Same behaviour through all interfaces (Priority: P3)

An integrator uses the library, the command line or the REST service and expects the same forms, options and reports in each.

**Why this priority**: Required by the constitution for a conversion to count as complete, but it builds on the stories above.

**Independent Test**: Run the same QR, URL and image inputs through library, CLI and REST; documents, payloads, images and reports are identical.

**Acceptance Scenarios**:

1. **Given** the same input and options, **When** it is converted through library, CLI and REST, **Then** output and report are identical, byte for byte for payloads and images.
2. **Given** a running REST service, **When** its capabilities are requested, **Then** the new input and output forms are listed.

---

### Edge Cases

- Image with several QR codes → input error stating that exactly one is expected; the converter does not choose one.
- Image that is too large, corrupt or in an unsupported format → input or size error; no conversion attempted.
- Damaged or poorly photographed QR code that cannot be read → input error; never a partial plan.
- QR payload with a corrupt compression or encoding → input error, as for the textual payload today.
- Payload that decompresses beyond the size limit → input error (protection against oversized content).
- QR code holding a CHMED23A payload → input error stating that this version is not yet supported.
- eRezept URL with a query or fragment in unexpected order, or with additional parameters → the known parameters are read; unknown ones are ignored and reported without their values.
- eRezept whose embedded document is a medication plan rather than a prescription → input error.
- Signature parameters that are present but malformed → passed on unchanged as unverified data; the converter does not judge them, and the report notes their form could not be recognised.
- Non-ASCII content (umlauts, accents) → preserved through QR encoding and decoding.
- Medication plan with no entries or the minimum content → valid QR code, as long as the plan itself is valid.

## Requirements *(mandatory)*

### Functional Requirements

**Reading**

- **FR-001**: The system MUST accept an image holding exactly one QR code and decode it into its content, without any network access.
- **FR-002**: The system MUST convert decoded eMediplan QR content and the textual payload with the same result as the plain CHMED16A JSON of that plan or prescription, including the conversion report.
- **FR-003**: The system MUST accept a signed eRezept URL, and a QR code image holding one, and convert the embedded CHMED16A prescription to a CH EMED Medication Prescription document.
- **FR-004**: The system MUST return the identity, timestamp and signature of a signed eRezept unchanged and clearly marked as not verified, beside the document as a separate part of the result; the CH EMED document and the report MUST NOT contain it.
- **FR-005**: The system MUST NOT verify signatures, check the status of a prescription or contact HIN or eRezept Schweiz services, and the report MUST state that the signature was not verified.
- **FR-019**: The options for data that CHMED16A lacks (patient identifier, author details; feature 001 FR-023) MUST also apply to QR code images, textual payloads and signed eRezept URLs. They are used only where the source lacks the data; if neither source nor options provide data required by CH EMED, the conversion MUST fail with `MISSING_REQUIRED_DATA` naming the missing data, never its values.

**Writing**

- **FR-006**: The system MUST produce the textual CHMED16A QR payload from a CH EMED Medication Card or Medication Prescription, using the prefix, compression and encoding defined by the eMediplan specification.
- **FR-007**: The system MUST produce, on request, a QR code image from a CH EMED Medication Card that meets the symbol requirements stated by the eMediplan specification (printed size 4 × 4 cm, blank margin around the code) and the symbol parameters the specification leaves open, as pinned in the repository (error-correction level, quiet zone, module size). For a CH EMED Medication Prescription it MUST refuse a QR image with a specific error.
- **FR-008**: The system MUST fail with a specific error when a payload does not fit into one QR code; it MUST NOT split, shorten or drop content to make it fit.
- **FR-009**: The system MUST produce an unsigned CHMED16A prescription payload that meets the eRezept data requirements, so that it can be signed by the HIN erxproxy without modification, and MUST refuse to produce it when those requirements are violated.
- **FR-010**: The caller MUST be able to choose the target CHMED version of a QR output. Only CHMED16A is offered until CHMED23A is implemented; another version is refused with a specific error.

**Determinism and conformance**

- **FR-011**: For identical input and options, payloads and images MUST be byte-identical.
- **FR-012**: A payload produced by the system and read back MUST yield the same CHMED16A document; a full round trip through CH EMED MUST differ only by the documented capability gaps.
- **FR-013**: The input form MUST be recognised reliably and an input that does not match the declared source MUST be refused with an error stating what was detected.

**Interfaces**

- **FR-014**: Every new input and output form and option MUST be available through library, CLI and REST with identical behaviour and report, without changing the behaviour of existing operations.
- **FR-015**: Capability information of the service MUST list the new forms, and the API description MUST cover them.

**Data protection**

- **FR-016**: Logs, error messages and reports MUST NOT contain raw QR payloads, eRezept URLs, identities, timestamps, signatures or patient-identifying data; the signature data is only returned to the caller as defined in FR-004.
- **FR-017**: Images and payloads MUST NOT be stored by the system beyond the processing of a request.
- **FR-018**: The maximum size of an input image MUST be limited and, in the REST service, configurable by the operator.

### Key Entities

- **QR code image**: an image holding one QR code; its content is a CHMED payload or an eRezept URL.
- **CHMED QR payload**: the textual transmission form of a CHMED16A document (prefix, compression flag, encoded content).
- **Signed eRezept**: a URL holding a CHMED16A prescription payload plus identity, timestamp and signature.
- **Signature data**: identity, timestamp and signature of a signed eRezept; carried unchanged, never verified.
- **Unsigned prescription payload**: the CHMED16A prescription payload ready to be signed by the HIN erxproxy.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For 100% of the feature 001 reference plans and prescriptions, conversion through a QR code image with the same options gives the same CH EMED document and report as conversion from plain JSON, including the failure for missing required data.
- **SC-002**: 100% of the produced QR code images are decoded to exactly the original payload by at least one independent QR reader.
- **SC-003**: 100% of the synthetic signed eRezept fixtures are converted with identity, timestamp and signature returned unchanged and marked as not verified.
- **SC-004**: 100% of the produced unsigned prescription payloads meet the eRezept data requirements and the published schema after decoding.
- **SC-005**: A large realistic plan with 20 medications fits into one QR code image that meets the symbol requirements and pinned parameters of FR-007, and is read back without error.
- **SC-006**: Reading a QR code image adds less than 1 second to the conversion time of the same plan from plain JSON, on the reference hardware.
- **SC-007**: An automated scan of logs and reports from all test runs finds zero raw payloads, eRezept URLs, signature data or patient-identifying data.
- **SC-008**: For 100% of the inputs of the test corpus, library, CLI and REST give identical documents, payloads, images and reports.
- **SC-009**: During all conversions, no connection to an external service is attempted.

## Assumptions

- This feature builds on feature 001 (CHMED16A conversion, eRezept data requirements) and feature 002 (REST service; documents and images travel base64-encoded in the JSON messages).
- Only CHMED16A is converted. CHMED23A stays in the constitution's scope and follows in its own feature; CHMED23A QR content is refused for now, and the target-version choice (FR-010) already exists so that it can be added later without changing the operations.
- Accepted input images are common raster formats (PNG and JPEG); images are produced as PNG. Clean digital images and good scans are supported; reading strongly distorted or poorly lit phone photos is best effort and not guaranteed, as primary systems normally decode with a scanner.
- A QR code holds one document; multi-part QR codes are out of scope.
- Signing, signature verification and status checks stay out of scope (constitution); they are done by the primary system with HIN.
- Fixtures contain only synthetic data; real prescriptions or QR codes from real plans are never used (constitution).
- The reference hardware is the one of feature 002 (provisional: 4 vCPU, 8 GB RAM).
- The eMediplan specification commit and the eRezept documentation pinned by feature 001 apply; any symbol requirements or URL details not covered by them are pinned in the plan of this feature.
