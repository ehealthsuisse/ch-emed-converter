# Changelog

All notable changes to the converter are recorded here, following
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
[Semantic Versioning](https://semver.org/). Changes to the output structure, to reason codes or to
the report format MUST be recorded here (constitution, "Development Workflow").

## [Unreleased] — 1.0.0

### Changed

- The CLI is a thin JAR (`converter-cli/target/ch-emed-converter.jar`) with its dependencies in
  `converter-cli/target/lib/` instead of a shaded fat JAR; ship both together. The REST service
  uses the same layout (`converter-rest/target/ch-emed-converter-rest.jar` + `lib/`) instead of a
  Spring Boot fat JAR.
- Feature 003 changes the behaviour of existing inputs: signed eRezept URLs are converted (before:
  `PARSE_ERROR`, rule M-IN-03); FHIR JSON declared as `chmed16a` fails with `PARSE_ERROR` "use
  source ch-emed" instead of schema errors; GIF, BMP, TIFF and WebP input fails with
  `IMAGE_UNREADABLE`; a QR payload, eRezept URL or image declared as `ch-emed` fails with a
  `PARSE_ERROR` naming the detected form. The unsupported-conversion message lists the supported
  pairs.
- The build produces a distribution archive `converter-cli/target/ch-emed-converter-<version>-dist.zip`
  (JAR, `lib/`, start scripts for Linux/macOS and Windows, `LICENSE`, `NOTICE`, `THIRD-PARTY.txt`,
  README, third-party licence overview). The REST service has its own archive
  `converter-rest/target/ch-emed-converter-rest-<version>-dist.zip` (JAR, `lib/`, start scripts,
  operator documentation `rest-service.md`, licences).

### Added

- Conversion CHMED16A ↔ CH EMED 6.0.0 for **medication plans** (CHMED16A `MedType` 1 ↔ Medication
  Card document) and **prescriptions** (`MedType` 3 ↔ Medication Prescription document), via
  library (`converter-core`, `format-chmed16a`, `converter-validation`) and CLI (`converter-cli`).
- Accepted CHMED16A input forms: plain JSON and the textual QR payload (`CHMED16A1…`, Base64
  GZip); QR images and signed eRezept URLs since feature 003 (below).
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

- **REST service** `converter-rest` (feature 002, Spring Boot 4.1): `POST
  /api/v1/conversions/{from}/{to}` with a JSON message carrying the input document base64-encoded
  and the same options as the CLI (`strict`, `language`, `confidentiality`, `patientIdentifier`,
  `author`); the output document is returned base64-encoded and byte-identical to library and CLI.
  RFC 9457 problem details with ten distinct `errorType`s; `GET /api/v1/capabilities`;
  `GET /api/v1/openapi.yaml` (contract-first); liveness/readiness probes; configurable size, time
  and concurrency limits; console logging, optional log file with `logging.file.name`. No
  authentication, no TLS, no persistence — operator documentation in
  `docs/operations/rest-service.md`.
- Constitution 1.4.1: the REST service returns the conversion report with every response for which
  a conversion was attempted.
- Reason code `VALIDATION_INCOMPLETE` (ERROR): the HL7 validator aborts pattern checks after a fixed
  500 ms of wall-clock time, so under CPU load valid documents failed validation depending on
  timing. The validator now repeats such a run twice (after 0.2 s and 1 s); if every run is
  affected, the report contains this single entry instead of the unreliable messages. The CLI
  exits `3` and the REST service answers `503 SERVICE_BUSY` (with report and `Retry-After`)
  instead of declaring the document invalid.

- Licence: Apache License 2.0 (`LICENSE`, `<licenses>` in the parent POM).

- **QR code and eRezept Schweiz** (feature 003):
  - new module `converter-qr` (ZXing 3.5.4) reading one QR code from PNG/JPEG in memory and
    writing deterministic PNGs (error correction M, 4 × 4 cm via `pHYs`; parameters provisional,
    see `docs/specifications.md`);
  - source `chmed16a` detects its input form by content: JSON, QR payload, QR image, signed eRezept
    URL `https://eprescription.hin.ch/#CHMED16A1…&i=&t=&s=`;
  - library: option `ConversionOptions.outputForm` (`DOCUMENT`, `QR_PAYLOAD`, `QR_IMAGE`), result
    field `ConversionResult.signature` (raw, never verified), `Converter.formats()`, SPI defaults
    `FormatModule.inputForms()`/`outputForms()`; existing constructors kept;
  - CLI: `--output-form`, `--signature-output` (default `<output>.signature.json`);
  - REST (OpenAPI 1.1.0): `options.outputForm`, media types `text/plain` and `image/png`, response
    field `signature`, capabilities `formats` and `limits.maxImagePixels`;
  - prescriptions are output as unsigned payload only (input of the HIN erxproxy); a QR image of
    a prescription is refused;
  - reason codes `IMAGE_UNREADABLE`, `IMAGE_TOO_LARGE`, `QR_NOT_FOUND`, `QR_MULTIPLE`,
    `EREZEPT_URL_INVALID`, `SIGNATURE_NOT_VERIFIED`, `SIGNATURE_FORM_UNRECOGNISED`,
    `NOT_COVERED_BY_SIGNATURE`, `QR_CAPACITY_EXCEEDED`, `QR_IMAGE_NOT_ALLOWED`,
    `UNSUPPORTED_OUTPUT_FORM`; mapping rules `M-IN-07`, `M-QR-*`, `M-URL-*`.

### Pinned specifications

See [docs/specifications.md](docs/specifications.md): CH EMED 6.0.0 (CH Core 6.0.0), FHIR R4
4.0.1, eMediplan CHMED16A commit `0b579aa`, eRezept Schweiz documentation 0.10.2.

### CLI exit codes

`0` success or success with warnings, `1` failed (invalid input, unsupported type, strict-mode
loss, output validation failure), `2` usage error, `3` internal error.

### Known limitations

- Performance: SC-005 is split into SC-005a (conversion of 100 medicaments < 2 s, measured 0.02–0.1 s) and SC-005b (official validation < 30 s with a warm context, raised from 15 s on 2026-11-01; measured 3 s on a Linux server with 4 vCPUs, up to 19 s on a Windows workstation) and SC-005c (added: 20 different medications converted and validated < 5 s); the cold start of about 30–40 s is reported separately.
- The mapping specification still needs the review of a person with CH EMED / eMediplan domain
  knowledge (task T118); open interpretation points are marked "domain review".
