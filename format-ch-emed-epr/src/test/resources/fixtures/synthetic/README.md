# Synthetic fixtures (feature 005)

Derived from official examples for single cases; no real patient data (constitution IV).

| File | Derived from | Change | Used for |
|------|--------------|--------|----------|
| `Bundle-UnknownDocumentType.json` | `official/Bundle-DocumentDis1.json` | SNOMED CT code of the Composition type set to `371531000` (report of clinical encounter) | a document type outside the converter's scope is refused (spec edge case 1, task T006) |
