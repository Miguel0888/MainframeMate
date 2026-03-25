package de.bund.zrb.mermaid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic test running on the APP classpath to isolate classpath-related GraalJS hangs.
 */
class GraalJsAppClasspathDiagnostic {

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void step1_rendererAvailable() {
        System.err.println("[AppDiag] Step 1: Getting renderer...");
        long t0 = System.currentTimeMillis();
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        System.err.println("[AppDiag] Renderer available: " + renderer.isAvailable()
                + " in " + (System.currentTimeMillis() - t0) + " ms");
        assertTrue(renderer.isAvailable(), "Renderer should be available");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void step2_simpleRender() {
        System.err.println("[AppDiag] Step 2: Simple flowchart render...");
        long t0 = System.currentTimeMillis();
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        String svg = renderer.renderToSvg("graph TD; A-->B;");
        long elapsed = System.currentTimeMillis() - t0;
        System.err.println("[AppDiag] Render completed in " + elapsed + " ms");
        System.err.println("[AppDiag] SVG length: " + (svg != null ? svg.length() : "null"));
        if (svg != null) {
            System.err.println("[AppDiag] SVG starts with: " + svg.substring(0, Math.min(200, svg.length())));
        }
        assertNotNull(svg, "SVG should not be null");
        assertTrue(svg.contains("<svg"), "Should contain <svg>");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void step3_pizzaRender() {
        System.err.println("[AppDiag] Step 3: Pizzabestellung render...");
        long t0 = System.currentTimeMillis();
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        String pizzaCode = "graph TD\n"
                + "    Hunger([Hunger]) -->|Check| Geld{Geld?}\n"
                + "    Geld -->|Nein| Nudeln[Nudeln kochen]\n"
                + "    Geld -->|Ja| Pizza{Lust auf Pizza?}\n"
                + "    Pizza -->|Ja| Mario([Mario anrufen])\n"
                + "    Pizza -->|Nein| Sushi[Sushi bestellen]\n"
                + "    Mario --> Satt([Satt])\n"
                + "    Sushi --> Satt\n"
                + "    Nudeln --> Satt";
        String svg = renderer.renderToSvg(pizzaCode);
        long elapsed = System.currentTimeMillis() - t0;
        System.err.println("[AppDiag] Pizza render completed in " + elapsed + " ms");
        System.err.println("[AppDiag] SVG length: " + (svg != null ? svg.length() : "null"));
        assertNotNull(svg, "Pizza SVG should not be null");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void step4_sequenceRender() {
        System.err.println("[AppDiag] Step 4: Sequence diagram render...");
        long t0 = System.currentTimeMillis();
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        String seqCode = "sequenceDiagram\n"
                + "    participant Chef\n"
                + "    participant Alice\n"
                + "    participant Bob\n"
                + "    Chef->>Alice: Agenda\n"
                + "    Chef->>Bob: Agenda\n"
                + "    loop Diskussion\n"
                + "        Alice->>Bob: Vorschlag\n"
                + "        Bob-->>Alice: Feedback\n"
                + "    end\n"
                + "    Note right of Bob: Deadline Fr\n"
                + "    Alice->>Chef: Ergebnis";
        String svg = renderer.renderToSvg(seqCode);
        long elapsed = System.currentTimeMillis() - t0;
        System.err.println("[AppDiag] Sequence render completed in " + elapsed + " ms");
        System.err.println("[AppDiag] SVG length: " + (svg != null ? svg.length() : "null"));
        assertNotNull(svg, "Sequence SVG should not be null");
    }
}
