# Mermaid JS → SVG Rendering Spike (GraalJS)

Mermaid JS converter has its own licence and comes from:

<https://cdn.jsdelivr.net/npm/mermaid@9.4.3/dist/>

**Download URL:** <https://cdn.jsdelivr.net/npm/mermaid@9.4.3/dist/mermaid.min.js>

Version 9.x empfohlen, da sie noch als UMD/IIFE-Bundle verfügbar ist, nicht ESM-only wie neuere Versionen.

## Ausführen

Spike vom **Projekt-Root** ausführen:

    gradlew :mermaid-spike:run

Tests ausführen:

    gradlew :mermaid-spike:test

## Spike-Ergebnisse

| Stage | Beschreibung | Status |
|-------|-------------|--------|
| 1 | GraalJS läuft auf Java 8 sauber | ✅ |
| 2 | Pseudo-Browser-Globals (window, document, navigator, console, setTimeout etc.) | ✅ |
| 3 | Echtes mermaid.min.js Bundle laden (2.7 MB UMD) | ✅ |
| 4 | `graph TD; A-->B; B-->C;` → SVG rendern (7.5 KB SVG) | ✅ |

Alle Unit-Tests grün, Interpreter-Warnung unterdrückt.

## Architektur

### Dateien

| Datei | Zweck |
|-------|-------|
| `browser-shim.js` | Minimales Browser-Umfeld für GraalJS (window, document, DOM, CSS, Selektoren) |
| `mermaid.min.js` | Mermaid 9.4.3 UMD-Bundle (extern, nicht eingecheckt) |
| `GraalJsExecutor` | GraalJS Polyglot-Context mit JavaBridge |
| `MermaidRenderingProbe` | 4-stufige Probe (Basic JS → Browser-Shim → Mermaid-Load → SVG-Render) |
| `GraalJsSpikeMain` | Main-Klasse, führt alle Stages aus |

### browser-shim.js Umfang

Der Shim stellt bereit:
- **EventTarget** mit addEventListener/removeEventListener/dispatchEvent
- **DOM-Elemente** mit appendChild, removeChild, querySelector, querySelectorAll
- **Selektor-Engine** für `#id`, `.class`, `tag`, `[attr="value"]`, Komma-Kombis
- **CSSStyleDeclaration** mit setProperty/getPropertyValue/removeProperty
- **DOM-Konstruktoren** (Element, Node, NodeFilter, NamedNodeMap etc.) für DOMPurify
- **Standard Built-ins** auf window (Error, Map, Set, Promise, Symbol etc.)
- **Browser-APIs** (console, setTimeout, DOMParser, XMLSerializer, MutationObserver etc.)

### SVG-Extraktion

Das Callback von `mermaid.render()` liefert kein SVG direkt. Stattdessen wird
das SVG aus dem DOM extrahiert: Der XMLSerializer serialisiert das SVG-Element
aus dem Rendering-Container.

## Nächste Schritte

1. **SVG → Bild**: Batik (bereits im Projekt) kann das SVG in BufferedImage umwandeln
2. **Integration**: `MermaidRenderer` Klasse im `app`-Modul erstellen
3. **Markdown-Pipeline**: Mermaid-Codeblöcke in Markdown erkennen und durch `<img>` ersetzen
4. **Performance**: GraalJS-Context vorinitialisieren und Mermaid-Bundle nur einmal laden
