# Contract changes: REST API (converter-rest)

Changes to [openapi.yaml](../../002-rest-conversion-service/contracts/openapi.yaml), version
`1.1.0` → `1.2.0`. All changes are additive, and existing requests and responses stay valid.

## Routes

No new routes. `POST /api/v1/conversions/{from}/{to}` accepts `ch-emed-epr` for `from` and `to`:

| Route | Use |
|-------|-----|
| `/api/v1/conversions/ch-emed-epr/ch-emed` | PMLC/PRE/MTP → CH EMED |
| `/api/v1/conversions/ch-emed/ch-emed-epr` | CH EMED → PMLC/PRE/MTP, or with `treatment-plans` → collection of MTP documents |
| `/api/v1/conversions/chmed16a/ch-emed-epr` and reverse | `404` problem `UNSUPPORTED_CONVERSION` with `supportedConversions`; detail names the path through `ch-emed` |

## Schema changes

- `RequestOptions.outputForm`: enum `[document, qr-payload, qr-image, treatment-plans]`;
  description: "`treatment-plans` only for target `ch-emed-epr` and a Medication Card".
- `RequestOptions.treatmentPlanReferences` (new, optional): array of `{documentId, statementId}`,
  both strings with pattern `^urn:uuid:[0-9a-fA-F-]{36}$`; description per research R15.
- `RequestOptions.originalRepresentation` (new, optional): string, `format: byte`, a PDF/A in
  base64; description per research R16, including that it is not used with `treatment-plans`.
- `CapabilitiesResponse.formats[].outputForms.items`: same enum.
- Examples of `from`/`to` and `formats` include `ch-emed-epr`.
- The output of `ch-emed-epr` and `treatment-plans` is FHIR JSON with media type
  `application/fhir+json`, base64-encoded as all documents.
- Report codes `NO_TREATMENTS`, `TREATMENT_PLAN_EXISTS` and `TREATMENT_PLAN_REFERENCES_MISMATCH`
  are listed in the report description.

## Errors

| Case | Status | `errorType` |
|------|--------|-------------|
| direct pair with `chmed16a` | 404 | `UNSUPPORTED_CONVERSION` (existing) |
| `treatment-plans` with another target | 400 | `MALFORMED_REQUEST`, as for the other forms (existing check) |
| malformed `treatmentPlanReferences` (not `urn:uuid:`) | 400 | `MALFORMED_REQUEST` |
| EPR input not parseable or failing input validation, unsupported document type | 422 | `INVALID_INPUT`, with report (existing) |
| missing required data, wrong number of treatment-plan references, no treatments, output validation error, strict-mode loss | 422 | `CONVERSION_FAILED`, with report (existing) |
