# Research: REST Conversion Service

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-10-18

Builds on feature 001 ([plan](../001-chmed16a-ch-emed-conversion/plan.md),
[research](../001-chmed16a-ch-emed-conversion/research.md)). Each entry: Decision / Rationale /
Alternatives considered.

## R1 — Web framework

- **Decision**: Spring Boot 4.1.x (current GA 4.1.1), Spring Web MVC on embedded Tomcat, pinned
  via the Spring Boot BOM in the parent POM. Per constitution v1.4.1 ("once chosen, the web
  framework applies to the whole project"), Spring Boot is from now on the project's web
  framework. Spring dependencies are allowed **only** in the new `converter-rest` module.
- **Rationale**: Decided by the user (2026-10-18). Actuator gives health/readiness/metrics,
  built-in RFC 9457 `ProblemDetail` support, wide operational familiarity, used across the HAPI
  ecosystem (matchbox, HAPI JPA server). Spring Boot 4 supports Java 17+, so Java 21 fits.
- **Alternatives**: JAX-RS (Jersey 3 on Jetty 12) — leaner, but health checks, metrics and
  OpenAPI would have to be assembled by hand; Spring Boot 3.5 (rejected: older generation).

## R2 — Resource design

- **Decision** (revised 2026-10-29): `POST /api/v1/conversions/{from}/{to}` with an
  `application/json` request message `{document, options}`; `document` is the input document
  base64-encoded (RFC 4648 §4, standard alphabet with padding), `options` the conversion options
  (R12). `GET /api/v1/capabilities`. API description at `GET /api/v1/openapi.yaml`.
- **Rationale**: Decided by the user (2026-10-29). One message format for every input form
  (CHMED16A JSON, CHMED16A QR payload text, FHIR JSON, later QR images); options with personal
  data travel in the body instead of URL or headers (FR-002); base64 delivers the exact input
  bytes to the library, so the service never re-serializes a document; a single, strictly typed
  schema eases client generation (SC-005). An unsupported pair remains `404` on a non-existent
  resource; new formats add path values without changing existing operations (FR-001). The
  library detects the input form (JSON or QR payload) itself, so no media type field is needed
  in the request.
- **Trade-offs accepted**: ~33 % larger requests (limits refer to the decoded document, R6);
  documents not human-readable in the message (quickstart shows `base64`); both encoded and
  decoded document briefly in memory (negligible at 5 MB).
- **Alternatives**: Raw document as request body with options as query parameters/headers
  (original decision; rejected: patient identifier and author would appear in URLs or headers
  and are logged by proxies); multipart with a document part and an options part (rejected:
  more complex for clients, two content models); JSON envelope with the document embedded as
  JSON object (rejected: requires re-serialization of the input, may change bytes).

## R3 — Response format and byte identity (FR-003, FR-006)

- **Decision** (revised 2026-10-29): Success: `application/json` message
  `{outcome, correlationId, report, output, outputMediaType}`, where `output` is the library's
  output bytes base64-encoded (RFC 4648 §4) and `outputMediaType` the media type of the decoded
  document (`application/fhir+json` for `ch-emed`, `application/json` for `chmed16a`). Failures:
  `application/problem+json` (RFC 9457) extended with `errorType`, `correlationId` and, when a
  conversion was attempted, the full `report` (constitution v1.4.1).
- **Rationale**: Base64 keeps the document byte-identical to the library result all the way to
  the client (a client's JSON parser cannot alter it), mirrors the request, and allows binary
  outputs (QR images) later without a new operation; RFC 9457 is the standard machine-readable
  error format and is natively supported by Spring.
- **Note**: Spring Boot 4 uses Jackson 3 for its own JSON; HAPI and `format-chmed16a` use
  Jackson 2 internally. Both coexist (different packages); the REST layer handles documents only
  as byte arrays and never parses them.
- **Alternatives**: Output embedded verbatim as raw JSON value (original decision; rejected:
  asymmetric to the request, clients re-serialize it, no binary outputs); re-serializing the
  document as a JSON node (rejected: may change bytes, number formats, key order).

## R4 — Failure status mapping (FR-007, SC-006)

- **Decision**: The "response status" of the spec is the pair (HTTP status, `errorType`):

  | Case | HTTP | `errorType` |
  |------|------|-------------|
  | malformed request message (not JSON, missing/empty `document`, invalid base64, unknown property, invalid option value) | 400 | `MALFORMED_REQUEST` |
  | unsupported source/target pair, unknown or syntactically invalid format name | 404 | `UNSUPPORTED_CONVERSION` |
  | request message or decoded document larger than limit | 413 | `REQUEST_TOO_LARGE` |
  | request `Content-Type` is not `application/json` | 415 | `UNSUPPORTED_MEDIA_TYPE` |
  | invalid input document (PARSE / VALIDATE_INPUT errors incl. unparseable document, wrong CHMED version, unsupported document type, decoded QR payload above `maxDecodedSize`) | 422 | `INVALID_INPUT` |
  | conversion failed (strict-mode loss, missing required data, output validation error) | 422 | `CONVERSION_FAILED` |
  | concurrency limit reached, or validation not completed under load (`VALIDATION_INCOMPLETE`, with report; added 2026-10-29) | 503 | `SERVICE_BUSY` (with `Retry-After`) |
  | validation resources not ready | 503 | `NOT_READY` |
  | conversion exceeded time limit | 504 | `CONVERSION_TIMEOUT` |
  | unexpected error | 500 | `INTERNAL_ERROR` |

- **Rationale**: Uses the closest standard HTTP semantics; `errorType` makes cases that share a
  status code unambiguous for clients.

## R5 — Statelessness, privacy and logging (FR-005, FR-014, constitution IV)

- **Decision**: No session, no Spring cache, no request/response body or option logging, no Tomcat access
  log with bodies; `server.error.include-stacktrace=never`, `include-message=never`. One
  servlet filter writes one structured log line per request (time, method, route template,
  status, outcome, duration, input size, correlation id). Correlation id: incoming
  `X-Correlation-Id` accepted if it matches `[A-Za-z0-9-]{1,64}`, otherwise a new random UUID;
  returned as header and in the body.
  Logs go to the console by default; the operator can add a rotated log file with
  `logging.file.name` (Spring Boot default logging, added 2026-11-01). No file is written
  unasked, in particular no `spring.log` in the temp directory.
- **Rationale**: Constitution IV; correlation ids are operational metadata and not part of the
  deterministic document output.

## R6 — Limits, timeouts and concurrency (FR-017)

- **Decision**: Configuration properties `converter.rest.max-document-size` (decoded input
  document, default 5 MB), `converter.rest.max-request-size` (whole request message, default
  8 MB, must be ≥ 4/3 × max-document-size), `converter.rest.timeout` (default 40 s, raised from 10 s; it includes the time in the queue and
  stays above the 30 s of SC-002 and at the 40 s of SC-003),
  `converter.rest.max-concurrent` (default = available processors, at most 4) and
  `converter.rest.queue-size` (default 4 × max-concurrent; revised 2026-11-01 after measuring:
  20 simultaneous 20-medication plans took about 24 s in total on the Windows workstation with 4, 8 and 16 conversions at a
  time, so more than four only slow down each conversion and risk the validator's pattern time
  limit). The request size is enforced by a
  filter (Content-Length and a counting input stream), the document size after base64 decoding.
  Independently, the library's `maxDecodedSize` (5 MB) limits the decompressed QR payload and
  yields an `INVALID_INPUT` report entry. Conversions run on a bounded
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

- **Decision** (revised 2026-10-29): thin runnable JAR `ch-emed-converter-rest.jar` with its
  dependencies in `lib/` (manifest `Class-Path`), the same layout as the CLI, instead of a Spring
  Boot fat JAR; OCI image built with
  `spring-boot:build-image` (Paketo buildpacks, Java 21). Operator documentation
  `docs/operations/rest-service.md` states explicitly: no authentication and no TLS in the
  service; MUST run behind an API gateway / reverse proxy or in a protected network; no data is
  stored.
- **Rationale**: Spec assumption (operated by customer, e.g. as container); FR-012/FR-013.

## R11 — Testing

- **Decision**: Spring Boot slice/integration tests with MockMvc (contract, status mapping,
  limits); a parity test runs the 001 reference corpus through the service and the library and
  compares output bytes and report (SC-001); a load test with 20 parallel requests checks
  isolation and p95 < 40 s with 20-medication plans (SC-003, revised 2026-11-01); a log-capture test scans for request content, option values
  and patient values (SC-004); a test with all outbound connections blocked proves FR-015; a
  Java client generated from `openapi.yaml` (openapi-generator, test scope only) performs a
  conversion against the packaged JAR (SC-005); end-to-end smoke test against the packaged JAR.
  The parity test compares service, library and CLI output for the same input and options.
- **Rationale**: Constitution III; each success criterion has an automated check.

## R12 — Conversion options (FR-002)

- **Decision**: The `options` object of the request message offers exactly the CLI options:
  `strict` (boolean, default `false`), `language` (`de-CH` | `fr-CH` | `it-CH` | `en-CH`, used
  only if the source has none, default `de-CH`), `confidentiality` (`normal` | `restricted` |
  `secret`, default `normal`), `patientIdentifier` `{system, value}` and `author`
  `{gln, given, family, organization {name, street, zip, city, country}}` — the same structure
  as the CLI `--author` file. The service builds the library options from
  `ConversionOptions.defaults()` and overrides only the values present in the request;
  `validateOutput` is always `true`, `idStrategy`, `clock` and `maxDecodedSize` stay at the
  library defaults (identical to the CLI, so output is byte-identical, FR-003). Unknown
  properties or invalid values → `400 MALFORMED_REQUEST`; the problem `detail` names the
  property, never its value.
- **Rationale**: Constitution Conversion Scope (identical options in library, CLI and REST);
  feature 001 FR-023 needs patient identifier and author for many CHMED16A plans; personal data
  stays out of URLs, headers and logs (constitution IV).
- **Alternatives**: Query parameters for all options (rejected: personal data in URLs);
  headers for patient identifier and author (rejected: headers are commonly logged by gateways).

## Sources

- Spring Boot project page (current version 4.1.1): https://spring.io/projects/spring-boot
- RFC 9457 Problem Details for HTTP APIs: https://www.rfc-editor.org/rfc/rfc9457
