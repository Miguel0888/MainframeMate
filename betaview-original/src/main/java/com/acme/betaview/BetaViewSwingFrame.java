package com.acme.betaview;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Main application frame.
 * <p>
 * Layout: single horizontal JSplitPane.
 * <ul>
 *   <li>Left: JTabbedPane with ConnectionTabPanels (one per server connection) + a "+" new-tab button</li>
 *   <li>Right: DocumentTabbedPane (shared document previews)</li>
 * </ul>
 * At startup the left pane is empty except for the "+" tab.  Clicking "+" opens a
 * modal {@link ConnectDialog}.  On success a new {@link ConnectionTabPanel} is added.
 */
public final class BetaViewSwingFrame extends JFrame {

    private final BetaViewAppProperties props;

    // ---- Left: connection tabs ----
    private final JTabbedPane connectionTabs = new JTabbedPane(JTabbedPane.TOP);

    // ---- Right: document previews (shared) ----
    private final DocumentTabbedPane documentTabbedPane = new DocumentTabbedPane();

    // ---- "+" placeholder panel (always last tab) ----
    private final JPanel newTabPlaceholder = new JPanel();

    /** Suppress the ChangeListener while we are programmatically adjusting tabs. */
    private boolean suppressTabChange = false;

    public BetaViewSwingFrame(BetaViewAppProperties props) {
        super("BetaView Client");
        this.props = props;
        configureUi();
    }

    // ================================================================ UI setup

    private void configureUi() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ---- "+" tab ----
        connectionTabs.addTab("+", newTabPlaceholder);
        connectionTabs.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                int idx = connectionTabs.indexAtLocation(e.getX(), e.getY());
                if (idx >= 0 && connectionTabs.getComponentAt(idx) == newTabPlaceholder) {
                    SwingUtilities.invokeLater(() -> openNewConnection());
                }
            }
        });

        // ---- Split pane ----
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        rightPanel.add(documentTabbedPane, BorderLayout.CENTER);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        leftPanel.add(connectionTabs, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        mainSplit.setResizeWeight(0.4);
        add(mainSplit, BorderLayout.CENTER);

        // ---- Wire document-tab events ----
        documentTabbedPane.setTabCloseListener(this::closeTab);
        documentTabbedPane.setTabSelectionListener((tab, panel, loaded) -> {
            if (!loaded) loadTabContent(tab, panel);
        });

        setSize(1600, 900);
        setLocationRelativeTo(null);
    }

    // ================================================================ New connection

    private void openNewConnection() {
        ConnectDialog dlg = new ConnectDialog(this, props);
        dlg.setVisible(true);

        ConnectDialog.ConnectResult result = dlg.getResult();
        if (result == null) return; // cancelled

        ConnectionTabPanel tabPanel = new ConnectionTabPanel(result, props);
        tabPanel.setConnectionListener(new ConnectionTabPanel.ConnectionListener() {
            @Override
            public void onOpenDocument(String html, String action) {
                handleOpenDocument(html, action, tabPanel);
            }
            @Override
            public void onOpenBookmarkDocument(String html, SidebarPanel.BookmarkItem item) {
                handleOpenBookmarkDocument(html, item, tabPanel);
            }
            @Override
            public void onCloseAllTabs() {
                closeAllTabs();
            }
        });

        // Insert before the "+" tab
        suppressTabChange = true;
        int insertIdx = connectionTabs.getTabCount() - 1;
        connectionTabs.insertTab(null, null, tabPanel, result.displayName(), insertIdx);
        connectionTabs.setTabComponentAt(insertIdx, createConnectionTabComponent(result.displayName(), tabPanel));
        connectionTabs.setSelectedIndex(insertIdx);
        suppressTabChange = false;

        // Fetch open server tabs (docbrowser bookmarks) and create lazy Swing tabs
        if (result.openLastTabs()) {
            fetchAndOpenServerTabs(tabPanel);
        }
    }

    /**
     * Calls getDBBookmarksJSON.json.action to get the currently open server-side
     * docbrowser tabs and creates lazy Swing tabs for each one.
     */
    private void fetchAndOpenServerTabs(ConnectionTabPanel connTab) {
        new SwingWorker<List<DocumentTab>, Void>() {
            @Override protected List<DocumentTab> doInBackground() throws Exception {
                BetaViewClient client = connTab.getClient();
                BetaViewSession session = connTab.getSession();

                java.util.LinkedHashMap<String, String> form = new java.util.LinkedHashMap<>();
                form.put("csrfToken", session.csrfToken().value());
                String json = client.postFormText(session,
                        "getDBBookmarksJSON.json.action", form);

                return parseDocBrowserBookmarks(json);
            }
            @Override protected void done() {
                try {
                    List<DocumentTab> tabs = get();
                    for (DocumentTab tab : tabs) {
                        DocumentPreviewPanel panel = documentTabbedPane.addTabInBackground(tab);
                        setupPanelListeners(panel, tab.key(), connTab);
                        // NOT calling setTabLoaded → stays unloaded → lazy
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    /**
     * Parse the JSON from getDBBookmarksJSON.json.action and extract docbrowser tabs.
     * Manual parsing to avoid adding a JSON library dependency.
     */
    private static List<DocumentTab> parseDocBrowserBookmarks(String json) {
        List<DocumentTab> result = new java.util.ArrayList<>();
        if (json == null || json.isEmpty()) return result;

        // Split into individual bookmark objects by finding each {...} block
        // inside "dbbookmarks":[...]
        int arrStart = json.indexOf("\"dbbookmarks\":[");
        if (arrStart < 0) return result;
        arrStart = json.indexOf('[', arrStart);
        int arrEnd = json.indexOf(']', arrStart);
        if (arrEnd < 0) return result;
        String arrContent = json.substring(arrStart + 1, arrEnd);

        // Split on "},{" to get individual objects
        String[] objects = arrContent.split("\\},\\s*\\{");
        for (String obj : objects) {
            obj = obj.trim();
            if (obj.startsWith("{")) obj = obj.substring(1);
            if (obj.endsWith("}")) obj = obj.substring(0, obj.length() - 1);

            String fav = extractJsonString(obj, "fav");
            if (!"<docbrowsertab>".equals(fav)) continue;

            String link   = extractJsonString(obj, "link");
            String linkID = extractJsonString(obj, "linkID");
            String name   = extractJsonString(obj, "name");
            String desc   = extractJsonString(obj, "description");

            // Extract docid and favid from link
            String docId = "", favId = "";
            for (String part : link.split("&")) {
                if (part.startsWith("docid=")) docId = part.substring(6);
                else if (part.startsWith("favid=")) favId = part.substring(6);
            }

            String openAction = "opendocumentlink.action?" + link;
            result.add(new DocumentTab(docId, favId, linkID, name, desc, openAction, false));
        }
        return result;
    }

    private static String extractJsonString(String obj, String key) {
        String search = "\"" + key + "\":\"";
        int start = obj.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = obj.indexOf('"', start);
        return end > start ? obj.substring(start, end) : "";
    }

    private JPanel createConnectionTabComponent(String title, ConnectionTabPanel panel) {
        JPanel comp = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        comp.setOpaque(false);
        comp.add(new JLabel(title));

        JButton closeBtn = new JButton("\u00D7");
        closeBtn.setPreferredSize(new Dimension(20, 20));
        closeBtn.setMargin(new Insets(0, 0, 0, 0));
        closeBtn.setFocusable(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.addActionListener(e -> {
            int idx = connectionTabs.indexOfComponent(panel);
            if (idx >= 0) {
                suppressTabChange = true;
                connectionTabs.removeTabAt(idx);
                suppressTabChange = false;
                if (connectionTabs.getTabCount() > 1) {
                    connectionTabs.setSelectedIndex(connectionTabs.getTabCount() - 2);
                }
            }
        });
        comp.add(closeBtn);
        return comp;
    }

    // ================================================================ Handle open document

    private void handleOpenDocument(String html, String action, ConnectionTabPanel connTab) {
        List<DocumentTab> tabs = DocumentTabParser.parse(html);

        // Add ALL tabs from the server to the Swing tabbed pane
        DocumentTab activeTab = null;
        for (DocumentTab t : tabs) {
            documentTabbedPane.addOrSelectTab(t);
            if (t.isActive()) activeTab = t;
        }
        if (activeTab == null) {
            activeTab = new DocumentTab("", "", action, "Dokument", "", action, true);
            documentTabbedPane.addOrSelectTab(activeTab);
        }

        // Load content for the active tab
        DocumentPreviewPanel panel = documentTabbedPane.addOrSelectTab(activeTab);
        setupPanelListeners(panel, activeTab.key(), connTab);
        panel.loadDocument(html);
        documentTabbedPane.setTabLoaded(activeTab.key());
    }

    private void handleOpenBookmarkDocument(String html, SidebarPanel.BookmarkItem item,
                                            ConnectionTabPanel connTab) {
        List<DocumentTab> tabs = DocumentTabParser.parse(html);

        // Add ALL tabs from the server to the Swing tabbed pane
        DocumentTab activeTab = null;
        for (DocumentTab t : tabs) {
            documentTabbedPane.addOrSelectTab(t);
            if (t.isActive()) activeTab = t;
        }
        if (activeTab == null) {
            activeTab = new DocumentTab("", "", item.action(), item.name(), "", item.action(), true);
            documentTabbedPane.addOrSelectTab(activeTab);
        }

        // Load content for the active tab
        DocumentPreviewPanel panel = documentTabbedPane.addOrSelectTab(activeTab);
        setupPanelListeners(panel, activeTab.key(), connTab);
        panel.loadDocument(html);
        documentTabbedPane.setTabLoaded(activeTab.key());
    }

    // ================================================================ Document preview actions
    // Each method captures the ConnectionTabPanel that opened the document so it always
    // uses the correct client/session, even if the user switches connection tabs.

    private void setupPanelListeners(DocumentPreviewPanel panel, String tabKey,
                                     ConnectionTabPanel conn) {
        BetaViewClient client = conn.getClient();
        BetaViewSession session = conn.getSession();

        panel.setPageChangeListener(page -> loadPage(panel, client, session, page));
        panel.setDownloadListener(() -> downloadDocument(panel, client, session));
        panel.setDownloadOptionsListener(() -> downloadWithOptions(panel, client, session));
        panel.setBookmarkListener(() -> bookmarkDocument(panel, client, session));
        panel.setPrintListener(() -> showPrintInfo(panel, client, session));
        panel.setDocInfoListener(() -> showDocumentInfo(panel, client, session));
        panel.setNotesRefreshListener(() -> loadNotes(panel, client, session));
        panel.setMarksRefreshListener(() -> loadMarkedPages(panel, client, session));
        panel.setFulltextSearchListener(q -> doFulltextSearch(panel, client, session, q));
        panel.setRefreshListener(() -> {
            DocumentTab tab = documentTabbedPane.getSelectedTab();
            if (tab != null) {
                documentTabbedPane.setTabNotLoaded(tab.key());
                loadTabContent(tab, panel);
            }
        });
        panel.setTabsRefreshListener(() -> {
            java.util.List<DocumentTab> openTabs = new java.util.ArrayList<>();
            for (String k : documentTabbedPane.allKeys()) {
                DocumentTab t = documentTabbedPane.getTab(k);
                if (t != null) openTabs.add(t);
            }
            panel.setOpenTabs(openTabs);
        });
    }

    // ---- Tab close ----

    private void closeTab(DocumentTab tab, String key) {
        ConnectionTabPanel conn = getActiveConnectionTab();
        if (conn != null && tab.linkID() != null && !tab.linkID().isEmpty()) {
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    conn.getClient().getText(conn.getSession(),
                            "closeSingleDocument.action?linkID=" + tab.linkID());
                    return null;
                }
                @Override protected void done() {
                    try { get(); } catch (Exception ignored) {}
                    documentTabbedPane.removeTab(key);
                }
            }.execute();
        } else {
            documentTabbedPane.removeTab(key);
        }
    }

    private void closeAllTabs() {
        ConnectionTabPanel conn = getActiveConnectionTab();
        if (conn != null) {
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    conn.getClient().getText(conn.getSession(), "closeAllDocuments.action");
                    return null;
                }
                @Override protected void done() {
                    try { get(); } catch (Exception ignored) {}
                    documentTabbedPane.removeAllTabs();
                }
            }.execute();
        } else {
            documentTabbedPane.removeAllTabs();
        }
    }

    // ---- Tab lazy load ----

    private void loadTabContent(DocumentTab tab, DocumentPreviewPanel targetPanel) {
        ConnectionTabPanel conn = getActiveConnectionTab();
        if (conn == null) return;
        String action = tab.openAction();
        if (action == null || action.isEmpty()) return;

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return conn.getClient().getText(conn.getSession(), action);
            }
            @Override protected void done() {
                try {
                    String html = get();
                    setupPanelListeners(targetPanel, tab.key(), conn);
                    targetPanel.loadDocument(html);
                    documentTabbedPane.setTabLoaded(tab.key());
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    // ---- Page loading ----

    private void loadPage(DocumentPreviewPanel panel, BetaViewClient client,
                          BetaViewSession session, int page) {
        panel.showPreviewMessage("Lade Seite " + page + " ...");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                // Step 1: Tell the server to navigate to the target page
                // (directItem is 1-based, same as UI page number)
                client.postFormText(session,
                        "document.viewer.navigation.action?directItem=" + page,
                        new java.util.LinkedHashMap<>());

                // Step 2: Fetch the page content (pageIndex is 0-based)
                java.util.LinkedHashMap<String, String> form = new java.util.LinkedHashMap<>();
                form.put("pageIndex", String.valueOf(page - 1));
                return parsePageText(client.postFormText(session, "document.page.get.action", form));
            }
            @Override protected void done() {
                try { panel.showPreviewText(get()); }
                catch (Exception ex) { panel.showPreviewMessage("Fehler: " + ex.getMessage()); }
            }
        }.execute();
    }

    // ---- Download ----

    /**
     * Quick download (all pages, original format) – mirrors the "Herunterladen" button.
     * Flow: permission check → open dialog → submit with printAllPages+ORIG.
     */
    private void downloadDocument(DocumentPreviewPanel panel, BetaViewClient client,
                                  BetaViewSession session) {
        new SwingWorker<DownloadResult, Void>() {
            @Override protected DownloadResult doInBackground() throws Exception {
                return new DownloadDocumentUseCase(client)
                        .execute(session, DownloadDocumentUseCase.PageSelection.ALL_PAGES);
            }
            @Override protected void done() {
                try {
                    DownloadResult r = get();
                    saveDownloadResult(r);
                } catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    /**
     * Download with options – mirrors the "Herunterladen mit Optionen" dialog.
     * Fetches the dialog HTML from the server, parses all hidden fields, shows a
     * Swing dialog with page-range / format / notes options, validates, then downloads.
     */
    private void downloadWithOptions(DocumentPreviewPanel panel, BetaViewClient client,
                                     BetaViewSession session) {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.postFormText(session,
                        "downloadDialog.action?&init=yes&source=docbrowser", new java.util.LinkedHashMap<>());
            }
            @Override protected void done() {
                try {
                    String dialogHtml = get();
                    java.util.Map<String, String> hidden = HiddenInputExtractor.extractHiddenInputs(dialogHtml);

                    // ---- Build options dialog ----
                    JPanel dp = new JPanel(new java.awt.GridBagLayout());
                    GridBagConstraints gc = new GridBagConstraints();
                    gc.insets = new Insets(3, 6, 3, 6);
                    gc.anchor = GridBagConstraints.WEST;
                    gc.fill = GridBagConstraints.HORIZONTAL;
                    int row = 0;

                    // -- Page range --
                    gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
                    dp.add(new JLabel("── Seitenbereich ──"), gc);
                    gc.gridwidth = 1;
                    row++;

                    String totalPages = hidden.getOrDefault("pages", "1");
                    String[] pageOptions = {"Alle Seiten", "Aktuelle Seite", "Seitenbereich"};
                    JComboBox<String> pageRangeCombo = new JComboBox<>(pageOptions);
                    gc.gridx = 0; gc.gridy = row;
                    dp.add(new JLabel("Seiten:"), gc);
                    gc.gridx = 1;
                    dp.add(pageRangeCombo, gc);
                    row++;

                    JTextField rangeFrom = new JTextField("1", 5);
                    JTextField rangeTo = new JTextField(totalPages, 5);
                    JPanel rangePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                    rangePanel.add(new JLabel("von:"));
                    rangePanel.add(rangeFrom);
                    rangePanel.add(new JLabel("bis:"));
                    rangePanel.add(rangeTo);
                    rangePanel.setVisible(false);
                    gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
                    dp.add(rangePanel, gc);
                    gc.gridwidth = 1;
                    row++;

                    pageRangeCombo.addActionListener(ev ->
                            rangePanel.setVisible("Seitenbereich".equals(pageRangeCombo.getSelectedItem())));

                    // -- Format --
                    gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
                    dp.add(new JLabel("── Format ──"), gc);
                    gc.gridwidth = 1;
                    row++;

                    boolean csvEnabled = "true".equals(hidden.getOrDefault("csvEnabled", "false"));
                    java.util.List<String> fmtList = new java.util.ArrayList<>();
                    fmtList.add("Original");
                    if (csvEnabled) fmtList.add("CSV");
                    JComboBox<String> formatCombo = new JComboBox<>(fmtList.toArray(new String[0]));
                    gc.gridx = 0; gc.gridy = row;
                    dp.add(new JLabel("Format:"), gc);
                    gc.gridx = 1;
                    dp.add(formatCombo, gc);
                    row++;

                    // CSV options (hidden by default)
                    String[] separators = {", (Komma)", "; (Semikolon)", "Tabulator"};
                    String[] sepValues = {"comma", "semicolon", "tab"};
                    JComboBox<String> sepCombo = new JComboBox<>(separators);
                    String[] csvModes = {"Nach x Zeichen", "Als Ersatz für", "Variabel"};
                    String[] csvModeValues = {"csv_int", "csv_char", "csv_flex"};
                    JComboBox<String> csvModeCombo = new JComboBox<>(csvModes);
                    JTextField csvInput = new JTextField("10", 8);
                    JCheckBox csvTrim = new JCheckBox("Leerzeichen trimmen", false);

                    JPanel csvPanel = new JPanel(new java.awt.GridBagLayout());
                    GridBagConstraints cc = new GridBagConstraints();
                    cc.insets = new Insets(2, 4, 2, 4);
                    cc.anchor = GridBagConstraints.WEST;
                    cc.fill = GridBagConstraints.HORIZONTAL;
                    int cr = 0;
                    cc.gridx = 0; cc.gridy = cr; csvPanel.add(new JLabel("Separator:"), cc);
                    cc.gridx = 1; csvPanel.add(sepCombo, cc); cr++;
                    cc.gridx = 0; cc.gridy = cr; csvPanel.add(new JLabel("Trennstelle:"), cc);
                    cc.gridx = 1; csvPanel.add(csvModeCombo, cc); cr++;
                    cc.gridx = 0; cc.gridy = cr; csvPanel.add(new JLabel("Wert:"), cc);
                    cc.gridx = 1; csvPanel.add(csvInput, cc); cr++;
                    cc.gridx = 0; cc.gridy = cr; cc.gridwidth = 2; csvPanel.add(csvTrim, cc);
                    csvPanel.setVisible(false);

                    gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
                    dp.add(csvPanel, gc);
                    gc.gridwidth = 1;
                    row++;

                    formatCombo.addActionListener(ev ->
                            csvPanel.setVisible("CSV".equals(formatCombo.getSelectedItem())));

                    // -- Notes --
                    gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
                    dp.add(new JLabel("── Notizen ──"), gc);
                    gc.gridwidth = 1;
                    row++;

                    String[] noteOptions = {"Nur Text", "Nur Notizen", "Beides"};
                    String[] noteValues = {"printTextOnly", "printAntsOnly", "printWithAnts"};
                    JComboBox<String> notesCombo = new JComboBox<>(noteOptions);
                    gc.gridx = 0; gc.gridy = row;
                    dp.add(new JLabel("Inhalt:"), gc);
                    gc.gridx = 1;
                    dp.add(notesCombo, gc);
                    row++;

                    String[] noteTypeOptions = {"Alle Notizen", "Nur öffentliche", "Nur private"};
                    String[] noteTypeValues = {"all", "public", "private"};
                    JComboBox<String> noteTypeCombo = new JComboBox<>(noteTypeOptions);
                    gc.gridx = 0; gc.gridy = row;
                    dp.add(new JLabel("Art:"), gc);
                    gc.gridx = 1;
                    dp.add(noteTypeCombo, gc);

                    // ---- Show dialog (scrollable, capped size) ----
                    JScrollPane scrollPane = new JScrollPane(dp);
                    scrollPane.setBorder(null);
                    scrollPane.getVerticalScrollBar().setUnitIncrement(16);
                    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                    int maxH = (int) (screen.height * 0.7);
                    Dimension pref = dp.getPreferredSize();
                    scrollPane.setPreferredSize(new Dimension(
                            Math.min(pref.width + 30, 600),
                            Math.min(pref.height + 10, maxH)));

                    int opt = JOptionPane.showConfirmDialog(BetaViewSwingFrame.this, scrollPane,
                            "Herunterladen mit Optionen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                    if (opt != JOptionPane.OK_OPTION) return;

                    // ---- Build form ----
                    java.util.LinkedHashMap<String, String> form = new java.util.LinkedHashMap<>();

                    // Struts token
                    String tokenName = hidden.get("struts.token.name");
                    if (tokenName != null) {
                        form.put("struts.token.name", tokenName);
                        String tokenVal = hidden.get(tokenName);
                        if (tokenVal != null) form.put(tokenName, tokenVal);
                    }

                    // All hidden fields from the dialog
                    for (String key : new String[]{"function", "product", "source", "pages",
                            "docLink", "convert", "convertEnabled", "csvEnabled", "doctype",
                            "jobinfo", "textenco", "bss_ukey", "idxstatus", "hasHiddenRanges",
                            "dbinst", "docid", "lineColumnRanges", "index"}) {
                        if (hidden.containsKey(key)) form.put(key, hidden.get(key));
                    }

                    // Page selection
                    form.put("markedPages", "");
                    form.put("suffix", "");
                    form.put("rangePrintStyle", "");
                    int pageIdx = pageRangeCombo.getSelectedIndex();
                    String printStyle = pageIdx == 1 ? "printCurrentPage"
                            : pageIdx == 2 ? "printRange" : "printAllPages";
                    form.put("printStyle", printStyle);
                    form.put("rangeBegin", rangeFrom.getText().trim());
                    form.put("rangeEnd", rangeTo.getText().trim());

                    // Format
                    boolean isCsv = "CSV".equals(formatCombo.getSelectedItem());
                    form.put("downloadFormat", isCsv ? "CSV" : "ORIG");
                    int sepIdx = sepCombo.getSelectedIndex();
                    form.put("csvStyle", csvModeValues[csvModeCombo.getSelectedIndex()]);
                    form.put("delimiter", sepValues[sepIdx]);
                    form.put("csv_int", "csv_int".equals(csvModeValues[csvModeCombo.getSelectedIndex()])
                            ? csvInput.getText().trim() : hidden.getOrDefault("csv_int", "10"));
                    form.put("csv_char", "csv_char".equals(csvModeValues[csvModeCombo.getSelectedIndex()])
                            ? csvInput.getText().trim() : "");
                    form.put("csv_flex", "csv_flex".equals(csvModeValues[csvModeCombo.getSelectedIndex()])
                            ? csvInput.getText().trim() : "");
                    form.put("dataInput", isCsv ? csvInput.getText().trim() : "");
                    form.put("csv_trim", csvTrim.isSelected() ? "true" : "false");

                    // Notes
                    form.put("printWhatStyle", noteValues[notesCombo.getSelectedIndex()]);
                    form.put("antsPrintStyle", "antsToDoc");
                    form.put("antsType", noteTypeValues[noteTypeCombo.getSelectedIndex()]);

                    // ---- Submit ----
                    DownloadResult r = client.postFormDownload(session, "downloadWithOptions.action", form);
                    saveDownloadResult(r);
                } catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    private void saveDownloadResult(DownloadResult r) {
        String ext = r.isPdf() ? ".pdf" : ".txt";
        JFileChooser ch = new JFileChooser();
        ch.setSelectedFile(new java.io.File("dokument" + ext));
        if (ch.showSaveDialog(BetaViewSwingFrame.this) == JFileChooser.APPROVE_OPTION) {
            try {
                java.nio.file.Files.write(ch.getSelectedFile().toPath(), r.data());
            } catch (Exception ex) { showError(ex); }
        }
    }

    // ---- Bookmark ----

    private void bookmarkDocument(DocumentPreviewPanel panel, BetaViewClient client,
                                  BetaViewSession session) {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.postFormText(session, "docbrowser.bookmark.get.action", new java.util.LinkedHashMap<>());
            }
            @Override protected void done() {
                try {
                    String dh = get();
                    java.util.Map<String, String> h = HiddenInputExtractor.extractHiddenInputs(dh);
                    org.jsoup.nodes.Document d = org.jsoup.Jsoup.parse(dh);
                    org.jsoup.nodes.Element ni = d.selectFirst("#we_id_bookmark_name");
                    org.jsoup.nodes.Element di = d.selectFirst("#we_id_bookmark_desc");

                    JPanel dp = new JPanel(new GridLayout(2, 2, 4, 4));
                    dp.add(new JLabel("Name:"));
                    JTextField nf = new JTextField(ni != null ? ni.attr("value") : "");
                    dp.add(nf);
                    dp.add(new JLabel("Beschreibung:"));
                    JTextField df = new JTextField(di != null ? di.attr("value") : "");
                    dp.add(df);
                    if (JOptionPane.showConfirmDialog(BetaViewSwingFrame.this, dp,
                            "Dokument merken", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

                    java.util.LinkedHashMap<String, String> f = new java.util.LinkedHashMap<>();
                    String tn = h.get("struts.token.name");
                    if (tn != null) { f.put("struts.token.name", tn); String tv = h.get(tn); if (tv != null) f.put(tn, tv); }
                    f.put("bookmark_name", nf.getText().trim());
                    f.put("bookmark_desc", df.getText().trim());
                    f.put("indexDocResultList", h.getOrDefault("indexDocResultList", "-1"));
                    f.put("source", h.getOrDefault("source", "docbrowser"));
                    client.postFormText(session, "bookmarks.add.action", f);
                } catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    // ---- Print / Doc info ----

    private void showPrintInfo(DocumentPreviewPanel panel, BetaViewClient client,
                               BetaViewSession session) {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.getText(session, "printDialog.action?&init=yes&source=docbrowser");
            }
            @Override protected void done() {
                try {
                    String text = org.jsoup.Jsoup.parse(get()).select(".modal-body").text();
                    JTextArea ta = new JTextArea(text.isEmpty() ? get() : text, 20, 60);
                    ta.setLineWrap(true); ta.setWrapStyleWord(true); ta.setEditable(false);
                    JOptionPane.showMessageDialog(BetaViewSwingFrame.this, new JScrollPane(ta),
                            "Druckinformationen", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    private void showDocumentInfo(DocumentPreviewPanel panel, BetaViewClient client,
                                  BetaViewSession session) {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.getText(session, "docbrowser.documentproperties.get.action");
            }
            @Override protected void done() {
                try {
                    org.jsoup.nodes.Document d = org.jsoup.Jsoup.parse(get());
                    StringBuilder sb = new StringBuilder();
                    for (org.jsoup.nodes.Element r : d.select(".row")) {
                        org.jsoup.select.Elements ls = r.select(".el_label_desc");
                        org.jsoup.select.Elements vs = r.select(".el_label_value");
                        for (int i = 0; i < Math.min(ls.size(), vs.size()); i++) {
                            String l = ls.get(i).text().trim(), v = vs.get(i).text().trim();
                            if (!l.isEmpty() || !v.isEmpty()) sb.append(l).append(": ").append(v).append("\n");
                        }
                        if (ls.isEmpty() && vs.isEmpty()) { String t = r.text().trim(); if (!t.isEmpty()) sb.append(t).append("\n"); }
                    }
                    if (sb.length() == 0) sb.append(d.text());
                    JTextArea ta = new JTextArea(sb.toString(), 25, 70);
                    ta.setLineWrap(true); ta.setWrapStyleWord(true); ta.setEditable(false); ta.setCaretPosition(0);
                    JOptionPane.showMessageDialog(BetaViewSwingFrame.this, new JScrollPane(ta),
                            "Dokumentinformationen", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    // ---- Preview drawer ----

    private void loadNotes(DocumentPreviewPanel panel, BetaViewClient client, BetaViewSession session) {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.getText(session, "document.notes.list.action");
            }
            @Override protected void done() {
                try {
                    org.jsoup.nodes.Document d = org.jsoup.Jsoup.parse(get());
                    StringBuilder sb = new StringBuilder();
                    for (org.jsoup.nodes.Element n : d.select(".we_note_text, .el_note_content, textarea")) {
                        String t = n.text().trim();
                        if (!t.isEmpty()) sb.append(t).append("\n\n");
                    }
                    if (sb.length() == 0) {
                        org.jsoup.nodes.Element c = d.selectFirst("#we_id_doc_notes_container_content");
                        sb.append("0".equals(c != null ? c.attr("data-counttotal") : "0")
                                ? "Keine Notizen vorhanden." : d.text());
                    }
                    panel.setNotesContent(sb.toString().trim());
                } catch (Exception ex) { panel.setNotesContent("Fehler: " + ex.getMessage()); }
            }
        }.execute();
    }

    private void loadMarkedPages(DocumentPreviewPanel panel, BetaViewClient client, BetaViewSession session) {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.getText(session, "holdhide.list.action");
            }
            @Override protected void done() {
                try {
                    String j = get();
                    panel.setMarksContent(j.contains("\"holdLineIndex\"")
                            ? "Hold/Hide-Konfiguration:\n" + j : "Keine markierten Bereiche.");
                } catch (Exception ex) { panel.setMarksContent("Fehler: " + ex.getMessage()); }
            }
        }.execute();
    }

    private void doFulltextSearch(DocumentPreviewPanel panel, BetaViewClient client,
                                  BetaViewSession session, String query) {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                java.util.LinkedHashMap<String, String> f = new java.util.LinkedHashMap<>();
                f.put("searchString", query);
                f.put("caseSensitive", "false");
                f.put("searchType", "contains");
                return client.postFormText(session, "document.search.action", f);
            }
            @Override protected void done() {
                try { panel.setSearchResult(org.jsoup.Jsoup.parse(get()).text()); }
                catch (Exception ex) { panel.setSearchResult("Fehler: " + ex.getMessage()); }
            }
        }.execute();
    }

    // ================================================================ Helpers

    private ConnectionTabPanel getActiveConnectionTab() {
        Component sel = connectionTabs.getSelectedComponent();
        return sel instanceof ConnectionTabPanel ? (ConnectionTabPanel) sel : null;
    }

    private static String parsePageText(String json) {
        StringBuilder sb = new StringBuilder();
        int textIdx = json.indexOf("\"text\"");
        if (textIdx < 0) return json;
        int arrStart = json.indexOf('[', textIdx);
        if (arrStart < 0) return json;
        int arrEnd = json.indexOf(']', arrStart);
        if (arrEnd < 0) return json;

        String arr = json.substring(arrStart + 1, arrEnd);
        boolean inStr = false, esc = false;
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            char c = arr.charAt(i);
            if (esc) {
                switch (c) { case '/': line.append('/'); break; case 'n': line.append('\n'); break;
                    case 't': line.append('\t'); break; case '"': line.append('"'); break;
                    case '\\': line.append('\\'); break; default: line.append(c); }
                esc = false;
            } else if (c == '\\') { esc = true; }
            else if (c == '"') { inStr = !inStr; }
            else if (c == ',' && !inStr) { sb.append(line).append('\n'); line.setLength(0); }
            else { line.append(c); }
        }
        if (line.length() > 0) sb.append(line).append('\n');
        return sb.toString();
    }

    private void showError(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.trim().isEmpty()) msg = ex.getClass().getName();
        JOptionPane.showMessageDialog(this, msg, "Fehler", JOptionPane.ERROR_MESSAGE);
    }
}
