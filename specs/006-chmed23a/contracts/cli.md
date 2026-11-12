# Contract changes: Command-Line Interface (converter-cli)

These are changes to the [feature 003 CLI contract](../../003-qr-erezept/contracts/cli.md). There
are no new options; streams and exit codes are unchanged.

| Item | Change |
|------|--------|
| `--from chmed23a` | accepts CHMED23A JSON, a `CHMED23A.` payload, several chunk payloads in one text input (whitespace-separated, any order), or a PNG/JPEG image with one QR code; detected by content |
| `--to chmed23a` | writes a CHMED23A plan or prescription |
| `--output-form document\|qr-payload\|qr-image` | `qr-*` also with `--to chmed23a`; a QR image of a prescription is allowed for CHMED23A. Help text: "qr-* only with --to chmed16a or chmed23a" |
| `formats` | new line `chmed23a: input json, qr-payload, qr-image; output document, qr-payload, qr-image; document types MP, Rx; deprecated by IG eMediplan` |
| `--from chmed23a --to chmed16a` (and other direct pairs) | exit `1`, `UNSUPPORTED_CONVERSION`, message names the path through `ch-emed` |

Exit codes as before:
- `1` for every failed conversion, including `CHUNK_INVALID` and `QR_CAPACITY_EXCEEDED`;
- `2` for usage errors, e.g. `--output-form qr-image --to ch-emed`.

## Examples

```text
ch-emed-converter convert --from chmed23a --to ch-emed plan.json -o card.json
ch-emed-converter convert --from chmed23a --to ch-emed plan-chunks.txt -o card.json      # CHMED23A.1/3.… CHMED23A.2/3.… CHMED23A.3/3.…
ch-emed-converter convert --from chmed23a --to ch-emed plan-qr.png -o card.json
ch-emed-converter convert --from ch-emed --to chmed23a card.json --output-form qr-image -o plan.png
ch-emed-converter convert --from ch-emed --to chmed23a rx.json --output-form qr-payload -o rx.txt
```
