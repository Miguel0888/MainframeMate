package de.bund.zrb.mermaid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * Temporary debug test — renders diagrams and dumps getBBox logs.
 */
class BBoxDebugTest {

    private void renderAndDump(String label, String source) {
        GraalJsExecutor executor = new GraalJsExecutor();
        String shim = MermaidRenderer.loadResource("/mermaid/browser-shim.js");
        String mermaidBundle = MermaidRenderer.loadResource("/mermaid/mermaid.min.js");
        if (shim == null || mermaidBundle == null) {
            System.err.println("SKIP: mermaid resources not on classpath");
            return;
        }

        String script = shim + "\n" + mermaidBundle + "\n"
                + "var __mermaid = window.mermaid;\n"
                + "var __svgResult = '';\n"
                + "var __renderError = '';\n"
                + "__mermaid.initialize({ startOnLoad: false, securityLevel: 'loose', htmlLabels: false, flowchart: { htmlLabels: false }, sequence: { htmlLabels: false } });\n"
                + "var __container = document.createElement('div');\n"
                + "__container.id = 'dmmd-debug';\n"
                + "__container.setAttribute('id', 'dmmd-debug');\n"
                + "document.body.appendChild(__container);\n"
                + "try {\n"
                + "  var __result = __mermaid.render('mmd-debug', '" + source + "');\n"
                + "  if (__result && typeof __result.then === 'function') {\n"
                + "    __result.then(function(res) {\n"
                + "      __svgResult = (res && res.svg) ? res.svg : '';\n"
                + "    })['catch'](function(err) { __renderError = '' + err; });\n"
                + "  } else if (__result && __result.svg) { __svgResult = __result.svg; }\n"
                + "} catch(e) { __renderError = '' + e; }\n";

        String resultExpr = "JSON.stringify({viewBox: (function() { var m = (__svgResult||'').match(/viewBox=\\\"([^\\\"]*)\\\"/); return m ? m[1] : 'NONE'; })(), error: __renderError, bboxLogCount: (typeof __bboxLog !== 'undefined' ? __bboxLog.length : 0), svgBBox: (typeof __bboxLog !== 'undefined' ? __bboxLog.filter(function(l){return l.indexOf('svg#')===0;}) : [])})";
        JsExecutionResult result = executor.executeAsync(script, resultExpr);

        System.out.println("=== " + label + " ===");
        System.out.println("Success: " + result.isSuccessful());
        if (result.getErrorMessage() != null && !result.getErrorMessage().isEmpty()) {
            System.out.println("Error: " + result.getErrorMessage());
        }
        // Pretty-print the JSON output
        String out = result.getOutput();
        if (out != null && out.length() > 200) {
            // Print viewBox first
            int vbStart = out.indexOf("\"viewBox\":\"");
            if (vbStart >= 0) {
                int vbEnd = out.indexOf("\"", vbStart + 11);
                System.out.println("ViewBox: " + out.substring(vbStart + 11, vbEnd));
            }
            // Print each bbox log entry on its own line
            int logStart = out.indexOf("\"bboxLog\":[");
            if (logStart >= 0) {
                String logPart = out.substring(logStart + 11);
                String[] entries = logPart.split("\",\"");
                System.out.println("=== All " + entries.length + " getBBox calls ===");
                for (int i = 0; i < entries.length; i++) {
                    String e = entries[i].replace("[\"", "").replace("\"]}", "").replace("\"", "");
                    System.out.println("  " + (i+1) + ": " + e);
                }
            }
        } else {
            System.out.println("Output: " + out);
        }
    }

    @Test
    @DisplayName("Debug: Kanban getBBox")
    void debugKanbanBBox() {
        renderAndDump("Kanban", "kanban\\n"
                + "  Backlog\\n"
                + "    Aufgabe A\\n"
                + "    Aufgabe B\\n"
                + "    Aufgabe C\\n"
                + "  In Arbeit\\n"
                + "    Aufgabe D\\n"
                + "  Fertig\\n"
                + "    Aufgabe E\\n"
                + "    Aufgabe F\\n"
                + "    Aufgabe G");
    }

    @Test
    @DisplayName("Debug: Gantt getBBox")
    void debugGanttBBox() {
        renderAndDump("Gantt", "gantt\\n"
                + "  title Projekt\\n"
                + "  dateFormat YYYY-MM-DD\\n"
                + "  section Planung\\n"
                + "    Analyse :a1, 2025-01-01, 7d\\n"
                + "    Design :a2, after a1, 5d\\n"
                + "  section Umsetzung\\n"
                + "    Implementierung :a3, after a2, 14d\\n"
                + "    Test :a4, after a3, 7d");
    }

    @Test
    @DisplayName("Debug: Architecture getBBox")
    void debugArchitectureBBox() {
        renderAndDump("Architecture", "architecture-beta\\n"
                + "  group api[API]\\n"
                + "    service db(database)[DB]\\n"
                + "    service app(server)[App]\\n"
                + "    service web(internet)[Web]\\n"
                + "  db:R -- L:app\\n"
                + "  app:R -- L:web");
    }

    @Test
    @DisplayName("Debug: XY-Chart getBBox")
    void debugXyChartBBox() {
        renderAndDump("XY-Chart", "xychart-beta\\n"
                + "  title Wetter\\n"
                + "  x-axis [Jan, Feb, Mar, Apr]\\n"
                + "  y-axis \\\"Werte\\\" 0 --> 100\\n"
                + "  bar [50, 40, 55, 70]");
    }
}
