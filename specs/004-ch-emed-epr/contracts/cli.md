# Contract changes: Command-Line Interface (converter-cli)

Additions to the [feature 001 CLI contract](../../001-chmed16a-ch-emed-conversion/contracts/cli.md)
and the [feature 003 changes](../../003-qr-erezept/contracts/cli.md). There are no new options,
and streams and exit codes are unchanged.

## New values

| Option | New value | Meaning |
|--------|-----------|---------|
| `--from`, `--to` | `ch-emed-epr` | CH EMED EPR 3.0.0 document (FHIR JSON) |
| `--output-form` | `treatment-plans` | only with `--from ch-emed --to ch-emed-epr` and a Medication Card: one `collection` Bundle with one MTP document per treatment |

`--patient-id`, `--author` and `--confidentiality` supply data that CH EMED EPR requires and the
source lacks, as for CHMED16A.

## Examples

```bash
java -jar ch-emed-converter.jar convert --from ch-emed-epr --to ch-emed pmlc.json -o card.json
java -jar ch-emed-converter.jar convert --from ch-emed --to chmed16a card.json --output-form qr-image -o plan.png
java -jar ch-emed-converter.jar convert --from ch-emed --to ch-emed-epr card.json --output-form treatment-plans -o mtp-set.json
```

`--from chmed16a --to ch-emed-epr` exits `1` with `UNSUPPORTED_CONVERSION`; the message names the
two steps through `ch-emed`.

## formats command

`formats` lists `ch-emed-epr` with its input and output forms and its pinned specification.
