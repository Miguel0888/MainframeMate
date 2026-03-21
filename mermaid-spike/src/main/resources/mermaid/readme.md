Mermaid js converter has its own licence and comes from:

https://cdn.jsdelivr.net/npm/mermaid@9.4.3/dist/

Download URL:
https://cdn.jsdelivr.net/npm/mermaid@9.4.3/dist/mermaid.min.js

(Version 9.x empfohlen, da sie noch als UMD/IIFE-Bundle verfügbar ist, nicht ESM-only wie neuere Versionen)

Test:
```gradlew :mermaid-spike:run``` ausführen — die Stage-3-Ausgabe zeigt dir dann genau, woran Mermaid scheitert (Modulformat, fehlende DOM-APIs, SVG-Funktionen etc.), und ich kann den DOM-Shim gezielt erweitern.

Stages:

✅ Stage 1 – GraalJS läuft auf Java 8 sauber

✅ Stage 2 – Pseudo-Browser-Globals (window, document, navigator, console, setTimeout etc.) lassen sich installieren

✅ Stage 3 – JS-Bundles werden im Kontext mit DOM-Shim geladen (aktuell Platzhalter)

✅ Alle 6 Tests grün, Interpreter-Warnung unterdrückt