package de.bund.zrb.betaview.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabbed pane that holds open document tabs, each containing a DocumentPreviewPanel.
 * Supports close per tab and close-all.
 */
public final class DocumentTabbedPane extends JPanel {

    private final JTabbedPane tabbedPane;
    private final JLabel emptyLabel;

    /** linkID (or key) â†’ TabEntry. Insertion-ordered. */
    private final Map<String, TabEntry> tabEntries = new LinkedHashMap<>();

    private TabCloseListener tabCloseListener;
    private CloseAllListener closeAllListener;
    private TabSelectionListener tabSelectionListener;

    public DocumentTabbedPane() {
        setLayout(new BorderLayout());

        tabbedPane = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        add(tabbedPane, BorderLayout.CENTER);

        emptyLabel = new JLabel("Kein Dokument geöffnet. Wählen Sie ein Ergebnis aus der Tabelle.", JLabel.CENTER);
        emptyLabel.setForeground(Color.GRAY);

        showEmptyState();


        tabbedPane.addChangeListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            if (idx >= 0 && tabSelectionListener != null) {
                TabEntry entry = getEntryByIndex(idx);
                if (entry != null) {
                    tabSelectionListener.onTabSelected(entry.tab, entry.previewPanel, entry.loaded);
                }
            }
        });
    }

    // ======== Public API ========

    /**
     * Add a new tab or select it if already open.
     * Returns the DocumentPreviewPanel for the tab.
     */
    public DocumentPreviewPanel addOrSelectTab(DocumentTab tab) {
        String key = tab.key();
        TabEntry existing = tabEntries.get(key);
        if (existing != null) {
            tabbedPane.setSelectedComponent(existing.previewPanel);
            return existing.previewPanel;
        }

        hideEmptyState();

        DocumentPreviewPanel panel = new DocumentPreviewPanel();
        TabEntry entry = new TabEntry(tab, panel);
        tabEntries.put(key, entry);

        tabbedPane.addTab(null, panel);
        int tabIndex = tabbedPane.indexOfComponent(panel);
        tabbedPane.setTabComponentAt(tabIndex, createTabComponent(tab, key));
        tabbedPane.setSelectedComponent(panel);

        return panel;
    }

    /**
     * Add a tab without selecting it. For lazy-loaded background tabs.
     * If the tab already exists, just returns its panel.
     */
    public DocumentPreviewPanel addTabInBackground(DocumentTab tab) {
        String key = tab.key();
        TabEntry existing = tabEntries.get(key);
        if (existing != null) {
            return existing.previewPanel;
        }

        hideEmptyState();

        DocumentPreviewPanel panel = new DocumentPreviewPanel();
        TabEntry entry = new TabEntry(tab, panel);
        tabEntries.put(key, entry);

        tabbedPane.addTab(null, panel);
        int tabIndex = tabbedPane.indexOfComponent(panel);
        tabbedPane.setTabComponentAt(tabIndex, createTabComponent(tab, key));
        // NOT selecting -- tab stays in background

        return panel;
    }

    /** Mark a tab as loaded (content has been fetched). */
    public void setTabLoaded(String key) {
        TabEntry entry = tabEntries.get(key);
        if (entry != null) {
            entry.loaded = true;
        }
    }

    /** Mark a tab as not loaded (forces reload on next select). */
    public void setTabNotLoaded(String key) {
        TabEntry entry = tabEntries.get(key);
        if (entry != null) {
            entry.loaded = false;
        }
    }

    /** Remove a tab by its key (linkID). */
    public void removeTab(String key) {
        TabEntry entry = tabEntries.remove(key);
        if (entry != null) {
            int idx = tabbedPane.indexOfComponent(entry.previewPanel);
            if (idx >= 0) {
                tabbedPane.removeTabAt(idx);
            }
        }
        if (tabEntries.isEmpty()) {
            showEmptyState();
        }
    }

    /** Remove all tabs from the UI. */
    public void removeAllTabs() {
        tabEntries.clear();
        tabbedPane.removeAll();
        showEmptyState();
    }

    /** Get all currently open tab keys. */
    public List<String> allKeys() {
        return new ArrayList<>(tabEntries.keySet());
    }

    /** Get the DocumentTab for a key. */
    public DocumentTab getTab(String key) {
        TabEntry e = tabEntries.get(key);
        return e != null ? e.tab : null;
    }

    /** Get the currently selected DocumentPreviewPanel. */
    public DocumentPreviewPanel getSelectedPreviewPanel() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx < 0) return null;
        TabEntry entry = getEntryByIndex(idx);
        return entry != null ? entry.previewPanel : null;
    }

    /** Get the currently selected DocumentTab. */
    public DocumentTab getSelectedTab() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx < 0) return null;
        TabEntry entry = getEntryByIndex(idx);
        return entry != null ? entry.tab : null;
    }

    public boolean isTabLoaded(String key) {
        TabEntry entry = tabEntries.get(key);
        return entry != null && entry.loaded;
    }

    // ======== Listeners ========

    public void setTabCloseListener(TabCloseListener l)         { this.tabCloseListener = l; }
    public void setCloseAllListener(CloseAllListener l)         { this.closeAllListener = l; }
    public void setTabSelectionListener(TabSelectionListener l) { this.tabSelectionListener = l; }

    @FunctionalInterface
    public interface TabCloseListener {
        void onTabClose(DocumentTab tab, String key);
    }

    @FunctionalInterface
    public interface CloseAllListener {
        void onCloseAll();
    }

    @FunctionalInterface
    public interface TabSelectionListener {
        void onTabSelected(DocumentTab tab, DocumentPreviewPanel panel, boolean loaded);
    }

    // ======== Private ========

    private void showEmptyState() {
        if (tabbedPane.getTabCount() == 0) {
            tabbedPane.addTab("", emptyLabel);
            tabbedPane.setEnabledAt(0, false);
        }
    }

    private void hideEmptyState() {
        int idx = tabbedPane.indexOfComponent(emptyLabel);
        if (idx >= 0) {
            tabbedPane.removeTabAt(idx);
        }
    }

    private JPanel createTabComponent(DocumentTab tab, String key) {
        JPanel tabComp = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabComp.setOpaque(false);

        String label = tab.title();
        if (label.length() > 30) label = label.substring(0, 27) + "...";
        JLabel titleLabel = new JLabel(label);
        titleLabel.setToolTipText(tab.timestamp() + " \u2013 " + tab.title());
        tabComp.add(titleLabel);

        JButton closeBtn = new JButton("\u00d7");
        closeBtn.setPreferredSize(new Dimension(20, 20));
        closeBtn.setMargin(new java.awt.Insets(0, 0, 0, 0));
        closeBtn.setFocusable(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.addActionListener(e -> {
            if (tabCloseListener != null) {
                tabCloseListener.onTabClose(tab, key);
            }
        });
        tabComp.add(closeBtn);

        return tabComp;
    }

    private TabEntry getEntryByIndex(int idx) {
        java.awt.Component comp = tabbedPane.getComponentAt(idx);
        for (TabEntry entry : tabEntries.values()) {
            if (entry.previewPanel == comp) {
                return entry;
            }
        }
        return null;
    }

    private static final class TabEntry {
        final DocumentTab tab;
        final DocumentPreviewPanel previewPanel;
        boolean loaded;

        TabEntry(DocumentTab tab, DocumentPreviewPanel previewPanel) {
            this.tab = tab;
            this.previewPanel = previewPanel;
            this.loaded = false;
        }
    }
}
