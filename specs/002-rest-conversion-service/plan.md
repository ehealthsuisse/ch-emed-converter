# Implementation Plan: REST Conversion Service

**Branch**: `002-rest-conversion-service` | **Date**: 2026-10-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-rest-conversion-service/spec.md`

## Summary

A stateless REST/JSON service exposing every conversion of the core library. New Maven module
`converter-rest` (Spring Boot 4.1, Spring Web MVC) on top of `converter-core` from feature 001:
`POST /api/v1/conversions/{from}/{to}` takes the native document as request body and returns
outcome, conversion report and the library's output bytes verbatim; failures use RFC 9457
problem details with a distinct `errorType`. Capabilities and a contract-first OpenAPI
description are served; Actuator provides liveness/readiness. No authentication, no TLS, no
persistence — protection is the operator's job and is documented as such.

## Technical Context

**Language/Version**: Java 21 LTS

**Primary Dependencies**: Spring Boot 4.1.x (spring-boot-starter-web, -actuator, -validation);
`converter-core`, `converter-validation`, `format-chmed16a` from feature 001; Atlassian
swagger-request-validator (tests only)

**Storage**: N/A (stateless; no cache, no session)

**Testing**: JUnit 5, Spring Boot Test (MockMvc), AssertJ, swagger-request-validator; parity,
load and log-scan tests (research R11)

**Target Platform**: JVM 21 on Linux; executable JAR and OCI image (Paketo buildpacks)

**Project Type**: Web service (module of the existing Maven multi-module project)

**Performance Goals**: 100-medication plan end to end < 3 s (SC-002); 20 parallel requests, p95
< 3 s, no cross-request data (SC-003)

**Constraints**: Byte-identical output vs. library; no request content or PHI in logs; no
external calls; configurable size/time/concurrency limits; Spring confined to `converter-rest`

**Scale/Scope**: 3 endpoints (+ OpenAPI file, Actuator health); 10 documented failure types

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Gate (constitution v1.4.0) | Status | Evidence |
|---|----------------------------|--------|----------|
| I | Outputs conform; official validation | PASS | Service returns the library result unchanged (raw bytes, R3); validation happens in the core (001 R4); readiness DOWN without packages (R7) |
| II | No silent loss; report always available | PASS | Every 2xx and 422 response carries the full report (R3/R4) |
| III | Test-first; corpus; contract | PASS | Contract-first OpenAPI with enforced contract tests (R8); parity test over the 001 corpus (R11) |
| IV | Local only; no PHI in logs; no persistence; operator secures transport and access | PASS | R5 logging/statelessness; no outbound clients; operator documentation required (R10, FR-012/013) |
| V | CH EMED hub; deterministic; thin layers; core framework-free | PASS | Controller delegates to `Converter`; Spring only in `converter-rest`; correlation ids are metadata outside the document |
| Scope | Every conversion via library, CLI and REST with identical behaviour | PASS | Pairs and options come from the library (R9); parity test enforces identity — completes the delivery requirement for feature 001 |
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
│   ├── api/          # ConversionController, CapabilitiesController, OpenApiController
│   ├── model/        # ConversionResponse, CapabilitiesResponse (records), RawJson
│   ├── error/        # ErrorType, ProblemFactory, global exception handler
│   ├── execution/    # BoundedConversionExecutor (pool, timeout, busy)
│   ├── web/          # CorrelationIdFilter, RequestSizeLimitFilter, RequestLogFilter
│   └── health/       # ConverterReadinessIndicator
├── src/main/resources/
│   ├── application.yaml                 # defaults, actuator exposure, error settings
│   └── static-api/openapi.yaml          # copy of contracts/openapi.yaml (build-time sync)
└── src/test/java/ch/emed/converter/rest/
    ├── contract/     # OpenAPI contract tests, status mapping (SC-006)
    ├── parity/       # service vs. library over 001 corpus (SC-001)
    ├── load/         # 20 parallel requests (SC-003), performance (SC-002)
    └── privacy/      # log capture scan (SC-004)
```

**Structure Decision**: One new module `converter-rest` in the existing multi-module build;
it depends on `converter-core` (API) and, at runtime, on the format and validation modules.
No other module gains a Spring dependency.

## Complexity Tracking

No constitution violations; nothing to justify.
