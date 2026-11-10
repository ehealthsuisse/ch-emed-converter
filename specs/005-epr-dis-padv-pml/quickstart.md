# Quickstart & Validation Guide: CH EMED EPR, further document types

Runnable scenarios that show the feature works end to end.

- Contracts: [library-api.md](contracts/library-api.md), [cli.md](contracts/cli.md),
  [rest-api.md](contracts/rest-api.md).
- Data model: [data-model.md](data-model.md).

`CLI` below stands for `java -jar converter-cli/target/ch-emed-converter.jar`.

## Prerequisites

- Java 21, and the build of this branch. No new packages.
- Fixtures:
  - official CH EMED EPR examples in `format-ch-emed-epr/src/test/resources/fixtures/official/`;
  - CH EMED examples in `format-ch-emed-epr/src/test/resources/fixtures/chemed/` (research R8).

## Build and test

```bash
./mvnw -B clean verify
```

Run the time tests on the build machine, limited to 4 vCPU (`tools/remote-build.sh load`).

## Scenario 1 — Read DIS, PADV and PML from the EPR (US1, SC-001)

```bash
CLI convert --from ch-emed-epr --to ch-emed fixtures/official/Bundle-DocumentDis1.json -o dis.json --report dis-report.json
CLI convert --from ch-emed-epr --to ch-emed fixtures/official/Bundle-DocumentPadvChangeParacetamolCARAPMP004.json -o padv.json
CLI convert --from ch-emed-epr --to ch-emed fixtures/official/Bundle-BundlePml1.json -o pml.json
```

Expected:
- Exit `0` each time.
- The outputs declare the CH EMED dispense, advice and list profiles and validate.
- The report shows `PROFILE_CHANGED` entries, no clinical loss and no `REFERENCE_INCONSISTENT`.

## Scenario 2 — Dispense to the EPR with a treatment-plan reference (US2.2)

```bash
CLI convert --from ch-emed --to ch-emed-epr fixtures/chemed/Bundle-3-1-MedicationDispense.json -o dis.json
```

Without the option: exit `1`, and `MISSING_REQUIRED_DATA` at the dispense's extension, without any
identifier. Then:

```bash
CLI convert --from ch-emed --to ch-emed-epr fixtures/chemed/Bundle-3-1-MedicationDispense.json -o dis.json \
  --treatment-plan urn:uuid:00000000-0000-4000-8000-000000000001,urn:uuid:00000000-0000-4000-8000-000000000002
```

Expected:
- Exit `0`, and a valid DIS.
- `DERIVED_VALUE` for the reference.
- `UNMAPPABLE_ELEMENT` for each removed element.

## Scenario 3 — Medication list to the EPR (US2.5)

```bash
CLI convert --from ch-emed --to ch-emed-epr fixtures/chemed/Bundle-2-1-MedicationList.json -o pml.json
```

Expected: exit `0` and a valid PML, because the author is a Device.

The same list with a PractitionerRole as author (synthetic fixture) gives exit `1` and
`MISSING_REQUIRED_DATA` at `Composition.author`.

## Scenario 4 — Contradicting references (FR-008, SC-008)

Convert the synthetic PADV whose changed statement belongs to another treatment plan than the
observation references.

Expected:
- Exit `0`, with the output unchanged apart from the profiles.
- One WARNING `REFERENCE_INCONSISTENT` naming both extension paths and no identifiers.

## Scenario 5 — No path to eMediplan (edge case)

```bash
CLI convert --from ch-emed --to chmed16a dis-from-scenario-1.json -o plan.json
```

Expected: exit `1`, `UNSUPPORTED_DOCUMENT_TYPE`, and a message naming medication card and
medication prescription as supported.

## Scenario 6 — Interfaces (US3, SC-005)

```bash
CLI formats
curl -s localhost:8080/api/v1/capabilities
```

Expected: `ch-emed-epr` lists the document types `PMLC, PRE, MTP, DIS, PADV, PML`.

The parity test (`ServiceLibraryParityTest`) passes over the new corpus.

## Scenario 7 — Performance (SC-004)

`PerformanceTest` converts a PML with 100 entries of each kind to CH EMED and back, including
validation, on 4 vCPU.

Expected: below the limit of feature 004. The time is recorded in
`docs/operations/rest-service.md`.
