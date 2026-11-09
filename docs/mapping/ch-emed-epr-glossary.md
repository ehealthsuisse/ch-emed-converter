# Glossary: CH EMED ↔ CH EMED EPR

Lookup of the names and abbreviations used in CH EMED 6.0.0 and CH EMED EPR 3.0.0 (CARA EMED).
Codes and profile names are taken from the pinned packages (see [pinned specifications](../specifications.md));
the mapping rules are in [ch-emed-epr.md](ch-emed-epr.md).

## Document types

| Abbreviation (CH EMED EPR) | CH EMED 6.0.0 document | German | Composition `type` (SNOMED CT) | Converter (feature 004) |
|---|---|---|---|---|
| **MTP** | Medication Treatment Plan | Behandlungsplan (one entry per document) | `761931002` | yes |
| **PRE** | Medication Prescription | Rezept | `761938008` | yes |
| **PMLC** | Medication Card | Medikationskarte (current state, produced by the PMP service) | `736378000` | yes |
| **DIS** | Medication Dispense | Abgabe | `82291000195104` | later |
| **PADV** | Pharmaceutical Advice | Pharmazeutische Empfehlung | `419891008` | later |
| **PML** | Medication List | Medikationsliste | `721912009` | later |

## Profiles

Each EPR profile maps to the profile named in its `baseDefinition` (M-EPR-01). `<type>` is one of
`medicationtreatmentplan`, `medicationprescription`, `medicationcard`, `medicationdispense`,
`pharmaceuticaladvice`, `medicationlist`.

| CH EMED EPR 3.0.0 | CH EMED 6.0.0 / CH Core 6.0.0 |
|---|---|
| `ch-emed-epr-document-<type>` | `ch-emed-document-<type>` |
| `ch-emed-epr-composition-<type>` | `ch-emed-composition-<type>` |
| `ch-emed-epr-medicationstatement-treatmentplan` | `ch-emed-medicationstatement` (MTP only) |
| `ch-emed-epr-medicationstatement-card`, `-list`, `-changed`, `-changed-list` | `ch-emed-medicationstatement-card`, `-list`, `-changed`, `-changed-list` |
| `ch-emed-epr-medicationrequest`, `-list`, `-changed`, `medication-request-changed-list` | CH EMED profiles of the same name |
| `ch-emed-epr-medicationdispense`, `-list` | `ch-emed-medicationdispense`, `-list` |
| `ch-emed-epr-medication`, `medication-medicationdispense` | CH EMED profiles of the same name |
| `ch-emed-epr-observation`, `-list` | `ch-emed-observation`, `-list` |
| `ch-emed-epr-patient` | `ch-core-patient-epr` |
| `ch-emed-epr-related-person` | `ch-core-relatedperson` |
| `ch-emed-epr-practitioner`, `-practitionerrole`, `-organization` | `ch-emed-practitioner`, `-practitionerrole`, `-organization` |
| `ch-emed-epr-device` | FHIR core `Device` (profile dropped towards CH EMED) |
| `ch-emed-epr-observation-body-weight` | FHIR core `Observation` (profile dropped towards CH EMED) |
| `ch-emed-epr-dosage`, `-dosage-split`, `-dosage-medicationrequest` | CH EMED dosage profiles (base entry, split entry) |

## Other terms

| Term | Meaning |
|---|---|
| **CARA EMED** | Common name for CH EMED EPR (`ch.fhir.ig.ch-emed-epr`, maintained by CARA) |
| **EPR / EPD** | Electronic Patient Record / Elektronisches Patientendossier |
| **PMP** | *Plan de médication partagé*: CARA's eMedication service; stores the documents, aggregates them and produces the PMLC, whose author (a Device) it is |
| **CHMED16A** | eMediplan format (JSON, compressed in the QR code) |
| **IG** | FHIR Implementation Guide |
| **IHE MHD** | Mobile access to Health Documents; transport into the EPR (out of scope) |
| **Original representation** | PDF of the document, section LOINC `55108-5`; PDF/A in the EPR |
| **treatmentPlan** | Extension `ch-emed-ext-treatmentplan`: reference to the MTP document and its MedicationStatement (required for PRE and PMLC entries) |
| **lastConsideredDocument** | Extension `ch-emed-ext-last-considered-document`: last document considered for a PMLC entry |
| **GLN** | Global Location Number of a healthcare professional (OID `2.51.1.3`) |
