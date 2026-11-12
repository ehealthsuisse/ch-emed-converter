# Quickstart & Validation Guide: CHMED23A ↔ CH EMED

Runnable scenarios that show the feature works end to end.

- Contracts: [library-api.md](contracts/library-api.md), [cli.md](contracts/cli.md),
  [rest-api.md](contracts/rest-api.md).
- Data model: [data-model.md](data-model.md).

`CLI` below stands for `java -jar converter-cli/target/ch-emed-converter.jar`, and `FX` stands for
`format-chmed23a/src/test/resources/fixtures`.

## Prerequisites

- Java 21, and the build of this branch. There are no new packages.
- Fixtures (research R13):
  - `FX/chmed23a/official/Example1.json` … `Example4.json`, from `examples.md` at commit `0b579aa6`;
  - `FX/chmed23a/posology/`, `FX/chmed23a/dotnet/` and `FX/chmed23a/synthetic/`.

## Build and test

```bash
./mvnw -B clean verify
```

Run the time tests on the build machine, limited to 4 vCPU (`tools/remote-build.sh load`).

## Scenario 1: Read the official plans and the prescription (US1, US2, SC-001)

```bash
CLI convert --from chmed23a --to ch-emed FX/chmed23a/official/Example1.json -o card1.json --report r1.json
CLI convert --from chmed23a --to ch-emed FX/chmed23a/official/Example3.json -o card3.json
CLI convert --from chmed23a --to ch-emed FX/chmed23a/official/Example4.json -o rx4.json --report r4.json
```

Expected:
- Exit `0` each time, and the outputs validate against CH EMED 6.0.0.
- `card1.json` is a Medication Card with a PractitionerRole author. `card3.json` has the Patient as
  author (`auth` 2).
- `rx4.json` is a Medication Prescription. The medicament with four posologies becomes four
  MedicationRequests, and `r4.json` has the `DERIVED_VALUE` entry for it (research R10, F4).
- The reports list:
  - `DERIVED_VALUE` for the document id;
  - `UNMAPPABLE_ELEMENT` with clinical loss for `mData`;
  - `DOSAGE_FREE_TEXT_FALLBACK` for each sequence and interval posology;
  - INFORMATION for usage `-` fields (research F2).
- No patient data appears in the reports.

## Scenario 2: The same plan in every input form (US1.2, SC-003)

```bash
CLI convert --from ch-emed --to chmed23a card1.json --output-form qr-payload -o plan1.txt
CLI convert --from chmed23a --to ch-emed plan1.txt -o card1-payload.json
CLI convert --from chmed23a --to ch-emed FX/chmed23a/synthetic/Example1-chunks-3-shuffled.txt -o card1-chunks.json
CLI convert --from ch-emed --to chmed23a card1.json --output-form qr-image -o plan1.png
CLI convert --from chmed23a --to ch-emed plan1.png -o card1-image.json
```

Expected:
- `plan1.txt` starts with `CHMED23A.` and decodes with
  `sed -r 's/^CHMED23[A-Z]+\.(.*)$/\1/I' | base64 --decode | zcat`, the decoder from the
  specification.
- `card1-payload.json`, `card1-chunks.json` and `card1-image.json` are byte-identical to each other
  and to the conversion of the JSON that `plan1.txt` holds.

## Scenario 3: Chunk errors (edge cases)

```bash
CLI convert --from chmed23a --to ch-emed FX/chmed23a/synthetic/Example1-chunks-missing-2.txt
CLI convert --from chmed23a --to ch-emed FX/chmed23a/synthetic/Example1-chunks-duplicate-1.txt
CLI convert --from chmed23a --to ch-emed FX/chmed23a/synthetic/chunk-1-of-1.txt
```

Expected: exit `1` and `CHUNK_INVALID` each time. The messages name the index and contain no
Base64 data.

## Scenario 4: Write CHMED23A and round trip (US3, SC-002)

```bash
CLI convert --from ch-emed --to chmed23a card1.json -o plan1.json
CLI convert --from ch-emed --to chmed23a rx4.json --output-form qr-image -o rx4.png
```

Expected:
- `plan1.json` passes the conformance rules C23-*. Compared with `Example1.json`, it differs only by
  the documented gaps: no `mData`, no extensions, text posologies as FreeText, and the derived `id`.
- `rx4.png` is written, because a CHMED23A prescription has no signature (research R6). For a
  CHMED16A prescription, the same request fails with `QR_IMAGE_NOT_ALLOWED`.

A plan that is too large for one code:

```bash
CLI convert --from ch-emed --to chmed23a FX/expected/large-card.json --output-form qr-image -o big.png
```

Expected: exit `1` and `QR_CAPACITY_EXCEEDED` naming 2331 bytes. With `--output-form qr-payload`,
the same document converts.

## Scenario 5: Refusals (FR-003, FR-004, edge cases)

```bash
CLI convert --from chmed23a --to ch-emed FX/chmed23a/synthetic/medtype-2.json
CLI convert --from chmed23a --to ch-emed format-chmed16a/src/test/resources/fixtures/chmed16a/official/<plan>.json
CLI convert --from chmed16a --to ch-emed FX/chmed23a/official/Example1.json
CLI convert --from chmed23a --to chmed16a FX/chmed23a/official/Example1.json
CLI convert --from chmed23a --to ch-emed FX/chmed23a/synthetic/invalid-daily-3-values.json
```

Expected, each with exit `1`:
1. `UNSUPPORTED_DOCUMENT_TYPE`, naming MP and Rx.
2. `UNSUPPORTED_VERSION`, "use source chmed16a".
3. `UNSUPPORTED_VERSION`, "use source chmed23a".
4. `UNSUPPORTED_CONVERSION`, naming the path through `ch-emed`.
5. `CHMED_INVALID_FIELD` at `/meds/0/pos/0/po/ds`, without values.

## Scenario 6: Interfaces and capabilities (US4, SC-004)

```bash
CLI formats
curl -s localhost:8080/api/v1/capabilities | jq '.formats[] | select(.name=="chmed23a")'
jq -n --arg d "$(base64 -w0 plan1.txt)" '{document: $d}' \
  | curl -s -X POST -H 'Content-Type: application/json' --data-binary @- \
      localhost:8080/api/v1/conversions/chmed23a/ch-emed
```

Expected:
- The `formats` line for `chmed23a` ends in `deprecated by IG eMediplan`.
- The capabilities entry has `"deprecated": true`.
- The REST response document, Base64-decoded, equals `card1-payload.json`, and the reports are
  identical. The parity
  test asserts this over the whole corpus.

## Scenario 7: Performance (SC-005)

```bash
tools/remote-build.sh load
```

Expected: `PerformanceTest` (format-chmed23a) converts the 20-medicament plan to CH EMED and back,
including validation, within 30 s warm on 4 vCPUs. The measured time is recorded in
`docs/operations/rest-service.md`.
