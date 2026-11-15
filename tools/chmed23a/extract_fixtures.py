"""Extracts CHMED23A test fixtures and code tables from the pinned specification (feature 006).

Run once from the repository root with network access:

    python -I tools/chmed23a/extract_fixtures.py

Writes (UTF-8, LF):
- format-chmed23a/src/test/resources/fixtures/chmed23a/posology/*.json (research R13.2)
- format-chmed23a/src/main/resources/chmed23a/terminology/*.csv (research R7)
- format-chmed23a/src/test/resources/fixtures/chmed23a/terminology.md (copy for TerminologyTest)
"""

import io
import json
import os
import re
import urllib.request

COMMIT = "0b579aa6f3aff10a017498db57048c6504091653"
BASE = "https://raw.githubusercontent.com/ig-emediplan/specification/" + COMMIT + "/chmed23a/"
MODULE = "format-chmed23a/src"
POSOLOGY_DIR = MODULE + "/test/resources/fixtures/chmed23a/posology"
TERMINOLOGY_DIR = MODULE + "/main/resources/chmed23a/terminology"


def fetch(name):
    with urllib.request.urlopen(BASE + name) as response:
        return response.read().decode("utf-8")


def strip_comments(text):
    """Removes // and /* */ comments outside JSON strings (json5 blocks of posology.md)."""
    out, i, in_string = [], 0, False
    while i < len(text):
        c = text[i]
        if in_string:
            out.append(c)
            if c == "\\":
                out.append(text[i + 1])
                i += 1
            elif c == '"':
                in_string = False
        elif c == '"':
            in_string = True
            out.append(c)
        elif text.startswith("//", i):
            while i < len(text) and text[i] != "\n":
                i += 1
            continue
        elif text.startswith("/*", i):
            i = text.index("*/", i) + 2
            continue
        else:
            out.append(c)
        i += 1
    return "".join(out)


def carrier(po):
    """Minimal medication plan around one posology (patient of the README example)."""
    return {
        "patient": {
            "fName": "Dora",
            "lName": "Graber",
            "bdt": "1954-12-01",
            "gender": 2,
            "lng": "de",
            "ids": [{"type": 1, "val": "80756015090002647590"}],
        },
        "meds": [{"id": "Med1", "idType": 1, "pos": [po], "autoMed": False}],
        "medType": 1,
        "auth": 2,
        "dt": "2024-01-09T09:14:36+01:00",
    }


def posology(unit_po, **attributes):
    pos = {"po": unit_po, "unit": "Stk"}
    pos.update(attributes)
    return pos


def cyclic(unit, tdo):
    return {"t": 4, "cyDuU": unit, "cyDu": 1, "tdo": tdo, "tdpc": 1}


# Blocks of posology.md in document order, with the object they illustrate and how it is wrapped.
WRAPPERS = [
    ("Posology-1", lambda b: posology({"t": 1, "ds": [1, 0, 1, 0]}, **{k: v for k, v in b.items() if k != "po"})),
    ("Daily-1", lambda b: posology(b)),
    ("FreeText-1", lambda b: posology(b)),
    ("Single-1", lambda b: posology(b)),
    ("Cyclic-1", lambda b: posology(b)),
    ("Sequence-1", lambda b: posology(b)),
    ("DosageOnly-1", lambda b: posology(cyclic(4, b))),
    ("Times-1", lambda b: posology(cyclic(4, b))),
    ("DaySegments-1", lambda b: posology(cyclic(4, b))),
    ("WeekDays-1", lambda b: posology(cyclic(5, b))),
    ("DaysOfMonth-1", lambda b: posology(cyclic(6, b))),
    ("Interval-1", lambda b: posology(cyclic(4, b))),
    ("DosageSimple-1", lambda b: posology(cyclic(4, {"t": 1, "do": b}))),
    ("DosageFromTo-1", lambda b: posology(cyclic(4, {"t": 1, "do": b}))),
    ("DosageRange-1", lambda b: posology(cyclic(4, {"t": 1, "do": b}))),
    ("PosologySequence-1", lambda b: posology({"t": 5, "sos": [b]})),
    ("Pause-1", lambda b: posology({"t": 5, "sos": [
        {"t": 1, "po": {"t": 1, "ds": [1, 0, 0, 0]}, "duU": 4, "du": 21}, b]})),
    ("ApplicationAtTime-1", lambda b: posology(cyclic(4, {"t": 2, "ts": [b]}))),
    ("ApplicationInSegment-1", lambda b: posology(cyclic(4, {"t": 3, "ss": [b]}))),
]


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def extract_posology():
    text = fetch("posology.md")
    blocks = [(text[: m.start()].count("\n") + 1, m.group(1))
              for m in re.finditer(r"```json5\n(.*?)```", text, re.S)]
    if len(blocks) != len(WRAPPERS):
        raise SystemExit("posology.md has %d example blocks, expected %d" % (len(blocks), len(WRAPPERS)))
    rows = []
    for (line, block), (name, wrap) in zip(blocks, WRAPPERS):
        fragment = json.loads(strip_comments(block))
        document = carrier(wrap(fragment))
        write(POSOLOGY_DIR + "/" + name + ".json", json.dumps(document, indent=2, ensure_ascii=False) + "\n")
        rows.append("| `%s.json` | %d |" % (name, line))
    return rows


def extract_terminology():
    text = fetch("terminology.md")
    write(MODULE + "/test/resources/fixtures/chmed23a/terminology.md", text)
    sections = {"Route of Administration": "route", "Method of Administration": "method", "Unit": "unit"}
    for title, name in sections.items():
        body = re.search(r"^## " + re.escape(title) + r"\n(.*?)(?=^## |\Z)", text, re.S | re.M).group(1)
        lines = ["# CHMED23A " + title + ", terminology.md at ig-emediplan/specification@" + COMMIT[:8],
                 "code;de;fr"]
        for code, de, fr in re.findall(r"^\| `([^`]*)` \| ([^|]*?) \| ([^|]*?) \|$", body, re.M):
            lines.append(";".join([code, de.strip(), fr.strip()]))
        write(TERMINOLOGY_DIR + "/" + name + ".csv", "\n".join(lines) + "\n")
    # Risk categories: the table of README.md "RiskCategory" names categories 1-5 and 7;
    # every other risk of terminology.md is an allergy (category 6).
    readme = fetch("README.md")
    categories = {code: category for category, code in
                  re.findall(r"^\|(\d+)\|(\d+)\|", readme, re.M)}
    body = re.search(r"^## Risks\n(.*)\Z", text, re.S | re.M).group(1)
    lines = ["# CHMED23A risks, terminology.md and README.md at ig-emediplan/specification@" + COMMIT[:8],
             "code;category;de;fr"]
    for code, de, fr in re.findall(r"^\| `([^`]*)` \| ([^|]*?) \| ([^|]*?) \|$", body, re.M):
        lines.append(";".join([code, categories.get(code, "6"), de.strip(), fr.strip()]))
    write(TERMINOLOGY_DIR + "/risk.csv", "\n".join(lines) + "\n")


if __name__ == "__main__":
    rows = extract_posology()
    extract_terminology()
    print("\n".join(rows))
