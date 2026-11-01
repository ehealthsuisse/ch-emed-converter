# Feature Specification: REST Conversion Service

**Feature Branch**: `002-rest-conversion-service`

**Created**: 2026-10-18

**Status**: Implemented

**Input**: User description: "Ein REST-Service muss dazugehören."

## Clarifications

### Session 2026-10-18

- Q: FHIR operation, plain REST/JSON API, or both? → A: Plain REST/JSON API.
- Q: Which access control? → A: None in the service; protected by API gateway or network,
  operated by the customer.
- TLS is not required in the service; transport security is the operator's responsibility.

### Session 2026-10-29

- Q: Which conversion options does the service offer? → A: The same as the CLI (constitution
  Conversion Scope: identical options in library, CLI and REST): strict, document language,
  confidentiality, patient identifier and author details. Options carrying personal data are
  never sent in the URL or in headers.
- Q: How are document and options transported? → A: As one JSON request message; the input
  document is embedded base64-encoded (RFC 4648 §4), the options as a JSON object. The response
  carries the output document base64-encoded as well, so that byte identity can be verified by
  the client.
- Q: A document that cannot be parsed (e.g. not JSON) → A: The conversion is attempted; the
  library's PARSE errors are returned as invalid input with report. Only a malformed request
  message (not JSON, invalid base64, missing document) is rejected without conversion.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Convert a document over HTTP (Priority: P1)

An integrator connects a primary system (practice, pharmacy, hospital or EPR integration) to the
converter over the network instead of embedding the library. The system sends a document in one
supported format together with the desired target format and receives the converted document
and the conversion report in the response.

**Why this priority**: Remote access is the purpose of this feature; it lets systems that are not
written in Java, or that cannot embed the library, use every conversion.

**Independent Test**: Send each official CHMED16A and CH EMED example from feature 001 to the
service with the respective target format; every response contains the same output document
and report that the library produces for the same input and options.

**Acceptance Scenarios**:

1. **Given** a running service and a valid CHMED16A medication plan, **When** a conversion to
   CH EMED is requested, **Then** the response indicates success and contains the CH EMED
   document and the conversion report, identical to the library result for the same input.
2. **Given** a valid CH EMED document, **When** a conversion to CHMED16A is requested, **Then**
   the response contains the CHMED16A document and the report.
3. **Given** a conversion that succeeds with warnings, **When** it is requested, **Then** the
   response indicates success, contains the document, and the report lists every warning.

---

### User Story 2 - Clear, machine-readable failures (Priority: P2)

An integrator's system must react automatically to problems. When the input is invalid, the
requested conversion is not supported, strict mode rejects a lossy conversion, or the request
itself is malformed, the service answers with a failure status that distinguishes these cases
and a machine-readable report explaining why.

**Why this priority**: Unattended integrations depend on reliable error signalling; without it
the service cannot be used safely, but it builds on the successful path of Story 1.

**Independent Test**: Send invalid documents, unsupported format combinations, strict-mode
violations and malformed requests; each yields the documented failure status and a report
whose entries match the library's report for the same input.

**Acceptance Scenarios**:

1. **Given** an invalid input document, **When** a conversion is requested, **Then** the response
   signals a client-side input error, contains no output document, and the report lists the
   validation errors with their locations.
2. **Given** a source/target combination that is not supported, **When** it is requested,
   **Then** the response signals that the conversion is not supported and names the supported
   combinations.
3. **Given** strict mode and an input whose conversion loses clinically relevant information,
   **When** it is requested, **Then** the response signals a failed conversion (distinct from
   invalid input) and contains the report but no document.
4. **Given** any failure, **When** the response is inspected, **Then** it contains no
   patient-identifying data beyond the documents the caller itself sent.

---

### User Story 3 - Discover capabilities and API description (Priority: P3)

An integrator building a client wants to know which conversions, options and specification
versions the running service supports, and wants a machine-readable API description to
generate client code.

**Why this priority**: Eases integration and lets clients adapt automatically when formats are
added later (CHMED23A, CARA EMED, QR code / eRezept), but conversions work without it.

**Independent Test**: Query the capability information and the API description of a running
service and compare them with the conversions actually offered.

**Acceptance Scenarios**:

1. **Given** a running service, **When** capabilities are requested, **Then** the response lists
   every supported source/target pair, the available options (e.g. strict mode) and the pinned
   specification versions.
2. **Given** a running service, **When** the API description is requested, **Then** a
   machine-readable description of all operations, inputs, outputs and failure responses is
   returned that matches the actual behaviour.

---

### User Story 4 - Operate the service safely (Priority: P4)

An operator runs the service in a hospital, pharmacy chain or service provider environment. They
need health and readiness information for monitoring, operational logs without patient data, and
the assurance that no medication data is stored by the service.

**Why this priority**: Required for production operation, but not for functional evaluation of
the conversions.

**Independent Test**: Start the service, query health and readiness, run the test corpus
through it, then inspect logs and the runtime environment for persisted or logged medication
data.

**Acceptance Scenarios**:

1. **Given** a started service, **When** health and readiness are queried, **Then** they report
   whether the service and its validation resources are ready to accept conversions.
2. **Given** a series of conversions, **When** logs and storage of the service are inspected,
   **Then** they contain no request or response content and no patient-identifying data.

---

### Edge Cases

- Request message that is not JSON, has no or an empty document, has a document that is not
  valid base64, or contains unknown or invalid options → client error, no conversion attempted.
- Request message that is not declared as JSON → "unsupported media type" error.
- Document that is not parseable in the source format (e.g. not JSON) → input error with the
  library's report (PARSE phase).
- Request or decoded document larger than the configured maximum size → rejected with a
  specific "too large" error.
- Conversion needs a patient identifier or author details that neither the source nor the
  request options provide → failed conversion naming the missing data (feature 001 FR-023).
- eRezept URL sent as CHMED16A input → input error until the eRezept feature is implemented;
  signature data is never logged.
- Missing or unknown source or target format → client error naming the supported formats.
- Source format declared by the caller differs from the detected format (e.g. CHMED23A sent as
  CHMED16A) → input error stating the detected format.
- Conversion exceeds the configured time limit → failure response; no partial document returned.
- Many simultaneous requests → each conversion is independent; results never mix between
  requests.
- Service started while validation resources (pinned IG packages, schemas) are missing →
  readiness reports "not ready" and conversions are refused rather than returned unvalidated.
- Unexpected internal error → generic server-error response with a correlation id, no stack
  traces or input data in the response or logs.
- Service reachable without upstream protection → not detectable by the service itself; covered
  by the mandatory operator documentation (FR-013).
- Non-ASCII content (umlauts, accents) → preserved unchanged through the service.

## Requirements *(mandatory)*

### Functional Requirements

**Conversion operations**

- **FR-001**: The service MUST offer every conversion provided by the core library — initially
  CHMED16A ↔ CH EMED (medication plan ↔ MedicationCard, prescription ↔ MedicationPrescription)
  from feature 001 — and MUST offer later conversions (CHMED23A, CARA EMED, eMediplan QR code, eRezept)
  without changes to the existing operations.
- **FR-002**: Callers MUST be able to specify per request the source format, the target format
  and the same conversion options as the CLI: strict mode, document language, confidentiality,
  patient identifier and author details (feature 001 FR-022/FR-023). Options that carry personal
  data (patient identifier, author details) MUST NOT be transmitted in the URL or in headers.
- **FR-003**: For identical input and options, the service MUST return the same output document
  and the same conversion report as the library and the CLI (byte-identical output document
  after base64 decoding).
- **FR-004**: Each response MUST contain the conversion outcome ("success", "success with
  warnings", "failed") and the machine-readable conversion report; successful responses MUST
  also contain the output document.
- **FR-005**: The service MUST be stateless: each request is processed independently, and the
  service MUST NOT persist, cache or queue request or response content beyond the request.
- **FR-006**: The interface MUST be a plain REST/JSON API (not a FHIR operation). The request is
  a JSON message containing the input document base64-encoded and the conversion options; the
  response is a JSON message containing outcome, conversion report and, on success, the output
  document base64-encoded together with its media type. Documents are transported as exact
  bytes and never re-serialized by the service.

**Failure handling**

- **FR-007**: The service MUST distinguish in its response status at least: malformed request,
  invalid input document, unsupported conversion, failed conversion (e.g. strict mode), request
  too large, timeout, and internal error.
- **FR-008**: Failure responses MUST contain machine-readable problem details with codes and
  human-readable messages and, whenever a conversion was attempted, the conversion report
  (constitution v1.4.1); they MUST NOT contain stack traces, internal details, input content or
  option values.
- **FR-009**: Internal errors MUST be identifiable through a correlation id that appears in the
  response and in the operational log.

**Discovery and documentation**

- **FR-010**: The service MUST provide capability information listing supported conversions,
  options and pinned specification versions.
- **FR-011**: The service MUST publish a machine-readable API description that covers all
  operations, request and response structures and failure responses.

**Security, privacy and operations**

- **FR-012**: The service is not required to provide transport encryption (TLS) itself;
  securing the network path is the operator's responsibility, and the operator documentation MUST state this
  explicitly (constitution v1.3.1).
- **FR-013**: The service MUST NOT implement its own authentication or authorization; access
  control is the operator's responsibility (API gateway, reverse proxy or network segmentation).
  The operator documentation MUST state explicitly that the service MUST NOT be exposed without
  such protection, because requests contain patient data.
- **FR-014**: Operational logs MUST record request metadata (time, operation, outcome, duration,
  correlation id) but MUST NOT contain request or response content or patient-identifying data.
- **FR-015**: The service MUST NOT call external services during conversion; validation
  resources MUST be available locally.
- **FR-016**: The service MUST provide health and readiness information; readiness MUST be
  negative while validation resources are unavailable.
- **FR-017**: Maximum request size, maximum decoded document size, conversion time limit and
  concurrency limits MUST be configurable by the operator.

### Key Entities

- **Conversion Request**: source and target format (path), JSON message with the base64-encoded
  input document and the conversion options (strict, language, confidentiality, patient
  identifier, author details).
- **Conversion Response**: outcome, conversion report and, on success, the base64-encoded output
  document with its media type.
- **Conversion Report**: as defined in feature 001 (entries with path, reason code, message,
  severity); identical for library, CLI and service.
- **Capability Statement**: supported source/target pairs, options and pinned specification
  versions of the running service.
- **Operational Log Entry**: time, operation, outcome, duration and correlation id, without
  content.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For 100% of the feature 001 reference corpus, service responses are identical to
  library results (output document and report).
- **SC-002**: With a warm service, a large realistic medication plan with 20 different entries is
  converted and returned end to end in under 5 seconds, and the upper bound of 100 different
  entries in under 30 seconds (dominated by the mandatory HL7 output validation, see feature 001
  SC-005); the service adds less than 1 second to the library's own conversion time.
- **SC-003**: With 20 simultaneous conversion requests with such 20-entry plans, no request returns
  another request's data; every request is either answered with 95% of the responses within 40
  seconds (warm service) or, if the configured capacity (`max-concurrent` + `queue-size`) is
  exhausted, rejected at once with `SERVICE_BUSY`.
- **SC-004**: An automated scan of logs and the service's storage after running the full test
  corpus finds zero occurrences of request content or patient-identifying data.
- **SC-005**: An integrator can generate a working client from the API description and complete
  a first successful conversion within 30 minutes.
- **SC-006**: 100% of the failure cases listed in FR-007 return their documented, distinct
  response status.

## Assumptions

- The service is a thin layer over the same core library used by the CLI (constitution
  Principle V); all conversion behaviour is defined in the respective conversion features.
- Conversions are synchronous request/response; asynchronous or batch processing is out of
  scope.
- The service is deployed and operated by the customer or a service provider in its own
  infrastructure (e.g. as a container); hosting by the project is out of scope.
- Access control and transport security (e.g. TLS) are provided by the operator's
  infrastructure (API gateway, reverse proxy, network segmentation); the service has no user
  or client management.
- Request and response messages are JSON; the embedded documents are base64-encoded and can be
  any form supported by the library (CHMED16A JSON, CHMED16A QR payload text, FHIR JSON, later
  binary forms such as QR images).
- SC-002/SC-003 were revised on 2026-10-29 and 2026-11-01 after measuring (Windows workstation
  with 16 logical cores, warm). The original limits of 3 s contradicted feature 001 and cannot be met while
  output validation is mandatory (constitution I). The earlier tests used repeated medications
  (100 entries: 6-8 s) and 2-entry plans for the parallel case, which understated the load; with
  different medications: 20 entries 3-4 s, 100 entries about 19 s, almost all of it HL7 output
  validation (the REST layer adds no measurable time). 20 simultaneous 20-entry plans took about
  24 s in total with 4, 8 and 16 conversions at a time: the validation gains no throughput above
  four, so `max-concurrent` defaults to at most 4. 100 entries are an upper bound that has to
  work rather than be fast; a real plan is expected to have about 10-20 entries. Measured later on
  a Linux server limited to 4 vCPUs (Ryzen 9 3950X; 2026-11-01): 20 entries 0.4 s, 100 entries
  1.9 s, 20 simultaneous 20-entry plans p95 7.9 s. The limits stay generous on purpose, as the
  operators' hardware is unknown.
- Reference hardware for operators (provisional, to be revised): 4 vCPU, 8 GB RAM, JVM 21 with
  default heap settings. With the default limits a service accepts 20 simultaneous conversions
  (4 running, 16 queued); further requests get `SERVICE_BUSY`.
- eRezept URLs and QR codes are handled by the eRezept feature; until then they are rejected as
  invalid input.
- The web framework is chosen in the plan of this feature and then applies project-wide
  (constitution v1.3.0).
- QR image upload and download become relevant with the eMediplan QR code feature and are not
  part of this specification.
