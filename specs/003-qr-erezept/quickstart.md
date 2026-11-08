# Quickstart & Validation Guide: eMediplan QR Code and eRezept Schweiz

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Runnable checks that prove the feature end to end. Formats, codes and fields are defined in the
[data model](./data-model.md) and the [contracts](./contracts/). All inputs are synthetic
fixtures.

## Prerequisites

- JDK 21, Maven wrapper.
- Build machine `andromeda` (homeoffice), otherwise local Windows.
- Time tests on 4 vCPUs (`taskset -c 0-3`).

## Build and test

```bash
sh ./mvnw -B clean verify
```

This runs all tests, including the new ones of `converter-qr`, `format-chmed16a`, `converter-cli`
and `converter-rest`.

## Scenario 1 — Read a medication plan from a QR image (US1, SC-001)

```bash
java -jar converter-cli/target/ch-emed-converter.jar convert --from chmed16a --to ch-emed \
  format-chmed16a/src/test/resources/fixtures/qr/plan-basic.png -o card-from-image.json --author author.json
java -jar converter-cli/target/ch-emed-converter.jar convert --from chmed16a --to ch-emed \
  format-chmed16a/src/test/resources/fixtures/chmed16a/plan-basic.json -o card-from-json.json --author author.json
cmp card-from-image.json card-from-json.json
```

**Expected**: Exit 0, and the two files are identical. Without `--author`, both fail with exit 1
and `MISSING_REQUIRED_DATA` at `/Auth` (FR-019).

**Error cases**, each exit 1 with no output:
- an image without a QR code → `QR_NOT_FOUND`;
- an image with two codes → `QR_MULTIPLE`;
- a QR code with a web address → `PARSE_ERROR`.

## Scenario 2 — Read a signed eRezept (US2, SC-003)

```bash
java -jar converter-cli/target/ch-emed-converter.jar convert --from chmed16a --to ch-emed \
  format-chmed16a/src/test/resources/fixtures/erezept/rx-signed.url.txt -o rx.json --report report.json
cat rx.json.signature.json
```

**Expected**:
- `rx.json` equals the conversion of the embedded prescription.
- `rx.json.signature.json` holds the raw fixture values of `i`, `t` and `s` with
  `"verified": false`.
- The report contains `SIGNATURE_NOT_VERIFIED` and none of the values.

**Error cases**:
- a URL without `s` → exit 1, `EREZEPT_URL_INVALID` naming `s`;
- a URL with a foreign host → `EREZEPT_URL_INVALID`;
- a URL holding a plan → `UNSUPPORTED_DOCUMENT_TYPE`.

## Scenario 3 — Create a QR code for a medication plan (US3, SC-002, SC-005)

```bash
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed --to chmed16a \
  card-from-json.json --output-form qr-payload -o plan.txt
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed --to chmed16a \
  card-from-json.json --output-form qr-image -o plan.png
```

**Expected**:
- `plan.txt` starts with `CHMED16A1H4sI`.
- `plan.png` is a 1-bit PNG with a 4 × 4 cm print size.
- Any phone QR app shows exactly the content of `plan.txt`. The automated check of the same is
  done by the BoofCV test.
- Running twice gives identical files.

The 20-medication plan of `LargePlanGenerator` fits (`PerformanceTest`, SC-005). An oversized plan
fails with `QR_CAPACITY_EXCEEDED`, and `qr-payload` still works for it.

## Scenario 4 — Prepare a prescription for signing (US4, SC-004)

```bash
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed --to chmed16a \
  format-chmed16a/src/test/resources/fixtures/chemed/medication-prescription-example.json \
  --output-form qr-payload -o rx-unsigned.txt
```

**Expected**:
- `rx-unsigned.txt` decodes to a prescription that passes `erezept-chmed16a-schemav7.json` and the
  eRezept rules.
- `--output-form qr-image` on the same file fails with exit 1 and `QR_IMAGE_NOT_ALLOWED`.

**Optional, manual, only with an HIN test account**: POST `rx-unsigned.txt` to a local erxproxy
in the integration environment (`/ePrescription/create?output-format=data`). This is the only way
to confirm "signable unchanged". The converter itself never contacts it.

## Scenario 5 — Same behaviour through all interfaces (US5, SC-008)

```bash
B64=$(base64 -w0 format-chmed16a/src/test/resources/fixtures/qr/plan-basic.png)
curl -s -X POST http://localhost:8080/api/v1/conversions/chmed16a/ch-emed -H 'Content-Type: application/json' \
  -d "{\"document\":\"$B64\",\"options\":{\"author\":$(cat author.json)}}" | jq '.outcome, .outputMediaType'
curl -s http://localhost:8080/api/v1/capabilities | jq '.formats'
```

**Expected**:
- The decoded `output` equals `card-from-image.json`.
- `formats` lists the input forms `json`, `qr-payload`, `erezept-url`, `qr-image` and the output
  forms `document`, `qr-payload`, `qr-image` for `chmed16a`.

The parity test checks this over the whole corpus.

## Scenario 6 — Privacy and offline (SC-007, SC-009)

The tests `ProblemLeakTest`/log scan and `NoOutboundNetworkTest` cover the new forms. Expected: no
occurrence of `CHMED16A1`, `eprescription.hin.ch` or a fixture signature value in logs, reports or
problem details, and no outbound connection attempt.

## Performance (SC-006)

```bash
taskset -c 0-3 sh ./mvnw -B -pl format-chmed16a -am test -Dtest=PerformanceTest -Dsurefire.failIfNoSpecifiedTests=false
```

**Expected**: Reading a QR image adds less than 1 s to the conversion of the same plan from JSON.
The measured values and the payload size of the 20-medication plan go into this file after
implementation.
