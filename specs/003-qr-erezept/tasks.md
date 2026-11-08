---

description: "Task list for feature 003 - eMediplan QR Code and eRezept Schweiz"
---

# Tasks: eMediplan QR Code and eRezept Schweiz

**Input**: Design documents from `/specs/003-qr-erezept/`

**Prerequisites**:
- plan.md, spec.md, research.md (R1–R12), data-model.md, contracts/ (library-api.md, cli.md,
  rest-api.md), quickstart.md.
- Features 001 and 002 build green.

**Tests**: INCLUDED. Constitution principle III requires test-first, QR tests at payload and image
level, and synthetic signed eRezept URLs (research R10). Write each test first and confirm that it
fails before implementing.

**Organization**: Tasks are grouped by user story, so each story can be implemented and tested
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story (US1–US5) the task belongs to
- All paths are relative to the repository root

## Path Conventions

- **Core API**: `converter-core/src/main/java/ch/emed/converter/core/<package>/`
- **QR codec**: `converter-qr/src/main/java/ch/emed/converter/qr/`. It has no CHMED knowledge and
  no framework.
- **CHMED16A**: `format-chmed16a/src/main/java/ch/emed/converter/chmed16a/<package>/`
- **Tests**: `<module>/src/test/java/...`
- **Fixtures**: `format-chmed16a/src/test/resources/fixtures/{qr,erezept}/`. They are synthetic
  only: no real plans, prescriptions or QR codes.
- **Never in logs, reports or problem details**: payloads, eRezept URLs, signature values or
  patient data (FR-016).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: New module, dependencies, pinning

- [X] T001 In `pom.xml`, register the module `converter-qr` between `converter-core` and
  `format-chmed16a`. Add the managed versions `com.google.zxing:core` and
  `org.boofcv:boofcv-recognition` (test scope).
  - Check the latest releases on Maven Central first; the plan assumes ZXing 3.5.3.
  - Record the chosen versions in the task note.
  - *Done 2026-11-08: ZXing core 3.5.4 (released 2025-11), BoofCV 1.6.0 (2026-10), the latest
    releases on Maven Central.*
- [X] T002 Create `converter-qr/pom.xml`.
  - Java 21; dependency `com.google.zxing:core` only (no `javase` module).
  - Test dependencies: JUnit 5, AssertJ, BoofCV.
  - The parent's `enforce-framework-free` and license-plugin rules apply.
- [X] T003 Add a compile dependency on `converter-qr` to `format-chmed16a/pom.xml`.
- [X] T004 [P] Add ZXing (Apache-2.0, `converter-qr`, shipped in both distributions) and BoofCV
  (Apache-2.0, test only, not shipped) to `docs/third-party-licenses.md`.
- [X] T005 [P] Update `docs/specifications.md` (research R12):
  - add `chtransmissionformat/README.md` to the files of the pinned eMediplan commit;
  - add the QR parameters of research R3, marked "provisional, pending IG eMediplan
    confirmation";
  - add the eRezept URL shape of research R6 (documentation 0.10.2);
  - add ZXing under Libraries.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Library API additions and input-form detection that every story needs (data model §1,
§3, §4, §5; contracts/library-api.md)

**CRITICAL**: No user story work can begin until this phase is complete.

### Tests (write first, must fail)

- [X] T006 [P] In `converter-core/src/test/java/ch/emed/converter/core/api/ConversionApiAdditionsTest.java`,
  test the API additions:
  - `ConversionOptions.defaults().outputForm() == DOCUMENT`; `withOutputForm` keeps all other
    options;
  - the old canonical constructor still compiles and sets `DOCUMENT`;
  - `ConversionResult` with 3 arguments has an empty `signature`;
  - a `signature` without `output` throws ("`signature` is present only if `output` is present");
  - `SignatureData.verified()` is always false.
- [X] T007 [P] In `converter-core/src/test/java/ch/emed/converter/core/api/OutputFormPipelineTest.java`,
  test the pipeline with a stub `FormatModule`:
  - an output form that the target module does not list → FAILED with ERROR
    `UNSUPPORTED_OUTPUT_FORM` (phase PARSE), before any mapping;
  - `QR_PAYLOAD` with target `CH_EMED` → the same error;
  - a signature set via `MappingContext.setSignature` appears in the result only on success;
  - `Converter.formats()` lists input and output forms per format, sorted by format id;
  - FR-010: `CH_EMED → CHMED23A` (no module registered) → `UNSUPPORTED_CONVERSION`, and the
    message lists the supported pairs.
- [X] T008 [P] In `converter-core/src/test/java/ch/emed/converter/core/api/ChEmedSourceFormHintTest.java`
  (FR-013), test source `CH_EMED` with a PNG, a JPEG, an eRezept URL and a `CHMED16A1` payload.
  Each gives FAILED with `PARSE_ERROR` and the message "Input is a CHMED16A QR image | eRezept URL
  | QR payload; use source chmed16a", and the message never contains input content.
- [X] T009 [P] In `format-chmed16a/src/test/java/ch/emed/converter/chmed16a/io/InputFormDetectorTest.java`,
  test the detection order of data model §1:
  - PNG magic `89 50 4E 47 0D 0A 1A 0A` → IMAGE; JPEG `FF D8 FF` → IMAGE;
  - text starting with `https://eprescription.hin.ch/` (scheme and host case-insensitive, leading
    and trailing whitespace trimmed) → EREZEPT_URL;
  - GIF `GIF8`, BMP `BM`, TIFF `49 49 2A 00` / `4D 4D 00 2A`, WebP (`RIFF` + `WEBP` at offset 8)
    → UNSUPPORTED_IMAGE;
  - text starting with `CHMED` (case-insensitive) → PAYLOAD;
  - a JSON object with a top-level `resourceType` (for example a CH EMED Bundle) → FHIR_JSON
    (FR-013);
  - anything else → JSON;
  - `http://eprescription.hin.ch/` and other hosts are not EREZEPT_URL.

### Implementation

- [X] T010 [P] Create the enum `OutputForm` (`DOCUMENT`, `QR_PAYLOAD`, `QR_IMAGE`) with external
  names `document`, `qr-payload`, `qr-image` in
  `converter-core/src/main/java/ch/emed/converter/core/api/OutputForm.java`.
- [X] T011 [P] Create the record `SignatureData(String identity, String timestamp, String
  signature)`, all non-null, with `verified()` returning `false`, in
  `converter-core/src/main/java/ch/emed/converter/core/api/SignatureData.java`.
- [X] T012 Add the component `outputForm` (default `DOCUMENT`) and `withOutputForm` to
  `converter-core/src/main/java/ch/emed/converter/core/api/ConversionOptions.java`.
  - Keep the previous canonical constructor as an overload.
  - Pass `outputForm` through every existing `with…` method.
  - Depends on T010.
- [X] T013 Add the component `signature` (`Optional<SignatureData>`) to
  `converter-core/src/main/java/ch/emed/converter/core/api/ConversionResult.java`.
  - Invariant: present only if `output` is present.
  - Keep the 3-argument constructor (sets it empty).
  - Depends on T011.
- [X] T014 [P] Add the codes of data model §4 to
  `converter-core/src/main/java/ch/emed/converter/core/report/ReasonCode.java`, each with a
  one-line Javadoc: `IMAGE_UNREADABLE`, `IMAGE_TOO_LARGE`, `QR_NOT_FOUND`, `QR_MULTIPLE`,
  `EREZEPT_URL_INVALID`, `SIGNATURE_NOT_VERIFIED`, `SIGNATURE_FORM_UNRECOGNISED`,
  `NOT_COVERED_BY_SIGNATURE`, `QR_CAPACITY_EXCEEDED`, `QR_IMAGE_NOT_ALLOWED`,
  `UNSUPPORTED_OUTPUT_FORM`.
- [X] T015 Add the format SPI and capability additions in `converter-core`:
  - `FormatModule`: `default List<String> inputForms()` (`["json"]`) and
    `default List<OutputForm> outputForms()` (`[DOCUMENT]`);
  - a new record `FormatInfo(FormatId format, List<String> inputForms, List<OutputForm>
    outputForms)` in `api/FormatInfo.java`;
  - `Converter.formats()`.
- [X] T016 Add `setSignature(SignatureData)` (at most once; a second call throws
  `IllegalStateException`) and `signature()` to
  `converter-core/src/main/java/ch/emed/converter/core/spi/MappingContext.java`.
- [X] T017 Change `converter-core/src/main/java/ch/emed/converter/core/api/DefaultConverter.java`
  (depends on T012–T016):
  - check the output form against the target's `outputForms()` (`CH_EMED`: `DOCUMENT` only), with
    `UNSUPPORTED_OUTPUT_FORM`, before parsing;
  - copy `ctx.signature()` into the result when the outcome is not FAILED;
  - add the form hint of T008 in `parseChEmed` (check the magic bytes and prefixes; never echo the
    input);
  - implement `formats()` (`CH_EMED`: `["json"]`, `[DOCUMENT]`).
- [X] T018 Implement `InputFormDetector` (enum `InputForm { JSON, FHIR_JSON, PAYLOAD,
  EREZEPT_URL, IMAGE, UNSUPPORTED_IMAGE }`) per data model §1 in
  `format-chmed16a/src/main/java/ch/emed/converter/chmed16a/io/InputFormDetector.java`.
  - Route JSON and PAYLOAD through the existing readers in `Chmed16aFormatModule.toChEmed`;
    IMAGE and EREZEPT_URL fail with `PARSE_ERROR` "not yet supported" until US1/US2.
  - UNSUPPORTED_IMAGE → `IMAGE_UNREADABLE` "unsupported image format".
  - FHIR_JSON → `PARSE_ERROR` "Input is a FHIR resource; use source ch-emed", with a test case in
    an existing CHMED16A reader test. Before, such input failed with schema errors; adjust any
    existing test that expected them, and list the change in T060.
  - Remove the old `http` rule M-IN-03 from `Chmed16aJsonReader` and replace it with
    `PARSE_ERROR` for an `http(s)` text that is not an eRezept URL.
- [X] T019 Update `specs/001-chmed16a-ch-emed-conversion/contracts/library-api.md`: add a pointer
  to `specs/003-qr-erezept/contracts/library-api.md` and the new components. Update the
  reason-code catalogue in `docs/mapping/chmed16a-ch-emed.md` with the new codes, and replace
  M-IN-03 by the detection order of data model §1.

**Checkpoint**: The existing test suite is still green (FR-014, existing operations unchanged), and
T006–T009 pass.

---

## Phase 3: User Story 1 - Read a medication plan from a QR code image (Priority: P1) MVP

**Goal**: A PNG/JPEG image with one QR code holding a CHMED16A payload is converted to CH EMED with
the same document and report as the plain JSON.

**Independent Test**: Convert the committed independent QR images. Each gives the same CH EMED
document and report as the matching JSON fixture with the same options (spec US1, SC-001 for the
fixture subset).

### Tests (write first, must fail)

- [X] T020 [P] [US1] Create independent image fixtures in
  `format-chmed16a/src/test/resources/fixtures/qr/` and a `README.md` there with the exact
  generation commands. Generate them once with `qrencode` from the existing synthetic payloads:
  - `plan-basic.png` (from `chmed16a/plan-basic.qr.txt`);
  - `readme-example.png` (from `official/readme-example-medication-plan.qr.txt`);
  - `plan-non-ascii.png` (from the payload of `plan-non-ascii.json`, produced with `gzip -n` and
    `base64`);
  - `plan-non-ascii-plain.png` (from `CHMED16A0` + the plain JSON of `plan-non-ascii.json`, UTF-8,
    with umlauts and accents; `qrencode -8`);
  - `plan-basic.jpg` (JPEG quality 85 of `plan-basic.png`);
  - `two-codes.png` (two codes side by side);
  - `no-code.png` (plain grey image);
  - `url-code.png` (QR code with `https://example.org/`);
  - `chmed23a.png` (from `chmed16a/chmed23a-plan.json` as a CHMED23A payload);
  - `not-an-image.gif`.
  - *Done 2026-11-08: `qrencode` is not installed on the build machines; the images were made
    with BoofCV, an encoder independent of ZXing, by
    `converter-qr/src/test/java/ch/emed/converter/qr/QrFixtureGenerator.java` (runs only with
    `-Dqr.fixtures=generate`). The command is in `fixtures/qr/README.md`.*
- [X] T021 [P] [US1] In `converter-qr/src/test/java/ch/emed/converter/qr/QrImageReaderTest.java`,
  test the reader:
  - PNG and JPEG decode to the exact content bytes;
  - `plan-non-ascii-plain.png` gives exactly the UTF-8 bytes of its payload, umlauts included;
  - no code → `NOT_FOUND`; two codes → `MULTIPLE` (the reader does not choose);
  - GIF or truncated PNG → `UNREADABLE`;
  - a header declaring 8000 × 6000 px (48 MP > 40 MP) → `TOO_LARGE` without decoding the pixels,
    and the message names the pixel limit;
  - no file appears in `java.io.tmpdir` during reading (FR-017);
  - exception messages never contain the decoded text.
- [X] T022 [P] [US1] Create the test helper `DegradedImages` (rotate 8°, scale 60 %, JPEG quality
  60, gaussian noise σ=8, each with a fixed seed) in
  `converter-qr/src/test/java/ch/emed/converter/qr/DegradedImages.java`, and a test in
  `QrImageReaderDegradedTest.java` that reads every variant of `plan-basic.png`. This is best
  effort, but these four variants must pass.
- [X] T023 [US1] In `format-chmed16a/src/test/java/ch/emed/converter/chmed16a/ImageInputTest.java`,
  test image input end to end:
  - each image of T020 that holds a plan, converted to CH EMED, gives a document and report equal
    to the JSON fixture's conversion with the same options;
  - without `author`, both fail identically with `MISSING_REQUIRED_DATA` at `/Auth` (US1
    scenario 4, FR-019);
  - `no-code.png` → `QR_NOT_FOUND`; `two-codes.png` → `QR_MULTIPLE`; `url-code.png` →
    `PARSE_ERROR` without the URL in the report; `chmed23a.png` → `UNSUPPORTED_VERSION`;
    `not-an-image.gif` → `IMAGE_UNREADABLE` "unsupported image format";
  - `plan-non-ascii-plain.png` gives the same document and report as `plan-non-ascii.json`
    (edge case non-ASCII);
  - a corrupt image → `IMAGE_UNREADABLE`;
  - an image above `maxDecodedSize` bytes (option set low in the test) → `IMAGE_TOO_LARGE`, and
    the message names the byte limit;
  - none of these failures returns output.
  - *Done 2026-11-08 as `it/ImageInputIT.java`: it uses the CH EMED validator, like the other
    end-to-end tests of the module.*

### Implementation

- [X] T024 [P] [US1] Create `QrException` with `enum Kind { UNREADABLE, TOO_LARGE, NOT_FOUND,
  MULTIPLE, CAPACITY }` and a message that never contains content, in
  `converter-qr/src/main/java/ch/emed/converter/qr/QrException.java`.
- [X] T025 [US1] Implement `QrImageReader.read(byte[] image, long maxPixels)` → `byte[]` (the raw content bytes), per
  research R5, in `converter-qr/src/main/java/ch/emed/converter/qr/QrImageReader.java`:
  - accept PNG and JPEG by magic bytes;
  - read via `ImageIO` with `MemoryCacheImageInputStream`; check width × height from the header
    before `read()`;
  - convert to `RGBLuminanceSource`;
  - detect several codes with `QRCodeMultiReader`; decode with `QRCodeReader` and hint
    `TRY_HARDER`;
  - return the concatenated `ResultMetadataType.BYTE_SEGMENTS`, not ZXing's guessed text (research
    R5);
  - binarizer fallback `HybridBinarizer` → `GlobalHistogramBinarizer`;
  - default `maxPixels` = 40 000 000;
  - depends on T024.
  - *Done 2026-11-08 with a change: ZXing fills `BYTE_SEGMENTS` only for byte-mode segments, so
    a code with mixed modes (e.g. `CHMED16A1` alphanumeric) would lose content. The reader
    therefore decodes with the hint `CHARACTER_SET=ISO-8859-1`, which maps every byte to one
    char, and returns the chars as bytes. If an ECI is present (symbology identifier `]Q2`), the
    text is decoded as declared and returned as UTF-8. Both cases are covered by tests in
    `QrImageReaderTest`.*
- [X] T026 [US1] Wire IMAGE in `format-chmed16a/src/main/java/ch/emed/converter/chmed16a/Chmed16aFormatModule.java`:
  - read the image with `QrImageReader` and map `QrException` kinds to `IMAGE_UNREADABLE`,
    `IMAGE_TOO_LARGE`, `QR_NOT_FOUND` and `QR_MULTIPLE` (phase PARSE);
  - reject an image larger than `maxDecodedSize` bytes with `IMAGE_TOO_LARGE`, naming the byte
    limit;
  - decode the content bytes as UTF-8 (strict decoder; invalid UTF-8 → `PARSE_ERROR`);
  - detect the text again: PAYLOAD → existing decoder (non-ASCII allowed in `CHMED16A0`);
    EREZEPT_URL → "not yet supported" until US2; anything else → `PARSE_ERROR` "QR code does not
    hold a CHMED payload";
  - list `qr-payload` and `qr-image` in `inputForms()`;
  - depends on T025.
- [X] T027 [US1] Add a section "QR code input" (M-QR-01 … M-QR-04: image forms, limits, one code
  only, content check) to `docs/mapping/chmed16a-ch-emed.md`.
  - *Done 2026-11-08, together with the new rule M-IN-07 (input-form detection, T018).*

**Checkpoint**: US1 works through the library. CLI and REST already pass the bytes through, and
their parity is checked in US5.

---

## Phase 4: User Story 2 - Read a signed eRezept (Priority: P1)

**Goal**: A signed eRezept URL, or a QR image holding one, is converted to a CH EMED Medication
Prescription. The signature data is returned raw and unverified beside the document.

**Independent Test**: Convert the synthetic signed eRezept fixtures. The document equals the
conversion of the embedded prescription, and the signature values come back unchanged with
`verified = false` (SC-003).

### Tests (write first, must fail)

- [X] T028 [P] [US2] Create synthetic fixtures in
  `format-chmed16a/src/test/resources/fixtures/erezept/` with a `README.md` (generation, "all
  values invented"):
  - `rx-signed.url.txt`: `https://eprescription.hin.ch/#` + payload of `chmed16a/rx-basic.json` +
    `&i=Erika+Muster+%28HIN%7Cemust1%29&t=1767225600&s=` + hex of 48 bytes `0x30…`;
  - `rx-signed-erezept-example.url.txt`, from `rx-erezept-example.json`;
  - `rx-missing-s.url.txt`, `rx-duplicate-t.url.txt`, `rx-empty-i.url.txt`;
  - `rx-unknown-param.url.txt` (`&x=1`);
  - `rx-query.url.txt` (`?ref=1` before `#`);
  - `rx-reordered.url.txt` (`s`, `i`, payload, `t`);
  - `rx-malformed-sig.url.txt` (`t=yesterday`, `s=not-hex`);
  - `rx-foreign-host.url.txt` (`https://example.org/#…`);
  - `plan-signed.url.txt` (payload of a medication plan);
  - `rx-signed.png` (QR code of `rx-signed.url.txt`, made with `qrencode`).
  - *Done 2026-11-08 with `gzip -n` and `base64` in Git Bash (commands in `fixtures/erezept/README.md`);
    `rx-signed.png` made with BoofCV (`QrFixtureGenerator`, see T020). Added
    `rx-no-patient-id.json` / `rx-signed-no-patient-id.url.txt` (needs `patientIdentifier`) and
    `rx-no-payload.url.txt`.*
- [X] T029 [P] [US2] In `format-chmed16a/src/test/java/ch/emed/converter/chmed16a/io/ErezeptUrlParserTest.java`,
  test the parser:
  - payload and raw `i`, `t`, `s` are extracted, and `i` stays URL-encoded;
  - parameter order is free; a query part is ignored;
  - an unknown parameter → `UNKNOWN_FIELD_IGNORED` naming `x`, without its value;
  - missing, duplicate or empty `i`/`t`/`s` → `EREZEPT_URL_INVALID` naming the parameter;
  - foreign host or no payload → `EREZEPT_URL_INVALID`;
  - `t` not all digits or `s` not hex → INFORMATION `SIGNATURE_FORM_UNRECOGNISED` (field name
    only), with the values passed on unchanged;
  - no message contains a parameter value.
- [X] T030 [US2] In `format-chmed16a/src/test/java/ch/emed/converter/chmed16a/ErezeptInputTest.java`,
  test the conversion end to end:
  - `rx-signed.url.txt` → the document byte-equals the conversion of `rx-basic.json` with the same
    options;
  - `result.signature()` holds the exact fixture values and `verified() == false`;
  - the report contains `SIGNATURE_NOT_VERIFIED` and none of the values;
  - `rx-signed.png` gives the same result as the URL;
  - `plan-signed.url.txt` → `UNSUPPORTED_DOCUMENT_TYPE`, no signature;
  - a prescription that needs `patientIdentifier`: without it → `MISSING_REQUIRED_DATA` and
    neither output nor signature (US2 scenario 5); with it → success plus INFORMATION
    `NOT_COVERED_BY_SIGNATURE` at `/Patient/Ids`. The same holds for author details at
    `/HcPerson` (research R7);
  - a log capture during the test finds no fixture value (FR-016).
  - *Done 2026-11-08 as `it/ErezeptInputIT.java`. Options for the author of a revision 1
    prescription supply both `/HcPerson` and `/HcOrg`; both are marked.*

### Implementation

- [X] T031 [US2] Implement `ErezeptUrlParser.parse(String url, ReportCollector report)` →
  `Parsed(String payload, SignatureData signature)` or null, per research R6, in
  `format-chmed16a/src/main/java/ch/emed/converter/chmed16a/io/ErezeptUrlParser.java`:
  - host constant `eprescription.hin.ch`;
  - split the fragment on `&`; the token starting with `CHMED` is the payload, the other tokens
    are `key=value` pairs;
  - `t` is recognised by `[0-9]+`, `s` by `[0-9A-Fa-f]+`;
  - limit the URL length to `maxDecodedSize`.
- [X] T032 [US2] Wire EREZEPT_URL (direct, and from IMAGE) in
  `format-chmed16a/src/main/java/ch/emed/converter/chmed16a/Chmed16aFormatModule.java`:
  - parse the URL, decode the payload with the existing decoder, then require `isPrescription()`
    (otherwise `UNSUPPORTED_DOCUMENT_TYPE`);
  - call `ctx.setSignature`; add `SIGNATURE_NOT_VERIFIED` (path `/`);
  - add `erezept-url` to `inputForms()`;
  - depends on T031.
- [X] T033 [US2] Add `NOT_COVERED_BY_SIGNATURE` (INFORMATION, phase MAP, path of the supplemented
  element) wherever an option fills data, when `ctx.signature()` is present. The places are in
  `format-chmed16a/src/main/java/ch/emed/converter/chmed16a/mapping/PatientMapper.java`
  (`patientIdentifier`) and `AuthorMapper.java` / `MedicationPrescriptionBuilder.java` (`author`).
  - *Done 2026-11-08 via `MappingScope.suppliedByOption(path)`, called in `PatientMapper` and
    `AuthorMapper.mapPrescription` (`/HcPerson`, `/HcOrg`).*
- [X] T034 [US2] Add a section "eRezept Schweiz input" (M-URL-01 … M-URL-05, because M-ERX-* is taken by the eRezept readiness rules: host, parameters,
  raw signature data, prescription only, options not covered by the signature) to
  `docs/mapping/chmed16a-ch-emed.md`. Note the open question about the host of the integration
  environment (research R6).

**Checkpoint**: US1 and US2 together read every input form through the library.

---

## Phase 5: User Story 3 - Create a QR code for a medication plan (Priority: P2)

**Goal**: A CH EMED Medication Card gives the `CHMED16A1` payload, or a PNG meeting the symbol
requirements, that an independent reader decodes exactly.

**Independent Test**: Create payloads and images from the CH EMED fixtures. BoofCV decodes each
image to exactly the payload, and reading the image back yields the expected plan (SC-002).

### Tests (write first, must fail)

- [X] T035 [P] [US3] In `format-chmed16a/src/test/java/ch/emed/converter/chmed16a/io/Chmed16aPayloadEncoderTest.java`,
  test the encoder:
  - the output starts with `CHMED16A1H4sI`;
  - `Chmed16aPayloadDecoder.decode(encode(json))` equals `json` byte for byte (FR-012);
  - the GZip header MTIME is 0;
  - two runs give identical bytes;
  - the output is ASCII without a trailing line feed;
  - Base64 uses the standard alphabet with padding.
- [X] T036 [P] [US3] In `converter-qr/src/test/java/ch/emed/converter/qr/QrImageWriterTest.java`,
  test the writer:
  - the PNG is 1-bit grayscale, with no `tEXt`/`tIME` chunks;
  - the quiet zone is 4 modules and the module size 8 px;
  - level M and the smallest version (checked by decoding with ZXing and reading the version);
  - the `pHYs` resolution gives 40 mm for the symbol without quiet zone (±0.1 mm);
  - identical bytes for identical text (FR-011);
  - text above 2331 bytes → `CAPACITY` with the limit in the message;
  - BoofCV decodes every image of the CHMED16A corpus payloads to exactly the payload (SC-002).
- [X] T037 [US3] In `format-chmed16a/src/test/java/ch/emed/converter/chmed16a/QrOutputTest.java`,
  test the output forms end to end:
  - for every Medication Card fixture, `QR_PAYLOAD` decodes to the `DOCUMENT` output and
    `QR_IMAGE` reads back to the same payload;
  - a round trip CH EMED → image → CH EMED differs only by the documented capability gaps
    (`capability-gaps.json`);
  - the empty plan gives a valid code;
  - a generated oversized plan (`LargePlanGenerator` in `format-chmed16a/src/test/java/ch/emed/converter/chmed16a/testsupport/`, enough medications to exceed 2331 bytes) →
    `QR_CAPACITY_EXCEEDED` with no output, while `QR_PAYLOAD` of the same plan succeeds;
  - umlauts and accents survive the round trip.
  - *Done 2026-11-08 as `it/QrOutputIT.java`. The image round trip is compared with the document
    round trip, whose differences `RoundTripIT` already checks against `capability-gaps.json`.
    Generated plans compress well: 100 medicaments still fit, so the oversized case uses 600,
    converted without the validator (not under test here).*
- [X] T038 [P] [US3] In `format-chmed16a/src/test/java/ch/emed/converter/chmed16a/it/PerformanceTest.java`,
  add SC-005: the 20-medication plan of `LargePlanGenerator` (`testsupport/`) fits into one image at level M and reads
  back without error. Log the payload length and QR version for the record in quickstart.md.
  - *Done 2026-11-08: 20 medicaments give a 673-byte payload, QR version 21 (level M), PNG
    2683 bytes (Windows run; recorded in quickstart.md with T061).*

### Implementation

- [X] T039 [P] [US3] Implement `Chmed16aPayloadEncoder.encode(byte[] json)` → `byte[]` (ASCII):
  `"CHMED16A1"` + `Base64.getEncoder()` of `GZIPOutputStream` (default level, MTIME 0), per
  research R2. File:
  `format-chmed16a/src/main/java/ch/emed/converter/chmed16a/io/Chmed16aPayloadEncoder.java`.
- [X] T040 [P] [US3] Implement `QrImageWriter.write(String text)` → PNG bytes, per research R3 and
  data model §2, in `converter-qr/src/main/java/ch/emed/converter/qr/QrImageWriter.java`:
  - ZXing `QRCodeWriter`, `ERROR_CORRECTION=M`, `MARGIN=4`, `CHARACTER_SET=ISO-8859-1`;
  - scale 8 px/module into a `TYPE_BYTE_BINARY` image;
  - ImageIO PNG writer with a `pHYs` node computed from the symbol width;
  - throw `QrException(CAPACITY)` if ZXing reports that the data is too big or the text exceeds
    2331 bytes.
  - *Done 2026-11-08 with ZXing's `Encoder` and an own rendering, which fixes quiet zone and
    module size exactly. No `CHARACTER_SET` hint: with one, ZXing adds an ECI segment (12 bits,
    found by the capacity test); without, it encodes ISO-8859-1 plainly.*
- [X] T041 [US3] Implement the output forms in `Chmed16aFormatModule.fromChEmed`
  (`format-chmed16a/src/main/java/ch/emed/converter/chmed16a/Chmed16aFormatModule.java`):
  - `QR_PAYLOAD` → encoder on the JSON writer's bytes;
  - `QR_IMAGE` → encoder, then writer; `QrException(CAPACITY)` → `QR_CAPACITY_EXCEEDED` (phase
    MAP, message with the limit in bytes);
  - `outputForms()` = `DOCUMENT`, `QR_PAYLOAD`, `QR_IMAGE`;
  - depends on T039 and T040.
- [X] T042 [US3] Add a section "QR code output" (M-QR-10 … M-QR-12: payload construction, symbol
  parameters, capacity, determinism) to `docs/mapping/chmed16a-ch-emed.md`.

**Checkpoint**: Medication plans make the round trip CH EMED ↔ QR image through the library.

---

## Phase 6: User Story 4 - Prepare a prescription for signing (Priority: P2)

**Goal**: A CH EMED Medication Prescription gives the unsigned `CHMED16A1` payload that the HIN
erxproxy can sign unchanged. A QR image of it is refused.

**Independent Test**: Convert the CH EMED prescriptions with `QR_PAYLOAD`. Each decoded payload
passes `erezept-chmed16a-schemav7.json` and the eRezept rules (SC-004).

### Tests (write first, must fail)

- [X] T043 [US4] In `format-chmed16a/src/test/java/ch/emed/converter/chmed16a/PrescriptionPayloadTest.java`,
  test prescriptions:
  - for every CH EMED prescription fixture that converts successfully, `QR_PAYLOAD` decodes to
    JSON that passes `schemas/erezept-chmed16a-schemav7.json` and `ErezeptPrescriptionRules`;
  - `result.signature()` is empty;
  - `rx-missing-prescriber-gln.json` fails with the rule violations in the report and no output;
  - `QR_IMAGE` on `medication-prescription-example.json` → `QR_IMAGE_NOT_ALLOWED` (message:
    prescriptions are output as unsigned payload only), with no output.
  - *Done 2026-11-08 as `it/PrescriptionPayloadIT.java`; it also checks that the decoded payload
    equals the `document` output.*

### Implementation

- [X] T044 [US4] In `Chmed16aFormatModule.fromChEmed`, refuse `QR_IMAGE` for
  `MEDICATION_PRESCRIPTION` with `QR_IMAGE_NOT_ALLOWED` (phase MAP), before building the result.
  `QR_PAYLOAD` reuses T041 after `ErezeptPrescriptionRules`, so the existing refusal on violations
  applies (FR-009).
- [X] T045 [US4] Add M-QR-13 (prescription: payload only, no image, eRezept rules) to
  `docs/mapping/chmed16a-ch-emed.md`. Add a note to `docs/operations/rest-service.md` and to
  `README.md` that the payload is the input of `POST /ePrescription/create` of the erxproxy, and
  that the converter never calls it.

**Checkpoint**: Every reading and writing form works through the library.

---

## Phase 7: User Story 5 - Same behaviour through all interfaces (Priority: P3)

**Goal**: CLI and REST offer the new forms, options and signature data with the same documents,
payloads, images and reports as the library.

**Independent Test**: Run the same QR, URL and image inputs through library, CLI and REST. The
documents, payloads, images and reports are identical (SC-008), and the capabilities list the new
forms.

### Tests (write first, must fail)

- [X] T046 [P] [US5] In `converter-cli/src/test/java/ch/emed/converter/cli/QrCliTest.java`, test
  the CLI (contracts/cli.md):
  - `--output-form qr-payload|qr-image` writes the same bytes as the library, and PNG to stdout
    unchanged;
  - `--output-form qr-image --to ch-emed` → exit 2;
  - image and URL input files convert;
  - a signed URL with `-o rx.json` writes `rx.json.signature.json`; `--signature-output sig.json`
    writes there;
  - with stdout output, stderr has one line "signature data not written" without values, and no
    file;
  - failures (`QR_NOT_FOUND`, `QR_CAPACITY_EXCEEDED`, `QR_IMAGE_NOT_ALLOWED`) exit 1 and write
    neither output nor signature file.
  - *Done 2026-11-08 without the capacity case: it needs a very large CH EMED document and is
    covered through the library by `QrOutputIT`; the CLI only passes the outcome on. An unknown
    `--output-form` is a usage error too.*
- [X] T047 [P] [US5] Add tests for the new parts of the contract to
  `converter-rest/src/test/java/ch/emed/converter/rest/contract/` (new `QrConversionContractTest.java`):
  - `outputForm` values accepted, and an unknown value → 400;
  - `outputForm=qr-image` with target `ch-emed` → 400 `MALFORMED_REQUEST` without a report;
  - success responses with `outputMediaType` `text/plain` or `image/png` and `signature` validate
    against the updated OpenAPI file;
  - the error cases of contracts/rest-api.md give the listed status and `errorType`, with a
    report;
  - capabilities contain `formats` and `limits.maxImagePixels`.
- [X] T048 [US5] Extend `converter-rest/src/test/java/ch/emed/converter/rest/parity/ServiceLibraryParityTest.java` (the
  library/CLI/REST parity test) to the input forms image and eRezept URL and the output forms
  `qr-payload` and `qr-image`. The decoded outputs, the reports and the signature data must be
  identical across the three interfaces (SC-008).
  - *Done 2026-11-08: all files of `fixtures/qr` and `fixtures/erezept` as input, the CH EMED
    corpus as `qr-payload` and `qr-image`; 120 cases in total, including failures.*

### Implementation

- [X] T049 [US5] Add `--output-form` (picocli enum converter on the external names) and
  `--signature-output` to `converter-cli/src/main/java/ch/emed/converter/cli/ConvertCommand.java`:
  - write the binary output unchanged;
  - write the signature JSON (`identity`, `timestamp`, `signature`, `verified`, in this order,
    UTF-8, pretty-printed) only on success;
  - default path `<output>.signature.json`; stderr note when the output is stdout;
  - usage error (exit 2) for `qr-*` with `--to ch-emed`.
- [X] T050 [US5] Apply contracts/rest-api.md to
  `specs/002-rest-conversion-service/contracts/openapi.yaml`: `info.version` 1.1.0, `outputForm`,
  `SignatureData`, `signature`, `outputMediaType` examples, `formats`, `maxImagePixels`, and
  `document` descriptions naming image and URL input. Check that the generated client
  (`GeneratedClientIT`) still compiles.
- [X] T051 [US5] Change the REST model and controller in
  `converter-rest/src/main/java/ch/emed/converter/rest/`:
  - `model/RequestOptions` + `outputForm`; `api/RequestOptionsMapper` maps it to
    `OutputForm`;
  - `api/ConversionController` refuses a non-`document` form for target `ch-emed` with 400
    `MALFORMED_REQUEST` before the conversion;
  - `mediaType(target, outputForm)` → `application/fhir+json`, `application/json`, `text/plain`
    or `image/png`;
  - `model/ConversionResponse` + `signature` (included only when present);
  - `model/CapabilitiesResponse` + `formats` from `Converter.formats()`, and
    `limits.maxImagePixels` = 40000000;
  - `CapabilitiesController` lists `outputForm` in `options`.
- [X] T052 [US5] Map the new codes in
  `converter-rest/src/main/java/ch/emed/converter/rest/error/FailureClassifier.java`:
  - `IMAGE_UNREADABLE`, `IMAGE_TOO_LARGE`, `QR_NOT_FOUND`, `QR_MULTIPLE`, `EREZEPT_URL_INVALID` →
    `INVALID_INPUT`;
  - `QR_CAPACITY_EXCEEDED`, `QR_IMAGE_NOT_ALLOWED` → `CONVERSION_FAILED`.

  In `GlobalExceptionHandler` and `ProblemFactory`, make sure that no problem detail contains
  payload, URL or signature values.
  - *Done 2026-11-08 without a code change in `FailureClassifier`: it classifies by phase, and all
    image, QR and URL codes are reported in phase PARSE (→ `INVALID_INPUT`), capacity and
    prescription image in phase MAP (→ `CONVERSION_FAILED`); `QrConversionContractTest` checks
    it. `GlobalExceptionHandler` gained `outputForm` in its list of contract property names; the
    problem details stay fixed texts.*
- [X] T053 [US5] Update `specs/001-chmed16a-ch-emed-conversion/contracts/cli.md` (pointer to the
  003 delta, new options in the table) and the CLI and REST sections of `README.md` with the
  examples of contracts/cli.md and quickstart.md Scenario 5.

**Checkpoint**: All five stories work through library, CLI and REST.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T054 [P] In `format-chmed16a/src/test/java/ch/emed/converter/chmed16a/CorpusImageTest.java`,
  cover SC-001 for the full corpus: for every reference plan and prescription of feature 001,
  generate the image in the test (encoder + writer) and read it back. The resulting CH EMED
  document and report must equal the plain JSON conversion with the same options, including the
  failures for missing required data. Prescriptions go through `QrImageWriter` directly, because
  the library refuses images for them.
- [ ] T055 [P] Add SC-006 to `format-chmed16a/src/test/java/ch/emed/converter/chmed16a/it/PerformanceTest.java`:
  reading the image of the 20-medication plan adds less than 1 s, warm, compared with its JSON.
- [ ] T056 [P] Extend `LogLeakTest` and `error/ProblemLeakTest` in
  `converter-rest/src/test/java/ch/emed/converter/rest/privacy/` (and the CLI stderr capture) to
  every new input and output form. They must find zero occurrences of `CHMED16A1`,
  `eprescription.hin.ch`, the fixture `i`/`t`/`s` values and fixture patient names in logs,
  reports and problem details (SC-007).
- [ ] T057 [P] Extend `converter-rest/src/test/java/ch/emed/converter/rest/privacy/NoOutboundNetworkTest.java`
  to the QR image and eRezept fixtures, and expect zero outbound attempts (SC-009).
- [ ] T058 [P] Update `docs/architecture.md`:
  - module diagram with `converter-qr`;
  - QR/eRezept from "planned" to "implemented (feature 003)";
  - the table row for the eRezept QR code / URL.
- [ ] T059 [P] Update `docs/operations/rest-service.md`: image input and its limits
  (`max-document-size`, 40 MP), new 422 cases, signature data in responses (treat as sensitive, it
  is never logged), and that the service never contacts HIN.
- [ ] T060 Add a MINOR entry to `CHANGELOG.md`:
  - library API additions, CLI options, OpenAPI 1.1.0, new reason codes;
  - changed behaviour of existing inputs: eRezept URLs are accepted (M-IN-03 removed); FHIR JSON
    with source `chmed16a` now fails with `PARSE_ERROR` instead of schema errors; GIF/BMP/TIFF/WebP
    input fails with `IMAGE_UNREADABLE`.
- [ ] T061 Run the full build and the time tests without asking first (user approval 2026-11-08; build machine `andromeda`
  in homeoffice, otherwise Windows): `sh ./mvnw -B clean verify` and the `taskset -c 0-3` time
  tests. Record the measured values, payload length and QR version of the 20-medication plan in
  `specs/003-qr-erezept/quickstart.md`.
- [ ] T062 Record the open items in `specs/003-qr-erezept/research.md` (R1, R6) and as a note in
  `docs/specifications.md`:
  - error-correction level M, to be confirmed by IG eMediplan or a domain reviewer;
  - host of the erxproxy integration environment, to be checked with a test URL;
  - signability without modification (constitution I), shown only indirectly (schema, rules,
    payload form).

  Optional and manual, with an HIN test account: sign one unsigned payload with the erxproxy
  (quickstart Scenario 4). This part does not block T063.
- [ ] T063 Set `**Status**` in `specs/003-qr-erezept/spec.md` to `Implemented` once T001–T061
  and the recording part of T062 are done.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup; blocks all user stories.
- **US1 (Phase 3)**: depends on Foundational. MVP.
- **US2 (Phase 4)**: depends on Foundational. T028 `rx-signed.png` and T032 (URL from an image)
  use T025 from US1; the URL path itself is independent of US1.
- **US3 (Phase 5)**: depends on Foundational. T037 reads images back with T025 (US1).
- **US4 (Phase 6)**: depends on US3 (T041 payload output).
- **US5 (Phase 7)**: depends on the stories whose forms it exposes; complete after US1–US4.
- **Polish (Phase 8)**: T054 needs US1 and US3; T055 needs US1 and US3; T061 after everything
  else; T063 last.

### Within each story

Tests first (must fail) → codec/parser → format module wiring → mapping documentation.

### Parallel opportunities

- **Setup**: T004 and T005 in parallel after T001–T003.
- **Foundational**: tests T006–T009 together; T010, T011 and T014 together; then T012, T013,
  T015, T016 → T017; T018 in parallel with T012–T017 (other module).
- **US1**: T020, T021 and T022 together; T024 in parallel with the tests.
- **US2**: T028 and T029 together. US2 (URL part, T029, T031) can run in parallel with US1.
- **US3**: T035, T036 and T038 together; T039 and T040 in parallel (different modules).
- **US5**: T046 and T047 together; CLI (T049) and REST (T050–T052) in parallel.
- **Polish**: T054–T059 in parallel.

## Parallel Example: User Story 3

```text
Task: T035 Chmed16aPayloadEncoderTest
Task: T036 QrImageWriterTest (incl. BoofCV cross-check)
Task: T038 PerformanceTest SC-005
Task: T039 Chmed16aPayloadEncoder          (format-chmed16a)
Task: T040 QrImageWriter                   (converter-qr)
```

## Implementation Strategy

### MVP first

1. Phase 1 Setup → Phase 2 Foundational (the existing tests stay green).
2. Phase 3 (US1) → validate with `ImageInputTest` and quickstart Scenario 1 (library or CLI, since
   the CLI passes bytes through unchanged).

### Incremental delivery

1. US1 (read QR image), then US2 (signed eRezept): both P1, the reading side complete.
2. US3 (QR for plans), then US4 (unsigned prescription payload): the writing side.
3. US5 (CLI and REST options, signature data, capabilities, parity): required by the constitution
   before the feature counts as complete.
4. Polish: full corpus, performance, privacy, docs, measurements, open items.

## Notes

- **Not new formats**: QR, payload and eRezept URL are forms of CHMED16A. No new conversion pairs
  or REST resources (research R8, FR-014).
- **Signature data**: It is returned raw and never verified. It appears only in
  `ConversionResult.signature`, the REST field `signature` and the CLI signature file, never in
  the document, the report or logs.
- **Provisional QR parameters**: Error correction M and the 40 MP limit are pinned in this plan.
  Change them only through the research, data model and `docs/specifications.md`.
- **Commits**: Collect changes and propose a commit at sensible points (for example after each
  phase checkpoint); commit only after the user's yes.
