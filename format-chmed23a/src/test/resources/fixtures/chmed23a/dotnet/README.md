# .NET reference examples of CHMED23A, transcribed to JSON

The examples of `chmed23a/client-implementations/dotnet/Source/Emediplan.ChMed23A.Examples/`
(`ig-emediplan/specification`, commit `0b579aa6f3aff10a017498db57048c6504091653`, MIT licence, see
`../official/README.md`) build `Medication` objects in C# and print them through the .NET
serializer; the repository holds no JSON for them. `tools/chmed23a/transcribe_dotnet.py` evaluates
the initialisers with the pinned model classes (JSON names from `Serialization/Constants`, object
types, enum values) into JSON (research R13.3). One file per example method, named
`<Class>.<Method>.json`.

Transcription rules:
- only the properties an example sets, plus the object type `t`; defaults of the C# model
  (e.g. `tdpc` = 1) are not written;
- `DateTimeOffset.Now` → `2024-01-09T09:14:36+01:00`; `Guid.NewGuid()` → `MyId`;
- `new DateTime(y, m, d)` → `YYYY-MM-DD`; `new TimeSpan(h, m, s)` → `hh:mm:ss`.

The `MedicationExamples` (`Example1`–`Example4`) are the documents of `examples.md` and are covered
by `../official/`.

Finding F9 (research R13): most examples use units outside the CDTYP 9 code list (`TABL`,
`Applikation`, `Tabl`), which the specification requires ("code representation"). The validator
reports them as WARNING (research R7) and the mapping carries the dosage as text.

Coverage of the compatibility tables of posology.md: together with `../posology/` and
`../official/`, these fixtures cover every "Yes" cell except DaysOfMonth with Times or
DaySegments, Interval with DosageFromTo, and DosageRange/DosageFromTo inside applications; those
are covered by `../synthetic/cover-*.json`.
