# Implementation Plan: REST Conversion Service

**Branch**: `002-rest-conversion-service` | **Date**: 2026-10-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-rest-conversion-service/spec.md`

## Summary

A stateless REST/JSON service exposing every conversion of the core library. New Maven module
`converter-rest` (Spring Boot 4.1, Spring Web MVC) on top of `converter-core` from feature 001:
`POST /api/v1/conversions/{from}/{to}` takes a JSON message with the base64-encoded input
document and the same conversion options as the CLI, and returns outcome, conversion report and
the library's output bytes base64-encoded; failures use RFC 9457 problem details with a
distinct `errorType` (revised 2026-10-29, research R2/R3/R12). Capabilities and a contract-first OpenAPI
description are served; Actuator provides liveness/readiness. No authentication, no TLS, no
persistence — protection is the operator's job and is documented as such.

## Technical Context

**Language/Version**: Java 21 LTS

**Primary Dependencies**: Spring Boot 4.1.x (spring-boot-starter-web, -actuator, -validation);
`converter-core`, `converter-validation`, `format-chmed16a` from feature 001; tests only:
Atlassian swagger-request-validator, `converter-cli` (parity), openapi-generator (client
generation, SC-005)

**Storage**: N/A (stateless; no cache, no session)

**Testing**: JUnit 5, Spring Boot Test (MockMvc), AssertJ, swagger-request-validator; parity,
load, log-scan, no-outbound-network and generated-client tests (research R11)

**Target Platform**: JVM 21 on Linux; runnable thin JAR with `lib/` (as the CLI) and OCI image (Paketo buildpacks)

**Project Type**: Web service (module of the existing Maven multi-module project)

**Performance Goals** (revised 2026-11-01): 20 different medications end to end < 5 s warm, upper
bound of 100 < 30 s, REST overhead < 1 s (SC-002, feature 001 SC-005); 20 parallel requests with
20-medication plans answered with p95 < 30 s or rejected at once with `SERVICE_BUSY` beyond the configured capacity, no
cross-request data (SC-003)

**Constraints**: Byte-identical output vs. library and CLI (after base64 decoding); documents
never parsed or re-serialized by the REST layer; options with personal data only in the body;
no request content, option values or PHI in logs; no external calls; configurable
request/document size, time and concurrency limits; Spring confined to `converter-rest`

**Scale/Scope**: 3 endpoints (+ OpenAPI file, Actuator health); 10 documented failure types

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Gate (constitution v1.4.1) | Status | Evidence |
|---|----------------------------|--------|----------|
| I | Outputs conform; official validation | PASS | Service returns the library result unchanged (base64 of the exact bytes, R3); validation happens in the core (001 R4); readiness DOWN without packages (R7) |
| II | No silent loss; report with every response where a conversion was attempted | PASS | Every 2xx and 422 response carries the full report; all other failures carry problem details (R3/R4, constitution v1.4.1) |
| III | Test-first; corpus; contract | PASS | Contract-first OpenAPI with enforced contract tests (R8); parity test over the 001 corpus (R11) |
| IV | Local only; no PHI in logs; no persistence; operator secures transport and access | PASS | R5 logging/statelessness; patient identifier/author only in the body (R12); no outbound clients, verified by test (R11); operator documentation required (R10, FR-012/013) |
| V | CH EMED hub; deterministic; thin layers; core framework-free | PASS | Controller delegates to `Converter`; Spring only in `converter-rest`; correlation ids are metadata outside the document |
| Scope | Every conversion via library, CLI and REST with identical behaviour | PASS | Pairs come from the library (R9); the REST options equal the CLI options (R12); parity test over library, CLI and REST enforces identity — completes the delivery requirement for feature 001 |
| Tech | Java 21; web framework chosen in plan, then project-wide | PASS | Spring Boot 4.1 chosen (R1) and recorded as project web framework |

Post-design re-check (after Phase 1): **PASS** — no violations.

## Project Structure

### Documentation (this feature)

```text
specs/002-rest-conversion-service/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── openapi.yaml     # Phase 1 output (source of truth for the API)
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks - not created here)
```

### Source Code (repository root)

```text
pom.xml                                  # + Spring Boot BOM, + module converter-rest
docs/
└── operations/
    └── rest-service.md                  # operator documentation (no auth/TLS, limits, probes)

converter-rest/
├── src/main/java/ch/emed/converter/rest/
│   ├── RestApplication.java             # Spring Boot entry point
│   ├── config/       # ConverterRestProperties (limits), ConverterBeans (shared warm Converter)
│   ├── api/          # ConversionController, RequestOptionsMapper, CapabilitiesController, OpenApiController
│   ├── model/        # ConversionRequestMessage, RequestOptions, ConversionResponse, CapabilitiesResponse (records)
│   ├── error/        # ErrorType, ProblemFactory, global exception handler
│   ├── execution/    # BoundedConversionExecutor (pool, timeout, busy)
│   ├── web/          # CorrelationIdFilter, RequestSizeLimitFilter, RequestLogFilter
│   └── health/       # ConverterReadinessIndicator
├── src/main/resources/
│   ├── application.yaml                 # defaults, actuator exposure, error settings
│   └── static-api/openapi.yaml          # copy of contracts/openapi.yaml (build-time sync)
└── src/test/java/ch/emed/converter/rest/
    ├── contract/     # OpenAPI contract tests, status mapping (SC-006)
    ├── parity/       # service vs. library vs. CLI over 001 corpus (SC-001)
    ├── load/         # 20 parallel requests (SC-003), performance (SC-002)
    ├── privacy/      # log capture scan (SC-004), no outbound network (FR-015)
    └── e2e/          # packaged JAR smoke test, generated client (SC-005)
```

**Structure Decision**: One new module `converter-rest` in the existing multi-module build;
it depends on `converter-core` (API) and, at runtime, on the format and validation modules.
No other module gains a Spring dependency.

## Complexity Tracking

No constitution violations; nothing to justify.
