# Domain review questions / Fragen für den Domain-Review

This file holds the questions for the domain review of the CHMED16A, CHMED23A and CH EMED EPR
mappings, first in German, then in English. It is compiled from the "Domain review pending" lists of
[chmed16a-ch-emed.md](chmed16a-ch-emed.md), [chmed23a-ch-emed.md](chmed23a-ch-emed.md) and
[ch-emed-epr.md](ch-emed-epr.md) (tasks T118, T086, T036). When an answer is given, record it in the
mapping specification of the rule; changes to the mapping follow in a feature of their own.

## Deutsch

### Fragen für den Domain-Review

Der CH EMED Converter wandelt eMediplan-Dokumente (CHMED16A, CHMED23A) und CH EMED EPR in beide Richtungen mit CH EMED um. Wo die Formate verschieden viel ausdrücken können, mussten wir Spezifikationen auslegen. Diese Auslegungen sollen von einer Fachperson mit Kenntnis von eMediplan und CH EMED geprüft werden.

Jede Frage nennt zuerst, was das Feld laut Spezifikation bedeutet, dann was der Converter heute tut, und zuletzt, was zu entscheiden ist. „Clinical loss“ heisst: Die Information steht im Ergebnis nicht mehr genau so wie in der Quelle; im Strict-Modus schlägt die Konvertierung dann fehl.

**So antworten Sie:** Kreuzen Sie pro Frage eine Antwort an oder schreiben Sie sie dazu, und nennen Sie die Nummer der Frage (z. B. „C4: pro Abgabe“), etwa als Kommentar zum Pull Request oder per Mail. Bei „anders“ bitte kurz begründen. Fragen, die Sie nicht beurteilen können, lassen Sie offen.

- **Stand:** 15.11.2026, Branch `dev`
- **Aufgaben:** T118 (CHMED16A), T086 (CHMED23A), T036 (CH EMED EPR)
- **Quellen:** `docs/mapping/chmed16a-ch-emed.md`, `chmed23a-ch-emed.md`, `ch-emed-epr.md`
- **Spezifikationen:** eMediplan `ig-emediplan/specification@0b579aa6`, CH EMED 6.0.0, CH EMED EPR 3.0.0
- **Arbeitsgruppe CH EMED 7:** Arbeitsstand der Sub-AG eMedikation FHIR & Inhalt (Entwurf und Sitzungen bis 12.11.2026); nichts davon ist entschieden

### A Codes und Einheiten

Wie Applikationsweg, Methode, Einheiten und Produkt-Identifikatoren codiert werden.

#### A1 Applikationsweg

Regeln: CHMED16A M-DOS-04 · CHMED23A M23-DOS-13

- **Bedeutung:** CHMED16A gibt den Weg `Roa` als INDEX-Code (CDTYP 26) an, CHMED23A `roa` direkt als EDQM-Code (CDTYP 61). Das CH-EMED-Profil verlangt einen codierten Weg.
- **Heute:** CHMED23A: Der Code wird unverändert übernommen, wenn er im CH-Term-Value-Set liegt. CHMED16A: Nur `PO` wird zu EDQM `20053000` „Oral use“; jeder andere Weg geht mit clinical loss verloren.
- **Frage:** Soll es für CHMED16A eine Zuordnung CDTYP 26 → EDQM geben?

  - [ ] Ja – die Zuordnung liefern wir bzw. ist hier zu finden: …
  - [ ] Nein – nur `PO`, Verlust melden genügt

#### A2 Applikationsmethode

Regeln: CHMED23A M23-DOS-14 · method-edqm.csv

- **Bedeutung:** `moa` sind HCI-Codes (CDTYP 62), die laut Spezifikation nur „auf EDQM basieren“, zum Beispiel `19` „schlucken“.
- **Heute:** Eine Tabelle ordnet alle 14 Codes über die deutsche Bezeichnung einem EDQM-Code zu. Das Profil verlangt einen Code, Text ist nicht möglich.
- **Frage:** Bedeuten die Codes bei gleicher Bezeichnung wirklich dasselbe? Ein falscher Code wäre schlimmer als keiner.

  - [ ] Die Tabelle ist richtig
  - [ ] Diese Zeilen sind falsch: …

#### A3 Einheiten

Regeln: CHMED16A M-DOS-08, M-REV-16 · CHMED23A M23-DOS-15, M23-REV-12

- **Bedeutung:** Beide Formate verwenden Einheiten aus INDEX CDTYP 9, zum Beispiel `STK` (Stück), `TR`/`gtt` (Tropfen), `HUB`, `IE`/`UI` (Internationale Einheiten), `Appl` (Applikation).
- **Heute:** `STK` → SNOMED CT `767525000` „Unit“; Tropfen → `732994000` „Drop“; `HUB` → `732981002`; `IE`/`UI` → UCUM `[iU]`; Masse und Volumen → UCUM. `Appl` hat keine Zuordnung, die Dosierung wird zu Text. Rückweg: `767525000` und `732936001` „Tablet“ werden zu `STK`; fehlt eine Einheit, schreibt CHMED23A `N/A`.
- **Frage:** Sind diese Zuordnungen richtig?

  - [ ] „Unit“ ist der richtige Code für `STK`
  - [ ] „Tablet“ darf als `STK` zurückgelesen werden
  - [ ] Für `Appl` gibt es diese Einheit: …

#### A4 Pharmacode und Produktnummer

Regeln: CHMED16A M-MED-03 · CHMED23A M23-MED-03, M23-MED-04

- **Bedeutung:** Ein Medikament wird über die GTIN (Refdata), den Pharmacode oder die Produktnummer (beide INDEX, HCI Solutions) oder als Freitext angegeben. In den vier offiziellen CHMED23A-Beispielen haben 16 von 20 Medikamenten eine GTIN.
- **Heute:** Nur die GTIN wird codiert (`urn:oid:2.51.1.1`). Für Pharmacode und Produktnummer gibt es in den gepinnten Paketen kein Code-System; die Nummer geht als Text mit („Pharmacode 1234567“), WARNING ohne clinical loss.
- **Frage:** Ein Empfänger kann das Produkt dann nicht mehr automatisch erkennen, etwa für eine Interaktionsprüfung. Soll das als clinical loss zählen?

  - [ ] Ja, clinical loss
  - [ ] Nein, WARNING ohne clinical loss genügt
  - [ ] Anders: …

#### A5 SL-Indikationscode

Regeln: CHMED16A M-RX-08, M-REV-38

- **Bedeutung:** `IndC` ist der Indikationscode der Spezialitätenliste aus der eRezept-Dokumentation, Format `00000.00`.
- **Heute:** Er geht als Text in `reasonCode.text` („SL indication code …“), ohne clinical loss, weil die gepinnten Pakete kein Code-System dafür haben.
- **Frage:** Gibt es ein Code-System, das verwendet werden darf?

  - [ ] Ja: …
  - [ ] Nein, Text genügt

### B Dosierung

Angaben zur Einnahme, die CH EMED nicht genau gleich darstellen kann.

#### B1 Bezug zur Mahlzeit

Regeln: CHMED23A M23-DOS-16

- **Bedeutung:** `relMeal`: 1 vor, 2 während, 3 nach dem Essen, 4 nüchtern, 5 mit fettreicher, 6 mit leichter, 7 mit einer Hauptmahlzeit.
- **Heute:** 1–3 werden zu den FHIR-Zeitcodes AC…, C…, PC… (bei festen Uhrzeiten zu Text, weil FHIR `when` und `timeOfDay` nicht zusammen erlaubt). 4–7 haben keinen solchen Code und gehen als übersetzter Text in `additionalInstruction`, WARNING ohne clinical loss. Der Rückweg erkennt den Text wieder.
- **Arbeitsgruppe CH EMED 7:** Laut Entwurf der Arbeitsgruppe geben in CH EMED 6 andere `when`-Codes als MORN/NOON/EVE/NIGHT eine Validierungswarnung, also auch AC…, C…, PC… für 1–3. Als Alternative nennt der Entwurf SNOMED-CT-Codes für „vor/nach dem Essen“ in `additionalInstruction`. Entschieden ist nichts.
- **Frage:** Wie sollen 4–7 behandelt werden?

  - [ ] „Nüchtern“ soll als clinical loss zählen
  - [ ] Ein Code aus einer gepinnten Terminologie (z. B. SNOMED CT) soll gesetzt werden: …
  - [ ] Text ohne clinical loss ist richtig

### C Rezept

Wiederholungen, Abgabemengen, Substitution und Empfänger von Rezepten.

#### C1 Bedeutung von `Rep`

Regeln: CHMED16A M-RX-06, M-REV-36, M-ERX-04

- **Bedeutung:** Die Spezifikation nennt `Rep` „number of repetitions in months, e.g. permanent prescription for 6 months“.
- **Heute:** Der Converter liest es als Gültigkeitsdauer: `validityPeriod` vom Rezeptdatum an für `Rep` Monate (Ende inklusive). Zurück werden nur ganze Monate 1–24 zu `Rep`. Werte über 24 werden gelesen, beim eRezept aber abgelehnt.
- **Arbeitsgruppe CH EMED 7:** Die Arbeitsgruppe liest `Rep` ebenso: Wiederholbarkeit nur in Monaten, 0 heisst nur eine Abgabe, eine Anzahl Wiederholungen gibt es nicht. Heute ergibt `Rep` 0 gar keine Angabe; mit `numberOfRepeatsAllowed` = 0 wäre es eindeutig.
- **Frage:** Was bedeutet `Rep`, und wie sind Werte über 24 Monate zu behandeln?

  - [ ] Gültigkeitsdauer in Monaten (wie heute)
  - [ ] Anzahl Wiederholungen
  - [ ] Werte über 24: …

#### C2 Fehlende Wiederholungsangabe

Regeln: CHMED23A M23-RX-05

- **Bedeutung:** Fehlt `reps`, gilt laut Spezifikation „Number 1“: Das Rezept darf einmal wiederholt werden. Nicht wiederholbar ist es nur mit `v` = 0.
- **Heute:** Der Converter setzt keine Standardwerte für klinisch relevante Felder (Constitution II). `numberOfRepeatsAllowed` fehlt dann, und FHIR gibt einem fehlenden Wert keine Bedeutung. Ein Empfänger könnte „keine Wiederholung“ lesen.
- **Arbeitsgruppe CH EMED 7:** Was ein fehlendes `numberOfRepeatsAllowed` heisst, ist offen. Vorschlag der Arbeitsgruppe: fehlend = Anzahl unbestimmt, 0 = nur eine Abgabe. Der FHIR-Änderungsantrag FHIR-58711 (R6): fehlend = keine Wiederholung. HL7 prüft ein zusätzliches Element (Stand 12.11.2026).
- **Frage:** Soll der Converter hier eine Ausnahme machen?

  - [ ] Ja: `numberOfRepeatsAllowed` = 1 schreiben und als abgeleitet melden
  - [ ] Nein: fehlend lassen
  - [ ] Anders: …

#### C3 Wiederholungsdauer unter einem Tag

Regeln: CHMED23A M23-RX-04

- **Bedeutung:** `reps` kann eine Dauer in Sekunden, Minuten oder Stunden haben: ein Rezept, das nur einige Stunden wiederholt eingelöst werden darf.
- **Heute:** Die Gültigkeit wird in ganzen Tagen berechnet; solche Dauern gehen mit clinical loss verloren.
- **Frage:** Kommt das in der Praxis vor?

  - [ ] Nein, die Meldung genügt
  - [ ] Ja, so: …

#### C4 Anzahl Packungen

Regeln: CHMED16A M-RX-05 · CHMED23A M23-RX-06

- **Bedeutung:** `NbPack`/`nbPack` ist die Anzahl abzugebender Packungen, Standardwert 1. Welche Packung gemeint ist, ergibt sich aus dem Produkt (meist der GTIN).
- **Heute:** Wird zu `dispenseRequest.quantity`, in FHIR die Menge pro Abgabe. Der Standardwert wird nicht gesetzt.
- **Frage:** Gilt die Anzahl pro Abgabe oder für das ganze Rezept, wenn Wiederholungen erlaubt sind? Beispiel: 2 Packungen, 3 Wiederholungen.

  - [ ] Pro Abgabe (jedes Mal 2)
  - [ ] Für das ganze Rezept (insgesamt 2)
  - [ ] Anders: …

#### C5 Mehrere Posologien eines Medikaments

Regeln: CHMED23A M23-RX-02, research R10

- **Bedeutung:** Ein verordnetes Medikament kann mehrere Posologien haben, zum Beispiel ein Ausschleichschema in vier Phasen (offizielles Beispiel 4).
- **Heute:** Das Profil erlaubt eine Basis-Dosierung pro MedicationRequest. Es entstehen vier Requests, jeder mit einer Kopie der Abgabedaten einschliesslich `nbPack`.
- **Arbeitsgruppe CH EMED 7:** Der Entwurf schlägt für CH EMED 7 vor, `timing.repeat.boundsPeriod` nur noch für das einzelne Dosierungselement zu verwenden und die Gesamtdauer separat anzugeben. Dann könnte ein Ausschleichschema in einem Request mit mehreren Dosierungen bleiben.
- **Frage:** Eine Apotheke könnte die Packungen viermal abgeben. Welche Variante ist richtig?

  - [ ] Mehrere Requests (heute)
  - [ ] Ein Request mit der Dosierung als Text (Struktur geht verloren)
  - [ ] Anders: …

#### C6 Substitution

Regeln: CHMED16A M-RX-07, M-REV-37 · CHMED23A M23-RX-07

- **Bedeutung:** `Subs` 1 bzw. `isNotSub` true: nicht ersetzen. 0 bzw. false (Standard): Ersatz erlaubt.
- **Heute:** „Nicht ersetzen“ → Code `N`; „erlaubt“ → Code `E` (equivalent). Fehlt die Angabe, wird nichts geschrieben; FHIR und CH EMED EPR lesen das als „erlaubt“. Rückweg: ein anderer Code wird zu „nicht ersetzen“ (sichere Lesart) mit clinical loss.
- **Frage:** Ist `E` der richtige Code für „Substitution erlaubt“?

  - [ ] Ja
  - [ ] Nein, besser: …

#### C7 `sic`

Regeln: CHMED23A M23-RX-08

- **Bedeutung:** „Sic erat scriptum“: Der Arzt bestätigt der Apotheke, dass die Verordnung bewusst genau so gewählt ist. Das ist kein Substitutionsverbot.
- **Heute:** CH EMED hat kein Element dafür; die Angabe geht mit clinical loss verloren.
- **Arbeitsgruppe CH EMED 7:** Im Entwurf gilt Weglassen als keine Option (Text oder eigenes Feld). IHE und EU MPD haben dafür eine Extension `offLabel`; ob sie passt, ist offen.
- **Frage:** Soll sie als Text mitgehen, damit die Apotheke sie sieht?

  - [ ] Ja, z. B. in `MedicationRequest.note`
  - [ ] Nein, die Meldung genügt
  - [ ] Anders: …

#### C8 Empfänger des Rezepts

Regeln: CHMED16A M-RX-09, M-REV-39 · CHMED23A M23-RX-09 · see E3

- **Bedeutung:** `Rcv`/`rec` ist die GLN der Fachperson, an die das Rezept elektronisch geschickt wird.
- **Heute:** Wird zu `dispenseRequest.performer` als logische Referenz mit der GLN. Auf dem Weg ins EPR wird dieses Element entfernt (Frage E3).
- **Frage:** Genügt die logische Referenz?

  - [ ] Ja
  - [ ] Nein: …

#### C9 Postleitzahlen beim eRezept

Regeln: CHMED16A M-ERX-04

- **Bedeutung:** Die eRezept-Regeln erlauben für `Zip` 4 oder 5 Ziffern.
- **Heute:** Eine ausländische Adresse mit anderer Postleitzahl verletzt die Regel, und es entsteht kein eRezept.
- **Frage:** Wie sollen ausländische Adressen behandelt werden?

  - [ ] Ablehnen ist richtig
  - [ ] Anders: …

#### C10 GLN- und GTIN-Prüfziffern

Regeln: CHMED23A C23-OBJ-15, research F7

- **Bedeutung:** GLN und GTIN haben 13 Ziffern mit GS1-Prüfziffer. Die CHMED23A-Spezifikation schreibt das Format nicht vor. Von den vier GLN der offiziellen Beispiele sind drei Platzhalter mit 11 oder 12 Ziffern; alle 16 GTIN sind gültig.
- **Heute:** Eine falsche Länge oder Prüfziffer ergibt eine WARNING, der Wert wird unverändert übernommen. Die .NET-Referenzimplementierung lehnt ungültige GLN ab; CH EMED EPR verlangt gültige GLN.
- **Frage:** Soll der Converter ungültige GLN ablehnen?

  - [ ] Nein, WARNING genügt (heute)
  - [ ] Ja, in Rezepten
  - [ ] Ja, immer
  - [ ] Anders: …

### D Inhalt des Medikationsplans

Angaben im Plan, für die die CH EMED Medication Card keinen Platz hat.

#### D1 Medizinische Daten

Regeln: CHMED16A M-MDT-01 · CHMED23A M23-MDT-01

- **Bedeutung:** Gewicht und Grösse, Risikokategorien (z. B. Niereninsuffizienz, Schwangerschaft, Allergien), erster Tag der letzten Menstruation, Frühgeburt und Gestationsalter.
- **Heute:** Die Medication Card hat keinen Platz dafür; jedes Element geht mit clinical loss verloren.
- **Frage:** Risiken wie Allergien oder Schwangerschaft sind für die Abgabe wichtig. Sollen sie wenigstens als Text mitgehen?

  - [ ] Ja, als Text, z. B. im Abschnitt Bemerkungen
  - [ ] Ja, aber anders: …
  - [ ] Nein, die Meldung genügt

#### D2 Verordnende Person

Regeln: CHMED16A M-MED-07 · CHMED23A M23-MED-07

- **Bedeutung:** `PrscbBy`/`prscbBy` nennt im Plan, wer das Medikament verordnet hat (GLN oder Bezeichnung), bei CHMED16A auch Selbstmedikation.
- **Heute:** Wird keinem Element zugeordnet, WARNING.
- **Frage:** Soll die Angabe mitgehen?

  - [ ] Ja, als Text in `MedicationStatement.note`
  - [ ] Ja, mit GLN als Referenz auf die Person
  - [ ] Nein
  - [ ] Anders: …

#### D3 Validierung des Plans

Regeln: CHMED16A M-DOC-07

- **Bedeutung:** `ValBy` und `ValDt`: GLN der Fachperson, die den Plan validiert hat, und das Datum.
- **Heute:** Wird keinem Element zugeordnet, WARNING ohne clinical loss.
- **Arbeitsgruppe CH EMED 7:** Die Arbeitsgruppe diskutiert, die Verifikation des ganzen Plans abzubilden, etwa mit der IHE-MeOw-Extension `verificationInformation` (verifizierende Person und Zeitpunkt) auf MedicationStatement. Priorität tief, eventuell erst in der nächsten Revision.
- **Frage:** Ist die Validierung klinisch relevant?

  - [ ] Ja, behalten (z. B. als Attester)
  - [ ] Nein
  - [ ] Anders: …

#### D4 Status auf dem Weg nach eMediplan

Regeln: CHMED16A M-REV-10, M-REV-32

- **Bedeutung:** CHMED16A kennt keinen Status eines Medikaments.
- **Heute:** Aktive, geplante und abgeschlossene Einträge werden übernommen; pausierte, unbekannte und Entwürfe ebenfalls, mit clinical loss; abgesetzte, nicht eingenommene und stornierte fehlen, mit clinical loss; irrtümlich erfasste fehlen.
- **Frage:** Gehört ein pausiertes Medikament auf den Plan?

  - [ ] Ja (heute)
  - [ ] Nein
  - [ ] Anders: …

#### D5 Titel der Dokumente

Regeln: CHMED16A M-RX-01, M-DOC-08

- **Bedeutung:** Titel und Abschnittstitel richten sich nach der Sprache des Patienten.
- **Heute:** Rezept: „Rezept“ / „Ordonnance“ / „Prescrizione“, Abschnitt z. B. „Arzneimittelverordnung“.
- **Frage:** Sind das die Begriffe, die die Empfänger erwarten?

  - [ ] Ja
  - [ ] Nein, besser: …

### E CH EMED EPR: entfernte Elemente

CH EMED EPR unterstützt manche Elemente von CH EMED nicht; sie werden entfernt. Klinisch relevante ergeben clinical loss, die übrigen nur eine INFORMATION.

#### E1 Liste der klinisch relevanten Elemente

Regeln: ch-emed-epr.md, Unsupported elements · M-EPR-22

- **Bedeutung:** Als klinisch markiert sind: Zeitangaben einer Basis-Dosierung (Wochentage, Uhrzeiten, Häufigkeit, Dauer), Text einer geteilten Dosierung, Körperstelle, Methode, Maximaldosis, Status-Grund, Grund und Zeitraum einer Behandlung, Art der Therapie, erkannte Probleme bei Verordnungen und die Werte einer Beobachtung.
- **Heute:** Diese ergeben beim Entfernen clinical loss; alle anderen (z. B. `meta`, Encounter, Notiz-Autor) nur INFORMATION.
- **Frage:** Ist die Liste vollständig und richtig?

  - [ ] Ja
  - [ ] Ergänzen oder streichen: …

#### E2 Selbstmedikation

Regeln: MedicationStatement.category · M-MED-06, M23-MED-06

- **Bedeutung:** Die Selbstmedikation aus eMediplan (`AutoMed`/`autoMed`) steht in CH EMED in `MedicationStatement.category` (`patientspecified`).
- **Heute:** Gilt als nicht klinisch; auf dem Weg eMediplan → CH EMED → EPR geht „Selbstmedikation“ ohne clinical loss verloren.
- **Frage:** Soll das als clinical loss zählen?

  - [ ] Ja
  - [ ] Nein
  - [ ] Anders: …

#### E3 Empfänger des Rezepts im EPR

Regeln: MedicationRequest.dispenseRequest.performer · see C8

- **Bedeutung:** Hier steht der Empfänger eines Rezepts (`Rcv`/`rec` aus eMediplan).
- **Heute:** Gilt als nicht klinisch und wird auf dem Weg ins EPR mit INFORMATION entfernt.
- **Frage:** Ist das richtig?

  - [ ] Ja
  - [ ] Nein, clinical loss
  - [ ] Anders: …

#### E4 Status-Grund und erkannte Probleme einer Abgabe (vermutlich Unstimmigkeit)

Regeln: MedicationDispense.statusReason[x], .detectedIssue

- **Bedeutung:** Bei `MedicationRequest` gelten genau diese beiden Elemente als klinisch, bei `MedicationDispense` nicht.
- **Heute:** Bei einer Abgabe werden sie mit INFORMATION entfernt.
- **Frage:** Sollen sie wie bei der Verordnung als klinisch gelten? Eine Korrektur ändert Report-Einträge und braucht einen CHANGELOG-Eintrag.

  - [ ] Ja, klinisch
  - [ ] Nein, bewusst nicht klinisch
  - [ ] Anders: …

#### E5 Reichdauer einer Abgabe

Regeln: MedicationDispense.daysSupply

- **Bedeutung:** Für wie viele Tage die abgegebene Menge reicht.
- **Heute:** Gilt als nicht klinisch, wird mit INFORMATION entfernt.
- **Frage:** Ist das richtig?

  - [ ] Ja
  - [ ] Nein, clinical loss
  - [ ] Anders: …

#### E6 Dringlichkeit einer Verordnung

Regeln: MedicationRequest.priority

- **Bedeutung:** Zum Beispiel „dringend“.
- **Heute:** Gilt als nicht klinisch, wird mit INFORMATION entfernt.
- **Frage:** Ist das richtig?

  - [ ] Ja
  - [ ] Nein, clinical loss
  - [ ] Anders: …

## English

### Questions for the domain review

The CH EMED Converter converts eMediplan documents (CHMED16A, CHMED23A) and CH EMED EPR to and from CH EMED. Where the formats differ in what they can express, we had to interpret the specifications. A person who knows eMediplan and CH EMED should review these interpretations.

Each question first says what the field means according to the specification, then what the converter does today, and finally what needs to be decided. “Clinical loss” means that the result no longer carries the information exactly as the source did; in strict mode such a conversion fails.

**How to answer:** tick or write one answer per question and name its number (for example “C4: per dispense”), for example as a comment on the pull request or by mail. For “other”, please give a short reason. Leave out any question you cannot judge.

- **As of:** 2026-11-15, branch `dev`
- **Tasks:** T118 (CHMED16A), T086 (CHMED23A), T036 (CH EMED EPR)
- **Sources:** `docs/mapping/chmed16a-ch-emed.md`, `chmed23a-ch-emed.md`, `ch-emed-epr.md`
- **Specifications:** eMediplan `ig-emediplan/specification@0b579aa6`, CH EMED 6.0.0, CH EMED EPR 3.0.0
- **CH EMED 7 working group:** Work in progress of the eMedication FHIR & Content sub-working group (draft and meetings up to 2026-11-12); none of it is decided

### A Codes and units

How routes, methods, units and product identifiers are coded.

#### A1 Route of administration

Rules: CHMED16A M-DOS-04 · CHMED23A M23-DOS-13

- **Meaning:** CHMED16A gives the route `Roa` as an INDEX code (CDTYP 26), CHMED23A gives `roa` as an EDQM code (CDTYP 61). The CH EMED profile requires a coded route.
- **Today:** CHMED23A: the code is carried unchanged if it is in the CH Term value set. CHMED16A: only `PO` becomes EDQM `20053000` “Oral use”; every other route is lost with clinical loss.
- **Question:** Should there be a mapping CDTYP 26 → EDQM for CHMED16A?

  - [ ] Yes – we provide the mapping, or it can be found here: …
  - [ ] No – only `PO`; reporting the loss is enough

#### A2 Method of administration

Rules: CHMED23A M23-DOS-14 · method-edqm.csv

- **Meaning:** `moa` are HCI codes (CDTYP 62) that the specification calls only “based on” EDQM, for example `19` “schlucken” (swallow).
- **Today:** A table maps all 14 codes to an EDQM code by their German designation. The profile requires a code; text is not possible.
- **Question:** Do codes with the same designation really mean the same? A wrong code would be worse than none.

  - [ ] The table is correct
  - [ ] These rows are wrong: …

#### A3 Units

Rules: CHMED16A M-DOS-08, M-REV-16 · CHMED23A M23-DOS-15, M23-REV-12

- **Meaning:** Both formats use units of INDEX CDTYP 9, for example `STK` (pieces), `TR`/`gtt` (drops), `HUB` (puffs), `IE`/`UI` (international units), `Appl` (application).
- **Today:** `STK` → SNOMED CT `767525000` “Unit”; drops → `732994000` “Drop”; `HUB` → `732981002`; `IE`/`UI` → UCUM `[iU]`; mass and volume → UCUM. `Appl` has no mapping, so the dosage becomes text. In reverse, `767525000` and `732936001` “Tablet” become `STK`; without a unit, CHMED23A gets `N/A`.
- **Question:** Are these mappings right?

  - [ ] “Unit” is the right code for `STK`
  - [ ] “Tablet” may be read back as `STK`
  - [ ] For `Appl` there is this unit: …

#### A4 Pharmacode and product number

Rules: CHMED16A M-MED-03 · CHMED23A M23-MED-03, M23-MED-04

- **Meaning:** A medicament is identified by its GTIN (Refdata), its Pharmacode or product number (both INDEX, HCI Solutions), or as free text. In the four official CHMED23A examples, 16 of 20 medicaments have a GTIN.
- **Today:** Only the GTIN is coded (`urn:oid:2.51.1.1`). The pinned packages have no code system for Pharmacode or product number; the number goes along as text (“Pharmacode 1234567”), WARNING without clinical loss.
- **Question:** A receiver can then no longer identify the product automatically, for example for an interaction check. Should this count as clinical loss?

  - [ ] Yes, clinical loss
  - [ ] No, a WARNING without clinical loss is enough
  - [ ] Other: …

#### A5 SL indication code

Rules: CHMED16A M-RX-08, M-REV-38

- **Meaning:** `IndC` is the indication code of the Swiss list of specialities (SL) from the eRezept documentation, format `00000.00`.
- **Today:** It goes into `reasonCode.text` (“SL indication code …”), without clinical loss, as the pinned packages have no code system for it.
- **Question:** Is there a code system that may be used?

  - [ ] Yes: …
  - [ ] No, text is enough

### B Dosage

Dosage details that CH EMED cannot represent in exactly the same way.

#### B1 Relation to meals

Rules: CHMED23A M23-DOS-16

- **Meaning:** `relMeal`: 1 before, 2 during, 3 after a meal, 4 empty stomach, 5 with a high-fat meal, 6 with a light meal, 7 with a main meal.
- **Today:** 1–3 become the FHIR event timing codes AC…, C…, PC… (text with fixed times of day, as FHIR does not allow `when` together with `timeOfDay`). 4–7 have no such code and go into `additionalInstruction` as localized text, WARNING without clinical loss. The reverse direction recognises the text.
- **CH EMED 7 working group:** According to the working group draft, `when` codes other than MORN/NOON/EVE/NIGHT give a validation warning in CH EMED 6, so AC…, C…, PC… for 1–3 do as well. As an alternative, the draft names SNOMED CT codes for “before/after a meal” in `additionalInstruction`. Nothing is decided.
- **Question:** How should 4–7 be treated?

  - [ ] “Empty stomach” should count as clinical loss
  - [ ] A code from a pinned terminology (for example SNOMED CT) should be set: …
  - [ ] Text without clinical loss is right

### C Prescription

Repetitions, dispense quantities, substitution and receiver of prescriptions.

#### C1 Meaning of `Rep`

Rules: CHMED16A M-RX-06, M-REV-36, M-ERX-04

- **Meaning:** The specification calls `Rep` “number of repetitions in months, e.g. permanent prescription for 6 months”.
- **Today:** The converter reads it as a validity period: `validityPeriod` from the date of the prescription for `Rep` months (inclusive end). In reverse, only whole months 1–24 become `Rep`. Values above 24 are read but refused for eRezept.
- **CH EMED 7 working group:** The working group reads `Rep` the same way: repeatability in months only, 0 means a single dispense, there is no number of repetitions. Today `Rep` 0 gives no element at all; `numberOfRepeatsAllowed` = 0 would make it unambiguous.
- **Question:** What does `Rep` mean, and how should values above 24 months be treated?

  - [ ] Validity in months (as today)
  - [ ] Number of repetitions
  - [ ] Values above 24: …

#### C2 Absent repetition

Rules: CHMED23A M23-RX-05

- **Meaning:** If `reps` is absent, the specification treats it as “Number 1”: the prescription may be redeemed once more. It is non-repeatable only with `v` = 0.
- **Today:** The converter sets no defaults for clinically relevant fields (constitution II). `numberOfRepeatsAllowed` then stays absent, and FHIR gives an absent value no meaning. A receiver might read “no repetition”.
- **CH EMED 7 working group:** What an absent `numberOfRepeatsAllowed` means is open. Working group proposal: absent = number undefined, 0 = a single dispense. FHIR change request FHIR-58711 (R6): absent = no repeats. HL7 is considering an additional element (as of 2026-11-12).
- **Question:** Should the converter make an exception here?

  - [ ] Yes: write `numberOfRepeatsAllowed` = 1 and report it as derived
  - [ ] No: leave it absent
  - [ ] Other: …

#### C3 Repetition period under one day

Rules: CHMED23A M23-RX-04

- **Meaning:** `reps` can have a duration in seconds, minutes or hours: a prescription that may be repeated only for some hours.
- **Today:** Validity is computed in whole days; such durations are lost with clinical loss.
- **Question:** Does this occur in practice?

  - [ ] No, the report entry is enough
  - [ ] Yes, like this: …

#### C4 Number of packages

Rules: CHMED16A M-RX-05 · CHMED23A M23-RX-06

- **Meaning:** `NbPack`/`nbPack` is the number of packages to be delivered, default 1. The package follows from the product (usually its GTIN).
- **Today:** It becomes `dispenseRequest.quantity`, which FHIR defines as the amount per dispense. The default is not set.
- **Question:** Does the number apply to each dispense or to the whole prescription when repetitions are allowed? Example: 2 packages, 3 repetitions.

  - [ ] Per dispense (2 each time)
  - [ ] For the whole prescription (2 in total)
  - [ ] Other: …

#### C5 Several posologies of one medicament

Rules: CHMED23A M23-RX-02, research R10

- **Meaning:** A prescribed medicament can have several posologies, for example a tapering scheme in four periods (official example 4).
- **Today:** The profile allows one base dosage per MedicationRequest. Four requests result, each with a copy of the dispense data including `nbPack`.
- **CH EMED 7 working group:** The draft proposes for CH EMED 7 to use `timing.repeat.boundsPeriod` only for the single dosage element and to give the overall duration separately. A tapering scheme could then stay in one request with several dosages.
- **Question:** A pharmacy might dispense the packages four times. Which variant is right?

  - [ ] Several requests (today)
  - [ ] One request with the dosage as text (loses the structure)
  - [ ] Other: …

#### C6 Substitution

Rules: CHMED16A M-RX-07, M-REV-37 · CHMED23A M23-RX-07

- **Meaning:** `Subs` 1 or `isNotSub` true: do not substitute. 0 or false (default): substitution allowed.
- **Today:** “Do not substitute” → code `N`; “allowed” → code `E` (equivalent). If absent, nothing is written; FHIR and CH EMED EPR read that as “allowed”. In reverse, any other code becomes “do not substitute” (the safer reading) with clinical loss.
- **Question:** Is `E` the right code for “substitution allowed”?

  - [ ] Yes
  - [ ] No, better: …

#### C7 `sic`

Rules: CHMED23A M23-RX-08

- **Meaning:** “Sic erat scriptum”: the physician confirms to the pharmacist that the prescription was chosen deliberately, exactly so. It does not forbid substitution.
- **Today:** CH EMED has no element for it; it is lost with clinical loss.
- **CH EMED 7 working group:** In the draft, dropping it is not considered an option (text or a field of its own). IHE and EU MPD have an `offLabel` extension; whether it fits is open.
- **Question:** Should it go along as text so that the pharmacy still sees it?

  - [ ] Yes, for example in `MedicationRequest.note`
  - [ ] No, the report entry is enough
  - [ ] Other: …

#### C8 Receiver of the prescription

Rules: CHMED16A M-RX-09, M-REV-39 · CHMED23A M23-RX-09 · see E3

- **Meaning:** `Rcv`/`rec` is the GLN of the healthcare professional to whom the prescription is sent electronically.
- **Today:** It becomes `dispenseRequest.performer` as a logical reference with the GLN. Towards the EPR this element is removed (question E3).
- **Question:** Is the logical reference enough?

  - [ ] Yes
  - [ ] No: …

#### C9 Postal codes in eRezept

Rules: CHMED16A M-ERX-04

- **Meaning:** The eRezept rules allow 4 or 5 digits for `Zip`.
- **Today:** A foreign address with another postal code breaks the rule, and no eRezept is produced.
- **Question:** How should foreign addresses be treated?

  - [ ] Refusing is right
  - [ ] Otherwise: …

#### C10 GLN and GTIN check digits

Rules: CHMED23A C23-OBJ-15, research F7

- **Meaning:** GLN and GTIN have 13 digits with a GS1 check digit. The CHMED23A specification does not constrain the format. Of the four GLNs in the official examples, three are placeholders with 11 or 12 digits; all 16 GTINs are valid.
- **Today:** A wrong length or check digit gives a WARNING, and the value is carried unchanged. The .NET reference implementation refuses invalid GLNs; CH EMED EPR requires valid GLNs.
- **Question:** Should the converter refuse invalid GLNs?

  - [ ] No, a WARNING is enough (today)
  - [ ] Yes, in prescriptions
  - [ ] Yes, always
  - [ ] Other: …

### D Content of the medication plan

Plan data for which the CH EMED Medication Card has no place.

#### D1 Medical data

Rules: CHMED16A M-MDT-01 · CHMED23A M23-MDT-01

- **Meaning:** Weight and height, risk categories (for example renal insufficiency, pregnancy, allergies), first day of the last menstruation, premature birth and time of gestation.
- **Today:** The Medication Card has no place for them; each element is lost with clinical loss.
- **Question:** Risks such as allergies or pregnancy matter for dispensing. Should they at least go along as text?

  - [ ] Yes, as text, for example in the remarks section
  - [ ] Yes, but differently: …
  - [ ] No, the report entry is enough

#### D2 Prescriber

Rules: CHMED16A M-MED-07 · CHMED23A M23-MED-07

- **Meaning:** `PrscbBy`/`prscbBy` names in the plan who prescribed the medicament (GLN or designation); in CHMED16A it can also mark self-medication.
- **Today:** Mapped to no element, WARNING.
- **Question:** Should it go along?

  - [ ] Yes, as text in `MedicationStatement.note`
  - [ ] Yes, with a GLN as a reference to the person
  - [ ] No
  - [ ] Other: …

#### D3 Validation of the plan

Rules: CHMED16A M-DOC-07

- **Meaning:** `ValBy` and `ValDt`: the GLN of the healthcare professional who validated the plan, and the date.
- **Today:** Mapped to no element, WARNING without clinical loss.
- **CH EMED 7 working group:** The working group discusses recording the verification of the whole plan, for example with the IHE MeOw extension `verificationInformation` (verifier and time) on MedicationStatement. Low priority, possibly only in the next revision.
- **Question:** Is the validation clinically relevant?

  - [ ] Yes, keep it (for example as an attester)
  - [ ] No
  - [ ] Other: …

#### D4 Status towards eMediplan

Rules: CHMED16A M-REV-10, M-REV-32

- **Meaning:** CHMED16A has no status of a medicament.
- **Today:** Active, intended and completed entries are written; on-hold, unknown and draft ones too, with clinical loss; stopped, not-taken and cancelled ones are left out, with clinical loss; entered-in-error ones are left out.
- **Question:** Does an on-hold medication belong on the plan?

  - [ ] Yes (today)
  - [ ] No
  - [ ] Other: …

#### D5 Document titles

Rules: CHMED16A M-RX-01, M-DOC-08

- **Meaning:** Titles and section titles follow the patient's language.
- **Today:** Prescription: “Rezept” / “Ordonnance” / “Prescrizione”, section for example “Arzneimittelverordnung”.
- **Question:** Are these the terms the receivers expect?

  - [ ] Yes
  - [ ] No, better: …

### E CH EMED EPR: removed elements

CH EMED EPR does not support some elements of CH EMED; they are removed. Clinically relevant ones give clinical loss, the others only an INFORMATION.

#### E1 List of clinically relevant elements

Rules: ch-emed-epr.md, Unsupported elements · M-EPR-22

- **Meaning:** Marked as clinical are: timing of a base dosage (weekdays, times of day, frequency, duration), text of a split dosage, body site, method, maximum dose, status reason, reason and period of a treatment, course of therapy, detected issues of prescriptions, and the values of an observation.
- **Today:** Removing them gives clinical loss; all others (for example `meta`, encounter, note author) give only INFORMATION.
- **Question:** Is the list complete and right?

  - [ ] Yes
  - [ ] Add or remove: …

#### E2 Self-medication

Rules: MedicationStatement.category · M-MED-06, M23-MED-06

- **Meaning:** Self-medication from eMediplan (`AutoMed`/`autoMed`) is held in CH EMED in `MedicationStatement.category` (`patientspecified`).
- **Today:** Marked not clinical; on the way eMediplan → CH EMED → EPR, “self-medication” is lost without clinical loss.
- **Question:** Should this count as clinical loss?

  - [ ] Yes
  - [ ] No
  - [ ] Other: …

#### E3 Receiver of the prescription in the EPR

Rules: MedicationRequest.dispenseRequest.performer · see C8

- **Meaning:** This holds the receiver of a prescription (`Rcv`/`rec` from eMediplan).
- **Today:** Marked not clinical and removed towards the EPR with INFORMATION.
- **Question:** Is that right?

  - [ ] Yes
  - [ ] No, clinical loss
  - [ ] Other: …

#### E4 Status reason and detected issues of a dispense (likely inconsistency)

Rules: MedicationDispense.statusReason[x], .detectedIssue

- **Meaning:** On `MedicationRequest` exactly these two elements are clinical, on `MedicationDispense` they are not.
- **Today:** On a dispense they are removed with INFORMATION.
- **Question:** Should they be clinical as on the prescription? A correction changes report entries and needs a CHANGELOG entry.

  - [ ] Yes, clinical
  - [ ] No, deliberately not clinical
  - [ ] Other: …

#### E5 Days of supply of a dispense

Rules: MedicationDispense.daysSupply

- **Meaning:** For how many days the dispensed amount lasts.
- **Today:** Marked not clinical, removed with INFORMATION.
- **Question:** Is that right?

  - [ ] Yes
  - [ ] No, clinical loss
  - [ ] Other: …

#### E6 Priority of a prescription

Rules: MedicationRequest.priority

- **Meaning:** For example “urgent”.
- **Today:** Marked not clinical, removed with INFORMATION.
- **Question:** Is that right?

  - [ ] Yes
  - [ ] No, clinical loss
  - [ ] Other: …

---

Compiled from the mapping specifications in the repository. The answers go into the mapping specifications; changes to the mapping follow in a feature of their own with a CHANGELOG entry.
