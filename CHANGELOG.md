# Changelog

All notable changes to the converter are recorded here, following
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
[Semantic Versioning](https://semver.org/). Changes to the output structure, to reason codes or to
the report format MUST be recorded here (constitution, "Development Workflow").

## [Unreleased] — 1.0.0

### Changed

- The CLI is a thin JAR (`converter-cli/target/ch-emed-converter.jar`) with its dependencies in
  `converter-cli/target/lib/` instead of a shaded fat JAR; ship both together.
- The build produces a distribution archive `converter-cli/target/ch-emed-converter-<version>-dist.zip`
  (JAR, `lib/`, start scripts for Linux/macOS and Windows, `LICENSE`, `NOTICE`, `THIRD-PARTY.txt`,
  README, third-party licence overview).

### Added

- Conversion CHMED16A ↔ CH EMED 6.0.0 for **medication plans** (CHMED16A `MedType` 1 ↔ Medication
  Card document) and **prescriptions** (`MedType` 3 ↔ Medication Prescription document), via
  library (`converter-core`, `format-chmed16a`, `converter-validation`) and CLI (`converter-cli`).
- Accepted CHMED16A input forms: plain JSON and the textual QR payload (`CHMED16A1…`, Base64
  GZip). Signed eRezept URLs and QR images are out of scope (QR/eRezept feature).
- Output of CH EMED documents validated offline with the official HL7 validator wrapper (HAPI FHIR)
  against the vendored IG packages; cross-check with `tools/validate-outputs.sh`.
- Conversion report (schema version 1.0.0) with severities, reason codes, paths, clinical-loss
  flag; strict mode (`--strict`) fails on clinical loss.
- eRezept Schweiz readiness rules for produced prescriptions (`EREZEPT_REQUIREMENT`), derived from
  the documentation 0.10.2 and its JSON schema v7.
- Deterministic output (UUID v5 ids, no wall clock), round-trip evidence against
  `capability-gaps.json`, privacy scan of reports and logs.
- Single mapping specification `docs/mapping/chmed16a-ch-emed.md` (rule ids `M-…`), checked
  against the code by `MappingTraceabilityTest`.

- Licence: Apache License 2.0 (`LICENSE`, `<licenses>` in the parent POM).

### Pinned specifications

See [docs/specifications.md](docs/specifications.md): CH EMED 6.0.0 (CH Core 6.0.0), FHIR R4
4.0.1, eMediplan CHMED16A commit `0b579aa`, eRezept Schweiz documentation 0.10.2.

### CLI exit codes

`0` success or success with warnings, `1` failed (invalid input, unsupported type, strict-mode
loss, output validation failure), `2` usage error, `3` internal error.

### Known limitations

- Performance: SC-005 is split into SC-005a (conversion of 100 medicaments < 2 s, measured 0.02–0.1 s) and SC-005b (official validation < 15 s with a warm context, measured about 10 s, linear at ~80 ms per medication entry); the cold start of about 30–40 s is reported separately.
- The mapping specification still needs the review of a person with CH EMED / eMediplan domain
  knowledge (task T118); open interpretation points are marked "domain review".
