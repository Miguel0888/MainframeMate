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

## Architektur

| Klasse | Zweck |
|--------|-------|
| `MermaidRenderer` | Singleton-Fassade — `renderToSvg(diagramCode)` |
| `GraalJsExecutor` | Interner GraalJS Polyglot-Context Wrapper |
| `JsExecutionResult` | Immutables Ergebnisobjekt (success/failure) |
| `MermaidRendererMain` | Standalone-Main zum manuellen Testen |

### Ressourcen

| Datei | Zweck |
|-------|-------|
| `browser-shim.js` | Minimales Browser-Umfeld für GraalJS (DOM, CSS, Selektoren) |
| `mermaid.min.js` | Mermaid 9.4.3 UMD-Bundle (extern, nicht eingecheckt) |

### Integration in app

    implementation project(':mermaid-renderer')

    MermaidRenderer renderer = MermaidRenderer.getInstance();
    String svg = renderer.renderToSvg("graph TD; A-->B;");
