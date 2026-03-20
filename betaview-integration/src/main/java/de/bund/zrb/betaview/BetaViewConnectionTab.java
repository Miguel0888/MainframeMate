package de.bund.zrb.betaview;

import de.zrb.bund.newApi.ui.ConnectionTab;
import de.zrb.bund.newApi.ui.FindBarPanel;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BetaView connection tab that implements ConnectionTab for integration
 * into MainframeMate's TabbedPaneManager.
 *
 * This is the search/filter tab (left side in the original BetaViewSwingFrame).
 * It contains the filter tabs, results table, load/save buttons and a status bar.
 *
 * Document preview is handled by BetaViewDocumentTab instances that are opened
 * as separate tabs in the TabbedPaneManager.
 */
public final class BetaViewConnectionTab implements ConnectionTab {

    // ---- Connection state ----
    private final URL baseUrl;
    private final BetaViewClient client;
    private final BetaViewSession session;
    private final LoadResultsHtmlUseCase loadResultsHtmlUseCase;
    private final String displayName;

    // ---- TabbedPaneManager for opening document tabs ----
    private final Object tabbedPaneManager; // de.bund.zrb.ui.TabbedPaneManager

    // ---- Filter fields ----
    private final JTextField favoriteIdField = new JTextField();
    private final JTextField localeField = new JTextField();
    private final TimePeriodFilterPanel timePeriodPanel = new TimePeriodFilterPanel();
    private final FormFilterPanel formPanel = new FormFilterPanel();
    private final ExtensionFilterPanel extensionPanel = new ExtensionFilterPanel();
    private final ReportFilterPanel reportPanel = new ReportFilterPanel();
    private final JobNameFilterPanel jobNamePanel = new JobNameFilterPanel();
    private final WildcardFilterPanel folderPanel = new WildcardFilterPanel(12);
    private final WildcardFilterPanel tabPanel = new WildcardFilterPanel(12);
    private final JTextField titleField = new JTextField("", 15);
    private final JCheckBox ftitleCheckBox = new JCheckBox("Exakter Titel");
    private final WildcardFilterPanel recipientPanel = new WildcardFilterPanel(12);
    private final TriStateFilterPanel onlinePanel = new TriStateFilterPanel();
    private final TriStateFilterPanel lgrnotePanel = new TriStateFilterPanel();
    private final TriStateFilterPanel lgrxreadPanel = new TriStateFilterPanel();
    private final TriStateFilterPanel archivePanel = new TriStateFilterPanel();
    private final ComboFilterPanel processPanel = new ComboFilterPanel(new OptionItem[]{
            new OptionItem("", "(Alle)"),
            new OptionItem("LIST", "LIST"),
            new OptionItem("REPORT", "REPORT")
    });
    private final TriStateFilterPanel deletePanel = new TriStateFilterPanel();
    private final ComboFilterPanel lgrstatPanel = new ComboFilterPanel(new OptionItem[]{
            new OptionItem("", "(Alle)"),
            new OptionItem("H", "H – Halten"),
            new OptionItem("C", "C – Abgeschlossen"),
            new OptionItem("N", "N – Neu"),
            new OptionItem("T", "T – Temporär")
    });
    private final TriStateFilterPanel reloadPanel = new TriStateFilterPanel();

    // ---- UI components ----
    private final JPanel mainPanel;
    private final JButton loadButton = new JButton("Load Results");
    private final JButton saveSearchButton = new JButton("Suche speichern");
    private final JButton closeAllTabsButton = new JButton("Alle Tabs schließen");
    private final JLabel statusLabel = new JLabel("Verbunden");
    private final JProgressBar progress = new JProgressBar();
    private final ResultsTablePanel resultsTablePanel = new ResultsTablePanel();
    private final SidebarPanel sidebarPanel = new SidebarPanel();
    private final FindBarPanel searchBar = new FindBarPanel("Ergebnisse filtern…");

    // ---- Document management ----
    private final DocumentTabbedPane documentTabbedPane = new DocumentTabbedPane();

    // ---- Listener for document open (set by BetaViewDocumentTabManager) ----
    private DocumentOpenListener documentOpenListener;

    public BetaViewConnectionTab(URL baseUrl, BetaViewClient client, BetaViewSession session,
                                 LoadResultsHtmlUseCase loadResultsUseCase, String displayName,
                                 BetaViewAppProperties defaults) {
        this.baseUrl = baseUrl;
        this.client = client;
        this.session = session;
        this.loadResultsHtmlUseCase = loadResultsUseCase;
        this.displayName = displayName;
        this.tabbedPaneManager = null;

        this.mainPanel = new JPanel(new BorderLayout(0, 4));
        buildUI(defaults);
    }

    // ======== Public API ========

    BetaViewClient getClient() { return client; }
    BetaViewSession getSession() { return session; }
    public DocumentTabbedPane getDocumentTabbedPane() { return documentTabbedPane; }

    public void setDocumentOpenListener(DocumentOpenListener l) { this.documentOpenListener = l; }

    /**
     * Creates a BetaViewDocumentTabManager wired to this connection tab.
     * Sets it as the document open listener and returns it.
     * This method encapsulates the package-private client/session types.
     */
    public BetaViewDocumentTabManager createAndWireDocumentTabManager(
            BetaViewDocumentTabManager.TabHost tabHost) {
        BetaViewDocumentTabManager docManager = new BetaViewDocumentTabManager(
                client, session, getTitle(), tabHost);
        setDocumentOpenListener(docManager);
        return docManager;
    }

    /** Listener for events that need to open documents in the document preview area. */
    public interface DocumentOpenListener {
        void onOpenDocument(String html, String action);
        void onOpenBookmarkDocument(String html, SidebarPanel.BookmarkItem item);
        void onCloseAllTabs();
    }

    // ======== UI Build ========

    private void buildUI(BetaViewAppProperties defaults) {
        // ---- Top: Filter Tabs ----
        JTabbedPane filterTabs = buildFilterTabs(defaults);

        // ---- Buttons ----
        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        leftBtns.add(loadButton);
        leftBtns.add(saveSearchButton);
        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        rightBtns.add(closeAllTabsButton);
        JPanel btnPanel = new JPanel(new BorderLayout());
        btnPanel.add(leftBtns, BorderLayout.WEST);
        btnPanel.add(rightBtns, BorderLayout.EAST);

        JPanel filterArea = new JPanel(new BorderLayout(0, 2));
        filterArea.add(filterTabs, BorderLayout.CENTER);
        filterArea.add(btnPanel, BorderLayout.SOUTH);

        // ---- Bottom: Results ----
        // ---- Status bar ----
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        progress.setIndeterminate(true);
        progress.setVisible(false);
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(progress, BorderLayout.EAST);

        JPanel resultsArea = new JPanel(new BorderLayout(0, 2));
        resultsArea.add(resultsTablePanel, BorderLayout.CENTER);
        resultsArea.add(statusBar, BorderLayout.SOUTH);

        // ---- Split: filters (top) | results (bottom) ----
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filterArea, resultsArea);
        split.setResizeWeight(0.45);
        mainPanel.add(split, BorderLayout.CENTER);

        // ---- Apply defaults ----
        if (defaults != null) {
            favoriteIdField.setText(nullToEmpty(defaults.favoriteId()));
            localeField.setText(nullToEmpty(defaults.locale()));
            formPanel.applyInitialValue(defaults.form());
            extensionPanel.applyInitialValue(defaults.extension());
            reportPanel.applyInitialValue(defaults.report());
            jobNamePanel.applyInitialValue(defaults.jobName());
            timePeriodPanel.applyInitialValues("last7days", "days", defaults.daysBack());
        }

        // ---- Wiring ----
        loadButton.addActionListener(e -> loadResults());
        saveSearchButton.addActionListener(e -> saveCurrentSearch());
        closeAllTabsButton.addActionListener(e -> {
            if (documentOpenListener != null) documentOpenListener.onCloseAllTabs();
        });

        resultsTablePanel.addSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadDetailForSelectedRow();
            }
        });

        sidebarPanel.setSidebarListener(new SidebarPanel.SidebarListener() {
            @Override public void onSavedSearchSelected(SidebarPanel.SavedItem item) { loadSavedSearch(item); }
            @Override public void onBookmarkSelected(SidebarPanel.BookmarkItem item) { openBookmark(item); }
            @Override public void onRefreshSavedSearches() { refreshSavedSearches(); }
            @Override public void onRefreshBookmarks() { refreshBookmarks(); }
        });
    }

    // ======== Filter tabs ========

    private JTabbedPane buildFilterTabs(BetaViewAppProperties props) {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);

        // Zeit
        JPanel timePanel = new JPanel(new GridBagLayout());
        timePanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        GridBagConstraints tc = gbc(); int tr = 0;
        addRow(timePanel, tc, tr++, "Favorite ID", favoriteIdField);
        addRow(timePanel, tc, tr++, "Locale", localeField);
        addRow(timePanel, tc, tr++, "Zeitraum", timePeriodPanel);
        tc.gridy = tr; tc.weighty = 1.0; tc.gridx = 0;
        timePanel.add(new JLabel(), tc);
        tabs.addTab("Zeit", timePanel);

        // Dokument
        JPanel docPanel = new JPanel(new GridBagLayout());
        docPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        GridBagConstraints dc = gbc(); int dr = 0;
        addRow(docPanel, dc, dr++, "Form", formPanel);
        addRow(docPanel, dc, dr++, "Extension", extensionPanel);
        addRow(docPanel, dc, dr++, "Report", reportPanel);
        addRow(docPanel, dc, dr++, "Jobname", jobNamePanel);
        addRow(docPanel, dc, dr++, "Ordner", folderPanel);
        addRow(docPanel, dc, dr++, "Fach", tabPanel);
        addRow(docPanel, dc, dr++, "Titel", titleField);
        addRow(docPanel, dc, dr++, "", ftitleCheckBox);
        addRow(docPanel, dc, dr++, "Empfänger", recipientPanel);
        dc.gridy = dr; dc.weighty = 1.0; dc.gridx = 0;
        docPanel.add(new JLabel(), dc);
        tabs.addTab("Dokument", docPanel);

        // Status
        JPanel statusPanel = new JPanel(new GridBagLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        GridBagConstraints sc = gbc(); int sr = 0;
        addRow(statusPanel, sc, sr++, "Online", onlinePanel);
        addRow(statusPanel, sc, sr++, "Notiz", lgrnotePanel);
        addRow(statusPanel, sc, sr++, "Gelesen", lgrxreadPanel);
        addRow(statusPanel, sc, sr++, "Archiv", archivePanel);
        addRow(statusPanel, sc, sr++, "Verarbeitung", processPanel);
        addRow(statusPanel, sc, sr++, "Löschen", deletePanel);
        addRow(statusPanel, sc, sr++, "Listenstatus", lgrstatPanel);
        addRow(statusPanel, sc, sr++, "Neu laden", reloadPanel);
        sc.gridy = sr; sc.weighty = 1.0; sc.gridx = 0;
        statusPanel.add(new JLabel(), sc);
        tabs.addTab("Status", statusPanel);

        // Suchen
        tabs.addTab("Suchen", sidebarPanel.getSavedSearchesPanel());

        // Gemerkt
        tabs.addTab("Gemerkt", sidebarPanel.getBookmarksPanel());

        return tabs;
    }

    // ======== Actions ========

    private void loadResults() {
        ResultFilter filter;
        try {
            filter = readFilterFromUi();
        } catch (Exception ex) {
            showError(ex); return;
        }

        setBusy(true);
        statusLabel.setText("Loading results...");

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return loadResultsHtmlUseCase.execute(session, filter);
            }
            @Override protected void done() {
                try {
                    String html = get();
                    resultsTablePanel.loadResults(html);
                    resultsTablePanel.clearSelection();
                    statusLabel.setText("Results loaded");
                } catch (Exception ex) {
                    statusLabel.setText("Load failed");
                    showError(ex);
                } finally { setBusy(false); }
            }
        }.execute();
    }

    private void saveCurrentSearch() {
        setBusy(true);
        statusLabel.setText("Lade Speichern-Dialog...");

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                LinkedHashMap<String, String> form = new LinkedHashMap<>();
                form.put("csrfToken", session.csrfToken().value());
                return client.postFormText(session, "savedSelection.fetchAddDialog.action", form);
            }
            @Override protected void done() {
                try {
                    String dialogHtml = get();
                    Map<String, String> hidden = HiddenInputExtractor.extractHiddenInputs(dialogHtml);

                    JPanel dialogPanel = new JPanel(new GridLayout(2, 2, 4, 4));
                    dialogPanel.add(new JLabel("Name der Suche:"));
                    JTextField nameField = new JTextField();
                    dialogPanel.add(nameField);
                    dialogPanel.add(new JLabel("Beschreibung:"));
                    JTextField descField = new JTextField();
                    dialogPanel.add(descField);

                    int opt = JOptionPane.showConfirmDialog(
                            SwingUtilities.getWindowAncestor(mainPanel),
                            dialogPanel, "Suche speichern", JOptionPane.OK_CANCEL_OPTION);
                    if (opt != JOptionPane.OK_OPTION || nameField.getText().trim().isEmpty()) {
                        statusLabel.setText("Speichern abgebrochen"); setBusy(false); return;
                    }

                    LinkedHashMap<String, String> saveForm = new LinkedHashMap<>();
                    String tokenName = hidden.get("struts.token.name");
                    if (tokenName != null) {
                        saveForm.put("struts.token.name", tokenName);
                        String tokenVal = hidden.get(tokenName);
                        if (tokenVal != null) saveForm.put(tokenName, tokenVal);
                    }
                    saveForm.put("selection_name", nameField.getText().trim());
                    saveForm.put("selection_desc", descField.getText().trim());

                    statusLabel.setText("Speichere Suche...");
                    new SwingWorker<Void, Void>() {
                        @Override protected Void doInBackground() throws Exception {
                            client.postFormText(session, "savedSelection.add.action", saveForm);
                            return null;
                        }
                        @Override protected void done() {
                            try {
                                get();
                                statusLabel.setText("Suche gespeichert: " + nameField.getText().trim());
                                refreshSavedSearches();
                            } catch (Exception ex) {
                                statusLabel.setText("Speichern fehlgeschlagen: " + ex.getMessage());
                            } finally { setBusy(false); }
                        }
                    }.execute();
                } catch (Exception ex) {
                    statusLabel.setText("Speichern fehlgeschlagen: " + ex.getMessage());
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void loadDetailForSelectedRow() {
        String action = resultsTablePanel.getActionForSelectedRow();
        if (action == null || action.isEmpty()) return;

        setBusy(true);
        statusLabel.setText("Opening document...");

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.getText(session, action);
            }
            @Override protected void done() {
                try {
                    String html = get();
                    if (documentOpenListener != null) documentOpenListener.onOpenDocument(html, action);
                    statusLabel.setText("Document opened");
                } catch (Exception ex) {
                    statusLabel.setText("Open failed");
                    showError(ex);
                } finally { setBusy(false); }
            }
        }.execute();
    }

    // ---- Sidebar actions ----

    private void refreshSavedSearches() {
        statusLabel.setText("Lade gespeicherte Suchen...");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                LinkedHashMap<String, String> form = new LinkedHashMap<>();
                form.put("csrfToken", session.csrfToken().value());
                return client.postFormText(session, "savedSelection.list.action", form);
            }
            @Override protected void done() {
                try { sidebarPanel.loadSavedSearches(get()); statusLabel.setText("Gespeicherte Suchen geladen"); }
                catch (Exception ex) { statusLabel.setText("Fehler: " + ex.getMessage()); }
            }
        }.execute();
    }

    private void refreshBookmarks() {
        statusLabel.setText("Lade gemerkte Dokumente...");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                LinkedHashMap<String, String> form = new LinkedHashMap<>();
                form.put("csrfToken", session.csrfToken().value());
                return client.postFormText(session, "bookmarks.list.action", form);
            }
            @Override protected void done() {
                try { sidebarPanel.loadBookmarks(get()); statusLabel.setText("Gemerkte Dokumente geladen"); }
                catch (Exception ex) { statusLabel.setText("Fehler: " + ex.getMessage()); }
            }
        }.execute();
    }

    private void loadSavedSearch(SidebarPanel.SavedItem item) {
        if (item.action().isEmpty()) return;
        setBusy(true);
        statusLabel.setText("Lade gespeicherte Suche: " + item.name() + "...");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                LinkedHashMap<String, String> form = new LinkedHashMap<>();
                if (item.tokenName() != null) {
                    form.put("struts.token.name", item.tokenName());
                    if (item.tokenValue() != null) form.put(item.tokenName(), item.tokenValue());
                }
                if (!item.ssId().isEmpty()) form.put("ssIDForm", item.ssId());

                String html = client.postFormText(session, item.action(), form);

                if (!html.contains("resulttable") && !html.contains("showResult")) {
                    html = client.getText(session, "showResult.action");
                }
                return html;
            }
            @Override protected void done() {
                try {
                    resultsTablePanel.loadResults(get());
                    resultsTablePanel.clearSelection();
                    statusLabel.setText("Gespeicherte Suche geladen: " + item.name());
                    refreshSavedSearches();
                } catch (Exception ex) {
                    statusLabel.setText("Fehler: " + ex.getMessage());
                } finally { setBusy(false); }
            }
        }.execute();
    }

    private void openBookmark(SidebarPanel.BookmarkItem item) {
        if (item.action().isEmpty()) return;
        setBusy(true);
        statusLabel.setText("Öffne Lesezeichen: " + item.name() + "...");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.getText(session, item.action());
            }
            @Override protected void done() {
                try {
                    String html = get();
                    if (documentOpenListener != null) documentOpenListener.onOpenBookmarkDocument(html, item);
                    statusLabel.setText("Lesezeichen geöffnet: " + item.name());
                } catch (Exception ex) {
                    statusLabel.setText("Fehler: " + ex.getMessage());
                } finally { setBusy(false); }
            }
        }.execute();
    }

    // ======== ConnectionTab interface ========

    @Override
    public String getTitle() {
        return "📋 BetaView: " + displayName;
    }

    @Override
    public String getTooltip() {
        return "BetaView: " + displayName + " (" + baseUrl + ")";
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        // No persistent connection to close (HTTP is stateless)
    }

    @Override
    public void saveIfApplicable() {
        // Not applicable for connection tabs
    }

    @Override
    public String getContent() {
        return "";
    }

    @Override
    public void markAsChanged() {
        // Not applicable
    }

    @Override
    public String getPath() {
        return baseUrl != null ? baseUrl.toString() : "";
    }

    @Override
    public Type getType() {
        return Type.CONNECTION;
    }

    @Override
    public void focusSearchField() {
        searchBar.focusAndSelectAll();
    }

    @Override
    public void searchFor(String searchPattern) {
        // Not applicable for BetaView search tab
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        JMenuItem refreshItem = new JMenuItem("🔄 Suchen aktualisieren");
        refreshItem.addActionListener(e -> refreshSavedSearches());

        menu.add(refreshItem);
        menu.addSeparator();
        menu.add(closeItem);

        return menu;
    }

    // ======== Helpers ========

    private ResultFilter readFilterFromUi() {
        return new ResultFilter.Builder()
                .favoriteId(favoriteIdField.getText().trim())
                .locale(localeField.getText().trim())
                .daysBack(timePeriodPanel.value())
                .form(formPanel.value())
                .extensionPattern(extensionPanel.value())
                .report(reportPanel.value())
                .jobName(jobNamePanel.value())
                .lastsel(timePeriodPanel.lastsel())
                .timeunit(timePeriodPanel.timeunit())
                .lastdate(timePeriodPanel.lastdate())
                .datefrom(timePeriodPanel.datefrom())
                .dateto(timePeriodPanel.dateto())
                .folder(folderPanel.value())
                .tab(tabPanel.value())
                .title(titleField.getText().trim())
                .ftitle(ftitleCheckBox.isSelected() ? "1" : "0")
                .recipient(recipientPanel.value())
                .online(onlinePanel.value())
                .lgrnote(lgrnotePanel.value())
                .lgrxread(lgrxreadPanel.value())
                .archive(archivePanel.value())
                .process(processPanel.value())
                .delete(deletePanel.value())
                .lgrstat(lgrstatPanel.value())
                .reload(reloadPanel.value())
                .build();
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        loadButton.setEnabled(!busy);
        saveSearchButton.setEnabled(!busy);
    }

    private void showError(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.trim().isEmpty()) msg = ex.getClass().getName();
        JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(mainPanel), msg, "Fehler", JOptionPane.ERROR_MESSAGE);
    }

    private static GridBagConstraints gbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.0;
        return c;
    }

    private static void addRow(JPanel panel, GridBagConstraints c, int row, String label, Component field) {
        c.gridx = 0; c.gridy = row; c.weightx = 0.0;
        panel.add(new JLabel(label), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1.0;
        panel.add(field, c);
    }

    private static String nullToEmpty(String v) { return v == null ? "" : v; }
}
