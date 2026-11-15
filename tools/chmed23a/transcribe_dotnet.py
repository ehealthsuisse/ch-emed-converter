"""Transcribes the C# examples of the CHMED23A .NET reference implementation to JSON (feature 006).

The examples (Emediplan.ChMed23A.Examples) build Medication objects with C# object initialisers and
print them through the .NET serializer; the JSON itself is not in the repository. This script reads
the pinned model classes (JSON property names from Serialization/Constants, object types from the
ObjectType overrides, enum values) and evaluates the initialisers into the same JSON structure,
writing only the properties the example sets plus the object type `t` (research R13.3).

Run once from the repository root with network access:

    python -I tools/chmed23a/transcribe_dotnet.py

Writes format-chmed23a/src/test/resources/fixtures/chmed23a/dotnet/<Class>.<Method>.json (UTF-8,
LF). `DateTimeOffset.Now` becomes 2024-01-09T09:14:36+01:00 so that the fixtures are stable.
"""

import io
import json
import os
import re
import urllib.request
from decimal import Decimal

COMMIT = "0b579aa6f3aff10a017498db57048c6504091653"
API = "https://api.github.com/repos/ig-emediplan/specification/git/trees/" + COMMIT + "?recursive=1"
RAW = "https://raw.githubusercontent.com/ig-emediplan/specification/" + COMMIT + "/"
SOURCE = "chmed23a/client-implementations/dotnet/Source/"
OUT = "format-chmed23a/src/test/resources/fixtures/chmed23a/dotnet"
NOW = "2024-01-09T09:14:36+01:00"


def fetch(path):
    with urllib.request.urlopen(RAW + path) as response:
        return response.read().decode("utf-8-sig")


def tree():
    with urllib.request.urlopen(API) as response:
        return [t["path"] for t in json.load(response)["tree"] if t["type"] == "blob"]


# ---------------------------------------------------------------------------------------------
# Model metadata


def load_model(paths):
    constants = {}
    for path in paths:
        if "/Serialization/Constants/" not in path:
            continue
        text = fetch(path)
        stack = []
        for line in text.splitlines():
            m = re.match(r"\s*public (?:static |partial )*class (\w+)", line)
            if m:
                stack.append(m.group(1))
                continue
            m = re.match(r'\s*public const string (\w+) = "([^"]*)";', line)
            if m:
                key = ".".join(stack[1:] + [m.group(1)])
                constants[key] = m.group(2)
            if re.match(r"\s*}\s*$", line) and stack:
                stack.pop()
    enums, classes = {}, {}
    for path in paths:
        if "/Emediplan.ChMed23A/Models/" not in path:
            continue
        text = fetch(path)
        m = re.search(r"public enum (\w+)\s*{(.*?)}", text, re.S)
        if m:
            values, nxt = {}, 0
            for name, value in re.findall(r"^\s*(\w+)\s*(?:=\s*(\d+))?\s*,?\s*$", m.group(2), re.M):
                nxt = int(value) if value else nxt
                values[name] = nxt
                nxt += 1
            enums[m.group(1)] = values
            continue
        m = re.search(r"public (?:abstract )?class (\w+)\s*(?::\s*(\w+))?", text)
        if not m:
            continue
        props = {}
        for const, prop in re.findall(
                r"\[JsonProperty\(JsonPropertyNames\.([\w.]+)\)\](?:\s*\[[^\]]*\])*\s*public [\w<>\[\]?,. ]+? (\w+) {", text):
            props[prop] = constants[const]
        object_type = re.search(r"override \w+ (?:ObjectType|Type)\s*=>\s*\w+\.(\w+);", text)
        classes[m.group(1)] = {"base": m.group(2), "props": props,
                               "type": object_type.group(1) if object_type else None,
                               "enum": re.search(r"override (\w+) (?:ObjectType|Type)\s*=>", text)}
    for info in classes.values():
        info["enum"] = info["enum"].group(1) if info["enum"] else None
    return enums, classes


# ---------------------------------------------------------------------------------------------
# C# initialiser evaluation


TOKEN = re.compile(r'\s*(?:(@?"(?:[^"\\]|\\.|"")*")|(\d+(?:\.\d+)?[mMfFdD]?)|(\w+)|(=>|[{}()\[\],.=<>?:;-]))')


class Parser:
    def __init__(self, text, enums, classes):
        self.tokens = [t for t in TOKEN.findall(text)]
        self.tokens = ["".join(t) for t in self.tokens]
        self.i = 0
        self.enums = enums
        self.classes = classes

    def peek(self, k=0):
        return self.tokens[self.i + k] if self.i + k < len(self.tokens) else None

    def take(self, expected=None):
        tok = self.tokens[self.i]
        if expected is not None and tok != expected:
            raise ValueError("expected %s, got %s at %d" % (expected, tok, self.i))
        self.i += 1
        return tok

    def type_name(self):
        name = self.take()
        if self.peek() == "<":
            depth = 0
            while True:
                tok = self.take()
                name += tok
                depth += tok == "<"
                depth -= tok == ">"
                if depth == 0:
                    break
        if self.peek() == "[":
            self.take("[")
            self.take("]")
            name += "[]"
        return name

    def expression(self, expected_class=None):
        tok = self.peek()
        if tok == "new":
            return self.new(expected_class)
        if tok == "-":
            self.take()
            return -self.expression()
        if tok.startswith('"') or tok.startswith('@"'):
            self.take()
            return json.loads(tok.lstrip("@")) if not tok.startswith("@") else tok[2:-1].replace('""', '"')
        if re.match(r"\d", tok):
            self.take()
            return Decimal(tok.rstrip("mMfFdD"))
        if tok in ("true", "false"):
            self.take()
            return tok == "true"
        if tok == "null":
            self.take()
            return None
        if tok == "Guid" and self.peek(2) == "NewGuid":
            # Guid.NewGuid().ToString(): a fixed id, as the other examples use "MyId"
            for _ in range(9):
                self.take()
            return "MyId"
        if tok == "DateTimeOffset" and self.peek(2) == "Now":
            self.take(), self.take("."), self.take()
            return NOW
        # Enum member, e.g. Gender.Female
        name = self.take()
        self.take(".")
        member = self.take()
        if name not in self.enums:
            raise ValueError("unknown expression %s.%s" % (name, member))
        return self.enums[name][member]

    def new(self, expected_class):
        self.take("new")
        if self.peek() == "(":
            cls = expected_class
            self.take("("), self.take(")")
        elif self.peek() == "[":
            self.take("["), self.take("]")
            return self.collection(expected_class)
        else:
            cls = self.type_name()
        if cls in ("DateTime", "DateTimeOffset", "TimeSpan"):
            args = self.arguments()
            if cls == "DateTime":
                return "%04d-%02d-%02d" % tuple(int(a) for a in args[:3])
            if cls == "TimeSpan":
                return "%02d:%02d:%02d" % tuple(int(a) for a in args[:3])
            raise ValueError("DateTimeOffset constructor not supported")
        if cls.startswith("List") or cls.endswith("[]"):
            if self.peek() == "(":
                self.arguments()
            if self.peek() != "{":
                return []
            element = re.sub(r"^List<(.*)>$", r"\1", cls).rstrip("[]")
            return self.collection(element)
        if self.peek() == "(":
            self.arguments()
        return self.object(cls)

    def arguments(self):
        self.take("(")
        args = []
        while self.peek() != ")":
            args.append(self.expression())
            if self.peek() == ",":
                self.take(",")
        self.take(")")
        return args

    def collection(self, element):
        self.take("{")
        items = []
        while self.peek() != "}":
            items.append(self.expression(element))
            if self.peek() == ",":
                self.take(",")
        self.take("}")
        return items

    def properties(self, cls):
        props, c = {}, cls
        while c in self.classes:
            props = {**self.classes[c]["props"], **props}
            c = self.classes[c]["base"]
        return props

    def object(self, cls):
        info = self.classes.get(cls)
        if info is None:
            raise ValueError("unknown class " + cls)
        result = {}
        if info["type"]:
            result["t"] = self.enums[info["enum"]][info["type"]]
        names = self.properties(cls)
        if self.peek() != "{":
            return result
        self.take("{")
        while self.peek() != "}":
            prop = self.take()
            self.take("=")
            value = self.expression(self.property_class(cls, prop))
            if prop not in names:
                raise ValueError("%s.%s has no JSON name" % (cls, prop))
            result[names[prop]] = value
            if self.peek() == ",":
                self.take(",")
        self.take("}")
        return result

    def property_class(self, cls, prop):
        return PROPERTY_TYPES.get((cls, prop))


# Element types of target-typed `new()` in lists (from the model classes).
PROPERTY_TYPES = {
    ("Patient", "Ids"): "PatientId",
    ("Medication", "Medicaments"): "Medicament",
    ("Medicament", "Posologies"): "Posology",
    ("MedicalData", "RiskCategories"): "RiskCategory",
}


# ---------------------------------------------------------------------------------------------


def transcribe(paths, enums, classes):
    os.makedirs(OUT, exist_ok=True)
    written = []
    for path in sorted(paths):
        if "/Emediplan.ChMed23A.Examples/" not in path or not path.endswith("Examples.cs"):
            continue
        text = fetch(path)
        cls = re.search(r"public class (\w+)", text).group(1)
        for m in re.finditer(r"public void (\w+)\(\)\s*{\s*var medication = (new .*?);\s*\n\s*PrintSerializedJson",
                             text, re.S):
            body = re.sub(r"//[^\n]*", "", m.group(2))
            document = Parser(body, enums, classes).expression("Medication")
            name = "%s.%s.json" % (cls, m.group(1))
            with io.open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
                f.write(json.dumps(document, indent=2, ensure_ascii=False, default=number) + "\n")
            written.append(name)
    return written


def number(value):
    if isinstance(value, Decimal):
        return int(value) if value == value.to_integral_value() and "." not in str(value) else float(value)
    raise TypeError(value)


if __name__ == "__main__":
    paths = [p for p in tree() if p.startswith(SOURCE)]
    enums, classes = load_model(paths)
    for name in transcribe(paths, enums, classes):
        print(name)
