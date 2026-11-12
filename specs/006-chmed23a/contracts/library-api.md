# Contract changes: Library API (converter-core)

These are additions to the [feature 004 library contract](../../004-ch-emed-epr/contracts/library-api.md)
and its changes in [feature 005](../../005-epr-dis-padv-pml/contracts/library-api.md). All additions
are backward compatible: existing constructors and defaults stay.

## New values and members

```java
FormatId.CHMED23A                       // external name "chmed23a"
ReasonCode.CHUNK_INVALID                // ERROR; chunked payload incomplete or inconsistent (research R5)

// FormatModule (SPI)
default boolean deprecated() { return false; }   // true for chmed23a (research R12)

// FormatInfo: new component; the existing constructors pass false
public record FormatInfo(
    FormatId format,
    List<String> inputForms,
    List<OutputForm> outputForms,
    List<String> documentTypes,
    boolean deprecated) { … }
```

## Moved types

These classes move from `format-chmed16a` to `converter-core`, package `ch.emed.converter.core.chemed`
(research R2): `ChEmedDocumentReader` (with `DocumentType`, `ChEmedDocument`), `Profiles`, `Labels`,
`Narratives`, the unit table (`Units`) and `MappingRule`. They were not part of the documented API.
Behaviour is unchanged, and CHMED16A output stays byte-identical.

## Changed behaviour

| Item | Before | Now |
|------|--------|-----|
| `Converter.formats()` | `chmed16a`, `ch-emed`, `ch-emed-epr` | also `chmed23a`: input forms `json, qr-payload, qr-image`, output forms `DOCUMENT, QR_PAYLOAD, QR_IMAGE`, document types `MP, Rx`, `deprecated` = `true` |
| `Converter.supportedConversions()` | – | also `chmed23a → ch-emed` and `ch-emed → chmed23a` |
| `chmed23a ↔ chmed16a`, `chmed23a ↔ ch-emed-epr` | `UNSUPPORTED_CONVERSION` (unknown format) | `UNSUPPORTED_CONVERSION`, message names the path through `ch-emed` |
| `chmed16a → ch-emed` with CHMED23A input | `UNSUPPORTED_VERSION` | same code, message names the source `chmed23a` (research R3) |

## Usage

```java
Converter converter = Converters.create();
ConversionResult card =
    converter.convert(new ConversionRequest(
        FormatId.CHMED23A, FormatId.CH_EMED, payloadBytes, ConversionOptions.defaults()));

ConversionResult image =
    converter.convert(new ConversionRequest(
        FormatId.CH_EMED, FormatId.CHMED23A, cardJson,
        ConversionOptions.defaults().withOutputForm(OutputForm.QR_IMAGE)));
```

`Converters.create()`, `ConversionOptions.defaults()` and `withOutputForm` are the
existing methods of the feature 001 and 003 contracts.
