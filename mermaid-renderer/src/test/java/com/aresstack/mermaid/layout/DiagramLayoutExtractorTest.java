package com.aresstack.mermaid.layout;

import com.aresstack.mermaid.layout.DiagramLayoutExtractor;
import com.aresstack.mermaid.layout.DiagramNode;
import com.aresstack.mermaid.layout.DiagramEdge;
import com.aresstack.mermaid.layout.RenderedDiagram;
import de.bund.zrb.mermaid.MermaidRenderer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DiagramLayoutExtractor}: verifies that node positions
 * and edge geometry can be extracted from rendered Mermaid SVGs.
 */
class DiagramLayoutExtractorTest {

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void flowchart_nodesAndEdgesExtracted() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            System.out.println("Mermaid not available — skip");
            return;
        }

        String svg = renderer.renderToSvg("graph TD\n    A[Start] --> B{Decision}\n    B -->|Yes| C[Action]\n    B -->|No| D[End]");
        assertNotNull(svg, "SVG should not be null");

        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram, "RenderedDiagram should not be null");

        System.out.println("Diagram type: " + diagram.getDiagramType());
        System.out.println("Nodes: " + diagram.getNodes().size());
        for (DiagramNode n : diagram.getNodes()) {
            System.out.println("  " + n);
        }
        System.out.println("Edges: " + diagram.getEdges().size());
        for (DiagramEdge e : diagram.getEdges()) {
            System.out.println("  " + e);
        }

        // We should find 4 nodes: A, B, C, D
        assertTrue(diagram.getNodes().size() >= 4,
                "Should find at least 4 nodes, found: " + diagram.getNodes().size());

        // Check that we can look up nodes by id
        DiagramNode nodeA = diagram.findNodeById("A");
        assertNotNull(nodeA, "Should find node A");
        assertEquals("Start", nodeA.getLabel());
        assertTrue(nodeA.getWidth() > 0, "Node A should have positive width");
        assertTrue(nodeA.getHeight() > 0, "Node A should have positive height");

        // We should find edges
        assertTrue(diagram.getEdges().size() >= 3,
                "Should find at least 3 edges, found: " + diagram.getEdges().size());

        // Check edge connectivity
        List<DiagramEdge> edgesFromB = diagram.findEdgesFor("B");
        assertFalse(edgesFromB.isEmpty(), "Node B should have connected edges");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void classDiagram_nodesExtracted() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) return;

        String svg = renderer.renderToSvg(
                "classDiagram\n"
                + "    class Animal {\n"
                + "        +String name\n"
                + "        +makeSound()\n"
                + "    }\n"
                + "    class Dog {\n"
                + "        +fetch()\n"
                + "    }\n"
                + "    Animal <|-- Dog"
        );
        assertNotNull(svg);

        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram);

        System.out.println("Class diagram type: " + diagram.getDiagramType());
        System.out.println("Nodes:");
        for (DiagramNode n : diagram.getNodes()) {
            System.out.println("  " + n);
        }
        System.out.println("Edges:");
        for (DiagramEdge e : diagram.getEdges()) {
            System.out.println("  " + e);
        }

        assertTrue(diagram.getNodes().size() >= 2,
                "Should find at least 2 class nodes, found: " + diagram.getNodes().size());
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void sequenceDiagram_actorsAndMessagesExtracted() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) return;

        String svg = renderer.renderToSvg(
                "sequenceDiagram\n"
                + "    participant Alice\n"
                + "    participant Bob\n"
                + "    Alice->>Bob: Hello\n"
                + "    Bob-->>Alice: Hi back"
        );
        assertNotNull(svg);

        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram);

        System.out.println("Sequence diagram: " + diagram.getDiagramType());
        System.out.println("Actors:");
        for (DiagramNode n : diagram.getNodes()) {
            System.out.println("  " + n);
        }
        System.out.println("Messages:");
        for (DiagramEdge e : diagram.getEdges()) {
            System.out.println("  " + e);
        }

        assertEquals("sequence", diagram.getDiagramType());
        assertTrue(diagram.getNodes().size() >= 2,
                "Should find at least 2 actors, found: " + diagram.getNodes().size());
    }

    @Test
    void extract_nullInput_returnsNull() {
        assertNull(DiagramLayoutExtractor.extract(null));
        assertNull(DiagramLayoutExtractor.extract(""));
    }

    @Test
    void hitTest_findNodeAt() {
        // Synthetic test with a minimal SVG
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 200\">"
                + "<g class=\"nodes\">"
                + "<g class=\"node\" id=\"flowchart-A-0\" transform=\"translate(50,50)\">"
                + "<rect x=\"-30\" y=\"-20\" width=\"60\" height=\"40\" fill=\"#eee\"/>"
                + "<g class=\"label\"><text>Hello</text></g>"
                + "</g>"
                + "</g></svg>";

        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram);
        assertEquals(1, diagram.getNodes().size());

        DiagramNode node = diagram.getNodes().get(0);
        assertEquals("A", node.getId());
        assertEquals("Hello", node.getLabel());

        // Hit test inside the node
        DiagramNode hit = diagram.findNodeAt(50, 50);
        assertNotNull(hit, "Should find node at (50,50)");
        assertEquals("A", hit.getId());

        // Hit test outside the node
        DiagramNode miss = diagram.findNodeAt(150, 150);
        assertNull(miss, "Should not find node at (150,150)");
    }
}

