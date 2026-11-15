# ch-emed-converter

Bidirectional conversion between **eMediplan CHMED16A** and **CHMED23A** (medication plan,
prescription; CHMED23A is deprecated by IG eMediplan but still supported) and
**CH EMED 6.0.0** (Medication Card and Medication Prescription document), as a Java 21 library, a
command-line tool and a REST service with identical behaviour. CH EMED is the hub model: every conversion goes to or from CH EMED, so further
formats can be plugged in as modules.

- **Offline and local**: no network calls, no telemetry; patient data never leaves the process and
  never appears in reports or logs.
- **Conformant output**: CH EMED output is validated against the pinned IG packages (HL7 FHIR
  validator through HAPI FHIR); CHMED16A prescriptions meet the data requirements of eRezept
  Schweiz. As unsigned QR payload (`CHMED16A1…`) they are the input of the HIN erxproxy, which
  signs them; the converter itself never contacts HIN.
- **QR codes and eRezept**: reads eMediplan QR images (PNG, JPEG) and signed eRezept URLs or their
  QR codes, and writes QR payloads and, for medication plans, QR images (feature 003).
- **No silent loss**: everything that cannot be represented is listed in a machine-readable
  conversion report; `--strict` turns clinical loss into a failure.
- **Deterministic**: the same input and options give byte-identical output.

| Module | Purpose |
|--------|---------|
| `converter-core` | framework-free API, conversion report, format SPI, original-representation PDF |
| `converter-qr` | QR code image reader and writer (ZXing), without format knowledge |
| `converter-validation` | offline CH EMED validation with the vendored IG packages |
| `format-chmed16a` | CHMED16A model, parser, validator, mappers, serializer |
| `format-chmed23a` | CHMED23A (deprecated by IG eMediplan): model, reader, conformance rules, mappers, writer, QR payload with chunks |
| `format-ch-emed-epr` | CH EMED EPR 3.0.0 (CARA EMED): PMLC, PRE, MTP, DIS, PADV and PML in both directions with CH EMED |
| `converter-cli` | picocli command line (`ch-emed-converter.jar`) |
| `converter-rest` | stateless REST/JSON service, Spring Boot (`ch-emed-converter-rest.jar`) |

## Build

JDK 21 and the Maven wrapper are enough; the IG packages are vendored, no network is needed after
the first dependency download.

```text
./mvnw verify
```

The build runs unit and integration tests, the privacy scan, Spotless (google-java-format), the
licence report (`target/licenses/THIRD-PARTY.txt`) and packages the CLI as `converter-cli/target/ch-emed-converter.jar` plus its dependencies in
`converter-cli/target/lib/` (the JAR references them via its manifest `Class-Path`, so ship the JAR and
`lib/` together). The distribution `converter-cli/target/ch-emed-converter-<version>-dist.zip`
contains the JAR, `lib/`, the start scripts `ch-emed-converter.sh` / `ch-emed-converter.bat`
(they use `JAVA_HOME`, Java 21+), `LICENSE`, `NOTICE`, `THIRD-PARTY.txt`, this README and the
third-party licence overview. The REST service is packaged the same way:
`converter-rest/target/ch-emed-converter-rest.jar` with its dependencies in `converter-rest/target/lib/`
(ship both together); its distribution `converter-rest/target/ch-emed-converter-rest-<version>-dist.zip`
contains the JAR, `lib/`, the start scripts `ch-emed-converter-rest.sh` / `ch-emed-converter-rest.bat`
(`JAVA_HOME`, Java 21+; JVM options in `JAVA_OPTS`), the operator documentation `rest-service.md`,
`LICENSE`, `NOTICE`, `THIRD-PARTY.txt`, this README and the third-party licence overview.

## Command line

```text
java -jar converter-cli/target/ch-emed-converter.jar convert --from chmed16a --to ch-emed \
  plan.json -o card.json --report report.json
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed --to chmed16a \
  card.json --strict > plan.json
java -jar converter-cli/target/ch-emed-converter.jar formats
# feature 003: QR image or signed eRezept URL in, QR payload or QR image out
java -jar converter-cli/target/ch-emed-converter.jar convert --from chmed16a --to ch-emed \
  plan-qr.png --author author.json -o card.json
java -jar converter-cli/target/ch-emed-converter.jar convert --from chmed16a --to ch-emed \
  erezept-url.txt -o rx.json            # signature data in rx.json.signature.json
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed --to chmed16a \
  card.json --output-form qr-image -o plan.png
# feature 004: CH EMED EPR (CARA EMED), always through ch-emed
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed-epr --to ch-emed \
  pmlc.json -o card.json
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed --to ch-emed-epr \
  rx.json -o pre.json --treatment-plan urn:uuid:<mtp>,urn:uuid:<statement>
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed --to ch-emed-epr \
  card.json --output-form treatment-plans -o mtp-set.json
# feature 006: CHMED23A (deprecated by IG eMediplan): JSON, payload (also chunks) or QR image
java -jar converter-cli/target/ch-emed-converter.jar convert --from chmed23a --to ch-emed \
  plan.json -o card.json
java -jar converter-cli/target/ch-emed-converter.jar convert --from chmed23a --to ch-emed \
  plan-chunks.txt -o card.json          # CHMED23A.1/3.… CHMED23A.2/3.… CHMED23A.3/3.…
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed --to chmed23a \
  card.json --output-form qr-image -o plan.png
```

| Option | Meaning |
|--------|---------|
| `--from`, `--to` | `chmed16a`, `chmed23a`, `ch-emed` or `ch-emed-epr`; every conversion has `ch-emed` on one side (convert `chmed16a` → `ch-emed` → `ch-emed-epr` in two steps) |
| `<input-file>` / `-` | input file or stdin; CHMED16A also as textual QR payload `CHMED16A1…`, PNG/JPEG QR image or signed eRezept URL; CHMED23A also as payload `CHMED23A.…`, several chunk payloads in one text input or PNG/JPEG QR image (detected by content) |
| `-o`, `--output` | output file (default: stdout) |
| `--output-form document\|qr-payload\|qr-image\|treatment-plans` | output form; `qr-*` for `--to chmed16a` (`qr-image` only for medication plans) and `--to chmed23a` (`qr-image` also for prescriptions), `treatment-plans` for `--to ch-emed-epr` and a medication card (one MTP per treatment) |
| `--signature-output <file>` | signature data of a signed eRezept (raw, not verified); default `<output>.signature.json` |
| `--report <file>`, `--report-format json\|text` | JSON report file; report format on stderr |
| `--strict` | fail if clinically relevant information cannot be represented |
| `--language`, `--confidentiality` | document language and confidentiality if the source has none |
| `--patient-id <system>\|<value>`, `--author <file>` | data CH EMED requires but CHMED16A or CHMED23A may lack |
| `--treatment-plan <documentId>,<statementId>` | repeatable, `--to ch-emed-epr`: reference to the treatment plan (MTP) in the EPR of each prescribed medication or card entry without one, in document order |
| `--original-representation <file>` | `--to ch-emed-epr`: PDF/A used as original representation (otherwise one is rendered for PRE and PMLC) |

Exit codes: `0` success or success with warnings, `1` failed (nothing on stdout), `2` usage error,
`3` internal error or validation not completed under system load (`VALIDATION_INCOMPLETE`; retry).
Full contract: [contracts/cli.md](specs/001-chmed16a-ch-emed-conversion/contracts/cli.md), QR and
eRezept additions in [specs/003-qr-erezept/contracts/cli.md](specs/003-qr-erezept/contracts/cli.md), CH EMED EPR additions in
[specs/004-ch-emed-epr/contracts/cli.md](specs/004-ch-emed-epr/contracts/cli.md), CHMED23A additions in
[specs/006-chmed23a/contracts/cli.md](specs/006-chmed23a/contracts/cli.md).

## Library

```java
Converter converter = Converters.create();          // discovers the format modules once
ConversionResult result =
    converter.convert(
        new ConversionRequest(
            FormatId.CHMED16A, FormatId.CH_EMED, planJson,
            ConversionOptions.defaults().withAuthor(author).withStrict(false)));

result.outcome();                 // SUCCESS, SUCCESS_WITH_WARNINGS or FAILED
result.output();                  // Optional<byte[]>: present unless FAILED
result.report().entries();        // paths, reason codes, severities, clinicalLoss
```

`convert` never throws for invalid input; thread-safe; share one `Converter` (the validation context
is expensive to build). Contract:
[contracts/library-api.md](specs/001-chmed16a-ch-emed-conversion/contracts/library-api.md).

## REST service

```text
java -jar converter-rest/target/ch-emed-converter-rest.jar
```

`POST /api/v1/conversions/{from}/{to}` takes a JSON message with the input document
base64-encoded and the same options as the CLI; the response carries outcome, report and the output
document base64-encoded (byte-identical to library and CLI):

```json
{ "document": "eyJJZCI6…",
  "options": { "strict": false, "author": { "gln": "7601000000001", "given": "…", "family": "…",
               "organization": { "name": "…", "street": "…", "zip": "…", "city": "…", "country": "CH" } } } }
```

The document may also be a QR image or a signed eRezept URL; `options.outputForm` (`document`,
`qr-payload`, `qr-image`) selects the output form, `outputMediaType` names it, and a signed eRezept
adds the raw, unverified `signature` beside the document (feature 003). For `ch-emed-epr`, `outputForm`
`treatment-plans`, `treatmentPlanReferences` (`[{documentId, statementId}]`) and
`originalRepresentation` (PDF/A, base64) work as the CLI options (feature 004, OpenAPI 1.2.0).

Failures are RFC 9457 problem details with a distinct `errorType`; `GET /api/v1/capabilities` lists
conversions, input and output forms, options, pinned specifications and limits; `GET /api/v1/openapi.yaml` serves the
contract ([openapi.yaml](specs/002-rest-conversion-service/contracts/openapi.yaml)).

**The service has no authentication and no TLS and must run behind an API gateway or in a protected
network** — requests contain patient data. Operation, configuration and probes:
[docs/operations/rest-service.md](docs/operations/rest-service.md).

## Specifications and mapping

- Architecture overview (modules, pipeline, adding a format): [docs/architecture.md](docs/architecture.md)
- Pinned versions (CH EMED, CH Core, CHMED16A commit, eRezept): [docs/specifications.md](docs/specifications.md)
- Mapping rules, capability gaps, reason codes: [docs/mapping/chmed16a-ch-emed.md](docs/mapping/chmed16a-ch-emed.md), [docs/mapping/chmed23a-ch-emed.md](docs/mapping/chmed23a-ch-emed.md)
- Third-party licences: [docs/third-party-licenses.md](docs/third-party-licenses.md)
- Change history: [CHANGELOG.md](CHANGELOG.md)

## Tools

- `tools/fetch-packages.sh` — the only step with network access: vendors the IG packages.
- `tools/validate-outputs.sh` — cross-check of all test outputs with the official `validator_cli.jar`
  (downloaded once into `tools/`, ignored by git).

## Performance

The conversion itself (mapping, PDF, serialization) takes well under 0.2 s for a plan with 100
medicaments (SC-005a, limit 2 s). The official validation of the produced document dominates the
time. Measured on 2026-11-14 with a warm validation context, on the reference hardware (Docker
container limited to 4 vCPUs and 8 GB RAM, default heap, Ryzen 9 3950X) and on a Windows
workstation (Intel i7-1260P, 16 logical cores, no limits):

| Case | Reference hardware | Windows workstation | Limit |
|------|--------------------|---------------------|-------|
| CHMED16A → CH EMED, 100 medicaments, with validation (SC-005b) | 0.8 s | 2.0 s | 30 s |
| CHMED16A → CH EMED, 20 medications, with validation (SC-005c) | 0.2 s | 0.4 s | 5 s |
| CHMED23A → CH EMED → CHMED23A, 20 medications | 0.4 s | 1.0 s | – |
| CH EMED → CH EMED EPR PMLC, 20 medications | 1.0 s | 2.3 s | 5 s |
| CH EMED EPR PML ↔ CH EMED, 109 entries | 4 s | 4.5–5.7 s | 30 s |
| REST service, 20 simultaneous requests with 20 medications (slowest answer) | 2.9 s | 7.8 s | – |
| Cold start (loading the packages, first conversion) | 5 s | 9 s | – |

These times need HL7 FHIR core 6.10.4 or later, pinned in the parent POM: with the 6.9.12 that
HAPI 8.12.1 ships, validation is 3–11 times slower on the Windows workstation (100 medicaments
11 s, PML 50 s) and the EPR limits are missed, because that version starts a thread for every
pattern check. Reuse the `Converter`. The REST service builds the converter once at startup, adds
no measurable time of its own and runs at most four conversions at a time by default.

## Data protection

Reports and logs contain element paths, codes and severities only. Operators must not enable DEBUG
logging for `org.hl7.fhir` or `ca.uhn.fhir` (the libraries log resource content there). Test
fixtures are synthetic or official example data.

## Licence

Copyright 2026 x-tention. Apache License, Version 2.0, see [LICENSE](LICENSE) and
[NOTICE](NOTICE). Third-party licences:
[docs/third-party-licenses.md](docs/third-party-licenses.md).
