# MCP Research Tool Suite – Architektur und Nutzung

## Übersicht

Die Research-Tool-Suite bietet einem Bot eine **menübasierte Navigation** durch Webseiten.
Statt DOM-Details (CSS-Selektoren, XPath) arbeitet der Bot mit **Action-Tokens** (menuItemIds)
und **viewTokens** für Race-Condition-freie Interaktion.

**Kernprinzipien:**
- **Navigation zuerst**: Jeder Tool-Call liefert (a) kurzen Textausschnitt und (b) klickbares Menü.
- **Indexierung im Hintergrund**: Network-Plane sammelt Inhalte automatisch → H2 + Lucene.
- **Aktionen außen, Auswertung innen**: Klicks per WebDriver BiDi `input.performActions`, Datenerhebung per JS im Browser.
- **Event-getriebenes Timing**: Settle-Policies (NAVIGATION / DOM_QUIET / NETWORK_QUIET).
- **Robuste Tagging-Bridge**: JS taggt Elemente → CSS locateNodes → WebDriver Actions.

## Architektur: 3-Plane-System

```
┌─────────────────────────────────────────────────────────────┐
│  Bot (LLM)                                                  │
│  ↕ MCP Tools (research_session_start, research_open, ...)   │
├─────────────────────────────────────────────────────────────┤
│  Research Layer (wd4j-mcp-server/research/)                 │
│  ├── ResearchSession      → sessionId, userContextId,       │
│  │                          viewToken, menuItem→SharedRef,  │
│  │                          domainPolicy, limits, privacy   │
│  ├── MenuViewBuilder      → Tagging-Bridge (JS→CSS locate)  │
│  ├── MenuView / MenuItem  → Datenmodell                     │
│  └── SettlePolicy         → Wait-Strategie                  │
├─────────────────────────────────────────────────────────────┤
│  Action Plane              Network Plane      DOM Plane     │
│  BrowserSession            H2 Archiv          JS Scripts    │
│  navigate/click/type       Lucene Index       Tagging       │
│  input.performActions      addDataCollector   MutationObs   │
│  browsingContext.navigate  getData/disownData DOMParser     │
│  UserContext-Isolation     ResponseCompleted               │
├─────────────────────────────────────────────────────────────┤
│  Persistenz: H2 (ArchiveRepository) + Lucene (SearchService)│
└─────────────────────────────────────────────────────────────┘
```

## Vollständige Tool-Suite

### Session-Management

| Tool | Beschreibung | Eingabe | Ausgabe |
|------|-------------|---------|---------|
| `research_session_start` | Session erzeugen mit UserContext-Isolation, Policies, Limits | `mode`, `domainPolicy`, `limits`, `seedUrls` | `sessionId`, `userContextId`, `contexts[]`, `status` |
| `research_config_update` | Session-Config live ändern | `domainPolicy`, `limits`, `defaultSettlePolicy`, `maxMenuItems`, `excerptMaxLength` | Bestätigung |

### Navigation & Interaktion (Kern)

| Tool | Beschreibung | Eingabe | Ausgabe |
|------|-------------|---------|---------|
| `research_open` | URL navigieren + Menüansicht | `url` (req.), `wait` (none/interactive/complete), `settlePolicy`, `sessionId`, `contextId` | viewToken, excerpt, menuItems[], newArchivedDocs[] |
| `research_menu` | Aktuelle Menüansicht | `selector`, `sessionId`, `contextId` | viewToken, excerpt, menuItems[], newArchivedDocs[] |
| `research_choose` | Menüeintrag klicken | `menuItemId` (req.), `viewToken` (req.), `settlePolicy`, `wait`, `sessionId`, `contextId` | viewToken, excerpt, menuItems[], newArchivedDocs[] |
| `research_navigate` | Back/Forward/Reload | `action` (back/forward/reload), `settlePolicy` | viewToken, excerpt, menuItems[] |

### Archiv & Suche

| Tool | Beschreibung | Eingabe | Ausgabe |
|------|-------------|---------|---------|
| `research_doc_get` | Archiviertes Dokument abrufen | `entryId` oder `url`, `maxTextLength` | Metadaten + extractedText |
| `research_search` | Lucene-Volltextsuche | `query` (req.), `maxResults` | Trefferliste mit docId, url, snippet, score |

### Crawl-Queue

| Tool | Beschreibung | Eingabe | Ausgabe |
|------|-------------|---------|---------|
| `research_queue_add` | URLs zur Crawl-Queue hinzufügen | `urls` (req.), `sourceId`, `depth` | added, skipped |
| `research_queue_status` | Queue-Status abfragen | `sourceId` | pending, crawled, indexed, failed, nextPending[] |

## viewToken-Vertrag

- Jede MenuView hat einen `viewToken` (z.B. `v1`, `v2`, ...)
- `menuItemId`s (z.B. `m0`, `m3`) sind **nur innerhalb** desselben viewTokens gültig
- Bei Navigation/Reload/Choose wird ein neuer viewToken erzeugt
- `research_choose` mit stale viewToken → **definierter Fehler** → Bot muss `research_menu` aufrufen

## Settle-Policies

| Policy | Wann verwenden | Implementierung |
|--------|---------------|-----------------|
| `NAVIGATION` | Standard-Links (full page load) | 1s Delay nach navigate() |
| `DOM_QUIET` | SPA-Clicks ohne Navigation | MutationObserver wartet auf 500ms Ruhe (max 5s) |
| `NETWORK_QUIET` | AJAX-heavy Seiten | PerformanceObserver wartet auf 500ms Ruhe (max 8s) |

## Network Ingestion Pipeline

Die Network Plane sammelt HTTP-Responses automatisch im Hintergrund:

```
responseCompleted Event
        │
        ▼
┌─ Filter Chain ──────────────────────┐
│ Status 2xx?                         │
│ MIME in allowlist? (text/html, etc.) │
│ URL not excluded? (no /login etc.)  │
│ Domain policy allows?               │
│ Body size ≤ maxBytesPerDoc?         │
└─────────────┬───────────────────────┘
              ▼
    ingestionExecutor (async)
              │
        getData() ←── Retry (3x, 100-300ms jitter)
              │
        disownData() ←── Speicher freigeben
              │
        callback.onBodyCaptured()
              │
        session.addArchivedDocId()
```

### Start/Stop Lifecycle
- **Start**: `research_session_start` (mode=research) → `NetworkIngestionPipeline.start(callback)`
- **Stop**: `ResearchSessionManager.remove()` → `pipeline.stop()`

### Konfiguration
- `maxBytesPerDoc`: Max Response-Body-Größe (default: 2MB)
- `headerAllowlist`: Nur diese Header werden gespeichert (default: content-type, content-length, last-modified, etag, cache-control)
- `domainPolicy`: include/exclude Listen
- MIME-Allowlist: text/html, text/plain, text/xml, text/csv, application/json, application/xml, application/xhtml+xml, ...
- Excluded URLs: /login, /signin, /auth, /oauth, /token, /checkout, /payment, ...

### Metriken
- `capturedCount`: Erfolgreich erfasste Bodies
- `skippedCount`: Übersprungen (Filter)
- `failedCount`: getData oder Callback fehlgeschlagen

## ReadinessState (wait-Parameter)

`browsingContext.navigate` mit `wait` (Default: `interactive`):
- `none`: sofort zurück, bevor Seite geladen
- `interactive`: DOM ist da, aber Bilder/Subresources evtl. noch nicht
- `complete`: alles geladen (kann auf heavy pages timeout verursachen)

## Tagging-Bridge (Click/Choose ohne JS clicks)

1. JS-Script beschreibt interaktive Elemente und taggt sie mit `data-mm-menu-id`
2. CSS `browsingContext.locateNodes("[data-mm-menu-id]")` → SharedReferences
3. `research_choose` nutzt `input.performActions` (PointerMove → Element-Origin → PointerDown → PointerUp)
4. Fallback bei performActions-Fehler: JS `callFunction` mit `el.scrollIntoView() + el.click()`
5. Nach jeder Aktion werden Tags bereinigt, neuer viewToken gesetzt

## Session-Isolation

- Pro Bot: eigener `UserContext` (Cookie/Storage-Isolation via `browser.createUserContext`)
- Pro Session: mehrere BrowsingContexts möglich
- Domain-Policy: include/exclude Listen filterbar
- Limits: maxUrls, maxDepth, maxBytesPerDoc

## Datei-Übersicht

### Package: `wd4j-mcp-server/research/`
- `ResearchSession.java` – Session-State (sessionId, userContextId, viewToken, menuItem→SharedRef, domainPolicy, limits, privacyPolicy, newArchivedDocIds)
- `ResearchSessionManager.java` – Singleton, pro BrowserSession eine Session
- `MenuView.java` – Immutable Snapshot (viewToken, excerpt, menuItems)
- `MenuItem.java` – Einzelner Menüeintrag (menuItemId, type, label, href, actionHint)
- `MenuViewBuilder.java` – Tagging-Bridge + Settle-Logik
- `NetworkIngestionPipeline.java` – Network-First Body Collection (addDataCollector → responseCompleted → getData → disownData → callback)
- `SettlePolicy.java` – Enum (NAVIGATION, DOM_QUIET, NETWORK_QUIET)

### Tools: `wd4j-mcp-server/tool/impl/`
- `ResearchSessionStartTool.java` – `research_session_start`
- `ResearchOpenTool.java` – `research_open`
- `ResearchMenuTool.java` – `research_menu`
- `ResearchChooseTool.java` – `research_choose` (WebDriver Actions + JS Fallback)
- `ResearchBackForwardTool.java` – `research_navigate`
- `ResearchConfigUpdateTool.java` – `research_config_update`

### Tools: `plugins/webSearch/tools/`
- `ResearchDocGetTool.java` – `research_doc_get` (H2 Archiv)
- `ResearchSearchTool.java` – `research_search` (Lucene)
- `ResearchQueueAddTool.java` – `research_queue_add`
- `ResearchQueueStatusTool.java` – `research_queue_status`

### Geändert
- `plugins/webSearch/plugin/WebSearchPlugin.java` – Alle alten Browse*-Tools entfernt, 10 Research-Tools + 5 Utility-Tools registriert
- `plugins/webSearch/build.gradle` – `compileOnly project(':app')`
- `wd4j-mcp-server/McpServerMain.java` – Alte Browser*-Tools durch Research-Tools ersetzt
- `app/ChatMode.java` – AGENT + RECHERCHE System-Prompts auf neue Tool-Namen umgestellt
- `app/ChatSession.java` – Fuzzy-Match mappt alte Tool-Namen auf neue; Auto-Archivierung triggert bei research_open/choose/menu
- `app/WebSnapshotPipeline.java` – Javadoc aktualisiert

### Gelöschte Dateien (durch Research-Tools ersetzt)
- `BrowseNavigateTool.java` → `ResearchOpenTool.java`
- `BrowseReadPageTool.java` → `ResearchMenuTool.java`
- `BrowseSnapshotTool.java` → `ResearchMenuTool.java`
- `BrowseClickTool.java` → `ResearchChooseTool.java`
- `BrowseLocateTool.java` → Tagging-Bridge (MenuViewBuilder)
- `BrowseBackForwardTool.java` → `ResearchBackForwardTool.java`
- `BrowseWaitTool.java` → Settle-Policies (NAVIGATION/DOM_QUIET/NETWORK_QUIET)
- `BrowserNavigateTool.java` → `ResearchOpenTool.java`
- `BrowserOpenTool.java` → `ResearchSessionStartTool.java`
- `BrowserClickCssTool.java` → `ResearchChooseTool.java`
- `BrowserTypeCssTool.java` → `BrowseTypeTool.java` (behalten)
- `BrowserWaitForTool.java` → Settle-Policies
- `BrowserLaunchTool.java` → `ResearchSessionStartTool.java`
- `BrowserCloseTool.java` → Session-Lifecycle
- `PageDomSnapshotTool.java` → `ResearchMenuTool.java` (Tagging-Bridge)
- `PageExtractTool.java` → `ResearchMenuTool.java` (excerpt)

## Beispiel-Workflow (Bot)

```
Bot: research_session_start(mode="research", domainPolicy={include:["news.example.com"]})
→ sessionId: "a1b2c3d4", userContextId: "uc-42", contexts: ["ctx-1"]

Bot: research_open(url="https://news.example.com", wait="interactive")
→ viewToken: v1, excerpt: "...", menuItems: [m0] link: "Headlines", [m1] link: "Sports"

Bot: research_choose(menuItemId="m1", viewToken="v1", settlePolicy="NAVIGATION")
→ viewToken: v2, excerpt: "Sports news...", menuItems: [m0] link: "Football", ...

Bot: research_choose(menuItemId="m0", viewToken="v2", settlePolicy="DOM_QUIET")
→ viewToken: v3, excerpt: "Football article...", menuItems: ...

Bot: research_navigate(action="back")
→ viewToken: v4, excerpt: "Sports news...", menuItems: ...

Bot: research_queue_add(urls=["https://news.example.com/page2", "..."])
→ {added: 2, skipped: 0}

Bot: research_queue_status()
→ {pending: 2, crawled: 1, indexed: 1, failed: 0}

Bot: research_search(query="football results")
→ {results: [{documentId: "...", snippet: "...", score: 0.85}]}

Bot: research_doc_get(entryId="abc-123")
→ {extractedText: "Full article text...", metadata: {...}}

Bot: research_config_update(limits={maxDepth:3}, defaultSettlePolicy="DOM_QUIET")
→ "Configuration updated: maxDepth: 3, defaultSettlePolicy: DOM_QUIET"
```

## Anforderungsabdeckung (Mapping)

| Anforderung | Status | Tool/Komponente |
|------------|--------|----------------|
| `research_session_start` mit UserContext | ✅ | ResearchSessionStartTool + browser.createUserContext |
| `research_open` mit `wait` | ✅ | ResearchOpenTool + browsingContext.navigate(wait) |
| `research_menu` mit `newArchivedDocs[]` | ✅ | ResearchMenuTool + drainNewArchivedDocIds() |
| `research_choose` mit viewToken-Validierung | ✅ | ResearchChooseTool + resolveMenuItem() |
| `research_choose` mit WebDriver Actions (nicht JS click) | ✅ | input.performActions + WDElementOrigin Fallback |
| `research_back`/`forward`/`reload` | ✅ | ResearchBackForwardTool |
| `research_doc_get` (H2) | ✅ | ResearchDocGetTool |
| `research_search` (Lucene) | ✅ | ResearchSearchTool |
| `research_queue_add`/`status` | ✅ | ResearchQueueAddTool / ResearchQueueStatusTool |
| `research_config_update` | ✅ | ResearchConfigUpdateTool |
| viewToken-Stabilitätsvertrag | ✅ | ResearchSession.isViewTokenValid() |
| Tagging-Bridge (data-mm-menu-id) | ✅ | MenuViewBuilder.buildDescribeScript() |
| Settle-Policies (NAVIGATION/DOM_QUIET/NETWORK_QUIET) | ✅ | MenuViewBuilder.settle() |
| Domain-Policy (include/exclude) | ✅ | ResearchSession.isUrlAllowed() |
| Limits (maxUrls, maxDepth, maxBytesPerDoc) | ✅ | ResearchSession config |
| Privacy-Policy (header allowlist) | ✅ | ResearchSession.headerAllowlist |
| Network Plane (addDataCollector/getData/disownData) | ✅ | NetworkIngestionPipeline |
| Event-Subscription (network.responseCompleted) | ✅ | addEventListener + Consumer |
| Retry/Backoff bei getData | ✅ | 3 Versuche, 100-300ms Jitter |
| Privacy-Filter (MIME, URL, Header-Allowlist) | ✅ | isCaptureableMime, isExcludedUrl, headerAllowlist |
| Pipeline-Lifecycle (start/stop mit Session) | ✅ | ResearchSessionStartTool + ResearchSessionManager |
| H2 Schema (request/response/body/doc/crawl_queue) | ⚠️ Teilweise | Bestehende archive_entries + web_cache Tabellen |
| Lucene Batch-Commit-Policy | ⚠️ Teilweise | Bestehende LuceneLexicalIndex.commitBatch() |
| SPA DOM-Snapshot-Pipeline | ⚠️ Teilweise | MutationObserver in DOM_QUIET settle |
| WebSocket/SSE-Tap | 🔮 Geplant | Erfordert Preload-Script WebSocket-Wrapper |
