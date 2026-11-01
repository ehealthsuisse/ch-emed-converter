# Synthetic CHMED16A fixtures

All persons, GLNs and insurance card numbers are fictitious (constitution IV). Official examples
are in `official/`.

QR payload fixtures were generated once from the JSON fixtures (compact JSON, GZip with mtime 0,
Base64, prefix `CHMED16A1`):

```text
python3 -c "import gzip,base64,json;d=json.dumps(json.load(open('plan-basic.json')),ensure_ascii=False,separators=(',',':')).encode();print('CHMED16A1'+base64.b64encode(gzip.compress(d,mtime=0)).decode())" > plan-basic.qr.txt
```

`qr-oversized.txt` decompresses to more than 5 MB; `erezept-url.txt` is a synthetic eRezept URL
with fake identity/timestamp/signature parameters.
