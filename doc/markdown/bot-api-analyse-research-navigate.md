# Technische Analyse: Bot-API für research_navigate

**Datum:** 2026-02-25  
**Zweck:** Alle technischen Erkenntnisse aus den Chat-Sitzungen und der Architektur zusammenfassen, um eine optimierte Bot-API zu entwickeln.

---

## 1. Beobachtete Probleme (aus den Chat-Logs)

### 1.1 Problem: Bot ruft immer wieder dieselbe URL auf

**Chat 29de6920 (alte API mit research_open/research_choose):**
- Bot bekommt `[m0] link: ...` Format zurück
- Bot versteht nicht, was `research_choose` mit `viewToken` und `menuItemId` bedeutet
- Bot ruft stattdessen 3× `research_open` mit `https://de.yahoo.com/` auf
- Fehlermeldung "ALREADY on this page" greift nur beim 1. Mal als Error, danach liefert `research_open` trotzdem die Seite nochmal

**Chat 5bda9cef (neue API mit research_navigate):**
- Bot navigiert initial korrekt zu `https://de.nachrichten.yahoo.com/` → 50 Links mit URLs korrekt zurückgegeben
- Bot navigiert dann aber **zurück** zu `https://de.yahoo.com/` (warum?!)
- Danach **Endlosschleife**: Bot ruft 20+ Mal `research_navigate` mit `https://de.yahoo.com/` auf
- "ALREADY on this page"-Fehler wird korrekt zurückgegeben, aber Bot ignoriert ihn
- Bot versucht sogar `describe_tool` für `functions.research_navigate` (existiert nicht)
- Einmal navigiert er erfolgreich zur Politik-Seite, springt aber sofort wieder zurück zu yahoo.com
- Browser Timeouts nach 30s, Browser-Restarts, trotzdem immer wieder dieselbe URL

### 1.2 Kernursache

Der Bot (gpt4o-mini/20b-Modell) hat folgende Probleme:
1. **Er speichert die Link-URLs nicht im Kontext** – nach dem ersten navigate-Aufruf "vergisst" er die zurückgegebenen Links
2. **Er fällt auf seine "Ausgangs-URL" zurück** – die URL aus dem User-Prompt ("Yahoo Deutschland") wird als einzige URL behalten
3. **Er versteht das Antwort-Format nicht** – Die Linkliste wird nicht als "hier sind deine nächsten Optionen" verstanden
4. **Fehlermeldungen werden ignoriert** – "ALREADY on this page" wird x-fach wiederholt
5. **Die `[m0]` IDs waren die falsche Abstraktionsebene** – Kleine Modelle verstehen keine abstrakten Link-IDs

---

## 2. Aktuelle technische Architektur

### 2.1 Tool-Kette (Stand aktuell)

```
Einziges Tool: research_navigate
  Parameter: target (String) – URL (absolut/relativ) oder "back"/"forward"
  
Intern:
  1. Session auto-init (UserContext, RunId, Pipeline)
  2. URL-Auflösung (relativ → absolut gegen aktuelle Seite)
  3. Same-URL-Guard (blockiert identische URL)
  4. browsingContext.navigate(url, contextId, INTERACTIVE)
  5. MenuViewBuilder → Jsoup HTML-Parsing → MenuView
  6. Antwort: Titel, Excerpt, Link-Liste, Network-Traffic
```

### 2.2 Datenfluss (Network Module)

```
Browser-Navigation
        │
        ▼
  WebDriver BiDi: browsingContext.navigate(url, wait=interactive)
        │
        ▼
  NetworkIngestionPipeline (läuft im Hintergrund)
    ├── network.responseCompleted Events
    ├── Filter: Status 2xx, MIME allowlist, URL nicht excluded
    ├── network.getResponseBody() mit Retry (3x)
    ├── Callback → H2-Archiv + Lucene-Index
    └── Kategorisierung: HTML/JS/CSS/XHR/FONT/IMAGE/MEDIA/OTHER
        │
        ▼
  MenuViewBuilder
    ├── Pipeline.getLastHtmlBody() → letzte HTML-Response
    ├── HtmlLinkExtractor.parse(html, url, maxLinks, excerptLen)
    │     ├── Jsoup DOM-Parsing (kein JS!)
    │     ├── Title: <title>, og:title, <h1>
    │     ├── Excerpt: <article>, [role=main], <main>, body (ohne nav/footer/script)
    │     └── Links: <a href> mit Dedupe, Label, Kategorisierung
    └── MenuView(viewToken, url, title, excerpt, menuItems)
```

### 2.3 Verfügbare Daten pro Seite

| Datenquelle | Was | Verfügbar ohne JS-Injection |
|-------------|-----|---------------------------|
| HTML Body | Seitentitel, Excerpt, Links | ✅ Ja (Jsoup) |
| network.responseCompleted | URL, Status, Headers, MIME | ✅ Ja |
| network.getResponseBody | Body-Text | ✅ Ja |
| HTTP Headers | content-type, etag, cache-control, last-modified | ✅ Ja (gefiltert) |
| Redirect-Chain | Endgültige URL nach Redirects | ✅ Ja (NavigateResult.url) |
| XHR/Fetch Responses | API-Daten, JSON | ✅ Ja (Pipeline erfasst alles ≥2xx) |
| Cookie-Banner | Button-Texte, Consent-URLs | ⚠️ Nur über CSS-Selektor-Dismissal |
| DOM nach JS-Rendering | SPA-Inhalte | ❌ Nicht ohne Scripts |
| JavaScript-Events | Clicks, Input-Changes | ❌ Nicht ohne Scripts |

### 2.4 Was das Network Module liefert

Die `NetworkIngestionPipeline` erfasst automatisch:

1. **Alle HTTP-Responses** (gefiltert nach MIME/Status/Domain)
2. **Response-Bodies** via `network.getResponseBody(requestId)`
3. **Kategorisierte Traffic-Zähler** (HTML:x, JS:y, CSS:z, XHR:w, ...)
4. **Archivierte Dokument-IDs** für spätere Volltextsuche

**Nicht genutzt, aber verfügbar via BiDi:**
- `network.beforeRequestSent` – Request-Headers, Cookies
- `network.responseStarted` – Response-Start-Zeitpunkt
- `network.fetchError` – Fehlgeschlagene Requests
- `browsingContext.navigationStarted/Completed` – Navigation-Timing
- `browsingContext.domContentLoaded` – DOM-Ready-Zeitpunkt

---

## 3. Identifizierte Design-Fehler der aktuellen API

### 3.1 Fehlermeldung greift nicht nachhaltig

**Problem:** Die "ALREADY on this page"-Fehlermeldung wird beim 2. Aufruf als `isError=true` geliefert, aber der Bot wiederholt trotzdem. Beim Bot (kleines Modell) führt eine einmalige Fehlermeldung nicht zum Umdenken.

**Lösung:** 
- Fehlermeldung muss **konkreter** sein: nicht nur "Pick a link", sondern **explizit 2-3 empfohlene URLs nennen** die zum User-Intent passen
- Ein "retry counter" pro URL, nach 3 Fehlversuchen → **konkrete Navigation vorschlagen** ("Ich navigiere für dich zu: ...")

### 3.2 Link-Format nicht bot-tauglich

**Problem:** Das Format `URL – Beschreibung` ist für kleine Modelle zu subtil. Der Bot sieht die URL, kopiert sie aber nicht als `target`-Parameter.

**Aktuelles Format:**
```
── Links (50) ──────────────
  https://de.nachrichten.yahoo.com/sport/bundesliga/ – Bundesliga
  https://de.nachrichten.yahoo.com/politik/ – Politik
  ...
```

**Vorschlag (Bot-optimiert):**
```
── Hier kannst du als nächstes hin ──
Für Bundesliga:  https://de.nachrichten.yahoo.com/sport/bundesliga/
Für Politik:     https://de.nachrichten.yahoo.com/politik/
Für Panorama:    /panorama/
Für Sport:       /sport/
...
```

Key Insight: **"Für X: URL"** statt **"URL – X"** → Der Bot denkt "ich will Politik" → sieht "Für Politik:" → nimmt die URL danach.

### 3.3 Relative URLs nicht genug genutzt

**Problem:** Absolute URLs (z.B. `https://de.nachrichten.yahoo.com/sport/bundesliga/`) verschwenden Token und verwirren den Bot. Relative URLs (`/sport/bundesliga/`) sind kürzer und werden vom Tool automatisch aufgelöst.

**Lösung:** Wenn die Link-URL auf derselben Domain liegt, nur den Pfad zurückgeben (relativ). Das Tool löst bereits relative Pfade auf (`resolveUrl()`).

### 3.4 Zu viele Links (50!)

**Problem:** 50 Links überfluten den Kontext des Bots. Ein 20b-Modell hat ~4k Token Kontext für Tool-Antworten.

**Lösung:** 
- Default `maxMenuItems` auf **15-20** reduzieren
- Links nach **Relevanz zum User-Intent** sortieren (dafür müsste der User-Intent an das Tool übergeben werden)
- Kategorien-basiert: erst Navigations-Links, dann Content-Links

### 3.5 Keine "Intent-Awareness"

**Problem:** Der Bot wird gebeten "Suche Wirtschaft und Politik auf Yahoo". Das Tool weiß nichts vom User-Intent und liefert alle 50 Links. Der Bot kann nicht filtern.

**Lösung (ohne injizierte Scripts):**
- Optional: `hint`-Parameter in `research_navigate` → wird an die Antwort durchgereicht als "Du suchst: {hint} – diese Links sind relevant:"
- Die Antwort selbst enthält dann **priorisierte Links** (die zum Hint passen kommen zuerst)
- Das ist kein Filter im Tool, sondern eine **Sortier-Hilfe** für den Bot

### 3.6 Session-Start ist ein separater Aufruf (war)

**Status:** Bereits gelöst! `research_navigate` startet die Session automatisch via `ensureSession()`. `research_session_start` ist nicht mehr nötig.

---

## 4. Was technisch möglich ist (ohne JS-Injection)

### 4.1 Vollständig verfügbar ✅

| Feature | Wie | Status |
|---------|-----|--------|
| URL-Navigation | `browsingContext.navigate` | ✅ Implementiert |
| Seitentext extrahieren | Jsoup auf HTML-Body | ✅ Implementiert |
| Links extrahieren | Jsoup `<a href>` | ✅ Implementiert |
| Vor/Zurück | `window.history.back/forward` (einzige erlaubte Eval) | ✅ Implementiert |
| HTTP-Response-Bodies archivieren | `network.getResponseBody` | ✅ Implementiert |
| Volltextsuche im Archiv | Lucene | ✅ Implementiert |
| XHR/API-Responses mitlesen | NetworkIngestionPipeline | ✅ Implementiert |
| Cookie-Banner wegklicken | CSS-Selektor-basiert | ⚠️ Teilweise |
| Relative URL-Auflösung | `resolveUrl()` | ✅ Implementiert |
| Same-URL-Erkennung | URI-Normalisierung | ✅ Implementiert |
| Redirect-Erkennung | NavigateResult.url | ✅ Implementiert |
| Meta-Daten (og:title, description) | Jsoup | ✅ Möglich (title ja, description nein) |

### 4.2 Erweiterbar ohne JS-Injection ⚡

| Feature | Wie | Aufwand |
|---------|-----|--------|
| **Meta-Description** extrahieren | Jsoup `meta[name=description]` | Gering |
| **Formulare** erkennen | Jsoup `<form>`, `<input>`, `<select>` | Mittel |
| **HTTP-Header** auswerten (Content-Language, etc.) | Pipeline-Header-Erfassung | Gering |
| **Redirect-Kette** anzeigen | Bereits in NavigateResult vorhanden | Gering |
| **Response-Timing** | `network.responseCompleted` timestamps | Gering |
| **Fehler-Responses** (403, 404, 500) erkennen | Status aus Pipeline | Gering |
| **JSON-API-Responses** parsen | Pipeline XHR-Bodies + Gson | Mittel |
| **RSS/Atom-Feeds** erkennen und parsen | Jsoup `link[type=application/rss+xml]` | Mittel |
| **Pagination** erkennen | Jsoup `<a>` mit Seitennavigation-Heuristik | Mittel |
| **Canonical-URL** | Jsoup `link[rel=canonical]` | Gering |

### 4.3 Nicht möglich ohne JS-Injection ❌

| Feature | Warum nicht |
|---------|------------|
| SPA-Inhalte (React/Vue) die nur via JS gerendert werden | DOM ist nur im Browser |
| Infinite-Scroll-Inhalte | Erfordert Scroll-Events + JS |
| Dynamisch geladene Menüs (Hamburger/Dropdown) | Erfordert Click + JS |
| Client-Side-Routing (React Router etc.) | Erfordert JS pushState |
| Formular-Submission (POST) | Erfordert JS oder Input-Actions |
| Login/Authentifizierung | Erfordert JS + Formular-Handling |

---

## 5. Optimierungsvorschläge für die Bot-API

### 5.1 Antwort-Format optimieren

**Ziel:** Ein 20b-Modell muss innerhalb von max. ~1500 Token Tool-Antwort verstehen, wo es als nächstes hin kann.

```
Du bist auf: Yahoo Deutschland (https://de.yahoo.com/)

Hier ist ein Auszug vom Seiteninhalt:
Fall Epstein: Bill Gates gesteht zwei Affären. Treffen von Merz und Xi...

Hier kannst du weiternavigieren:
  Für Nachrichten:        /nachrichten/
  Für Politik:            /politik/
  Für Wirtschaft/Finanzen: https://de.finance.yahoo.com/
  Für Sport:              /sport/
  Für Artikel "Bill Gates gesteht Affären": /nachrichten/epstein-ermittlungen-bill-gates-gibt-115153148.html
  Für Artikel "Merz und Xi":               /nachrichten/merz-xi-wollen-deutsch-chinesische-122318982.html
  ...

Rufe research_navigate mit einer der URLs auf, um weiterzunavigieren.
```

### 5.2 Same-URL Endlosschleifen-Breaker

Statt nur Fehlermeldung, **aktiv eine Alternative vorschlagen**:

```java
// Nach 2. Fehlversuch auf gleicher URL:
"Du hast diese Seite bereits besucht. 
 Basierend auf den verfügbaren Links empfehle ich:
 → Für Nachrichten: research_navigate('/nachrichten/')
 → Für Politik: research_navigate('/politik/')
 Rufe research_navigate mit EINER dieser URLs auf."
```

### 5.3 Link-Priorisierung

In `HtmlLinkExtractor` werden Links bereits kategorisiert (content → section → other). Diese Kategorisierung sollte sich im Output widerspiegeln:

```
── Hauptinhalte ──
  /nachrichten/epstein-bill-gates-115153148.html – Bill Gates gesteht Affären
  /nachrichten/merz-xi-122318982.html – Treffen Merz und Xi

── Rubriken ──
  /politik/ – Politik
  /sport/ – Sport
  /nachrichten/ – Alle Nachrichten

── Extern ──
  https://de.finance.yahoo.com/ – Finanzen
```

### 5.4 Token-Budget einhalten

| Antwort-Teil | Max. Token | Strategie |
|--------------|-----------|-----------|
| Seitentitel + URL | ~30 | Fest |
| Excerpt | ~500 | `excerptMaxLength` auf 500 reduzieren |
| Link-Liste | ~600 | Max 15 Links, relative Pfade |
| Metadata | ~100 | Network-Traffic, Archiv-IDs |
| Instruction | ~50 | "Rufe research_navigate auf" |
| **Gesamt** | **~1280** | Passt in 4k-Kontext |

### 5.5 "Für X: URL" statt "URL – X"

**Psychologische Begründung:** Ein kleines Modell liest die Antwort als natürliche Sprache. "Für Politik: /politik/" wird als Handlungsanweisung verstanden ("wenn du Politik willst, geh hier hin"). "URL – Beschreibung" ist eine Datentabelle, die nicht zur Aktion anregt.

---

## 6. Zusammenfassung: Was wir haben und was fehlt

### Haben wir ✅
- Einziges Navigations-Tool (`research_navigate`)
- Automatischer Session-Start
- HTML-Parsing ohne JS-Injection (Jsoup)
- Network-Response-Archivierung
- Same-URL-Guard
- Relative URL-Auflösung
- Vor/Zurück-Navigation
- Volltextsuche im Archiv

### Fehlt / Muss optimiert werden 🔧
- [ ] **Link-Format**: `URL – Label` → `Für Label: URL` (bot-freundlich)
- [ ] **Relative Pfade bevorzugen**: Same-Domain-Links als `/pfad/` statt volle URL
- [ ] **Max Links reduzieren**: 50 → 15-20
- [ ] **Excerpt kürzen**: 3000 → 500-800 Zeichen
- [ ] **Endlosschleifen-Breaker**: Nach 2 Fehlversuchen konkrete Alternative vorschlagen
- [ ] **Kategorisierte Link-Ausgabe**: Hauptinhalte / Rubriken / Extern
- [ ] **Meta-Description** extrahieren und mit in Excerpt einbauen
- [ ] **Intent-Hint**: Optionaler `hint`-Parameter zur Link-Priorisierung
- [ ] **Tool-Description** optimieren für kleine Modelle
- [ ] **Architektur-Doku anpassen**: Beispiel-Workflow aktualisieren (kein `m0`/`m1` mehr)

### Bewusst verzichtet 🚫
- Injizierte Scripts (außer Cookie-Banner-Dismissal)
- SPA-Rendering
- Formular-Handling
- Client-Side-Routing
- WebSocket/SSE-Tapping

---

## 7. Nächste Schritte

1. **MenuItem.toCompactString()** → neues Format `Für {label}: {relativeUrl}`
2. **MenuView.toCompactText()** → kategorisierte Ausgabe, kürzerer Excerpt
3. **ResearchNavigateTool.buildResponseText()** → klarere Instruktion
4. **ResearchNavigateTool** → Endlosschleifen-Breaker (Retry-Counter)
5. **HtmlLinkExtractor** → Meta-Description extrahieren
6. **ResearchSession** → `maxMenuItems` Default auf 15
7. **Integration-Tests** mit kleinem Modell validieren

