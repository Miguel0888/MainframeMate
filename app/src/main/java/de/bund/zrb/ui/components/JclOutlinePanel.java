package de.bund.zrb.ui.components;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.jcl.parser.AntlrJclParser;

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
 * Panel displaying JCL outline (structure) similar to Eclipse outline view.
 * Shows JOBs, EXECs, DDs, PROCs etc. in a tree structure.
 * Uses ANTLR-based parser with regex-based fallback.
 */
public class JclOutlinePanel extends JPanel {

    private final JTree outlineTree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final JLabel statusLabel;
    private final JComboBox<String> filterCombo;

    private JclOutlineModel currentModel;
    private Consumer<Integer> lineNavigator;
    private final AntlrJclParser parser = new AntlrJclParser();

    // Filter options
    private static final String FILTER_ALL = "Alle";
    private static final String FILTER_JOBS = "Jobs";
    private static final String FILTER_STEPS = "Steps (EXEC)";
    private static final String FILTER_DD = "DDs";
    private static final String FILTER_PROCS = "Prozeduren";

    public JclOutlinePanel() {
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Header with title and filter
        JPanel headerPanel = new JPanel(new BorderLayout(4, 0));

        JLabel titleLabel = new JLabel("📑 JCL Outline");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        filterCombo = new JComboBox<>(new String[]{
                FILTER_ALL, FILTER_JOBS, FILTER_STEPS, FILTER_DD, FILTER_PROCS
        });
        filterCombo.setPreferredSize(new Dimension(100, 24));
        filterCombo.addActionListener(e -> applyFilter());
        headerPanel.add(filterCombo, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Tree for outline
        rootNode = new DefaultMutableTreeNode("JCL");
        treeModel = new DefaultTreeModel(rootNode);
        outlineTree = new JTree(treeModel);
        outlineTree.setRootVisible(false);
        outlineTree.setShowsRootHandles(true);
        outlineTree.setCellRenderer(new JclTreeCellRenderer());

        // Double-click to navigate
        outlineTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelected();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(outlineTree);
        add(scrollPane, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Keine JCL geladen");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));
        statusLabel.setForeground(Color.GRAY);
        add(statusLabel, BorderLayout.SOUTH);

        // Show placeholder
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
     * Parse and display JCL content.
     */
    public void setContent(String jclContent, String sourceName) {
        if (jclContent == null || jclContent.isEmpty()) {
            showPlaceholder();
            return;
        }

        // Parse JCL
        currentModel = parser.parse(jclContent, sourceName);

        if (currentModel.isEmpty()) {
            showNoElements();
            return;
        }

        // Build tree
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

    private void buildTree() {
        rootNode.removeAllChildren();

        if (currentModel == null || currentModel.isEmpty()) {
            treeModel.reload();
            return;
        }

        String filter = (String) filterCombo.getSelectedItem();
        List<JclElement> elements = getFilteredElements(filter);

        // Group by type or show flat
        if (FILTER_ALL.equals(filter)) {
            buildGroupedTree(elements);
        } else {
            buildFlatTree(elements);
        }

        treeModel.reload();
        expandAll();
    }

    private void buildGroupedTree(List<JclElement> elements) {
        // Create category nodes
        DefaultMutableTreeNode jobsNode = new DefaultMutableTreeNode("📋 Jobs");
        DefaultMutableTreeNode stepsNode = new DefaultMutableTreeNode("▶ Steps");
        DefaultMutableTreeNode ddsNode = new DefaultMutableTreeNode("📄 DDs");
        DefaultMutableTreeNode procsNode = new DefaultMutableTreeNode("📦 Prozeduren");
        DefaultMutableTreeNode othersNode = new DefaultMutableTreeNode("⚙ Andere");

        for (JclElement element : elements) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(element);

            // Add children
            for (JclElement child : element.getChildren()) {
                node.add(new DefaultMutableTreeNode(child));
            }

            switch (element.getType()) {
                case JOB:
                    jobsNode.add(node);
                    break;
                case EXEC:
                    stepsNode.add(node);
                    break;
                case DD:
                    ddsNode.add(node);
                    break;
                case PROC:
                case PEND:
                    procsNode.add(node);
                    break;
                default:
                    othersNode.add(node);
                    break;
            }
        }

        // Only add non-empty categories
        if (jobsNode.getChildCount() > 0) rootNode.add(jobsNode);
        if (stepsNode.getChildCount() > 0) rootNode.add(stepsNode);
        if (ddsNode.getChildCount() > 0) rootNode.add(ddsNode);
        if (procsNode.getChildCount() > 0) rootNode.add(procsNode);
        if (othersNode.getChildCount() > 0) rootNode.add(othersNode);
    }

    private void buildFlatTree(List<JclElement> elements) {
        for (JclElement element : elements) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(element);

            // Add children
            for (JclElement child : element.getChildren()) {
                node.add(new DefaultMutableTreeNode(child));
            }

            rootNode.add(node);
        }
    }

    private List<JclElement> getFilteredElements(String filter) {
        if (currentModel == null) return new ArrayList<>();

        List<JclElement> all = currentModel.getElements();
        if (FILTER_ALL.equals(filter)) {
            return all;
        }

        List<JclElement> filtered = new ArrayList<>();
        for (JclElement e : all) {
            switch (filter) {
                case FILTER_JOBS:
                    if (e.getType() == JclElementType.JOB) filtered.add(e);
                    break;
                case FILTER_STEPS:
                    if (e.getType() == JclElementType.EXEC) filtered.add(e);
                    break;
                case FILTER_DD:
                    if (e.getType() == JclElementType.DD) filtered.add(e);
                    break;
                case FILTER_PROCS:
                    if (e.getType() == JclElementType.PROC || e.getType() == JclElementType.PEND) {
                        filtered.add(e);
                    }
                    break;
            }
        }
        return filtered;
    }

    private void applyFilter() {
        buildTree();
    }

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

    private void updateStatus() {
        if (currentModel == null) {
            statusLabel.setText("Keine JCL geladen");
            return;
        }

        int jobs = currentModel.getJobs().size();
        int steps = currentModel.getSteps().size();
        int total = currentModel.getElementCount();

        statusLabel.setText(String.format("%d Jobs, %d Steps, %d Elemente", jobs, steps, total));
    }

    private void showPlaceholder() {
        rootNode.removeAllChildren();
        rootNode.add(new DefaultMutableTreeNode("📭 Öffne eine JCL-Datei"));
        treeModel.reload();
        statusLabel.setText("Keine JCL geladen");
    }

    private void showNoElements() {
        rootNode.removeAllChildren();
        rootNode.add(new DefaultMutableTreeNode("⚠ Keine JCL-Elemente gefunden"));
        treeModel.reload();
        statusLabel.setText("Keine Struktur erkannt");
    }

    /**
     * Custom tree cell renderer for JCL elements.
     */
    private static class JclTreeCellRenderer extends DefaultTreeCellRenderer {
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
                    setIcon(null); // We use emoji icons in text
                } else if (userObject instanceof String) {
                    setText((String) userObject);
                    setToolTipText(null);
                }
            }

            return this;
        }
    }
}

