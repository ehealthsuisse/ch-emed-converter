"""Writes manifest.json for the vendored FHIR packages (called by fetch-packages.sh).

Packages are listed in the breadth-first order in which they were resolved from the root
package. The first version of a package name encountered (the one declared closest to CH EMED)
gets "load": true; other versions are vendored for completeness but not loaded into the
validator, because HAPI keeps a single definition per canonical URL.
"""

import hashlib
import json
import os
import sys


def main(target: str, order_file: str, root: str) -> None:
    entries = []
    loaded = set()
    with open(order_file, encoding="utf-8") as order:
        for line in order:
            name, version = line.split()
            file_name = f"{name}-{version}.tgz"
            with open(os.path.join(target, file_name), "rb") as package:
                sha256 = hashlib.sha256(package.read()).hexdigest()
            entries.append(
                {
                    "name": name,
                    "version": version,
                    "file": file_name,
                    "sha256": sha256,
                    "load": name not in loaded,
                }
            )
            loaded.add(name)
    with open(os.path.join(target, "manifest.json"), "w", encoding="utf-8") as out:
        json.dump({"root": root, "packages": entries}, out, indent=2)
        out.write("\n")
    print(f"Wrote manifest with {len(entries)} packages")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
