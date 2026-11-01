# eRezept Schweiz CHMED16A prescription schema

`erezept-chmed16a-schemav7.json` is the JSON schema published by the E-Rezept Schweiz Service
(developer documentation 0.10.2, <https://developers.e-rezept.ch/erezept/schema/schemav7.json>,
downloaded 2026-10-20). One change was needed to make it parseable as JSON: the duplicated
`"required"` key and the trailing comma in the top-level `"then"` were merged into
`"required": ["HcOrg", "HcPerson"]` (same meaning). It is used as test cross-check of the produced
prescriptions (task T094, `ErezeptSchemaCrossCheckTest`).
