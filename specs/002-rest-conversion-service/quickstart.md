# Quickstart & Validation Guide: REST Conversion Service

Proves the feature end to end. API: [contracts/openapi.yaml](./contracts/openapi.yaml);
representations: [data-model.md](./data-model.md). Fixtures come from feature 001
(`format-chmed16a/src/test/resources/fixtures/`).

## Prerequisites

- JDK 21; feature 001 modules build (`./mvnw verify` green)
- `curl` and `jq` for the manual scenarios

## Build, test, start

```text
./mvnw verify                      # includes contract, parity, load and privacy tests
java -jar converter-rest/target/ch-emed-converter-rest.jar
curl -s localhost:8080/actuator/health/readiness        # → {"status":"UP"} once packages are loaded
```

## Scenario 1 — Convert over HTTP (US1, SC-001)

The request is a JSON message with the base64-encoded document (research R2):

```text
F=fixtures/chmed16a/plan-basic.json
jq -n --arg doc "$(base64 -w0 "$F")" \
   '{document: $doc, options: {strict: true}}' \
  | curl -s -X POST 'localhost:8080/api/v1/conversions/chmed16a/ch-emed' \
      -H 'Content-Type: application/json' --data-binary @- \
  | tee /tmp/resp.json | jq '.outcome, .report.outcome, .outputMediaType'
jq -r .output /tmp/resp.json | base64 -d > /tmp/out.json
```

Expected: HTTP 200, `"SUCCESS"`, `"application/fhir+json"`; `/tmp/out.json` is byte-identical to
the CLI output for the same file and options
(`java -jar converter-cli/... convert --from chmed16a --to ch-emed --strict fixtures/.../plan-basic.json`).
The automated parity test does this for the whole 001 corpus (library, CLI and service).

The same plan as QR payload (`base64 -w0 plan-basic.qr.txt` as `document`) → HTTP 200 with
identical decoded output.

All options (same as the CLI): `strict`, `language`, `confidentiality`, `patientIdentifier`,
`author` (structure of the CLI `--author` file).

## Scenario 2 — Failure types (US2, SC-006)

| Request | Expected status / `errorType` |
|---------|-------------------------------|
| request body `not json` (message itself) | 400 `MALFORMED_REQUEST`, no report |
| `document` missing, empty or not valid base64 | 400 `MALFORMED_REQUEST` |
| unknown property, e.g. `options.foo` | 400 `MALFORMED_REQUEST` |
| `options.strict: "yes"`, `options.language: "de-DE"`, `options.patientIdentifier` without `value` | 400 `MALFORMED_REQUEST`; `detail` names the property, not the value |
| `document` = base64 of `invalid-json.txt` | 422 `INVALID_INPUT` with PARSE report |
| `document` = base64 of `erezept-url.txt` | 422 `INVALID_INPUT` with report; no signature data in the log |
| `POST /conversions/chmed16a/chmed16a` | 404 `UNSUPPORTED_CONVERSION` + `supportedConversions` |
| `POST /conversions/ch_emed!/chmed16a` | 404 `UNSUPPORTED_CONVERSION` (never 500) |
| `options.strict: true` with `plan-unmappable-element.json` | 422 `CONVERSION_FAILED` with report, no `output` |
| `plan-no-ids.json` (synthetic plan without `Patient.Ids`) without `options.patientIdentifier` | 422 `CONVERSION_FAILED`, report `MISSING_REQUIRED_DATA` |
| same plan with `options.patientIdentifier` | 200; decoded output equals CLI with `--patient-id` |
| `Content-Type: application/xml` or `text/plain` | 415 `UNSUPPORTED_MEDIA_TYPE` |
| message > `max-request-size` (start with `--converter.rest.max-request-size=1KB`) | 413 `REQUEST_TOO_LARGE` |
| decoded document > `max-document-size` (`--converter.rest.max-document-size=1KB`) | 413 `REQUEST_TOO_LARGE` |
| `document` = base64 of `qr-oversized.txt` | 422 `INVALID_INPUT` (library `maxDecodedSize`) |
| conversion slower than `--converter.rest.timeout=1ms` | 504 `CONVERSION_TIMEOUT` |

Every failure body is `application/problem+json` with `correlationId`; no stack trace, no input
content, no option values. A report is present exactly for `INVALID_INPUT` and
`CONVERSION_FAILED` (constitution v1.4.1).

## Scenario 3 — Capabilities and API description (US3)

```text
curl -s localhost:8080/api/v1/capabilities | jq
curl -s localhost:8080/api/v1/openapi.yaml | head
```

Expected: pairs `chmed16a→ch-emed` and `ch-emed→chmed16a`, options `strict`, `language`,
`confidentiality`, `patientIdentifier`, `author`, specification `ch.fhir.ig.ch-emed#6.0.0`,
limits incl. `maxDocumentSizeBytes`; the OpenAPI file equals `contracts/openapi.yaml`.

A Java client generated from the OpenAPI file performs a first conversion (SC-005; automated in
`e2e/GeneratedClientTest`).

## Scenario 4 — Operation (US4, SC-004)

1. Start with the IG packages directory removed from the classpath (test profile
   `missing-packages`): readiness `DOWN`, conversions → 503 `NOT_READY`.
2. Run the corpus, then search the log output for any fixture patient value or document
   fragment: zero matches (automated in `privacy/LogLeakTest`).

## Scenario 5 — Load and isolation (SC-002, SC-003)

`./mvnw -pl converter-rest test -Dtest='*LoadTest'` sends 20 parallel requests with different
patients. Expected: p95 < 3 s; each response contains only its own patient's document; a single
100-medication plan < 3 s end to end.

## Scenario 6 — Container image

```text
./mvnw -pl converter-rest spring-boot:build-image
docker run --rm -p 8080:8080 ch-emed-converter-rest:<version>
```

Expected: same results as above; image runs without network access to the internet.
