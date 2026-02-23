# PalDate — Anforderungsspezifikation

> Einfacher, unveränderlicher Datums-/Uhrzeittyp für das NDV-Protokoll.
> Repräsentiert einen Zeitpunkt mit Genauigkeit bis zur Minute (keine Sekunden).

---

## 1. Zweck

`PalDate` ist ein schlanker Wertetyp, der Datum und Uhrzeit auf Minutengenauigkeit
speichert. Er wird innerhalb des NDV-Protokolls verwendet, um Zeitstempel von
Objekten auf dem Server darzustellen — z.B. das Erstellungsdatum, Änderungsdatum
oder Zugriffsdatum von Natural-Quellcode-Objekten.

Die Klasse ist **final** und implementiert `Serializable`.

---

## 2. Datenfelder

| Feld | Typ | Bemerkung |
|------|-----|-----------|
| Tag | Ganzzahl | 1–31 (0 = nicht gesetzt) |
| Monat | Ganzzahl | 1–12 (0 = nicht gesetzt) |
| Jahr | Ganzzahl | Wird gespeichert wie übergeben |
| Stunde | Ganzzahl | 0–23 |
| Minute | Ganzzahl | 0–59 |

Es gibt keine Sekunden und keine Zehntel — dafür existiert `PalTimeStamp`.

---

## 3. Konstruktoren

### `public PalDate()`

Erzeugt ein leeres Datum. Alle Felder sind 0.

### `public PalDate(int jahr, int monat, int tag, int stunde, int minute)`

Erzeugt ein Datum mit den angegebenen Werten.
Die Werte werden **direkt** übernommen — es findet keine Normalisierung statt.

---

## 4. Getter

| Signatur | Rückgabe |
|----------|----------|
| `public int getYear()` | Jahr (wie im Konstruktor übergeben) |
| `public int getMonth()` | Monat |
| `public int getDay()` | Tag |
| `public int getHour()` | Stunde |
| `public int getMinute()` | Minute |

Es gibt keine Setter — die Felder werden ausschließlich über den Konstruktor gesetzt.

---

## 5. Gleichheitsvergleich

### `public boolean equals(Object anderes)`

Überschreibt `Object.equals(Object)`. Vergleichslogik:

1. **Identitätsprüfung:** Wenn `anderes` dasselbe Objekt ist → `true`.
2. **Typprüfung:** Wenn `anderes` kein `PalDate` ist → `false`.
3. **Feldvergleich:** Zwei `PalDate`-Instanzen sind gleich, wenn **alle fünf Felder**
   übereinstimmen: Tag, Monat, Jahr, Stunde, Minute.

---

## 6. Hash-Code

### `public int hashCode()`

Muss konsistent zu `equals()` sein: Zwei gleiche `PalDate`-Instanzen müssen denselben
Hash-Wert liefern. Alle fünf Felder (Tag, Monat, Jahr, Stunde, Minute) müssen in
die Berechnung einfließen.

Die konkrete Hash-Formel ist ein Implementierungsdetail und kann frei gewählt werden.

---

## 7. Textdarstellung

### `public String toString()`

Gibt das Datum im Format **`TT/MM/JJJJ HH:MM`** zurück.

**Regeln:**

- Tag und Monat werden mit führender Null auf 2 Stellen aufgefüllt.
- Das Jahr wird mit führenden Nullen auf 4 Stellen aufgefüllt.
- Stunde und Minute werden mit führender Null auf 2 Stellen aufgefüllt.
- Trennzeichen: `/` zwischen Tag, Monat und Jahr; Leerzeichen zwischen Datum und
  Uhrzeit; `:` zwischen Stunde und Minute.
- Es findet **keine Jahres-Normalisierung** statt — der gespeicherte Wert wird
  direkt formatiert.

**Beispiele:**

| Felder | Ergebnis |
|--------|----------|
| Tag=5, Monat=3, Jahr=2026, Stunde=9, Minute=7 | `"05/03/2026 09:07"` |
| Tag=23, Monat=12, Jahr=98, Stunde=14, Minute=30 | `"23/12/0098 14:30"` |
| Tag=1, Monat=1, Jahr=0, Stunde=0, Minute=0 | `"01/01/0000 00:00"` |

---

## 8. Fehlerbehandlung

Keine. Die Klasse arbeitet mit einfachen Ganzzahlen und kann keine Exceptions werfen.
Im Original-Bytecode existiert ein Catch für `PalDate$Exception` — das ist ein
Decompiler-Artefakt und kann in einer Neuimplementierung weggelassen werden.

---

## 9. Abhängigkeiten

Keine. `PalDate` hängt nur von `java.io.Serializable` ab.

---

## 10. Verwendung im Protokoll

`PalDate` wird verwendet von:
- **`PalTypeObject`** — als Quellcode-Datum, GP-Datum und Zugriffsdatum
- **`PalTypeLibraryStatistics`** — als Änderungsdatum
- **`PalTimeStamp`** — als Eingabe für die Fabrikmethode `get(PalDate, String)`
