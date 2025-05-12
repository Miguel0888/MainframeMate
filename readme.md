# MainframeMate - Your assistant for configuring the Mainframe
..todo

MainframeMate/
├─ src/
│  └─ main/
│     ├─ java/
│     │  └─ de/beispiel/mainframemate/
│     │     ├─ Main.java               // Einstiegspunkt
│     │     ├─ ui/                     // Swing GUI-Klassen
│     │     ├─ ftp/                    // FTP-/SFTP-Client-Logik
│     │     ├─ config/                 // z. B. Properties-Loader
│     │     └─ service/                // spätere Job- oder Analyse-Logik
│     └─ resources/
│        └─ icon.png                   // Icon für Tray oder Fenster
├─ build.gradle


### Automatische Proxy-Konfiguration per WPAD/PAC-Datei
Wenn unter Windows ein Setupskript mit URL für das Netzwerk hinterlegt ist, muss das Projekt wie folgt über die PowerShell gebaut werden:

```
./gradlew assemble --init-script proxy-init.gradle
```

Dadurch werden die nötigen Dependencies in den Gradle-Cache geladen. Anschließen kann das Projekt auch einfach wie gewohnt in IntelliJ gestartet und dedebugged werden. Dazu einfach die Play-Taste neben der main anklicken. Bei Änderungen der Dependencies muss der o.g. Befehl im Terminal allerdings immer wieder erneut ausgeführt werden. 

**Tipp:** Wer sich das wiederholte Ausführen im Terminal sparen möchte, kann die Datei `proxy-init.gradle` auch global unter `%USERPROFILE%\.gradle\init.gradle` ablegen. Damit wird die automatische Proxy-Konfiguration dauerhaft für alle Gradle-Projekte übernommen – unabhängig davon, wie sie gestartet werden. (Die Datei muss zwingend in init.gradle umbenannt werden, ansonsten funktioniert es nicht.)

### Proxy-Konfiguration für GIT-Versionsverwaltung
Da GIT analog zu Gradle die Proxy Konfiguration aus Windows nicht automatisch übernimmt, muss einmalig für dem ersten Push folgendes Script ausgeführt werden:
```
.\configure-git-proxy.ps1
```
Hierbei wird ein minimalistischer JavaScript Parser verwendet, um an die notwendigen Informationen aus der PAC-Datei zu gelangen.

### Proxy-Konfiguration für IDEA (funktioniert aber nicht für Gradle und GIT)
Im obigen Fall muss auch IDEA angepasst werden, damit die GIT-Versionsverwaltung wie gewohnt funktioniert. Das geht am einfachsten wie folgt:
Settings → Appearance & Behavior → System Settings → HTTP Proxy:
- steht auf Auto-detect proxy settings
- oder manuell mit den richtigen Daten (falls "auto" nicht ausreicht die Box für den Link anhaken)
Die URL für die halbautomatische Einstellung bekommt man über ein Klick auf den blauen Link zu den Systemeinstellugen, die URL dort einfach kopieren.

