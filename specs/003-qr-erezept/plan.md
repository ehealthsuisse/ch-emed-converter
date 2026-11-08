# Implementation Plan: eMediplan QR Code and eRezept Schweiz

**Branch**: `003-qr-erezept` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-qr-erezept/spec.md`

## Summary

CHMED16A gains three new transmission forms: the QR image, the textual payload as an output, and
the signed eRezept URL. They are forms of the existing format, not new formats, so the CH EMED hub,
the conversion pairs and the REST resources stay as they are (research R8).

**Reading**: Source `chmed16a` detects every input form by its content:
- JSON;
- `CHMED16A1…` payload;
- `https://eprescription.hin.ch/#…` URL;
- PNG/JPEG image.

A new module `converter-qr` (ZXing 3.5.3, Apache-2.0, research R4/R11) turns an image into text.
For a signed eRezept, the signature data (`i`, `t`, `s`) is returned raw and unverified beside the
document, as `ConversionResult.signature` (R6/R8).

**Writing**: The new option `outputForm` (`document`, `qr-payload`, `qr-image`) selects:
- the `CHMED16A1` payload: GZip + Base64 of the JSON writer's bytes (R2);
- for medication plans only, a PNG with error correction M, a 4-module quiet zone and a `pHYs`
  resolution for a 4 × 4 cm symbol (R3).

A prescription with `qr-image` and a payload beyond QR capacity are refused with specific errors.

**Interfaces**: Library, CLI and REST expose the same options, results and reports (R9).

## Technical Context

**Language/Version**: Java 21 LTS

**Primary Dependencies**:
- Existing modules: `converter-core`, `format-chmed16a`, `converter-validation`, `converter-cli`,
  `converter-rest` (features 001/002).
- New: `com.google.zxing:core` 3.5.3 (Apache-2.0) in the new module `converter-qr`.
- JDK `javax.imageio` for PNG/JPEG.
- Tests only: BoofCV `boofcv-recognition` (Apache-2.0) as the independent QR reader.

**Storage**: N/A. ImageIO disk cache off, no temp files (FR-017, R5).

**Testing**:
- JUnit 5 and AssertJ, as in features 001/002.
- Fixture tests over the corpus × input form.
- Image fixtures: generated, independent (`qrencode`), degraded variants with fixed seeds.
- Synthetic signed eRezept URLs.
- Extended parity, log-scan and no-network tests (R10).

**Target Platform**: JVM 21 (Linux, Windows). CLI thin JAR with `lib/`; REST thin JAR and OCI
image. Images need `java.desktop`, which the Paketo JRE contains. AWT runs headless (no display,
no fonts needed).

**Project Type**: Library with CLI and web service (Maven multi-module); one new module.

**Performance Goals**:
- SC-006: reading a QR image adds < 1 s to the conversion of the same plan from JSON (reference
  hardware: 4 vCPU).
- SC-005: a realistic 20-medication plan fits into one QR code at level M.
- The existing limits of feature 002 (20 medications < 5 s) apply unchanged.

**Constraints**:
- Byte-identical payloads and images for identical input (FR-011).
- No network; no logging of payloads, URLs, signature data or PHI (FR-016).
- Image size limited by bytes (`maxDecodedSize`, REST `max-document-size`) and by pixels (40 MP).
- No splitting of payloads (FR-008).
- Existing operations unchanged (FR-014).

**Scale/Scope**:
- 3 new output forms of `ch-emed → chmed16a` and 3 new input forms of `chmed16a → ch-emed`.
- About 9 new reason codes.
- 1 new library result field, 1 new option, 2 CLI options, 2 REST fields, 1 capabilities field.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Gate (constitution v1.4.1) | Status | Evidence |
|---|----------------------------|--------|----------|
| I | QR payload uses the prefix, compression and encoding of the CHMED spec; images meet its symbol requirements; eRezept payloads signable unchanged; versions pinned | PASS | R2: prefix, GZip and Base64 as in ChTransmissionFormat. R3: 4 × 4 cm via `pHYs`, white margin; the unstated parameters (ECC M, quiet zone) are pinned as plan decisions, marked provisional pending domain confirmation. R6: unsigned payload is the erxproxy input form. R12: pinning in `docs/specifications.md` |
| II | No silent loss; report entries | PASS | Payload = JSON bytes, so the QR form adds no loss (FR-012). Capacity overflow fails instead of truncating (FR-008). Options for signed input are reported as `NOT_COVERED_BY_SIGNATURE` (R7). Unknown URL parameters are reported by name |
| III | Test-first; payload- and image-level QR tests; synthetic signed eRezept URLs; round trips | PASS | R10: corpus × form, image fixtures (generated, independent, degraded), independent reader BoofCV, synthetic `i`/`t`/`s`, round trip CH EMED → payload/image → CH EMED |
| IV | Local only; no PHI or raw payloads in logs; no HIN contact; signature data never logged; no persistence | PASS | ZXing and ImageIO are offline. ImageIO disk cache off (R5). Signature data only in the result, never in report or logs (FR-004/016). No-network and log-scan tests extended (R10) |
| V | Hub model; deterministic; QR codec as a separate layer; core framework-free; simplest design | PASS | Forms of CHMED16A, not new formats (R8). MTIME 0, fixed module size and level (R2/R3). `converter-qr` is the QR layer (R11); `converter-core` gets only an enum, a record and SPI defaults |
| Scope | Every conversion via library, CLI and REST, identical behaviour | PASS | R9: `outputForm` and signature data in all three interfaces; parity test extended to the new forms (SC-008) |
| Tech | Java 21; HAPI; licences reviewed | PASS | ZXing and BoofCV are Apache-2.0 (BoofCV test only); `third-party-licenses.md` updated (R12) |

Post-design re-check (after Phase 1): **PASS**. One justified addition: see Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/003-qr-erezept/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── library-api.md   # changes to the library API of feature 001
│   ├── cli.md           # changes to the CLI contract of feature 001
│   └── rest-api.md      # changes to specs/002-rest-conversion-service/contracts/openapi.yaml (1.0.0 → 1.1.0)
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
pom.xml                                   # + module converter-qr, + ZXing and BoofCV versions
docs/
├── specifications.md                     # + ChTransmissionFormat file, QR parameters, eRezept URL, ZXing
├── third-party-licenses.md               # + ZXing (Apache-2.0)
├── architecture.md                       # eRezept/QR from "planned" to "implemented", module diagram
├── mapping/chmed16a-ch-emed.md           # M-IN-03 replaced; new sections QR (M-QR-*) and eRezept (M-ERX-*); reason codes
└── operations/rest-service.md            # image size, new error cases

converter-core/src/main/java/ch/emed/converter/core/
├── api/OutputForm.java                   # DOCUMENT, QR_PAYLOAD, QR_IMAGE
├── api/SignatureData.java                # identity, timestamp, signature (raw), verified() = false
├── api/ConversionOptions.java            # + outputForm (overloads kept)
├── api/ConversionResult.java             # + signature (overloads kept)
├── api/Converter.java                    # + formats(): input and output forms per format
├── api/DefaultConverter.java             # output-form check for target CH EMED; signature from context; form hint for CH EMED source
├── spi/FormatModule.java                 # + default inputForms(), outputForms()
├── spi/MappingContext.java               # + signature slot set by the format module
└── report/ReasonCode.java                # + new codes (data-model §4)

converter-qr/                             # NEW: QR image codec, no CHMED knowledge
├── pom.xml                               # zxing core; enforcer framework-free
└── src/main/java/ch/emed/converter/qr/
    ├── QrImageReader.java                # image bytes → content bytes (UTF-8); PNG/JPEG, size and pixel limits, one code only
    ├── QrImageWriter.java                # text → PNG (level M, quiet zone 4, 8 px/module, pHYs 4 cm)
    └── QrException.java                  # kind: UNREADABLE, TOO_LARGE, NOT_FOUND, MULTIPLE, CAPACITY

format-chmed16a/src/main/java/ch/emed/converter/chmed16a/
├── Chmed16aFormatModule.java             # form detection on input, output form on output
├── io/InputFormDetector.java             # JSON | PAYLOAD | EREZEPT_URL | IMAGE (magic bytes, prefixes)
├── io/Chmed16aPayloadEncoder.java        # JSON bytes → CHMED16A1 payload (R2)
└── io/ErezeptUrlParser.java              # URL → payload + SignatureData; parameter checks (R6)

converter-cli/src/main/java/ch/emed/converter/cli/ConvertCommand.java   # --output-form, --signature-output
converter-rest/src/main/java/ch/emed/converter/rest/                     # outputForm, signature, media types, capabilities.formats, FailureClassifier

tests (per module, src/test/...):
├── converter-qr:      codec tests, determinism, capacity, multiple codes, pixel limit, BoofCV cross-check
├── format-chmed16a:   form detection, payload encoder round trip, eRezept URL parser, corpus × form, PerformanceTest SC-005/006
│                      fixtures/qr/ (independent PNG/JPEG), fixtures/erezept/ (synthetic URLs)
├── converter-cli:     options, signature file, stdout binary output
└── converter-rest:    contract, parity (SC-008), privacy log scan (SC-007), no network (SC-009)
```

**Structure Decision**: One new module `converter-qr` between `converter-core` and
`format-chmed16a` (R11). `format-chmed16a` depends on it. `converter-cli` and `converter-rest` get
it transitively. All other changes stay in the existing modules. No module gains a framework
dependency.

## Complexity Tracking

| Addition | Why Needed | Simpler Alternative Rejected Because |
|----------|------------|--------------------------------------|
| New module `converter-qr` and dependency ZXing | QR images must be decoded and encoded (FR-001, FR-007). The constitution names QR encoding/decoding as a separate layer. CHMED23A will reuse the codec | A package in `format-chmed16a` would force CHMED23A to depend on CHMED16A or copy the code. An own QR implementation is far more code and risk than a reviewed Apache-2.0 library |
| Test dependency BoofCV | SC-002 requires an independent reader; decoding our own images with the same library proves little | Committed images only from external tools would not cover the images the converter produces |
