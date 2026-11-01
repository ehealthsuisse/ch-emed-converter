#!/usr/bin/env bash
# Cross-check of constitution principle I: validates every CH EMED document produced by the
# integration tests with the official HL7 validator (validator_cli.jar) against the vendored CH EMED
# packages, offline (no terminology server). Expects "Errors: 0".
#
# Usage:
#   ./mvnw verify                      # writes format-chmed16a/target/it-outputs/*.json
#   tools/validate-outputs.sh          # downloads tools/validator_cli.jar once if missing
#
# Environment: JAVA (default java), VALIDATOR_URL (default: latest release on GitHub).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${SCRIPT_DIR}/.."
JAR="${SCRIPT_DIR}/validator_cli.jar"
OUTPUTS="${ROOT}/format-chmed16a/target/it-outputs"
PACKAGES="${ROOT}/converter-validation/src/main/resources/packages"
JAVA="${JAVA:-${JAVA_HOME:+${JAVA_HOME}/bin/}java}"
URL="${VALIDATOR_URL:-https://github.com/hapifhir/org.hl7.fhir.core/releases/latest/download/validator_cli.jar}"

if [[ ! -d "${OUTPUTS}" ]] || ! ls "${OUTPUTS}"/*.json >/dev/null 2>&1; then
  echo "No outputs in ${OUTPUTS}; run ./mvnw verify first." >&2
  exit 2
fi
if [[ ! -f "${JAR}" ]]; then
  echo "Downloading validator_cli.jar (once, ignored by git)..." >&2
  curl -fsSL -o "${JAR}" "${URL}"
fi

# vendored packages as -ig (CH EMED and its dependency closure that the manifest loads)
ig_args=()
while IFS= read -r file; do
  ig_args+=(-ig "${PACKAGES}/${file}")
done < <(python3 - "${PACKAGES}/manifest.json" <<'PY'
import json, sys
for p in json.load(open(sys.argv[1]))["packages"]:
    if p.get("load", True):
        print(p["file"])
PY
)

# one invocation for all documents: loading the packages dominates the run time
out="$("${JAVA}" -jar "${JAR}" "${OUTPUTS}"/*.json -version 4.0.1 -tx n/a "${ig_args[@]}" 2>&1 || true)"
count="$(ls "${OUTPUTS}"/*.json | wc -l)"
if grep -Eq "^Success: 0 errors" <<<"${out}"; then
  echo "OK: ${count} documents, $(grep -E '^Success:' <<<"${out}")"
  exit 0
fi
echo "ERROR: the official validator reports errors"
grep -E "^(Failure|Success):|Error @" <<<"${out}" | head -20 | sed 's/^/  /'
exit 1
