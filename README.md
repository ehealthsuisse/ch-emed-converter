# ch-emed-converter

Bidirectional conversion between **eMediplan CHMED16A** (medication plan, prescription) and
**CH EMED 6.0.0** (Medication Card and Medication Prescription document), as a Java 21 library and a
command-line tool. CH EMED is the hub model: every conversion goes to or from CH EMED, so further
formats can be plugged in as modules.

- **Offline and local**: no network calls, no telemetry; patient data never leaves the process and
  never appears in reports or logs.
- **Conformant output**: CH EMED output is validated against the pinned IG packages (HL7 FHIR
  validator through HAPI FHIR); CHMED16A prescriptions meet the data requirements of eRezept
  Schweiz.
- **No silent loss**: everything that cannot be represented is listed in a machine-readable
  conversion report; `--strict` turns clinical loss into a failure.
- **Deterministic**: the same input and options give byte-identical output.

| Module | Purpose |
|--------|---------|
| `converter-core` | framework-free API, conversion report, format SPI, original-representation PDF |
| `converter-validation` | offline CH EMED validation with the vendored IG packages |
| `format-chmed16a` | CHMED16A model, parser, validator, mappers, serializer |
| `converter-cli` | picocli command line (`ch-emed-converter.jar`) |

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
third-party licence overview.

## Command line

```text
java -jar converter-cli/target/ch-emed-converter.jar convert --from chmed16a --to ch-emed \
  plan.json -o card.json --report report.json
java -jar converter-cli/target/ch-emed-converter.jar convert --from ch-emed --to chmed16a \
  card.json --strict > plan.json
java -jar converter-cli/target/ch-emed-converter.jar formats
```

| Option | Meaning |
|--------|---------|
| `--from`, `--to` | `chmed16a` or `ch-emed` |
| `<input-file>` / `-` | input file or stdin (CHMED16A also as textual QR payload `CHMED16A1…`) |
| `-o`, `--output` | output file (default: stdout) |
| `--report <file>`, `--report-format json\|text` | JSON report file; report format on stderr |
| `--strict` | fail if clinically relevant information cannot be represented |
| `--language`, `--confidentiality` | document language and confidentiality if the source has none |
| `--patient-id <system>\|<value>`, `--author <file>` | data CH EMED requires but CHMED16A may lack |

Exit codes: `0` success or success with warnings, `1` failed (nothing on stdout), `2` usage error,
`3` internal error. Full contract: [contracts/cli.md](specs/001-chmed16a-ch-emed-conversion/contracts/cli.md).

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

## Specifications and mapping

- Architecture overview (modules, pipeline, adding a format): [docs/architecture.md](docs/architecture.md)
- Pinned versions (CH EMED, CH Core, CHMED16A commit, eRezept): [docs/specifications.md](docs/specifications.md)
- Mapping rules, capability gaps, reason codes: [docs/mapping/chmed16a-ch-emed.md](docs/mapping/chmed16a-ch-emed.md)
- Third-party licences: [docs/third-party-licenses.md](docs/third-party-licenses.md)
- Change history: [CHANGELOG.md](CHANGELOG.md)

## Tools

- `tools/fetch-packages.sh` — the only step with network access: vendors the IG packages.
- `tools/validate-outputs.sh` — cross-check of all test outputs with the official `validator_cli.jar`
  (downloaded once into `tools/`, ignored by git).

## Performance

The conversion itself (mapping, PDF, serialization) takes well under 0.2 s for a plan with 100
medicaments (SC-005a, limit 2 s). The official validation of the produced CH EMED document dominates:
about 10 s with a warm validation context, linear at about 80 ms per MedicationStatement (SC-005b,
limit 15 s). The cold start (loading the packages) takes about 30–40 s. Reuse the `Converter`.

## Data protection

Reports and logs contain element paths, codes and severities only. Operators must not enable DEBUG
logging for `org.hl7.fhir` or `ca.uhn.fhir` (the libraries log resource content there). Test
fixtures are synthetic or official example data.

## Licence

Copyright 2026 x-tention. Apache License, Version 2.0, see [LICENSE](LICENSE) and
[NOTICE](NOTICE). Third-party licences:
[docs/third-party-licenses.md](docs/third-party-licenses.md).
