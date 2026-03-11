package de.bund.zrb.betaview;

import de.zrb.bund.newApi.ui.FtpTab;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BetaView document preview tab that implements FtpTab for integration
 * into MainframeMate's TabbedPaneManager.
 *
 * Each opened BetaView document gets its own tab in the TabbedPaneManager,
 * similar to how FileTabImpl works for FTP/NDV files.
 *
 * This wraps a DocumentPreviewPanel and provides all document actions
 * (page navigation, download, bookmark, notes, search, etc.).
 */
public final class BetaViewDocumentTab implements FtpTab {

    private final BetaViewClient client;
    private final BetaViewSession session;
    private final DocumentTab documentTab;
    private final DocumentPreviewPanel previewPanel;
    private final String displayName;

    private boolean loaded = false;

    public BetaViewDocumentTab(BetaViewClient client, BetaViewSession session,
                               DocumentTab documentTab, String displayName) {
        this.client = client;
        this.session = session;
        this.documentTab = documentTab;
        this.displayName = displayName;
        this.previewPanel = new DocumentPreviewPanel();

        setupListeners();
    }

    // ======== Public API ========

    public DocumentTab getDocumentTab() { return documentTab; }
    public DocumentPreviewPanel getPreviewPanel() { return previewPanel; }
    public boolean isLoaded() { return loaded; }
    public void setLoaded(boolean loaded) { this.loaded = loaded; }

    /**
     * Load HTML content into this document tab.
     */
    public void loadDocument(String html) {
        previewPanel.loadDocument(html);
        loaded = true;
    }

    /**
     * Load content from the server by action URL.
     */
    public void loadFromAction(String action) {
        if (action == null || action.isEmpty()) return;

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.getText(session, action);
            }
            @Override protected void done() {
                try {
                    String html = get();
                    previewPanel.loadDocument(html);
                    loaded = true;
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    // ======== Listeners setup ========

    private void setupListeners() {
        previewPanel.setPageChangeListener(page -> loadPage(page));
        previewPanel.setDownloadListener(() -> downloadDocument());
        previewPanel.setDownloadOptionsListener(() -> downloadWithOptions());
        previewPanel.setBookmarkListener(() -> bookmarkDocument());
        previewPanel.setPrintListener(() -> showPrintInfo());
        previewPanel.setDocInfoListener(() -> showDocumentInfo());
        previewPanel.setNotesRefreshListener(() -> loadNotes());
        previewPanel.setMarksRefreshListener(() -> loadMarkedPages());
        previewPanel.setFulltextSearchListener(q -> doFulltextSearch(q));
        previewPanel.setRefreshListener(() -> {
            loaded = false;
            loadFromAction(documentTab.openAction());
        });
    }

    // ---- Page loading ----

    private void loadPage(int page) {
        previewPanel.showPreviewMessage("Lade Seite " + page + " ...");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                client.postFormText(session,
                        "document.viewer.navigation.action?directItem=" + page,
                        new LinkedHashMap<>());

                LinkedHashMap<String, String> form = new LinkedHashMap<>();
                form.put("pageIndex", String.valueOf(page - 1));
                return parsePageText(client.postFormText(session, "document.page.get.action", form));
            }
            @Override protected void done() {
                try { previewPanel.showPreviewText(get()); }
                catch (Exception ex) { previewPanel.showPreviewMessage("Fehler: " + ex.getMessage()); }
            }
        }.execute();
    }

    // ---- Download ----

    private void downloadDocument() {
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

    private void downloadWithOptions() {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.postFormText(session,
                        "downloadDialog.action?&init=yes&source=docbrowser", new LinkedHashMap<>());
            }
            @Override protected void done() {
                try {
                    String dialogHtml = get();
                    Map<String, String> hidden = HiddenInputExtractor.extractHiddenInputs(dialogHtml);

                    // ---- Build options dialog ----
                    JPanel dp = new JPanel(new GridBagLayout());
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
                    java.util.ArrayList<String> fmtList = new java.util.ArrayList<>();
                    fmtList.add("Original");
                    if (csvEnabled) fmtList.add("CSV");
                    JComboBox<String> formatCombo = new JComboBox<>(fmtList.toArray(new String[0]));
                    gc.gridx = 0; gc.gridy = row;
                    dp.add(new JLabel("Format:"), gc);
                    gc.gridx = 1;
                    dp.add(formatCombo, gc);
                    row++;

                    // CSV options
                    String[] separators = {", (Komma)", "; (Semikolon)", "Tabulator"};
                    String[] sepValues = {"comma", "semicolon", "tab"};
                    JComboBox<String> sepCombo = new JComboBox<>(separators);
                    String[] csvModes = {"Nach x Zeichen", "Als Ersatz für", "Variabel"};
                    String[] csvModeValues = {"csv_int", "csv_char", "csv_flex"};
                    JComboBox<String> csvModeCombo = new JComboBox<>(csvModes);
                    JTextField csvInput = new JTextField("10", 8);
                    JCheckBox csvTrim = new JCheckBox("Leerzeichen trimmen", false);

                    JPanel csvPanel = new JPanel(new GridBagLayout());
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

                    // ---- Show dialog ----
                    JScrollPane scrollPane = new JScrollPane(dp);
                    scrollPane.setBorder(null);
                    scrollPane.getVerticalScrollBar().setUnitIncrement(16);
                    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                    int maxH = (int) (screen.height * 0.7);
                    Dimension pref = dp.getPreferredSize();
                    scrollPane.setPreferredSize(new Dimension(
                            Math.min(pref.width + 30, 600),
                            Math.min(pref.height + 10, maxH)));

                    Window parentWindow = SwingUtilities.getWindowAncestor(previewPanel);
                    int opt = JOptionPane.showConfirmDialog(parentWindow, scrollPane,
                            "Herunterladen mit Optionen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                    if (opt != JOptionPane.OK_OPTION) return;

                    // ---- Build form ----
                    LinkedHashMap<String, String> form = new LinkedHashMap<>();

                    String tokenName = hidden.get("struts.token.name");
                    if (tokenName != null) {
                        form.put("struts.token.name", tokenName);
                        String tokenVal = hidden.get(tokenName);
                        if (tokenVal != null) form.put(tokenName, tokenVal);
                    }

                    for (String key : new String[]{"function", "product", "source", "pages",
                            "docLink", "convert", "convertEnabled", "csvEnabled", "doctype",
                            "jobinfo", "textenco", "bss_ukey", "idxstatus", "hasHiddenRanges",
                            "dbinst", "docid", "lineColumnRanges", "index"}) {
                        if (hidden.containsKey(key)) form.put(key, hidden.get(key));
                    }

                    form.put("markedPages", "");
                    form.put("suffix", "");
                    form.put("rangePrintStyle", "");
                    int pageIdx = pageRangeCombo.getSelectedIndex();
                    String printStyle = pageIdx == 1 ? "printCurrentPage"
                            : pageIdx == 2 ? "printRange" : "printAllPages";
                    form.put("printStyle", printStyle);
                    form.put("rangeBegin", rangeFrom.getText().trim());
                    form.put("rangeEnd", rangeTo.getText().trim());

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

                    form.put("printWhatStyle", noteValues[notesCombo.getSelectedIndex()]);
                    form.put("antsPrintStyle", "antsToDoc");
                    form.put("antsType", noteTypeValues[noteTypeCombo.getSelectedIndex()]);

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
        Window parentWindow = SwingUtilities.getWindowAncestor(previewPanel);
        if (ch.showSaveDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
            try {
                java.nio.file.Files.write(ch.getSelectedFile().toPath(), r.data());
            } catch (Exception ex) { showError(ex); }
        }
    }

    // ---- Bookmark ----

    private void bookmarkDocument() {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.postFormText(session, "docbrowser.bookmark.get.action", new LinkedHashMap<>());
            }
            @Override protected void done() {
                try {
                    String dh = get();
                    Map<String, String> h = HiddenInputExtractor.extractHiddenInputs(dh);
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

                    Window parentWindow = SwingUtilities.getWindowAncestor(previewPanel);
                    if (JOptionPane.showConfirmDialog(parentWindow, dp,
                            "Dokument merken", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

                    LinkedHashMap<String, String> f = new LinkedHashMap<>();
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

    private void showPrintInfo() {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.getText(session, "printDialog.action?&init=yes&source=docbrowser");
            }
            @Override protected void done() {
                try {
                    String text = org.jsoup.Jsoup.parse(get()).select(".modal-body").text();
                    JTextArea ta = new JTextArea(text.isEmpty() ? get() : text, 20, 60);
                    ta.setLineWrap(true); ta.setWrapStyleWord(true); ta.setEditable(false);
                    Window parentWindow = SwingUtilities.getWindowAncestor(previewPanel);
                    JOptionPane.showMessageDialog(parentWindow, new JScrollPane(ta),
                            "Druckinformationen", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    private void showDocumentInfo() {
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
                    Window parentWindow = SwingUtilities.getWindowAncestor(previewPanel);
                    JOptionPane.showMessageDialog(parentWindow, new JScrollPane(ta),
                            "Dokumentinformationen", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    // ---- Preview drawer ----

    private void loadNotes() {
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
                    previewPanel.setNotesContent(sb.toString().trim());
                } catch (Exception ex) { previewPanel.setNotesContent("Fehler: " + ex.getMessage()); }
            }
        }.execute();
    }

    private void loadMarkedPages() {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.getText(session, "holdhide.list.action");
            }
            @Override protected void done() {
                try {
                    String j = get();
                    previewPanel.setMarksContent(j.contains("\"holdLineIndex\"")
                            ? "Hold/Hide-Konfiguration:\n" + j : "Keine markierten Bereiche.");
                } catch (Exception ex) { previewPanel.setMarksContent("Fehler: " + ex.getMessage()); }
            }
        }.execute();
    }

    private void doFulltextSearch(String query) {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                LinkedHashMap<String, String> f = new LinkedHashMap<>();
                f.put("searchString", query);
                f.put("caseSensitive", "false");
                f.put("searchType", "contains");
                return client.postFormText(session, "document.search.action", f);
            }
            @Override protected void done() {
                try { previewPanel.setSearchResult(org.jsoup.Jsoup.parse(get()).text()); }
                catch (Exception ex) { previewPanel.setSearchResult("Fehler: " + ex.getMessage()); }
            }
        }.execute();
    }

    // ======== FtpTab interface ========

    @Override
    public String getTitle() {
        String title = documentTab.title();
        if (title.length() > 30) title = title.substring(0, 27) + "...";
        return "📄 " + title;
    }

    @Override
    public String getTooltip() {
        return "BetaView Dokument: " + documentTab.timestamp() + " – " + documentTab.title();
    }

    @Override
    public JComponent getComponent() {
        return previewPanel;
    }

    @Override
    public void onClose() {
        // Close the server-side tab if we have a linkID
        if (documentTab.linkID() != null && !documentTab.linkID().isEmpty()) {
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    client.getText(session, "closeSingleDocument.action?linkID=" + documentTab.linkID());
                    return null;
                }
                @Override protected void done() {
                    try { get(); } catch (Exception ignored) {}
                }
            }.execute();
        }
    }

    @Override
    public void saveIfApplicable() {
        // Not applicable for document preview tabs
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
        return "betaview://" + documentTab.key();
    }

    @Override
    public Type getType() {
        return Type.FILE;
    }

    @Override
    public void focusSearchField() {
        // No search field in document preview
    }

    @Override
    public void searchFor(String searchPattern) {
        // Could trigger fulltext search
        if (searchPattern != null && !searchPattern.isEmpty()) {
            doFulltextSearch(searchPattern);
        }
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        JMenuItem downloadItem = new JMenuItem("⬇ Herunterladen");
        downloadItem.addActionListener(e -> downloadDocument());

        JMenuItem bookmarkItem = new JMenuItem("☆ Merken");
        bookmarkItem.addActionListener(e -> bookmarkDocument());

        menu.add(downloadItem);
        menu.add(bookmarkItem);
        menu.addSeparator();
        menu.add(closeItem);

        return menu;
    }

    // ======== Helpers ========

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
        Window parentWindow = SwingUtilities.getWindowAncestor(previewPanel);
        JOptionPane.showMessageDialog(parentWindow, msg, "Fehler", JOptionPane.ERROR_MESSAGE);
    }
}
