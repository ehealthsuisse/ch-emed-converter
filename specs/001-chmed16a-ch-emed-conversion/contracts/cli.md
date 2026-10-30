# Contract: Command-Line Interface (converter-cli)

Runnable JAR `ch-emed-converter.jar`, thin layer over the [library API](./library-api.md).

## Synopsis

```text
ch-emed-converter convert --from <format> --to <format> [options] [<input-file>|-]
ch-emed-converter formats
ch-emed-converter --version
```

| Option | Meaning | Default |
|--------|---------|---------|
| `--from`, `--to` | `chmed16a` or `ch-emed` | required |
| `<input-file>` / `-` | input file or stdin | stdin |
| `-o, --output <file>` | write output document to file | stdout |
| `--report <file>` | write JSON conversion report to file | — |
| `--report-format json\|text` | report format on stderr | `text` |
| `--strict` | strict mode (FR-016) | off |
| `--language` | document language if `Patient.Lng` is absent: `de-CH`, `fr-CH`, `it-CH`, `en-CH` (FR-022) | `de-CH` |
| `--confidentiality` | `normal`, `restricted`, `secret` (FR-022) | `normal` |
| `--patient-id <system>\|<value>` | patient identifier if the source has none (FR-023) | — |
| `--author <file>` | JSON file with author details `{"gln","given","family","organization":{"name","street","zip","city","country"}}` (FR-023) | — |

`formats` prints supported conversion pairs and pinned specification versions.
`--version` prints converter version and pinned specification versions.

## Streams

- stdout: output document only (UTF-8 JSON), nothing else.
- stderr: report (text or JSON) and diagnostics; never input content or patient data.

## Exit codes

| Code | Meaning |
|------|---------|
| 0 | `SUCCESS` or `SUCCESS_WITH_WARNINGS` (output written; warnings in the report) |
| 1 | `FAILED` — invalid input, unsupported conversion/document type/version, strict-mode loss, output validation failure (no output written) |
| 2 | usage error (unknown option/format, missing argument) |
| 3 | internal error (e.g. validation context could not be loaded), or the validation could not be completed under system load (`VALIDATION_INCOMPLETE` in the report; retry later, added 2026-10-29) |

Warnings deliberately exit with 0 so that pipelines treat a valid output as success; callers
that need to react to warnings use `--strict` or evaluate the JSON report.

## Examples

```text
ch-emed-converter convert --from chmed16a --to ch-emed plan.json -o card.json --report report.json
cat card.json | ch-emed-converter convert --from ch-emed --to chmed16a --strict > plan.json
```
