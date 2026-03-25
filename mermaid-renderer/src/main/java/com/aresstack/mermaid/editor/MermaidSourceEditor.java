package com.aresstack.mermaid.editor;

import com.aresstack.mermaid.parser.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;

/**
 * Precise, AST-based editor for Mermaid source code.
 *
 * <p>Instead of fragile regex/string-replace operations, this class:
 * <ol>
 *   <li>Detects the diagram type from the source header</li>
 *   <li>Parses the source using the diagram-specific ANTLR grammar</li>
 *   <li>Uses {@link TokenStreamRewriter} to apply modifications at
 *       exact token positions</li>
 *   <li>Returns the modified source with all original formatting preserved</li>
 * </ol>
 *
 * <h3>Usage example</h3>
 * <pre>
 *   MermaidSourceEditor editor = MermaidSourceEditor.parse(source);
 *   editor.reverseEdge("Alpha", "Delta");
 *   String modified = editor.getText();
 * </pre>
 */
public final class MermaidSourceEditor {

    // ═══════════════════════════════════════════════════════════
    //  Parsed edge / node / block model
    // ═══════════════════════════════════════════════════════════

    /** A single edge statement identified in the source. */
    public static final class EdgeInfo {
        /** Logical source node ID (e.g. "Alpha"). */
        public final String sourceId;
        /** Logical target node ID (e.g. "Delta"). */
        public final String targetId;
        /** The arrow text as it appears in the source (e.g. "-->"). */
        public final String arrowText;
        /** Optional label text (e.g. "dashed"), or empty string. */
        public final String label;
        /** Token index of the source ID token. */
        public final int sourceTokenIndex;
        /** Token index of the target ID token. */
        public final int targetTokenIndex;
        /** Token index of the arrow token. */
        public final int arrowTokenIndex;
        /** Token index of the label text token (-1 if no label). */
        public final int labelTokenIndex;
        /** Token index of the opening pipe (-1 if no label). */
        public final int labelPipeOpenIndex;
        /** Token index of the closing pipe (-1 if no label). */
        public final int labelPipeCloseIndex;
        /** Start token index of the entire edge segment (from source to target, inclusive). */
        public final int startTokenIndex;
        /** Stop token index of the entire edge segment. */
        public final int stopTokenIndex;

        EdgeInfo(String sourceId, String targetId, String arrowText, String label,
                 int sourceTokenIndex, int targetTokenIndex, int arrowTokenIndex,
                 int labelTokenIndex, int labelPipeOpenIndex, int labelPipeCloseIndex,
                 int startTokenIndex, int stopTokenIndex) {
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.arrowText = arrowText;
            this.label = label;
            this.sourceTokenIndex = sourceTokenIndex;
            this.targetTokenIndex = targetTokenIndex;
            this.arrowTokenIndex = arrowTokenIndex;
            this.labelTokenIndex = labelTokenIndex;
            this.labelPipeOpenIndex = labelPipeOpenIndex;
            this.labelPipeCloseIndex = labelPipeCloseIndex;
            this.startTokenIndex = startTokenIndex;
            this.stopTokenIndex = stopTokenIndex;
        }

        @Override
        public String toString() {
            return sourceId + " " + arrowText + (label.isEmpty() ? "" : "|" + label + "|")
                    + " " + targetId + " [@" + startTokenIndex + ".." + stopTokenIndex + "]";
        }
    }

    /** A block (class body, entity body, composite state, fragment). */
    public static final class BlockInfo {
        public final String keyword;     // "class", entity name, "state", "loop", etc.
        public final String name;        // class/entity/state name
        public final int headerStartToken;
        public final int headerStopToken;
        public final int bodyStartToken; // first token AFTER opening brace/keyword
        public final int bodyStopToken;  // closing brace/end token
        public final List<MemberInfo> members;

        BlockInfo(String keyword, String name, int headerStartToken, int headerStopToken,
                  int bodyStartToken, int bodyStopToken, List<MemberInfo> members) {
            this.keyword = keyword;
            this.name = name;
            this.headerStartToken = headerStartToken;
            this.headerStopToken = headerStopToken;
            this.bodyStartToken = bodyStartToken;
            this.bodyStopToken = bodyStopToken;
            this.members = members != null ? members : Collections.<MemberInfo>emptyList();
        }
    }

    /** A member line inside a block (class field/method, ER attribute). */
    public static final class MemberInfo {
        public final String text;
        public final int startTokenIndex;
        public final int stopTokenIndex;

        MemberInfo(String text, int startTokenIndex, int stopTokenIndex) {
            this.text = text;
            this.startTokenIndex = startTokenIndex;
            this.stopTokenIndex = stopTokenIndex;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fields
    // ═══════════════════════════════════════════════════════════

    private final String diagramType;
    private final CommonTokenStream tokens;
    private final TokenStreamRewriter rewriter;
    private final List<EdgeInfo> edges;
    private final List<BlockInfo> blocks;

    private MermaidSourceEditor(String diagramType, CommonTokenStream tokens,
                                List<EdgeInfo> edges, List<BlockInfo> blocks) {
        this.diagramType = diagramType;
        this.tokens = tokens;
        this.rewriter = new TokenStreamRewriter(tokens);
        this.edges = edges;
        this.blocks = blocks;
    }

    // ═══════════════════════════════════════════════════════════
    //  Factory: parse source and build editor
    // ═══════════════════════════════════════════════════════════

    /**
     * Parse a Mermaid source string and return an editor for precise modifications.
     *
     * @param source  the complete Mermaid diagram source
     * @return a new editor, or {@code null} if the diagram type is not supported
     */
    public static MermaidSourceEditor parse(String source) {
        if (source == null || source.trim().isEmpty()) return null;

        String type = detectDiagramType(source);
        if (type == null) return null;

        switch (type) {
            case "flowchart":   return parseFlowchart(source);
            case "stateDiagram":return parseStateDiagram(source);
            case "erDiagram":   return parseErDiagram(source);
            case "sequence":    return parseSequence(source);
            case "classDiagram":return parseClassDiagram(source);
            default:            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Public API — queries
    // ═══════════════════════════════════════════════════════════

    public String getDiagramType() { return diagramType; }

    public List<EdgeInfo> getEdges() { return Collections.unmodifiableList(edges); }

    public List<BlockInfo> getBlocks() { return Collections.unmodifiableList(blocks); }

    /**
     * Find an edge by its source and target node IDs.
     * If multiple edges connect the same nodes, returns the first one.
     */
    public EdgeInfo findEdge(String sourceId, String targetId) {
        for (EdgeInfo e : edges) {
            if (e.sourceId.equals(sourceId) && e.targetId.equals(targetId)) return e;
        }
        return null;
    }

    /**
     * Find an edge by its source and target IDs (trying both directions).
     */
    public EdgeInfo findEdgeAnyDirection(String id1, String id2) {
        EdgeInfo e = findEdge(id1, id2);
        if (e != null) return e;
        return findEdge(id2, id1);
    }

    /**
     * Find all edges that connect the same two nodes.
     */
    public List<EdgeInfo> findEdges(String sourceId, String targetId) {
        List<EdgeInfo> result = new ArrayList<EdgeInfo>();
        for (EdgeInfo e : edges) {
            if (e.sourceId.equals(sourceId) && e.targetId.equals(targetId)) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * Find a block by its name (class name, entity name, state name, etc.).
     */
    public BlockInfo findBlock(String name) {
        for (BlockInfo b : blocks) {
            if (b.name.equals(name)) return b;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  Public API — modifications
    // ═══════════════════════════════════════════════════════════

    /**
     * Get the modified source text (with all pending modifications applied).
     */
    public String getText() {
        return rewriter.getText();
    }

    /**
     * Replace the arrow of a specific edge.
     *
     * @param edge     the edge to modify (from {@link #getEdges()} or {@link #findEdge})
     * @param newArrow the new arrow text (e.g. "--x", "-.->", "==>")
     */
    public void replaceArrow(EdgeInfo edge, String newArrow) {
        rewriter.replace(edge.arrowTokenIndex, newArrow);
    }

    /**
     * Replace the label of a specific edge.
     * If the edge has no label, adds one (including pipes for flowchart).
     */
    public void replaceLabel(EdgeInfo edge, String newLabel) {
        if (edge.labelTokenIndex >= 0) {
            // Existing label — replace its text
            rewriter.replace(edge.labelTokenIndex, newLabel);
        } else if (!newLabel.isEmpty()) {
            // No existing label — insert after the arrow token
            if ("flowchart".equals(diagramType)) {
                rewriter.insertAfter(edge.arrowTokenIndex, "|" + newLabel + "|");
            } else {
                // Sequence, ER, Class, State — label after colon
                rewriter.insertAfter(edge.targetTokenIndex, " : " + newLabel);
            }
        }
    }

    /**
     * Reverse the direction of an edge (swap source ↔ target in the source code).
     * For ER diagrams, also mirrors the cardinality markers.
     */
    public void reverseEdge(EdgeInfo edge) {
        // Get the original token texts
        String srcText = tokens.get(edge.sourceTokenIndex).getText();
        String tgtText = tokens.get(edge.targetTokenIndex).getText();

        // Swap source ↔ target ID tokens
        rewriter.replace(edge.sourceTokenIndex, tgtText);
        rewriter.replace(edge.targetTokenIndex, srcText);

        // For ER diagrams, also mirror the cardinality markers
        if ("erDiagram".equals(diagramType)) {
            // The arrow text is leftCard + connector + rightCard
            // We need to replace the entire cardinality block with its mirror image
            String arrow = edge.arrowText;
            String mirrored = mirrorErArrow(arrow);
            if (!mirrored.equals(arrow)) {
                // Replace the full cardinality range (from arrowTokenIndex to before target)
                // arrowTokenIndex points to leftCardinality start
                int arrowStart = edge.arrowTokenIndex;
                int arrowStop = edge.targetTokenIndex - 1;
                // Scan back from target to find last non-whitespace token of the arrow
                while (arrowStop > arrowStart) {
                    Token t = tokens.get(arrowStop);
                    if (t.getChannel() == Token.HIDDEN_CHANNEL || t.getText().trim().isEmpty()) {
                        arrowStop--;
                    } else {
                        break;
                    }
                }
                rewriter.replace(arrowStart, arrowStop, mirrored);
            }
        }
    }

    /**
     * Mirror an ER cardinality arrow: swap left and right sides.
     * E.g., {@code "||--o{"} → {@code "}o--||"}, {@code "|o--||"} → {@code "||--o|"}
     */
    static String mirrorErArrow(String arrow) {
        if (arrow == null || arrow.length() < 6) return arrow;

        // Find the connector (-- or ==)
        int connIdx = arrow.indexOf("--");
        boolean identifying = false;
        if (connIdx < 0) {
            connIdx = arrow.indexOf("==");
            identifying = true;
        }
        if (connIdx < 0) return arrow;

        String leftCard = arrow.substring(0, connIdx);
        String connector = identifying ? "==" : "--";
        String rightCard = arrow.substring(connIdx + 2);

        // Mirror each cardinality: left↔right
        String newLeft = mirrorCardinality(rightCard);
        String newRight = mirrorCardinality(leftCard);
        return newLeft + connector + newRight;
    }

    /**
     * Mirror a single cardinality marker from one side to the other.
     * Left markers face the connector from the left, right markers from the right.
     */
    private static String mirrorCardinality(String card) {
        switch (card) {
            case "||": return "||";
            case "|o": return "o|";
            case "o|": return "|o";
            case "}|": return "|{";
            case "|{": return "}|";
            case "}o": return "o{";
            case "o{": return "}o";
            default:   return card;
        }
    }

    /**
     * Delete an entire edge statement (the line containing the edge).
     * Removes the full line including leading whitespace and trailing newline.
     */
    public void deleteEdge(EdgeInfo edge) {
        // Find the newline BEFORE this edge (to delete the whole line)
        int startIdx = edge.startTokenIndex;
        int stopIdx = edge.stopTokenIndex;

        // Extend to include leading whitespace (on hidden channel)
        while (startIdx > 0) {
            Token prev = tokens.get(startIdx - 1);
            if (prev.getChannel() == Token.HIDDEN_CHANNEL || prev.getType() == -1) {
                startIdx--;
            } else if (prev.getText().equals("\n") || prev.getText().equals("\r\n")) {
                // Include the preceding newline to remove the whole line
                startIdx--;
                break;
            } else {
                break;
            }
        }

        // Extend to include trailing newline
        while (stopIdx < tokens.size() - 1) {
            Token next = tokens.get(stopIdx + 1);
            if (next.getChannel() == Token.HIDDEN_CHANNEL) {
                stopIdx++;
            } else if (next.getText().equals("\n") || next.getText().equals("\r\n")) {
                stopIdx++;
                break;
            } else {
                break;
            }
        }

        rewriter.delete(startIdx, stopIdx);
    }

    /**
     * Rename a node everywhere it appears in the source (as edge endpoint or standalone).
     */
    public void renameNode(String oldId, String newId) {
        tokens.fill();
        // Walk ALL tokens (including hidden-channel) and replace matching content
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            String text = t.getText();
            if (text.equals(oldId)) {
                // Exact match: ID token
                rewriter.replace(i, newId);
            } else if (text.contains(oldId)) {
                // SHAPE tokens like [Rechteck] or ([Rund]) contain the label text
                // Replace the old ID inside the token text
                String newText = text.replace(oldId, newId);
                rewriter.replace(i, newText);
            }
        }
    }

    /**
     * Replace the entire segment from source ID through arrow/label to target ID
     * with new text. Used for complex edits like changing ER cardinalities.
     */
    public void replaceEdgeSegment(EdgeInfo edge, String newText) {
        rewriter.replace(edge.startTokenIndex, edge.stopTokenIndex, newText);
    }

    /**
     * Add a member line to a block (class body, entity body).
     *
     * @param block       the block to add to
     * @param memberText  the member line text (e.g. "+String name", "int age PK")
     */
    public void addMember(BlockInfo block, String memberText) {
        // Insert before the closing brace
        rewriter.insertBefore(block.bodyStopToken, "        " + memberText + "\n    ");
    }

    /**
     * Remove a specific member line from a block.
     */
    public void removeMember(MemberInfo member) {
        // Find and remove the whole line including leading whitespace
        int startIdx = member.startTokenIndex;
        int stopIdx = member.stopTokenIndex;

        // Extend to include leading whitespace
        while (startIdx > 0) {
            Token prev = tokens.get(startIdx - 1);
            if (prev.getChannel() == Token.HIDDEN_CHANNEL) {
                startIdx--;
            } else {
                break;
            }
        }

        // Extend to include trailing newline
        while (stopIdx < tokens.size() - 1) {
            Token next = tokens.get(stopIdx + 1);
            if (next.getText().equals("\n") || next.getText().equals("\r\n")) {
                stopIdx++;
                break;
            } else if (next.getChannel() == Token.HIDDEN_CHANNEL) {
                stopIdx++;
            } else {
                break;
            }
        }

        rewriter.delete(startIdx, stopIdx);
    }

    /**
     * Add a new edge to the end of the source.
     */
    public void addEdge(String sourceId, String targetId, String arrow) {
        addEdgeWithLabel(sourceId, targetId, arrow, "");
    }

    /**
     * Add a new edge to the end of the source, with an optional label suffix.
     *
     * @param sourceId    source node ID
     * @param targetId    target node ID
     * @param arrow       the arrow text (e.g. "-->", "||--o{")
     * @param labelSuffix optional suffix after the target (e.g. " : relates"), empty string if none
     */
    public void addEdgeWithLabel(String sourceId, String targetId, String arrow, String labelSuffix) {
        // Insert at the very end (before EOF)
        int lastTokenIdx = tokens.size() - 1; // EOF
        rewriter.insertBefore(lastTokenIdx, "\n    " + sourceId + " " + arrow + " " + targetId + labelSuffix);
    }

    // ═══════════════════════════════════════════════════════════
    //  Diagram-type detection
    // ═══════════════════════════════════════════════════════════

    private static String detectDiagramType(String source) {
        String trimmed = source.trim();
        // Skip leading %% comments
        while (trimmed.startsWith("%%")) {
            int nl = trimmed.indexOf('\n');
            if (nl < 0) return null;
            trimmed = trimmed.substring(nl + 1).trim();
        }
        if (trimmed.startsWith("graph ") || trimmed.startsWith("graph\n")
                || trimmed.startsWith("flowchart ") || trimmed.startsWith("flowchart\n")) {
            return "flowchart";
        }
        if (trimmed.startsWith("stateDiagram")) return "stateDiagram";
        if (trimmed.startsWith("erDiagram")) return "erDiagram";
        if (trimmed.startsWith("sequenceDiagram")) return "sequence";
        if (trimmed.startsWith("classDiagram")) return "classDiagram";
        if (trimmed.startsWith("mindmap")) return "mindmap";
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  Flowchart parser
    // ═══════════════════════════════════════════════════════════

    private static MermaidSourceEditor parseFlowchart(String source) {
        MermaidFlowchartLexer lexer = new MermaidFlowchartLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners(); // suppress console noise
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MermaidFlowchartParser parser = new MermaidFlowchartParser(tokens);
        parser.removeErrorListeners();

        MermaidFlowchartParser.DocumentContext doc = parser.document();
        List<EdgeInfo> edges = new ArrayList<EdgeInfo>();

        // Walk all edgeChain rules
        for (MermaidFlowchartParser.LineContext lineCtx : findAll(doc, MermaidFlowchartParser.LineContext.class)) {
            MermaidFlowchartParser.EdgeChainContext chain = lineCtx.edgeChain();
            if (chain == null) continue;

            List<MermaidFlowchartParser.NodeRefContext> nodeRefs = chain.nodeRef();
            List<MermaidFlowchartParser.ArrowContext> arrows = chain.arrow();
            List<MermaidFlowchartParser.EdgeLabelContext> labels = chain.edgeLabel();

            // Build edge list from chain: nodeRef[0] arrow[0] label? nodeRef[1] arrow[1] label? nodeRef[2] ...
            int labelIdx = 0;
            for (int i = 0; i < arrows.size(); i++) {
                MermaidFlowchartParser.NodeRefContext srcCtx = nodeRefs.get(i);
                MermaidFlowchartParser.NodeRefContext tgtCtx = nodeRefs.get(i + 1);
                MermaidFlowchartParser.ArrowContext arrowCtx = arrows.get(i);

                String srcId = srcCtx.ID().getText();
                String tgtId = tgtCtx.ID().getText();
                String arrowText = arrowCtx.getText();

                // Find label between this arrow and the next nodeRef
                String label = "";
                int labelTokenIdx = -1;
                int labelPipeOpen = -1;
                int labelPipeClose = -1;

                // Check if there's a label context associated with this arrow
                // Labels appear between arrow and target nodeRef
                if (labelIdx < labels.size()) {
                    MermaidFlowchartParser.EdgeLabelContext lbl = labels.get(labelIdx);
                    // Check if this label is between the current arrow and target
                    int arrowStop = arrowCtx.getStop().getTokenIndex();
                    int tgtStart = tgtCtx.getStart().getTokenIndex();
                    int lblStart = lbl.getStart().getTokenIndex();
                    if (lblStart > arrowStop && lblStart < tgtStart) {
                        if (lbl.LABEL_TEXT() != null) {
                            label = lbl.LABEL_TEXT().getText();
                            labelTokenIdx = lbl.LABEL_TEXT().getSymbol().getTokenIndex();
                        }
                        labelPipeOpen = lbl.PIPE_OPEN().getSymbol().getTokenIndex();
                        labelPipeClose = lbl.PIPE_CLOSE().getSymbol().getTokenIndex();
                        labelIdx++;
                    }
                }

                int startToken = srcCtx.getStart().getTokenIndex();
                int stopToken = tgtCtx.getStop().getTokenIndex();

                edges.add(new EdgeInfo(
                        srcId, tgtId, arrowText, label,
                        srcCtx.ID().getSymbol().getTokenIndex(),
                        tgtCtx.ID().getSymbol().getTokenIndex(),
                        arrowCtx.getStart().getTokenIndex(),
                        labelTokenIdx, labelPipeOpen, labelPipeClose,
                        startToken, stopToken
                ));
            }
        }

        return new MermaidSourceEditor("flowchart", tokens, edges, Collections.<BlockInfo>emptyList());
    }

    // ═══════════════════════════════════════════════════════════
    //  State diagram parser
    // ═══════════════════════════════════════════════════════════

    private static MermaidSourceEditor parseStateDiagram(String source) {
        MermaidStateDiagLexer lexer = new MermaidStateDiagLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MermaidStateDiagParser parser = new MermaidStateDiagParser(tokens);
        parser.removeErrorListeners();

        MermaidStateDiagParser.DocumentContext doc = parser.document();
        List<EdgeInfo> edges = new ArrayList<EdgeInfo>();

        for (MermaidStateDiagParser.LineContext lineCtx : findAll(doc, MermaidStateDiagParser.LineContext.class)) {
            MermaidStateDiagParser.TransitionContext trans = lineCtx.transition();
            if (trans == null) continue;

            List<MermaidStateDiagParser.StateRefContext> refs = trans.stateRef();
            String srcId = refs.get(0).getText();
            String tgtId = refs.get(1).getText();
            String arrowText = trans.ARROW().getText();

            String label = "";
            int labelTokenIdx = -1;
            if (trans.guard() != null) {
                label = getFullText(tokens, trans.guard());
                labelTokenIdx = trans.guard().getStart().getTokenIndex();
            }

            edges.add(new EdgeInfo(
                    srcId, tgtId, arrowText, label,
                    refs.get(0).getStart().getTokenIndex(),
                    refs.get(1).getStart().getTokenIndex(),
                    trans.ARROW().getSymbol().getTokenIndex(),
                    labelTokenIdx, -1, -1,
                    trans.getStart().getTokenIndex(),
                    trans.getStop().getTokenIndex()
            ));
        }

        return new MermaidSourceEditor("stateDiagram", tokens, edges, Collections.<BlockInfo>emptyList());
    }

    // ═══════════════════════════════════════════════════════════
    //  ER diagram parser
    // ═══════════════════════════════════════════════════════════

    private static MermaidSourceEditor parseErDiagram(String source) {
        MermaidErDiagLexer lexer = new MermaidErDiagLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MermaidErDiagParser parser = new MermaidErDiagParser(tokens);
        parser.removeErrorListeners();

        MermaidErDiagParser.DocumentContext doc = parser.document();
        List<EdgeInfo> edges = new ArrayList<EdgeInfo>();
        List<BlockInfo> blocks = new ArrayList<BlockInfo>();

        for (MermaidErDiagParser.LineContext lineCtx : findAll(doc, MermaidErDiagParser.LineContext.class)) {
            // ── Relationships ──
            MermaidErDiagParser.RelationshipContext rel = lineCtx.relationship();
            if (rel != null) {
                List<TerminalNode> ids = rel.ID();
                String srcId = ids.get(0).getText();
                String tgtId = ids.get(1).getText();

                // Build the full arrow text: leftCard + connector + rightCard
                String arrowText = getFullText(tokens, rel.leftCardinality())
                        + getFullText(tokens, rel.connector())
                        + getFullText(tokens, rel.rightCardinality());

                String label = "";
                int labelTokenIdx = -1;
                if (rel.relLabel() != null) {
                    label = getFullText(tokens, rel.relLabel());
                    labelTokenIdx = rel.relLabel().getStart().getTokenIndex();
                }

                edges.add(new EdgeInfo(
                        srcId, tgtId, arrowText, label,
                        ids.get(0).getSymbol().getTokenIndex(),
                        ids.get(1).getSymbol().getTokenIndex(),
                        rel.leftCardinality().getStart().getTokenIndex(), // "arrow" = cardinality block
                        labelTokenIdx, -1, -1,
                        rel.getStart().getTokenIndex(),
                        rel.getStop().getTokenIndex()
                ));
            }

            // ── Entity blocks ──
            MermaidErDiagParser.EntityBlockStartContext block = lineCtx.entityBlockStart();
            if (block != null) {
                String entityName = block.ID().getText();
                int headerStart = block.getStart().getTokenIndex();
                int headerStop = block.getStop().getTokenIndex();

                // Find the matching closing brace
                int bodyStart = headerStop + 1;
                int bodyStop = findMatchingBrace(tokens, headerStop);

                // Collect member lines
                List<MemberInfo> members = new ArrayList<MemberInfo>();
                // Members are attributeLine rules between this block start and its close
                // We'll scan the tokens for attribute-like patterns
                // (this is handled by the caller using findBlock + the block positions)

                blocks.add(new BlockInfo("entity", entityName,
                        headerStart, headerStop, bodyStart, bodyStop, members));
            }
        }

        return new MermaidSourceEditor("erDiagram", tokens, edges, blocks);
    }

    // ═══════════════════════════════════════════════════════════
    //  Sequence diagram parser
    // ═══════════════════════════════════════════════════════════

    private static MermaidSourceEditor parseSequence(String source) {
        MermaidSequenceLexer lexer = new MermaidSequenceLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MermaidSequenceParser parser = new MermaidSequenceParser(tokens);
        parser.removeErrorListeners();

        MermaidSequenceParser.DocumentContext doc = parser.document();
        List<EdgeInfo> edges = new ArrayList<EdgeInfo>();

        for (MermaidSequenceParser.LineContext lineCtx : findAll(doc, MermaidSequenceParser.LineContext.class)) {
            MermaidSequenceParser.MessageContext msg = lineCtx.message();
            if (msg == null) continue;

            List<TerminalNode> ids = msg.ID();
            String srcId = ids.get(0).getText();
            String tgtId = ids.get(1).getText();
            String arrowText = msg.seqArrow().getText();

            String label = "";
            int labelTokenIdx = -1;
            if (msg.messageText() != null) {
                label = getFullText(tokens, msg.messageText());
                labelTokenIdx = msg.messageText().getStart().getTokenIndex();
            }

            edges.add(new EdgeInfo(
                    srcId, tgtId, arrowText, label,
                    ids.get(0).getSymbol().getTokenIndex(),
                    ids.get(1).getSymbol().getTokenIndex(),
                    msg.seqArrow().getStart().getTokenIndex(),
                    labelTokenIdx, -1, -1,
                    msg.getStart().getTokenIndex(),
                    msg.getStop().getTokenIndex()
            ));
        }

        return new MermaidSourceEditor("sequence", tokens, edges, Collections.<BlockInfo>emptyList());
    }

    // ═══════════════════════════════════════════════════════════
    //  Class diagram parser
    // ═══════════════════════════════════════════════════════════

    private static MermaidSourceEditor parseClassDiagram(String source) {
        MermaidClassDiagLexer lexer = new MermaidClassDiagLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MermaidClassDiagParser parser = new MermaidClassDiagParser(tokens);
        parser.removeErrorListeners();

        MermaidClassDiagParser.DocumentContext doc = parser.document();
        List<EdgeInfo> edges = new ArrayList<EdgeInfo>();
        List<BlockInfo> blocks = new ArrayList<BlockInfo>();

        for (MermaidClassDiagParser.LineContext lineCtx : findAll(doc, MermaidClassDiagParser.LineContext.class)) {
            // ── Relations ──
            MermaidClassDiagParser.ClassRelationContext rel = lineCtx.classRelation();
            if (rel != null) {
                List<TerminalNode> ids = rel.ID();
                String srcId = ids.get(0).getText();
                String tgtId = ids.get(1).getText();
                String arrowText = rel.CLASS_REL().getText();

                String label = "";
                int labelTokenIdx = -1;
                if (rel.relLabel() != null) {
                    label = getFullText(tokens, rel.relLabel());
                    labelTokenIdx = rel.relLabel().getStart().getTokenIndex();
                }

                edges.add(new EdgeInfo(
                        srcId, tgtId, arrowText, label,
                        ids.get(0).getSymbol().getTokenIndex(),
                        ids.get(1).getSymbol().getTokenIndex(),
                        rel.CLASS_REL().getSymbol().getTokenIndex(),
                        labelTokenIdx, -1, -1,
                        rel.getStart().getTokenIndex(),
                        rel.getStop().getTokenIndex()
                ));
            }

            // ── Class blocks ──
            MermaidClassDiagParser.ClassBlockStartContext block = lineCtx.classBlockStart();
            if (block != null) {
                String className = block.ID().getText();
                int headerStart = block.getStart().getTokenIndex();
                int headerStop = block.getStop().getTokenIndex();
                int bodyStart = headerStop + 1;
                int bodyStop = findMatchingBrace(tokens, headerStop);

                blocks.add(new BlockInfo("class", className,
                        headerStart, headerStop, bodyStart, bodyStop,
                        Collections.<MemberInfo>emptyList()));
            }
        }

        return new MermaidSourceEditor("classDiagram", tokens, edges, blocks);
    }

    // ═══════════════════════════════════════════════════════════
    //  Utility methods
    // ═══════════════════════════════════════════════════════════

    /**
     * Find all parse tree nodes of a given type, depth-first.
     */
    @SuppressWarnings("unchecked")
    private static <T extends ParseTree> List<T> findAll(ParseTree root, Class<T> type) {
        List<T> result = new ArrayList<T>();
        findAllRecursive(root, type, result);
        return result;
    }

    private static <T extends ParseTree> void findAllRecursive(ParseTree node, Class<T> type, List<T> result) {
        if (type.isInstance(node)) {
            result.add(type.cast(node));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            findAllRecursive(node.getChild(i), type, result);
        }
    }

    /**
     * Get the full text of a parse tree context, INCLUDING hidden-channel tokens (whitespace).
     */
    private static String getFullText(CommonTokenStream tokens, ParserRuleContext ctx) {
        if (ctx == null) return "";
        int start = ctx.getStart().getTokenIndex();
        int stop = ctx.getStop().getTokenIndex();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= stop; i++) {
            sb.append(tokens.get(i).getText());
        }
        return sb.toString().trim();
    }

    /**
     * Find the matching closing brace for a block that starts with '{'.
     */
    private static int findMatchingBrace(CommonTokenStream tokens, int openBraceIndex) {
        tokens.fill();
        int depth = 0;
        for (int i = openBraceIndex; i < tokens.size(); i++) {
            String text = tokens.get(i).getText();
            if ("{".equals(text)) depth++;
            else if ("}".equals(text)) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return tokens.size() - 1; // fallback: EOF
    }
}

