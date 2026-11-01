---

description: "Task list for feature 002 - REST Conversion Service"
---

# Tasks: REST Conversion Service

**Input**: Design documents from `/specs/002-rest-conversion-service/`

**Prerequisites**: plan.md, spec.md, research.md (R1-R12), data-model.md, contracts/openapi.yaml, quickstart.md; feature 001 modules (`converter-core`, `converter-validation`, `format-chmed16a`, `converter-cli`) build green

**Tests**: INCLUDED. Constitution Principle III (test-first) and research R8/R11 require contract, parity, load and privacy tests. Write each test first and confirm it fails before implementing.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested independently.

**Revision 2026-10-29**: JSON request message with base64 document and the full CLI option set (research R2, R3, R12); constitution v1.4.1; findings C1, C2, H1-H3, M1-M4 of `/speckit-analyze`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story (US1-US4) the task belongs to
- All paths are relative to the repository root

## Path Conventions

- Production code: `converter-rest/src/main/java/ch/emed/converter/rest/<package>/`
- Resources: `converter-rest/src/main/resources/`
- Tests: `converter-rest/src/test/java/ch/emed/converter/rest/<package>/`
- Spring dependencies are allowed ONLY in `converter-rest` (plan, constitution V)
- The REST layer handles documents only as `byte[]` and base64 strings; it never parses or re-serializes a document (research R2/R3)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: New Maven module and build wiring

- [X] T001 Add the Spring Boot 4.1.x BOM to `dependencyManagement` and register module `converter-rest` in `pom.xml`; keep all other modules free of Spring dependencies — *Done 2026-10-29: the Spring Boot BOM (4.1.1) is imported in `converter-rest/pom.xml` instead of the parent, so the versions of the other modules stay untouched.*
- [X] T002 Create `converter-rest/pom.xml` (Java 21, dependencies: spring-boot-starter-web, spring-boot-starter-actuator, spring-boot-starter-validation, `converter-core`, runtime `converter-validation` and `format-chmed16a`; test: spring-boot-starter-test, Atlassian swagger-request-validator-mockmvc, `converter-cli`; spring-boot-maven-plugin producing `ch-emed-converter-rest.jar`; changed on 2026-10-29 to a thin JAR with `lib/` like the CLI, the plugin is kept for `build-image` only) — *Done: Boot 4 names the starter `spring-boot-starter-webmvc`; the Atlassian validator moved to `com.atlassian.oai:openapi-request-validator-mockmvc` 3.0.0 (needs `json-schema-validator` 2.0.1, test scope); Jackson 2 pinned to the parent version (byte identity).*
- [X] T003 [P] Create `converter-rest/src/main/resources/application.yaml` with defaults from data-model §8 (`converter.rest.max-document-size=5MB`, `max-request-size=8MB`, `timeout=40s` (raised from 10s, see research R6), `max-concurrent`, `queue-size`), `spring.threads.virtual.enabled=true`, `server.error.include-stacktrace=never`, `server.error.include-message=never`, Actuator exposing only `health` with probes enabled
- [X] T004 [P] Configure Maven build step copying `specs/002-rest-conversion-service/contracts/openapi.yaml` to `converter-rest/src/main/resources/static-api/openapi.yaml` at build time (research R8) in `converter-rest/pom.xml` — *Done: copied to `target/classes/static-api/openapi.yaml` (no second copy in `src`).*
- [X] T005 Create the Spring Boot entry point `converter-rest/src/main/java/ch/emed/converter/rest/RestApplication.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared building blocks that every user story needs

**CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 [P] Implement `ConverterRestProperties` (`converter.rest.*`: max-document-size > 0, max-request-size ≥ 4/3 × max-document-size, timeout > 0, max-concurrent ≥ 1, queue-size ≥ 0; defaults per data-model §8) with bean validation in `converter-rest/src/main/java/ch/emed/converter/rest/config/ConverterRestProperties.java`
- [X] T007 [P] Implement `ErrorType` enum with HTTP status mapping from data-model §6 (`MALFORMED_REQUEST` 400, `UNSUPPORTED_CONVERSION` 404, `REQUEST_TOO_LARGE` 413, `UNSUPPORTED_MEDIA_TYPE` 415, `INVALID_INPUT` 422, `CONVERSION_FAILED` 422, `SERVICE_BUSY` 503, `NOT_READY` 503, `CONVERSION_TIMEOUT` 504, `INTERNAL_ERROR` 500) in `converter-rest/src/main/java/ch/emed/converter/rest/error/ErrorType.java`
- [X] T008 [P] Implement `CorrelationIdFilter`: accept `X-Correlation-Id` matching `[A-Za-z0-9-]{1,64}`, otherwise generate a UUID; expose it on the request and as response header in `converter-rest/src/main/java/ch/emed/converter/rest/web/CorrelationIdFilter.java`
- [X] T009 Implement `ConverterBeans`: build one shared, warm `Converter` (validation context with vendored IG packages) and track service state STARTING/READY/FAILED (data-model §9) in `converter-rest/src/main/java/ch/emed/converter/rest/config/ConverterBeans.java` — *Done: state kept in `config/ConverterHolder.java`; without a CH EMED validator the state is FAILED.*
- [X] T010 Implement `ProblemFactory` building RFC 9457 `application/problem+json` responses (`type` = `urn:ch-emed-converter:problem:<error-type-kebab>`, fixed `title`, `status`, `detail` without input content, option values, patient values or stack traces, `errorType`, `correlationId`, `report` whenever a conversion was attempted (constitution v1.4.1), `supportedConversions` for `UNSUPPORTED_CONVERSION`) in `converter-rest/src/main/java/ch/emed/converter/rest/error/ProblemFactory.java` (depends on T007, T008)
- [X] T011 Implement the global exception handler: unexpected exceptions → `INTERNAL_ERROR` 500 with correlation id and no internals; unreadable/non-JSON message, unknown property, wrong JSON type → `MALFORMED_REQUEST` 400 with the property path only; wrong `Content-Type` → `UNSUPPORTED_MEDIA_TYPE` 415, in `converter-rest/src/main/java/ch/emed/converter/rest/error/GlobalExceptionHandler.java` (depends on T010)
- [X] T012 [P] Implement `RequestLogFilter` writing exactly one structured log line per request with timestamp, correlationId, method, route template, from, to, status, errorType, outcome, durationMs, requestBytes, documentBytes - never body content, option values, report messages, patient data or eRezept signature data (data-model §10, FR-014) in `converter-rest/src/main/java/ch/emed/converter/rest/web/RequestLogFilter.java`
- [X] T013 [P] Configure the Spring JSON mapper for the REST messages to fail on unknown properties (`additionalProperties: false` in the contract) in `converter-rest/src/main/java/ch/emed/converter/rest/config/JsonConfig.java` — *Done via `spring.jackson.deserialization.fail-on-unknown-properties: true` in `application.yaml` instead of a `JsonConfig` class.*

**Checkpoint**: Foundation ready - user stories can begin.

---

## Phase 3: User Story 1 - Convert a document over HTTP (Priority: P1) MVP

**Goal**: `POST /api/v1/conversions/{from}/{to}` with `{document, options}` converts every supported document and returns outcome, report and the base64-encoded library output.

**Independent Test**: Send each official CHMED16A and CH EMED example of feature 001 with the matching target; each decoded `output` and report equals library and CLI results for the same options (SC-001).

### Tests for User Story 1 (write first, must fail)

- [X] T014 [P] [US1] Contract test: successful `chmed16a`→`ch-emed` and `ch-emed`→`chmed16a` requests/responses validate against `contracts/openapi.yaml`, incl. a CHMED16A QR payload (`plan-basic.qr.txt`) as `document` and `outputMediaType` `application/fhir+json` / `application/json`, in `converter-rest/src/test/java/ch/emed/converter/rest/contract/ConversionContractTest.java`
- [X] T015 [P] [US1] Parity test: run the whole feature 001 reference corpus through the service (MockMvc), the library and the CLI (`converter-cli` in-process) with identical options; assert decoded `output` byte-identical and reports equal (SC-001, FR-003); run every option (`strict`, `language`, `confidentiality`, `patientIdentifier`, `author`) at least once, in `converter-rest/src/test/java/ch/emed/converter/rest/parity/ServiceLibraryParityTest.java` — *Done: CHMED16A plans of the corpus lack the author details CH EMED needs, so the corpus runs twice (defaults: parity of failures; with `author`: success path). 75 cases.*
- [X] T016 [P] [US1] Test: success with warnings returns `SUCCESS_WITH_WARNINGS` and every warning in the report; omitted `options` yields the same result as `ConversionOptions.defaults()`, in `converter-rest/src/test/java/ch/emed/converter/rest/api/ConversionOutcomeTest.java`
- [X] T017 [P] [US1] Create synthetic fixture `converter-rest/src/test/resources/fixtures/plan-no-ids.json` (copy of `format-chmed16a/src/test/resources/fixtures/chmed16a/plan-basic.json` without `Patient.Ids`, synthetic data only) and a test that it converts with `options.patientIdentifier` and an `author` object, decoded output equal to the CLI with `--patient-id`/`--author`, in `converter-rest/src/test/java/ch/emed/converter/rest/api/ConversionOptionsTest.java`

### Implementation for User Story 1

- [X] T018 [P] [US1] Implement request records per data-model §2/§3: `ConversionRequestMessage(String document, RequestOptions options)` and `RequestOptions(Boolean strict, String language, String confidentiality, PatientIdentifierDto patientIdentifier, AuthorDto author)` with nested `OrganizationDto(name, street, zip, city, country)` in `converter-rest/src/main/java/ch/emed/converter/rest/model/ConversionRequestMessage.java` and `converter-rest/src/main/java/ch/emed/converter/rest/model/RequestOptions.java` — *Done: nested records are named `RequestOptions.PatientIdentifier`, `.Author`, `.Organization`.*
- [X] T019 [P] [US1] Implement `ConversionResponse` record (`outcome` SUCCESS/SUCCESS_WITH_WARNINGS only, `correlationId`, `report`, `output` base64 string, `outputMediaType`) per data-model §4 in `converter-rest/src/main/java/ch/emed/converter/rest/model/ConversionResponse.java`
- [X] T020 [US1] Implement `RequestOptionsMapper`: start from `ConversionOptions.defaults()` and override only present values via `withStrict`, `withDefaultLanguage` (must be in `ConversionOptions.LANGUAGES`), `withConfidentiality(Confidentiality.valueOf(upper-case))`, `withPatientIdentifier(new PatientIdentifier(system, value))`, `withAuthor(new AuthorDetails(gln, given, family, organization == null ? null : new OrganizationDetails(...)))`; keep `validateOutput=true`, `idStrategy`, `clock`, `maxDecodedSize` at library defaults; invalid values → `MALFORMED_REQUEST` naming the property path, never the value (research R12, same rules as `converter-cli/src/main/java/ch/emed/converter/cli/ConvertCommand.java`) in `converter-rest/src/main/java/ch/emed/converter/rest/api/RequestOptionsMapper.java` (depends on T018)
- [X] T021 [US1] Implement `BoundedConversionExecutor`: `ThreadPoolExecutor` with `max-concurrent` threads and `queue-size` queue, `Future.get(timeout)`, cancellation without partial output (research R6) in `converter-rest/src/main/java/ch/emed/converter/rest/execution/BoundedConversionExecutor.java` (depends on T006)
- [X] T022 [US1] Implement `ConversionController` `POST /api/v1/conversions/{from}/{to}` (consumes `application/json`): base64-decode `document` (RFC 4648 §4) to `byte[]`, build `ConversionRequest(source, target, bytes, mappedOptions)`, delegate to the shared `Converter` via the executor, base64-encode `ConversionResult.output()`, set `outputMediaType` from the target format, return `ConversionResponse` with `X-Correlation-Id` in `converter-rest/src/main/java/ch/emed/converter/rest/api/ConversionController.java` (depends on T009, T019, T020, T021) — *Done; failure classification and request validation completed in T028-T030.*

**Checkpoint**: US1 works and is demonstrable on its own (quickstart Scenario 1).

---

## Phase 4: User Story 2 - Clear, machine-readable failures (Priority: P2)

**Goal**: Every failure returns the documented HTTP status + `errorType`, a report whenever a conversion was attempted, and no patient data or option values.

**Independent Test**: Run the failure table of quickstart Scenario 2; every case yields its distinct status (SC-006).

### Tests for User Story 2 (write first, must fail)

- [X] T023 [P] [US2] Status-mapping contract test covering all 10 `ErrorType`s from data-model §6 against `contracts/openapi.yaml`: message not JSON 400, `document` missing/empty/invalid base64 400, unknown property 400, invalid option value 400, unparseable document (`invalid-json.txt`) 422 `INVALID_INPUT` with PARSE report, unsupported pair 404 with `supportedConversions`, invalid format name `ch_emed!` 404 (never 500; skip request-schema validation for this case), strict loss 422 `CONVERSION_FAILED` without `output`, `plan-no-ids.json` without `patientIdentifier` 422 `CONVERSION_FAILED` with `MISSING_REQUIRED_DATA`, `Content-Type` `application/xml`/`text/plain` 415, busy 503 with `Retry-After`, timeout 504, internal 500; report present exactly for 422, in `converter-rest/src/test/java/ch/emed/converter/rest/contract/FailureStatusContractTest.java` — *Done: split into `FailureStatusContractTest` (request/input failures, real converter) and `ServiceStateStatusContractTest` (busy, not ready, timeout, internal error with mocked converter and executor; no validation context needed). Responses are validated against the contract with request validation switched off.*
- [X] T024 [P] [US2] Test: failure reports equal the library's report for the same input and options; detected-format mismatch (`chmed23a-plan.json` sent as CHMED16A) yields `INVALID_INPUT` naming the detected format; `erezept-url.txt` yields `INVALID_INPUT` and no signature data appears in response or log, in `converter-rest/src/test/java/ch/emed/converter/rest/api/FailureReportTest.java`
- [X] T025 [P] [US2] Test: message larger than `max-request-size` (via Content-Length and via counting stream) and decoded document larger than `max-document-size` return 413 `REQUEST_TOO_LARGE` before conversion; `qr-oversized.txt` (decompresses > 5 MB) returns 422 `INVALID_INPUT` from the library's `maxDecodedSize`, in `converter-rest/src/test/java/ch/emed/converter/rest/web/RequestSizeLimitTest.java` — *Done: the counting stream is tested at filter level (MockMvc always sends a Content-Length).*
- [X] T026 [P] [US2] Test: problem responses contain no stack trace, input fragment, option value (patient identifier, author name/GLN) or patient value (US2 scenario 4, FR-008) in `converter-rest/src/test/java/ch/emed/converter/rest/error/ProblemLeakTest.java` — *Done: found and fixed a leak - the name of an unknown property comes from the caller and was echoed in `detail`; now only contract property names are echoed, an unknown one becomes `(unknown property)`.*

### Implementation for User Story 2

- [X] T027 [P] [US2] Implement `RequestSizeLimitFilter` (Content-Length check plus counting input stream, `max-request-size`) producing `REQUEST_TOO_LARGE` in `converter-rest/src/main/java/ch/emed/converter/rest/web/RequestSizeLimitFilter.java`
- [X] T028 [US2] Add request validation to `ConversionController`: `FormatId.fromExternalName` throws `IllegalArgumentException` for an invalid name → `UNSUPPORTED_CONVERSION` 404 with `supportedConversions`, as for unknown or unsupported pairs; missing/empty `document` or invalid base64 → `MALFORMED_REQUEST`; decoded size > `max-document-size` → `REQUEST_TOO_LARGE`, in `converter-rest/src/main/java/ch/emed/converter/rest/api/ConversionController.java` (depends on T022)
- [X] T029 [US2] Classify a `FAILED` library result: any ERROR entry in phase `PARSE` or `VALIDATE_INPUT` → `INVALID_INPUT`, otherwise `CONVERSION_FAILED` (incl. `MISSING_REQUIRED_DATA`); include the full report and no `output` (data-model §6) in `converter-rest/src/main/java/ch/emed/converter/rest/error/FailureClassifier.java` and use it from `ConversionController` (depends on T010)
- [X] T030 [US2] Map `BoundedConversionExecutor` rejection to `SERVICE_BUSY` (+ `Retry-After`) and timeout to `CONVERSION_TIMEOUT`; refuse conversions with `NOT_READY` while service state is not READY in `converter-rest/src/main/java/ch/emed/converter/rest/execution/BoundedConversionExecutor.java` and `ConversionController.java` (depends on T021, T028) — *Done: plus `execution/BoundedConversionExecutorTest` for real rejection and cancellation; `Retry-After: 1` for both 503 types.*

**Checkpoint**: US1 and US2 work independently.

---

## Phase 5: User Story 3 - Discover capabilities and API description (Priority: P3)

**Goal**: Clients can query supported conversions, options, spec versions and limits, and fetch the OpenAPI description.

**Independent Test**: Compare `/api/v1/capabilities` and `/api/v1/openapi.yaml` with the conversions and options actually offered (quickstart Scenario 3).

### Tests for User Story 3 (write first, must fail)

- [X] T031 [P] [US3] Contract test for `GET /api/v1/capabilities`: pairs match `Converter.supportedConversions()`, options `strict`, `language`, `confidentiality`, `patientIdentifier`, `author` with defaults, pinned `ch.fhir.ig.ch-emed#6.0.0`, limits `maxRequestSizeBytes`, `maxDocumentSizeBytes`, `timeoutSeconds` reflect configuration, in `converter-rest/src/test/java/ch/emed/converter/rest/contract/CapabilitiesContractTest.java` — *Done; while not ready, `/capabilities` answers 503 `NOT_READY` (added to the contract, tested in `ServiceStateStatusContractTest`).*
- [X] T032 [P] [US3] Test: `GET /api/v1/openapi.yaml` returns content equal to `specs/002-rest-conversion-service/contracts/openapi.yaml` and every offered operation is described in `converter-rest/src/test/java/ch/emed/converter/rest/contract/OpenApiServedTest.java`

### Implementation for User Story 3

- [X] T033 [P] [US3] Implement `CapabilitiesResponse` record (`converterVersion`, `conversions`, `options`, `specifications`, `limits`) per data-model §7 in `converter-rest/src/main/java/ch/emed/converter/rest/model/CapabilitiesResponse.java`
- [X] T034 [US3] Implement `CapabilitiesController` `GET /api/v1/capabilities` using `Converter.supportedConversions()` and `Converter.pinnedSpecifications()` in `converter-rest/src/main/java/ch/emed/converter/rest/api/CapabilitiesController.java` (depends on T033)
- [X] T035 [P] [US3] Implement `OpenApiController` serving `static-api/openapi.yaml` as `application/yaml` at `GET /api/v1/openapi.yaml` in `converter-rest/src/main/java/ch/emed/converter/rest/api/OpenApiController.java` — *Done: serves the copy packaged from the spec (T004).*

**Checkpoint**: US1-US3 independently functional.

---

## Phase 6: User Story 4 - Operate the service safely (Priority: P4)

**Goal**: Health/readiness for monitoring, no patient data in logs or storage, no outbound calls, documented operation model.

**Independent Test**: Start service, query health/readiness, run the corpus, scan logs and storage for content, block outbound network (quickstart Scenario 4, SC-004).

### Tests for User Story 4 (write first, must fail)

- [X] T036 [P] [US4] Test: readiness `UP` after startup; with profile `missing-packages` readiness `DOWN` and conversions return 503 `NOT_READY` (FR-016) in `converter-rest/src/test/java/ch/emed/converter/rest/health/ReadinessTest.java` and `converter-rest/src/test/resources/application-missing-packages.yaml` — *Done without a `missing-packages` profile: the nested test replaces the converter holder via `@TestBean` with one whose build fails like a missing IG package, so no real validation context is built.*
- [X] T037 [P] [US4] Privacy test `LogLeakTest`: capture logs while running the full corpus with all options set; assert zero occurrences of fixture patient values, document fragments, base64 document strings, option values (patient identifier, author) and eRezept signature data, and no files written to disk (SC-004, FR-005, FR-014) in `converter-rest/src/test/java/ch/emed/converter/rest/privacy/LogLeakTest.java` — *Done; uncovered that the CLI's `logback.xml` (WARN) won on the test classpath and that Spring Boot's `base.xml` would write `spring.log` to the temp directory. Added `logback-spring.xml` (console only, HAPI/HL7 at WARN) and `logback-test.xml`.*
- [X] T038 [P] [US4] Test: internal error response and log line carry the same correlation id and no stack trace/input (FR-009) in `converter-rest/src/test/java/ch/emed/converter/rest/privacy/CorrelationIdTest.java`
- [X] T039 [P] [US4] Test `NoOutboundNetworkTest`: run the corpus with a `ProxySelector`/`SocketFactory` that rejects and records every outbound connection; assert zero attempts (FR-015, constitution IV) in `converter-rest/src/test/java/ch/emed/converter/rest/privacy/NoOutboundNetworkTest.java` — *Done via the default `ProxySelector` (covers URLConnection, java.net.http, OkHttp); raw sockets are not covered.*

### Implementation for User Story 4

- [X] T040 [US4] Implement `ConverterReadinessIndicator` (DOWN until shared `Converter` is built or if build failed; part of the readiness group) in `converter-rest/src/main/java/ch/emed/converter/rest/health/ConverterReadinessIndicator.java` (depends on T009)
- [X] T041 [P] [US4] Write operator documentation: no authentication and no TLS in the service, MUST run behind API gateway/reverse proxy or in a protected network, no data stored, request bodies contain patient data (gateway must not log bodies), limits (`converter.rest.max-request-size`, `max-document-size`, `timeout`, `max-concurrent`, `queue-size`) and their relation to the library's fixed `maxDecodedSize` (5 MB), probes, optional management port, container usage, in `docs/operations/rest-service.md` (FR-012, FR-013, FR-017)

**Checkpoint**: All user stories independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T042 [P] Load test: 20 parallel requests with different patients; each response contains only its own patient, with 20 different medications per plan, every request is answered with p95 < 30 s or rejected at once with `SERVICE_BUSY` beyond the configured capacity (SC-003), a 20-medication plan converts in < 5 s and a 100-medication plan in < 30 s end to end with less than 1 s above the library time (SC-002), in `converter-rest/src/test/java/ch/emed/converter/rest/load/ConversionLoadTest.java` — *Done after revising SC-002/SC-003 on 2026-10-29: the original limits of 3 s failed (100 medications 6-8 s, 20 parallel p95 4-6 s) because of the HL7 output validation in the library and contradicted feature 001 SC-005b; they were aligned with feature 001. Revised again on 2026-11-01: realistic plans (`load/PlanGenerator`, different medications) instead of copies and 2-entry plans.*
- [X] T043 [P] Test: non-ASCII content (umlauts, accents, `plan-non-ascii.json`) is preserved unchanged through base64 transport in `converter-rest/src/test/java/ch/emed/converter/rest/parity/NonAsciiPreservationTest.java`
- [X] T044 End-to-end smoke test against the packaged JAR (readiness, one conversion each direction, one failure) in `converter-rest/src/test/java/ch/emed/converter/rest/e2e/PackagedJarSmokeTest.java` — *Done as `PackagedJarSmokeIT` (failsafe, runs after `package`; helper `PackagedJar` starts the JAR once on a free port).*
- [X] T045 Generated-client test (SC-005): generate a Java client from `contracts/openapi.yaml` with openapi-generator-maven-plugin in test scope (`converter-rest/pom.xml`) and perform one successful conversion against the packaged JAR in `converter-rest/src/test/java/ch/emed/converter/rest/e2e/GeneratedClientTest.java` (depends on T044) — *Done as `GeneratedClientIT` with openapi-generator 7.26.0 (`java`/`native`, test scope); OpenAPI 3.1 support of the generator is beta but sufficient.*
- [X] T046 Configure `spring-boot:build-image` (Paketo, Java 21) in `converter-rest/pom.xml` and verify the image starts and converts without internet access (quickstart Scenario 6) — *Done 2026-10-29: configured in `converter-rest/pom.xml` (image `ch-emed-converter-rest:<version>`, `BP_JVM_VERSION=21`); built with Rancher Desktop and verified in an internal Docker network without internet access (results in quickstart.md, scenario 6).*
- [X] T047 [P] Update `README.md`, `CHANGELOG.md` and `docs/architecture.md` with the REST module, the request/response format (base64 documents, options) and a link to `docs/operations/rest-service.md`
- [X] T048 Run all quickstart scenarios 1-6 in `specs/002-rest-conversion-service/quickstart.md` and record deviations — *Done; results and deviations in quickstart.md, section "Validation run 2026-10-29".*

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup; blocks all user stories
- **US1 (Phase 3)**: depends on Foundational; MVP
- **US2 (Phase 4)**: depends on Foundational; extends `ConversionController` from US1 (T028 after T022); T023/T024 use fixture T017
- **US3 (Phase 5)**: depends on Foundational and `ConverterBeans` (T009); independent of US1/US2
- **US4 (Phase 6)**: depends on Foundational; T036 needs T030 for the `NOT_READY` check
- **Polish (Phase 7)**: after the desired stories are complete; T045 after T044

### Within each story

Tests first (must fail) → models → mapper/executor → controller → integration.

### Parallel opportunities

- Setup: T003, T004 in parallel after T002
- Foundational: T006, T007, T008, T012, T013 in parallel; then T010, T011
- US1 tests T014-T017 together, models T018/T019 together; US2 tests T023-T026 together; US3 and US4 can be built by different people in parallel once Foundational is done

## Parallel Example: User Story 1

```text
Task: T014 ConversionContractTest
Task: T015 ServiceLibraryParityTest
Task: T016 ConversionOutcomeTest
Task: T017 plan-no-ids fixture + ConversionOptionsTest
Task: T018 ConversionRequestMessage / RequestOptions records
Task: T019 ConversionResponse record
```

## Implementation Strategy

### MVP first

1. Phase 1 Setup → Phase 2 Foundational
2. Phase 3 (US1) → validate with quickstart Scenario 1 and the parity test → demo

### Incremental delivery

1. US1 (convert) → US2 (failure handling; needed before unattended use) → US3 (discovery) → US4 (operations; needed for production)
2. Polish: load, E2E, generated client, container, docs

## Notes

- The service never parses or re-serializes a document; byte identity with library and CLI after base64 decoding is the core invariant (R2/R3).
- Patient identifier and author travel only in the request body; they never appear in URLs, headers, logs or problem details (R12).
- No auth, no TLS, no persistence in the service; document this, do not implement it.
- Commit after each task or logical group; stop at any checkpoint to validate independently.
