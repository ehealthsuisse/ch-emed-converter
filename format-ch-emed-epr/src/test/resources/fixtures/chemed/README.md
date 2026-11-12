# CH EMED examples

`Bundle-1-1-MedicationTreatmentPlan.json` is copied unchanged from `package/example/` of the
vendored package `ch.fhir.ig.ch-emed#6.0.0`
(`converter-validation/src/main/resources/packages/ch.fhir.ig.ch-emed-6.0.0.tgz`, CC0-1.0). It is
officially published example data with a fictitious patient (constitution IV). Used for the
treatment plan round trip CH EMED → MTP → CH EMED (spec US3.4, task T044).

Dispenses `Bundle-1-2`, `Bundle-2-4`, `Bundle-3-1` and `Bundle-3-2-MedicationDispense.json`,
pharmaceutical advice `Bundle-2-2-PharmaceuticalAdvice.json` and
`Bundle-PharmaceuticalAdvice-ChangeDosage.json`, and the medication list
`Bundle-2-1-MedicationList.json` come from the same package, unchanged. Feature 004 refused them
(task T072); since feature 005 they are inputs of the conversion to CH EMED EPR (research R8). The
dispenses 3-1 and 3-2 have no treatment-plan reference and need the option
`treatmentPlanReferences`; the list 2-1 has a Device as author and can become a PML.
