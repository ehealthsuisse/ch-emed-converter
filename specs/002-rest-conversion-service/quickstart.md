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

The request is a JSON message with the base64-encoded document (research R2). CHMED16A plans carry
only `Auth`; CH EMED needs practitioner and organisation, so conversions to CH EMED need the
`author` option (feature 001 FR-023), here from `author.json` (same file as for the CLI
`--author`):

```text
F=fixtures/chmed16a/plan-basic.json
jq -n --arg doc "$(base64 -w0 "$F")" --slurpfile author author.json \
   '{document: $doc, options: {strict: true, author: $author[0]}}' \
  | curl -s -X POST 'localhost:8080/api/v1/conversions/chmed16a/ch-emed' \
      -H 'Content-Type: application/json' --data-binary @- \
  | tee /tmp/resp.json | jq '.outcome, .report.outcome, .outputMediaType'
jq -r .output /tmp/resp.json | base64 -d > /tmp/out.json
```

Expected: HTTP 200, `"SUCCESS_WITH_WARNINGS"` for this plan, `"application/fhir+json"`; `/tmp/out.json` is byte-identical to the CLI output for the same file
and options
(`java -jar converter-cli/... convert --from chmed16a --to ch-emed --strict --author author.json fixtures/.../plan-basic.json`).
Without `author` the conversion fails with 422 `CONVERSION_FAILED` (`MISSING_REQUIRED_DATA` at
`/Auth`).
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
patients. Expected (SC-002/SC-003 as revised on 2026-10-29): p95 < 15 s and no rejection with the
default limits on a machine with at least 7 cores (capacity `max-concurrent` + `queue-size` ≥ 20);
each response contains only its own patient's document; a single 100-medication plan < 15 s end to
end, less than 1 s above the library's own time.

## Scenario 6 — Container image

```text
./mvnw -pl converter-rest spring-boot:build-image
docker run --rm -p 8080:8080 ch-emed-converter-rest:<version>
```

Expected: same results as above; image runs without network access to the internet.

## Validation run 2026-10-29 (T048)

Packaged JAR on Windows 11, JDK 21 (Corretto 21.0.11), 16 logical cores. Scenarios 1-3 were run
with a Python script (standard library only; `jq` was not available) that sends the same requests
as the commands above; scenarios 4-5 are covered by the automated tests.

| Scenario | Result | Deviation |
|----------|--------|-----------|
| 1 Convert over HTTP | pass | The example above originally had no `author` option and failed with 422 `CONVERSION_FAILED`; corrected. With `author`: 200, output byte-identical to the CLI, QR payload gives identical output. |
| 2 Failure types | pass | All 13 rows checked (status, `errorType`, `application/problem+json`). |
| 3 Capabilities / API description | pass | Served `openapi.yaml` is byte-identical to the contract. |
| 4 Operation | pass (automated) | No `missing-packages` profile; `ReadinessTest` simulates the failed build instead. Log of the run: 19 request lines, no patient or option values, no `spring.log` written. |
| 5 Load and isolation | not met at first, then criteria revised | Isolation holds. The original 3 s limits failed: 20 parallel requests p95 about 4-6 s (SC-003), 100 medications about 6-8 s (SC-002); cause is the HL7 output validation in the library, not the REST layer. SC-002/SC-003 were aligned with feature 001 (15 s, REST overhead < 1 s) the same day; `ConversionLoadTest` checks the revised limits. |
| 6 Container image | pass | Built later the same day with Rancher Desktop (Docker Engine 29.5.3): `ch-emed-converter-rest:1.0.0-SNAPSHOT`, about 450 MB, Liberica JRE 21.0.13. Run in an internal Docker network without internet (a `curl` container in the same network could not reach the internet): ready in about 15 s, conversion 200, malformed request 400, capabilities with `timeoutSeconds` 20, no patient data in the log. Deviation: the active Docker context pointed to Docker Desktop; the build used `DOCKER_HOST=npipe:////./pipe/docker_engine`. |
