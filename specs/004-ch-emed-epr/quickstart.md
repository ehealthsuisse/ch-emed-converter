# Quickstart & Validation Guide: CH EMED EPR (CARA EMED)

Runnable scenarios that show the feature works end to end. Contracts:
[library-api.md](contracts/library-api.md), [cli.md](contracts/cli.md),
[rest-api.md](contracts/rest-api.md); data model: [data-model.md](data-model.md).

## Prerequisites

- Java 21, and the build of this branch.
- Vendored packages `ch.fhir.ig.ch-emed-epr#3.0.0` and `hl7.fhir.uv.tools.r4#0.9.0` (fetched by
  `tools/fetch-packages.sh`, checked against `manifest.json`).
- Fixtures: official examples in `format-ch-emed-epr/src/test/resources/fixtures/official/`, and
  the feature 001 corpus.

## Build and test

```bash
./mvnw -B clean verify
```

Time tests on the build machine, limited to 4 vCPU (`tools/remote-build.sh load`).

## Scenario 1 — Read a medication card from the EPR (US1, SC-001)

```bash
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed-epr --to ch-emed fixtures/official/pmlc-1.json -o card.json --report report.json
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed --to chmed16a card.json --output-form qr-image -o plan.png
```

Expected:
- exit `0` for both commands;
- `card.json` validates as a CH EMED Medication Card;
- `plan.png` holds the eMediplan of the same treatments;
- `report.json` lists the profile changes as INFORMATION only.

## Scenario 2 — Create an EPR prescription and card (US2, SC-002)

Convert the CH EMED reference prescriptions and cards of feature 001 with `--to ch-emed-epr`, first
without options, then with `--patient-id`, `--author` and one `--treatment-plan` per entry (for a
card, the identifiers of the MTP documents of scenario 3).

Expected:
- without options: `MISSING_REQUIRED_DATA` naming the treatment-plan references;
- with options: every result validates against the PRE or PMLC document profile and carries a
  PDF/A-1b original representation;
- unsupported elements are reported with their paths;
- a document without an author role fails with `MISSING_REQUIRED_DATA` until `--author` is given.

## Scenario 3 — Treatment plans from a card (US3, SC-004)

```bash
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed --to ch-emed-epr card.json --output-form treatment-plans -o mtp-set.json
```

Expected:
- a `collection` Bundle with one MTP document per treatment, in the order of the card;
- each MTP document validates on its own;
- a second run gives byte-identical output;
- a card without treatments fails with `NO_TREATMENTS`.

## Scenario 4 — Read prescriptions and treatment plans (US4)

Convert the official PRE and MTP examples to `ch-emed`, then the PRE results with `--to chmed16a`.

Expected:
- valid CH EMED documents;
- CHMED16A prescriptions that meet the eRezept rules, or the existing report of what is missing;
- `--from ch-emed-epr --to chmed16a` exits `1` with `UNSUPPORTED_CONVERSION` naming the path
  through `ch-emed`.

## Scenario 5 — Round trips (FR-010, SC-003)

EPR → CH EMED → EPR for the official examples, and CH EMED → EPR → CH EMED for the feature 001
documents. The differences match the documented changes of `docs/mapping/ch-emed-epr.md`.

## Scenario 6 — Same behaviour through all interfaces (US5, SC-006)

Run the scenarios through library, CLI and REST (`POST /api/v1/conversions/ch-emed/ch-emed-epr`).

Expected:
- identical documents and reports;
- `GET /api/v1/capabilities` lists `ch-emed-epr` with `document` and `treatment-plans`.

## Scenario 7 — Regression, privacy and offline (FR-013, SC-007, SC-008)

- **Regression**: the corpus of features 001–003 gives byte-identical output and reports with the
  EPR package loaded.
- **Privacy**: the log and report scan finds no patient data.
- **Offline**: the no-network test passes.

## Performance (SC-005, R14)

Measure on 4 vCPU:
- the start-up time of the validation context;
- a card with 20 medications to PMLC (limit of feature 001 SC-005c: < 5 s);
- the same card to `treatment-plans` (measured and recorded).
