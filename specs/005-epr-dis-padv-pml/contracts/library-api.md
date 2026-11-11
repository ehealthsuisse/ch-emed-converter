# Contract changes: Library API (converter-core)

These are additions to the [feature 004 library contract](../../004-ch-emed-epr/contracts/library-api.md).
Everything is additive: no new type, option or constructor.

## New values

```java
ReasonCode.REFERENCE_INCONSISTENT   // WARNING; references within one document contradict each other (research R5)
```

## Changed behaviour

| Item | Before | Now |
|------|--------|-----|
| `Converter.formats()`, entry `ch-emed-epr` | `documentTypes()` = `[PMLC, PRE, MTP]` | `[PMLC, PRE, MTP, DIS, PADV, PML]` |
| `ch-emed ↔ ch-emed-epr` with a dispense, pharmaceutical advice or medication list | `FAILED`, `UNSUPPORTED_DOCUMENT_TYPE` | converted (data-model §1) |
| `ConversionOptions.treatmentPlanReferences()` | prescribed medications of a PRE and card entries without reference | also the dispense of a DIS and the changed MedicationRequest of a PADV without reference (research F1) |
| `ch-emed → chmed16a` with a dispense, pharmaceutical advice or medication list | `UNSUPPORTED_DOCUMENT_TYPE` "Document type … is not supported" | same code, message also names the supported types (research R7) |
| `ch-emed → ch-emed-epr`, full URLs | kept | full URLs that are not `urn:uuid` become `urn:uuid`, references follow, `DERIVED_VALUE` per entry (rule M-EPR-05, research F5); also for PMLC, PRE and MTP |
