# Data Model: eMediplan QR Code and eRezept Schweiz

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

Extends the data model of feature 001 (library) and feature 002 (REST). Only new or changed
elements are listed.

## 1. Input forms (source `chmed16a`, detected by content, FR-013)

Detection happens in this order, on the input bytes:

| Form | Detection | Processing |
|------|-----------|------------|
| `IMAGE` | starts with `89 50 4E 47 0D 0A 1A 0A` (PNG) or `FF D8 FF` (JPEG) | `QrImageReader` → content bytes (raw byte segments) decoded as UTF-8. The text is then detected again as `PAYLOAD` or `EREZEPT_URL`; anything else, or invalid UTF-8, is `PARSE_ERROR` |
| `UNSUPPORTED_IMAGE` | starts with `GIF8` (GIF), `BM` (BMP), `49 49 2A 00` / `4D 4D 00 2A` (TIFF) or `RIFF` + `WEBP` at offset 8 (WebP) | `IMAGE_UNREADABLE` "unsupported image format"; no decoding attempted |
| `EREZEPT_URL` | text (UTF-8, trimmed) starts with `https://eprescription.hin.ch/` (scheme and host case-insensitive) | `ErezeptUrlParser` → payload + `SignatureData` |
| `PAYLOAD` | text starts with `CHMED` (case-insensitive) | `Chmed16aPayloadDecoder` (unchanged, M-IN-02) |
| `FHIR_JSON` | a JSON object whose top-level `resourceType` is present (checked by the JSON reader before the CHMED16A schema) | `PARSE_ERROR` "Input is a FHIR resource; use source ch-emed" (FR-013) |
| `JSON` | everything else | `Chmed16aJsonReader` (unchanged) |

For source `ch-emed`, an input detected as `IMAGE`, `EREZEPT_URL` or `PAYLOAD` fails with
`PARSE_ERROR` and the message "Input is a CHMED16A QR image | eRezept URL | QR payload; use source
chmed16a" (FR-013). The input is never echoed.

**Limits**:
- `maxDecodedSize` (existing, 5 MB) limits the image bytes, the decompressed payload and the URL
  length.
- Images are limited to 40 megapixels, read from the header before decoding.
- Both the byte limit and the pixel limit fail with `IMAGE_TOO_LARGE`, and the message names the
  limit that was hit. FR-018 is met by the configurable byte limit (REST `max-document-size`);
  the pixel limit is a fixed protection.

## 2. Output forms (`OutputForm`, option `outputForm`)

| Value (library / CLI and REST) | Target `chmed16a`, Medication Card | Target `chmed16a`, Medication Prescription | Target `ch-emed` |
|---|---|---|---|
| `DOCUMENT` / `document` (default) | CHMED16A JSON (unchanged) | CHMED16A JSON (unchanged) | FHIR JSON (unchanged) |
| `QR_PAYLOAD` / `qr-payload` | `CHMED16A1…` (ASCII) | `CHMED16A1…`, unsigned, eRezept rules checked (FR-009) | not allowed |
| `QR_IMAGE` / `qr-image` | PNG | refused: `QR_IMAGE_NOT_ALLOWED` | not allowed |

"Not allowed" means:
- library: ERROR `UNSUPPORTED_OUTPUT_FORM` (phase PARSE);
- CLI: usage error, exit 2;
- REST: 400 `MALFORMED_REQUEST` before the conversion.

Payload: `"CHMED16A1" + Base64(GZip(jsonBytes))`, with the details in research R2. The output
bytes are ASCII without a trailing line feed.

**QR image** (research R3):

| Property | Value |
|----------|-------|
| Symbology | QR Code (ISO/IEC 18004), byte mode, no ECI |
| Error correction | M (provisional, see research R1) |
| Version | smallest that fits, 1–40 |
| Quiet zone | 4 modules |
| Module size | 8 px |
| PNG | 1-bit grayscale, no text chunks, `pHYs` such that the symbol without quiet zone is 40 mm wide |
| Capacity | 2331 bytes (version 40-M); larger payloads → `QR_CAPACITY_EXCEEDED` |

## 3. Signature data (`SignatureData`, FR-004)

| Field | Type | Content |
|-------|------|---------|
| `identity` | string | value of `i`, raw, as in the URL (still URL-encoded) |
| `timestamp` | string | value of `t`, raw |
| `signature` | string | value of `s`, raw |
| `verified` | boolean | always `false` |

**Where it appears**:
- library: `ConversionResult.signature()`, present only if the input was a signed eRezept
  (`EREZEPT_URL`, or an `IMAGE` containing one) and the outcome is not FAILED;
- REST: response field `signature`;
- CLI: JSON file (see §6).

It never appears in the CH EMED document, the report or logs.

**Validation**: `i`, `t` and `s` must each be present exactly once and be non-empty
(`EREZEPT_URL_INVALID` otherwise). The form is not judged: if `t` is not all digits or `s` is not
hex, the conversion adds INFORMATION `SIGNATURE_FORM_UNRECOGNISED` (field name only).

## 4. New reason codes

| Code | Severity | Phase | Meaning |
|------|----------|-------|---------|
| `IMAGE_UNREADABLE` | ERROR | PARSE | unsupported image format (GIF, BMP, TIFF, WebP), or a PNG/JPEG that cannot be decoded |
| `IMAGE_TOO_LARGE` | ERROR | PARSE | image above the byte limit (`maxDecodedSize`) or the pixel limit (40 MP); the message names which |
| `QR_NOT_FOUND` | ERROR | PARSE | no readable QR code in the image |
| `QR_MULTIPLE` | ERROR | PARSE | more than one QR code in the image |
| `EREZEPT_URL_INVALID` | ERROR | PARSE | foreign host, no payload, or `i`/`t`/`s` missing or duplicate (names the parameter) |
| `SIGNATURE_NOT_VERIFIED` | INFORMATION | PARSE | always for a signed eRezept: signature and prescription status not checked (FR-005) |
| `SIGNATURE_FORM_UNRECOGNISED` | INFORMATION | PARSE | `t` or `s` does not have the expected form; passed on unchanged |
| `NOT_COVERED_BY_SIGNATURE` | INFORMATION | MAP | an option supplied data for a signed eRezept (path of the supplemented element; research R7) |
| `QR_CAPACITY_EXCEEDED` | ERROR | MAP | payload larger than one QR code holds (states the limit in bytes, FR-008) |
| `QR_IMAGE_NOT_ALLOWED` | ERROR | MAP | QR image requested for a prescription |
| `UNSUPPORTED_OUTPUT_FORM` | ERROR | PARSE | output form not offered for the target format |

**Reused codes**:
- `PARSE_ERROR`: QR content that is neither payload nor eRezept URL, or not valid UTF-8; wrong
  source for the detected form, in both directions (FR-013).
- `UNSUPPORTED_CONVERSION`: `ch-emed → chmed23a`, the version choice of FR-010; the message lists
  the supported pairs.
- `UNSUPPORTED_VERSION`: CHMED23A payload in an image or URL.
- `UNSUPPORTED_DOCUMENT_TYPE`: an eRezept URL that holds a medication plan.
- `UNKNOWN_FIELD_IGNORED`: unknown URL parameter, by name.
- `MISSING_REQUIRED_DATA` (FR-019).

None of the messages contains payload, URL, signature values or patient data.

## 5. Library API changes (summary; details in [contracts/library-api.md](./contracts/library-api.md))

- **`OutputForm`**: enum `DOCUMENT`, `QR_PAYLOAD`, `QR_IMAGE`.
- **`ConversionOptions`**: new component `outputForm` (default `DOCUMENT`) and `withOutputForm`.
  The existing canonical constructor stays as an overload.
- **`ConversionResult`**: new component `signature` (`Optional<SignatureData>`). The existing
  3-argument constructor stays and sets it empty. The invariant: `signature` is present only if
  `output` is present.
- **`Converter.formats()`**: returns `List<FormatInfo>`, with
  `FormatInfo(FormatId format, List<String> inputForms, List<OutputForm> outputForms)`.
  - `chmed16a`: inputs `json`, `qr-payload`, `erezept-url`, `qr-image`; outputs `DOCUMENT`,
    `QR_PAYLOAD`, `QR_IMAGE`.
  - `ch-emed`: inputs `json`; outputs `DOCUMENT`.
- **`FormatModule`**: two default methods, `inputForms()` (`["json"]`) and `outputForms()`
  (`[DOCUMENT]`).
- **`MappingContext`**: `setSignature(SignatureData)`; read by `DefaultConverter`.

## 6. CLI (details in [contracts/cli.md](./contracts/cli.md))

| Option | Meaning | Default |
|--------|---------|---------|
| `--output-form document\|qr-payload\|qr-image` | output form (target `chmed16a` only) | `document` |
| `--signature-output <file>` | file for the signature data (JSON as in §3) | `<output>.signature.json` if `-o` is given; otherwise not written, with a note on stderr |

The signature file is written only on success, with the same ordering and encoding as the report
(UTF-8, pretty-printed, keys in the order of §3). The input may be binary (an image); the output on
stdout may be binary (PNG).

## 7. REST (details in [contracts/rest-api.md](./contracts/rest-api.md))

- **`RequestOptions`**: `outputForm` (`document`, `qr-payload`, `qr-image`; default `document`).
- **`ConversionResponse`**:
  - `signature`: object as in §3, only for signed input.
  - `outputMediaType`: `application/json` (CHMED16A JSON), `application/fhir+json`, `text/plain`
    (payload) or `image/png`.
- **`CapabilitiesResponse`**: `formats`, a list of `{name, inputForms, outputForms}`.
  - `options` lists `outputForm`.
  - `limits` adds `maxImagePixels`.
- **ErrorType mapping** (`FailureClassifier`):
  - `IMAGE_*`, `QR_NOT_FOUND`, `QR_MULTIPLE`, `EREZEPT_URL_INVALID` → 422 `INVALID_INPUT`;
  - `QR_CAPACITY_EXCEEDED`, `QR_IMAGE_NOT_ALLOWED` → 422 `CONVERSION_FAILED`;
  - `UNSUPPORTED_OUTPUT_FORM` cannot reach the classifier, since the controller refuses it
    beforehand (400).

## 8. State and lifecycle

Stateless, as in features 001/002. An image, decoded text, URL and signature data live only for
one request. No temp files: ImageIO uses `MemoryCacheImageInputStream`, and `ImageIO.setUseCache`
is not relied on globally.
