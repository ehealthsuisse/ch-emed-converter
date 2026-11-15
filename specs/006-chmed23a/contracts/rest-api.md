# Contract changes: REST API (converter-rest)

These are changes to [openapi.yaml](../../002-rest-conversion-service/contracts/openapi.yaml),
version `1.2.1` → `1.3.0` (minor: a new optional property). Existing requests and responses stay
valid.

## Routes

There are no new route templates. The existing route `/api/v1/conversions/{from}/{to}` serves two
new pairs:
- `/api/v1/conversions/chmed23a/ch-emed`: the Base64 `document` holds CHMED23A JSON, a payload,
  chunk payloads as one text, or a PNG/JPEG image. The library detects the form;
- `/api/v1/conversions/ch-emed/chmed23a`: `outputForm` `document`, `qr-payload` or `qr-image`.

`/api/v1/conversions/chmed23a/chmed16a` and the other direct pairs answer like any unsupported pair
(404, `UNSUPPORTED_CONVERSION`, detail names the path through `ch-emed`).

## Schema changes

- `requestBody` description of `/conversions/{from}/{to}`: the list of inputs adds "CHMED23A JSON,
  CHMED23A QR payload text `CHMED23A.…` (also as several chunk payloads), PNG or JPEG image".

- `CapabilitiesResponse.formats[]`: a new optional property `deprecated` (boolean). It is `true` for
  `chmed23a` and absent for the other formats. Its description reads "Withdrawn by the publisher of
  the format; still converted. For chmed23a: IG eMediplan strategy 2025-12-02."
- `FormatName.examples`: `[chmed16a, chmed23a, ch-emed, ch-emed-epr]`.
- `RequestOptions.outputForm` description: "qr-payload and qr-image only for targets chmed16a and
  chmed23a; qr-image not for chmed16a prescriptions." The same text goes into the capabilities
  option list (`CapabilitiesController`).
- Report entries may carry the new reason code `CHUNK_INVALID`. The report schema lists codes as
  free strings, so it does not change.

## Example (capabilities, excerpt)

```json
{
  "name": "chmed23a",
  "inputForms": ["json", "qr-payload", "qr-image"],
  "outputForms": ["document", "qr-payload", "qr-image"],
  "documentTypes": ["MP", "Rx"],
  "deprecated": true
}
```
