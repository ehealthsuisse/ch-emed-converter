# Quickstart run (task T120)

Run on 2026-10-20 against `converter-cli/target/ch-emed-converter.jar` built by `./mvnw verify`
(JDK 21.0.12, WSL2 Linux). Scenarios follow [../quickstart.md](../quickstart.md).

| # | Scenario | Result | Notes |
|---|----------|--------|-------|
| – | `./mvnw verify` | **pass** | BUILD SUCCESS: 32 core, 12 validation, 225 unit + 91 integration tests in format-chmed16a, 14 CLI tests; Spotless and licence report ran |
| 1 | CHMED16A plan → CH EMED (`plan-basic.json`) | **pass** | exit 0, `Bundle` of type `document`, report `SUCCESS_WITH_WARNINGS` (16 entries, validator warning for the synthetic GLN). Without `--author`: exit 1, `MISSING_REQUIRED_DATA` (FR-023) — quickstart text corrected to pass `--author` |
| 2 | CH EMED → CHMED16A (official Medication Card) | **pass** | exit 0, MedType 1, 2 medicaments, 2 WARNINGs (non-VeKa patient identifier, …) all documented gaps |
| 3 | Prescription and eRezept readiness | **pass with correction** | official Medication Prescription → exit 0, MedType 3, `rev` 3, `Auth` = GLN, `HcPerson`/`HcOrg` present, no `EREZEPT_REQUIREMENT`. A copy without the prescriber GLN fails with `FHIR_VALIDATION` (CH EMED makes the GLN mandatory) instead of `EREZEPT_REQUIREMENT`; the eRezept error is covered by `PrescriptionIT` for callers that skip input validation — quickstart text corrected |
| 4 | Strict mode (`plan-unmappable-element.json`) | **pass** | lenient: exit 0, `SUCCESS_WITH_WARNINGS`, `/Medicaments/1/Roa` with `clinicalLoss: true`; `--strict`: exit 1, no output, same entry |
| 5 | Input forms, invalid and unsupported input | **pass** | QR payload output byte-identical to the JSON one; corrupt Base64/GZip, oversized payload, eRezept URL, invalid JSON, CHMED23A, MedType 2 → exit 1 with `PARSE_ERROR`/`UNSUPPORTED_VERSION`/`UNSUPPORTED_DOCUMENT_TYPE`; `rx-missing-hcperson.json` → exit 0 with `CHMED_REVISION_FIELD_MISSING` |
| 6 | Round trip and determinism | **pass** | `RoundTripIT` (24 round trips) and `DeterminismIT` green; all differences explained by `capability-gaps.json` |
| 7 | Privacy | **pass** | `PhiLeakTest`: zero findings over the whole corpus in both directions (found and fixed: third-party DEBUG logging, see mapping specification "Logging") |
| 8 | Performance (SC-005a/b) | **pass** | SC-005a: conversion of 100 medicaments 16–30 ms (CHMED16A → CH EMED) and ~100 ms (CH EMED → CHMED16A), limit 2 s. SC-005b: official validation of the result ~9–10 s warm, limit 15 s; cold start ~30–40 s, reported separately. SC-005 was split on 2026-10-21 because a joint 2 s limit is out of reach: the HL7 `ValidationEngine` (7.7–9.6 s warm) and parallel validation of sub-documents (~6.5 s, validator scales ~1.3×) were tried and rejected; the cost is linear, ~80 ms per MedicationStatement plus ~0.6 s fixed |
| X | Cross-check with the official validator | **pass** | `tools/validate-outputs.sh`: 17 integration-test outputs, `Success: 0 errors` with `validator_cli.jar` (latest release), CH EMED 6.0.0 packages, `-tx n/a` |

## Open points found while running

- The mapping specification still needs the review of a person with CH EMED / eMediplan domain
  knowledge (T118); interpretation points are marked "domain review" in the specification.
