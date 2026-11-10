# Contract changes: Command-Line Interface (converter-cli)

Additions to the [feature 001 CLI contract](../../001-chmed16a-ch-emed-conversion/contracts/cli.md)
and the [feature 003 changes](../../003-qr-erezept/contracts/cli.md). There are two new options,
and streams and exit codes are unchanged.

## New values

| Option | New value | Meaning |
|--------|-----------|---------|
| `--from`, `--to` | `ch-emed-epr` | CH EMED EPR 3.0.0 document (FHIR JSON) |
| `--output-form` | `treatment-plans` | only with `--from ch-emed --to ch-emed-epr` and a Medication Card: one `collection` Bundle with one MTP document per treatment |

## New option (FR-017, research R15)

| Option | Meaning |
|--------|---------|
| `--treatment-plan <documentId>,<statementId>` | repeatable; one per prescribed medication (PRE) or card entry (PMLC) without a treatment-plan reference, in document order; both values `urn:uuid:<uuid>`. A malformed value exits `2`; a wrong count fails with `TREATMENT_PLAN_REFERENCES_MISMATCH` (exit `1`) |
| `--original-representation <file>` | PDF/A for the original representation of PRE, PMLC or MTP; used if its XMP declares PDF/A-1 or -2; otherwise reported, and the next step of research R16 applies (PDF/A of the source, then a rendered PDF/A for PRE and PMLC). Not used with `--output-form treatment-plans` (reported) |

`--patient-id`, `--author` and `--confidentiality` supply data that CH EMED EPR requires and the
source lacks, as for CHMED16A.

## Examples

```bash
java -jar ch-emed-converter.jar convert --from ch-emed-epr --to ch-emed pmlc.json -o card.json
java -jar ch-emed-converter.jar convert --from ch-emed --to chmed16a card.json --output-form qr-image -o plan.png
java -jar ch-emed-converter.jar convert --from ch-emed --to ch-emed-epr card.json --output-form treatment-plans -o mtp-set.json
java -jar ch-emed-converter.jar convert --from ch-emed --to ch-emed-epr rx.json -o pre.json \n  --treatment-plan urn:uuid:<mtp-1>,urn:uuid:<statement-1> --treatment-plan urn:uuid:<mtp-2>,urn:uuid:<statement-2>
```

The second example works for cards with a person author; for a PMLC, whose author is the
aggregation service without GLN, it exits `1` with `MISSING_REQUIRED_DATA` (spec US1.2).

`--from chmed16a --to ch-emed-epr` exits `1` with `UNSUPPORTED_CONVERSION`; the message names the
two steps through `ch-emed`.

## formats command

`formats` lists `ch-emed-epr` with its input and output forms and its pinned specification.
