package de.bund.zrb.ui.drawer;
import de.bund.zrb.wiki.domain.OutlineNode;
import de.bund.zrb.wiki.ui.WikiFileTab;
import de.bund.zrb.ui.components.Chat;
import de.bund.zrb.ui.components.HelpButton;
import de.bund.zrb.ui.components.JclOutlinePanel;
import de.bund.zrb.ui.components.TabbedPaneWithHelpOverlay;
import de.bund.zrb.ui.components.WorkflowPanel;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.McpService;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.workflow.WorkflowRunner;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;

public class RightDrawer extends JPanel {

    private final ChatManager chatManager;
    private final TabbedPaneWithHelpOverlay tabbedPane;
    private final MainframeContext mainframeContext;
    private final ToolRegistry toolRegistry;
    private final McpService mcpService;
    private final de.bund.zrb.service.McpChatEventBridge chatEventBridge;

    private final JclOutlinePanel outlinePanel;

    /** Separate tree for wiki page outlines, shown when a WikiFileTab is active. */
    private final JTree wikiOutlineTree;
    private final DefaultTreeModel wikiOutlineModel;
    private final JPanel wikiOutlinePanel;
    private WikiFileTab activeWikiFileTab;

    /** Generic scroll callback for wiki outline (used for WikiConnectionTab preview). */
    private Consumer<String> activeAnchorScroller;

    private JCheckBox keepAliveCheckbox;
    private JCheckBox contextMemoryCheckbox;

    public RightDrawer(MainframeContext mainframeContext, ChatManager chatManager,
                       ToolRegistry toolRegistry, McpService mcpService,
                       de.bund.zrb.service.McpChatEventBridge chatEventBridge) {

        this.mainframeContext = mainframeContext;
        this.chatManager = chatManager;
        this.toolRegistry = toolRegistry;
        this.mcpService = mcpService;
        this.chatEventBridge = chatEventBridge;

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tabbedPane = new TabbedPaneWithHelpOverlay();

        // Hilfe-Button als Overlay über der Tab-Leiste
        HelpButton helpButton = new HelpButton("Hilfe zur Seitenleiste",
                e -> HelpContentProvider.showHelpPopup(
                        (Component) e.getSource(),
                        HelpContentProvider.HelpTopic.RIGHT_DRAWER));
        tabbedPane.setHelpComponent(helpButton);

        add(tabbedPane, BorderLayout.CENTER);

        // Initialize outline panel
        outlinePanel = new JclOutlinePanel();

        // Initialize wiki outline tree
        DefaultMutableTreeNode wikiRoot = new DefaultMutableTreeNode("Wiki-Gliederung");
        wikiOutlineModel = new DefaultTreeModel(wikiRoot);
        wikiOutlineTree = new JTree(wikiOutlineModel);
        wikiOutlineTree.setRootVisible(false);
        wikiOutlineTree.addTreeSelectionListener(e -> {
            javax.swing.tree.DefaultMutableTreeNode node =
                    (javax.swing.tree.DefaultMutableTreeNode) wikiOutlineTree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof WikiOutlineEntry) {
                WikiOutlineEntry entry = (WikiOutlineEntry) node.getUserObject();
                if (entry.anchor != null) {
                    if (activeWikiFileTab != null) {
                        activeWikiFileTab.scrollToAnchor(entry.anchor);
                    } else if (activeAnchorScroller != null) {
                        activeAnchorScroller.accept(entry.anchor);
                    }
                }
            }
        });
        wikiOutlinePanel = new JPanel(new BorderLayout());
        wikiOutlinePanel.add(new JScrollPane(wikiOutlineTree), BorderLayout.CENTER);

        addWorkflowTab();
        addOutlineTab();
        addChatTab();
    }

    // Keep old constructor for binary/source compatibility
    public RightDrawer(MainframeContext mainframeContext, ChatManager chatManager,
                       ToolRegistry toolRegistry, McpService mcpService) {
        this(mainframeContext, chatManager, toolRegistry, mcpService, null);
    }

    private void addWorkflowTab() {
        WorkflowRunner runner = mainframeContext.getWorkflowRunner();
        WorkflowPanel workflowPanel = new WorkflowPanel(runner, mainframeContext);
        tabbedPane.addTab("📋 Workflow", workflowPanel);
    }

    private void addOutlineTab() {
        tabbedPane.addTab("📑 Outline", outlinePanel);
    }

    private void addChatTab() {
        // Bridge is optional; if not provided, tool events simply won't be displayed.
        Chat chatPanel = new Chat(mainframeContext, chatManager, chatEventBridge);
        tabbedPane.addTab("💬 Chat", chatPanel);
    }

    public void addApplicationState(Map<String, String> state) {
        if (state == null) return;
        if (keepAliveCheckbox != null && contextMemoryCheckbox != null) {
            state.put("chat.keepAlive", String.valueOf(keepAliveCheckbox.isSelected()));
            state.put("chat.rememberContext", String.valueOf(contextMemoryCheckbox.isSelected()));
        }
        // Persist selected tab index
        state.put("drawer.right.selectedTab", String.valueOf(tabbedPane.getSelectedIndex()));
    }

    /**
     * Restore previously persisted state (e.g. selected tab).
     */
    public void restoreApplicationState(Map<String, String> state) {
        if (state == null) return;
        String tabIdx = state.get("drawer.right.selectedTab");
        if (tabIdx != null) {
            try {
                int idx = Integer.parseInt(tabIdx);
                if (idx >= 0 && idx < tabbedPane.getTabCount()) {
                    tabbedPane.setSelectedIndex(idx);
                }
            } catch (NumberFormatException ignored) { /* keep default */ }
        }
    }

    /**
     * Update the JCL outline panel with content from a file.
     * Call this when a JCL file is opened or its content changes.
     */
    public void updateJclOutline(String jclContent, String sourceName) {
        if (outlinePanel != null) {
            outlinePanel.setContent(jclContent, sourceName);
        }
    }

    /**
     * Update the JCL outline panel with a language hint from the sentence type dropdown.
     *
     * @param jclContent   source content
     * @param sourceName   display name
     * @param languageHint sentence type key (e.g. "JCL", "COBOL", "NATURAL") or null for auto-detect
     */
    public void updateJclOutline(String jclContent, String sourceName, String languageHint) {
        if (outlinePanel != null) {
            outlinePanel.setContent(jclContent, sourceName, languageHint);
        }
    }

    /**
     * Clear the JCL outline panel.
     */
    public void clearJclOutline() {
        if (outlinePanel != null) {
            outlinePanel.clear();
        }
    }

    /**
     * Get the JCL outline panel for external configuration.
     */
    public JclOutlinePanel getOutlinePanel() {
        return outlinePanel;
    }

    /**
     * Switch to the Outline tab.
     */
    public void showOutlineTab() {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).contains("Outline")) {
                tabbedPane.setSelectedIndex(i);
                break;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Wiki Outline
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the outline panel with a wiki page heading structure.
     * Replaces the JCL outline content with the wiki outline tree.
     */
    public void updateWikiOutline(OutlineNode root, String pageTitle, WikiFileTab fileTab) {
        this.activeWikiFileTab = fileTab;
        this.activeAnchorScroller = fileTab != null ? anchor -> fileTab.scrollToAnchor(anchor) : null;
        applyWikiOutline(root, pageTitle);
    }

    /**
     * Update the wiki outline with a generic scroll callback (e.g. from WikiConnectionTab preview).
     *
     * @param root       outline tree root
     * @param pageTitle  page title
     * @param scroller   callback to scroll to an anchor, or {@code null}
     */
    public void updateWikiOutline(OutlineNode root, String pageTitle, Consumer<String> scroller) {
        this.activeWikiFileTab = null;
        this.activeAnchorScroller = scroller;
        applyWikiOutline(root, pageTitle);
    }

    private void applyWikiOutline(OutlineNode root, String pageTitle) {
        DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode(
                pageTitle != null ? pageTitle : "Wiki-Gliederung");
        if (root != null) {
            for (OutlineNode child : root.children()) {
                addWikiOutlineNode(treeRoot, child);
            }
        }
        wikiOutlineModel.setRoot(treeRoot);

        // Expand all
        for (int i = 0; i < wikiOutlineTree.getRowCount(); i++) {
            wikiOutlineTree.expandRow(i);
        }

        // Switch the outline tab content to wiki outline
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).contains("Outline")) {
                tabbedPane.getTabbedPaneDelegate().setComponentAt(i, wikiOutlinePanel);
                tabbedPane.setSelectedIndex(i);
                break;
            }
        }
    }

    /**
     * Restore the JCL/COBOL/Natural outline panel (when switching away from wiki tabs).
     */
    public void restoreCodeOutline() {
        this.activeWikiFileTab = null;
        this.activeAnchorScroller = null;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).contains("Outline")) {
                tabbedPane.getTabbedPaneDelegate().setComponentAt(i, outlinePanel);
                break;
            }
        }
    }

    private void addWikiOutlineNode(DefaultMutableTreeNode parent, OutlineNode node) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(
                new WikiOutlineEntry(node.text(), node.anchor()));
        parent.add(treeNode);
        for (OutlineNode child : node.children()) {
            addWikiOutlineNode(treeNode, child);
        }
    }

    /** Simple display object for wiki outline tree nodes. */
    private static final class WikiOutlineEntry {
        final String text;
        final String anchor;

        WikiOutlineEntry(String text, String anchor) {
            this.text = text;
            this.anchor = anchor;
        }

        @Override
        public String toString() { return text; }
    }
}
