package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.*;
import de.bund.zrb.wiki.port.WikiContentService;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * ConnectionTab for browsing MediaWiki sites.
 * Layout: vertical split – top: search controls + results table, bottom: HTML preview.
 * Outline is delegated to the RightDrawer via WikiFileTab.
 * Single-click on result → preview below. Double-click/Enter → open as new WikiFileTab.
 */
public class WikiConnectionTab implements ConnectionTab {

    private static final Logger LOG = Logger.getLogger(WikiConnectionTab.class.getName());

    private final JPanel mainPanel;
    private final WikiContentService service;
    private final JComboBox<WikiSiteDescriptor> siteSelector;
    private final JTextField searchField;
    private final JEditorPane htmlPane;
    private final JLabel statusLabel;
    private final JTextField resultFilterField;

    // Results table
    private final WikiResultTableModel resultModel;
    private final JTable resultTable;
    private final TableRowSorter<WikiResultTableModel> resultSorter;

    /** Callback for opening a wiki page as FileTab in MainframeMate. */
    private OpenCallback openCallback;
    /** Callback to notify about outline changes (for RightDrawer). */
    private OutlineCallback outlineCallback;
    /** Callback for prefetching search results into local cache. */
    private WikiPrefetchCallback prefetchCallback;
    /** Callback to resolve wiki login credentials per site. */
    private CredentialsCallback credentialsCallback;
    /** Callback to persist credentials entered via the login prompt. */
    private CredentialsSaveCallback credentialsSaveCallback;

    private String currentPageTitle;
    private WikiSiteId currentSiteId;
    private WikiPageView currentPreview;

    /** Panel containing htmlScroll + optional ImageStripPanel for the preview area. */
    private JPanel previewPanel;

    public WikiConnectionTab(WikiContentService service) {
        this.service = service;
        this.mainPanel = new JPanel(new BorderLayout(0, 0));

        // ═══════════════════════════════════════════════════════════
        //  Top panel: site selector + search
        // ═══════════════════════════════════════════════════════════
        JPanel topControls = new JPanel();
        topControls.setLayout(new BoxLayout(topControls, BoxLayout.Y_AXIS));
        topControls.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Row 1: Site selector
        JPanel sitePanel = new JPanel(new BorderLayout(4, 0));
        sitePanel.add(new JLabel("Wiki:"), BorderLayout.WEST);
        siteSelector = new JComboBox<WikiSiteDescriptor>();
        for (WikiSiteDescriptor site : service.listSites()) {
            siteSelector.addItem(site);
        }
        sitePanel.add(siteSelector, BorderLayout.CENTER);
        sitePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        topControls.add(sitePanel);
        topControls.add(Box.createVerticalStrut(4));

        // Row 2: Search
        JPanel searchPanel = new JPanel(new BorderLayout(4, 0));
        searchPanel.add(new JLabel("🔍"), BorderLayout.WEST);
        searchField = new JTextField();
        searchField.setToolTipText("Wiki durchsuchen (Enter)");
        searchField.addActionListener(e -> searchWiki());
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        topControls.add(searchPanel);

        // ═══════════════════════════════════════════════════════════
        //  Results table
        // ═══════════════════════════════════════════════════════════
        resultModel = new WikiResultTableModel();
        resultTable = new JTable(resultModel);
        resultTable.setRowHeight(22);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(300);

        resultSorter = new TableRowSorter<WikiResultTableModel>(resultModel);
        resultTable.setRowSorter(resultSorter);

        // Single-click → preview
        resultTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int viewRow = resultTable.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = resultTable.convertRowIndexToModel(viewRow);
                    String title = resultModel.getTitleAt(modelRow);
                    loadPreview(title);
                }
            }
        });

        // Double-click → open as tab
        resultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedAsTab();
                }
            }
        });

        // Enter → open as tab
        resultTable.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openWikiTab");
        resultTable.getActionMap().put("openWikiTab", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSelectedAsTab();
            }
        });

        JScrollPane resultScroll = new JScrollPane(resultTable);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Suchergebnisse"));

        // Result filter bar
        resultFilterField = new JTextField();
        resultFilterField.setToolTipText("Ergebnisse filtern (Regex)");
        resultFilterField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyResultFilter(); }
            public void removeUpdate(DocumentEvent e) { applyResultFilter(); }
            public void changedUpdate(DocumentEvent e) { applyResultFilter(); }
        });
        JPanel resultFilterBar = new JPanel(new BorderLayout(2, 0));
        resultFilterBar.add(new JLabel(" 🔎 "), BorderLayout.WEST);
        resultFilterBar.add(resultFilterField, BorderLayout.CENTER);

        // Top half: controls + results (no filter bar here)
        JPanel topHalf = new JPanel(new BorderLayout(0, 0));
        topHalf.add(topControls, BorderLayout.NORTH);
        topHalf.add(resultScroll, BorderLayout.CENTER);

        // ═══════════════════════════════════════════════════════════
        //  HTML preview pane
        // ═══════════════════════════════════════════════════════════
        htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        htmlPane.setContentType("text/html");
        htmlPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                handleLink(e);
            }
        });
        JScrollPane htmlScroll = new JScrollPane(htmlPane);
        htmlScroll.setBorder(BorderFactory.createTitledBorder("Vorschau"));

        previewPanel = new JPanel(new BorderLayout(0, 0));
        previewPanel.add(htmlScroll, BorderLayout.CENTER);

        // ═══════════════════════════════════════════════════════════
        //  Main split: top/bottom
        // ═══════════════════════════════════════════════════════════
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topHalf, previewPanel);
        mainSplit.setDividerLocation(300);
        mainSplit.setResizeWeight(0.4);
        mainPanel.add(mainSplit, BorderLayout.CENTER);

        // Filter bar below everything (filters results + positioned below preview)
        JPanel bottomBar = new JPanel(new BorderLayout(0, 0));
        bottomBar.add(resultFilterBar, BorderLayout.CENTER);
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        bottomBar.add(statusLabel, BorderLayout.SOUTH);
        mainPanel.add(bottomBar, BorderLayout.SOUTH);

        if (siteSelector.getItemCount() > 0) {
            siteSelector.setSelectedIndex(0);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Actions
    // ═══════════════════════════════════════════════════════════

    private void loadPreview(String pageTitle) {
        if (pageTitle == null || pageTitle.isEmpty()) return;
        WikiSiteDescriptor site = (WikiSiteDescriptor) siteSelector.getSelectedItem();
        if (site == null) return;

        currentSiteId = site.id();
        currentPageTitle = pageTitle;

        // Check in-memory prefetch cache first (O(1))
        if (prefetchCallback != null) {
            WikiPageView cached = prefetchCallback.getCached(site.id(), pageTitle);
            if (cached != null) {
                applyPreview(cached);
                triggerPrefetchFromCursor();
                return;
            }
        }

        statusLabel.setText("⏳ Lade Vorschau: " + pageTitle + "…");

        // Use dedicated priority thread if available, otherwise plain SwingWorker
        new SwingWorker<WikiPageView, Void>() {
            @Override
            protected WikiPageView doInBackground() throws Exception {
                if (prefetchCallback != null) {
                    // loadPriority uses its own dedicated thread, never blocked by prefetch pool
                    WikiPageView view = prefetchCallback.loadPriority(site.id(), pageTitle);
                    if (view != null) return view;
                }
                // Fallback: direct load
                WikiCredentials creds = getCredentials(site);
                return service.loadPage(site.id(), pageTitle, creds);
            }

            @Override
            protected void done() {
                try {
                    WikiPageView view = get();
                    if (view != null) {
                        applyPreview(view);
                        triggerPrefetchFromCursor();
                    } else {
                        statusLabel.setText("❌ Seite konnte nicht geladen werden");
                    }
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "[Wiki] Failed to load preview: " + pageTitle, ex);
                    htmlPane.setText("<html><body><h2>Fehler</h2><p>" + escHtml(getRootMessage(ex)) + "</p></body></html>");
                    statusLabel.setText("❌ Fehler: " + getRootMessage(ex));
                }
            }
        }.execute();
    }

    /**
     * Trigger prefetch of pages ahead of the currently selected row.
     */
    private void triggerPrefetchFromCursor() {
        if (prefetchCallback == null) return;
        WikiSiteDescriptor site = (WikiSiteDescriptor) siteSelector.getSelectedItem();
        if (site == null) return;

        int viewRow = resultTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = resultTable.convertRowIndexToModel(viewRow);

        // Collect all current result titles
        java.util.List<String> allTitles = resultModel.getAllTitles();
        if (!allTitles.isEmpty()) {
            prefetchCallback.prefetchSearchResults(site.id(), allTitles, modelRow);
        }
    }

    /** Apply a loaded WikiPageView to the preview pane and outline. */
    private void applyPreview(WikiPageView view) {
        currentPreview = view;
        htmlPane.setText(wrapHtml(view.cleanedHtml()));
        htmlPane.setCaretPosition(0);
        statusLabel.setText("✅ Vorschau: " + view.title());

        // Update image strip in preview area
        Component eastComp = ((java.awt.BorderLayout) previewPanel.getLayout())
                .getLayoutComponent(java.awt.BorderLayout.EAST);
        if (eastComp != null) {
            previewPanel.remove(eastComp);
        }
        if (view.images() != null && !view.images().isEmpty()) {
            previewPanel.add(new ImageStripPanel(view.images()), java.awt.BorderLayout.EAST);
        }
        previewPanel.revalidate();
        previewPanel.repaint();

        if (outlineCallback != null) {
            outlineCallback.onOutlineChanged(view.outline(), view.title());
        }
    }

    private void openSelectedAsTab() {
        int viewRow = resultTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = resultTable.convertRowIndexToModel(viewRow);
        String title = resultModel.getTitleAt(modelRow);
        if (title == null || title.isEmpty()) return;

        WikiSiteDescriptor site = (WikiSiteDescriptor) siteSelector.getSelectedItem();
        if (site == null || openCallback == null) return;

        // 1) Use current preview if it matches (already loaded)
        if (currentPreview != null && title.equals(currentPreview.title())) {
            openCallback.openWikiPage(site.id().value(), title, currentPreview.cleanedHtml(),
                    currentPreview.outline(), currentPreview.images());
            return;
        }

        // 2) Use prefetch cache or priority thread
        statusLabel.setText("⏳ Öffne: " + title + "…");
        new SwingWorker<WikiPageView, Void>() {
            @Override
            protected WikiPageView doInBackground() throws Exception {
                if (prefetchCallback != null) {
                    WikiPageView view = prefetchCallback.loadPriority(site.id(), title);
                    if (view != null) return view;
                }
                return service.loadPage(site.id(), title, getCredentials(site));
            }

            @Override
            protected void done() {
                try {
                    WikiPageView view = get();
                    if (view != null && openCallback != null) {
                        openCallback.openWikiPage(site.id().value(), view.title(),
                                view.cleanedHtml(), view.outline(), view.images());
                        statusLabel.setText("✅ Geöffnet: " + view.title());
                    }
                } catch (Exception ex) {
                    statusLabel.setText("❌ Fehler: " + getRootMessage(ex));
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
        resultModel.clear();

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                WikiCredentials creds = getCredentials(site);
                return service.searchPages(site.id(), query, creds, 30);
            }

            @Override
            protected void done() {
                try {
                    List<String> results = get();
                    resultModel.setResults(results);
                    statusLabel.setText(results.size() + " Ergebnisse für \"" + query + "\"");

                    if (!results.isEmpty() && resultTable.getRowCount() > 0) {
                        resultTable.setRowSelectionInterval(0, 0);
                    }

                    // Prefetch results into local cache in the background
                    if (prefetchCallback != null && !results.isEmpty()) {
                        prefetchCallback.prefetchSearchResults(site.id(), results);
                    }
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "[Wiki] Search failed", ex);
                    statusLabel.setText("❌ Suche fehlgeschlagen: " + getRootMessage(ex));
                }
            }
        }.execute();
    }

    private void handleLink(HyperlinkEvent e) {
        String desc = e.getDescription();
        if (desc != null && desc.startsWith("#")) {
            htmlPane.scrollToReference(desc.substring(1));
            return;
        }

        String url = e.getURL() != null ? e.getURL().toString() : desc;
        if (url == null) return;

        String pageTitle = extractPageTitle(url);
        if (pageTitle != null) {
            WikiSiteDescriptor site = (WikiSiteDescriptor) siteSelector.getSelectedItem();
            if (site != null && openCallback != null) {
                statusLabel.setText("⏳ Öffne: " + pageTitle + "…");
                new SwingWorker<WikiPageView, Void>() {
                    @Override
                    protected WikiPageView doInBackground() throws Exception {
                        return service.loadPage(site.id(), pageTitle, getCredentials(site));
                    }

                    @Override
                    protected void done() {
                        try {
                            WikiPageView view = get();
                            openCallback.openWikiPage(site.id().value(), view.title(),
                                    view.cleanedHtml(), view.outline(), view.images());
                            statusLabel.setText("✅ Geöffnet: " + view.title());
                        } catch (Exception ex) {
                            statusLabel.setText("❌ Fehler: " + getRootMessage(ex));
                        }
                    }
                }.execute();
            }
        } else {
            try {
                Desktop.getDesktop().browse(e.getURL().toURI());
            } catch (Exception ex) {
                LOG.log(Level.FINE, "[Wiki] Could not open external link", ex);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Filter logic
    // ═══════════════════════════════════════════════════════════

    private void applyResultFilter() {
        String regex = resultFilterField.getText().trim();
        if (regex.isEmpty()) {
            resultSorter.setRowFilter(null);
            resultFilterField.setBackground(UIManager.getColor("TextField.background"));
        } else {
            try {
                resultSorter.setRowFilter(RowFilter.regexFilter("(?i)" + regex, 0));
                resultFilterField.setBackground(UIManager.getColor("TextField.background"));
            } catch (Exception ex) {
                resultFilterField.setBackground(new Color(255, 200, 200));
            }
        }
    }


    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    static String extractPageTitle(String url) {
        int wikiIdx = url.indexOf("/wiki/");
        if (wikiIdx >= 0) {
            String raw = url.substring(wikiIdx + 6);
            int hashIdx = raw.indexOf('#');
            if (hashIdx >= 0) raw = raw.substring(0, hashIdx);
            int queryIdx = raw.indexOf('?');
            if (queryIdx >= 0) raw = raw.substring(0, queryIdx);
            return decodeUrl(raw.replace('_', ' '));
        }
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

    private WikiCredentials getCredentials(WikiSiteDescriptor site) {
        if (!site.requiresLogin()) {
            LOG.fine("[Wiki] getCredentials: site '" + site.displayName() + "' does not require login");
            return WikiCredentials.anonymous();
        }
        if (credentialsCallback != null) {
            WikiCredentials creds = credentialsCallback.getCredentials(site.id());
            LOG.fine("[Wiki] getCredentials: callback returned " + (creds == null ? "null" : (creds.isAnonymous() ? "anonymous" : "user='" + creds.username() + "'")));
            if (creds != null && !creds.isAnonymous()) return creds;
        } else {
            LOG.warning("[Wiki] getCredentials: credentialsCallback is NULL for site '" + site.displayName() + "'");
        }

        // No credentials stored – prompt the user
        LOG.info("[Wiki] getCredentials: no credentials available, prompting user for site '" + site.displayName() + "'");
        final WikiCredentials[] result = new WikiCredentials[]{null};
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                javax.swing.JTextField userField = new javax.swing.JTextField(20);
                javax.swing.JPasswordField passField = new javax.swing.JPasswordField(20);
                javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.GridBagLayout());
                java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
                gbc.insets = new java.awt.Insets(4, 4, 4, 4);
                gbc.anchor = java.awt.GridBagConstraints.WEST;

                gbc.gridx = 0; gbc.gridy = 0;
                panel.add(new javax.swing.JLabel("Wiki:"), gbc);
                gbc.gridx = 1;
                panel.add(new javax.swing.JLabel(site.displayName()), gbc);

                gbc.gridx = 0; gbc.gridy = 1;
                panel.add(new javax.swing.JLabel("Benutzername:"), gbc);
                gbc.gridx = 1; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
                panel.add(userField, gbc);

                gbc.gridx = 0; gbc.gridy = 2; gbc.fill = java.awt.GridBagConstraints.NONE; gbc.weightx = 0;
                panel.add(new javax.swing.JLabel("Passwort:"), gbc);
                gbc.gridx = 1; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
                panel.add(passField, gbc);

                int choice = javax.swing.JOptionPane.showConfirmDialog(
                        mainPanel, panel,
                        "Wiki-Login: " + site.displayName(),
                        javax.swing.JOptionPane.OK_CANCEL_OPTION,
                        javax.swing.JOptionPane.PLAIN_MESSAGE);

                if (choice == javax.swing.JOptionPane.OK_OPTION) {
                    String user = userField.getText().trim();
                    String pass = new String(passField.getPassword());
                    if (!user.isEmpty()) {
                        result[0] = new WikiCredentials(user, pass.toCharArray());
                        // Save to settings for next time
                        if (credentialsSaveCallback != null) {
                            credentialsSaveCallback.saveCredentials(site.id(), user, pass);
                        }
                    }
                }
            });
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Wiki] Credentials prompt failed", e);
        }

        if (result[0] != null) {
            return result[0];
        }
        LOG.warning("[Wiki] getCredentials: user cancelled or empty – falling back to anonymous");
        return WikiCredentials.anonymous();
    }

    static String wrapHtml(String bodyHtml) {
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

    static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String getRootMessage(Exception ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    public WikiContentService getService() { return service; }

    // ═══════════════════════════════════════════════════════════
    //  ConnectionTab interface
    // ═══════════════════════════════════════════════════════════

    @Override public String getTitle() { return "📖 Wiki"; }
    @Override public String getTooltip() { return "MediaWiki-Seiten durchsuchen und anzeigen"; }
    @Override public JComponent getComponent() { return mainPanel; }
    @Override public void onClose() {
        if (prefetchCallback != null) {
            prefetchCallback.shutdown();
        }
    }
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
    //  Callbacks
    // ═══════════════════════════════════════════════════════════

    public void setOpenCallback(OpenCallback callback) {
        this.openCallback = callback;
    }

    public void setOutlineCallback(OutlineCallback callback) {
        this.outlineCallback = callback;
    }

    public void setPrefetchCallback(WikiPrefetchCallback callback) {
        this.prefetchCallback = callback;
    }

    /**
     * Open a wiki page externally (e.g. from a relation entry in the LeftDrawer).
     * Uses the priority thread if prefetch is available, otherwise loads directly.
     */
    public void openPageExternally(String siteIdStr, String pageTitle) {
        if (openCallback == null) return;

        WikiSiteDescriptor site = findSiteById(siteIdStr);
        if (site == null && siteSelector.getItemCount() > 0) {
            site = (WikiSiteDescriptor) siteSelector.getItemAt(0);
        }
        if (site == null) return;

        final WikiSiteDescriptor targetSite = site;

        // Check prefetch cache first
        if (prefetchCallback != null) {
            WikiPageView cached = prefetchCallback.getCached(targetSite.id(), pageTitle);
            if (cached != null) {
                openCallback.openWikiPage(targetSite.id().value(), cached.title(),
                        cached.cleanedHtml(), cached.outline(), cached.images());
                return;
            }
        }

        // Load in background
        statusLabel.setText("⏳ Öffne: " + pageTitle + "…");
        new SwingWorker<WikiPageView, Void>() {
            @Override
            protected WikiPageView doInBackground() throws Exception {
                if (prefetchCallback != null) {
                    WikiPageView v = prefetchCallback.loadPriority(targetSite.id(), pageTitle);
                    if (v != null) return v;
                }
                return service.loadPage(targetSite.id(), pageTitle, getCredentials(targetSite));
            }

            @Override
            protected void done() {
                try {
                    WikiPageView view = get();
                    if (view != null && openCallback != null) {
                        openCallback.openWikiPage(targetSite.id().value(), view.title(),
                                view.cleanedHtml(), view.outline(), view.images());
                        statusLabel.setText("✅ Geöffnet: " + view.title());
                    }
                } catch (Exception ex) {
                    statusLabel.setText("❌ Fehler: " + getRootMessage(ex));
                }
            }
        }.execute();
    }

    private WikiSiteDescriptor findSiteById(String siteIdStr) {
        for (int i = 0; i < siteSelector.getItemCount(); i++) {
            WikiSiteDescriptor s = (WikiSiteDescriptor) siteSelector.getItemAt(i);
            if (s.id().value().equals(siteIdStr)) {
                return s;
            }
        }
        return null;
    }

    public interface OpenCallback {
        void openWikiPage(String siteId, String pageTitle, String htmlContent,
                          OutlineNode outline, java.util.List<de.bund.zrb.wiki.domain.ImageRef> images);
    }

    public interface OutlineCallback {
        void onOutlineChanged(OutlineNode outline, String pageTitle);
    }

    public interface CredentialsCallback {
        /** Return credentials for the given wiki site, or null if unavailable. */
        WikiCredentials getCredentials(WikiSiteId siteId);
    }

    /** Callback to persist credentials entered via the login prompt dialog. */
    public interface CredentialsSaveCallback {
        void saveCredentials(WikiSiteId siteId, String username, String password);
    }

    public void setCredentialsCallback(CredentialsCallback callback) {
        this.credentialsCallback = callback;
    }

    public void setCredentialsSaveCallback(CredentialsSaveCallback callback) {
        this.credentialsSaveCallback = callback;
    }

    // ═══════════════════════════════════════════════════════════
    //  Result Table Model
    // ═══════════════════════════════════════════════════════════

    private static final class WikiResultTableModel extends AbstractTableModel {
        private final List<String> titles = new ArrayList<String>();

        void setResults(List<String> results) {
            titles.clear();
            titles.addAll(results);
            fireTableDataChanged();
        }

        void clear() {
            titles.clear();
            fireTableDataChanged();
        }

        String getTitleAt(int row) {
            return titles.get(row);
        }

        List<String> getAllTitles() {
            return new ArrayList<String>(titles);
        }

        @Override public int getRowCount() { return titles.size(); }
        @Override public int getColumnCount() { return 1; }
        @Override public String getColumnName(int col) { return "Seitentitel"; }

        @Override
        public Object getValueAt(int row, int col) {
            return titles.get(row);
        }
    }
}

