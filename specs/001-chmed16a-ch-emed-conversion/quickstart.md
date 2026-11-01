# Quickstart & Validation Guide: CHMED16A ↔ CH EMED Conversion

Proves the feature end to end. Contracts: [library API](./contracts/library-api.md),
[CLI](./contracts/cli.md), [report schema](./contracts/conversion-report.schema.json);
entities: [data-model.md](./data-model.md).

## Prerequisites

- JDK 21 (`java -version` → 21.x)
- No network access needed after the first Maven dependency download (IG packages are vendored)
- Optional for the CI cross-check: `validator_cli.jar` (HL7) in `tools/`

## Build and test

```text
./mvnw verify
```

Expected: all modules build; test suites include per-rule mapping tests, official-example tests,
round-trip tests, determinism tests, report-schema tests and the PHI scan — all green.

## Scenario 1 — CHMED16A medication plan → CH EMED Medication Card (US1, SC-001)

```text
java -jar converter-cli/target/ch-emed-converter.jar convert --from chmed16a --to ch-emed \
  --author converter-cli/src/test/resources/author.json \
  format-chmed16a/src/test/resources/fixtures/chmed16a/plan-basic.json \
  -o /tmp/card.json --report /tmp/report.json; echo $?
```

Expected: exit code `0`; `/tmp/card.json` is a `Bundle` of type `document` whose `Composition`
conforms to the CH EMED 6.0.0 Medication Card document; `/tmp/report.json` validates against the
report schema with outcome `SUCCESS_WITH_WARNINGS` (the validator warns that the synthetic GLN
has no valid check digit). Without `--author` the conversion fails with `MISSING_REQUIRED_DATA`
(FR-023): a CHMED16A plan carries only the author GLN.

## Scenario 2 — CH EMED → CHMED16A (US2, SC-002)

```text
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed --to chmed16a \
  format-chmed16a/src/test/resources/fixtures/chemed/medication-card-example.json
```

Expected: exit code `0`; stdout is CHMED16A JSON (MedType 1) passing the CHMED16A
validator; every warning names a documented capability gap.

## Scenario 3 — Prescription and eRezept readiness (US3, FR-008b, SC-007)

Convert `fixtures/chemed/medication-prescription-example.json` to CHMED16A.
Expected: MedType 3, `rev` 3, `Auth` = prescriber GLN, `HcPerson`/`HcOrg` present, no
`EREZEPT_REQUIREMENT` entries (route and treatment reason of the example are reported as clinical
loss, a prescription has no such fields). Then convert `fixtures/chemed/rx-missing-prescriber-gln.json`
(prescriber GLN removed): exit code `1`, no output. The CH EMED profile already makes the GLN
mandatory, so the failure is `FHIR_VALIDATION` on `Practitioner.identifier`; the
`EREZEPT_REQUIREMENT` error with the path `Composition.author` is the safety net for library
callers that skip input validation (`PrescriptionIT`).

## Scenario 4 — Strict mode (US4)

Convert `fixtures/chmed16a/plan-unmappable-element.json` (with `--author`) without and with
`--strict`.
Expected: without → exit `0`, document written, outcome `SUCCESS_WITH_WARNINGS`, entry with `clinicalLoss: true`;
with → exit `1`, no stdout, same entry.

## Scenario 5 — Input forms, invalid and unsupported input (US1 scenario 3, FR-001, FR-004)

| Input fixture | Expected |
|---------------|----------|
| `plan-basic.qr.txt` (`CHMED16A1…` payload of `plan-basic.json`) | exit 0, output identical to the one of `plan-basic.json` |
| `qr-corrupt-base64.txt`, `qr-corrupt-gzip.txt` | exit 1, `PARSE_ERROR`, payload not echoed |
| `erezept-url.txt` | exit 1, error pointing to the QR/eRezept feature |
| `invalid-json.txt` | exit 1, `PARSE_ERROR` |
| `chmed23a-plan.json` | exit 1, `UNSUPPORTED_VERSION` naming CHMED23A |
| `chmed16a-pmc.json` (MedType 2) | exit 1, `UNSUPPORTED_DOCUMENT_TYPE` |
| `rx-missing-hcperson.json` | exit 0, WARNING `CHMED_REVISION_FIELD_MISSING` (and `EREZEPT_REQUIREMENT` WARNING) |
| `qr-oversized.txt` | exit 1, `PARSE_ERROR` (decoded size above the limit) |

All runs of this table use `--author converter-cli/src/test/resources/author.json --patient-id 'urn:oid:2.999.1|TEST-0001'`.

## Scenario 6 — Round trip and determinism (US5, SC-004, FR-018)

Run `./mvnw -pl format-chmed16a -am verify -Dit.test='RoundTripIT,DeterminismIT' -Dtest=NONE
-Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
(`target/roundtrip-differences.txt` lists all differences).
Expected: every fixture converted CHMED16A → CH EMED → CHMED16A (and reverse) differs only by
entries listed as capability gaps in `docs/mapping/chmed16a-ch-emed.md`; converting the same
input twice yields byte-identical output.

## Scenario 7 — Privacy (FR-017, SC-006)

The `PhiLeakTest` collects all reports and captured log output from the full corpus and scans
for every patient value present in the fixtures. Expected: zero matches.

## Scenario 8 — Performance (SC-005a/b)

`./mvnw -pl format-chmed16a -am test -Dtest=PerformanceTest -Dsurefire.failIfNoSpecifiedTests=false`
converts a 100-medication plan. Expected: the conversion itself < 2 s (SC-005a) and the official
validation of the result < 30 s (SC-005b, measured 3-19 s depending on the machine), and a 20-medication plan converted
and validated < 5 s (SC-005c); the cold start is logged separately.

## Cross-check with the official validator (Principle I)

```text
./mvnw verify                   # writes format-chmed16a/target/it-outputs/*.json
tools/validate-outputs.sh       # downloads tools/validator_cli.jar once
```

Expected: `OK: <n> documents, Success: 0 errors, …`.
