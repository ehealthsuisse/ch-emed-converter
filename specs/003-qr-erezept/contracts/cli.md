# Contract changes: Command-Line Interface (converter-cli)

Changes to the [feature 001 CLI contract](../../001-chmed16a-ch-emed-conversion/contracts/cli.md).
Existing options, streams and exit codes are unchanged.

Feature 004 adds `--output-form treatment-plans` and two options for `--to ch-emed-epr`:
[specs/004-ch-emed-epr/contracts/cli.md](../../004-ch-emed-epr/contracts/cli.md).

## New options

| Option | Meaning | Default |
|--------|---------|---------|
| `--output-form document\|qr-payload\|qr-image` | output form; `qr-*` only with `--to chmed16a` | `document` |
| `--signature-output <file>` | write the signature data of a signed eRezept as JSON (`{"identity","timestamp","signature","verified":false}`) | `<output>.signature.json` if `-o <output>` is given |

## Input

`<input-file>` or stdin may now be:
- a PNG or JPEG image;
- a text file with a `CHMED16A1…` payload;
- a text file with an eRezept URL;
- the CHMED16A JSON, as before.

The form is detected by content, and `--from chmed16a` covers all of them.

## Streams

- **stdout:** the output in the requested form, nothing else. With `qr-image`, these are PNG bytes.
- **stderr:** as before. If a signed eRezept is converted to stdout without `--signature-output`,
  one line says that the signature data was not written. The line contains no values.
- **Signature file:** It is written only on success. It is never printed to stdout or stderr.

## Exit codes

Unchanged:
- 1 for every failed conversion, including image, QR, URL and capacity errors.
- 2 for usage errors, including `--output-form qr-*` with `--to ch-emed`.

## Examples

```text
ch-emed-converter convert --from chmed16a --to ch-emed plan-qr.png -o card.json
ch-emed-converter convert --from chmed16a --to ch-emed erezept-url.txt -o rx.json        # also writes rx.json.signature.json
ch-emed-converter convert --from ch-emed --to chmed16a card.json --output-form qr-image -o plan.png
ch-emed-converter convert --from ch-emed --to chmed16a rx.json --output-form qr-payload -o rx.txt   # input for the HIN erxproxy
```
