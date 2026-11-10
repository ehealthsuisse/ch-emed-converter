# CH EMED examples

`Bundle-1-1-MedicationTreatmentPlan.json` is copied unchanged from `package/example/` of the
vendored package `ch.fhir.ig.ch-emed#6.0.0`
(`converter-validation/src/main/resources/packages/ch.fhir.ig.ch-emed-6.0.0.tgz`, CC0-1.0). It is
officially published example data with a fictitious patient (constitution IV). Used for the
treatment plan round trip CH EMED → MTP → CH EMED (spec US3.4, task T044).

`Bundle-1-2-MedicationDispense.json`, `Bundle-2-2-PharmaceuticalAdvice.json` and
`Bundle-2-1-MedicationList.json` come from the same package; they are document types outside
feature 004 and must be refused (spec edge case 1, task T072).
