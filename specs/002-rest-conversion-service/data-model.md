# Data Model: REST Conversion Service

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

The conversion entities (request, options, result, report) are those of feature 001
([data-model](../001-chmed16a-ch-emed-conversion/data-model.md) §1). This document adds the
HTTP representations. Wire format: [contracts/openapi.yaml](./contracts/openapi.yaml).

## 1. Conversion request (HTTP)

| Part | Source | Rules |
|------|--------|-------|
| `from`, `to` | path segments | lower-case format names: `chmed16a`, `ch-emed` (later: `chmed23a`, `cara-emed`, …); unknown or unsupported pair → 404 `UNSUPPORTED_CONVERSION` |
| `strict` | query, boolean | default `false`; other values → 400 `MALFORMED_REQUEST` |
| body | raw bytes | `Content-Type` `application/json` or `application/fhir+json`; `text/plain` only when `from` = `chmed16a` (textual QR payload, FR-001 of feature 001); any other type or combination → 415; non-empty (else 400); ≤ `max-request-size` (else 413) |
| `X-Correlation-Id` | header, optional | `[A-Za-z0-9-]{1,64}`, otherwise replaced by a new UUID |

Maps to `ConversionRequest(source, target, body, ConversionOptions(strict, validateOutput=true, …))`.
`validateOutput` is always true in the service.

## 2. ConversionResponse (2xx, `application/json`)

| Field | Type | Rules |
|-------|------|-------|
| outcome | `SUCCESS` / `SUCCESS_WITH_WARNINGS` | `FAILED` never appears in a 2xx |
| correlationId | string | same as `X-Correlation-Id` response header |
| report | ConversionReport | as defined by `conversion-report.schema.json` (001) |
| output | raw JSON | library output bytes inserted verbatim (byte-identical, R3) |

## 3. Problem response (4xx/5xx, `application/problem+json`)

RFC 9457 members plus extensions:

| Field | Type | Rules |
|-------|------|-------|
| type | URI | `urn:ch-emed-converter:problem:<error-type-kebab>` |
| title | string | short, fixed per type |
| status | int | HTTP status |
| detail | string | human-readable; never input content, patient values or stack traces |
| errorType | ErrorType | see §4 |
| correlationId | string | |
| report | ConversionReport | present for `INVALID_INPUT`, `CONVERSION_FAILED` |
| supportedConversions | list of `{from, to}` | present for `UNSUPPORTED_CONVERSION` |

## 4. ErrorType → HTTP status

`MALFORMED_REQUEST` 400 · `UNSUPPORTED_CONVERSION` 404 · `REQUEST_TOO_LARGE` 413 ·
`UNSUPPORTED_MEDIA_TYPE` 415 · `INVALID_INPUT` 422 · `CONVERSION_FAILED` 422 ·
`SERVICE_BUSY` 503 (+ `Retry-After`) · `NOT_READY` 503 · `CONVERSION_TIMEOUT` 504 ·
`INTERNAL_ERROR` 500.

**Classification of a `FAILED` library result**: any ERROR entry in phase `PARSE` or
`VALIDATE_INPUT` → `INVALID_INPUT`; otherwise (`MAP`, `VALIDATE_OUTPUT`, strict-mode loss) →
`CONVERSION_FAILED`.

## 5. CapabilitiesResponse (`GET /api/v1/capabilities`)

| Field | Type |
|-------|------|
| converterVersion | string |
| conversions | list of `{from, to}` |
| options | list of `{name, type, default, description}` (currently `strict`) |
| specifications | map format → pinned version (e.g. `CH_EMED` → `ch.fhir.ig.ch-emed#6.0.0`) |
| limits | `{maxRequestSizeBytes, timeoutSeconds}` |

## 6. Configuration (operator, `converter.rest.*`)

| Property | Default | Rules |
|----------|---------|-------|
| max-request-size | 5MB | > 0 |
| timeout | 10s | > 0 |
| max-concurrent | available processors | ≥ 1 |
| queue-size | 2 × max-concurrent | ≥ 0 |

Plus standard Spring Boot properties (`server.port`, `management.server.port`).

## 7. Service state

```text
STARTING ──(Converter + validation context built)──▶ READY
STARTING ──(build failed: packages missing/corrupt)──▶ FAILED (readiness DOWN, conversions 503 NOT_READY)
READY: requests processed independently; no state kept between requests
```

## 8. Operational log entry (one per request)

`timestamp`, `correlationId`, `method`, `route` (template, e.g. `/api/v1/conversions/{from}/{to}`),
`from`, `to`, `status`, `errorType` (if any), `outcome`, `durationMs`, `requestBytes`. Never
body content, report messages, patient data or eRezept signature data.
