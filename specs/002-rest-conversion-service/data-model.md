# Data Model: REST Conversion Service

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

The conversion entities (request, options, result, report) are those of feature 001
([data-model](../001-chmed16a-ch-emed-conversion/data-model.md) §1). This document adds the
HTTP representations. Wire format: [contracts/openapi.yaml](./contracts/openapi.yaml).
Revised 2026-10-29: JSON request message with base64 documents and the full CLI option set
(research R2, R3, R12).

## 1. Conversion request (HTTP)

| Part | Source | Rules |
|------|--------|-------|
| `from`, `to` | path segments | lower-case format names (`FormatId.externalName()`): `chmed16a`, `ch-emed` (later: `chmed23a`, `cara-emed`, …); unknown, unsupported or syntactically invalid name (`FormatId.fromExternalName` throws) → 404 `UNSUPPORTED_CONVERSION` |
| `Content-Type` | header | `application/json` (charset optional); anything else → 415 `UNSUPPORTED_MEDIA_TYPE` |
| body | ConversionRequest message (§2) | not JSON, unknown property, missing/empty `document` → 400 `MALFORMED_REQUEST`; > `max-request-size` → 413 `REQUEST_TOO_LARGE` |
| `X-Correlation-Id` | header, optional | `[A-Za-z0-9-]{1,64}`, otherwise replaced by a new UUID |

## 2. ConversionRequest message (`application/json`)

| Field | Type | Rules |
|-------|------|-------|
| document | string, base64 | required, non-empty; RFC 4648 §4 (standard alphabet, padding); invalid base64 → 400 `MALFORMED_REQUEST`; decoded size > `max-document-size` → 413 `REQUEST_TOO_LARGE`; decoded bytes are passed unchanged to the library, which detects the form (CHMED16A JSON, CHMED16A QR payload, FHIR JSON) |
| options | RequestOptions (§3) | optional; omitted → library defaults |

Unknown properties at any level → 400 `MALFORMED_REQUEST`.

## 3. RequestOptions

Same options as the CLI (`ConvertCommand`). The library options are built from
`ConversionOptions.defaults()`; only values present in the request are overridden.

| Field | Type | Default | Maps to | Rules |
|-------|------|---------|---------|-------|
| strict | boolean | `false` | `withStrict` | |
| language | `de-CH` \| `fr-CH` \| `it-CH` \| `en-CH` | `de-CH` | `withDefaultLanguage` | used only if the source has none (001 FR-022); other value → 400 |
| confidentiality | `normal` \| `restricted` \| `secret` | `normal` | `withConfidentiality(Confidentiality.valueOf(upper-case))` | other value → 400 |
| patientIdentifier | `{system, value}` | none | `withPatientIdentifier(new PatientIdentifier(system, value))` | both required and non-blank, else 400; used only if the source has none (001 FR-023) |
| author | `{gln, given, family, organization {name, street, zip, city, country}}` | none | `withAuthor(new AuthorDetails(…, new OrganizationDetails(…)))` | same structure as the CLI `--author` file; `organization` omitted → `null` as in the CLI; used only where the source lacks author data (001 FR-023) |

Fixed in the service: `validateOutput = true`; `idStrategy`, `clock`, `maxDecodedSize` stay at
the library defaults (identical to the CLI → byte-identical output, FR-003).

Problem `detail` for an invalid option names the property path (e.g.
`options.patientIdentifier.system`), never its value.

## 4. ConversionResponse (2xx, `application/json`)

| Field | Type | Rules |
|-------|------|-------|
| outcome | `SUCCESS` / `SUCCESS_WITH_WARNINGS` | `FAILED` never appears in a 2xx |
| correlationId | string | same as `X-Correlation-Id` response header |
| report | ConversionReport | as defined by `conversion-report.schema.json` (001) |
| output | string, base64 | library output bytes base64-encoded; decoded byte-identical to library and CLI output (R3) |
| outputMediaType | string | media type of the decoded output: `application/fhir+json` for `ch-emed`, `application/json` for `chmed16a` |

## 5. Problem response (4xx/5xx, `application/problem+json`)

RFC 9457 members plus extensions:

| Field | Type | Rules |
|-------|------|-------|
| type | URI | `urn:ch-emed-converter:problem:<error-type-kebab>` |
| title | string | short, fixed per type |
| status | int | HTTP status |
| detail | string | human-readable; never input content, option values, patient values or stack traces |
| errorType | ErrorType | see §6 |
| correlationId | string | |
| report | ConversionReport | present whenever a conversion was attempted: `INVALID_INPUT`, `CONVERSION_FAILED` (constitution v1.4.1) |
| supportedConversions | list of `{from, to}` | present for `UNSUPPORTED_CONVERSION` |

## 6. ErrorType → HTTP status

`MALFORMED_REQUEST` 400 · `UNSUPPORTED_CONVERSION` 404 · `REQUEST_TOO_LARGE` 413 ·
`UNSUPPORTED_MEDIA_TYPE` 415 · `INVALID_INPUT` 422 · `CONVERSION_FAILED` 422 ·
`SERVICE_BUSY` 503 (+ `Retry-After`) · `NOT_READY` 503 · `CONVERSION_TIMEOUT` 504 ·
`INTERNAL_ERROR` 500.

**Classification of a `FAILED` library result**: a `VALIDATION_INCOMPLETE` entry (validation not
completed under system load) → `SERVICE_BUSY` 503 with `Retry-After` and the report (added
2026-10-29); otherwise any ERROR entry in phase `PARSE` or
`VALIDATE_INPUT` → `INVALID_INPUT` (includes an unparseable document, a decoded QR payload above
`maxDecodedSize` and, until the eRezept feature, eRezept URLs); otherwise (`MAP`,
`VALIDATE_OUTPUT`, strict-mode loss, `MISSING_REQUIRED_DATA` for patient identifier/author) →
`CONVERSION_FAILED`.

## 7. CapabilitiesResponse (`GET /api/v1/capabilities`)

| Field | Type |
|-------|------|
| converterVersion | string |
| conversions | list of `{from, to}` |
| options | list of `{name, type, default, description}`: `strict`, `language`, `confidentiality`, `patientIdentifier`, `author` |
| specifications | map format → pinned version (e.g. `CH_EMED` → `ch.fhir.ig.ch-emed#6.0.0`) |
| limits | `{maxRequestSizeBytes, maxDocumentSizeBytes, timeoutSeconds}` |

## 8. Configuration (operator, `converter.rest.*`)

| Property | Default | Rules |
|----------|---------|-------|
| max-document-size | 5MB | > 0; decoded input document |
| max-request-size | 8MB | ≥ 4/3 × max-document-size; whole request message |
| timeout | 20s | > 0; above the 15 s of SC-002 (revised 2026-10-29) |
| max-concurrent | available processors | ≥ 1 |
| queue-size | 2 × max-concurrent | ≥ 0 |

Plus standard Spring Boot properties (`server.port`, `management.server.port`). The library's
`maxDecodedSize` (5 MB, decompressed QR payload) is not configurable in the service, as in the
CLI.

## 9. Service state

```text
STARTING ──(Converter + validation context built)──▶ READY
STARTING ──(build failed: packages missing/corrupt)──▶ FAILED (readiness DOWN, conversions 503 NOT_READY)
READY: requests processed independently; no state kept between requests
```

## 10. Operational log entry (one per request)

`timestamp`, `correlationId`, `method`, `route` (template, e.g. `/api/v1/conversions/{from}/{to}`),
`from`, `to`, `status`, `errorType` (if any), `outcome`, `durationMs`, `requestBytes`,
`documentBytes`. Never body content, option values, report messages, patient data or eRezept
signature data.
