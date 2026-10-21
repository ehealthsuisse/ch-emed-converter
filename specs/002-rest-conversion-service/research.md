# Research: REST Conversion Service

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-10-18

Builds on feature 001 ([plan](../001-chmed16a-ch-emed-conversion/plan.md),
[research](../001-chmed16a-ch-emed-conversion/research.md)). Each entry: Decision / Rationale /
Alternatives considered.

## R1 — Web framework

- **Decision**: Spring Boot 4.1.x (current GA 4.1.1), Spring Web MVC on embedded Tomcat, pinned
  via the Spring Boot BOM in the parent POM. Per constitution v1.4.0 ("once chosen, the web
  framework applies to the whole project"), Spring Boot is from now on the project's web
  framework. Spring dependencies are allowed **only** in the new `converter-rest` module.
- **Rationale**: Decided by the user (2026-10-18). Actuator gives health/readiness/metrics,
  built-in RFC 9457 `ProblemDetail` support, wide operational familiarity, used across the HAPI
  ecosystem (matchbox, HAPI JPA server). Spring Boot 4 supports Java 17+, so Java 21 fits.
- **Alternatives**: JAX-RS (Jersey 3 on Jetty 12) — leaner, but health checks, metrics and
  OpenAPI would have to be assembled by hand; Spring Boot 3.5 (rejected: older generation).

## R2 — Resource design

- **Decision**: `POST /api/v1/conversions/{from}/{to}` with the input document as the raw request
  body; options as query parameters (`strict`). `GET /api/v1/capabilities`. API description at
  `GET /api/v1/openapi.yaml`.
- **Rationale**: The body stays the native document (CHMED16A JSON, FHIR JSON, or the textual
  CHMED16A QR payload as `text/plain`, mirroring FR-001 of feature 001), so clients send
  files as they are; an unsupported pair is naturally `404` on a non-existent resource; new
  formats add path values without changing existing operations (FR-001).
- **Alternatives**: JSON request envelope containing the document (rejected: forces clients to
  escape/embed documents); multipart (rejected: unnecessary for JSON-only input).

## R3 — Response format and byte identity (FR-003, FR-006)

- **Decision**: Success: `application/json` envelope `{outcome, correlationId, report, output}`,
  where `output` is the library's output bytes inserted **verbatim** as a raw JSON value (no
  parse/re-serialize). Failures: `application/problem+json` (RFC 9457) extended with `errorType`,
  `correlationId` and, when a conversion ran, the full `report`.
- **Rationale**: Raw embedding guarantees the document is byte-identical to the library result;
  RFC 9457 is the standard machine-readable error format and is natively supported by Spring.
- **Note**: Spring Boot 4 uses Jackson 3 for its own JSON; HAPI and `format-chmed16a` use
  Jackson 2 internally. Both coexist (different packages); because `output` is embedded as raw
  bytes, the REST layer never re-serializes documents.
- **Alternatives**: Base64 `output` (rejected: spec requires native form); re-serializing the
  document as a JSON node (rejected: may change bytes, number formats, key order).

## R4 — Failure status mapping (FR-007, SC-006)

- **Decision**: The "response status" of the spec is the pair (HTTP status, `errorType`):

  | Case | HTTP | `errorType` |
  |------|------|-------------|
  | malformed request (bad option value, empty body) | 400 | `MALFORMED_REQUEST` |
  | unsupported source/target pair or unknown format | 404 | `UNSUPPORTED_CONVERSION` |
  | request larger than limit | 413 | `REQUEST_TOO_LARGE` |
  | content type not accepted for the source format (not JSON; `text/plain` only for `chmed16a`) | 415 | `UNSUPPORTED_MEDIA_TYPE` |
  | invalid input document (PARSE / VALIDATE_INPUT errors, wrong CHMED version, unsupported document type) | 422 | `INVALID_INPUT` |
  | conversion failed (strict-mode loss, missing required data, output validation error) | 422 | `CONVERSION_FAILED` |
  | concurrency limit reached | 503 | `SERVICE_BUSY` (with `Retry-After`) |
  | validation resources not ready | 503 | `NOT_READY` |
  | conversion exceeded time limit | 504 | `CONVERSION_TIMEOUT` |
  | unexpected error | 500 | `INTERNAL_ERROR` |

- **Rationale**: Uses the closest standard HTTP semantics; `errorType` makes cases that share a
  status code unambiguous for clients.

## R5 — Statelessness, privacy and logging (FR-005, FR-014, constitution IV)

- **Decision**: No session, no Spring cache, no request/response body logging, no Tomcat access
  log with bodies; `server.error.include-stacktrace=never`, `include-message=never`. One
  servlet filter writes one structured log line per request (time, method, route template,
  status, outcome, duration, input size, correlation id). Correlation id: incoming
  `X-Correlation-Id` accepted if it matches `[A-Za-z0-9-]{1,64}`, otherwise a new random UUID;
  returned as header and in the body.
- **Rationale**: Constitution IV; correlation ids are operational metadata and not part of the
  deterministic document output.

## R6 — Limits, timeouts and concurrency (FR-017)

- **Decision**: Configuration properties `converter.rest.max-request-size` (default 5 MB),
  `converter.rest.timeout` (default 10 s), `converter.rest.max-concurrent` (default = available
  processors) and `converter.rest.queue-size` (default 2 × max-concurrent). Size is enforced by a
  filter (Content-Length and a counting input stream). Conversions run on a bounded
  `ThreadPoolExecutor`; a full queue → `SERVICE_BUSY`, `Future.get(timeout)` → `CONVERSION_TIMEOUT`
  (task cancelled, no partial output). Request threads use virtual threads
  (`spring.threads.virtual.enabled=true`).
- **Rationale**: Conversion and validation are CPU-bound; a bounded pool protects the JVM, virtual
  threads keep waiting request threads cheap.

## R7 — Health and readiness (FR-016)

- **Decision**: Spring Boot Actuator with only the `health` endpoint exposed
  (`/actuator/health/liveness`, `/actuator/health/readiness`, probes enabled); a custom
  `HealthIndicator` in the readiness group reports DOWN until the shared `Converter` (warm
  validation context with vendored IG packages) is built, and DOWN if building failed. While not
  ready, conversion requests get `503 NOT_READY`. Actuator may run on a separate management port
  (`management.server.port`).
- **Rationale**: Standard Kubernetes/container probes; no unvalidated output while packages are
  missing.

## R8 — API description (FR-011)

- **Decision**: Contract-first: [contracts/openapi.yaml](./contracts/openapi.yaml) is the source
  of truth, packaged in the JAR and served at `/api/v1/openapi.yaml`. Contract tests validate
  every request/response of the test suite against it (Atlassian `swagger-request-validator` for
  MockMvc). No generated UI is shipped.
- **Rationale**: The spec is written before the code (constitution III); a static contract cannot
  drift silently because tests enforce it.
- **Alternatives**: springdoc-generated description (rejected: description follows the code, not
  the reverse; extra runtime dependency).

## R9 — Capability information (FR-010)

- **Decision**: `GET /api/v1/capabilities` returns converter version, supported pairs (from
  `Converter.supportedConversions()`), options with defaults and `Converter.pinnedSpecifications()`.
- **Rationale**: Single source of truth is the library; new format modules appear automatically.

## R10 — Packaging and operation

- **Decision**: Executable Spring Boot JAR `ch-emed-converter-rest.jar`; OCI image built with
  `spring-boot:build-image` (Paketo buildpacks, Java 21). Operator documentation
  `docs/operations/rest-service.md` states explicitly: no authentication and no TLS in the
  service; MUST run behind an API gateway / reverse proxy or in a protected network; no data is
  stored.
- **Rationale**: Spec assumption (operated by customer, e.g. as container); FR-012/FR-013.

## R11 — Testing

- **Decision**: Spring Boot slice/integration tests with MockMvc (contract, status mapping,
  limits); a parity test runs the 001 reference corpus through the service and the library and
  compares output bytes and report (SC-001); a load test with 20 parallel requests checks
  isolation and p95 < 3 s (SC-003); a log-capture test scans for request content and patient
  values (SC-004); end-to-end smoke test against the packaged JAR.
- **Rationale**: Constitution III; each success criterion has an automated check.

## Sources

- Spring Boot project page (current version 4.1.1): https://spring.io/projects/spring-boot
- RFC 9457 Problem Details for HTTP APIs: https://www.rfc-editor.org/rfc/rfc9457
