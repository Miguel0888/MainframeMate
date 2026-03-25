package com.aresstack.mermaid.editor;

import com.aresstack.mermaid.layout.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests specifically for ER diagram editing through the {@link SourceEditBridge}.
 * Reproduces the bugs reported: nothing works for ER (reverse, delete, rename, add edge).
 */
class SourceEditBridgeErTest {

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

    // ── Step 1: Verify ANTLR parser finds the ER edges ──

    @Test
    @DisplayName("ER: ANTLR parser finds 2 relationships in TC5 source")
    void parser_findsRelationships() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(ER_SOURCE);
        assertNotNull(editor, "Editor should be created for ER source");
        assertEquals("erDiagram", editor.getDiagramType());

        List<MermaidSourceEditor.EdgeInfo> edges = editor.getEdges();
        System.out.println("Found " + edges.size() + " edges:");
        for (MermaidSourceEditor.EdgeInfo e : edges) {
            System.out.println("  " + e);
        }
        assertEquals(2, edges.size(), "Should find 2 ER relationships");
    }

    // ── Step 2: Verify findEdgeRobust with SVG-style IDs ──

    @Test
    @DisplayName("ER: findEdgeRobust finds edge with raw IDs (AUTOR, BUCH)")
    void findEdgeRobust_rawIds() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(ER_SOURCE);
        MermaidSourceEditor.EdgeInfo ei = SourceEditBridge.findEdgeRobust(editor, "AUTOR", "BUCH");
        assertNotNull(ei, "Should find edge AUTOR->BUCH with raw IDs");
        assertEquals("AUTOR", ei.sourceId);
        assertEquals("BUCH", ei.targetId);
    }

    @Test
    @DisplayName("ER: findEdgeRobust finds edge with entity-prefixed IDs")
    void findEdgeRobust_entityPrefixedIds() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(ER_SOURCE);
        // SVG IDs might be like "entity-AUTOR-1"
        MermaidSourceEditor.EdgeInfo ei = SourceEditBridge.findEdgeRobust(editor, "entity-AUTOR-1", "entity-BUCH-0");
        assertNotNull(ei, "Should find edge with entity-prefixed IDs (after stripping)");
    }

    @Test
    @DisplayName("ER: findEdgeRobust works with reversed order from SVG")
    void findEdgeRobust_reversed() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(ER_SOURCE);
        // SVG might give us BUCH -> AUTOR (reversed from source)
        MermaidSourceEditor.EdgeInfo ei = SourceEditBridge.findEdgeRobust(editor, "BUCH", "AUTOR");
        assertNotNull(ei, "Should find edge even if source/target are swapped from SVG");
    }

    // ── Step 3: Test actual edit operations through SourceEditBridge ──

    @Test
    @DisplayName("ER: Reverse edge through SourceEditBridge")
    void reverseEdge_throughBridge() {
        // Simulate what the UI does: create an ErRelationship as the SVG extractor would
        ErRelationship edge = new ErRelationship(
                "AUTOR->BUCH", "AUTOR", "BUCH", "schreibt",
                "", 0, 0, 100, 50,
                ErCardinality.EXACTLY_ONE, ErCardinality.ZERO_OR_MORE, false);

        String result = SourceEditBridge.reverseEdge(ER_SOURCE, "erDiagram", edge);
        System.out.println("=== After reverse ===");
        System.out.println(result);
        assertNotEquals(ER_SOURCE, result, "Source should have changed");
        assertTrue(result.contains("BUCH"), "BUCH should still be present");
        assertTrue(result.contains("AUTOR"), "AUTOR should still be present");
    }

    @Test
    @DisplayName("ER: Delete edge through SourceEditBridge")
    void deleteEdge_throughBridge() {
        ErRelationship edge = new ErRelationship(
                "AUTOR->BUCH", "AUTOR", "BUCH", "schreibt",
                "", 0, 0, 100, 50,
                ErCardinality.EXACTLY_ONE, ErCardinality.ZERO_OR_MORE, false);

        String result = SourceEditBridge.deleteEdge(ER_SOURCE, "erDiagram", edge);
        System.out.println("=== After delete ===");
        System.out.println(result);
        assertNotEquals(ER_SOURCE, result, "Source should have changed");
        assertFalse(result.contains("schreibt"), "Deleted edge label should be gone");
        assertTrue(result.contains("verlegt"), "Other edge should remain");
    }

    @Test
    @DisplayName("ER: Change label through SourceEditBridge")
    void changeLabel_throughBridge() {
        ErRelationship edge = new ErRelationship(
                "AUTOR->BUCH", "AUTOR", "BUCH", "schreibt",
                "", 0, 0, 100, 50,
                ErCardinality.EXACTLY_ONE, ErCardinality.ZERO_OR_MORE, false);

        String result = SourceEditBridge.changeEdgeLabel(ER_SOURCE, "erDiagram", edge, "verfasst");
        System.out.println("=== After label change ===");
        System.out.println(result);
        assertNotEquals(ER_SOURCE, result, "Source should have changed");
        assertTrue(result.contains("verfasst"), "New label should be present");
        assertFalse(result.contains("schreibt"), "Old label should be gone");
    }

    @Test
    @DisplayName("ER: Rename entity through SourceEditBridge")
    void renameNode_throughBridge() {
        String result = SourceEditBridge.renameNode(ER_SOURCE, "erDiagram", "AUTOR", "AUTOR", "SCHRIFTSTELLER");
        System.out.println("=== After rename ===");
        System.out.println(result);
        assertNotEquals(ER_SOURCE, result, "Source should have changed");
        assertTrue(result.contains("SCHRIFTSTELLER"), "New name should be present");
        assertFalse(result.contains("AUTOR"), "Old name should be gone");
    }

    // ── Step 4: Test addEdge (drag-and-drop) ──

    @Test
    @DisplayName("ER: addEdge creates valid Mermaid ER syntax with label")
    void addEdge_createsValidSyntax() {
        String result = SourceEditBridge.addEdge(ER_SOURCE, "erDiagram", "AUTOR", "VERLAG");
        System.out.println("=== After addEdge ===");
        System.out.println(result);
        assertNotEquals(ER_SOURCE, result, "Source should have changed");
        // ER diagrams REQUIRE a ": label" suffix!
        assertTrue(result.contains("AUTOR") && result.contains("VERLAG"),
                "New edge should reference both entities");
        assertTrue(result.contains(":"), "ER edge must have a label (: part)");
    }

    @Test
    @DisplayName("ER: addEdge result is valid Mermaid (can be re-parsed)")
    void addEdge_resultIsReparseable() {
        String result = SourceEditBridge.addEdge(ER_SOURCE, "erDiagram", "AUTOR", "VERLAG");
        // Try to re-parse the result
        MermaidSourceEditor editor = MermaidSourceEditor.parse(result);
        assertNotNull(editor, "Result should be parseable");
        assertTrue(editor.getEdges().size() >= 3, "Should now have 3 edges");
    }

    // ── Step 5: Test changeErCardinality ──

    @Test
    @DisplayName("ER: changeErCardinality with empty label preserves ANTLR label")
    void changeCardinality_emptyLabelFallsBackToAntlr() {
        // This is the exact bug scenario: SVG extraction yields empty label,
        // so er.getLabel() returns "". The method must still produce ": label".
        String result = SourceEditBridge.changeErCardinality(ER_SOURCE,
                "AUTOR", "BUCH",
                ErCardinality.ONE_OR_MORE, ErCardinality.ZERO_OR_MORE,
                false, "");  // empty label — simulating broken SVG extraction
        System.out.println("=== After cardinality change (empty label) ===");
        System.out.println(result);
        // Must contain ": schreibt" (from ANTLR) — NOT a bare "AUTOR ...--... BUCH" line!
        assertTrue(result.contains(": schreibt"), "ANTLR label 'schreibt' must be preserved");
        // Must be parseable
        MermaidSourceEditor editor = MermaidSourceEditor.parse(result);
        assertNotNull(editor, "Result must be parseable Mermaid");
        assertEquals(2, editor.getEdges().size(), "Must still have 2 edges");
    }

    @Test
    @DisplayName("ER: changeErCardinality with null label preserves ANTLR label")
    void changeCardinality_nullLabelFallsBackToAntlr() {
        String result = SourceEditBridge.changeErCardinality(ER_SOURCE,
                "VERLAG", "BUCH",
                ErCardinality.EXACTLY_ONE, ErCardinality.ONE_OR_MORE,
                false, null);  // null label
        System.out.println("=== After cardinality change (null label) ===");
        System.out.println(result);
        assertTrue(result.contains(": verlegt"), "ANTLR label 'verlegt' must be preserved");
        MermaidSourceEditor editor = MermaidSourceEditor.parse(result);
        assertNotNull(editor, "Result must be parseable Mermaid");
    }

    @Test
    @DisplayName("ER: changeErCardinality with provided label uses it")
    void changeCardinality_withExplicitLabel() {
        String result = SourceEditBridge.changeErCardinality(ER_SOURCE,
                "AUTOR", "BUCH",
                ErCardinality.ONE_OR_MORE, ErCardinality.ONE_OR_MORE,
                false, "verfasst");
        System.out.println("=== After cardinality change (explicit label) ===");
        System.out.println(result);
        assertTrue(result.contains(": verfasst"), "Explicit label should be used");
        assertFalse(result.contains(": schreibt"), "Old label should be replaced");
    }

    @Test
    @DisplayName("ER: changeErCardinality result matches user's exact error scenario")
    void changeCardinality_userErrorScenario() {
        // Simulates the exact flow: user changes VERLAG relationship cardinality
        // with empty SVG label → must NOT produce "VERLAG }|--|| BUCH" without label
        String result = SourceEditBridge.changeErCardinality(ER_SOURCE,
                "VERLAG", "BUCH",
                ErCardinality.ONE_OR_MORE, ErCardinality.EXACTLY_ONE,
                false, "");
        System.out.println("=== User error scenario ===");
        System.out.println(result);
        // The line must NOT end with just "BUCH" — it must have ": verlegt" or ": relates"
        assertFalse(result.contains("BUCH\n") || result.trim().endsWith("BUCH"),
                "ER line must not end without a label");
        // Must re-parse without error
        MermaidSourceEditor editor = MermaidSourceEditor.parse(result);
        assertNotNull(editor, "Must be valid Mermaid");
        assertEquals(2, editor.getEdges().size());
    }

    // ── Step 6: Test stripSvgPrefix for entity IDs ──

    @Test
    @DisplayName("stripSvgPrefix correctly handles entity-prefixed IDs")
    void stripSvgPrefix_entityIds() {
        assertEquals("AUTOR", SourceEditBridge.stripSvgPrefix("entity-AUTOR-1"));
        assertEquals("BUCH", SourceEditBridge.stripSvgPrefix("entity-BUCH-0"));
        assertEquals("VERLAG", SourceEditBridge.stripSvgPrefix("entity-VERLAG-2"));
        assertEquals("AUTOR", SourceEditBridge.stripSvgPrefix("AUTOR")); // no prefix
    }
}

