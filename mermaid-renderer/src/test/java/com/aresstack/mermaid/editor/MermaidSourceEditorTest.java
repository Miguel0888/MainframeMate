package com.aresstack.mermaid.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MermaidSourceEditor} — verifies that the ANTLR-based
 * editor correctly parses and round-trip modifies Mermaid source code.
 *
 * These tests reproduce the exact bugs that the regex-based approach had:
 * - TC2: Changing the first edge changed the last edge
 * - TC6: Deleting edges deleted nodes instead
 * - TC6: Edge reversal didn't work for state diagrams
 */
class MermaidSourceEditorTest {

    // ═══════════════════════════════════════════════════════════
    //  Flowchart tests (TC1 + TC2)
    // ═══════════════════════════════════════════════════════════

    static final String FLOWCHART_TC2 =
            "graph LR\n"
            + "    Alpha --> Delta\n"
            + "    Beta -.->|dashed| Echo\n"
            + "    Gamma ==>|thick| Foxtrot";

    @Test
    @DisplayName("Flowchart: Parser extracts all 3 edges correctly")
    void flowchart_extractsEdges() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(FLOWCHART_TC2);
        assertNotNull(editor, "Editor should be created");
        assertEquals("flowchart", editor.getDiagramType());

        List<MermaidSourceEditor.EdgeInfo> edges = editor.getEdges();
        assertEquals(3, edges.size(), "Should find 3 edges");

        assertEquals("Alpha", edges.get(0).sourceId);
        assertEquals("Delta", edges.get(0).targetId);
        assertEquals("-->", edges.get(0).arrowText);
        assertEquals("", edges.get(0).label);

        assertEquals("Beta", edges.get(1).sourceId);
        assertEquals("Echo", edges.get(1).targetId);
        assertEquals("-.->", edges.get(1).arrowText);
        assertEquals("dashed", edges.get(1).label);

        assertEquals("Gamma", edges.get(2).sourceId);
        assertEquals("Foxtrot", edges.get(2).targetId);
        assertEquals("==>", edges.get(2).arrowText);
        assertEquals("thick", edges.get(2).label);
    }

    @Test
    @DisplayName("BUG FIX TC2: Changing first edge arrow ONLY changes the first edge")
    void flowchart_changeFirstEdgeArrow_onlyChangesFirst() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(FLOWCHART_TC2);
        MermaidSourceEditor.EdgeInfo firstEdge = editor.findEdge("Alpha", "Delta");
        assertNotNull(firstEdge, "Should find Alpha->Delta edge");

        editor.replaceArrow(firstEdge, "--x");
        String result = editor.getText();

        assertTrue(result.contains("Alpha --x Delta"), "First edge should have --x arrow");
        assertTrue(result.contains("Beta -.->"), "Second edge should be unchanged");
        assertTrue(result.contains("Gamma ==>"), "Third edge should be unchanged");
    }

    @Test
    @DisplayName("Flowchart: Reverse edge swaps source ↔ target precisely")
    void flowchart_reverseEdge() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(FLOWCHART_TC2);
        MermaidSourceEditor.EdgeInfo edge = editor.findEdge("Alpha", "Delta");
        editor.reverseEdge(edge);
        String result = editor.getText();

        assertTrue(result.contains("Delta --> Alpha"), "Source and target should be swapped");
        assertTrue(result.contains("Beta -.->"), "Other edges unchanged");
    }

    @Test
    @DisplayName("Flowchart: Delete edge removes only the target line")
    void flowchart_deleteEdge() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(FLOWCHART_TC2);
        MermaidSourceEditor.EdgeInfo edge = editor.findEdge("Beta", "Echo");
        editor.deleteEdge(edge);
        String result = editor.getText();

        assertTrue(result.contains("Alpha --> Delta"), "First edge should remain");
        assertFalse(result.contains("Beta"), "Second edge line should be removed");
        assertFalse(result.contains("Echo"), "Second edge line should be removed");
        assertTrue(result.contains("Gamma ==>"), "Third edge should remain");
    }

    @Test
    @DisplayName("Flowchart: Replace edge label")
    void flowchart_replaceLabel() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(FLOWCHART_TC2);
        MermaidSourceEditor.EdgeInfo edge = editor.findEdge("Beta", "Echo");
        editor.replaceLabel(edge, "gestrichelt");
        String result = editor.getText();

        assertTrue(result.contains("|gestrichelt|"), "Label should be updated");
        assertFalse(result.contains("|dashed|"), "Old label should be gone");
    }

    @Test
    @DisplayName("Flowchart: Round-trip preserves formatting")
    void flowchart_roundTrip_preservesFormatting() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(FLOWCHART_TC2);
        String result = editor.getText();
        assertEquals(FLOWCHART_TC2, result, "Unmodified source should be identical");
    }

    // ── Flowchart with shapes (TC1) ──

    static final String FLOWCHART_TC1 =
            "graph LR\n"
            + "    Rechteck[Rechteck] --> Rund([Rund])\n"
            + "    Rund --> Kreis((Kreis))\n"
            + "    Kreis --> Raute{Raute}\n"
            + "    Raute --> Sechseck{{Sechseck}}\n"
            + "    Sechseck --> Trapez[/Trapez/]";

    @Test
    @DisplayName("Flowchart TC1: Parses edges with shapes")
    void flowchart_tc1_parsesEdgesWithShapes() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(FLOWCHART_TC1);
        assertNotNull(editor);

        List<MermaidSourceEditor.EdgeInfo> edges = editor.getEdges();
        assertEquals(5, edges.size(), "Should find 5 edges");

        assertEquals("Rechteck", edges.get(0).sourceId);
        assertEquals("Rund", edges.get(0).targetId);

        assertEquals("Rund", edges.get(1).sourceId);
        assertEquals("Kreis", edges.get(1).targetId);

        assertEquals("Kreis", edges.get(2).sourceId);
        assertEquals("Raute", edges.get(2).targetId);

        assertEquals("Raute", edges.get(3).sourceId);
        assertEquals("Sechseck", edges.get(3).targetId);

        assertEquals("Sechseck", edges.get(4).sourceId);
        assertEquals("Trapez", edges.get(4).targetId);
    }

    @Test
    @DisplayName("BUG FIX TC1: Rename node updates all references precisely")
    void flowchart_tc1_renameNode() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(FLOWCHART_TC1);
        editor.renameNode("Rechteck", "Box");
        String result = editor.getText();

        // Both the shape definition and edge references should be renamed
        assertTrue(result.contains("Box"), "New name should appear");
        assertFalse(result.contains("Rechteck"), "Old name should be gone everywhere");
        // Other nodes should be untouched
        assertTrue(result.contains("Rund"), "Other nodes unchanged");
    }

    // ═══════════════════════════════════════════════════════════
    //  State diagram tests (TC6)
    // ═══════════════════════════════════════════════════════════

    static final String STATE_TC6 =
            "stateDiagram-v2\n"
            + "    [*] --> Rot\n"
            + "    Rot --> Rot_Gelb : warten\n"
            + "    Rot_Gelb --> Gruen\n"
            + "    Gruen --> Gelb\n"
            + "    Gelb --> Rot";

    @Test
    @DisplayName("State: Parser extracts all 5 transitions")
    void state_extractsTransitions() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(STATE_TC6);
        assertNotNull(editor);
        assertEquals("stateDiagram", editor.getDiagramType());

        List<MermaidSourceEditor.EdgeInfo> edges = editor.getEdges();
        assertEquals(5, edges.size());

        assertEquals("[*]", edges.get(0).sourceId);
        assertEquals("Rot", edges.get(0).targetId);

        assertEquals("Rot", edges.get(1).sourceId);
        assertEquals("Rot_Gelb", edges.get(1).targetId);
        assertEquals("warten", edges.get(1).label);

        assertEquals("Rot_Gelb", edges.get(2).sourceId);
        assertEquals("Gruen", edges.get(2).targetId);
    }

    @Test
    @DisplayName("BUG FIX TC6: Delete edge does NOT delete nodes")
    void state_deleteEdge_doesNotDeleteNodes() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(STATE_TC6);
        MermaidSourceEditor.EdgeInfo edge = editor.findEdge("Rot", "Rot_Gelb");
        assertNotNull(edge, "Should find Rot->Rot_Gelb transition");

        editor.deleteEdge(edge);
        String result = editor.getText();

        // The transition line should be removed
        assertFalse(result.contains("warten"), "Transition label 'warten' should be removed");
        // But other transitions referencing these states should remain!
        assertTrue(result.contains("[*] --> Rot"), "Transition to Rot should remain");
        assertTrue(result.contains("Rot_Gelb --> Gruen"), "Transition from Rot_Gelb should remain");
    }

    @Test
    @DisplayName("BUG FIX TC6: Reverse edge works for state transitions")
    void state_reverseEdge() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(STATE_TC6);
        MermaidSourceEditor.EdgeInfo edge = editor.findEdge("Gruen", "Gelb");
        assertNotNull(edge);

        editor.reverseEdge(edge);
        String result = editor.getText();

        assertTrue(result.contains("Gelb --> Gruen"), "Direction should be reversed");
        // Other transitions unchanged
        assertTrue(result.contains("[*] --> Rot"), "Other transitions unchanged");
    }

    @Test
    @DisplayName("State: Reverse edge with label preserves the label")
    void state_reverseEdge_preservesLabel() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(STATE_TC6);
        MermaidSourceEditor.EdgeInfo edge = editor.findEdge("Rot", "Rot_Gelb");
        assertNotNull(edge);

        editor.reverseEdge(edge);
        String result = editor.getText();

        assertTrue(result.contains("Rot_Gelb --> Rot : warten"),
                "Direction reversed, label preserved");
    }

    // ═══════════════════════════════════════════════════════════
    //  ER diagram tests (TC5)
    // ═══════════════════════════════════════════════════════════

    static final String ER_TC5 =
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
    @DisplayName("ER: Parser extracts relationships")
    void er_extractsRelationships() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(ER_TC5);
        assertNotNull(editor);
        assertEquals("erDiagram", editor.getDiagramType());

        List<MermaidSourceEditor.EdgeInfo> edges = editor.getEdges();
        assertEquals(2, edges.size());

        assertEquals("AUTOR", edges.get(0).sourceId);
        assertEquals("BUCH", edges.get(0).targetId);
        assertEquals("schreibt", edges.get(0).label);

        assertEquals("VERLAG", edges.get(1).sourceId);
        assertEquals("BUCH", edges.get(1).targetId);
        assertEquals("verlegt", edges.get(1).label);
    }

    @Test
    @DisplayName("ER: Reverse relationship swaps entities AND mirrors cardinalities")
    void er_reverseRelationship() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(ER_TC5);
        MermaidSourceEditor.EdgeInfo edge = editor.findEdge("AUTOR", "BUCH");
        assertNotNull(edge);

        editor.reverseEdge(edge);
        String result = editor.getText();

        assertTrue(result.contains("BUCH") && result.contains("AUTOR"),
                "Both entity names should still be present");
        // After reversal: BUCH }o--|| AUTOR : schreibt
        assertTrue(result.contains("BUCH }o--|| AUTOR"),
                "Cardinalities should be mirrored: ||--o{ → }o--||");
    }

    @Test
    @DisplayName("ER: mirrorErArrow correctly mirrors cardinality arrows")
    void er_mirrorArrow() {
        assertEquals("}o--||", MermaidSourceEditor.mirrorErArrow("||--o{"));
        assertEquals("||--o{", MermaidSourceEditor.mirrorErArrow("}o--||"));
        assertEquals("}|..||", MermaidSourceEditor.mirrorErArrow("||..|{"));
        assertEquals("||--||", MermaidSourceEditor.mirrorErArrow("||--||"));
        assertEquals("|o--|{", MermaidSourceEditor.mirrorErArrow("}|--o|"));
    }

    // ═══════════════════════════════════════════════════════════
    //  Sequence diagram tests (TC4)
    // ═══════════════════════════════════════════════════════════

    static final String SEQ_TC4 =
            "sequenceDiagram\n"
            + "    participant Chef\n"
            + "    participant Alice\n"
            + "    participant Bob\n"
            + "    Chef->>Alice: Agenda\n"
            + "    Chef->>Bob: Agenda\n"
            + "    loop Diskussion\n"
            + "        Alice->>Bob: Vorschlag\n"
            + "        Bob-->>Alice: Feedback\n"
            + "    end\n"
            + "    Alice->>Chef: Ergebnis";

    @Test
    @DisplayName("Sequence: Parser extracts messages")
    void sequence_extractsMessages() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(SEQ_TC4);
        assertNotNull(editor);
        assertEquals("sequence", editor.getDiagramType());

        List<MermaidSourceEditor.EdgeInfo> edges = editor.getEdges();
        assertEquals(5, edges.size());

        assertEquals("Chef", edges.get(0).sourceId);
        assertEquals("Alice", edges.get(0).targetId);
        assertEquals("Agenda", edges.get(0).label);
        assertEquals("->>", edges.get(0).arrowText);

        assertEquals("Bob", edges.get(3).sourceId);
        assertEquals("Alice", edges.get(3).targetId);
        assertEquals("Feedback", edges.get(3).label);
        assertEquals("-->>", edges.get(3).arrowText);
    }

    @Test
    @DisplayName("Sequence: Reverse message swaps actors")
    void sequence_reverseMessage() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(SEQ_TC4);
        MermaidSourceEditor.EdgeInfo edge = editor.findEdge("Chef", "Alice");
        assertNotNull(edge);

        editor.reverseEdge(edge);
        String result = editor.getText();

        assertTrue(result.contains("Alice->>Chef: Agenda"),
                "Message direction should be reversed");
    }

    @Test
    @DisplayName("Sequence: Change arrow type")
    void sequence_changeArrowType() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(SEQ_TC4);
        MermaidSourceEditor.EdgeInfo edge = editor.findEdge("Chef", "Alice");
        assertNotNull(edge);

        editor.replaceArrow(edge, "-->>");
        String result = editor.getText();

        assertTrue(result.contains("Chef-->>Alice: Agenda"),
                "Arrow should be changed to dashed");
    }

    // ═══════════════════════════════════════════════════════════
    //  Class diagram tests (TC3)
    // ═══════════════════════════════════════════════════════════

    static final String CLASS_TC3 =
            "classDiagram\n"
            + "    class Animal {\n"
            + "        <<abstract>>\n"
            + "        +String name\n"
            + "        +int age\n"
            + "        +speak() void\n"
            + "    }\n"
            + "    class Dog {\n"
            + "        +fetch() void\n"
            + "    }\n"
            + "    class Cat {\n"
            + "        +purr() void\n"
            + "    }\n"
            + "    Animal <|-- Dog\n"
            + "    Animal <|-- Cat";

    @Test
    @DisplayName("Class: Parser extracts relations")
    void class_extractsRelations() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(CLASS_TC3);
        assertNotNull(editor);
        assertEquals("classDiagram", editor.getDiagramType());

        List<MermaidSourceEditor.EdgeInfo> edges = editor.getEdges();
        assertEquals(2, edges.size());

        assertEquals("Animal", edges.get(0).sourceId);
        assertEquals("Dog", edges.get(0).targetId);
        assertEquals("<|--", edges.get(0).arrowText);

        assertEquals("Animal", edges.get(1).sourceId);
        assertEquals("Cat", edges.get(1).targetId);
    }

    @Test
    @DisplayName("Class: Reverse inheritance relation")
    void class_reverseRelation() {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(CLASS_TC3);
        MermaidSourceEditor.EdgeInfo edge = editor.findEdge("Animal", "Dog");
        assertNotNull(edge);

        editor.reverseEdge(edge);
        String result = editor.getText();

        assertTrue(result.contains("Dog <|-- Animal"),
                "Inheritance direction should be reversed");
    }
}

