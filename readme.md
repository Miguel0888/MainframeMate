# MainframeMate – Datenimport aus Excel in Mainframe-Formate

MainframeMate hilft dir dabei, strukturierte Daten z. B. aus Excel-Dateien in festformatierte Textdateien zu konvertieren und optional direkt auf einen Mainframe-Host zu laden. Dabei unterstützt dich das Tool mit einem intuitiven Dialog und einem flexiblen Layoutsystem, das du über eine einfache JSON-Datei selbst konfigurieren kannst.

---

## Übersicht der Import-Logik

```mermaid
flowchart TD
    A[Excel-Datei auswählen] --> B[Satzart aus JSON wählen]
    B --> C[Spalten prüfen & zuordnen]
    C --> D[Formatierte Zeilen erzeugen]
    D --> E{An bestehende Datei anhängen?}
    E -- Ja --> F[Trennzeile optional einfügen]
    F --> G[In geöffneten Editor einfügen]
    E -- Nein --> G
```

---

## Excel-Import und Satzarten-Konfiguration

Damit der Import korrekt funktioniert, müssen sogenannte *Satzarten* definiert werden. Diese beschreiben:

* welche Spalten relevant sind,
* an welcher Position in der Zieldatei sie stehen sollen,
* ob an bestehende Inhalte angehängt werden soll,
* und (optional) wo die Datei gespeichert werden soll.

### Beispiel-Satzarten-Datei (JSON)

```json
{
  "KUNDEN": {
    "meta": {
      "append": true,
      "pfad": "USR.DATA.KUNDEN"
    },
    "felder": [
      { "name": "NAME",    "pos": 1,  "len": 20 },
      { "name": "ALTER",   "pos": 21, "len": 3  },
      { "name": "E-Mail",  "pos": 25, "len": 25 },
      { "name": "AKTIV",   "pos": 50, "len": 5  }
    ]
  },
  "PRODUKTE": {
    "meta": {
      "append": false,
      "pfad": "USR.DATA.PRODUKTE"
    },
    "felder": [
      { "name": "NR",     "pos": 1,  "len": 5  },
      { "name": "TITEL",  "pos": 7,  "len": 30 },
      { "name": "PREIS",  "pos": 38, "len": 10 }
    ]
  }
}
```

### Erklärungen:

| Schlüssel     | Beschreibung                                                               |
| ------------- | -------------------------------------------------------------------------- |
| `felder`      | Liste der Felddefinitionen für eine Zeile                                  |
| `name`        | Spaltenüberschrift in Excel (Groß-/Kleinschreibung wird ignoriert)         |
| `pos`         | Startposition des Feldes (1-basiert)                                       |
| `len`         | Länge des Feldes (auffüllend mit Leerzeichen)                              |
| `meta.append` | Soll der Inhalt an bestehende Datei angehängt werden? (true/false)         |
| `meta.pfad`   | Optionaler Hinweis, wohin der Import z. B. auf dem Mainframe erfolgen soll |

Zusätzlich unterstützt der Importdialog eine **Trennzeile**, die automatisch eingefügt wird, wenn die Option "Anhängen" aktiv ist. Diese lässt sich in den Einstellungen zentral vorgeben und wird bei aktivierter Append-Option mitgespeichert.

---

## Hinweise zur Bedienung

* Wird eine Satzart mit `"append": true` gewählt, ist die entsprechende Checkbox im Dialog automatisch aktiviert.
* Die Trennzeile wird **nur dann gespeichert**, wenn auch tatsächlich "Anhängen" aktiviert war.
* Beim Deaktivieren von "Anhängen" bleibt die Trennzeile sichtbar (ausgegraut), wird aber nicht gespeichert.
* Der Dateiname für neue Tabs lautet standardmäßig einfach `import` (ohne `.csv`), damit der Inhalt auch zu COBOL passt.

---

## Architekturübersicht (technisch)

```mermaid
graph TD
    A[ExcelImportPlugin] -->|öffnet| B[ExcelImportDialog]
    B -->|liefert Settings| C[SettingsManager]
    A -->|liest Datei| D[ExcelImportParser]
    A -->|konvertiert| E[formatFixedWidthBySatzart]
    E --> F[Text mit Leerzeichenpadding]
    A -->|optional anhängen| G[Bestehender Tab-Inhalt]
    A -->|schreibt| H[FileTab]
```

## Erweiterte Funktionen (seit Version 1.1)

**MainframeMate** wurde um eine Reihe hilfreicher Editor- und Konfigurationsfunktionen erweitert:

### 🎨 Farbliche Hervorhebung von Feldern

- Jeder importierte Datenblock wird auf Basis der Satzart farblich markiert.
- Statische Werte (`"value"`) erscheinen standardmäßig in **grau**.
- Optional lassen sich Farben pro Feldname in den allgemeinen **Einstellungen** überschreiben:
  - `"Feldname"` → Name des Felds (Groß-/Kleinschreibung wird ignoriert)
  - `"Farbe"` → Farbwert als HEX-Code (`#RRGGBB`)
- Die Farbüberschreibungen werden im Einstellungsdialog gepflegt.
- Doppelklick auf die Farbzelle öffnet einen **Color Picker**.

### 🧾 Mehrzeilige Satzarten mit `"row"`

- Felder können über das Attribut `"row"` in **mehrere Zeilen** verteilt werden.
- `"row"` ist optional, Standardwert: `1`.
- Auch `"value"`-Felder ohne Excel-Spalte können in beliebige Zeilen geschrieben werden.
- Beispiel:

```json
{ "name": "NAME", "pos": 1, "len": 20 },
{ "value": "Alter", "pos": 21, "len": 5, "row": 2 },
{ "name": "ALTER", "pos": 30, "len": 3, "row": 2 },
{ "name": "AKTIV", "pos": 10, "len": 1, "row": 3 }
```

### 🛠 Erweiterte Editor-Einstellungen

- Editor-Schriftart und -größe konfigurierbar.
- Zeilenende beim Speichern: `LF`, `CRLF`, oder `NONE`.
- Optional eine **vertikale Linie** z. B. bei Spalte 80 (in den allgemeinen Einstellungen).
- Inhalte werden farbig hervorgehoben, wenn Satzarten beim Import verwendet werden.

### 🧩 Farb-Overrides in JSON (experimentell)

Im JSON können alternativ zu den globalen Einstellungen auch Farbwerte pro Feld festgelegt werden:

```json
{ "name": "ALTER", "pos": 21, "len": 3, "color": "#FFA500" }
```

Die Konfiguration in den allgemeinen Einstellungen überschreibt die Farbe in der Satzart nur, wenn dort explizit hinterlegt.
Namenlose felder (value) können ebenfalls mit einer Farbe versehen. Dafür kann als Bezeichner CONST_ und dann der Inhalt der value-Angabe verwenden werden.
Beispielsweise steht in value 01, dann wird CONST_01 als Bezeichner verwendet.

### Künstliche Intelligenz

MainframeMate nutzt KI-gestützte Funktionen, um die Benutzererfahrung zu verbessern. Diese Funktionen sind experimentell und können in den Einstellungen aktiviert werden. Sie bieten Unterstützung bei der Satzartenerstellung und der Datenvalidierung.
Das Modell dafür ist lokal und benötigt keine Internetverbindung. Es muss allerdings einmalig von extern bezogen werden, geeignet ist z.B. das Mistral-7B-Modell von Hugging Face. Bitte auf das Format achten, das Modell muss als grun-quantisiertes GGUF-Modell vorliegen.
Für reine CPU-Beschleunigung kann der interne Treiber, basierend auf llama.cpp, verwendet werden. Für GPU-Beschleunigung muss der Treiber entweder neu kompiliert werden oder es wird auf OLLAMA ausgewichen, das ebenfalls vollständig unterstützt wird und bei der Modellauswahl auch freier ist.

---

### Plugins

Um ein Plugin zu nutzen muss es unter "<userHome>.mainframemate/plugins" liegen (wo auch die Settings sind). In Gradle reicht es aus diesen Task zu starten:

```plugins > excelimport > Tasks > build > build```

Das JAR wird dann automatisch in das richtige Verzeichnis kopiert. Für andere Plugins verfährt man ähnlich.

---

## Für Entwickler

### Proxy-Konfiguration
#### Automatische Proxy-Konfiguration per WPAD/PAC-Datei

Wenn unter Windows ein Setupskript mit URL für das Netzwerk hinterlegt ist, muss das Projekt wie folgt über die PowerShell gebaut werden:

```
./gradlew assemble --init-script proxy-init.gradle
```

Dadurch werden die nötigen Dependencies in den Gradle-Cache geladen. Anschließen kann das Projekt auch einfach wie gewohnt in IntelliJ gestartet und dedebugged werden. Dazu einfach die Play-Taste neben der main anklicken. Bei Änderungen der Dependencies muss der o.g. Befehl im Terminal allerdings immer wieder erneut ausgeführt werden.

**Tipp:** Wer sich das wiederholte Ausführen im Terminal sparen möchte, kann die Datei `proxy-init.gradle` auch global unter `%USERPROFILE%\.gradle\init.gradle` ablegen. Damit wird die automatische Proxy-Konfiguration dauerhaft für alle Gradle-Projekte übernommen – unabhängig davon, wie sie gestartet werden. (Die Datei muss zwingend in init.gradle umbenannt werden, ansonsten funktioniert es nicht.)

#### Proxy-Konfiguration für GIT-Versionsverwaltung

Da GIT analog zu Gradle die Proxy Konfiguration aus Windows nicht automatisch übernimmt, muss einmalig für dem ersten Push folgendes Script ausgeführt werden:

```
.\configure-git-proxy.ps1
```

Hierbei wird ein minimalistischer JavaScript Parser verwendet, um an die notwendigen Informationen aus der PAC-Datei zu gelangen.

#### Proxy-Konfiguration für IDEA (funktioniert aber nicht für Gradle und GIT)

Im obigen Fall muss auch IDEA angepasst werden, damit die GIT-Versionsverwaltung wie gewohnt funktioniert. Das geht am einfachsten wie folgt:
Settings → Appearance & Behavior → System Settings → HTTP Proxy:

* steht auf Auto-detect proxy settings
* oder manuell mit den richtigen Daten (falls "auto" nicht ausreicht die Box für den Link anhaken)
  Die URL für die halbautomatische Einstellung bekommt man über ein Klick auf den blauen Link zu den Systemeinstellugen, die URL dort einfach kopieren.

### KI-Unterstützung
Der gebündelte KI-Treiber kann mittels CMAKE und gcc / g++ oder ggf. mittels des MSVC-Plugins von Clang / LLVM in VS Code kompiliert werden.
Der neuen Hashwert ermittelt sich dann am leichtesten mittels:
##### Windows (PowerShell)
```powershell
Get-FileHash .\driver.exe -Algorithm SHA256
```
#### Linux/macOS
```bash
sha256sum driver.exe
```

(Linux wird aktuell nicht unterstützt - wurde nicht getestet)

---

## Third-party components used in this project:

- llama.cpp (MIT License)
  https://github.com/ggerganov/llama.cpp
  See: /llama/LICENSE-LLAMACPP.txt