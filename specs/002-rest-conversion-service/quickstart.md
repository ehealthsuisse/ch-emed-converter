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

```text
curl -s -X POST 'localhost:8080/api/v1/conversions/chmed16a/ch-emed' \
  -H 'Content-Type: application/json' --data-binary @fixtures/chmed16a/plan-basic.json \
  | tee /tmp/resp.json | jq '.outcome, .report.outcome'
```

Expected: HTTP 200, `"SUCCESS"`; `.output` is byte-identical to the CLI output for the same file
(`java -jar converter-cli/... convert --from chmed16a --to ch-emed fixtures/.../plan-basic.json`).
The automated parity test does this for the whole 001 corpus.

The same plan as QR payload (`plan-basic.qr.txt`, `-H 'Content-Type: text/plain'`) → HTTP 200 with
identical `.output`.

## Scenario 2 — Failure types (US2, SC-006)

| Request | Expected status / `errorType` |
|---------|-------------------------------|
| body `not json` | 422 `INVALID_INPUT` with report |
| `POST /conversions/chmed16a/chmed16a` | 404 `UNSUPPORTED_CONVERSION` + `supportedConversions` |
| `?strict=yes` | 400 `MALFORMED_REQUEST` |
| `?strict=true` with `plan-unmappable-element.json` | 422 `CONVERSION_FAILED` with report, no `output` |
| `Content-Type: application/xml` | 415 `UNSUPPORTED_MEDIA_TYPE` |
| `POST /conversions/ch-emed/chmed16a` with `Content-Type: text/plain` | 415 `UNSUPPORTED_MEDIA_TYPE` |
| body > `max-request-size` (start with `--converter.rest.max-request-size=1KB`) | 413 `REQUEST_TOO_LARGE` |
| conversion slower than `--converter.rest.timeout=1ms` | 504 `CONVERSION_TIMEOUT` |

Every failure body is `application/problem+json` with `correlationId`; no stack trace, no input
content.

## Scenario 3 — Capabilities and API description (US3)

```text
curl -s localhost:8080/api/v1/capabilities | jq
curl -s localhost:8080/api/v1/openapi.yaml | head
```

Expected: pairs `chmed16a→ch-emed` and `ch-emed→chmed16a`, option `strict`, specification
`ch.fhir.ig.ch-emed#6.0.0`; the OpenAPI file equals `contracts/openapi.yaml`.

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
