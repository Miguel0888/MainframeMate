package com.aresstack.mermaid.editor;

import com.aresstack.mermaid.layout.ErCardinality;
import de.bund.zrb.mermaid.MermaidRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduce the bug: changing ER cardinality causes an SVG error.
 */
class ErCardinalityChangeTest {

    static final String ER_SOURCE =
            "erDiagram\n"
            + "    BUCH {\n"
            + "        string isbn PK\n"
            + "        string titel\n"
            + "        int jahr\n"
            + "    }\n"
            + "    AUTOR {\n"
            + "        int id PK\n"
            + "        string name\n"
            + "    }\n"
            + "    VERLAG {\n"
            + "        int id PK\n"
            + "        string name\n"
            + "    }\n"
            + "    AUTOR ||--o{ BUCH : schreibt\n"
            + "    VERLAG ||--o{ BUCH : verlegt";

    @Test
    @DisplayName("changeErCardinality: all combinations produce valid source (unit test)")
    void allCombinations_produceValidSource() {
        ErCardinality[] allCards = ErCardinality.values();
        int failures = 0;

        for (ErCardinality srcCard : allCards) {
            for (ErCardinality tgtCard : allCards) {
                String modified = SourceEditBridge.changeErCardinality(
                        ER_SOURCE,
                        "AUTOR", "BUCH",
                        srcCard, tgtCard,
                        false, "schreibt");

                System.out.println("=== " + srcCard + " / " + tgtCard + " ===");
                // Extract just the relationship line
                for (String line : modified.split("\n")) {
                    if (line.contains("AUTOR") && line.contains("BUCH") && line.contains("schreibt")) {
                        System.out.println("  " + line.trim());
                    }
                }

                // Verify re-parseable by ANTLR
                MermaidSourceEditor editor = MermaidSourceEditor.parse(modified);
                assertNotNull(editor, "Should re-parse for " + srcCard + "/" + tgtCard);

                MermaidSourceEditor.EdgeInfo ei = editor.findEdge("AUTOR", "BUCH");
                if (ei == null) {
                    System.err.println("  FAIL: Edge AUTOR->BUCH not found after change to " + srcCard + "/" + tgtCard);
                    System.err.println("  Full source:\n" + modified);
                    failures++;
                } else {
                    System.out.println("  OK: arrow=" + ei.arrowText + " label=" + ei.label);
                }
            }
        }
        assertEquals(0, failures, failures + " cardinality combinations failed re-parsing");
    }

    @Test
    @DisplayName("changeErCardinality: all combinations render valid SVG")
    void allCombinations_renderValidSvg() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        ErCardinality[] allCards = ErCardinality.values();
        int failures = 0;

        for (ErCardinality srcCard : allCards) {
            for (ErCardinality tgtCard : allCards) {
                String modified = SourceEditBridge.changeErCardinality(
                        ER_SOURCE,
                        "AUTOR", "BUCH",
                        srcCard, tgtCard,
                        false, "schreibt");

                String svg = renderer.renderToSvg(modified);
                boolean ok = svg != null && svg.contains("<svg");
                System.out.println(srcCard + " / " + tgtCard + " → " + (ok ? "OK" : "FAIL"));
                if (!ok) {
                    // Print the relationship line for debugging
                    for (String line : modified.split("\n")) {
                        if (line.contains("AUTOR") && line.contains("BUCH")) {
                            System.err.println("  Failing line: " + line.trim());
                        }
                    }
                    System.err.println("  Full source:\n" + modified);
                    failures++;
                }
            }
        }
        assertEquals(0, failures, failures + " cardinality combinations produced SVG errors");
    }

    @Test
    @DisplayName("changeErCardinality: EXACTLY_ONE/EXACTLY_ONE must produce || on both sides")
    void exactlyOne_bothSides_mustHavePipes() {
        String modified = SourceEditBridge.changeErCardinality(
                ER_SOURCE,
                "AUTOR", "BUCH",
                ErCardinality.EXACTLY_ONE, ErCardinality.EXACTLY_ONE,
                false, "schreibt");

        System.out.println("=== Full modified source ===");
        System.out.println(modified);
        System.out.println("=== End ===");
        System.out.println("Contains '||--||': " + modified.contains("||--||"));
        System.out.println("Contains '||': " + modified.contains("||"));

        // Find the AUTOR...BUCH line and print char-by-char
        for (String line : modified.split("\n")) {
            if (line.contains("AUTOR") && line.contains("BUCH") && line.contains("schreibt")) {
                System.out.println("Line length: " + line.length());
                System.out.print("Chars: ");
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    System.out.print("[" + (int) c + ":" + c + "]");
                }
                System.out.println();
            }
        }

        assertTrue(modified.contains("||--||"),
                "EXACTLY_ONE on both sides must produce ||--|| in the source. Got:\n" + modified);
    }

    @Test
    @DisplayName("changeErCardinality: change to ONE_OR_MORE on both sides")
    void change_oneOrMore_bothSides() {
        String modified = SourceEditBridge.changeErCardinality(
                ER_SOURCE,
                "AUTOR", "BUCH",
                ErCardinality.ONE_OR_MORE, ErCardinality.ONE_OR_MORE,
                false, "schreibt");

        System.out.println("Modified source:\n" + modified);
        assertTrue(modified.contains("}|--|{"), "Should have }| (one-or-more) on left and |{ (one-or-more) on right");
    }

    @Test
    @DisplayName("changeErCardinality: with identifying connector")
    void change_withIdentifying() {
        String modified = SourceEditBridge.changeErCardinality(
                ER_SOURCE,
                "AUTOR", "BUCH",
                ErCardinality.EXACTLY_ONE, ErCardinality.ZERO_OR_MORE,
                true, "schreibt");

        System.out.println("Modified source (identifying):\n" + modified);
        // Check what connector is used
        for (String line : modified.split("\n")) {
            if (line.contains("AUTOR") && line.contains("BUCH") && line.contains("schreibt")) {
                System.out.println("  Rel line chars: ");
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == '|' || c == '-' || c == '=' || c == '.' || c == '{' || c == '}' || c == 'o') {
                        System.out.print("[" + c + "]");
                    }
                }
                System.out.println();
            }
        }

        // Try to render — this WILL fail if == is not valid Mermaid
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        String svg = renderer.renderToSvg(modified);
        boolean ok = svg != null && svg.contains("<svg");
        System.out.println("Render with identifying=true: " + (ok ? "OK" : "FAIL"));
        assertTrue(ok, "Identifying relationship should also render (connector must be valid Mermaid).\nSource:\n" + modified);
    }
}

