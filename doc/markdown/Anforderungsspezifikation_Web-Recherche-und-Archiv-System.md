# Anforderungsspezifikation: Web-Recherche & Archiv-System

**Projekt:** MainframeMate  
**Datum:** 2026-02-24  
**Version:** 1.0  

---

## 1. Übersicht

### 1.1 Zielbild

MainframeMate wird um ein **automatisiertes Web-Recherche-System** erweitert. Ein KI-gesteuerter „Recherche-Agent" kann selbständig Webseiten durchsuchen, von Link zu Link navigieren, deren Inhalte archivieren und indexieren. Das System besteht aus fünf zusammenwirkenden Komponenten:

1. **Recherche-Modus** – Neuer `ChatMode.RECHERCHE` im Chat-Dropdown
2. **Archiv-System** – Integrierte Dokumentendatenbank als neuer ConnectionTab
3. **Web-Cache** – Verwaltung gesammelter URLs mit Status-Tracking im IndexingControlPanel
4. **Snapshot-Pipeline** – Automatische Archivierung bei jedem Seitenbesuch
5. **Robustheit** – Firefox-Cleanup, Retry-Mechanismus, Crawl-Limits

### 1.2 Zusammenfassung

```
User tippt: "Recherchiere alle Wirtschaftsnachrichten auf de.yahoo.com"
  ↓
Recherche-Agent navigiert zu de.yahoo.com
  ↓
Extrahiert Links → prüft Domain-Filter → fügt URLs zum Web-Cache hinzu
  ↓
Besucht jede unbesuchte URL → Snapshot via Tika → Archiv-Ablage → Lucene-Index
  ↓
Agent antwortet mit Zusammenfassung aller gefundenen Artikel
```

---

## 2. Architektur-Entscheidungen

### 2.1 Neuer ChatMode: RECHERCHE

| Aspekt | Entscheidung |
|--------|-------------|
| Basis | Funktioniert wie `AGENT` (iterativer Tool-Call-Loop) |
| System-Prompt | Fokussiert auf systematisches Crawling + Archivierung |
| Allowed Tools | READ + WRITE + alle Browser-Tools + Web-Cache-Tools |
| Besonderheit | Automatische Archivierung bei Seitenbesuch (Hook) |

### 2.2 Archiv als 4. Backend-Typ

- `VirtualBackendType` wird um `ARCHIVE` erweitert
- Neuer `ArchiveConnectionTab` analog zu `LocalConnectionTabImpl`
- Nutzt `SplitPreviewTab` für Vorschau (bereits vorhanden, kein neuer Code)

### 2.3 Persistenz-Technologie

| Komponente | Technologie | Begründung |
|-----------|-------------|------------|
| Archiv-Metadaten | **H2 Embedded Database** | SQL-basiert, bewährt für Desktop-Apps, ACID-konform |
| HTML-Snapshots | **Dateisystem** (`~/.mainframemate/archive/snapshots/`) | Große Binärdaten, einfacher Zugriff |
| Volltextindex | **Lucene** (bestehend) | Wiederverwendung von `LuceneLexicalIndex` |
| URL-Status-Tracking | **H2** (gleiche DB) | Schnelle Statusabfragen |

### 2.4 Wiederverwendung bestehender Komponenten

| Bestehend | Wird genutzt für |
|----------|------------------|
| `SplitPreviewTab` | Vorschau im Archiv-Tab und im WebCache-Dialog |
| `BrowserToolAdapter` + alle Browse*Tools | Navigation im Recherche-Modus |
| `RagContentProcessor` | Tika-Extraktion → Chunking → Indexierung |
| `IndexingService` / `IndexingPipeline` | Automatische Indexierung archivierter Inhalte |
| `ExtractTextFromDocumentUseCase` | Text-Extraktion für Snapshots |
| `WebSearchBrowserManager` | Firefox-Session-Management |

---

## 3. Datenmodell

### 3.1 ArchiveEntry (Neu)

```java
package de.bund.zrb.archive.model;

public class ArchiveEntry {
    private String entryId;          // UUID
    private String url;              // Quell-URL (leer für manuell importierte Dateien)
    private String title;            // Seitentitel oder Dateiname
    private String contentText;      // Extrahierter Klartext (via Tika)
    private String mimeType;         // z.B. "text/html", "application/pdf"
    private String snapshotPath;     // Relativer Pfad zur HTML/Datei im Snapshot-Verzeichnis
    private long contentLength;      // Textlänge in Zeichen
    private long fileSizeBytes;      // Dateigröße des Snapshots
    private Instant crawlTimestamp;  // Zeitpunkt des Crawls/Imports
    private Instant lastIndexed;     // Zeitpunkt der letzten Indexierung
    private ArchiveEntryStatus status; // PENDING, CRAWLED, INDEXED, FAILED
    private String sourceId;         // Referenz auf IndexSource (optional)
    private String errorMessage;     // Fehlermeldung bei FAILED
    private Map<String, String> metadata; // Beliebige Metadaten (Author, Keywords, etc.)
}
```

### 3.2 ArchiveEntryStatus (Neu)

```java
public enum ArchiveEntryStatus {
    PENDING,   // URL bekannt, aber noch nicht besucht
    CRAWLED,   // Seite besucht, Snapshot vorhanden, noch nicht indexiert
    INDEXED,   // Vollständig verarbeitet (Snapshot + Lucene + optional Embedding)
    FAILED     // Fehler beim Crawl oder bei der Verarbeitung
}
```

### 3.3 Erweiterung: VirtualBackendType

```java
public enum VirtualBackendType {
    LOCAL,
    FTP,
    NDV,
    ARCHIVE  // NEU
}
```

### 3.4 Erweiterung: IndexSource

Bestehende `IndexSource` wird um Web-spezifische Felder erweitert:

```java
// Neue Felder in IndexSource:
private List<String> domainIncludePatterns = new ArrayList<>();  // z.B. "*.yahoo.com", "de.reuters.com/*"
private List<String> domainExcludePatterns = new ArrayList<>();  // z.B. "*.ad.yahoo.com", "*/login/*"
private int maxCrawlDepth = 3;           // Maximale Link-Tiefe
private int maxUrlsPerSession = 100;     // Max URLs pro Recherche-Session
private boolean respectRobotsTxt = true; // Robots.txt beachten
private String topicFilter = "";         // Themen-Filter (für themenbasierte Recherche)
```

### 3.5 WebCacheEntry (Arbeitstabelle für den Recherche-Agent)

```java
package de.bund.zrb.archive.model;

public class WebCacheEntry {
    private String url;                    // Normalisierte URL
    private String sourceId;               // Zugehörige IndexSource
    private ArchiveEntryStatus status;     // PENDING, CRAWLED, INDEXED, FAILED
    private int depth;                     // Link-Tiefe (0 = Start-URL)
    private String parentUrl;              // Von welcher Seite entdeckt
    private Instant discoveredAt;          // Wann die URL entdeckt wurde
    private String archiveEntryId;         // Referenz zum ArchiveEntry (nach Crawl)
}
```

---

## 4. UI-Spezifikation

### 4.1 ChatMode.RECHERCHE

**Position:** Im Mode-Dropdown der ChatSession, nach AGENT:

```
[Ask ▼]  →  Ask | Edit | Plan | Agent | Recherche
```

**System-Prompt:**

```
Du bist ein Web-Recherche-Agent. Deine Aufgabe ist es, systematisch Webseiten zu einem 
Thema oder auf einer Domain zu durchsuchen und alle relevanten Inhalte zu archivieren.

REGELN:
1. Navigiere zur Start-URL und lies den Seiteninhalt mit web_read_page.
2. Extrahiere alle relevanten Links aus dem Seitentext.
3. Prüfe mit web_cache_status, welche URLs bereits archiviert sind.
4. Besuche nur URLs, die noch PENDING sind (Status != INDEXED).
5. Für jede besuchte Seite: Lies den Inhalt, er wird automatisch archiviert.
6. Beachte die Domain-Filter: Besuche nur URLs, die zum konfigurierten Muster passen.
7. Arbeite dich systematisch durch alle Seiten, bis keine unbesuchten URLs mehr übrig sind 
   oder das Limit erreicht ist.
8. Am Ende: Fasse zusammen, welche Seiten du gefunden und archiviert hast.
9. Pro Antwort genau EINEN Tool-Call als reines JSON-Objekt.
10. Antworte auf Deutsch.
```

**Tooltip:** `"Durchsucht Webseiten systematisch und archiviert Inhalte für die spätere Suche."`

### 4.2 IndexingControlPanel – Web-Cache-Erweiterung

**Neue Spalte in der Source-Tabelle:**

| Name | Typ | Aktiv | Zeitplan | Letzter Lauf | Status | **Cache** |
|------|-----|-------|----------|-------------|--------|-----------|
| Yahoo News | WEB | ✓ | Manuell | 24.02.2026 | ✅ | **[🔗 42]** |

- Die Spalte "Cache" zeigt die Anzahl der URLs im Web-Cache
- Der Button `[🔗 42]` öffnet den **WebCacheDialog**

**Erweiterung des Detail-Panels** (für SourceType.WEB):
- Neues Sub-Panel "Domain-Filter" mit:
  - Include-Patterns (Textarea, eine Pattern pro Zeile)
  - Exclude-Patterns (Textarea)
  - Max Crawl-Tiefe (Spinner)
  - Max URLs pro Session (Spinner)
  - Checkbox "robots.txt beachten"
  - Textfeld "Themen-Filter" (optional)

### 4.3 WebCacheDialog (Neu)

**Layout:** Modaler Dialog, 900×600px, JSplitPane horizontal

```
┌─────────────────────────────────────────────────────────────────┐
│  Web-Cache: Yahoo News                                    [X]  │
├──────────────────────────────┬──────────────────────────────────┤
│  🔍 [Suchfeld____________]  │                                  │
│  ┌─────────────────────────┐│  ┌────────────────────────────┐  │
│  │ Status │ URL            ││  │                            │  │
│  ├────────┼────────────────┤│  │   SplitPreviewTab          │  │
│  │ ✅     │ de.yahoo.com/  ││  │   (Content-Vorschau)       │  │
│  │ ✅     │ de.yahoo.com/n.││  │                            │  │
│  │ ⏳     │ de.yahoo.com/p.││  │                            │  │
│  │ ❌     │ de.yahoo.com/x.││  │                            │  │
│  │ ⏳     │ de.yahoo.com/f.││  │                            │  │
│  └─────────────────────────┘│  │                            │  │
│                              │  └────────────────────────────┘  │
│  [➕ URL hinzufügen] [🗑️]   │                                  │
│  [▶ Alle crawlen] [⏹ Stop]  │                                  │
├──────────────────────────────┴──────────────────────────────────┤
│  42 URLs │ 35 indexiert │ 5 ausstehend │ 2 fehlgeschlagen      │
└─────────────────────────────────────────────────────────────────┘
```

**Verhalten:**
- Klick auf eine URL → Preview rechts laden (via `SplitPreviewTab`)
- Status-Icons: ⏳ PENDING, ✅ INDEXED, 🔄 CRAWLED, ❌ FAILED
- "Alle crawlen" startet die `WebSnapshotPipeline` für alle PENDING-URLs
- Kontextmenü: "Im Browser öffnen", "Aus Cache entfernen", "Erneut crawlen"

### 4.4 ArchiveConnectionTab (Neu)

**Layout:** Analog zu `LocalConnectionTabImpl`

```
┌─────────────────────────────────────────────────────────────────┐
│ [⏴] [⏵] [🔄] [Pfad/Suche________________________] [Öffnen]    │
├─────────────────────────────────────────────────────────────────┤
│  📁 de.yahoo.com/                                              │
│  📁 de.reuters.com/                                            │
│  📄 MeinDokument.pdf                                           │
│  📄 Notizen.md                                                 │
│  📁 Thema: Wirtschaft/                                         │
├─────────────────────────────────────────────────────────────────┤
│  📊 Indexierungs-Sidebar (optional, wie bei LocalConnectionTab)│
└─────────────────────────────────────────────────────────────────┘
```

**Funktionalität:**
- Navigation durch Archiv-Einträge, gruppiert nach Domain bzw. Thema
- Doppelklick → Öffnet Inhalt im `SplitPreviewTab`
- Drag & Drop von Dateien → Import ins Archiv
- Kontextmenü: "Löschen", "Erneut indexieren", "Im Browser öffnen" (nur für Web-Snapshots)
- Suchfeld: Durchsucht Archiv-Metadaten (Titel, URL) + Lucene-Volltext

**Registrierung:**
- Neuer Menüpunkt oder Button in der Toolbar zum Öffnen eines Archiv-Tabs
- `TabbedPaneManager` erkennt `VirtualBackendType.ARCHIVE`

---

## 5. Agent/Tool-Spezifikation

### 5.1 web_cache_status (Neues Tool)

```json
{
  "name": "web_cache_status",
  "description": "Prüft den Archivierungsstatus einer oder mehrerer URLs im Web-Cache. Gibt an, ob eine URL bereits besucht, archiviert und indexiert wurde oder noch aussteht.",
  "input_schema": {
    "type": "object",
    "properties": {
      "urls": {
        "type": "array",
        "items": { "type": "string" },
        "description": "Liste der zu prüfenden URLs"
      },
      "sourceId": {
        "type": "string",
        "description": "Optional: ID der IndexSource, um nur deren Cache zu prüfen"
      }
    },
    "required": ["urls"]
  }
}
```

**Response-Format:**

```json
{
  "results": [
    {
      "url": "https://de.yahoo.com/nachrichten/...",
      "status": "INDEXED",
      "contentLength": 4523,
      "crawlTimestamp": "2026-02-24T10:30:00Z",
      "archiveEntryId": "abc-123"
    },
    {
      "url": "https://de.yahoo.com/politik/...",
      "status": "PENDING",
      "contentLength": 0,
      "crawlTimestamp": null,
      "archiveEntryId": null
    }
  ],
  "summary": "2 URLs geprüft: 1 indexiert, 1 ausstehend"
}
```

### 5.2 web_cache_add_urls (Neues Tool)

```json
{
  "name": "web_cache_add_urls",
  "description": "Fügt neue URLs zum Web-Cache hinzu, damit sie beim nächsten Crawl-Durchlauf besucht werden. URLs, die nicht zum Domain-Filter passen, werden abgelehnt.",
  "input_schema": {
    "type": "object",
    "properties": {
      "urls": {
        "type": "array",
        "items": { "type": "string" },
        "description": "Liste der hinzuzufügenden URLs"
      },
      "sourceId": {
        "type": "string",
        "description": "ID der IndexSource, zu der die URLs gehören"
      },
      "parentUrl": {
        "type": "string",
        "description": "URL der Seite, von der die Links stammen (für Tiefenberechnung)"
      }
    },
    "required": ["urls", "sourceId"]
  }
}
```

**Response-Format:**

```json
{
  "added": 5,
  "rejected": 2,
  "rejectedUrls": [
    { "url": "https://ads.yahoo.com/...", "reason": "Domain-Exclude-Pattern matched" },
    { "url": "https://login.yahoo.com/...", "reason": "Max Crawl-Tiefe überschritten" }
  ],
  "totalPending": 12
}
```

### 5.3 web_archive_snapshot (Neues Tool, optional)

```json
{
  "name": "web_archive_snapshot",
  "description": "Erstellt einen Snapshot der aktuell im Browser geladenen Seite und speichert ihn im Archiv. Wird automatisch nach web_read_page aufgerufen, kann aber auch manuell getriggert werden.",
  "input_schema": {
    "type": "object",
    "properties": {
      "url": {
        "type": "string",
        "description": "URL der zu archivierenden Seite"
      },
      "content": {
        "type": "string",
        "description": "Optional: Bereits extrahierter Text (spart erneutes Laden)"
      }
    },
    "required": ["url"]
  }
}
```

### 5.4 Tool-Registrierung

Alle neuen Tools werden als reguläre `McpTool`-Implementierungen registriert:

```java
// In ToolRegistryImpl oder als Plugin
registry.register(new WebCacheStatusTool(archiveRepository));
registry.register(new WebCacheAddUrlsTool(archiveRepository, indexSourceRepository));
registry.register(new WebArchiveSnapshotTool(archiveRepository, snapshotPipeline));
```

**ToolAccessType:**
- `web_cache_status` → `READ`
- `web_cache_add_urls` → `WRITE`
- `web_archive_snapshot` → `WRITE`

---

## 6. Pipeline / Workflow

### 6.1 Recherche-Agent-Workflow

```
Start: User gibt Thema/Domain ein
  │
  ▼
Agent: web_navigate(startUrl)
  │
  ▼
Agent: web_read_page() → Seitentext
  │
  ├──► [Automatisch] WebSnapshotPipeline:
  │     1. HTML von BrowserSession holen
  │     2. Tika-Extraktion → Klartext
  │     3. ArchiveEntry erstellen (Status: CRAWLED)
  │     4. Snapshot auf Dateisystem speichern
  │     5. RagContentProcessor → Lucene-Index
  │     6. Status → INDEXED
  │
  ▼
Agent: Links aus Seitentext extrahieren
  │
  ▼
Agent: web_cache_add_urls(links, sourceId, parentUrl)
  │    → Domain-Filter anwenden
  │    → Duplikate filtern
  │    → Tiefe berechnen
  │
  ▼
Agent: web_cache_status(pendingUrls)
  │    → Nächste unbesuchte URL wählen
  │
  ▼
Agent: web_navigate(nächsteUrl)
  │
  ▼
[... Schleife bis keine PENDING-URLs mehr oder Limit erreicht ...]
  │
  ▼
Agent: Zusammenfassung an User
```

### 6.2 WebSnapshotPipeline (Klasse)

```java
package de.bund.zrb.archive.service;

/**
 * Automatische Archivierungs-Pipeline.
 * Wird getriggert bei jedem web_read_page-Aufruf im RECHERCHE-Modus.
 *
 * Schritte:
 * 1. URL + Content entgegennehmen
 * 2. ArchiveEntry in H2 erstellen (Status: CRAWLED)
 * 3. HTML-Snapshot im Dateisystem ablegen
 * 4. RagContentProcessor aufrufen (Chunking + Lucene)
 * 5. Status auf INDEXED setzen
 */
public class WebSnapshotPipeline {
    
    private final ArchiveRepository archiveRepo;
    private final RagContentProcessor contentProcessor;
    private final Path snapshotBaseDir;
    
    public ArchiveEntry processSnapshot(String url, String textContent, 
                                         String htmlContent, String title) {
        // ...
    }
}
```

### 6.3 Automatischer Hook

Im `RECHERCHE`-Modus wird nach jedem erfolgreichen `web_read_page`-Aufruf automatisch die `WebSnapshotPipeline` getriggert. Dies geschieht im `ChatSession.executeToolCallsSequentially()`:

```java
// Nach Tool-Ausführung prüfen:
if (currentMode == ChatMode.RECHERCHE && "web_read_page".equals(toolName)) {
    // Automatisch archivieren
    String currentUrl = browserManager.getCurrentUrl();
    String content = toolResult;
    webSnapshotPipeline.processSnapshot(currentUrl, content, htmlContent, title);
}
```

---

## 7. Persistenz

### 7.1 Verzeichnisstruktur

```
~/.mainframemate/
  ├── db/
  │   └── archive.mv.db          ← H2 Database (Metadaten + WebCache)
  ├── archive/
  │   └── snapshots/
  │       ├── de.yahoo.com/
  │       │   ├── abc123.html     ← HTML-Snapshots
  │       │   └── abc123.txt     ← Extrahierter Text
  │       └── manual/
  │           ├── dokument.pdf    ← Manuell importierte Dateien
  │           └── notizen.md
  ├── lucene/                     ← Bestehender Lucene-Index
  └── config/
      └── sources.json            ← Bestehende IndexSource-Persistenz
```

### 7.2 H2-Schema

```sql
CREATE TABLE archive_entries (
    entry_id VARCHAR(36) PRIMARY KEY,
    url VARCHAR(2048),
    title VARCHAR(512),
    mime_type VARCHAR(128),
    snapshot_path VARCHAR(1024),
    content_length BIGINT,
    file_size_bytes BIGINT,
    crawl_timestamp TIMESTAMP,
    last_indexed TIMESTAMP,
    status VARCHAR(20),
    source_id VARCHAR(36),
    error_message VARCHAR(2048),
    UNIQUE(url)  -- Verhindert Duplikate
);

CREATE TABLE archive_metadata (
    entry_id VARCHAR(36),
    meta_key VARCHAR(256),
    meta_value TEXT,
    PRIMARY KEY (entry_id, meta_key),
    FOREIGN KEY (entry_id) REFERENCES archive_entries(entry_id) ON DELETE CASCADE
);

CREATE TABLE web_cache (
    url VARCHAR(2048) PRIMARY KEY,
    source_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    depth INT DEFAULT 0,
    parent_url VARCHAR(2048),
    discovered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    archive_entry_id VARCHAR(36),
    FOREIGN KEY (archive_entry_id) REFERENCES archive_entries(entry_id)
);

CREATE INDEX idx_cache_source ON web_cache(source_id);
CREATE INDEX idx_cache_status ON web_cache(status);
CREATE INDEX idx_entries_status ON archive_entries(status);
CREATE INDEX idx_entries_url ON archive_entries(url);
```

### 7.3 ArchiveRepository

```java
package de.bund.zrb.archive.store;

public class ArchiveRepository {
    // CRUD für ArchiveEntry
    ArchiveEntry save(ArchiveEntry entry);
    ArchiveEntry findById(String entryId);
    ArchiveEntry findByUrl(String url);
    List<ArchiveEntry> findBySourceId(String sourceId);
    List<ArchiveEntry> findByStatus(ArchiveEntryStatus status);
    List<ArchiveEntry> findAll();
    void delete(String entryId);
    void updateStatus(String entryId, ArchiveEntryStatus status, String errorMessage);
    
    // Web-Cache-spezifisch
    void addWebCacheEntry(WebCacheEntry entry);
    List<WebCacheEntry> getPendingUrls(String sourceId, int limit);
    WebCacheEntry getWebCacheEntry(String url);
    void updateWebCacheStatus(String url, ArchiveEntryStatus status, String archiveEntryId);
    int countByStatus(String sourceId, ArchiveEntryStatus status);
    boolean urlExists(String url);
}
```

---

## 8. Robustheit / Fehlerbehandlung

### 8.1 Firefox-Prozess-Cleanup

**Problem:** Firefox-Prozesse bleiben nach App-Beendigung manchmal offen.

**Lösung – Dreistufig:**

1. **Normales Shutdown** (bestehend): `PluginManager.shutdownAll()` → `WebSearchPlugin.shutdown()` → `WebSearchBrowserManager.closeSession()` → `BrowserSession.close()`

2. **Process-Kill als Fallback** (Neu): In `WebSearchBrowserManager.closeSession()`:
   ```java
   public synchronized void closeSession() {
       if (session != null) {
           try {
               session.close();
           } catch (Exception e) {
               LOG.warning("Error closing browser session: " + e.getMessage());
           }
           // Fallback: Kill the Firefox process if still running
           killFirefoxProcess();
           session = null;
       }
   }
   
   private void killFirefoxProcess() {
       if (firefoxProcessHandle != null && firefoxProcessHandle.isAlive()) {
           firefoxProcessHandle.destroyForcibly();
       }
   }
   ```

3. **Shutdown-Hook-Erweiterung** in `Main.java` (bereits vorhanden, aber validieren):
   ```java
   Runtime.getRuntime().addShutdownHook(new Thread(() -> {
       PluginManager.shutdownAll(); // Schließt auch Firefox
   }, "plugin-shutdown-hook"));
   ```

### 8.2 Leere Modellantwort – Robusterer Retry

**Problem:** Das Modell liefert manchmal leere Antworten (weder Text noch Tool-Call), besonders bei Ollama-Modellen.

**Aktuelle Lösung:** `MAX_EMPTY_RESPONSE_RETRIES = 3` mit statischem Retry-Prompt.

**Verbesserung:**

```java
// In ChatSession:

private static final int MAX_EMPTY_RESPONSE_RETRIES = 5; // Erhöht von 3

// Adaptiver Retry-Prompt basierend auf Retry-Nummer
private String buildRetryPrompt(int retryCount) {
    if (retryCount <= 2) {
        return "Du hast eine leere Antwort geliefert. " +
               "Bitte beantworte die Nutzeranfrage: \"" + lastUserRequestText + "\"";
    } else if (retryCount <= 4) {
        return "WICHTIG: Deine letzte Antwort war leer. " +
               "Du MUSST jetzt entweder einen Tool-Call machen ODER eine Textantwort geben. " +
               "Aufgabe: \"" + lastUserRequestText + "\"\n" +
               "Wenn du nicht weiter weißt, sage dem Nutzer was du bisher herausgefunden hast.";
    } else {
        return "Letzte Chance: Antworte mit Text oder gib auf. Aufgabe: \"" + lastUserRequestText + "\"";
    }
}
```

**Zusätzlich im RECHERCHE-Modus:**
- Bei leerer Antwort nach Tool-Result: Nächste PENDING-URL aus dem Web-Cache nehmen und als Kontext mitgeben
- Exponentielles Backoff zwischen Retries (500ms, 1s, 2s, 4s)

### 8.3 Crawl-Limits und Sicherheit

| Parameter | Default | Konfigurierbar |
|-----------|---------|----------------|
| Max URLs pro Session | 100 | Ja (in IndexSource) |
| Max Crawl-Tiefe | 3 | Ja |
| Timeout pro Seite | 30s | Ja (in WebSearch-Settings) |
| robots.txt beachten | Ja | Ja (in IndexSource) |
| Max Snapshot-Größe | 5 MB | Ja |
| Retry bei FAILED-URLs | 0 (kein Retry) | Ja |
| Pause zwischen Requests | 1s | Ja (Rate-Limiting) |

### 8.4 Fehlerhafte URLs

```java
// In WebSnapshotPipeline:
try {
    processSnapshot(url, content, html, title);
} catch (Exception e) {
    archiveRepo.updateWebCacheStatus(url, ArchiveEntryStatus.FAILED, e.getMessage());
    LOG.warning("Failed to archive " + url + ": " + e.getMessage());
    // Nicht abbrechen – weiter mit nächster URL
}
```

### 8.5 Domain-Filter-Validierung

```java
public class DomainFilter {
    private final List<Pattern> includePatterns;
    private final List<Pattern> excludePatterns;
    
    public boolean accepts(String url) {
        // 1. Wenn Include-Patterns definiert: URL muss mindestens ein Pattern matchen
        // 2. Wenn Exclude-Patterns definiert: URL darf kein Pattern matchen
        // 3. Leere Pattern-Listen = alles erlaubt
    }
}
```

---

## 9. Implementierungsreihenfolge (Phasen)

### Phase 1: Archiv-Grundlagen ⏱️ ~3-4 Tage

**Ziel:** Funktionierende Archiv-Datenbank mit ConnectionTab

1. H2-Dependency in `build.gradle` hinzufügen
2. `ArchiveEntry`, `ArchiveEntryStatus`, `WebCacheEntry` – Datenmodell
3. `ArchiveRepository` – H2-basierter CRUD-Layer
4. `VirtualBackendType.ARCHIVE` – Enum erweitern
5. `ArchiveConnectionTab` – Einfache Liste + SplitPreviewTab-Vorschau
6. Menüeintrag/Button zum Öffnen des Archiv-Tabs
7. Manueller Import: Dateien per Drag & Drop ins Archiv
8. Automatische Indexierung (Lucene) bei Import

**Dateien:**
- `archive/model/ArchiveEntry.java` (neu)
- `archive/model/ArchiveEntryStatus.java` (neu)
- `archive/model/WebCacheEntry.java` (neu)
- `archive/store/ArchiveRepository.java` (neu)
- `archive/service/ArchiveService.java` (neu)
- `ui/ArchiveConnectionTab.java` (neu)
- `ui/VirtualBackendType.java` (ändern)

### Phase 2: Web-Cache & Tools ⏱️ ~2-3 Tage

**Ziel:** Web-Cache-Verwaltung mit UI und Tools für den Agent

1. `WebCacheStatusTool` implementieren
2. `WebCacheAddUrlsTool` implementieren
3. `DomainFilter` – URL-Pattern-Matching
4. `IndexSource` um Web-Felder erweitern
5. `IndexingControlPanel` – Cache-Spalte + Button
6. `WebCacheDialog` – Split-View mit URL-Liste + Preview
7. Tool-Registrierung in `ToolRegistryImpl`

**Dateien:**
- `archive/tools/WebCacheStatusTool.java` (neu)
- `archive/tools/WebCacheAddUrlsTool.java` (neu)
- `archive/service/DomainFilter.java` (neu)
- `indexing/model/IndexSource.java` (ändern)
- `indexing/ui/IndexingControlPanel.java` (ändern)
- `archive/ui/WebCacheDialog.java` (neu)

### Phase 3: Recherche-Modus ⏱️ ~2-3 Tage

**Ziel:** Funktionierender Recherche-ChatMode mit automatischer Archivierung

1. `ChatMode.RECHERCHE` – Enum-Eintrag mit System-Prompt
2. `WebSnapshotPipeline` – Automatische Archivierung
3. Hook in `ChatSession.executeToolCallsSequentially()` für Auto-Archivierung
4. `WebArchiveSnapshotTool` (optionales manuelles Tool)
5. Integration: Nach `web_read_page` im RECHERCHE-Modus → Pipeline triggern
6. Recherche-Agent-Ablauf testen (End-to-End)

**Dateien:**
- `ui/components/ChatMode.java` (ändern)
- `archive/service/WebSnapshotPipeline.java` (neu)
- `archive/tools/WebArchiveSnapshotTool.java` (neu)
- `ui/components/ChatSession.java` (ändern)

### Phase 4: Robustheit ⏱️ ~1-2 Tage

**Ziel:** Stabile Ausführung, sauberes Cleanup

1. Firefox-Process-Kill in `WebSearchBrowserManager.closeSession()`
2. Adaptiver Retry-Prompt in `ChatSession`
3. Rate-Limiting zwischen Requests
4. Domain-Filter-Validierung
5. Crawl-Limits (Max-URLs, Max-Tiefe, Timeout)
6. Fehlerhafte URLs → FAILED-Status ohne Abbruch

**Dateien:**
- `websearch/plugin/WebSearchBrowserManager.java` (ändern)
- `ui/components/ChatSession.java` (ändern)
- `archive/service/WebSnapshotPipeline.java` (ändern)

### Phase 5: Polish & Integration ⏱️ ~1-2 Tage

**Ziel:** Vollständige Integration in das bestehende System

1. `SearchIndexTool` – Archiv-Einträge in Suchergebnisse einbeziehen
2. Archiv-Statistik im ArchiveConnectionTab (Anzahl Dokumente, Gesamtgröße)
3. Re-Crawl-Logik (periodisches Update bereits archivierter Seiten)
4. Archiv-Export (Backup als ZIP)
5. UI-Feinschliff: Icons, Tooltips, Tastaturkürzel

**Dateien:**
- `mcp/SearchIndexTool.java` (ändern)
- Diverse UI-Dateien (kleinere Anpassungen)

---

## Anhang: Abhängigkeiten

### Neue Gradle-Dependencies

```groovy
// H2 Embedded Database
implementation 'com.h2database:h2:2.2.224'
```

### Bestehende Dependencies (bereits vorhanden, werden genutzt)
- Apache Tika (Textextraktion)
- Apache Lucene (Volltext-Index)
- Gson (JSON)
- RSyntaxTextArea (Code-Highlighting)
- OkHttp (HTTP-Client)
- wd4j (Firefox-Steuerung)

