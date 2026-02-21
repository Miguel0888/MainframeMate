# Anforderungsspezifikation: Neuimplementierung der Klasse zur Zeilennummerierung von Natural-Quelltext

**Paket:** com.softwareag.naturalone.natural.auxiliary.renumber.internal  
**Datum:** 2026-02-21  
**Status:** Entwurf

---

## 1. Zweck

Die Klasse ist ein Werkzeug zur Verwaltung von Zeilennummern in Natural-Quelltextzeilen. Natural-Quellcode verwendet 4-stellige Zeilennummern (Format `NNNN`, z. B. `0010`) als Zeilenpräfix und referenziert diese in Anweisungen über das Format `(NNNN)`, `(NNNN,` oder `(NNNN/`.

Die Klasse ist als **rein statische Werkzeugklasse** (kein öffentlicher Konstruktor) implementiert und bietet drei Hauptoperationen:

1. **Hochladen (Zeilennummern hinzufügen):** Zeilennummern an Quelltext anfügen und Referenzen umrechnen.
2. **Herunterladen (Zeilennummern entfernen):** Zeilennummern entfernen und Referenzen normalisieren oder durch Labels ersetzen.
3. **Aktualisieren (Zeilenreferenzen verschieben):** Bestehende Zeilenreferenzen um ein Offset verschieben.

---

## 2. Konstanten

| Name | Wert | Bedeutung |
|---|---|---|
| Maximale Zeilennummer | 9999 | Höchste darstellbare 4-stellige Zeilennummer |
| Länge einer Zeilenreferenz | 6 | Zeichenlänge inkl. Klammern, z. B. `(0010)` |

---

## 3. Schnittstelle zur Label-Steuerung

Wird von der Methode „Zeilennummern entfernen" verwendet, um das Label-Verhalten zu steuern. Die Schnittstelle definiert drei Abfragen:

| Abfrage | Bedeutung |
|---|---|
| Sollen Labels eingefügt werden? | Wahr = Labels statt numerischer Referenzen verwenden |
| Wie lautet das Label-Format? | Format-Zeichenkette für die Label-Generierung (z. B. `"L%d."`) |
| Soll das Label als eigene Zeile eingefügt werden? | Wahr = Label wird als separate Zeile vor der Inhaltszeile eingefügt |

---

## 4. `isLineReference(int pos, String line)` — syntaktische Prüfung

### 4.1 Eingabe

- **Position** *p*: Ganzzahl, Position im Zeichenstring
- **Zeile** *z*: Zeichenkette, die gesamte Quelltext-Zeile

### 4.2 Ausgabe

Wahrheitswert (wahr / falsch).

### 4.3 Zweck

Prüft, ob an Position *p* in der Zeichenkette *z* eine syntaktisch gültige Zeilenreferenz steht.

### 4.4 Gültiges Format

Eine Zeilenreferenz besteht aus genau 6 Zeichen und erfüllt das folgende Muster:

```
( + genau 4 ASCII-Ziffern + eines von: ) / ,
```

Beispiele: `(0010)`, `(0010/`, `(0010,`

### 4.5 Regeln

| Bedingung | Ergebnis |
|---|---|
| *p* < 0 | falsch |
| *p* + 6 > Länge von *z* | falsch |
| *p* + 6 ≤ Länge von *z* und die 6 Zeichen ab *p* entsprechen dem Muster | wahr |
| Teilzeichenkette entspricht dem Muster nicht | falsch |

### 4.6 Randfälle

- *p* < 0: Ergebnis ist `falsch`.
- *z* ist leer: *p* + 6 > 0 → `falsch`.
- *z* ist nicht übergeben (Nullwert): Laufzeitfehler ist erlaubt.

---

## 5. `isLineNumberReference(int pos, String line, boolean insertLabelsMode, boolean hasLineNumberPrefix, boolean renConst)` — semantische Prüfung

### 5.1 Eingabe

| Parameter | Bedeutung |
|---|---|
| Position *p* | Position der öffnenden Klammer `(` in der Zeichenkette |
| Zeile *z* | Die gesamte Quelltext-Zeile |
| Label-Modus aktiv? | Wahr = Label-Modus aktiv; beeinflusst Rückgabe bei Kommentaren |
| Hat Zeilennummern-Präfix? | Wahr = Zeile beginnt mit 4-stelliger Zeilennummer + Leerzeichen (5 Zeichen) |
| Konstanten umschreiben? | Wahr = Referenzen innerhalb von Zeichenketten-Literalen (`'...'` / `"..."`) gelten als gültig |

### 5.2 Ausgabe

Wahrheitswert (wahr / falsch).

### 5.3 Zweck

Prüft, ob an Position *p* eine **semantisch gültige** Zeilenreferenz steht, unter Berücksichtigung von Kommentaren und Zeichenketten-Literalen.

### 5.4 Algorithmus

1. **Wenn *p* = −1** → sofort `falsch`.
2. **Wenn die syntaktische Prüfung (§4) an Position *p* fehlschlägt** → `falsch`.
3. **Kommentar-Versatz** berechnen:
   - Versatz = 0, wenn kein Zeilennummern-Präfix vorhanden.
   - Versatz = 5, wenn Zeilennummern-Präfix vorhanden.
4. **Natural-Kommentar-Erkennung** (Zeile beginnt am berechneten Versatz mit einem Kommentar-Marker):
   - Wenn die Zeichenkette `"* "` oder `"**"` oder `"*/"` genau am berechneten Versatz beginnt:
     - Rückgabe: **Negation des Label-Modus** (d. h. im Nicht-Label-Modus gilt die Referenz als gültig; im Label-Modus nicht).
5. **Block-Kommentar-Erkennung** (Zeichenfolge `/*` VOR Position *p*):
   - Durchlaufe alle Zeichen vor *p*.
   - Wenn `/*` gefunden wird (und die Stelle nicht innerhalb eines Zeichenketten-Literals liegt):
     - Rückgabe: **Negation des Label-Modus**.
6. **Zeichenketten-Literal-Erkennung** (Anführungszeichen `'` und `"` VOR Position *p*):
   - Verwende Umschaltvariablen für einfache und doppelte Anführungszeichen.
   - Wenn *p* innerhalb eines offenen Zeichenketten-Literals liegt:
     - Rückgabe: der Wert des Parameters „Konstanten umschreiben?".
7. **Sonst:** `wahr`.

### 5.5 Wichtige Semantik

- Ein Block-Kommentar `/*` **innerhalb** eines Zeichenketten-Literals wird **nicht** als Block-Kommentar erkannt.
- Die Prüfreihenfolge ist: Natural-Kommentar → Block-Kommentar → Zeichenketten-Literal → gültig.
- Der Label-Modus invertiert das Ergebnis bei Kommentar-Kontext: im Label-Modus dürfen in Kommentaren keine Labels, sondern nur numerische Referenzen stehen.

---

## 6. `addLineNumbers(String[] source, int step, String labelPrefix, boolean updateRefs, boolean openSystemsServer, boolean renConst)`

### 6.1 Eingabe

| Parameter | Bedeutung |
|---|---|
| Quellzeilen *Q* | Feld von Zeichenketten (die unnummerierten Quelltextzeilen) |
| Schrittweite *s* | Ganzzahl, Abstand zwischen aufeinanderfolgenden Zeilennummern (z. B. 10 → 0010, 0020, 0030, …) |
| Label-Präfix *L* | Zeichenkette zur Erkennung von Label-Definitionen (z. B. `"!"` oder `"L"`). **Darf nicht leer/fehlend sein** (führt zu Laufzeitfehler). |
| Referenzen umrechnen? | Wahr = bestehende `(NNNN)`-Referenzen auf die neuen Schrittweiten umrechnen |
| Offene-Systeme-Server? | Wahr = ein zusätzliches Leerzeichen am Zeilenende anhängen |
| Konstanten umschreiben? | Durchgereicht an die semantische Referenzprüfung (§5): Referenzen in Zeichenketten-Literalen umrechnen? |

### 6.2 Ausgabe

Feld von Zeichenpuffern: jede Ergebnis-Zeile enthält die 4-stellige Zeilennummer, gefolgt von einem Leerzeichen und dem (ggf. modifizierten) Quelltext.

### 6.3 Zweck

Fügt jeder Quelltext-Zeile eine 4-stellige Zeilennummer als Präfix hinzu. Arbeitet in zwei Modi: **Normal-Modus** und **Label-Modus**.

### 6.4 Schrittweiten-Normalisierung

1. Wenn *s* = 0 → setze *s* auf 1.
2. Wenn *s* × Anzahl(Quellzeilen) > 9999 → Schrittweite wird reduziert:
   - Beginne mit *s* = 10.
   - Solange *s* > 1 UND ⌊9999 / Anzahl(Quellzeilen)⌋ < *s*: halbiere *s* (ganzzahlig: *s* = *s* / 2).
   - Ergebnis: die größte Zweierpotenz-Teilung von 10, die passt (10 → 5 → 2 → 1).

### 6.5 Label-Modus-Erkennung (Phase 1)

Vor der eigentlichen Nummerierung wird geprüft, ob die Quellzeilen Labels enthalten:

1. Durchlaufe alle Zeilen.
2. Suche das Label-Präfix *L* in jeder Zeile.
3. Wenn *L* an Position 0 einer Zeile gefunden wird:
   - Suche das nächste `'.'` nach dem Präfix.
   - Wenn ein `'.'` gefunden wird und die Zeichenkette zwischen dem Ende des Präfixes und dem `'.'` eine rein numerische Ganzzahl darstellt:
     - **Label-Modus wird aktiviert** → Abbruch der Suche.
4. Wenn kein solches Label-Muster gefunden wird: **Normal-Modus**.

### 6.6 Normal-Modus (Phase 2a, kein Label-Modus)

Für jede Zeile *i* (0-basiert, d. h. *i* ∈ {0, 1, …, n−1}):

1. Berechne die Zeilennummer: *Z* = (*i* + 1) × *s*.
2. Formatiere die Ergebnis-Zeile: 4-stellig formatiertes *Z* + Leerzeichen + Quelltext.
3. **Wenn „Referenzen umrechnen" aktiv:** Alle `(NNNN)`-Referenzen in der Zeile suchen und umrechnen:
   - Für jede gefundene Referenz `(NNNN)`:
     - Prüfe mit der semantischen Referenzprüfung (§5), ob es sich um eine gültige Referenz handelt (ohne Label-Modus, ohne Zeilennummern-Präfix).
     - Lese die 4 Ziffern als Ganzzahl → *R* (referenzierte Originalzeilennummer).
     - **Nur Rückwärts-Referenzen umrechnen:** Wenn *R* ≤ *i* + 1:
       - Neuer Wert: *R* × *s*.
       - Ersetze die 4 Ziffern durch den 4-stellig formatierten neuen Wert.
     - Vorwärts-Referenzen (*R* > *i* + 1) bleiben unverändert.
4. **Wenn „Offene-Systeme-Server" aktiv:** Ein Leerzeichen am Ende der Zeile anhängen.

### 6.7 Label-Modus (Phase 2b)

Im Label-Modus wird eine Zuordnungstabelle (Label → Zeilennummer) aufgebaut und verwendet.

Für jede Zeile *i*:

1. Berechne die Zeilennummer: *Z* = (*i* + 1) × *s*.
2. Die Ergebnis-Zeile beginnt mit dem 4-stellig formatierten *Z*.
3. Suche alle Vorkommen des Label-Präfixes *L* in der Zeile und klassifiziere:

#### 6.7.1 Label-Definition (Zeilenanfang)

Wenn das Label-Präfix *L* an **Position 0** der Quellzeile steht, wird es als Label-Definition erkannt:

- Suche den nächsten `'.'` nach dem Präfix.
- Wenn ein `'.'` gefunden wird und das Label **noch nicht** in der Zuordnungstabelle ist:
  - Trage das Label mit seiner Zeilennummer in die Tabelle ein (z. B. `!1.` → `0010`).
  - Der Quelltext nach dem Label (nach dem `'.'`, ggf. nach einem folgenden Leerzeichen) wird als eigentlicher Zeileninhalt übernommen.
- Wenn das Label **bereits** in der Tabelle ist: Das Duplikat wird ignoriert; der gesamte Zeileninhalt (inkl. Label) wird unverändert übernommen.

#### 6.7.2 Label-Definition (nur Leerraum davor)

Ein Label-Präfix, vor dem **ausschließlich Leerraum** (Leerzeichen oder Tabulator) steht, soll ebenfalls als Label-Definition erkannt werden. Von der Position des Präfixes wird rückwärts geprüft, ob alle vorhergehenden Zeichen Leerzeichen **oder** Tabulatoren sind. Wenn ja, gilt das Präfix als Label-Definition am Zeilenanfang.

#### 6.7.3 Label-Referenz (nach öffnender Klammer)

Wenn das Label-Präfix direkt nach einer öffnenden Klammer `(` steht (d. h. das Zeichen unmittelbar vor dem Präfix ist `(`):

- Suche das Ende der Label-Referenz: Punkt gefolgt von `)`, oder Punkt gefolgt von `/`, oder Punkt gefolgt von `,` (in dieser Reihenfolge).
- Wenn gefunden und das Label in der Zuordnungstabelle existiert: Ersetze das Label durch die zugeordnete Zeilennummer.
- Wenn das Label nicht in der Tabelle ist oder kein gültiges Ende gefunden wird: Keine Änderung.

#### 6.7.4 Zeileninhalt nach Label-Verarbeitung

- Nach der Label-Definition wird ein Leerzeichen angefügt.
- Wenn noch Inhalt nach dem Label vorhanden ist (Startposition < Zeilenlänge): Dieser wird angehängt.
- Wenn die Startposition genau der Zeilenlänge entspricht (Zeile besteht nur aus dem Label): Es wird nur die Zeilennummer gefolgt von einem Leerzeichen erzeugt.
- Wenn „Offene-Systeme-Server" aktiv und Inhalt vorhanden: Zusätzliches Leerzeichen am Ende.

### 6.8 Randfälle

| Fall | Verhalten |
|---|---|
| Quellzeilen nicht übergeben (Nullwert) | Laufzeitfehler (Nullzeiger) |
| Quellzeilen-Feld ist leer (Länge = 0) | Leeres Ergebnis-Feld |
| Label-Präfix nicht übergeben (Nullwert) | Laufzeitfehler (Nullzeiger bei Teilzeichenketten-Suche) |
| Ein Element im Quellzeilen-Feld ist Nullwert | Laufzeitfehler (Nullzeiger bei Zeichenpuffer-Erzeugung) |
| Zeile besteht nur aus dem Label (z. B. `!1.`) | Ergebnis: `"NNNN "` (Zeilennummer + Leerzeichen, kein weiterer Inhalt) |
| Schrittweite negativ | Negative Zeilennummern werden erzeugt (kein Schutz) |
| Schrittweite × Zeilenanzahl → ganzzahliger Überlauf | Undefiniertes Verhalten |

---

## 7. `removeLineNumbers(List<StringBuffer> source, boolean updateRefs, boolean renConst, int prefixLength, int step, IInsertLabels insertLabels)`

### 7.1 Eingabe

| Parameter | Bedeutung |
|---|---|
| Quellzeilen *Q* | Liste von Zeichenpuffern (Zeilen mit Zeilennummern). **ACHTUNG:** Die Funktion **verändert** die Eingabe-Puffer direkt! |
| Referenzen umschreiben? | Wahr = `(NNNN)`-Referenzen werden umgeschrieben |
| Konstanten umschreiben? | Referenzen in Zeichenketten-Literalen umschreiben? |
| Präfixlänge *P* | Anzahl der zu entfernenden Zeichen am Zeilenanfang (z. B. 5 für `NNNN `) |
| Schrittweite *s* | Ursprüngliche Schrittweite der Zeilennummern |
| Label-Steuerung | Optionales Objekt gemäß der Schnittstelle aus §3; Nullwert = kein Label-Modus |

### 7.2 Ausgabe

Feld von Zeichenketten: die Quellzeilen ohne Zeilennummern-Präfix, mit normalisierten oder durch Labels ersetzten Referenzen.

### 7.3 Zweck

Entfernt Zeilennummern-Prefixe und normalisiert bzw. ersetzt Zeilenreferenzen.

### 7.4 Phase 1: Referenzen umschreiben (wenn „Referenzen umschreiben" aktiv)

Für jede Zeile (mit 1-basiertem Zähler *k*):

1. Suche alle Vorkommen von `(` in der Zeile.
2. Für jede Fundstelle an Position *p*:
   - Prüfe mit der semantischen Referenzprüfung (§5, ohne Label-Modus, mit Zeilennummern-Präfix) → Ergebnis: „Referenz gültig?" (*V*).
   - Wenn die Label-Steuerung vorhanden ist UND „Labels einfügen" aktiviert ist UND *V* = wahr:
     - Prüfe erneut mit der semantischen Referenzprüfung (§5, **mit** Label-Modus, mit Zeilennummern-Präfix) → Ergebnis: „Label erlaubt?" (*E*).
   - Sonst: *E* = falsch.
3. Wenn *V* = wahr:
   - Lese die Zeilennummer aus den ersten 4 Zeichen der aktuellen Zeile → *A* (aktuelle Zeilennummer).
   - Lese die referenzierte Nummer aus der Referenz → *R* (referenzierte Zeilennummer).
   - **Nur Rückwärts-Referenzen:** Wenn *R* > 0 UND *R* ≤ *A*:
     - Suche rückwärts in der Quellzeilenliste diejenige Zeile, deren erste 4 Zeichen mit *R* übereinstimmen.
     - Wenn gefunden (an Index *t*):
       - **Wenn *E* = falsch** (numerische Ersetzung):
         - Neue Nummer = *t* + 1 + (wenn „Label als eigene Zeile" aktiv: Anzahl Einträge in der Label-Tabelle, sonst: 0).
         - Ersetze die 4 Ziffern der Referenz durch die 4-stellig formatierte neue Nummer.
       - **Wenn *E* = wahr** (Label-Ersetzung):
         - Prüfe, ob die Zielzeile ein bereits existierendes Label hat (siehe §9).
         - **Wenn ja:** Verwende das existierende Label.
         - **Wenn nein:**
           - Prüfe, ob in der Label-Tabelle bereits ein Label für diese Referenznummer existiert → wiederverwenden.
           - Andernfalls: Erzeuge ein neues Label mit dem Label-Format und einem hochzählenden Zähler.
           - **Kollisionsprüfung:** Solange das erzeugte Label als Teilzeichenkette im Quelltext vorkommt (siehe §10), erzeuge das nächste Label.
           - Trage das Label in die Tabelle ein (Schlüssel = 4-stellige Referenznummer).
         - Ersetze die 4 Ziffern der Referenz durch das Label.

### 7.5 Phase 2: Präfix entfernen und Labels einfügen

Für jede Zeile:

1. **Zeilenlänge > 4:**
   - Wenn Label-Modus aktiv und Label-Tabelle nicht leer:
     - Prüfe, ob die ersten 4 Zeichen der Zeile als Schlüssel in der Label-Tabelle existieren.
   - **Wenn Label gefunden:**
     - Falls „Label als eigene Zeile" aktiv: Füge das Label als eigenständige Zeile **vor** der Inhaltszeile ein; entferne die ersten *P* Zeichen der Inhaltszeile.
     - Falls „Label als eigene Zeile" nicht aktiv: Ersetze die ersten 4 Zeichen der Zeile durch das Label.
   - **Wenn kein Label:** Entferne die ersten *P* Zeichen der Zeile.
2. **Zeilenlänge = 4:** Lösche den gesamten Inhalt → leere Zeichenkette.
3. **Zeilenlänge < 4:** Zeile bleibt unverändert.
4. Füge die resultierende Zeichenkette in die Ergebnisliste ein.

### 7.6 Randfälle

| Fall | Verhalten |
|---|---|
| Quellzeilen nicht übergeben (Nullwert) | Laufzeitfehler (Nullzeiger) |
| Quellzeilenliste ist leer | Leeres Zeichenketten-Feld als Ergebnis |
| Label-Steuerung nicht übergeben (Nullwert) | Kein Label-Modus, rein numerische Ersetzung |
| Label-Steuerung vorhanden, aber „Labels einfügen" = falsch | Label-Modus deaktiviert |
| Label-Format der Steuerung ist Nullwert | Laufzeitfehler (Nullzeiger bei Formatierung) |
| Vorwärts-Referenz (*R* > *A*) | Referenz bleibt unverändert |
| Referenz zeigt auf nicht-existierende Zeile | Referenz bleibt unverändert |
| Präfixlänge > Zeilenlänge | Die Löschoperation begrenzt sich auf die Zeilenlänge (leere Zeichenkette) |
| Leerer Zeichenpuffer in der Liste | Bleibt unverändert (Länge < 4) |
| Mehrere Referenzen auf dieselbe Zeile | Gleiches Label wird wiederverwendet |
| Mehrere Referenzen in einer Zeile | Alle werden einzeln verarbeitet |
| Seiteneffekt: Eingabe-Puffer werden verändert | **JA** – die Zeichenpuffer-Objekte in der Liste werden direkt modifiziert |

---

## 8. `updateLineReferences(String[] source, int delta, boolean renConst)`

### 8.1 Eingabe

| Parameter | Bedeutung |
|---|---|
| Quellzeilen *Q* | Feld von Zeichenketten |
| Versatz *d* (Delta) | Ganzzahl, um die jede Referenz verschoben wird |
| Konstanten umschreiben? | Referenzen in Zeichenketten-Literalen umschreiben? |

### 8.2 Ausgabe

Dasselbe Feld von Zeichenketten wie die Eingabe (das Feld wird direkt verändert und zurückgegeben).

### 8.3 Zweck

Verschiebt alle Zeilenreferenzen `(NNNN)` im Quelltext um den Versatz *d*.

### 8.4 Algorithmus

Für jede Zeile *i* (0-basiert):

1. Suche alle Vorkommen von `(` in der Zeile.
2. Für jede Fundstelle:
   - Prüfe mit der semantischen Referenzprüfung (§5, ohne Label-Modus, ohne Zeilennummern-Präfix).
   - Lese die 4 Ziffern als Ganzzahl → *R*.
   - Berechne den neuen Wert: *R'* = *R* + *d*.
   - **Nur ersetzen wenn:** *R'* > 0 UND *R'* ≤ *i* + 1.
   - Ersetze die 4 Ziffern durch den 4-stellig formatierten Wert von *R'*.
3. **Rückgabe:** Dasselbe Feld-Objekt wie die Eingabe (Seiteneffekt: Eingabe wird direkt verändert).

### 8.5 Randfälle

| Fall | Verhalten |
|---|---|
| Quellzeilen nicht übergeben (Nullwert) | Laufzeitfehler (Nullzeiger) |
| Leeres Feld | Leeres Feld zurück (dasselbe Objekt) |
| *d* = 0 | Referenzen werden geprüft, aber nur ersetzt wenn *R* ≤ *i* + 1 (effektiv keine Änderung) |
| *R'* ≤ 0 | Nicht ersetzt |
| *R'* > *i* + 1 | Nicht ersetzt (Vorwärts-Referenzen werden nicht verschoben) |
| Ein Element im Feld ist Nullwert | Laufzeitfehler (Nullzeiger) |
| Leere Zeichenkette als Element | Keine Klammer gefunden → keine Änderung |
| Rückgabe ist dasselbe Objekt | **JA** – das Eingabe-Feld wird direkt verändert und zurückgegeben |

---

## 9. `getExistingLabel(String lineContent)` — Hilfsfunktion

### 9.1 Zweck

Erkennt ein bereits vorhandenes Label am Anfang eines (von Leerraum befreiten) Zeileninhalts.

### 9.2 Muster

```
Zeilenanfang + null oder mehr Buchstaben (a–z, A–Z) + null oder mehr Ziffern (0–9) + Punkt
```

### 9.3 Beispiele

| Eingabe | Ergebnis |
|---|---|
| `L1.CODE` | `L1.` |
| `ABC.REST` | `ABC.` |
| `123.REST` | `123.` |
| `MyLabel1.X` | `MyLabel1.` |
| `.CODE` | `.` (Achtung: leeres Label!) |
| `CODE` | kein Treffer (kein Punkt) |
| `9.REST` | `9.` |

### 9.4 Anforderung

- Das Suchmuster soll als unveränderliche Konstante definiert werden. Dies stellt die Threadsicherheit sicher.

---

## 10. `searchStringInSource(List<StringBuffer> source, String searchString)` — Hilfsfunktion

### 10.1 Zweck

Prüft, ob eine gegebene Zeichenkette als Teilzeichenkette in irgendeiner Zeile des Quelltexts vorkommt. Wird zur Label-Kollisionserkennung verwendet.

### 10.2 Regeln

- Wenn die Suchzeichenkette nicht übergeben ist (Nullwert) → `falsch`.
- Es wird ein exakter Teilzeichenketten-Vergleich durchgeführt.
- Die Suche bricht beim ersten Treffer ab.

---

## 11. Nicht-funktionale Anforderungen

### 11.1 Code-Qualität

- **Sprechende Bezeichner** für alle Variablen, Parameter und Methoden.
- **Typisierte Sammlungen** durchgängig verwenden (keine untypisiert deklarierten Sammlungen).
- **Defensive Prüfungen auf Nullwert** für alle öffentlichen Parameter, mit klarer Fehlermeldung.

### 11.2 Threadsicherheit

- Statische Suchmuster-Felder sollen als unveränderliche Konstanten deklariert werden.

### 11.3 Rückwärtskompatibilität

- Alle bestehenden Testklassen **MÜSSEN** nach der Neuimplementierung weiterhin bestehen.

### 11.4 Performanz

- Keine besonderen Anforderungen; die Funktionen werden typischerweise mit wenigen hundert bis tausend Zeilen aufgerufen.

---

## 12. Zusammenfassung der Schnittstellen

Die Klasse stellt folgende öffentliche Funktionen bereit:

| Funktion | Eingabe | Ausgabe |
|---|---|---|
| Ist Zeilenreferenz (§4) | Position, Zeile | Wahrheitswert |
| Ist Zeilennummern-Referenz (§5) | Position, Zeile, Label-Modus?, Präfix?, Konstanten? | Wahrheitswert |
| Zeilennummern hinzufügen (§6) | Quellzeilen, Schrittweite, Label-Präfix, Ref. umrechnen?, Offene Systeme?, Konstanten? | Feld von Zeichenpuffern |
| Zeilennummern entfernen (§7) | Quellzeilen, Ref. umschreiben?, Konstanten?, Präfixlänge, Schrittweite, Label-Steuerung | Feld von Zeichenketten |
| Zeilenreferenzen verschieben (§8) | Quellzeilen, Versatz, Konstanten? | Feld von Zeichenketten (dasselbe Objekt) |

Interne Hilfsfunktionen:

| Funktion | Eingabe | Ausgabe |
|---|---|---|
| Bestehendes Label erkennen (§9) | Zeileninhalt | Gefundenes Label oder „kein Treffer" |
| Zeichenkette in Quelltext suchen (§10) | Quellzeilen, Suchzeichenkette | Wahrheitswert |
