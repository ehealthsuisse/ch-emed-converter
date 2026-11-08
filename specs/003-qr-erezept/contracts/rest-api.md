# Contract changes: REST API

Changes to [`specs/002-rest-conversion-service/contracts/openapi.yaml`](../../002-rest-conversion-service/contracts/openapi.yaml).
That file stays the single OpenAPI source: the build syncs it into the service and generates the
test client from it. The implementation applies the changes below to it and raises `info.version`
from `1.0.0` to `1.1.0`. All changes are additive, and existing requests and responses stay valid.

## Request

```yaml
ConversionOptions:
  properties:
    outputForm:
      type: string
      enum: [document, qr-payload, qr-image]
      default: document
      description: |
        Form of the output. qr-payload and qr-image only for target chmed16a; qr-image only for
        medication plans (a prescription is refused, it is output as unsigned payload only).
```

The `document` description now also names the new input forms: a PNG or JPEG image with one QR
code, and the eRezept URL text. Both are base64-encoded like every document.

## Success response

```yaml
ConversionResponse:
  properties:
    outputMediaType:
      examples: [application/fhir+json, application/json, text/plain, image/png]
    signature:
      $ref: '#/components/schemas/SignatureData'
SignatureData:
  type: object
  additionalProperties: false
  required: [identity, timestamp, signature, verified]
  description: |
    Signature data of a signed eRezept, returned unchanged and NOT verified (raw values of the URL
    parameters i, t, s). Present only for signed input.
  properties:
    identity: { type: string }
    timestamp: { type: string }
    signature: { type: string }
    verified: { type: boolean, const: false }
```

## Capabilities

```yaml
CapabilitiesResponse:
  required: [converterVersion, conversions, formats, options, specifications, limits]
  properties:
    formats:
      type: array
      items:
        type: object
        required: [name, inputForms, outputForms]
        properties:
          name: { $ref: '#/components/schemas/FormatName' }
          inputForms: { type: array, items: { type: string }, examples: [[json, qr-payload, erezept-url, qr-image]] }
          outputForms: { type: array, items: { type: string, enum: [document, qr-payload, qr-image] } }
    limits:
      required: [maxRequestSizeBytes, maxDocumentSizeBytes, timeoutSeconds, maxImagePixels]
      properties:
        maxImagePixels: { type: integer }
```

Adding `formats` and `maxImagePixels` to `required` is safe for clients, because the service
always sends them.

## Failures

The `ErrorType` enum is unchanged; the new cases map onto the existing types.

| Case | HTTP | `errorType` | Report |
|------|------|-------------|--------|
| `outputForm` other than `document` with target `ch-emed` | 400 | `MALFORMED_REQUEST` | no (no conversion attempted) |
| `IMAGE_UNREADABLE`, `IMAGE_TOO_LARGE`, `QR_NOT_FOUND`, `QR_MULTIPLE`, `EREZEPT_URL_INVALID` | 422 | `INVALID_INPUT` | yes |
| `QR_CAPACITY_EXCEEDED`, `QR_IMAGE_NOT_ALLOWED` | 422 | `CONVERSION_FAILED` | yes |
| decoded image above `max-document-size` | 413 | `REQUEST_TOO_LARGE` | no |

Problem details and logs never contain the image, payload, URL or signature values (FR-016).
