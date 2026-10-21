<!--
Sync Impact Report
==================
Version change: 1.3.1 → 1.4.0
Bump rationale: MINOR — new supported format (eRezept Schweiz QR code / URL) added to the
Conversion Scope with matching conformance, test and data-protection rules; nothing removed.

Modified principles:
  - I. Specification Conformance → added eRezept Schweiz (CHMED16A prescription per eRezept
    conformance rules; signed URL structure preserved).
  - III. Test-First → eRezept fixtures (synthetic signed URLs) added to the corpus requirement.
  - IV. Patient Data Protection → no calls to HIN / eRezept services; signature data never
    logged.

Modified sections:
  - Conversion Scope → eRezept Schweiz row with its asymmetric direction (read signed QR/URL;
    write unsigned CHMED16A prescription payload); signing and status checks out of scope.

Added sections: none
Removed sections: none

Templates requiring updates: none — dependent templates read this file at runtime.
Affected artefacts:
  - specs/001-* rewritten from CHMED23A to CHMED16A (CHMED23A was withdrawn by IG eMediplan and
    is deferred to a later feature; it stays in scope).
  - specs/002-rest-conversion-service references updated to CHMED16A.

Follow-up TODOs: none
-->

# CH EMED Converter Constitution

## Core Principles

### I. Specification Conformance

- Every output MUST conform to the specification of its target format:
  - **CH EMED** (and CH EMED EPR where targeted): MUST validate without errors against the
    HL7 FHIR R4 profiles of the pinned IG packages and the CH Core profiles they depend on.
  - **CHMED16A / CHMED23A** (eMediplan): MUST conform to the respective published eMediplan
    specification, including the JSON structure and any schema provided by it.
  - **CARA EMED**: MUST conform to the CARA EMED specification and profiles published by CARA.
  - **eMediplan QR code**: the payload MUST use the prefix, compression and encoding defined by
    the respective CHMED specification; generated QR images MUST meet the symbol requirements
    (size, error-correction level) of that specification.
  - **eRezept Schweiz**: prescription payloads MUST conform to the CHMED16A prescription
    specification and the data requirements of the eRezept Schweiz developer documentation
    (strict CHMED conformance, correct product identifiers), so that they can be signed by the
    HIN erxproxy without modification.
- The exact version of every format specification and IG package, and the authoritative
  validation artefacts for it, MUST be pinned in the repository. The concrete versions are
  selected and justified in the feature spec/plan that introduces or changes a format; upgrading
  or adding a version is a deliberate, reviewed change.
- Official validation artefacts (e.g. the HL7 FHIR Validator with pinned IG packages, published
  JSON schemas) are the authority on conformance; hand-written checks MAY supplement them but
  MUST NOT replace them where such artefacts exist.

Rationale: interoperability between eMediplan, CARA and the Swiss EPR (EPD) depends on strict
conformance; a "mostly valid" medication document is a rejected or misinterpreted one.

### II. Lossless, Explicit Mapping

- Conversion MUST NOT silently drop, truncate, or invent clinical data (medication, dosage,
  route, timing, author, patient, status, notes).
- Every source element that cannot be represented in the target format MUST be surfaced in a
  structured conversion report (element path, reason, severity); the caller decides whether a
  lossy result is acceptable.
- The formats differ in expressiveness (e.g. CHMED16A dosage and posology structures versus
  CH EMED Dosage); these known capability gaps MUST be documented per conversion direction and
  MUST produce report entries when they occur.
- Mappings between formats, code systems (e.g. GTIN, Pharmacode, ATC, SNOMED CT, EDQM), and
  value sets MUST be documented in a single, versioned mapping specification that the code
  follows.
- Defaults MUST NOT be applied to clinically relevant fields; missing required data is an error
  or a reported warning, never a guess.

Rationale: in medication data, silent loss or fabrication is a patient-safety risk.

### III. Test-First with Reference Fixtures (NON-NEGOTIABLE)

- Tests are written before the implementation and MUST fail before the implementation is added
  (Red → Green → Refactor).
- Every mapping rule MUST be covered by at least one fixture-based test asserting the exact
  expected output, and every produced output in tests MUST pass the conformance checks of
  Principle I.
- The official example instances published for each supported format (CH EMED IG, eMediplan
  CHMED16A/CHMED23A, CARA EMED) MUST be part of the test corpus.
- Every bidirectional conversion pair MUST have round-trip tests
  (e.g. CHMED23A → CH EMED → CHMED23A); any difference after the round trip MUST match the
  documented capability gaps of Principle II.
- QR code support MUST be tested at payload level (decode/encode) and, where images are
  handled, with image fixtures; eRezept support MUST be tested with synthetic signed eRezept
  URLs/QR codes, never with real prescriptions.
- Bug fixes MUST start with a failing regression test reproducing the defect.

Rationale: conformance and fidelity claims are only credible when demonstrated by executable,
reproducible evidence.

### IV. Patient Data Protection

- The converter processes data locally and MUST NOT transmit input or output data to any
  external service (telemetry, remote validators, AI services) unless explicitly configured by
  the operator for that purpose.
- Logs, error messages, and conversion reports MUST NOT contain patient-identifying data
  (names, AHV/EPR identifiers, birth dates, addresses) nor raw QR payloads; they reference
  element paths and resource ids instead.
- Test fixtures and examples MUST be synthetic or officially published example data; real
  patient data or QR codes from real medication plans MUST NEVER be committed to the repository.
- The converter MUST NOT contact HIN or eRezept Schweiz services (signing, verification,
  dispensing); eRezept signature data (identity, timestamp, signature) MUST NOT be logged.
- The REST service MUST NOT persist request or response content (no storage, caching or
  queuing of medication data beyond the lifetime of a request).
- The REST service itself provides neither transport encryption nor access control; securing
  the network path (e.g. TLS at a gateway, network segmentation) and controlling access are the
  operator's responsibility. The operator documentation MUST state this explicitly, because
  requests contain patient data.

Rationale: medication data is particularly sensitive personal data under the Swiss revised
Federal Act on Data Protection (revDSG/nDSG) and the Federal Act on the EPR (EPDG).

### V. Deterministic, Simple Conversion

- CH EMED is the canonical hub model: every format is converted to and from CH EMED. Conversions
  between two non-CH-EMED formats (e.g. CHMED16A → CARA EMED), if offered, MUST be composed via
  CH EMED rather than implemented as separate direct mappings.
- Given identical input, configuration, and specification versions, the converter MUST produce
  byte-identical output (stable ordering, no wall-clock timestamps or random ids unless injected
  via configuration).
- Conversion logic MUST be stateless pure transformation; format parsers/serializers, QR
  encoding/decoding, I/O, CLI, and the REST service are separate layers around a core library
  that is usable and testable on its own. The core library MUST NOT depend on any web or
  application framework.
- Start with the simplest design that satisfies the specifications (YAGNI); additional
  abstractions, dependencies, or formats beyond the Conversion Scope require a documented
  justification in the feature plan.

Rationale: a single hub model keeps the number of mappings linear in the number of formats;
determinism makes outputs diffable, testable, and auditable; simplicity keeps the mapping
logic reviewable by domain experts.

## Conversion Scope

The converter MUST support the following conversions, each in both directions:

| Source / Target            | ↔ | Canonical model |
|----------------------------|---|-----------------|
| CHMED16A (eMediplan)       | ↔ | CH EMED         |
| CHMED23A (eMediplan)       | ↔ | CH EMED         |
| CARA EMED                  | ↔ | CH EMED         |
| eMediplan QR code          | ↔ | CH EMED         |
| eRezept Schweiz (QR / URL) | ↔ | CH EMED         |

- **eMediplan QR code**: input is a QR image or its textual payload; the payload is decoded
  (prefix, decompression, decoding) into CHMED16A or CHMED23A according to its version prefix
  and then converted to CH EMED. Output is the CHMED payload and, on request, a QR image.
  The target CHMED version for QR output MUST be selectable by the caller.
- **eRezept Schweiz**: input is a signed eRezept QR code or its URL (e.g.
  `https://eprescription.hin.ch/#CHMED16A1…` with identity, timestamp and signature
  parameters); the embedded CHMED16A prescription is converted to a CH EMED
  MedicationPrescription and the signature data is preserved, unverified, as metadata. Output
  is an unsigned CHMED16A prescription payload ready to be signed by the primary system via the
  HIN erxproxy; signing, signature verification and status checks are out of scope.
- Which CH EMED document types (e.g. MedicationCard, MedicationTreatmentPlan,
  MedicationPrescription, MedicationDispense, PharmaceuticalAdvice, MedicationList) are
  supported per format pair is defined in the respective feature spec, based on the semantics
  of the source format (an eMediplan medication plan maps naturally to a MedicationCard).
- **Delivery interfaces**: every conversion MUST be available through all three interfaces —
  Java library, command-line interface (CLI) and REST service — with identical behaviour,
  options (e.g. strict mode) and conversion report. A conversion is complete only when it is
  available through all three.
- Adding or removing a format, direction or delivery interface is a constitution amendment
  (MINOR or MAJOR).

## Domain & Technical Constraints

- Implementation platform: the core library, format modules, CLI and REST service MUST be
  implemented in Java, targeting Java 21 LTS (a later LTS only via amendment). The FHIR R4 data model, parsing and
  serialization MUST use HAPI FHIR; conformance checks per Principle I MUST use the official
  Java-based HL7 FHIR Validator with the pinned IG packages, run locally (Principle IV). Build
  tool, test framework, the REST service's web framework and further libraries are chosen in the
  feature plan; once chosen, the web framework applies to the whole project.
- CH EMED data model: HL7 FHIR R4, using the pinned CH EMED / CH EMED EPR and CH Core IG
  packages. Supported serializations MUST include FHIR JSON; FHIR XML support is decided per
  feature.
- CHMED16A / CHMED23A: JSON as defined by the eMediplan specifications; both the plain JSON form
  and the QR payload form MUST be accepted as input.
- CARA EMED: serialization as defined by the pinned CARA EMED specification.
- Terminology: code systems and value sets MUST be taken from the pinned specifications or their
  declared dependencies; locally invented codes are prohibited.
- Errors MUST be reported with machine-readable codes plus human-readable messages; the CLI
  MUST exit non-zero on conversion errors and offer a JSON form of the conversion report; the
  REST service MUST signal failures with appropriate HTTP status codes and return the conversion
  report in machine-readable form with every response.
- Third-party dependencies (including QR and compression libraries) MUST have a license
  compatible with the project license and are reviewed before introduction.

## Development Workflow & Quality Gates

- Work proceeds through the Spec Kit flow: specify → (clarify) → plan → tasks → implement. The
  plan's Constitution Check MUST pass, or deviations MUST be listed with justification.
- A change MUST NOT be merged unless:
  1. all tests pass, including the conformance checks of every produced fixture output;
  2. the mapping specification is updated for any mapping change;
  3. no new test fixture contains real patient data;
  4. at least one reviewer has approved; mapping changes SHOULD be reviewed by someone with
     domain knowledge of the formats involved (CH EMED, eMediplan, CARA).
- Changes to output that existing consumers rely on (structure, codes, report format) MUST be
  versioned following semantic versioning and noted in the changelog.

## Governance

- This constitution supersedes other project practices and guidelines; where they conflict,
  this document wins.
- Amendments are made via `/speckit-constitution`, submitted as a reviewed change that states
  the rationale and any migration impact on existing specs, plans, or code.
- Versioning of this document follows semantic versioning: MAJOR for removal or incompatible
  redefinition of a principle, MINOR for a new principle/section or materially expanded
  guidance, PATCH for clarifications and wording.
- Compliance is checked in every plan (Constitution Check) and every code review; unjustified
  complexity or principle violations are grounds for rejecting a change.

**Version**: 1.4.0 | **Ratified**: 2026-10-05 | **Last Amended**: 2026-10-18
