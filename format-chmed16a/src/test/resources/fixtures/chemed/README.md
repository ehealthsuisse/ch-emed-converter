# Official CH EMED example instances

Extracted unchanged from the vendored package `ch.fhir.ig.ch-emed#6.0.0`
(`converter-validation/src/main/resources/packages/ch.fhir.ig.ch-emed-6.0.0.tgz`):

| Fixture | Source file in package |
|---------|------------------------|
| `medication-card-example.json` | `package/example/Bundle-2-7-MedicationCard.json` |
| `medication-prescription-example.json` | `package/example/Bundle-2-6-MedicationPrescription.json` |

Official example data (constitution IV); the persons are fictitious.

| Fixture | Origin |
|---------|--------|
| `medication-card-example-uuid-fullurl.json` | `package/example/Bundle-2-7-MedicationCard-UUIDfullUrl.json`, unchanged (references resolved via `urn:uuid` fullUrl) |
| `card-unsupported-type.json` | `package/example/Bundle-2-3-MedicationTreatmentPlan.json`, unchanged (document type outside the supported set, FR-007) |

Derived fixtures (synthetic modifications of `medication-card-example.json`; statements are clones
of `2-7-MedStatNorvasc` with new ids; the persons stay fictitious):

| Fixture | Modification |
|---------|--------------|
| `card-inactive-medication.json` | four additional statements with status `stopped`, `on-hold`, `not-taken`, `entered-in-error` |
| `card-complex-timing.json` | three additional statements: `dayOfWeek` timing, `doseRange`, free-text-only dosage |
| `card-epr-elements.json` | document confidentiality `R` (restricted) with the CH Core EPR confidentiality extension |
| `card-invalid-profile.json` | required `Composition.subject` removed (profile violation) |
