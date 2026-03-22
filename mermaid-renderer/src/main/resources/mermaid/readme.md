# Mermaid Renderer

Renders Mermaid diagram code to SVG in pure Java via GraalJS.

Mermaid JS converter has its own licence and comes from:

<https://cdn.jsdelivr.net/npm/mermaid@9.4.3/dist/>

**Download URL:** <https://cdn.jsdelivr.net/npm/mermaid@9.4.3/dist/mermaid.min.js>

Version 9.x (UMD/IIFE bundle). Neuere Versionen sind ESM-only und erfordern einen Bundler.

## Ausführen

Vom **Projekt-Root**:

    gradlew :mermaid-renderer:run

Tests:

    gradlew :mermaid-renderer:test

Visueller Test (im `app`-Modul):

    gradlew :app:run -PmainClass=de.bund.zrb.mermaid.MermaidRenderTest

## Architektur

| Klasse | Zweck |
|--------|-------|
| `MermaidRenderer` | Singleton-Fassade — `renderToSvg(diagramCode)` |
| `MermaidSvgFixup` | Post-Processing: Batik-Kompatibilitäts-Fixes für SVG |
| `GraalJsExecutor` | Interner GraalJS Polyglot-Context Wrapper |
| `JsExecutionResult` | Immutables Ergebnisobjekt (success/failure) |
| `MermaidRendererMain` | Standalone-Main zum manuellen Testen |

### MermaidSvgFixup — Batik-Kompatibilität

Mermaid erzeugt SVG, das in Browsern funktioniert, aber in Apache Batik
(unserem Rasteriser) diverse Probleme hat. `MermaidSvgFixup.fixForBatik()`
wendet folgende DOM-Level-Fixes an:

| Fix | Problem | Lösung |
|-----|---------|--------|
| `moveMarkersToDefs` | Batik findet Marker nur in `<defs>` | Alle `<marker>` nach `<defs>` verschieben |
| `fixMarkerFills` | Marker-Pfeile unsichtbar (fill:none Vererbung) | Explizites `fill="#333333"` + `style` setzen |
| `fixMarkerViewBox` | viewBox 12×20 auf 12×12 Marker → Verzerrung | viewBox entfernen, marker-eigene Koordinaten nutzen |
| `fixGroupZOrder` | **Nodes malen ÜBER Pfeile → Spitzen verdeckt** | Reihenfolge: nodes → edgePaths → edgeLabels |
| `fixNodeZOrder` | Text hinter Shape | Shapes vor Labels sortieren |
| `fixLabelCentering` | Text nicht zentriert (dominant-baseline) | `dy="0.35em"` + `text-anchor="middle"` |
| `fixEdgeStrokes` | Linien unsichtbar (CSS-abhängig) | Explizite `stroke`/`stroke-width` Attribute |
| `fixEdgeLabelBackground` | Label-Hintergrund fehlt | fill/opacity auf vorhandene rects |
| `fixEdgeLabelRect` | Kein Rect hinter Label-Text | Background-Rect einfügen (opacity=1) |
| `fixCssFillNone` | CSS `fill:none` überschreibt Marker-Fill | CSS-Override für `.arrowMarkerPath` |
| `fixCssForBatik` | **`hsl()`, `rgba()`, `filter`, `position` → Batik crasht** | hsl→hex, rgba→hex, unsupported props strippen |
| `fixSequenceLifelines` | **Lebenslinien zu kurz → enden vor unteren Actor-Boxen** | y2 auf Top der unteren Boxen verlängern, y1 auf Bottom der oberen Boxen |
| `fixViewBoxFromAttributes` | ViewBox zu klein (fehlende Element-Koordinaten) | Alle x/y/width/height/x1/y1/x2/y2 scannen |
| `setDimensions` | **SVG hat keine/falsche Pixel-Dimensionen → verpixeltes Bild** | `width`+`height` setzen: max(vbW,vbH) → 2000px, andere Achse proportional |

### Ressourcen

| Datei | Zweck |
|-------|-------|
| `browser-shim.js` | Minimales Browser-Umfeld für GraalJS (DOM, CSS, Selektoren) |
| `mermaid.min.js` | Mermaid 9.4.3 UMD-Bundle (extern, nicht eingecheckt) |

### Integration in app

    implementation project(':mermaid-renderer')

    MermaidRenderer renderer = MermaidRenderer.getInstance();
    String svg = renderer.renderToSvg("graph TD; A-->B;");
    svg = MermaidSvgFixup.fixForBatik(svg); // Batik-kompatibel machen

### Visueller Test (MermaidRenderTest)

Das `app`-Modul enthält `MermaidRenderTest` — ein interaktives Swing-Tool:

- **8 fokussierte Micro-Tests** mit Zoom+Pan pro Diagramm (Mausrad, Drag, +/−/Einpassen-Buttons)
- **Erwartungsbeschreibung** über jedem Bild
- **Anmerkungsfeld** für Feedback (optional)
- **Spezifische Ja/Nein/Teilweise-Fragen** pro Testcase
- Ergebnis als JSON → `mermaid-test-result.json`

### Browser-Shim — `_computeElementDims`

Mermaid verwendet `getBBox()` zur Layout-Berechnung (Knotengrößen, Pfadendpunkte).
Der Browser-Shim implementiert `_computeElementDims()` für genaue Dimensionen:

| SVG-Element | Dimensionsquelle |
|-------------|-----------------|
| `rect` | `width`/`height` Attribute |
| `circle` | `r` × 2 |
| `ellipse` | `rx` × 2, `ry` × 2 |
| `polygon` | `points` parsen → min/max Bounding Box |
| `line` | `x1`/`y1`/`x2`/`y2` |
| `text` | `_estimateTextWidth()` (char × 8px + 16px) |
| `g`/Container | Kinder-BBoxes aggregieren (min/max, mit Transform-Offset) |

