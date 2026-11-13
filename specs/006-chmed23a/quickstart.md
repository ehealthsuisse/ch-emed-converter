# Quickstart & Validation Guide: CHMED23A ↔ CH EMED

Runnable scenarios that show the feature works end to end.

- Contracts: [library-api.md](contracts/library-api.md), [cli.md](contracts/cli.md),
  [rest-api.md](contracts/rest-api.md).
- Data model: [data-model.md](data-model.md).

`CLI` below stands for `java -jar converter-cli/target/ch-emed-converter.jar`, and `FX` stands for
`format-chmed23a/src/test/resources/fixtures`.

Walked through on 2026-11-13 with the built CLI and REST service on the Linux machine (T078). The
expected results below are the observed ones.

## Prerequisites

- Java 21, and the build of this branch. There are no new packages.
- Fixtures (research R13): `FX/chmed23a/official/Example1.json` … `Example4.json`, from
  `examples.md` at commit `0b579aa6`, and `FX/chmed23a/posology/` and `FX/chmed23a/dotnet/`.
- The tests build their synthetic inputs (chunks, medType 2, invalid posology, large plan) in
  memory. For the CLI, create them from the fixtures as shown in each scenario. `python3` and `jq`
  are used for that.

## Build and test

```bash
./mvnw -B clean verify
```

Run the time tests on the build machine, limited to 4 vCPU (`tools/remote-build.sh load`).

## Scenario 1: Read the official plans and the prescription (US1, US2, SC-001)

```bash
CLI convert --from chmed23a --to ch-emed FX/chmed23a/official/Example1.json -o card1.json --report r1.json
CLI convert --from chmed23a --to ch-emed FX/chmed23a/official/Example3.json -o card3.json --report r3.json \
  --patient-id 'urn:oid:2.999.1|T078'
CLI convert --from chmed23a --to ch-emed FX/chmed23a/official/Example4.json -o rx4.json --report r4.json \
  --patient-id 'urn:oid:2.999.1|T078'
```

Example 3 and Example 4 carry a local patient id whose `sId` is not an OID (research F7). It is
reported as `UNMAPPABLE_ELEMENT` at `/patient/ids/0/sId` and not repaired. Without `--patient-id`,
both fail with `MISSING_REQUIRED_DATA` at `/patient/ids`.

Expected:
- Exit `0` each time (`SUCCESS_WITH_WARNINGS`), and the outputs validate against CH EMED 6.0.0
  without errors.
- `card1.json` is a Medication Card (five MedicationStatements) with a PractitionerRole author.
  `card3.json` has the Patient as author (`auth` 2).
- `rx4.json` is a Medication Prescription with seven MedicationRequests. The medicament with four
  posologies becomes four MedicationRequests, and `r4.json` has the `DERIVED_VALUE` entry for
  `/meds/0/pos` (research R10, F4).
- The reports list:
  - `DERIVED_VALUE` for the document id and for medicament ids without a usable code;
  - `UNMAPPABLE_ELEMENT` with clinical loss for `mData` (`w`, `h`, `rcs`);
  - `DOSAGE_FREE_TEXT_FALLBACK` with clinical loss for each posology without an exact CH EMED
    equivalent (all four of Example 3, and the cyclic posology with unit `Appl` in Example 1);
  - INFORMATION for usage `-` fields, such as `nbPack` in a plan (research F2);
  - WARNING `CHMED_INVALID_FIELD` for GLNs with a wrong GS1 check digit (`/hcPerson/gln` in
    Example 1 and 4, `/hcOrg/gln` in Example 4; rule C23-OBJ-15). The values are carried unchanged.
- No patient data appears in the reports.

## Scenario 2: The same plan in every input form (US1.2, SC-003)

```bash
CLI convert --from ch-emed --to chmed23a card1.json --output-form qr-payload -o plan1.txt
CLI convert --from chmed23a --to ch-emed plan1.txt -o card1-payload.json
sed -r 's/^CHMED23[A-Z]+\.(.*)$/\1/I' plan1.txt | base64 --decode | zcat > plan1-decoded.json
CLI convert --from chmed23a --to ch-emed plan1-decoded.json -o card1-decoded.json
CLI convert --from ch-emed --to chmed23a card1.json --output-form qr-image -o plan1.png
CLI convert --from chmed23a --to ch-emed plan1.png -o card1-image.json
```

Chunks, built from `plan1.txt`: three chunks `CHMED23A.i/3.` in the order 3, 1, 2, one per line.

```bash
python3 - <<'PY'
d = open("plan1.txt").read().strip()[len("CHMED23A."):]
k = -(-len(d) // 3); t = [f"CHMED23A.{i+1}/3.{d[i*k:(i+1)*k]}" for i in range(3)]
open("chunks-3-shuffled.txt", "w").write("\n".join([t[2], t[0], t[1]]) + "\n")
open("chunks-missing-2.txt", "w").write(" ".join([t[0], t[2]]))
open("chunks-duplicate-1.txt", "w").write(" ".join([t[0], t[0], t[1], t[2]]))
open("chunk-1-of-1.txt", "w").write(f"CHMED23A.1/1.{d}")
PY
CLI convert --from chmed23a --to ch-emed chunks-3-shuffled.txt -o card1-chunks.json
```

Expected:
- `plan1.txt` starts with `CHMED23A.` and decodes with the decoder from the specification (the
  `sed … | zcat` line above).
- `plan1.png` is a PNG with one QR code.
- `card1-payload.json`, `card1-decoded.json`, `card1-chunks.json` and `card1-image.json` are
  byte-identical.

## Scenario 3: Chunk errors (edge cases)

```bash
CLI convert --from chmed23a --to ch-emed chunks-missing-2.txt
CLI convert --from chmed23a --to ch-emed chunks-duplicate-1.txt
CLI convert --from chmed23a --to ch-emed chunk-1-of-1.txt
```

Expected: exit `1` and `CHUNK_INVALID` each time, with the messages "Chunk 2 of 3 is missing",
"Chunk 1 occurs more than once" and "A chunk total must be greater than 1". No Base64 data appears.

## Scenario 4: Write CHMED23A and round trip (US3, SC-002)

```bash
CLI convert --from ch-emed --to chmed23a card1.json -o plan1.json
CLI convert --from ch-emed --to chmed23a rx4.json --output-form qr-image -o rx4.png
```

Expected:
- `plan1.json` passes the conformance rules C23-*. Compared with `Example1.json`, it differs only by
  the documented gaps:
  - the derived `id`;
  - no `mData`;
  - no usage-`-` fields of a plan (`nbPack`, `prscbBy`), and no `inRes` with its default `false`;
  - no `moa` (not a code of CDTYP 62) and no `tdpc`;
  - the cyclic posology with unit `Appl` as FreeText (`t` 2) with unit `N/A`;
  - `lng` in lower case (`de`).
- `rx4.png` is written, because a CHMED23A prescription has no signature (research R6). For a
  CHMED16A prescription, the same request fails with `QR_IMAGE_NOT_ALLOWED`.

A plan that is too large for one code: the synthetic plan of `LargePlan` with 40 medicaments
(`format-chmed23a/src/test/java/.../it/LargePlan.java`), written to `large-plan.json`.

```bash
CLI convert --from chmed23a --to ch-emed large-plan.json -o large-card.json
CLI convert --from ch-emed --to chmed23a large-card.json --output-form qr-image -o big.png
CLI convert --from ch-emed --to chmed23a large-card.json --output-form qr-payload -o big.txt
```

Expected: the image fails with exit `1` and `QR_CAPACITY_EXCEEDED`, "Text exceeds the capacity of
one QR code (2331 bytes at level M)". The payload converts (about 5.1 KB).

## Scenario 5: Refusals (FR-003, FR-004, edge cases)

```bash
jq '.medType = 2' FX/chmed23a/official/Example1.json > medtype-2.json
jq '.meds[0].pos[0].po.ds = [1, 0, 1]' FX/chmed23a/official/Example1.json > invalid-daily-3-values.json
CLI convert --from chmed23a --to ch-emed medtype-2.json
CLI convert --from chmed23a --to ch-emed \
  format-chmed16a/src/test/resources/fixtures/chmed16a/official/readme-example-medication-plan.json
CLI convert --from chmed16a --to ch-emed FX/chmed23a/official/Example1.json
CLI convert --from chmed23a --to chmed16a FX/chmed23a/official/Example1.json
CLI convert --from chmed23a --to ch-emed invalid-daily-3-values.json
```

Expected, each with exit `1`:
1. `UNSUPPORTED_DOCUMENT_TYPE` at `/medType`, naming MP and Rx.
2. `UNSUPPORTED_VERSION`, "Input is CHMED16A; use source chmed16a".
3. `UNSUPPORTED_VERSION`, "Input is a CHMED23A document (camelCase keys); use source chmed23a".
4. `UNSUPPORTED_CONVERSION`, ending in "convert through ch-emed: chmed23a -> ch-emed -> chmed16a".
5. `CHMED_INVALID_FIELD` at `/meds/0/pos/0/po/ds`, "ds must contain exactly 4 values", without
   values.

## Scenario 6: Interfaces and capabilities (US4, SC-004)

```bash
CLI formats
java -jar converter-rest/target/ch-emed-converter-rest.jar &
curl -s localhost:8080/api/v1/capabilities | jq -c '.formats[] | select(.name=="chmed23a")'
jq -n --arg d "$(base64 -w0 plan1.txt)" '{document: $d}' \
  | curl -s -X POST -H 'Content-Type: application/json' --data-binary @- \
      localhost:8080/api/v1/conversions/chmed23a/ch-emed > response.json
jq -r '.output' response.json | base64 --decode > card1-rest.json
```

Expected:
- The `formats` line for `chmed23a` reads "input json, qr-payload, qr-image; output document,
  qr-payload, qr-image; document types MP, Rx; deprecated by IG eMediplan".
- The capabilities entry has `"deprecated": true`.
- The response has `outcome`, `output`, `outputMediaType` (`application/fhir+json`) and `report`.
  `card1-rest.json` is byte-identical to `card1-payload.json`, and `report.entries` equals the
  entries of the CLI report for the same input. The parity test asserts this over the whole corpus.

## Scenario 7: Performance (SC-005)

```bash
tools/remote-build.sh load
```

Expected: `PerformanceTest` (format-chmed23a) converts the 20-medicament plan to CH EMED and back,
including validation, within 30 s warm on 4 vCPUs. Measured on 2026-11-13: 0.33–0.45 s per round
trip. The measurement is recorded in `docs/operations/rest-service.md`.
