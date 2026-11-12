# Contract changes: REST API (converter-rest)

These are changes to [openapi.yaml](../../002-rest-conversion-service/contracts/openapi.yaml),
version `1.2.0` → `1.2.1`. They change descriptions and examples only; requests and responses are
unchanged.

## Routes

There are no new routes. The routes `/api/v1/conversions/ch-emed-epr/ch-emed` and
`/api/v1/conversions/ch-emed/ch-emed-epr` now also accept dispenses, pharmaceutical advice and
medication lists.

## Description changes

- `CapabilitiesResponse.formats[].documentTypes`:
  - description: "… e.g. PMLC, PRE, MTP, DIS, PADV, PML …";
  - example: `[[PMLC, PRE, MTP, DIS, PADV, PML]]`.
- `RequestOptions.treatmentPlanReferences`: the description names the dispense of a DIS and the
  changed prescription of a PADV as further uses (research F1).
- The capabilities option text of `treatmentPlanReferences` (`CapabilitiesController`) is changed
  the same way.
- Report entries may carry the new reason code `REFERENCE_INCONSISTENT` (WARNING). The report
  schema lists codes as free strings, so the schema does not change.
