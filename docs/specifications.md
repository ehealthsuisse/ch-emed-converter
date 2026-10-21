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
| Files | `chmed16a/README.md` (object model, ChTransmissionFormat), `chmed16a/prescription.md` |
| Format identifier | `CHMED16A1` (QR payload prefix incl. compression flag) |

## eRezept Schweiz

| Artefact | Pinned version |
|----------|----------------|
| Developer documentation | 0.10.2 (<https://developers.e-rezept.ch/erezept/>) |
| Data requirements | `rezdat.html` (prescription data), `precon.html` (conformity) |

## Libraries

| Library | Version |
|---------|---------|
| HAPI FHIR | 8.12.1 |
| Jackson | 2.21.6 |
| picocli | 4.7.7 |
| Apache PDFBox | 3.0.8 |

All library versions are managed in the parent `pom.xml`.
