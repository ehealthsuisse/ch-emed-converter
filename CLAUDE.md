# CLAUDE.md

Guidance for Claude Code (and other coding agents) in this repository. Read the
[README](README.md) for what the converter does and how it is used; this file only adds what
is needed to work on it.

## Authority

- The constitution [.specify/memory/constitution.md](.specify/memory/constitution.md) wins over
  everything else, this file included. Its principles are non-negotiable: specification
  conformance, lossless and explicit mapping (every loss in the conversion report), test-first
  with reference fixtures, patient data protection, determinism.
- Change it only through `/speckit-constitution`, with a version bump (semver) and the rationale.

## Workflow

- Features follow Spec Kit: specify → (clarify) → plan → tasks → implement, in
  `specs/NNN-name/` (`spec.md`, `plan.md`, `tasks.md`, `contracts/`). Tasks are referenced by
  their IDs (e.g. T084) in commits and docs.
- Branches start from `dev`; pull requests go against `dev`, not `main`.
- Commit messages: `Feature NNN: <what>` for feature work, otherwise a short imperative
  sentence.
- Before a change is done: tests pass, the mapping spec is updated for any mapping change,
  `CHANGELOG.md` notes changes to output structure, reason codes or the report format.

## Layout

- `converter-core` (API, report, format SPI) and one `format-*` module per format
  (`chmed16a`, `chmed23a`, `ch-emed-epr`). CH EMED is the hub: every conversion goes to or from
  CH EMED; a new format is a new module, see [docs/architecture.md](docs/architecture.md).
- `converter-qr`, `converter-validation`, `converter-cli`, `converter-rest`: see the README table.
- `docs/mapping/`: mapping rules, capability gaps and reason codes per format pair, plus
  `domain-review-questions.md` (open questions for the domain review, German and English).
- `docs/specifications.md`: pinned specification versions. Never use a different version
  without updating it.

## Build and test

- JDK 21 and the Maven wrapper; no network after the first dependency download (IG packages
  are vendored by `tools/fetch-packages.sh`, the only step with network access).
- Full build: `./mvnw verify` (tests, privacy scan, Spotless, licence report, packaging). It
  takes several minutes; for one module use `./mvnw -pl format-chmed23a -am verify`.
- Formatting: Spotless with google-java-format; run `./mvnw spotless:apply` before committing
  Java code.
- Test fixtures live in `*/src/test/resources/fixtures/`. They must be synthetic or official
  example data, never real patient data.
- Performance figures in the README are measured on the reference hardware (Docker, 4 vCPUs,
  8 GB RAM, default heap), not on a workstation.

## Rules that are easy to miss

- No network calls, no telemetry, no patient data in reports, logs or exception messages.
- Codes only from the pinned specifications; never invent codes.
- Output must be deterministic (byte-identical for the same input and options): no timestamps,
  random ids or unstable ordering unless injected through the options (constitution V).
- `convert` never throws for invalid input; problems go into the conversion report with a
  reason code.
- Documentation and code comments are in English.
