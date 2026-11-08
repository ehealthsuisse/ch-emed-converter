# Research: eMediplan QR Code and eRezept Schweiz

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-08

Builds on feature 001 ([research](../001-chmed16a-ch-emed-conversion/research.md)) and feature 002
([research](../002-rest-conversion-service/research.md)). Each entry: Decision / Rationale /
Alternatives considered. Sources read on 2026-11-08: eMediplan specification at the pinned commit
`0b579aa6` (`chmed16a/README.md`, `chmed16a/prescription.md`, `chtransmissionformat/README.md`,
`LICENSE`) and the eRezept Schweiz developer documentation 0.10.2 (`rezdat`, `precon`, `rezbsp`,
`vernoa`, `dirint`, `visdoc`, `downloads`, `history`, `impressum`).

## R1 — What the specifications say about the QR symbol

- **Findings**:
  - `chtransmissionformat/README.md` defines the content of "the QR code on a printed medication
    plan". `chmed16a/README.md`, section "Header/2D barcode", requires that the barcode "must be
    displayed as a rectangle of 4 x 4 cm", with "a blank space of about 0.3 cm" around it. The
    same section also says "approximately 4.5 cm" wide, which contradicts the 4 x 4 cm.
  - Neither document states the QR version, error-correction level, encoding mode, module size,
    quiet zone or maximum payload. ReleaseYear 16 has no splitting into several codes; chunking
    exists only in the deprecated ReleaseYear 23.
  - The eRezept documentation states no QR requirements. The QR image of a *signed* eRezept is
    produced by the HIN erxproxy itself (`output-format=qrcode`, a base64-encoded PNG).
- **Decision**: The converter meets the stated requirements (4 x 4 cm symbol, white margin). For
  the unstated parameters, it pins its own choices (R3) and documents them as decisions of this
  plan, not of the specification.
- **Open item**: Confirm the error-correction level with IG eMediplan or a domain reviewer before
  release, together with the open domain review of feature 001 (T118). Until then the choice is
  marked provisional in `docs/specifications.md`.

## R2 — ChTransmissionFormat details for writing (FR-006)

- **Decision**: Payload = `CHMED16A1` + Base64 (RFC 4648 §4, standard alphabet, with padding) of
  the GZip (RFC 1952) compression of exactly the UTF-8 bytes that the JSON output form produces
  for the same document (`Chmed16aJsonWriter`). GZip uses `java.util.zip.GZIPOutputStream`: header
  MTIME 0, no file name, default compression level. The prefix is upper case. Plain JSON
  (compression flag 0) is never written.
- **Rationale**:
  - The specification requires UTF-8 and upper case when serializing. It says serialization
    "SHOULD NOT be done without compression".
  - Its examples use `gzip -n` and Unix `base64` (standard alphabet with `=` padding).
  - Reusing the JSON writer's bytes makes FR-012 (payload read back = same CHMED16A document)
    hold by construction.
  - MTIME 0 and a fixed level keep payloads byte-identical (FR-011). The bytes depend on the
    JDK's bundled zlib; the parity and determinism tests run on one JDK, so this does not matter
    for them.
- **Alternatives**:
  - Re-serialize the JSON minified with escaped `/`, as in the specification's example. Rejected:
    no rule requires it, and it would be a second serialization of the same document.
  - URL-safe Base64. Rejected: the examples and the eRezept tooling use the standard alphabet.

## R3 — QR encoding parameters (FR-007, FR-008, SC-005)

- **Decision**:
  - Byte mode, ISO-8859-1 transfer of the ASCII payload (the payload is pure ASCII, so no ECI is
    needed).
  - Error-correction level **M**.
  - Smallest QR version (1–40) that fits; mask chosen by the encoder.
  - Quiet zone of 4 modules (ISO/IEC 18004).
  - Image: 1-bit grayscale PNG with an integer module size of 8 pixels. The PNG `pHYs` chunk sets
    a resolution at which the symbol (without quiet zone) prints at 40 mm × 40 mm.
  - The quiet zone of 4 modules provides the white margin. The 0.3 cm margin of the specification
    is then a layout matter for the printing system.
  - Payloads above the byte-mode capacity of version 40-M (2331 bytes) fail with
    `QR_CAPACITY_EXCEEDED`, stating the limit (FR-008).
- **Rationale**:
  - Level M (about 15 % recovery) is the usual compromise for printed documents. L would leave
    poor scans little reserve.
  - Q/H would lower the capacity to 1663/1273 bytes. A realistic 20-medication plan compresses to
    roughly 1.5–2.2 KB of Base64, so with Q/H, SC-005 would be at risk. SC-005 is measured with
    the `LargePlanGenerator` of `format-chmed16a` (as in feature 002) and fixes the real figure.
  - The `pHYs` resolution makes "4 x 4 cm" a property of the file instead of a printing
    convention.
  - An integer module size keeps the image sharp and deterministic.
- **Risk**: At 4 cm, version 40 gives about 0.23 mm per module. That is readable at 300 dpi, but
  without much reserve. The measured payload size for 20 medications goes into the plan's
  performance record (SC-005).
- **Alternatives**:
  - A caller-selectable error-correction level. Rejected for now (YAGNI, not in the spec). It can
    be added later as an option without changing existing behaviour.
  - Level L. Rejected: less robust for scans.

## R4 — QR library

- **Decision**: ZXing `com.google.zxing:core` 3.5.3 (Apache-2.0), the latest release at planning
  time; the version is re-checked when the dependency is added. It is used for encoding
  (`QRCodeWriter`) and decoding (`QRCodeReader`, `QRCodeMultiReader` to detect several codes).
  `RGBLuminanceSource` comes from `core` itself, so the `javase` module (with its additional
  dependencies) is not needed. Images are read and written with `javax.imageio` (JDK,
  `java.desktop`).
- **Rationale**:
  - Pure Java, no native code, no network.
  - The most widely used open-source QR library on the JVM.
  - One library for both directions.
  - Its licence is already on the project's allow list.
- **Alternatives**:
  - Nayuki QR Code generator (MIT). Rejected: encoding only, a second library would be needed for
    decoding.
  - BoofCV. Rejected for production: large, its strength is computer vision. It is used in tests
    as the independent reader (R10).
  - Writing an own encoder. Rejected (simplicity, constitution V).

## R5 — Reading images (FR-001, FR-018, edge cases)

- **Decision**:
  - **Formats:** PNG and JPEG are recognised by their magic bytes (`89 50 4E 47`, `FF D8 FF`).
    Other common image formats are recognised by their magic bytes too and fail with
    `IMAGE_UNREADABLE` "unsupported image format": GIF `GIF8`, BMP `BM`, TIFF `II*\0` / `MM\0*`,
    WebP `RIFF….WEBP`.
  - **No temp files:** ImageIO reads through a `MemoryCacheImageInputStream`; the disk cache
    stays off (FR-017).
  - **Pixel limit:** Before the pixels are decoded, the image dimensions are read from the header
    and limited to 40 megapixels. This protects against decompression bombs. The byte size is
    limited by `maxDecodedSize` in the library and by the REST service's configurable
    `max-document-size` (FR-018). Both limits fail with `IMAGE_TOO_LARGE`, and the message says
    which limit was hit. The pixel limit is a fixed protection, not an operator setting; FR-018
    is met by the configurable byte limit.
  - **Decoding:** ZXing with `TRY_HARDER`, `PURE_BARCODE` off, and a binarizer fallback
    (`HybridBinarizer`, then `GlobalHistogramBinarizer`).
  - **Content bytes:** The raw content bytes are recovered and decoded as UTF-8, as
    ChTransmissionFormat requires; ZXing's charset guess is not used. Without an ECI, ZXing decodes
    with the hint `CHARACTER_SET=ISO-8859-1`, which maps each byte to one char, so the chars give
    back the bytes of all segments. `BYTE_SEGMENTS` alone is not enough, because it misses
    numeric and alphanumeric segments of mixed-mode codes (revised during T025). With an ECI
    (symbology identifier `]Q2`), the declared charset is used. A plain-JSON payload
    (`CHMED16A0`) with umlauts therefore arrives unchanged. Content that is not valid UTF-8 fails
    with `PARSE_ERROR`.
  - **Several codes:** More than one QR code found fails with `QR_MULTIPLE`. Content that is
    neither a CHMED payload nor an eRezept URL fails with `PARSE_ERROR`; the content is never
    echoed.
- **Rationale**:
  - Magic bytes make detection independent of file names and media types.
  - The cache flag closes the only path through which the JDK would write image data to disk.
  - Phone photos are best effort (spec assumption); the binarizer fallback covers uneven
    lighting cheaply.
- **Alternatives**:
  - Accepting more formats (GIF, TIFF, HEIC). Rejected: not needed by primary systems, which scan
    or export PNG/JPEG.
  - Pre-processing (deskewing, sharpening). Rejected (YAGNI).

## R6 — eRezept URL (FR-003, FR-004, FR-005)

- **Findings** (developer documentation 0.10.2, `rezbsp`, `vernoa`, `rezdat`):
  - Shape: `https://eprescription.hin.ch/#CHMED16A1<base64 gzip>&i=<identity>&t=<timestamp>&s=<signature>`.
    The parameters are in the fragment, not in the query.
  - `i` is a URL-encoded display string. `t` looks like Unix seconds. `s` is a long hex string.
    The format of `s` and the signature algorithm are not documented.
  - The erxproxy (`POST /ePrescription/create`, `Content-Type: text/plain`) takes the
    `CHMED16A1…` payload and returns either the signed URL (`output-format=data`) or a PNG of its
    QR code (`output-format=qrcode`).
- **Decision**:
  - **Recognition:** An input is recognised as an eRezept URL when it starts with
    `https://eprescription.hin.ch/` (case-insensitive scheme and host, trimmed).
  - **Splitting:** The fragment is split on `&`. The token starting with `CHMED` is the payload,
    decoded as today (M-IN-02). The other tokens are `key=value` pairs.
  - **Required parameters:** `i`, `t` and `s` must each be present exactly once and non-empty.
    Otherwise the conversion fails with `EREZEPT_URL_INVALID`, naming the missing parameter, never
    its value.
  - **Unknown parameters:** They are ignored and reported as `UNKNOWN_FIELD_IGNORED` with their
    name only. A query part (`?…`) is ignored in the same way.
  - **Signature data:** It is returned **raw**, exactly as in the URL (`i` still URL-encoded),
    with `verified = false`. If `t` is not all digits or `s` is not hex, the conversion still
    succeeds, with INFORMATION `SIGNATURE_FORM_UNRECOGNISED`.
  - **Document type:** The embedded document must be a prescription (`MedType` 3); otherwise the
    conversion fails with `UNSUPPORTED_DOCUMENT_TYPE`.
  - **Report:** The report always carries INFORMATION `SIGNATURE_NOT_VERIFIED`.
- **Rationale**:
  - Raw values are the only lossless form. The caller passes them to HIN unchanged, and decoding
    `i` is trivial for the caller.
  - A strict host check prevents arbitrary URLs from being treated as prescriptions.
  - Malformed but present signature parameters are not judged (spec edge case). Only the
    converter's own recognition is reported.
- **Open item**: The documentation does not say whether the erxproxy's integration environment
  (`--env int`) uses a different host. The host list is a constant; a test URL from the
  integration environment decides whether it needs a second entry (task T062).
- **Open evidence**: That the unsigned payload can be signed by the erxproxy *without
  modification* (constitution I) is shown indirectly: by the eRezept schema, the eRezept rules
  and the payload form that the documentation names as the erxproxy input. A real signing run
  needs an HIN test account and stays a manual check (T062), as in feature 001.
- **Alternatives**:
  - Returning decoded values. Rejected: not lossless.
  - Accepting any host. Rejected: anything with a CHMED payload in the fragment would then pass as
    an eRezept.

## R7 — Data supplied by options for a signed eRezept (FR-019)

- **Decision**: When options supply data for a signed eRezept (patient identifier, author
  details), the conversion adds INFORMATION `NOT_COVERED_BY_SIGNATURE` at the supplemented path
  (`/Patient/Ids`, `/HcPerson`, …). The CH EMED document then contains data that the signature
  does not cover, and the report says so.
- **Rationale**:
  - Without the entry, a caller could take the whole document for signed content.
  - The report already records every derived value (constitution II); this is the same kind of
    statement.
- **Alternatives**:
  - Refusing options for signed input. Rejected: the CH EMED document would then often be
    impossible to produce (FR-019, VeKa number often missing).

## R8 — Library API shape (FR-010, FR-014)

- **Decision**:
  - **Output form:** New option `ConversionOptions.outputForm` (`DOCUMENT` default, `QR_PAYLOAD`,
    `QR_IMAGE`).
  - **Signature data:** New result field `ConversionResult.signature`
    (`Optional<SignatureData>`), with `SignatureData(String identity, String timestamp, String
    signature)`. The record also exposes `verified()`, which always returns false.
  - **Input forms:** No new format ids. All input forms (JSON, payload, eRezept URL, PNG/JPEG
    image) stay under source `CHMED16A` and are detected by the format module.
  - **Version choice (FR-010):** It is expressed by the target format. `ch-emed → chmed16a` is the
    only QR target. `ch-emed → chmed23a` fails with the existing `UNSUPPORTED_CONVERSION`, which
    lists the supported pairs, until CHMED23A exists.
  - **Advertised forms:** `FormatModule` gets two default methods, `inputForms()` and
    `outputForms()`. The library exposes them through `Converter.formats()` (capabilities,
    FR-015).
- **Rationale**:
  - The hub model stays as it is: QR and eRezept are transmission forms of CHMED16A, not formats
    of their own. The constitution lists them separately in its scope table, but feature 001's
    research R2 already treats the QR payload as an input form of CHMED16A.
  - An option keeps the number of conversion pairs and REST resources unchanged (FR-014: existing
    operations unchanged).
- **Compatibility**: `ConversionResult` and `ConversionOptions` get an additional component. Their
  existing constructors and factory methods are kept as overloads, and all `with…` methods stay.
  This is a MINOR change of the library API (changelog).
- **Alternatives**:
  - New formats `CHMED16A_QR` and `EREZEPT`. Rejected: they would multiply conversion pairs, and
    the signature data would still need a place outside the document.
  - Signature data in the report. Rejected by the clarification of 2026-11-01.

## R9 — Interfaces (FR-014, FR-015)

- **CLI**:
  - `--output-form document|qr-payload|qr-image` (default `document`).
  - `--signature-output FILE` writes the signature data as JSON. If it is omitted and `-o FILE` is
    given, the data goes to `FILE.signature.json`. If the output is stdout, the signature data is
    not written and a note on stderr says so. The report stays identical across interfaces.
  - The input file is read as bytes, as today, so images work without a new option.
  - The exit codes stay as they are: every failed conversion, including image, QR and URL errors,
    exits with 1. An `--output-form` other than `document` with `--to ch-emed` is a usage error
    and exits with 2.
- **REST**:
  - `options.outputForm` (`document|qr-payload|qr-image`).
  - Response field `signature` (`{identity, timestamp, signature, verified}`), present only for
    signed input.
  - `outputMediaType` becomes `text/plain` (payload) or `image/png` (image) for the new forms.
  - Capabilities gain `formats` (input and output forms per format).
  - The OpenAPI version goes from 1.0.0 to 1.1.0 (additive).
- **REST errors**:

  | Case | HTTP | `errorType` |
  |------|------|-------------|
  | `outputForm` other than `document` with target `ch-emed` (checked before the conversion from the capabilities) | 400 | `MALFORMED_REQUEST` |
  | Image, QR or URL problems (`IMAGE_UNREADABLE`, `IMAGE_TOO_LARGE`, `QR_NOT_FOUND`, `QR_MULTIPLE`, `EREZEPT_URL_INVALID`) | 422 | `INVALID_INPUT` |
  | `QR_CAPACITY_EXCEEDED`, `QR_IMAGE_NOT_ALLOWED` | 422 | `CONVERSION_FAILED` |

- **Single contract**: The OpenAPI description remains the single file
  `specs/002-rest-conversion-service/contracts/openapi.yaml`, which the build syncs and generates
  the client from. This feature's contract describes the changes, and the implementation applies
  them to that file.

## R10 — Tests and fixtures (constitution III, SC-001…SC-009)

- **Reading tests**:
  - Images of the feature 001 reference plans are generated *in the test* by the converter's
    encoder (corpus × image).
  - Committed fixtures cover images not produced by our encoder: a few PNG/JPEG files generated
    once with an independent tool (`qrencode`), plus degraded variants (rotated 8°, scaled 60 %,
    JPEG quality 60, gaussian noise). The variants are made by a test helper with fixed seeds.
  - All content is synthetic.
- **Independent reader (SC-002)**: BoofCV `boofcv-recognition` (Apache-2.0), test scope only,
  decodes every produced image.
- **Signed eRezept fixtures**: Synthetic URLs built from the feature 001 prescriptions with
  invented `i`, `t` and `s` values (`s` = hex of a fixed byte pattern), since nothing is verified.
  Also: missing, duplicate, unknown and malformed parameters, a foreign host, and a plan instead of
  a prescription.
- **Parity, privacy and network tests**: The tests of feature 002 (library/CLI/REST parity, log
  scan, no outbound network) are extended to the new forms. The log scan also looks for the
  payload prefix `CHMED16A1`, `eprescription.hin.ch` and the fixture signature values.
- **Performance tests**: SC-005 (20 medications fit into one image) and SC-006 (image read adds
  < 1 s) are added to the existing `PerformanceTest`. They run on 4 vCPUs on the build machine.

## R11 — Module layout (constitution V)

- **Decision**: New module `converter-qr`, holding the QR image codec only (text ↔ PNG, image →
  text), with ZXing and `javax.imageio`. It knows nothing about CHMED. `format-chmed16a` depends
  on it and does the CHMED-specific work: input-form detection, payload encoder, eRezept URL
  parser, output forms.
- **Rationale**:
  - The constitution names QR encoding/decoding as a separate layer.
  - CHMED23A will need the same codec. The ZXing dependency stays out of `converter-core`.
- **Alternatives**:
  - A package inside `format-chmed16a`. Rejected: CHMED23A would have to depend on the CHMED16A
    module or copy the code.
  - The codec in `converter-core`. Rejected: core stays format- and image-free.

## R12 — Pinned specifications and licences

- **Decision**: `docs/specifications.md` adds the following:
  - `chtransmissionformat/README.md` to the files of the pinned commit.
  - The QR parameters of R3, marked provisional.
  - The eRezept URL shape of R6.
  - ZXing 3.5.3 (and BoofCV, test scope) under libraries.

  `docs/third-party-licenses.md` adds ZXing (Apache-2.0). The eMediplan repository is MIT
  (`LICENSE`, 2024). The eRezept documentation has no licence statement; only its URL shape is
  used, and no content is copied.
