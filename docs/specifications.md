# Pinned specifications

Constitution principle I requires every format specification, IG package and validation artefact
to be pinned. Changing anything in this file is a deliberate, reviewed change (research R2 of
feature 001). The values are also exposed at runtime via
`converter-core/src/main/resources/specifications.properties`.

## FHIR / CH EMED

| Artefact | Pinned version | Notes |
|----------|----------------|-------|
| FHIR | R4 (4.0.1) | R4 core definitions from HAPI `DefaultProfileValidationSupport` |
| CH EMED IG | `ch.fhir.ig.ch-emed#6.0.0` (2025-12-16, trial-use) | root package |
| CH Core | `ch.fhir.ig.ch-core#6.0.0` | dependency of CH EMED |
| CH Term | `ch.fhir.ig.ch-term#3.3.0` | CH Core declares `3.3.x`; resolved once and vendored |
| HL7 Terminology | `hl7.terminology.r4#7.0.1` | dependency of CH EMED |
| FHIR Extensions Pack | `hl7.fhir.uv.extensions.r4#5.2.0` | dependency of CH EMED |
| IHE FormatCode | `ihe.formatcode.fhir#1.4.0` | dependency of CH Core |

The vendored `.tgz` files, their SHA-256 checksums and which version of each package is loaded
into the validator are listed in
`converter-validation/src/main/resources/packages/manifest.json` (written by
`tools/fetch-packages.sh`). Older versions referenced deeper in the dependency tree
(`hl7.terminology.r4#6.5.0`, `#6.3.0`, `hl7.fhir.uv.extensions.r4#5.3.0-ballot-tc1`) are vendored
but not loaded, because HAPI keeps one definition per canonical URL; the version declared closest
to CH EMED wins.

## eMediplan CHMED16A

| Artefact | Pinned version |
|----------|----------------|
| Repository | <https://github.com/ig-emediplan/specification> |
| Commit | `0b579aa6f3aff10a017498db57048c6504091653` (2026-05-22) |
| Files | `chmed16a/README.md` (object model, layout incl. 2D barcode), `chmed16a/prescription.md`, `chtransmissionformat/README.md` (QR payload format) |
| Format identifier | `CHMED16A1` (QR payload prefix incl. compression flag) |

### QR symbol (feature 003)

The specification requires a printed size of 4 × 4 cm and a blank margin of about 0.3 cm
(`chmed16a/README.md`, "Header/2D barcode"). It does not state the parameters below; they are
decisions of feature 003 (research R1–R3), **provisional until confirmed by IG eMediplan or a domain
reviewer**.

| Parameter | Pinned value |
|-----------|--------------|
| Payload | `CHMED16A1` + Base64 (RFC 4648 §4, with padding) of GZip (MTIME 0) of the UTF-8 JSON |
| Symbology | QR Code (ISO/IEC 18004), byte mode, no ECI |
| Error correction | M |
| Version | smallest that fits (1–40); capacity 2331 bytes (40-M) |
| Quiet zone | 4 modules |
| Image | 1-bit PNG, 8 px per module, `pHYs` so that the symbol without quiet zone prints 40 mm wide |
| Reading | PNG and JPEG, at most 40 megapixels, exactly one code, content read as UTF-8 |

## eRezept Schweiz

| Artefact | Pinned version |
|----------|----------------|
| Developer documentation | 0.10.2 (<https://developers.e-rezept.ch/erezept/>) |
| Data requirements | `rezdat.html` (prescription data), `precon.html` (conformity) |
| Signed URL (feature 003) | `https://eprescription.hin.ch/#CHMED16A1…&i=<identity>&t=<timestamp>&s=<signature>`, parameters in the fragment (`rezbsp.html`, `vernoa.html`); `i`, `t`, `s` returned raw and unverified. Open: host of the erxproxy integration environment |
| Signing input (feature 003) | the unsigned `CHMED16A1…` payload, `POST /ePrescription/create` of the local erxproxy (`rezbsp.html`); never called by the converter |

### Open items (feature 003)

- QR error-correction level M and the other symbol parameters above: to be confirmed by IG
  eMediplan or a domain reviewer (research R1).
- Host of the erxproxy integration environment (`--env int`): not documented; check with a test URL
  whether a second host must be accepted (research R6).
- Signability of the unsigned payload "without modification": shown by the eRezept schema v7, the
  eRezept rules and the payload form named as erxproxy input; a real signing run needs an HIN
  test account (quickstart Scenario 4).

## Libraries

| Library | Version |
|---------|---------|
| HAPI FHIR | 8.12.1 |
| Jackson | 2.21.6 |
| picocli | 4.7.7 |
| Apache PDFBox | 3.0.8 |
| ZXing core | 3.5.4 |
| BoofCV (tests only) | 1.6.0 |

All library versions are managed in the parent `pom.xml`.
