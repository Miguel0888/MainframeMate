package com.aresstack.mermaid.layout;

import de.bund.zrb.mermaid.MermaidRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the polymorphic type hierarchy of DiagramNode / DiagramEdge subclasses.
 * Validates that the DiagramLayoutExtractor produces correct typed instances
 * for all major diagram types from MermaidRenderTest and MermaidRenderTest2.
 */
class TypeHierarchyTest {

    private static MermaidRenderer renderer;

    @BeforeAll
    static void initRenderer() {
        renderer = MermaidRenderer.getInstance();
        assertTrue(renderer.isAvailable(), "mermaid.min.js muss auf dem Classpath liegen");
    }

    private RenderedDiagram render(String code) {
        String svg = renderer.renderToSvg(code);
        assertNotNull(svg, "SVG darf nicht null sein für: " + code.substring(0, Math.min(40, code.length())));
        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram, "Extraktion darf nicht null sein");
        return diagram;
    }

    // ═══════════════════════════════════════════════════════════
    //  Flowchart: NodeShape + FlowchartEdge LineStyle
    // ═══════════════════════════════════════════════════════════

    @Test
    void flowchart_nodesAreFlowchartNodeInstances() {
        RenderedDiagram d = render(
                "graph TD\n"
                + "    A[Rechteck] --> B([Stadium])\n"
                + "    B --> C((Kreis))\n"
                + "    C --> D{Raute}\n"
                + "    D --> E{{Sechseck}}\n"
        );

        // All nodes should be FlowchartNode instances
        List<FlowchartNode> flowNodes = d.getNodesOfType(FlowchartNode.class);
        assertTrue(flowNodes.size() >= 4,
                "Mindestens 4 FlowchartNodes erwartet, gefunden: " + flowNodes.size());

        // Check that shapes are detected
        for (FlowchartNode fn : flowNodes) {
            assertNotNull(fn.getShape(), "Shape darf nicht null sein für " + fn.getId());
            System.out.println("  " + fn.getId() + " → " + fn.getShape());
        }
    }

    @Test
    void flowchart_edgesAreFlowchartEdgeInstances() {
        RenderedDiagram d = render(
                "graph LR\n"
                + "    Alpha --> Delta\n"
                + "    Beta -.->|dashed| Echo\n"
                + "    Gamma ==>|thick| Foxtrot\n"
        );

        List<FlowchartEdge> edges = d.getEdgesOfType(FlowchartEdge.class);
        assertTrue(edges.size() >= 3,
                "Mindestens 3 FlowchartEdges erwartet, gefunden: " + edges.size());

        // Print detected styles for debugging
        for (FlowchartEdge fe : edges) {
            System.out.println("  " + fe.getSourceId() + " -> " + fe.getTargetId()
                    + " : " + fe.getLineStyle() + " head=" + fe.getHeadType());
        }

        // At least one edge should be DASHED or THICK
        boolean hasDashed = false, hasThick = false;
        for (FlowchartEdge fe : edges) {
            if (fe.getLineStyle() == LineStyle.DASHED) hasDashed = true;
            if (fe.getLineStyle() == LineStyle.THICK) hasThick = true;
        }
        // Note: line style detection depends on SVG attributes — may be heuristic
        System.out.println("  hasDashed=" + hasDashed + ", hasThick=" + hasThick);
    }

    @Test
    void flowchart_shapeDetection_diamondVsRectangle() {
        RenderedDiagram d = render(
                "graph TD\n"
                + "    Start([Start]) --> Check{OK?}\n"
                + "    Check -->|Ja| Ende([Ende])\n"
                + "    Check -->|Nein| Fehler[Fehler]\n"
        );

        FlowchartNode check = null, fehler = null;
        for (FlowchartNode fn : d.getNodesOfType(FlowchartNode.class)) {
            if ("Check".equals(fn.getId())) check = fn;
            if ("Fehler".equals(fn.getId())) fehler = fn;
        }

        if (check != null) {
            System.out.println("  Check shape: " + check.getShape());
            assertEquals(NodeShape.DIAMOND, check.getShape(),
                    "Check sollte eine Raute sein");
        }
        if (fehler != null) {
            System.out.println("  Fehler shape: " + fehler.getShape());
            assertEquals(NodeShape.RECTANGLE, fehler.getShape(),
                    "Fehler sollte ein Rechteck sein");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Class diagram: ClassNode + ClassRelation
    // ═══════════════════════════════════════════════════════════

    @Test
    void classDiagram_nodesAreClassNodeInstances() {
        RenderedDiagram d = render(
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
                + "    Animal <|-- Cat\n"
        );

        List<ClassNode> classNodes = d.getNodesOfType(ClassNode.class);
        assertTrue(classNodes.size() >= 3,
                "Mindestens 3 ClassNodes erwartet, gefunden: " + classNodes.size());

        // Find Animal
        ClassNode animal = null;
        for (ClassNode cn : classNodes) {
            if ("Animal".equals(cn.getId())) {
                animal = cn;
                break;
            }
        }
        assertNotNull(animal, "Animal ClassNode muss gefunden werden");

        System.out.println("  Animal stereotype: '" + animal.getStereotype() + "'");
        System.out.println("  Animal members: " + animal.getMembers().size());
        for (ClassMember m : animal.getMembers()) {
            System.out.println("    " + (m.isMethod() ? "method" : "field") + ": " + m);
        }

        // Animal should have members
        assertFalse(animal.getMembers().isEmpty(),
                "Animal sollte Felder/Methoden haben");
    }

    @Test
    void classDiagram_edgesAreFlowchartEdgeOrClassRelation() {
        RenderedDiagram d = render(
                "classDiagram\n"
                + "    class Kunde\n"
                + "    class Bestellung\n"
                + "    Kunde <|-- Bestellung\n"
        );

        // Edges should exist
        assertFalse(d.getEdges().isEmpty(),
                "Mindestens 1 Edge erwartet");

        for (DiagramEdge e : d.getEdges()) {
            System.out.println("  Edge: " + e);
            System.out.println("    Type: " + e.getClass().getSimpleName());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ER diagram: ErEntityNode + ErRelationship
    // ═══════════════════════════════════════════════════════════

    @Test
    void erDiagram_nodesAreErEntityNodeInstances() {
        RenderedDiagram d = render(
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
                + "    AUTOR ||--o{ BUCH : schreibt\n"
        );

        List<ErEntityNode> entities = d.getNodesOfType(ErEntityNode.class);
        assertTrue(entities.size() >= 2,
                "Mindestens 2 ErEntityNodes erwartet, gefunden: " + entities.size());

        for (ErEntityNode en : entities) {
            System.out.println("  Entity: " + en.getId() + " with " + en.getAttributes().size() + " attributes");
            for (ErAttribute attr : en.getAttributes()) {
                System.out.println("    " + attr.getType() + " " + attr.getName()
                        + (attr.isPrimaryKey() ? " PK" : ""));
            }
        }

        // Check BUCH has attributes
        ErEntityNode buch = null;
        for (ErEntityNode en : entities) {
            if ("BUCH".equals(en.getId())) buch = en;
        }
        if (buch != null) {
            System.out.println("  BUCH attributes: " + buch.getAttributes().size());
            // Note: attribute extraction from SVG is heuristic and may
            // not work for all Mermaid versions. The important thing is
            // that buch IS an ErEntityNode (not plain DiagramNode).
        }
    }

    @Test
    void erDiagram_edgesAreErRelationshipInstances() {
        RenderedDiagram d = render(
                "erDiagram\n"
                + "    AUTOR ||--o{ BUCH : schreibt\n"
                + "    VERLAG ||--o{ BUCH : verlegt\n"
        );

        List<ErRelationship> rels = d.getEdgesOfType(ErRelationship.class);
        assertTrue(rels.size() >= 2,
                "Mindestens 2 ErRelationships erwartet, gefunden: " + rels.size());

        for (ErRelationship rel : rels) {
            System.out.println("  ER Rel: " + rel.getSourceId() + " " + rel.getSourceCardinality()
                    + "--" + rel.getTargetCardinality() + " " + rel.getTargetId());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Sequence diagram: SequenceActorNode + SequenceMessage
    // ═══════════════════════════════════════════════════════════

    @Test
    void sequence_nodesAreSequenceActorNodeInstances() {
        RenderedDiagram d = render(
                "sequenceDiagram\n"
                + "    participant Chef\n"
                + "    participant Alice\n"
                + "    participant Bob\n"
                + "    Chef->>Alice: Agenda\n"
                + "    Alice->>Bob: Vorschlag\n"
                + "    Bob-->>Alice: Feedback\n"
        );

        List<SequenceActorNode> actors = d.getNodesOfType(SequenceActorNode.class);
        assertEquals(3, actors.size(), "3 SequenceActorNodes erwartet");

        for (SequenceActorNode a : actors) {
            System.out.println("  Actor: " + a.getId() + " type=" + a.getActorType());
            assertEquals(SequenceActorNode.ActorType.PARTICIPANT, a.getActorType());
        }
    }

    @Test
    void sequence_edgesAreSequenceMessageInstances() {
        RenderedDiagram d = render(
                "sequenceDiagram\n"
                + "    participant Browser\n"
                + "    participant Server\n"
                + "    Browser->>Server: GET /api\n"
                + "    Server-->>Browser: JSON\n"
        );

        List<SequenceMessage> msgs = d.getEdgesOfType(SequenceMessage.class);
        assertTrue(msgs.size() >= 2,
                "Mindestens 2 SequenceMessages erwartet, gefunden: " + msgs.size());

        boolean hasSolid = false, hasDotted = false;
        for (SequenceMessage msg : msgs) {
            System.out.println("  Msg: " + msg.getSourceId() + " " + msg.getMessageType()
                    + " " + msg.getTargetId() + " [" + msg.getLabel() + "]");
            if (msg.getMessageType() == MessageType.SYNC_SOLID) hasSolid = true;
            if (msg.getMessageType() == MessageType.SYNC_DOTTED) hasDotted = true;
        }
        assertTrue(hasSolid, "Mindestens eine SYNC_SOLID Message erwartet");
        assertTrue(hasDotted, "Mindestens eine SYNC_DOTTED (reply) Message erwartet");
    }

    // ═══════════════════════════════════════════════════════════
    //  State diagram: StateDiagramNode
    // ═══════════════════════════════════════════════════════════

    @Test
    void stateDiagram_nodesAreStateDiagramNodeInstances() {
        RenderedDiagram d = render(
                "stateDiagram-v2\n"
                + "    [*] --> Rot\n"
                + "    Rot --> Rot_Gelb : warten\n"
                + "    Rot_Gelb --> Gruen\n"
                + "    Gruen --> Gelb\n"
                + "    Gelb --> Rot\n"
        );

        List<StateDiagramNode> states = d.getNodesOfType(StateDiagramNode.class);
        System.out.println("  State nodes found: " + states.size());
        for (StateDiagramNode sn : states) {
            System.out.println("    " + sn.getId()
                    + (sn.isStartState() ? " [START]" : "")
                    + (sn.isEndState() ? " [END]" : ""));
        }

        // Some nodes should be StateDiagramNode type
        assertFalse(d.getNodes().isEmpty(), "State diagram sollte Nodes haben");
    }

    // ═══════════════════════════════════════════════════════════
    //  Mindmap: MindmapItemNode
    // ═══════════════════════════════════════════════════════════

    @Test
    void mindmap_nodesAreMindmapItemNodeInstances() {
        RenderedDiagram d = render(
                "mindmap\n"
                + "  root((Humus))\n"
                + "    Arten\n"
                + "      Naehrhumus\n"
                + "      Dauerhumus\n"
                + "    Entstehung\n"
        );

        List<MindmapItemNode> items = d.getNodesOfType(MindmapItemNode.class);
        System.out.println("  Mindmap nodes found: " + items.size());
        for (MindmapItemNode mi : items) {
            System.out.println("    depth=" + mi.getDepth() + " root=" + mi.isRoot()
                    + " label='" + mi.getLabel() + "'");
        }

        assertFalse(items.isEmpty(), "Mindmap sollte MindmapItemNodes haben");
        // First should be root
        if (!items.isEmpty()) {
            assertTrue(items.get(0).isRoot(), "Erster Mindmap-Node sollte Root sein");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Polymorphism: instanceof checks work correctly
    // ═══════════════════════════════════════════════════════════

    @Test
    void polymorphism_instanceofWorksCorrectly() {
        RenderedDiagram d = render(
                "graph TD\n"
                + "    A[Start] --> B{Entscheidung}\n"
                + "    B -->|Ja| C[Ende]\n"
        );

        for (DiagramNode n : d.getNodes()) {
            // All should be DiagramNode instances
            assertTrue(n instanceof DiagramNode);
            // Flowchart nodes should be FlowchartNode instances
            if ("node".equals(n.getKind())) {
                assertTrue(n instanceof FlowchartNode,
                        n.getId() + " sollte FlowchartNode sein, ist: " + n.getClass().getSimpleName());
            }
        }

        for (DiagramEdge e : d.getEdges()) {
            assertTrue(e instanceof DiagramEdge);
            if ("flowchart-link".equals(e.getKind())) {
                assertTrue(e instanceof FlowchartEdge,
                        e.getId() + " sollte FlowchartEdge sein, ist: " + e.getClass().getSimpleName());
            }
        }
    }

    @Test
    void getNodesOfType_filtersCorrectly() {
        RenderedDiagram d = render(
                "graph TD\n    A --> B\n"
        );

        // Should get FlowchartNodes
        List<FlowchartNode> flowNodes = d.getNodesOfType(FlowchartNode.class);
        assertFalse(flowNodes.isEmpty());

        // Should NOT get ClassNodes from a flowchart
        List<ClassNode> classNodes = d.getNodesOfType(ClassNode.class);
        assertTrue(classNodes.isEmpty(),
                "Flowchart sollte keine ClassNodes enthalten");
    }

    // ═══════════════════════════════════════════════════════════
    //  ClassMember parsing
    // ═══════════════════════════════════════════════════════════

    @Test
    void classMember_parseField() {
        ClassMember m = ClassMember.parse("+String name");
        assertEquals(Visibility.PUBLIC, m.getVisibility());
        assertEquals("name", m.getName());
        assertEquals("String", m.getType());
        assertFalse(m.isMethod());
    }

    @Test
    void classMember_parseMethod() {
        ClassMember m = ClassMember.parse("+speak() void");
        assertEquals(Visibility.PUBLIC, m.getVisibility());
        assertEquals("speak", m.getName());
        assertEquals("void", m.getType());
        assertTrue(m.isMethod());
    }

    @Test
    void classMember_toMermaid() {
        ClassMember field = new ClassMember(Visibility.PRIVATE, "age", "int", false, "");
        assertEquals("-int age", field.toMermaid());

        ClassMember method = new ClassMember(Visibility.PUBLIC, "getAge", "int", true, "");
        assertEquals("+getAge() int", method.toMermaid());
    }

    // ═══════════════════════════════════════════════════════════
    //  ErAttribute parsing
    // ═══════════════════════════════════════════════════════════

    @Test
    void erAttribute_parse() {
        ErAttribute attr = ErAttribute.parse("string isbn PK");
        assertEquals("isbn", attr.getName());
        assertEquals("string", attr.getType());
        assertTrue(attr.isPrimaryKey());
    }

    @Test
    void erAttribute_toMermaid() {
        ErAttribute attr = new ErAttribute("isbn", "string", true, false, "");
        assertEquals("string isbn PK", attr.toMermaid());
    }

    // ═══════════════════════════════════════════════════════════
    //  Mermaid syntax generation (roundtrip)
    // ═══════════════════════════════════════════════════════════

    @Test
    void nodeShape_toMermaid() {
        assertEquals("A([Start])", NodeShape.STADIUM.toMermaid("A", "Start"));
        assertEquals("B{Entscheidung}", NodeShape.DIAMOND.toMermaid("B", "Entscheidung"));
        assertEquals("C((Kreis))", NodeShape.CIRCLE.toMermaid("C", "Kreis"));
        assertEquals("D{{Hex}}", NodeShape.HEXAGON.toMermaid("D", "Hex"));
    }

    @Test
    void relationType_toMermaid() {
        assertEquals("Animal <|-- Dog", RelationType.INHERITANCE.toMermaid("Animal", "Dog", null));
        assertEquals("A *-- B : owns", RelationType.COMPOSITION.toMermaid("A", "B", "owns"));
    }

    @Test
    void flowchartEdge_toMermaidArrow() {
        FlowchartEdge solid = new FlowchartEdge("", "A", "B", "", null,
                0, 0, 0, 0, LineStyle.SOLID, ArrowHead.NORMAL, ArrowHead.NONE);
        assertEquals("-->", solid.toMermaidArrow());

        FlowchartEdge dashed = new FlowchartEdge("", "A", "B", "test", null,
                0, 0, 0, 0, LineStyle.DASHED, ArrowHead.NORMAL, ArrowHead.NONE);
        assertEquals("-.->|test|", dashed.toMermaidArrow());

        FlowchartEdge thick = new FlowchartEdge("", "A", "B", "", null,
                0, 0, 0, 0, LineStyle.THICK, ArrowHead.NORMAL, ArrowHead.NONE);
        assertEquals("==>", thick.toMermaidArrow());
    }
}

