package de.bund.zrb.ui.components;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.jcl.parser.AntlrJclParser;
import de.bund.zrb.jcl.parser.CobolParser;
import de.bund.zrb.jcl.parser.NaturalParser;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel displaying mainframe source outline (JCL + COBOL + Natural) similar to Eclipse outline view.
 * Automatically detects the language and shows appropriate structure.
 */
public class JclOutlinePanel extends JPanel {

    private final JTree outlineTree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final JLabel titleLabel;
    private final JLabel statusLabel;
    private final JComboBox<String> filterCombo;

    private JclOutlineModel currentModel;
    private Consumer<Integer> lineNavigator;
    private final AntlrJclParser jclParser = new AntlrJclParser();
    private final CobolParser cobolParser = new CobolParser();
    private final NaturalParser naturalParser = new NaturalParser();
    private boolean updatingFilter = false; // guard against re-entrant filter events

    // Filter options – dynamically switched per language
    private static final String FILTER_ALL = "Alle";

    // JCL filters
    private static final String JCL_FILTER_JOBS = "Jobs";
    private static final String JCL_FILTER_STEPS = "Steps (EXEC)";
    private static final String JCL_FILTER_DD = "DDs";
    private static final String JCL_FILTER_PROCS = "Prozeduren";

    // COBOL filters
    private static final String COB_FILTER_DIVISIONS = "Divisions";
    private static final String COB_FILTER_SECTIONS = "Sections";
    private static final String COB_FILTER_PARAGRAPHS = "Paragraphs";
    private static final String COB_FILTER_DATA = "Data Items";
    private static final String COB_FILTER_CALLS = "Calls/Performs";

    // Natural filters
    private static final String NAT_FILTER_DATA = "DEFINE DATA";
    private static final String NAT_FILTER_SUBROUTINES = "Subroutines";
    private static final String NAT_FILTER_CALLS = "Calls";
    private static final String NAT_FILTER_DB = "DB Zugriffe";
    private static final String NAT_FILTER_FLOW = "Kontrollfluss";

    public JclOutlinePanel() {
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Header with title and filter
        JPanel headerPanel = new JPanel(new BorderLayout(4, 0));

        titleLabel = new JLabel("📑 Outline");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        filterCombo = new JComboBox<>(new String[]{ FILTER_ALL });
        filterCombo.setPreferredSize(new Dimension(120, 24));
        filterCombo.addActionListener(e -> applyFilter());
        headerPanel.add(filterCombo, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Tree for outline
        rootNode = new DefaultMutableTreeNode("Outline");
        treeModel = new DefaultTreeModel(rootNode);
        outlineTree = new JTree(treeModel);
        outlineTree.setRootVisible(false);
        outlineTree.setShowsRootHandles(true);
        outlineTree.setCellRenderer(new OutlineTreeCellRenderer());

        // Double-click to navigate
        outlineTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelected();
                }
            }
        });

        // Tooltip support
        ToolTipManager.sharedInstance().registerComponent(outlineTree);

        JScrollPane scrollPane = new JScrollPane(outlineTree);
        add(scrollPane, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Keine Datei geladen");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));
        statusLabel.setForeground(Color.GRAY);
        add(statusLabel, BorderLayout.SOUTH);

        showPlaceholder();
    }

    /**
     * Set the line navigator callback for when user double-clicks an element.
     */
    public void setLineNavigator(Consumer<Integer> navigator) {
        this.lineNavigator = navigator;
    }

    /**
     * Add tree selection listener.
     */
    public void addTreeSelectionListener(TreeSelectionListener listener) {
        outlineTree.addTreeSelectionListener(listener);
    }

    /**
     * Parse and display content. Automatically detects JCL vs COBOL.
     */
    public void setContent(String content, String sourceName) {
        if (content == null || content.isEmpty()) {
            showPlaceholder();
            return;
        }

        // Detect language and parse
        if (isNaturalContent(content)) {
            currentModel = naturalParser.parse(content, sourceName);
        } else if (isCobolContent(content)) {
            currentModel = cobolParser.parse(content, sourceName);
        } else {
            currentModel = jclParser.parse(content, sourceName);
        }

        if (currentModel.isEmpty()) {
            showNoElements();
            return;
        }

        // Update UI for language
        updateFilterOptions();
        updateTitle();
        buildTree();
        updateStatus();
    }

    /**
     * Clear the outline.
     */
    public void clear() {
        currentModel = null;
        rootNode.removeAllChildren();
        treeModel.reload();
        titleLabel.setText("📑 Outline");
        showPlaceholder();
    }

    /**
     * Get currently selected element.
     */
    public JclElement getSelectedElement() {
        TreePath path = outlineTree.getSelectionPath();
        if (path == null) return null;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();
        if (userObject instanceof JclElement) {
            return (JclElement) userObject;
        }
        return null;
    }

    // ── Language detection ───────────────────────────────────────────

    private boolean isNaturalContent(String content) {
        if (content == null) return false;
        String[] lines = content.split("\\r?\\n", 40);
        int naturalHits = 0;
        for (String line : lines) {
            String trimmed = line.trim().toUpperCase();
            if (trimmed.startsWith("DEFINE DATA")
                    || trimmed.startsWith("END-DEFINE")
                    || trimmed.startsWith("DEFINE SUBROUTINE")
                    || trimmed.startsWith("CALLNAT ")
                    || trimmed.startsWith("END-SUBROUTINE")
                    || trimmed.startsWith("LOCAL USING")
                    || trimmed.startsWith("PARAMETER USING")
                    || trimmed.startsWith("DECIDE ON")
                    || trimmed.startsWith("DECIDE FOR")
                    || trimmed.startsWith("INPUT USING MAP")
                    || trimmed.startsWith("FETCH RETURN")) {
                naturalHits++;
            }
        }
        return naturalHits >= 2;
    }

    private boolean isCobolContent(String content) {
        if (content == null) return false;
        String[] lines = content.split("\\r?\\n", 30);
        int cobolHits = 0;
        for (String line : lines) {
            String upper = line.toUpperCase();
            if (upper.contains("IDENTIFICATION DIVISION")
                    || upper.contains("PROCEDURE DIVISION")
                    || upper.contains("DATA DIVISION")
                    || upper.contains("ENVIRONMENT DIVISION")
                    || upper.contains("WORKING-STORAGE SECTION")
                    || upper.contains("PROGRAM-ID")) {
                cobolHits++;
            }
        }
        return cobolHits >= 1;
    }

    // ── Filter management ───────────────────────────────────────────

    private void updateFilterOptions() {
        updatingFilter = true;
        try {
            filterCombo.removeAllItems();
            filterCombo.addItem(FILTER_ALL);

            if (currentModel != null) {
                switch (currentModel.getLanguage()) {
                    case NATURAL:
                        filterCombo.addItem(NAT_FILTER_DATA);
                        filterCombo.addItem(NAT_FILTER_SUBROUTINES);
                        filterCombo.addItem(NAT_FILTER_CALLS);
                        filterCombo.addItem(NAT_FILTER_DB);
                        filterCombo.addItem(NAT_FILTER_FLOW);
                        break;
                    case COBOL:
                        filterCombo.addItem(COB_FILTER_DIVISIONS);
                        filterCombo.addItem(COB_FILTER_SECTIONS);
                        filterCombo.addItem(COB_FILTER_PARAGRAPHS);
                        filterCombo.addItem(COB_FILTER_DATA);
                        filterCombo.addItem(COB_FILTER_CALLS);
                        break;
                    default:
                        filterCombo.addItem(JCL_FILTER_JOBS);
                        filterCombo.addItem(JCL_FILTER_STEPS);
                        filterCombo.addItem(JCL_FILTER_DD);
                        filterCombo.addItem(JCL_FILTER_PROCS);
                        break;
                }
            }
        } finally {
            updatingFilter = false;
        }
    }

    private void updateTitle() {
        if (currentModel == null) {
            titleLabel.setText("📑 Outline");
            return;
        }
        switch (currentModel.getLanguage()) {
            case NATURAL: titleLabel.setText("📑 Natural Outline"); break;
            case COBOL:   titleLabel.setText("📑 COBOL Outline"); break;
            default:      titleLabel.setText("📑 JCL Outline"); break;
        }
    }

    // ── Tree building ───────────────────────────────────────────────

    private void buildTree() {
        rootNode.removeAllChildren();

        if (currentModel == null || currentModel.isEmpty()) {
            treeModel.reload();
            return;
        }

        String filter = (String) filterCombo.getSelectedItem();
        if (filter == null) {
            filter = FILTER_ALL;
        }
        List<JclElement> elements = getFilteredElements(filter);

        if (FILTER_ALL.equals(filter)) {
            switch (currentModel.getLanguage()) {
                case NATURAL: buildNaturalGroupedTree(elements); break;
                case COBOL:   buildCobolGroupedTree(elements); break;
                default:      buildJclGroupedTree(elements); break;
            }
        } else {
            buildFlatTree(elements);
        }

        treeModel.reload();
        expandAll();
    }

    private void buildJclGroupedTree(List<JclElement> elements) {
        DefaultMutableTreeNode jobsNode = new DefaultMutableTreeNode("📋 Jobs");
        DefaultMutableTreeNode stepsNode = new DefaultMutableTreeNode("▶ Steps");
        DefaultMutableTreeNode ddsNode = new DefaultMutableTreeNode("📄 DDs");
        DefaultMutableTreeNode procsNode = new DefaultMutableTreeNode("📦 Prozeduren");
        DefaultMutableTreeNode othersNode = new DefaultMutableTreeNode("⚙ Andere");

        for (JclElement element : elements) {
            DefaultMutableTreeNode node = createNodeWithChildren(element);
            switch (element.getType()) {
                case JOB:     jobsNode.add(node); break;
                case EXEC:    stepsNode.add(node); break;
                case DD:      ddsNode.add(node); break;
                case PROC:
                case PEND:    procsNode.add(node); break;
                default:      othersNode.add(node); break;
            }
        }

        addIfNotEmpty(rootNode, jobsNode);
        addIfNotEmpty(rootNode, stepsNode);
        addIfNotEmpty(rootNode, ddsNode);
        addIfNotEmpty(rootNode, procsNode);
        addIfNotEmpty(rootNode, othersNode);
    }

    private void buildCobolGroupedTree(List<JclElement> elements) {
        DefaultMutableTreeNode divisionsNode = new DefaultMutableTreeNode("📂 Divisions");
        DefaultMutableTreeNode sectionsNode = new DefaultMutableTreeNode("📁 Sections");
        DefaultMutableTreeNode paragraphsNode = new DefaultMutableTreeNode("📝 Paragraphs");
        DefaultMutableTreeNode dataNode = new DefaultMutableTreeNode("🔢 Data Items");
        DefaultMutableTreeNode callsNode = new DefaultMutableTreeNode("📞 Calls & Performs");
        DefaultMutableTreeNode othersNode = new DefaultMutableTreeNode("⚙ Andere");

        for (JclElement element : elements) {
            DefaultMutableTreeNode node = createNodeWithChildren(element);
            switch (element.getType()) {
                case DIVISION:
                case PROCEDURE_DIVISION:
                    divisionsNode.add(node); break;
                case SECTION:
                case WORKING_STORAGE:
                case LINKAGE_SECTION:
                case FILE_SECTION:
                case SCREEN_SECTION:
                    sectionsNode.add(node); break;
                case PARAGRAPH:
                    paragraphsNode.add(node); break;
                case LEVEL_01:
                case LEVEL_77:
                case LEVEL_88:
                case DATA_ITEM:
                    dataNode.add(node); break;
                case CALL_STMT:
                case PERFORM_STMT:
                    callsNode.add(node); break;
                default:
                    othersNode.add(node); break;
            }
        }

        addIfNotEmpty(rootNode, divisionsNode);
        addIfNotEmpty(rootNode, sectionsNode);
        addIfNotEmpty(rootNode, paragraphsNode);
        addIfNotEmpty(rootNode, dataNode);
        addIfNotEmpty(rootNode, callsNode);
        addIfNotEmpty(rootNode, othersNode);
    }

    private void buildNaturalGroupedTree(List<JclElement> elements) {
        DefaultMutableTreeNode dataNode = new DefaultMutableTreeNode("💾 DEFINE DATA");
        DefaultMutableTreeNode subsNode = new DefaultMutableTreeNode("📦 Subroutines");
        DefaultMutableTreeNode callsNode = new DefaultMutableTreeNode("📞 Calls");
        DefaultMutableTreeNode dbNode = new DefaultMutableTreeNode("📊 DB Zugriffe");
        DefaultMutableTreeNode flowNode = new DefaultMutableTreeNode("🔄 Kontrollfluss");
        DefaultMutableTreeNode ioNode = new DefaultMutableTreeNode("🖥 I/O");
        DefaultMutableTreeNode othersNode = new DefaultMutableTreeNode("⚙ Andere");

        for (JclElement element : elements) {
            DefaultMutableTreeNode node = createNodeWithChildren(element);
            switch (element.getType()) {
                case NAT_DEFINE_DATA:
                case NAT_LOCAL:
                case NAT_PARAMETER:
                case NAT_GLOBAL:
                case NAT_INDEPENDENT:
                case NAT_DATA_VAR:
                case NAT_DATA_VIEW:
                case NAT_DATA_REDEFINE:
                case NAT_DATA_CONST:
                    dataNode.add(node); break;
                case NAT_SUBROUTINE:
                case NAT_INLINE_SUBROUTINE:
                    subsNode.add(node); break;
                case NAT_CALLNAT:
                case NAT_CALL:
                case NAT_FETCH:
                case NAT_PERFORM:
                    callsNode.add(node); break;
                case NAT_READ:
                case NAT_FIND:
                case NAT_HISTOGRAM:
                case NAT_STORE:
                case NAT_UPDATE:
                case NAT_DELETE:
                case NAT_GET:
                    dbNode.add(node); break;
                case NAT_DECIDE:
                case NAT_IF_BLOCK:
                case NAT_FOR:
                case NAT_REPEAT:
                case NAT_ON_ERROR:
                    flowNode.add(node); break;
                case NAT_INPUT:
                case NAT_WRITE:
                case NAT_DISPLAY:
                case NAT_PRINT:
                    ioNode.add(node); break;
                default:
                    othersNode.add(node); break;
            }
        }

        addIfNotEmpty(rootNode, dataNode);
        addIfNotEmpty(rootNode, subsNode);
        addIfNotEmpty(rootNode, callsNode);
        addIfNotEmpty(rootNode, dbNode);
        addIfNotEmpty(rootNode, flowNode);
        addIfNotEmpty(rootNode, ioNode);
        addIfNotEmpty(rootNode, othersNode);
    }

    private void buildFlatTree(List<JclElement> elements) {
        for (JclElement element : elements) {
            rootNode.add(createNodeWithChildren(element));
        }
    }

    private DefaultMutableTreeNode createNodeWithChildren(JclElement element) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(element);
        for (JclElement child : element.getChildren()) {
            node.add(new DefaultMutableTreeNode(child));
        }
        return node;
    }

    private void addIfNotEmpty(DefaultMutableTreeNode parent, DefaultMutableTreeNode child) {
        if (child.getChildCount() > 0) {
            parent.add(child);
        }
    }

    // ── Filtering ───────────────────────────────────────────────────

    private List<JclElement> getFilteredElements(String filter) {
        if (currentModel == null) return new ArrayList<>();
        List<JclElement> all = currentModel.getElements();
        if (FILTER_ALL.equals(filter)) return all;

        List<JclElement> filtered = new ArrayList<>();
        for (JclElement e : all) {
            if (matchesFilter(e, filter)) {
                filtered.add(e);
            }
        }
        return filtered;
    }

    private boolean matchesFilter(JclElement e, String filter) {
        switch (filter) {
            // JCL
            case JCL_FILTER_JOBS:   return e.getType() == JclElementType.JOB;
            case JCL_FILTER_STEPS:  return e.getType() == JclElementType.EXEC;
            case JCL_FILTER_DD:     return e.getType() == JclElementType.DD;
            case JCL_FILTER_PROCS:  return e.getType() == JclElementType.PROC || e.getType() == JclElementType.PEND;
            // COBOL
            case COB_FILTER_DIVISIONS:
                return e.getType() == JclElementType.DIVISION || e.getType() == JclElementType.PROCEDURE_DIVISION;
            case COB_FILTER_SECTIONS:
                return e.getType() == JclElementType.SECTION || e.getType() == JclElementType.WORKING_STORAGE
                        || e.getType() == JclElementType.LINKAGE_SECTION || e.getType() == JclElementType.FILE_SECTION
                        || e.getType() == JclElementType.SCREEN_SECTION;
            case COB_FILTER_PARAGRAPHS:
                return e.getType() == JclElementType.PARAGRAPH;
            case COB_FILTER_DATA:
                return e.getType() == JclElementType.DATA_ITEM || e.getType() == JclElementType.LEVEL_01
                        || e.getType() == JclElementType.LEVEL_77 || e.getType() == JclElementType.LEVEL_88;
            case COB_FILTER_CALLS:
                return e.getType() == JclElementType.CALL_STMT || e.getType() == JclElementType.PERFORM_STMT;
            // Natural
            case NAT_FILTER_DATA:
                return e.getType() == JclElementType.NAT_DEFINE_DATA
                        || e.getType() == JclElementType.NAT_LOCAL
                        || e.getType() == JclElementType.NAT_PARAMETER
                        || e.getType() == JclElementType.NAT_GLOBAL
                        || e.getType() == JclElementType.NAT_INDEPENDENT
                        || e.getType() == JclElementType.NAT_DATA_VAR
                        || e.getType() == JclElementType.NAT_DATA_VIEW
                        || e.getType() == JclElementType.NAT_DATA_REDEFINE
                        || e.getType() == JclElementType.NAT_DATA_CONST;
            case NAT_FILTER_SUBROUTINES:
                return e.getType() == JclElementType.NAT_SUBROUTINE
                        || e.getType() == JclElementType.NAT_INLINE_SUBROUTINE;
            case NAT_FILTER_CALLS:
                return e.getType() == JclElementType.NAT_CALLNAT
                        || e.getType() == JclElementType.NAT_CALL
                        || e.getType() == JclElementType.NAT_FETCH
                        || e.getType() == JclElementType.NAT_PERFORM;
            case NAT_FILTER_DB:
                return e.getType() == JclElementType.NAT_READ
                        || e.getType() == JclElementType.NAT_FIND
                        || e.getType() == JclElementType.NAT_HISTOGRAM
                        || e.getType() == JclElementType.NAT_STORE
                        || e.getType() == JclElementType.NAT_UPDATE
                        || e.getType() == JclElementType.NAT_DELETE
                        || e.getType() == JclElementType.NAT_GET;
            case NAT_FILTER_FLOW:
                return e.getType() == JclElementType.NAT_DECIDE
                        || e.getType() == JclElementType.NAT_IF_BLOCK
                        || e.getType() == JclElementType.NAT_FOR
                        || e.getType() == JclElementType.NAT_REPEAT
                        || e.getType() == JclElementType.NAT_ON_ERROR;
            default:
                return true;
        }
    }

    private void applyFilter() {
        if (updatingFilter) return; // ignore events while combo is being rebuilt
        buildTree();
    }

    // ── Navigation ──────────────────────────────────────────────────

    private void expandAll() {
        for (int i = 0; i < outlineTree.getRowCount(); i++) {
            outlineTree.expandRow(i);
        }
    }

    private void navigateToSelected() {
        JclElement element = getSelectedElement();
        if (element != null && lineNavigator != null) {
            lineNavigator.accept(element.getLineNumber());
        }
    }

    // ── Status ──────────────────────────────────────────────────────

    private void updateStatus() {
        if (currentModel == null) {
            statusLabel.setText("Keine Datei geladen");
            return;
        }

        if (currentModel.getLanguage() == JclOutlineModel.Language.COBOL) {
            int divs = currentModel.getDivisions().size();
            int secs = currentModel.getSections().size();
            int paras = currentModel.getParagraphs().size();
            int total = currentModel.getElementCount();
            statusLabel.setText(String.format("%d Div, %d Sec, %d Para, %d Elemente", divs, secs, paras, total));
        } else if (currentModel.getLanguage() == JclOutlineModel.Language.NATURAL) {
            int subs = currentModel.getSubroutines().size();
            int calls = currentModel.getNaturalCalls().size();
            int dbOps = currentModel.getNaturalDbOps().size();
            int total = currentModel.getElementCount();
            statusLabel.setText(String.format("%d Subs, %d Calls, %d DB, %d Elemente", subs, calls, dbOps, total));
        } else {
            int jobs = currentModel.getJobs().size();
            int steps = currentModel.getSteps().size();
            int total = currentModel.getElementCount();
            statusLabel.setText(String.format("%d Jobs, %d Steps, %d Elemente", jobs, steps, total));
        }
    }

    private void showPlaceholder() {
        rootNode.removeAllChildren();
        rootNode.add(new DefaultMutableTreeNode("📭 Öffne eine JCL/COBOL/Natural-Datei"));
        treeModel.reload();
        statusLabel.setText("Keine Datei geladen");
    }

    private void showNoElements() {
        rootNode.removeAllChildren();
        rootNode.add(new DefaultMutableTreeNode("⚠ Keine Strukturelemente gefunden"));
        treeModel.reload();
        statusLabel.setText("Keine Struktur erkannt");
    }

    // ── Tree Cell Renderer ──────────────────────────────────────────

    private static class OutlineTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();

                if (userObject instanceof JclElement) {
                    JclElement element = (JclElement) userObject;
                    setText(element.getDisplayText());
                    setToolTipText(element.getTooltipText());
                    setIcon(null);
                } else if (userObject instanceof String) {
                    setText((String) userObject);
                    setToolTipText(null);
                }
            }

            return this;
        }
    }
}

