# Synthetic QR image fixtures (feature 003)

All images hold synthetic data only: the payloads come from the synthetic and official example
fixtures in `../chmed16a/` (constitution IV). They were generated once with BoofCV, an encoder
independent of the converter's ZXing codec, by `converter-qr/src/test/java/ch/emed/converter/qr/QrFixtureGenerator.java`:

```text
./mvnw -pl converter-qr test -Dtest=QrFixtureGenerator -Dqr.fixtures=generate
```

| File | Content |
|------|---------|
| `plan-basic.png`, `plan-basic.jpg` | payload of `chmed16a/plan-basic.qr.txt` (JPEG quality 85) |
| `readme-example.png` | payload of `chmed16a/official/readme-example-medication-plan.qr.txt` |
| `plan-non-ascii.png` | `CHMED16A1` + Base64 GZip of `chmed16a/plan-non-ascii.json` |
| `plan-non-ascii-plain.png` | `CHMED16A0` + the plain UTF-8 bytes of `chmed16a/plan-non-ascii.json` (umlauts in byte mode, no ECI) |
| `chmed23a.png` | `CHMED23A1` + Base64 GZip of `chmed16a/chmed23a-plan.json` (unsupported version) |
| `two-codes.png` | `plan-basic.png` and `readme-example.png` side by side |
| `no-code.png` | plain grey image |
| `url-code.png` | QR code holding `https://example.org/` |
| `not-an-image.gif` | `plan-basic` as GIF (unsupported image format) |

Error correction M, 4 px per module, quiet zone 4 modules, byte mode without ECI. `qrencode`, named
in tasks.md T020, was not available on the build machines; BoofCV serves the same purpose as an
independent encoder.
