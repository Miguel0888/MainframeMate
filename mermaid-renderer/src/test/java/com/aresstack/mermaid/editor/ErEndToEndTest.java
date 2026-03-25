package com.aresstack.mermaid.editor;

import de.bund.zrb.mermaid.MermaidRenderer;
import com.aresstack.mermaid.layout.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test that simulates the full UI cycle for ER diagrams:
 * render → extract → modify → re-render.
 * This reproduces the exact flow that MermaidSelectionTest uses.
 */
class ErEndToEndTest {

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
    @DisplayName("E2E: Render ER → extract edges → verify sourceId/targetId")
    void renderAndExtract() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        String svg = renderer.renderToSvg(ER_SOURCE);
        assertNotNull(svg, "SVG should be rendered");
        assertTrue(svg.contains("<svg"), "Should contain SVG root");

        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram);
        assertEquals("erDiagram", diagram.getDiagramType());

        System.out.println("=== Nodes (" + diagram.getNodes().size() + ") ===");
        for (DiagramNode n : diagram.getNodes()) {
            System.out.println("  " + n.getClass().getSimpleName()
                    + " id=" + n.getId() + " label=" + n.getLabel()
                    + " kind=" + n.getKind()
                    + " svgId=" + n.getSvgId());
        }

        System.out.println("=== Edges (" + diagram.getEdges().size() + ") ===");
        for (DiagramEdge e : diagram.getEdges()) {
            System.out.println("  " + e.getClass().getSimpleName()
                    + " src=" + e.getSourceId() + " tgt=" + e.getTargetId()
                    + " label=" + e.getLabel()
                    + " kind=" + e.getKind());
            if (e instanceof ErRelationship) {
                ErRelationship er = (ErRelationship) e;
                System.out.println("    srcCard=" + er.getSourceCardinality()
                        + " tgtCard=" + er.getTargetCardinality()
                        + " identifying=" + er.isIdentifying());
            }
        }

        // Now check: do the extracted edge source/target IDs match ANTLR-parsed IDs?
        MermaidSourceEditor editor = MermaidSourceEditor.parse(ER_SOURCE);
        assertNotNull(editor);

        System.out.println("=== ANTLR Edges ===");
        for (MermaidSourceEditor.EdgeInfo ei : editor.getEdges()) {
            System.out.println("  " + ei);
        }

        // Try to find each SVG-extracted edge in the ANTLR model
        for (DiagramEdge svgEdge : diagram.getEdges()) {
            if (svgEdge instanceof ErRelationship) {
                String srcId = svgEdge.getSourceId();
                String tgtId = svgEdge.getTargetId();
                System.out.println("\nLooking for SVG edge: " + srcId + " -> " + tgtId);
                MermaidSourceEditor.EdgeInfo ei = SourceEditBridge.findEdgeRobust(editor, srcId, tgtId);
                System.out.println("  findEdgeRobust result: " + ei);
                assertNotNull(ei, "Should find ANTLR edge for SVG edge: " + srcId + " -> " + tgtId);
            }
        }
    }

    @Test
    @DisplayName("E2E: Reverse ER edge and re-render")
    void reverseAndRerender() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();

        // Step 1: Render and extract
        String svg = renderer.renderToSvg(ER_SOURCE);
        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        List<ErRelationship> rels = diagram.getEdgesOfType(ErRelationship.class);
        assertFalse(rels.isEmpty(), "Should have ER relationships");

        ErRelationship firstRel = rels.get(0);
        System.out.println("Reversing: " + firstRel.getSourceId() + " -> " + firstRel.getTargetId());

        // Step 2: Reverse through bridge
        String modified = SourceEditBridge.reverseEdge(ER_SOURCE, "erDiagram", firstRel);
        System.out.println("Modified source:\n" + modified);
        assertNotEquals(ER_SOURCE, modified, "Source should have changed");

        // Step 3: Re-render
        String svg2 = renderer.renderToSvg(modified);
        assertNotNull(svg2, "Modified source should render");
        assertTrue(svg2.contains("<svg"), "Re-rendered SVG should be valid");

        RenderedDiagram diagram2 = DiagramLayoutExtractor.extract(svg2);
        assertNotNull(diagram2);
        System.out.println("Re-rendered: " + diagram2.getEdges().size() + " edges");
    }

    @Test
    @DisplayName("E2E: Rename ER entity and re-render")
    void renameAndRerender() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();

        // Step 1: Rename AUTOR → SCHRIFTSTELLER
        String modified = SourceEditBridge.renameNode(ER_SOURCE, "erDiagram",
                "AUTOR", "AUTOR", "SCHRIFTSTELLER");
        System.out.println("After rename:\n" + modified);
        assertTrue(modified.contains("SCHRIFTSTELLER"), "New name should be present");
        assertFalse(modified.contains("AUTOR"), "Old name should be gone");

        // Step 2: Re-render
        String svg = renderer.renderToSvg(modified);
        assertNotNull(svg, "Renamed source should render");
        assertTrue(svg.contains("<svg"), "Re-rendered SVG should be valid");
    }

    @Test
    @DisplayName("E2E: Add ER edge with label and re-render")
    void addEdgeAndRerender() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();

        // Step 1: Add edge
        String modified = SourceEditBridge.addEdge(ER_SOURCE, "erDiagram", "AUTOR", "VERLAG");
        System.out.println("After addEdge:\n" + modified);

        // Step 2: Re-render (this is where the SVG error occurred)
        String svg = renderer.renderToSvg(modified);
        System.out.println("SVG null? " + (svg == null));
        if (svg != null) {
            System.out.println("SVG has <svg? " + svg.contains("<svg"));
            System.out.println("SVG length: " + svg.length());
        }
        assertNotNull(svg, "Added edge source should render (no SVG error!)");
        assertTrue(svg.contains("<svg"), "Re-rendered SVG should be valid");
    }

    @Test
    @DisplayName("E2E: Delete ER edge and re-render")
    void deleteAndRerender() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();

        String svg = renderer.renderToSvg(ER_SOURCE);
        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        List<ErRelationship> rels = diagram.getEdgesOfType(ErRelationship.class);
        assertFalse(rels.isEmpty());

        ErRelationship firstRel = rels.get(0);
        String modified = SourceEditBridge.deleteEdge(ER_SOURCE, "erDiagram", firstRel);
        System.out.println("After delete:\n" + modified);

        String svg2 = renderer.renderToSvg(modified);
        assertNotNull(svg2, "Deleted edge source should render");
        assertTrue(svg2.contains("<svg"), "Re-rendered SVG should be valid");
    }
}

