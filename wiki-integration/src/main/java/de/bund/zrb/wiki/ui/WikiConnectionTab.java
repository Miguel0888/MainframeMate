package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.*;
import de.bund.zrb.wiki.port.WikiContentService;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ConnectionTab for browsing MediaWiki sites.
 * Left: site selector + search + outline tree.
 * Right: rendered wiki page (HTML).
 */
public class WikiConnectionTab implements ConnectionTab {

    private static final Logger LOG = Logger.getLogger(WikiConnectionTab.class.getName());

    private final JPanel mainPanel;
    private final WikiContentService service;
    private final JComboBox<WikiSiteDescriptor> siteSelector;
    private final JTextField searchField;
    private final JTextField pageField;
    private final JTree outlineTree;
    private final DefaultTreeModel outlineModel;
    private final JEditorPane htmlPane;
    private final JLabel statusLabel;
    private final JList<String> searchResultList;
    private final DefaultListModel<String> searchResultModel;

    /** Callback for opening a wiki page as FileTab in MainframeMate. */
    private OpenCallback openCallback;

    private String currentPageTitle;
    private WikiSiteId currentSiteId;

    public WikiConnectionTab(WikiContentService service) {
        this.service = service;
        this.mainPanel = new JPanel(new BorderLayout(4, 4));

        // ── Left panel: site + search + outline ──
        JPanel leftPanel = new JPanel(new BorderLayout(4, 4));
        leftPanel.setPreferredSize(new Dimension(280, 0));

        // Top: site selector + page field
        JPanel topControls = new JPanel();
        topControls.setLayout(new BoxLayout(topControls, BoxLayout.Y_AXIS));

        // Site selector
        JPanel sitePanel = new JPanel(new BorderLayout(2, 0));
        sitePanel.add(new JLabel("Wiki:"), BorderLayout.WEST);
        siteSelector = new JComboBox<WikiSiteDescriptor>();
        for (WikiSiteDescriptor site : service.listSites()) {
            siteSelector.addItem(site);
        }
        sitePanel.add(siteSelector, BorderLayout.CENTER);
        sitePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        topControls.add(sitePanel);
        topControls.add(Box.createVerticalStrut(4));

        // Page title field
        JPanel pagePanel = new JPanel(new BorderLayout(2, 0));
        pagePanel.add(new JLabel("Seite:"), BorderLayout.WEST);
        pageField = new JTextField("Hauptseite");
        pageField.addActionListener(e -> loadPage(pageField.getText().trim()));
        JButton goBtn = new JButton("→");
        goBtn.setToolTipText("Seite laden");
        goBtn.addActionListener(e -> loadPage(pageField.getText().trim()));
        pagePanel.add(pageField, BorderLayout.CENTER);
        pagePanel.add(goBtn, BorderLayout.EAST);
        pagePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        topControls.add(pagePanel);
        topControls.add(Box.createVerticalStrut(4));

        // Search
        JPanel searchPanel = new JPanel(new BorderLayout(2, 0));
        searchPanel.add(new JLabel("🔍"), BorderLayout.WEST);
        searchField = new JTextField();
        searchField.setToolTipText("Wiki durchsuchen (Enter)");
        searchField.addActionListener(e -> searchWiki());
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        topControls.add(searchPanel);

        leftPanel.add(topControls, BorderLayout.NORTH);

        // Center: split between search results and outline
        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        leftSplit.setDividerLocation(150);

        // Search results list
        searchResultModel = new DefaultListModel<String>();
        searchResultList = new JList<String>(searchResultModel);
        searchResultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        searchResultList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = searchResultList.getSelectedValue();
                    if (selected != null) {
                        pageField.setText(selected);
                        loadPage(selected);
                    }
                }
            }
        });
        JScrollPane searchScroll = new JScrollPane(searchResultList);
        searchScroll.setBorder(BorderFactory.createTitledBorder("Suchergebnisse"));
        leftSplit.setTopComponent(searchScroll);

        // ── Right panel: HTML viewer (init early so outline listener can reference it) ──
        htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        htmlPane.setContentType("text/html");
        htmlPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                handleLink(e);
            }
        });

        // Outline tree
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Gliederung");
        outlineModel = new DefaultTreeModel(rootNode);
        outlineTree = new JTree(outlineModel);
        outlineTree.setRootVisible(false);
        outlineTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) outlineTree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof OutlineEntry) {
                OutlineEntry entry = (OutlineEntry) node.getUserObject();
                if (entry.anchor != null) {
                    htmlPane.scrollToReference(entry.anchor);
                }
            }
        });
        JScrollPane outlineScroll = new JScrollPane(outlineTree);
        outlineScroll.setBorder(BorderFactory.createTitledBorder("Gliederung"));
        leftSplit.setBottomComponent(outlineScroll);

        leftPanel.add(leftSplit, BorderLayout.CENTER);

        // ── Assemble main split ──
        JScrollPane htmlScroll = new JScrollPane(htmlPane);

        // ── Main split ──
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, htmlScroll);
        mainSplit.setDividerLocation(280);
        mainPanel.add(mainSplit, BorderLayout.CENTER);

        // ── Status bar ──
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        // Load initial page if sites are configured
        if (siteSelector.getItemCount() > 0) {
            siteSelector.setSelectedIndex(0);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Actions
    // ═══════════════════════════════════════════════════════════

    private void loadPage(String pageTitle) {
        if (pageTitle == null || pageTitle.isEmpty()) return;
        WikiSiteDescriptor site = (WikiSiteDescriptor) siteSelector.getSelectedItem();
        if (site == null) return;

        currentSiteId = site.id();
        currentPageTitle = pageTitle;
        statusLabel.setText("⏳ Lade: " + pageTitle + "…");

        new SwingWorker<WikiPageView, Void>() {
            @Override
            protected WikiPageView doInBackground() throws Exception {
                WikiCredentials creds = getCredentials(site);
                return service.loadPage(site.id(), pageTitle, creds);
            }

            @Override
            protected void done() {
                try {
                    WikiPageView view = get();
                    htmlPane.setText(wrapHtml(view.cleanedHtml()));
                    htmlPane.setCaretPosition(0);
                    updateOutline(view.outline());
                    pageField.setText(view.title());
                    statusLabel.setText("✅ " + view.title());
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "[Wiki] Failed to load page: " + pageTitle, ex);
                    htmlPane.setText("<html><body><h2>Fehler</h2><p>" + escHtml(ex.getMessage()) + "</p></body></html>");
                    statusLabel.setText("❌ Fehler: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void searchWiki() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;
        WikiSiteDescriptor site = (WikiSiteDescriptor) siteSelector.getSelectedItem();
        if (site == null) return;

        statusLabel.setText("🔍 Suche: " + query + "…");
        searchResultModel.clear();

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                WikiCredentials creds = getCredentials(site);
                return service.searchPages(site.id(), query, creds, 20);
            }

            @Override
            protected void done() {
                try {
                    List<String> results = get();
                    for (String title : results) {
                        searchResultModel.addElement(title);
                    }
                    statusLabel.setText(results.size() + " Ergebnisse für \"" + query + "\"");
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "[Wiki] Search failed", ex);
                    statusLabel.setText("❌ Suche fehlgeschlagen: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void handleLink(HyperlinkEvent e) {
        String desc = e.getDescription();
        if (desc != null && desc.startsWith("#")) {
            // Anchor link — scroll within page
            htmlPane.scrollToReference(desc.substring(1));
            return;
        }

        // Try to extract wiki page title from URL
        String url = e.getURL() != null ? e.getURL().toString() : desc;
        if (url == null) return;

        // MediaWiki internal link pattern: /wiki/PageTitle or /w/index.php?title=PageTitle
        String pageTitle = extractPageTitle(url);
        if (pageTitle != null) {
            pageField.setText(pageTitle);
            loadPage(pageTitle);
        } else {
            // External link — open in browser
            try {
                Desktop.getDesktop().browse(e.getURL().toURI());
            } catch (Exception ex) {
                LOG.log(Level.FINE, "[Wiki] Could not open external link", ex);
            }
        }
    }

    private String extractPageTitle(String url) {
        // /wiki/Title
        int wikiIdx = url.indexOf("/wiki/");
        if (wikiIdx >= 0) {
            String raw = url.substring(wikiIdx + 6);
            int hashIdx = raw.indexOf('#');
            if (hashIdx >= 0) raw = raw.substring(0, hashIdx);
            int queryIdx = raw.indexOf('?');
            if (queryIdx >= 0) raw = raw.substring(0, queryIdx);
            return decodeUrl(raw.replace('_', ' '));
        }
        // ?title=Title
        int titleIdx = url.indexOf("title=");
        if (titleIdx >= 0) {
            String raw = url.substring(titleIdx + 6);
            int ampIdx = raw.indexOf('&');
            if (ampIdx >= 0) raw = raw.substring(0, ampIdx);
            return decodeUrl(raw.replace('_', ' '));
        }
        return null;
    }

    private static String decodeUrl(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private void updateOutline(OutlineNode root) {
        DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("Gliederung");
        if (root != null) {
            for (OutlineNode child : root.children()) {
                addOutlineNode(treeRoot, child);
            }
        }
        outlineModel.setRoot(treeRoot);
        // Expand all
        for (int i = 0; i < outlineTree.getRowCount(); i++) {
            outlineTree.expandRow(i);
        }
    }

    private void addOutlineNode(DefaultMutableTreeNode parent, OutlineNode node) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(
                new OutlineEntry(node.text(), node.anchor()));
        parent.add(treeNode);
        for (OutlineNode child : node.children()) {
            addOutlineNode(treeNode, child);
        }
    }

    private WikiCredentials getCredentials(WikiSiteDescriptor site) {
        if (!site.requiresLogin()) {
            return WikiCredentials.anonymous();
        }
        // TODO: integrate with MainframeMate LoginManager
        return WikiCredentials.anonymous();
    }

    private String wrapHtml(String bodyHtml) {
        return "<html><head><style>"
                + "body { font-family: sans-serif; font-size: 13px; margin: 12px; }"
                + "h1,h2,h3 { color: #333; }"
                + "a { color: #0645ad; }"
                + "pre, code { background: #f4f4f4; padding: 4px; }"
                + "table { border-collapse: collapse; } "
                + "td, th { border: 1px solid #ccc; padding: 4px; }"
                + "</style></head><body>"
                + bodyHtml
                + "</body></html>";
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ═══════════════════════════════════════════════════════════
    //  ConnectionTab interface
    // ═══════════════════════════════════════════════════════════

    @Override public String getTitle() { return "📖 Wiki"; }
    @Override public String getTooltip() { return "MediaWiki-Seiten durchsuchen und anzeigen"; }
    @Override public JComponent getComponent() { return mainPanel; }
    @Override public void onClose() { /* nothing */ }
    @Override public void saveIfApplicable() { /* read-only */ }
    @Override public String getContent() { return ""; }
    @Override public void markAsChanged() { /* not applicable */ }
    @Override public String getPath() { return "wiki://"; }
    @Override public Type getType() { return Type.CONNECTION; }

    @Override
    public void focusSearchField() {
        searchField.requestFocusInWindow();
    }

    @Override
    public void searchFor(String searchPattern) {
        searchField.setText(searchPattern);
        searchWiki();
    }

    // ═══════════════════════════════════════════════════════════
    //  Callback for opening pages in MainframeMate
    // ═══════════════════════════════════════════════════════════

    public void setOpenCallback(OpenCallback callback) {
        this.openCallback = callback;
    }

    public interface OpenCallback {
        void openWikiPage(String siteId, String pageTitle, String htmlContent);
    }

    // ═══════════════════════════════════════════════════════════
    //  Inner classes
    // ═══════════════════════════════════════════════════════════

    private static final class OutlineEntry {
        final String text;
        final String anchor;

        OutlineEntry(String text, String anchor) {
            this.text = text;
            this.anchor = anchor;
        }

        @Override
        public String toString() { return text; }
    }
}

