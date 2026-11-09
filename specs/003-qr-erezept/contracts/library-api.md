# Contract changes: Java Library API (converter-core)

Changes to [feature 001 library API](../../001-chmed16a-ch-emed-conversion/contracts/library-api.md).
Additive, so a MINOR version change. Existing calls compile and behave as before.

Feature 004 adds to these: output form `TREATMENT_PLANS` and the options of
[specs/004-ch-emed-epr/contracts/library-api.md](../../004-ch-emed-epr/contracts/library-api.md).

## Request / options

```java
public enum OutputForm { DOCUMENT, QR_PAYLOAD, QR_IMAGE }

ConversionOptions.defaults().outputForm()      // DOCUMENT
options.withOutputForm(OutputForm.QR_IMAGE)
```

`ConversionRequest.input` may now hold binary data: a PNG or JPEG image when the source is
`CHMED16A`. All other inputs are UTF-8 text, as before.

## Result

```java
public record SignatureData(String identity, String timestamp, String signature) {
  public boolean verified() { return false; }
}

public record ConversionResult(
    Outcome outcome,
    Optional<byte[]> output,
    ConversionReport report,
    Optional<SignatureData> signature) {
  public ConversionResult(Outcome outcome, Optional<byte[]> output, ConversionReport report) { … } // signature empty
}
```

The output bytes depend on the target format and the output form:

| Target | `outputForm` | `output` bytes |
|--------|--------------|----------------|
| `CH_EMED` | `DOCUMENT` | FHIR JSON (UTF-8) |
| `CHMED16A` | `DOCUMENT` | CHMED16A JSON (UTF-8) |
| `CHMED16A` | `QR_PAYLOAD` | `CHMED16A1…` (ASCII) |
| `CHMED16A` | `QR_IMAGE` | PNG (Medication Card only) |

## Capabilities

```java
public record FormatInfo(FormatId format, List<String> inputForms, List<OutputForm> outputForms) {}

List<FormatInfo> Converter.formats();   // sorted by format id
```

## Behavioural contract (additions)

1. **Input forms:** For source `CHMED16A`, the input form is detected by content
   ([data model §1](../data-model.md)). The same plan as JSON, payload, eRezept URL or image
   gives the same document and report, apart from the entries that belong to the form, such as
   `SIGNATURE_NOT_VERIFIED`. This holds with the same options (FR-002, FR-019).
2. **Signature data:** `signature` is present iff the input is a signed eRezept and the outcome is
   not `FAILED`. Its values are the raw URL parameters, and `verified()` is always `false`. The
   document and the report never contain them (FR-004).
3. **Determinism:** `QR_PAYLOAD` and `QR_IMAGE` outputs are byte-identical for identical input
   and options (FR-011).
4. **Round trip:** Decoding a `QR_PAYLOAD` output gives exactly the `DOCUMENT` output of the same
   request (FR-012). Reading a `QR_IMAGE` output gives that payload.
5. **Never throws** for invalid images, URLs or output forms. These are reported as `FAILED` with
   the reason codes of [data model §4](../data-model.md).
6. **Offline:** No network access and no temp files.

## Format SPI (additions)

```java
default List<String> inputForms() { return List.of("json"); }
default List<OutputForm> outputForms() { return List.of(OutputForm.DOCUMENT); }

// MappingContext
void setSignature(SignatureData signature);   // at most once per conversion
```

`fromChEmed` reads `ctx.options().outputForm()`. `DefaultConverter` refuses an output form that
the target module does not list (`UNSUPPORTED_OUTPUT_FORM`) before the conversion starts.
