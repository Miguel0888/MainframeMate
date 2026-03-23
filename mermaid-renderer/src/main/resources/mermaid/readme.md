# Mermaid Renderer

Renders Mermaid diagram code to SVG in pure Java via GraalJS.

## Mermaid-Version

Aktuell gebündelt: **Mermaid 11.4.1** (ESM → IIFE via esbuild).

Das Bundle wird erzeugt über `js-bundle/`:

    cd mermaid-renderer/js-bundle && npm install && npm run bundle

Output: `src/main/resources/mermaid/mermaid.min.js`

Ältere Versionen (9.x) waren UMD/IIFE und konnten direkt heruntergeladen werden.
Ab 10.x ist Mermaid ESM-only und erfordert den esbuild-Bundler.

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
| `mermaid.min.js` | Mermaid 11.4.1 IIFE-Bundle (generiert via esbuild, nicht eingecheckt) |

### Integration in app

    implementation project(':mermaid-renderer')

    MermaidRenderer renderer = MermaidRenderer.getInstance();
    String svg = renderer.renderToSvg("graph TD; A-->B;");
    svg = MermaidSvgFixup.fixForBatik(svg); // Batik-kompatibel machen

### Visueller Test (MermaidRenderTest)

Das `app`-Modul enthält drei visuelle Testsuiten:

| Klasse | Inhalt |
|--------|--------|
| `MermaidRenderTest` | 8 Micro-Tests (Flowchart, Sequenz, Mindmap, …) — Grundfunktionalität |
| `MermaidRenderTest2` | 15 Tests für erweiterte Diagrammtypen (Class, State, ER, Gantt, Pie, …) |
| `MermaidRenderTest3` | 6 Tests für noch fehlende/experimentelle Diagrammtypen |

Alle Tests bieten Zoom+Pan pro Diagramm (Mausrad, Drag, +/−/Einpassen),
Erwartungsbeschreibung, Anmerkungsfeld und spezifische Ja/Nein/Teilweise-Fragen.
Ergebnis als JSON → `mermaid-test-result.json` / `mermaid-test2-result.json`.

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

## Bekannte Issues (Test2 — kosmetische Mängel)

Die folgenden Diagrammtypen rendern korrekt, haben aber kosmetische Schwächen:

### TC1 — Klassendiagramm (UML)

- **Abstraktes Label**: Der `<<abstract>>`-Stereotyp sitzt in einer zu großen Box
  mit viel Leerraum darunter. Die Schrift von „Animal" ist dagegen gequetscht.
- **Positionierung**: Abstands- und Größenberechnung für Klassen-Compartments
  (Attribute/Methoden) ist noch nicht optimal.

### TC3 — Entity-Relationship (Bibliothek)

- **Attribut-Überlagerung**: Attribute innerhalb der Entitäts-Boxen überlagern sich
  teilweise. Die Texte werden in dieselbe Zeile geschrieben statt untereinander.
- **Ursache**: `tspan`-Mehrzeilenlogik im Browser-Shim nicht vollständig browsernah.

### TC4 — User Journey (Online-Shop)

- **Fehlende Zufriedenheitswerte**: Die numerischen Scores (1–5) werden nicht
  angezeigt, nur die Smileys unten auf den Lebenslinien.

### TC5 — Gantt-Chart (Projekt)

- **Einpassung**: Das Gantt-Diagramm ist naturgemäß sehr breit. Die Zeitachse
  wird korrekt dargestellt, aber das Diagramm füllt nur einen schmalen Streifen
  in der Test-UI (oben und unten viel Weißraum). Das liegt am Seitenverhältnis
  und ist kein Bug im Renderer.

### TC7 — Quadrant-Chart (Priorisierung)

- **Y-Achsenbeschriftung beschnitten**: Die Beschriftung der Y-Achse ist links
  minimal abgeschnitten (ViewBox-Berechnung zu knapp).
- **Überschrift beschnitten**: Der Titel oben ist leicht abgeschnitten.
- **Text-Abstände**: Y-Achsen-Label haben größeren Abstand als X-Achsen-Label.

### TC11 — XY-Chart (Temperatur + Regen)

- **Y-Achsenbeschriftung beschnitten**: Wie bei TC7 ist die Beschriftung der
  Y-Achse links minimal abgeschnitten.
- **Text-Abstände**: Monatsnamen sind dichter an der X-Achse als „Werte" an der Y-Achse.

### TC12 — Block-Diagramm (Systemarchitektur)

- **Überdeckte Pfeile**: Der Pfeil von Service B zur Datenbank überdeckt sich
  mit dem Pfeil von Service A und ist dadurch nicht separat sichtbar.

### TC14 — Architecture-Diagramm (Cloud)

- **Icon-Überlagerung**: Gruppensymbole (Cloud, Server-Rack) überlagern sich
  teilweise mit den Service-Icons innerhalb derselben Gruppe.
- **Stray Arrow**: Oben bei OnPrem gibt es eine lose Pfeilspitze, deren Herkunft
  unklar ist.

## Fehlende Diagrammtypen (Test3)

Die folgenden Diagrammtypen werden von Mermaid 11.x unterstützt, funktionieren
aber noch **nicht** korrekt mit dem GraalJS-Renderer + Browser-Shim + Batik:

| # | Diagrammtyp | Status | Problem |
|---|-------------|--------|---------|
| 1 | **Requirement Diagram** | ⚠️ SVG wird erzeugt, Batik rendert | Text-Überlagerungen: Mehrzeilige Labels werden in eine Zeile gequetscht (`tspan`-Bug im Shim) |
| 2 | **C4-Diagramm** (System Context) | ❌ SVG-Rendering fehlgeschlagen | Mermaid JS erzeugt kein valides SVG (fehlende C4-Unterstützung im Shim oder Bundle-Problem) |
| 3 | **ZenUML** | ❌ SVG-Rendering fehlgeschlagen | Mermaid JS erzeugt kein valides SVG (ZenUML benötigt wahrscheinlich zusätzliche DOM-APIs) |
| 4 | **Radar-Chart** (`radar-beta`) | ❌ SVG-Rendering fehlgeschlagen | Experimenteller Mermaid-Diagrammtyp; vermutlich fehlende Shim-APIs für `<canvas>` oder trigonometrische Layout-Berechnungen |
| 5 | **Treemap** (`treemap-beta`) | ❌ SVG-Rendering fehlgeschlagen | Experimenteller Mermaid-Diagrammtyp; vermutlich fehlende D3-Layout-Funktionen im Shim |
| 6 | **Venn-Diagramm** (`venn`) | ❌ SVG-Rendering fehlgeschlagen | Experimenteller Mermaid-Diagrammtyp; vermutlich fehlende D3-Layout-Funktionen im Shim |

### Bekannte Root-Causes für fehlende Typen

1. **Browser-Shim-Lücken**: Komplexere Diagrammtypen benötigen DOM-APIs, die der
   Shim noch nicht implementiert (z. B. `<canvas>`, `getComputedTextLength()`,
   erweiterte CSS-Selektoren).
2. **`tspan`-Positionierung**: SVG-Textpositionierung mit `em`-Einheiten und
   absoluten `y`-Koordinaten auf `tspan` wird nicht browsernah genug modelliert.
3. **`transform`-Parsing**: Der Shim unterstützt nur `translate(x,y)`, aber nicht
   `matrix()`, `scale()`, `rotate()` oder kombinierte Transformationen.
4. **Container-Größen**: Hartcodierte Fallbacks (800×600, 1600×900) führen bei
   manchen Diagrammen zu inkonsistenten Layout-Berechnungen.
