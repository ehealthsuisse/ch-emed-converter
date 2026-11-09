# Feature Specification: CH EMED EPR (CARA EMED)

**Feature Branch**: `004-ch-emed-epr`

**Created**: 2026-11-08

**Status**: Draft

**Input**: User description: "CH EMED EPR (CARA EMED): Der Converter soll Dokumente nach dem allgemeinen CH EMED IG 6.0.0 in EPD-konforme Dokumente nach CH EMED EPR 3.0.0 (ch.fhir.ig.ch-emed-epr, von CARA gepflegt) umwandeln und zurück, gemäss Conversion Scope der Constitution 1.5.0. Abgedeckt sind die Dokumenttypen Medikationskarte (PMLC), Rezept (PRE) und Behandlungsplan (MTP, ein Eintrag pro Dokument; aus einem eMediplan entsteht je Behandlung ein MTP); DIS, PADV und PML folgen später. CHMED16A erreicht CH EMED EPR über CH EMED (Hub), z. B. PMLC aus dem EPD lesen und als eMediplan/QR ausgeben, oder aus einem eMediplan Rezept und Behandlungspläne fürs EPD erzeugen. Ausgaben müssen fehlerfrei gegen die gepinnten CH-EMED-EPR-Profile validieren; Elemente, die CH EMED EPR nicht unterstützt, werden im Report ausgewiesen, nie still verworfen. Transport ins EPD (IHE MHD, PMP-Dienst von CARA) ist ausser Scope. Verfügbar über Library, CLI und REST mit identischem Verhalten."

## Clarifications

### Session 2026-11-08

- Q: Should conversions between CHMED16A and CH EMED EPR be offered in one operation, composed through CH EMED? → A: No, no simplifications yet. CH EMED EPR converts only to and from CH EMED; CHMED16A is reached in two steps with the existing conversions (FR-004).

### Session 2026-11-09

- Q: CH EMED EPR requires every prescription item and every card entry to reference its treatment plan (MTP) in the EPR, every card entry to name the last considered document, and prescriptions and cards to carry an original representation as PDF/A; general CH EMED documents, for example from an eMediplan, have none of this. How are PRE and PMLC produced from them? → A: The caller supplies the treatment-plan references as an option (FR-017); the caller may supply the original representation as PDF/A, and the converter renders one as PDF/A when neither the caller nor the source provides one (FR-018).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read a medication card from the EPR (Priority: P1)

A primary system (practice software, pharmacy system) has retrieved the patient's medication card (PMLC) from the EPR of the CARA community. It converts the card to a general CH EMED Medication Card; from there the existing conversion produces an eMediplan, to print the plan with its QR code or import it.

**Why this priority**: The medication card is the document the EPR offers every care provider; turning it into an eMediplan is the most direct use of the EPR data in daily practice, and it needs nothing beyond reading.

**Independent Test**: Convert the official PMLC examples of CH EMED EPR 3.0.0 to CH EMED and the results on to CHMED16A; the CH EMED documents validate against the pinned CH EMED profiles, and the CHMED16A plans pass the eMediplan checks of feature 001.

**Acceptance Scenarios**:

1. **Given** a valid PMLC document, **When** it is converted to CH EMED, **Then** the result is a valid CH EMED Medication Card with the same treatments, and the report lists every element that was changed.
2. **Given** the CH EMED Medication Card of step 1, **When** it is converted to CHMED16A with the existing conversion, optionally as QR payload or QR image, **Then** the result is a valid eMediplan with the same treatments.
3. **Given** a PMLC document with no treatments, **When** it is converted to CH EMED, **Then** the result is a valid Medication Card without medications.
4. **Given** a document that does not conform to CH EMED EPR, **When** it is converted from CH EMED EPR, **Then** the conversion fails with the validation findings and produces no output.

---

### User Story 2 - Create an EPR prescription and card (Priority: P1)

A primary system holds a prescription or a medication card as CH EMED, possibly converted from an eMediplan with the existing conversion. It converts it to a PRE or PMLC document that conforms to CH EMED EPR, ready to be submitted to the EPR by the system itself. In the EPR each prescribed medication and each card entry belongs to a treatment plan (MTP) that is already in the EPR, for example one created with story 3; the system names these treatment plans in the request.

**Why this priority**: Prescriptions are the documents primary systems most often have to place in the EPR; the converter already produces CH EMED prescriptions and cards, and this makes them EPR-ready.

**Independent Test**: Convert the CH EMED reference documents of feature 001, and the CH EMED documents produced from its CHMED16A reference plans and prescriptions, to CH EMED EPR; every result validates without errors against the pinned CH EMED EPR profiles.

**Acceptance Scenarios**:

1. **Given** a valid CH EMED Medication Prescription, **When** it is converted to CH EMED EPR, **Then** the result is a valid PRE document with the same prescribed medications.
2. **Given** a valid CH EMED Medication Card, **When** it is converted to CH EMED EPR, **Then** the result is a valid PMLC document.
3. **Given** a CHMED16A prescription, **When** a direct conversion to CH EMED EPR is requested, **Then** it is refused as an unsupported conversion that names the path through CH EMED.
4. **Given** a CH EMED document holding elements that CH EMED EPR does not support, **When** it is converted, **Then** these elements are left out and each is listed in the report with its path; clinically relevant ones are marked as clinical loss, so that strict mode fails.
5. **Given** a CH EMED document that lacks data CH EMED EPR requires (for example the treatment-plan references, the patient identifier or an author with a role), **When** it is converted without options supplying that data, **Then** the conversion fails naming the missing data, never its values.
6. **Given** a CH EMED prescription with n medications and no treatment-plan references, **When** it is converted with n treatment-plan references, **Then** each prescribed medication references its treatment plan in the given order; with a different number of references the conversion fails naming the mismatch.
7. **Given** a CH EMED card with treatment-plan references, **When** it is converted to PMLC, **Then** each entry names its treatment plan and, as last considered document, that treatment plan document, and the report shows the derived value.
8. **Given** a CH EMED prescription or card whose original representation is missing or not PDF/A, **When** it is converted to CH EMED EPR, **Then** the result carries an original representation rendered as PDF/A from the document content, and the report shows that it was rendered.
9. **Given** a CH EMED prescription and a PDF/A supplied by the caller, **When** it is converted to CH EMED EPR, **Then** the supplied PDF/A is the original representation, and the report shows that it was supplied; a supplied PDF that is not PDF/A is not used and is reported.

---

### User Story 3 - Create treatment plans for the EPR from an eMediplan (Priority: P2)

A primary system wants to place the treatments of an eMediplan into the EPR. In CH EMED EPR each treatment decision is a medication treatment plan document (MTP) with exactly one treatment. The system first converts the eMediplan to a CH EMED Medication Card with the existing conversion; the converter then produces one MTP per treatment of the card.

**Why this priority**: Treatment plans are how primary systems feed the medication card in the EPR; it depends on the document conversion of stories 1 and 2 and adds splitting one card into several documents.

**Independent Test**: Convert the CH EMED Medication Cards of feature 001, including those produced from its CHMED16A reference plans, to treatment plans; the number of MTP documents equals the number of treatments, each validates, and together they hold the same treatments as the card.

**Acceptance Scenarios**:

1. **Given** a CH EMED Medication Card with n treatments, **When** treatment plans are requested, **Then** exactly n MTP documents are returned, in the order of the treatments, each valid against CH EMED EPR.
2. **Given** the same card converted twice, **When** the MTP documents are compared, **Then** they are byte-identical, including their identifiers.
3. **Given** a card without treatments, **When** treatment plans are requested, **Then** the conversion fails with a specific error and produces no document.
4. **Given** a single CH EMED medication treatment plan, **When** it is converted to CH EMED EPR, **Then** the result is one valid MTP document, and converting it back yields the original document apart from the documented changes.

---

### User Story 4 - Read prescriptions and treatment plans from the EPR (Priority: P2)

A primary system has retrieved a PRE or MTP document from the EPR and converts it to CH EMED; from the CH EMED prescription the existing conversion produces a CHMED16A prescription or its unsigned payload for signing via HIN.

**Why this priority**: It completes both directions for all three document types; reading the card (story 1) covers the most common case first.

**Independent Test**: Convert the official PRE and MTP examples of CH EMED EPR 3.0.0 to CH EMED and the PRE results on to CHMED16A; the results validate, and a round trip back to CH EMED EPR differs only by the documented changes.

**Acceptance Scenarios**:

1. **Given** a valid PRE document, **When** it is converted to CH EMED, **Then** the result is a valid CH EMED Medication Prescription.
2. **Given** the CH EMED prescription of step 1, **When** it is converted to CHMED16A with the existing conversion, **Then** the result is a CHMED16A prescription that meets the eRezept data requirements, or the existing conversion reports what is missing.
3. **Given** a valid MTP document, **When** it is converted to CH EMED, **Then** the result is a valid CH EMED medication treatment plan.
4. **Given** a CH EMED EPR document, **When** a direct conversion to CHMED16A is requested, **Then** it is refused as an unsupported conversion that names the path through CH EMED.

---

### User Story 5 - Same behaviour through all interfaces (Priority: P3)

Integrators use the library, the command line or the REST service and get the same documents and reports for CH EMED EPR.

**Why this priority**: Required by the constitution for a conversion to count as complete; it builds on the stories above.

**Independent Test**: Run the same inputs through library, CLI and REST; documents and reports are identical, including the set of MTP documents.

**Acceptance Scenarios**:

1. **Given** the same input and options, **When** it is converted through library, CLI and REST, **Then** documents and reports are identical byte for byte.
2. **Given** a running REST service, **When** its capabilities are requested, **Then** CH EMED EPR is listed with its conversions and document types.

---

### Edge Cases

- CH EMED document of a type outside this feature (dispense, pharmaceutical advice, medication list) → refused as unsupported, naming the supported types.
- CH EMED EPR document of type DIS, PADV or PML → refused as not yet supported.
- Input that conforms to CH EMED but not to CH EMED EPR, declared as CH EMED EPR → fails with the validation findings and a hint that the source is probably general CH EMED.
- Input declared as general CH EMED that is a CH EMED EPR document → converted as CH EMED (every CH EMED EPR document is also a CH EMED document); no error.
- Elements marked as not supported by CH EMED EPR (for example identifier types, periods, attesters) → left out and reported, never silently dropped.
- Original representation (PDF) in the source → the PDF/A supplied by the caller wins; otherwise a PDF/A-1 or PDF/A-2 of the source is kept; otherwise one is rendered as PDF/A from the document content. A supplied or source PDF that is not PDF/A is not used and is reported (FR-018).
- Prescription or card without treatment-plan references and without the option → missing-data error naming the entries; references for entries that already have one, or a number of references that does not match the entries without one → error naming the mismatch, never the values.
- Card entry without last considered document → the document of its treatment plan, reported as derived value (FR-017).
- Patient without an identifier with system and value, or without the data the EPR patient requires → missing-data error unless supplied by options.
- Author that is a person without a role in an organisation → missing-data error unless supplied by options; the converter does not invent organisations.
- Confidentiality not given → taken from the existing confidentiality option (default `normal`), reported as derived value as in the existing conversions; never set without a report entry.
- Very large card (for example 100 treatments) → 100 MTP documents within the time limits of the existing conversions.
- Non-ASCII content (umlauts, accents, French and Italian texts) → preserved.

## Requirements *(mandatory)*

### Functional Requirements

**Conversions**

- **FR-001**: The system MUST offer CH EMED EPR as a source and target of its own, distinct from general CH EMED.
- **FR-002**: The system MUST convert in both directions between CH EMED Medication Card and PMLC, CH EMED Medication Prescription and PRE, and CH EMED medication treatment plan and MTP.
- **FR-003**: On request, the system MUST convert a CH EMED Medication Card into one MTP document per treatment, in the order of the treatments, returned together as one result; without that request a card becomes a PMLC document.
- **FR-004**: The system MUST NOT offer direct conversions between CH EMED EPR and other formats (CHMED16A in any of its forms); they are refused with a specific error naming the path through CH EMED. Combining the steps in one operation is left to a later feature.
- **FR-005**: Conversions without a defined mapping (for example document types outside FR-002) MUST be refused with a specific error that lists the supported conversions.

**Conformance and fidelity**

- **FR-006**: Every CH EMED EPR output MUST validate without errors against the pinned CH EMED EPR profiles and the CH EMED and CH Core profiles they depend on; output failing validation MUST NOT be returned.
- **FR-007**: Every CH EMED EPR input MUST be validated against the pinned CH EMED EPR profiles before conversion; an input with errors MUST be refused with the validation findings.
- **FR-008**: Elements that CH EMED EPR does not support MUST be left out of CH EMED EPR output and listed in the report with path and reason; clinically relevant elements MUST be marked as clinical loss.
- **FR-009**: Data required by CH EMED EPR but missing in the source MUST come from options: the existing patient identifier, author and confidentiality, and the treatment-plan references of FR-017; otherwise the conversion MUST fail with `MISSING_REQUIRED_DATA` naming the missing data, never its values. No other defaults may be applied to clinically relevant data.
- **FR-010**: A round trip CH EMED EPR → CH EMED → CH EMED EPR MUST give the original document apart from the changes documented in the mapping specification; a round trip CH EMED → CH EMED EPR → CH EMED MUST differ only by the documented elements of FR-008.
- **FR-011**: For identical input and options, output MUST be byte-identical, including the identifiers of generated MTP documents.
- **FR-012**: The rules of every conversion direction MUST be documented in the mapping specification and covered by fixture tests, including the official examples of CH EMED EPR for the three document types.
- **FR-017**: A new option MUST let the caller supply the treatment-plan references (identifier of the treatment plan document and of its medication statement) for the prescribed medications of a prescription and the entries of a card that have none, in document order; a card entry without last considered document MUST get the document of its treatment plan, reported as derived value. A number of references that does not match these entries MUST fail the conversion.
- **FR-018**: A CH EMED EPR prescription or card MUST carry an original representation as PDF/A. A new option MUST let the caller supply it; the supplied PDF, if it is PDF/A-1 or PDF/A-2, takes precedence over the one of the source. Otherwise a PDF/A-1 or PDF/A-2 of the source is kept; if there is none, the converter MUST render one as PDF/A from the converted document, without changing how existing conversions render their PDF. A supplied or source PDF that is not PDF/A is not used, and the report says so. Treatment plans take a supplied or source PDF/A, and get none otherwise. Every case is reported.

**Interfaces**

- **FR-013**: All conversions, options and the treatment-plan request MUST be available through library, CLI and REST with identical behaviour and report, without changing the behaviour of existing operations.
- **FR-014**: The capability information of the service MUST list CH EMED EPR, its conversions and document types, and the API description MUST cover them, including results with several documents.

**Data protection**

- **FR-015**: Logs, error messages and reports MUST NOT contain patient-identifying data; they reference element paths and resource ids, as for the existing conversions.
- **FR-016**: The system MUST NOT contact the EPR, CARA's eMedication service or any other external service; submitting and retrieving documents stays with the primary system.

### Key Entities

- **CH EMED EPR document**: a FHIR document conforming to CH EMED EPR 3.0.0; in this feature of type PMLC (medication card), PRE (prescription) or MTP (medication treatment plan).
- **Medication treatment plan (MTP)**: an EPR document holding exactly one treatment decision; a medication card becomes several of them.
- **Treatment-plan set**: the ordered result of FR-003, one MTP document per treatment, sharing one report.
- **Unsupported element**: an element allowed by CH EMED but marked as not supported by CH EMED EPR; left out and reported.
- **Treatment-plan reference**: the identifiers of a treatment plan document in the EPR and of its medication statement, which a prescribed medication or a card entry belongs to; supplied by the caller, never invented.
- **Conversion report**: as in features 001–003, covering all steps of a composed conversion.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the official PMLC, PRE and MTP examples of CH EMED EPR 3.0.0 are converted to CH EMED documents that validate without errors.
- **SC-002**: 100% of the CH EMED reference documents of feature 001, and of the CH EMED documents produced from its CHMED16A reference plans and prescriptions, are converted to CH EMED EPR documents that validate without errors when the required data is supplied by options (treatment-plan references, patient identifier, author), and fail only for documented missing data without them; every PRE and PMLC carries a PDF/A original representation.
- **SC-003**: 100% of the round trips of FR-010 over the test corpus differ only by documented changes.
- **SC-004**: For every medication card of the test corpus, the number of MTP documents equals the number of treatments, and every MTP validates without errors.
- **SC-005**: Converting a realistic medication card with 20 medications into CH EMED EPR, including validation, stays within the time limits that feature 001 sets for a conversion to CH EMED, on the reference hardware.
- **SC-006**: For 100% of the inputs of the test corpus, library, CLI and REST give identical documents and reports.
- **SC-007**: An automated scan of logs and reports from all test runs finds zero patient-identifying data.
- **SC-008**: During all conversions, no connection to an external service is attempted.

## Assumptions

- This feature builds on features 001 (CHMED16A ↔ CH EMED, options for missing data, original representation), 002 (REST service) and 003 (QR and eRezept transmission forms).
- CH EMED EPR 3.0.0 (2026-02-11) is pinned. It depends on CH EMED 6.0.0, CH Core 6.0.0, HL7 Terminology 7.0.1 and the FHIR Extensions Pack 5.2.0, which are already pinned; its own package and any further dependencies it declares are vendored like the existing ones.
- CH EMED EPR profiles CH EMED: every valid CH EMED EPR document is also a valid CH EMED document, so the direction CH EMED EPR → CH EMED mainly changes profile declarations and loses no clinical data. This is verified against the official examples during implementation.
- The treatment plans that a prescription or card references are already in the EPR, or are submitted together with it; the converter does not check this (FR-016). Story 3 produces treatment plans with stable identifiers that the caller can pass as references.
- PDF/A-1b is sufficient for the original representation (the EPR accepts PDF/A-1 and PDF/A-2).
- PMLC documents are produced in the EPR by the community's aggregator; the converter can still produce them, for testing and for systems that exchange cards outside the EPR.
- DIS, PADV and PML documents follow in a later feature.
- Submitting documents to the EPR and retrieving them (IHE MHD, CARA's eMedication service, EPR identifiers and metadata of the document registry) are out of scope (constitution 1.5.0).
- Identifiers of the CARA environments (assigning authorities of patient ids, named in the IG) are not inserted by the converter; patient identifiers come from the source or the existing option.
- Fixtures contain only synthetic data and the official examples of the IG (constitution).
- The reference hardware is the one of feature 002 (provisional: 4 vCPU, 8 GB RAM).
