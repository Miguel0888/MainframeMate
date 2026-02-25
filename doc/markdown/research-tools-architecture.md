# MCP Research Tool Suite – Architektur und Nutzung

## Übersicht

Die Research-Tool-Suite bietet einem Bot eine **menübasierte Navigation** durch Webseiten.
Statt DOM-Details (CSS-Selektoren, XPath) arbeitet der Bot mit **Action-Tokens** (menuItemIds)
und **viewTokens** für Race-Condition-freie Interaktion.

## Architektur: 3-Plane-System

```
┌─────────────────────────────────────────────────────────────┐
│  Bot (LLM)                                                  │
│  ↕ MCP Tools (research_open, research_menu, ...)            │
├─────────────────────────────────────────────────────────────┤
│  Research Layer (wd4j-mcp-server/research/)                 │
│  ├── ResearchSession      → viewToken, menuItem→SharedRef   │
│  ├── MenuViewBuilder      → Tagging-Bridge (JS→CSS locate)  │
│  ├── MenuView / MenuItem  → Datenmodell                     │
│  └── SettlePolicy         → Wait-Strategie                  │
├─────────────────────────────────────────────────────────────┤
│  Action Plane              Network Plane      DOM Plane     │
│  BrowserSession            (künftig:          JS Scripts    │
│  navigate/click/type       DataCollector)     Tagging       │
│  WebDriver BiDi                               MutationObs   │
├─────────────────────────────────────────────────────────────┤
│  Persistenz: H2 (ArchiveRepository) + Lucene (SearchService)│
└─────────────────────────────────────────────────────────────┘
```

## Tool-Suite

### Kern-Tools (Prio 1)

| Tool | Beschreibung | Eingabe | Ausgabe |
|------|-------------|---------|---------|
| `research_open` | URL navigieren + Menüansicht | `url` (required), `settlePolicy` | viewToken, excerpt, menuItems[] |
| `research_menu` | Aktuelle Menüansicht ohne Navigation | `selector` (optional) | viewToken, excerpt, menuItems[] |
| `research_choose` | Menüeintrag wählen (klicken) | `menuItemId`, `viewToken` (required), `settlePolicy` | viewToken, excerpt, menuItems[] |

### Navigations-Tools (Prio 2)

| Tool | Beschreibung | Eingabe |
|------|-------------|---------|
| `research_navigate` | Back/Forward/Reload | `action` (back/forward/reload), `settlePolicy` |

### Archiv/Such-Tools (Prio 3)

| Tool | Beschreibung | Eingabe |
|------|-------------|---------|
| `research_doc_get` | Archiviertes Dokument abrufen | `entryId` oder `url`, `maxTextLength` |
| `research_search` | Lucene-Suche | `query` (required), `maxResults` |

### Low-Level-Tools (weiterhin verfügbar)

`web_navigate`, `web_snapshot`, `web_click`, `web_type`, `web_read_page`, etc.

## viewToken-Vertrag

- Jede MenuView hat einen `viewToken` (z.B. `v1`, `v2`, ...)
- `menuItemId`s (z.B. `m0`, `m3`) sind **nur innerhalb** desselben viewTokens gültig
- Bei Navigation/Reload/Choose wird ein neuer viewToken erzeugt
- `research_choose` mit einem stale viewToken wird **abgelehnt** → Bot muss `research_menu` aufrufen

## Settle-Policies

| Policy | Wann verwenden | Implementierung |
|--------|---------------|-----------------|
| `NAVIGATION` | Standard-Links (full page load) | 1s Delay nach navigate() |
| `DOM_QUIET` | SPA-Clicks (keine Navigation) | MutationObserver wartet auf 500ms Ruhe |
| `NETWORK_QUIET` | AJAX-Seiten | PerformanceObserver wartet auf 500ms Ruhe |

## Tagging-Bridge

1. JS-Script beschreibt interaktive Elemente und taggt sie mit `data-mm-menu-id`
2. CSS `locateNodes("[data-mm-menu-id]")` liefert SharedReferences
3. `research_choose` nutzt SharedReference für Klick (nicht JS click)
4. Nach jeder Aktion werden Tags bereinigt und neue gesetzt

## Datei-Übersicht

### Neues Package: `wd4j-mcp-server/research/`
- `ResearchSession.java` – Session-State (viewToken, menuItem→SharedRef)
- `ResearchSessionManager.java` – Singleton, pro BrowserSession eine Session
- `MenuView.java` – Immutable Snapshot (viewToken, excerpt, menuItems)
- `MenuItem.java` – Einzelner Menüeintrag (menuItemId, type, label, href)
- `MenuViewBuilder.java` – Tagging-Bridge + Settle-Logik
- `SettlePolicy.java` – Enum (NAVIGATION, DOM_QUIET, NETWORK_QUIET)

### Neue Tools: `wd4j-mcp-server/tool/impl/`
- `ResearchOpenTool.java` – `research_open`
- `ResearchMenuTool.java` – `research_menu`
- `ResearchChooseTool.java` – `research_choose`
- `ResearchBackForwardTool.java` – `research_navigate`

### Neue Tools: `plugins/webSearch/tools/`
- `ResearchDocGetTool.java` – `research_doc_get` (H2 Archiv)
- `ResearchSearchTool.java` – `research_search` (Lucene)

### Geändert: `plugins/webSearch/plugin/WebSearchPlugin.java`
- Research-Tools vor den Low-Level-Tools registriert

### Geändert: `plugins/webSearch/build.gradle`
- `compileOnly project(':app')` für Archiv/Search-Zugriff

## Beispiel-Workflow (Bot)

```
Bot: research_open(url="https://news.example.com")
→ viewToken: v1, excerpt: "...", menuItems: [m0] link: "Headlines" → ..., [m1] link: "Sports" → ...

Bot: research_choose(menuItemId="m1", viewToken="v1")
→ viewToken: v2, excerpt: "Sports news...", menuItems: [m0] link: "Football" → ..., ...

Bot: research_choose(menuItemId="m0", viewToken="v2", settlePolicy="DOM_QUIET")
→ viewToken: v3, excerpt: "Football article...", menuItems: ...

Bot: research_navigate(action="back")
→ viewToken: v4, excerpt: "Sports news...", menuItems: ...

Bot: research_search(query="football results")
→ {results: [{documentId: "...", snippet: "...", score: 0.85}]}

Bot: research_doc_get(entryId="abc-123")
→ {extractedText: "Full article text...", metadata: {...}}
```

