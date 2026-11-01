#!/usr/bin/env bash
# Downloads the pinned CH EMED IG package and its full dependency closure from the FHIR package
# registry into converter-validation/src/main/resources/packages/ and writes manifest.json
# (name, version, sha256, load flag per package).
#
# This is the ONLY step of the build that needs network access (research R2/R4). Run it once
# when the pinned versions in docs/specifications.md change, review the result and commit it.
#
# hl7.fhir.r4.core is skipped: HAPI's DefaultProfileValidationSupport already ships the R4 core
# definitions.
set -euo pipefail

ROOT_PACKAGE="${1:-ch.fhir.ig.ch-emed#6.0.0}"
REGISTRY="${FHIR_REGISTRY:-https://packages.fhir.org}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${SCRIPT_DIR}/../converter-validation/src/main/resources/packages"

mkdir -p "${TARGET}"
work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT
: > "${work}/order.txt"

queue=("${ROOT_PACKAGE}")
declare -A seen=()

while ((${#queue[@]} > 0)); do
  spec="${queue[0]}"
  queue=("${queue[@]:1}")
  [[ -n "${seen[${spec}]:-}" ]] && continue
  seen["${spec}"]=1

  name="${spec%%#*}"
  version="${spec##*#}"
  if [[ "${name}" == "hl7.fhir.r4.core" ]]; then
    continue
  fi
  # Wildcard dependency versions (e.g. "3.3.x") are resolved once to the highest matching
  # published version; the resolved version is what gets vendored and recorded in manifest.json.
  if [[ "${version}" == *x ]]; then
    prefix="${version%x}"
    version="$(curl -fsSL "${REGISTRY}/${name}" | python3 -c '
import json, sys
prefix = sys.argv[1]
vs = [v for v in json.load(sys.stdin)["versions"] if v.startswith(prefix) and "-" not in v]
key = lambda v: [int(p) if p.isdigit() else 0 for p in v.split(".")]
print(sorted(vs, key=key)[-1])' "${prefix}")"
    echo "Resolved ${spec} -> ${name}#${version}"
    [[ -n "${seen[${name}#${version}]:-}" ]] && continue
    seen["${name}#${version}"]=1
  fi

  file="${TARGET}/${name}-${version}.tgz"
  echo "Fetching ${name}#${version}"
  curl -fsSL "${REGISTRY}/${name}/${version}" -o "${file}"
  echo "${name} ${version}" >> "${work}/order.txt"

  mkdir -p "${work}/${name}-${version}"
  tar xzf "${file}" -C "${work}/${name}-${version}" package/package.json
  deps="$(python3 -c 'import json,sys; d=json.load(open(sys.argv[1])).get("dependencies") or {}; print(" ".join(f"{k}#{v}" for k,v in sorted(d.items())))' "${work}/${name}-${version}/package/package.json")"
  for dep in ${deps}; do
    queue+=("${dep}")
  done
done

python3 "${SCRIPT_DIR}/write-package-manifest.py" "${TARGET}" "${work}/order.txt" "${ROOT_PACKAGE}"
