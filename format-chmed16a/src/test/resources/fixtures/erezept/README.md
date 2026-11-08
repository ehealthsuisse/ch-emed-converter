# Synthetic signed eRezept fixtures (feature 003, US2)

All values are invented (constitution III and IV). The prescriptions come from the synthetic
fixtures in `../chmed16a/`. The signature parameters are fixed values that nothing verifies:

| Parameter | Value |
|-----------|-------|
| `i` (identity) | `Erika+Muster+%28HIN%7Cemust1%29` (URL-encoded, as in the eRezept examples) |
| `t` (timestamp) | `1767225600` |
| `s` (signature) | `3066023100` followed by 43 × `ab` (hex) |

Payloads are `CHMED16A1` + Base64 of `gzip -n` of the JSON file. They were generated once in Git
Bash:

```bash
p() { printf 'CHMED16A1%s' "$(gzip -n -c "$1" | base64 -w0)"; }
printf 'https://eprescription.hin.ch/#%s&i=%s&t=%s&s=%s\n' "$(p ../chmed16a/rx-basic.json)" "$I" "$T" "$S" > rx-signed.url.txt
```

| File | Content |
|------|---------|
| `rx-signed.url.txt` | `rx-basic.json`, all parameters |
| `rx-signed.png` | QR code of `rx-signed.url.txt` (BoofCV, `QrFixtureGenerator`) |
| `rx-signed-erezept-example.url.txt` | `rx-erezept-example.json` (revision 1, no `HcPerson`: needs the option `author`) |
| `rx-no-patient-id.json`, `rx-signed-no-patient-id.url.txt` | `rx-basic.json` without `Patient.Ids` (needs the option `patientIdentifier`) |
| `rx-missing-s.url.txt` | no `s` |
| `rx-duplicate-t.url.txt` | `t` twice |
| `rx-empty-i.url.txt` | `i` empty |
| `rx-unknown-param.url.txt` | additional `x=1` |
| `rx-query.url.txt` | query `?ref=1` before the fragment |
| `rx-reordered.url.txt` | order `s`, `i`, payload, `t` |
| `rx-malformed-sig.url.txt` | `t=yesterday`, `s=not-hex` |
| `rx-foreign-host.url.txt` | host `example.org` |
| `rx-no-payload.url.txt` | parameters only |
| `plan-signed.url.txt` | payload of a medication plan (`plan-basic.json`) instead of a prescription |
