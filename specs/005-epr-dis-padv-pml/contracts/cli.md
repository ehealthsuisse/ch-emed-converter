# Contract changes: Command-Line Interface (converter-cli)

These are changes to the [feature 004 CLI contract](../../004-ch-emed-epr/contracts/cli.md). There
are no new options or values; streams and exit codes are unchanged.

| Item | Change |
|------|--------|
| `--from ch-emed-epr`, `--to ch-emed-epr` | also accept DIS, PADV and PML documents (data-model §1) |
| `--treatment-plan <documentId>,<statementId>` | also for the dispense of a DIS without treatment-plan reference (exactly one); help text names it |
| `formats` | line `ch-emed-epr: …; document types PMLC, PRE, MTP, DIS, PADV, PML` |

## Examples

```bash
# Dispense read from the EPR
java -jar ch-emed-converter.jar convert --from ch-emed-epr --to ch-emed dis.json -o dispense.json

# CH EMED dispense without treatment-plan reference to the EPR
java -jar ch-emed-converter.jar convert --from ch-emed --to ch-emed-epr dispense.json -o dis.json \
  --treatment-plan urn:uuid:<documentId>,urn:uuid:<statementId>
```
