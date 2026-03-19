package de.bund.zrb.archive.ui;

import de.bund.zrb.archive.model.ArchiveDocument;
import de.bund.zrb.archive.model.ArchiveRun;
import de.bund.zrb.archive.service.ArchiveService;
import de.bund.zrb.archive.service.ResourceStorageService;
import de.bund.zrb.archive.store.CacheRepository;
import de.bund.zrb.search.SearchHighlighter;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Connection tab for the Cache system.
 * Shows locally cached content from all sources (Web, FTP, NDV, Mail, BetaView).
 * Features: source type filter, host/kind filtering (for Web), search-term highlighting,
 * checkboxes for selective delete, delete-all with confirmation.
 */
public class CacheConnectionTab implements ConnectionTab {

    private final JPanel mainPanel;
    private final CacheRepository repo;
    private final ResourceStorageService storageService;
    private final JTextArea previewArea;
    private final JTextField searchField;
    private final JLabel statusLabel;
    private final TabbedPaneManager tabbedPaneManager;

    // View state
    private final JComboBox<String> viewSelector;
    private final JComboBox<String> sourceTypeFilter;
    private final JComboBox<String> hostFilter;
    private final JComboBox<String> kindFilter;
    private final JPanel hostFilterPanel;
    private final JPanel kindFilterPanel;
    private final RunTableModel runTableModel;
    private final DocumentTableModel docTableModel;
    private final JTable runTable;
    private final JTable dataTable;
    private final CardLayout cardLayout;
    private final JPanel tableCards;

    /** Current search query – used for yellow highlighting in preview. */
    private String currentSearchQuery = null;
    private final JToggleButton advancedToggle;

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    static {
        DATE_FMT.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
    }

    public CacheConnectionTab(TabbedPaneManager tabbedPaneManager) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.repo = CacheRepository.getInstance();
        this.storageService = ArchiveService.getInstance().getStorageService();
        this.mainPanel = new JPanel(new BorderLayout(4, 4));

        // ── Toolbar ──
        JPanel toolbar = new JPanel(new BorderLayout(4, 0));

        // Left: source type → view selector → refresh → delete
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        sourceTypeFilter = new JComboBox<>(new String[]{
                "Alle Quellen", "🌐 Web", "📁 FTP", "🖥 NDV", "📧 Mail", "📘 BetaView", "📊 SharePoint"});
        sourceTypeFilter.addActionListener(e -> {
            updateFilterVisibility();
            switchView();
        });
        leftPanel.add(sourceTypeFilter);

        viewSelector = new JComboBox<>(new String[]{"📁 Runs", "📄 Dokumente"});
        viewSelector.addActionListener(e -> switchView());
        leftPanel.add(viewSelector);

        JButton refreshBtn = new JButton("🔄");
        refreshBtn.setToolTipText("Aktualisieren");
        refreshBtn.addActionListener(e -> refresh());
        leftPanel.add(refreshBtn);

        JButton deleteBtn = new JButton("🗑 Löschen");
        deleteBtn.setToolTipText("Markierte oder alle Einträge löschen");
        deleteBtn.addActionListener(e -> deleteEntries());
        leftPanel.add(deleteBtn);

        // Center: search + advanced toggle
        JPanel searchPanel = new JPanel(new BorderLayout(2, 0));
        searchField = new JTextField();
        searchField.setToolTipText("Cache durchsuchen (Enter)…");
        searchField.addActionListener(e -> filterDocuments());
        advancedToggle = new JToggleButton("\u2699");
        advancedToggle.setToolTipText("Erweiterte Suche: AND OR NOT \"phrase\"\n\n"
                + "Beispiele:\n"
                + "  bundestag AND asyl  (beide Begriffe)\n"
                + "  klima OR energie    (einer der Begriffe)\n"
                + "  \"exakte phrase\"     (wörtlich)");
        advancedToggle.setFocusable(false);
        advancedToggle.setMargin(new Insets(2, 6, 2, 6));
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(advancedToggle, BorderLayout.EAST);

        // Right: host + kind filters (only visible for Web)
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

        hostFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        hostFilter = new JComboBox<>(new String[]{"Alle Hosts"});
        hostFilter.addActionListener(e -> filterDocuments());
        hostFilterPanel.add(new JLabel("Host:"));
        hostFilterPanel.add(hostFilter);
        filterPanel.add(hostFilterPanel);

        kindFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        kindFilter = new JComboBox<>(new String[]{"Alle Typen", "ARTICLE", "PAGE", "LISTING", "FEED_ENTRY", "OTHER"});
        kindFilter.addActionListener(e -> filterDocuments());
        kindFilterPanel.add(new JLabel("Typ:"));
        kindFilterPanel.add(kindFilter);
        filterPanel.add(kindFilterPanel);

        toolbar.add(leftPanel, BorderLayout.WEST);
        toolbar.add(searchPanel, BorderLayout.CENTER);
        toolbar.add(filterPanel, BorderLayout.EAST);
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // ── Tables (Card Layout for Run vs Document view) ──
        cardLayout = new CardLayout();
        tableCards = new JPanel(cardLayout);

        // Run table
        runTableModel = new RunTableModel();
        runTable = new JTable(runTableModel);
        runTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        runTable.getColumnModel().getColumn(0).setMaxWidth(80);
        runTable.getColumnModel().getColumn(2).setMaxWidth(100);
        runTable.getColumnModel().getColumn(3).setMaxWidth(80);
        runTable.getColumnModel().getColumn(4).setMaxWidth(80);
        runTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showRunPreview();
        });
        // Double-click: show documents for this run
        runTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = runTable.getSelectedRow();
                    if (row >= 0) {
                        ArchiveRun run = runTableModel.getRun(row);
                        viewSelector.setSelectedIndex(1);
                        loadDocumentsForRun(run.getRunId());
                    }
                }
            }
        });
        tableCards.add(new JScrollPane(runTable), "RUNS");

        // Document table (with checkbox column)
        docTableModel = new DocumentTableModel();
        dataTable = new JTable(docTableModel);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataTable.getColumnModel().getColumn(0).setMaxWidth(30);
        dataTable.getColumnModel().getColumn(0).setMinWidth(30);
        dataTable.getColumnModel().getColumn(2).setMaxWidth(80);
        dataTable.getColumnModel().getColumn(3).setMaxWidth(130);

        // Highlighting renderer for Titel column (col 1)
        dataTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? Color.WHITE : new Color(245, 245, 250));
                }
                if (value != null && currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
                    setText("<html>" + SearchHighlighter.highlightHtml(
                            SearchHighlighter.escHtml(value.toString()), currentSearchQuery) + "</html>");
                }
                return this;
            }
        });

        dataTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDocPreview();
        });
        tableCards.add(new JScrollPane(dataTable), "DOCS");

        // ── Preview ──
        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JScrollPane previewScroll = new JScrollPane(previewArea);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableCards, previewScroll);
        splitPane.setDividerLocation(250);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // ── Status ──
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        refresh();
    }

    // ═══════════════════════════════════════════════════════════
    //  Dynamic filter visibility
    // ═══════════════════════════════════════════════════════════

    /**
     * Show/hide Host and Kind filters depending on selected source type.
     * Host + Kind are only relevant for Web sources.
     */
    private void updateFilterVisibility() {
        boolean isWeb = sourceTypeFilter.getSelectedIndex() == 1; // "🌐 Web"
        hostFilterPanel.setVisible(isWeb);
        kindFilterPanel.setVisible(isWeb);
        mainPanel.revalidate();
    }

    // ═══════════════════════════════════════════════════════════
    //  View switching & refresh
    // ═══════════════════════════════════════════════════════════

    private void switchView() {
        int sourceIdx = sourceTypeFilter.getSelectedIndex();
        int selected = viewSelector.getSelectedIndex();

        // Runs view only makes sense for Web sources
        if (selected == 0 && (sourceIdx == 0 || sourceIdx == 1)) {
            cardLayout.show(tableCards, "RUNS");
            loadRuns();
        } else if (selected == 0 && sourceIdx >= 2) {
            // Non-web source selected but "Runs" view chosen: switch to docs
            viewSelector.setSelectedIndex(1);
            cardLayout.show(tableCards, "DOCS");
            loadAllDocuments();
        } else {
            cardLayout.show(tableCards, "DOCS");
            loadAllDocuments();
        }
    }

    private void refresh() {
        updateHostFilter();
        updateFilterVisibility();
        switchView();
    }

    // ═══════════════════════════════════════════════════════════
    //  Data loading
    // ═══════════════════════════════════════════════════════════

    private void loadRuns() {
        List<ArchiveRun> runs = repo.findAllRuns();
        runTableModel.setRuns(runs);
        int totalRes = 0, totalDoc = 0;
        for (ArchiveRun r : runs) { totalRes += r.getResourceCount(); totalDoc += r.getDocumentCount(); }
        statusLabel.setText(runs.size() + " Runs │ " + totalRes + " Resources │ " + totalDoc + " Dokumente");
    }

    private void loadAllDocuments() {
        int sourceIdx = sourceTypeFilter.getSelectedIndex();

        // For non-web sources show empty list with hint
        if (sourceIdx >= 2) {
            String sourceName = (String) sourceTypeFilter.getSelectedItem();
            currentSearchQuery = null;
            docTableModel.setDocuments(new ArrayList<ArchiveDocument>());
            statusLabel.setText("Keine gecachten " + sourceName + "-Inhalte vorhanden.");
            previewArea.setText("");
            return;
        }

        currentSearchQuery = null;
        List<ArchiveDocument> docs = repo.findAllDocuments();
        docTableModel.setDocuments(docs);
        statusLabel.setText(docs.size() + " Dokumente im Cache");
    }

    private void loadDocumentsForRun(String runId) {
        currentSearchQuery = null;
        List<ArchiveDocument> docs = repo.findDocumentsByRunId(runId);
        docTableModel.setDocuments(docs);
        statusLabel.setText(docs.size() + " Dokumente für Run " + runId.substring(0, Math.min(8, runId.length())) + "…");
    }

    // ═══════════════════════════════════════════════════════════
    //  Filtering
    // ═══════════════════════════════════════════════════════════

    private void filterDocuments() {
        int sourceIdx = sourceTypeFilter.getSelectedIndex();
        // 0=Alle, 1=Web, 2=FTP, 3=NDV, 4=Mail, 5=BetaView

        // For non-web sources there are currently no cached documents in the DB.
        // Show an empty list with a status hint.
        if (sourceIdx >= 2) {
            String sourceName = (String) sourceTypeFilter.getSelectedItem();
            docTableModel.setDocuments(new ArrayList<ArchiveDocument>());
            viewSelector.setSelectedIndex(1);
            cardLayout.show(tableCards, "DOCS");
            statusLabel.setText("Keine gecachten " + sourceName + "-Inhalte vorhanden. "
                    + "Inhalte werden beim manuellen Cachen aus dem jeweiligen Tab gespeichert.");
            previewArea.setText("");
            return;
        }

        String query = searchField.getText();
        String selectedHost = (String) hostFilter.getSelectedItem();
        String selectedKind = (String) kindFilter.getSelectedItem();

        String host = "Alle Hosts".equals(selectedHost) ? null : selectedHost;
        String rawQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;

        currentSearchQuery = rawQuery;

        List<ArchiveDocument> docs;
        if (rawQuery != null) {
            // Extract actual search terms (strip AND/OR/NOT operators)
            List<String> terms = SearchHighlighter.extractSearchTerms(rawQuery);

            if (terms.isEmpty()) {
                docs = host != null ? repo.searchDocuments(null, host, 500) : repo.findAllDocuments();
            } else if (advancedToggle.isSelected() && terms.size() > 1) {
                // Advanced mode: AND-logic – search for first term, then filter client-side
                docs = repo.searchDocuments(terms.get(0), host, 2000);
                for (int i = 1; i < terms.size(); i++) {
                    final String term = terms.get(i).toLowerCase();
                    List<ArchiveDocument> filtered = new ArrayList<ArchiveDocument>();
                    for (ArchiveDocument d : docs) {
                        boolean match = (d.getTitle() != null && d.getTitle().toLowerCase().contains(term))
                                || (d.getExcerpt() != null && d.getExcerpt().toLowerCase().contains(term))
                                || (d.getCanonicalUrl() != null && d.getCanonicalUrl().toLowerCase().contains(term));
                        if (match) filtered.add(d);
                    }
                    docs = filtered;
                }
            } else {
                // Simple mode: pass query to SQL LIKE
                docs = repo.searchDocuments(rawQuery, host, 500);
            }
        } else {
            docs = host != null ? repo.searchDocuments(null, host, 500) : repo.findAllDocuments();
        }

        // Additional kind filter (client-side)
        if (selectedKind != null && !"Alle Typen".equals(selectedKind)) {
            List<ArchiveDocument> filtered = new ArrayList<ArchiveDocument>();
            for (ArchiveDocument d : docs) {
                if (selectedKind.equals(d.getKind())) filtered.add(d);
            }
            docs = filtered;
        }

        // Switch to document view
        viewSelector.setSelectedIndex(1);
        cardLayout.show(tableCards, "DOCS");
        docTableModel.setDocuments(docs);
        statusLabel.setText(docs.size() + " Dokumente gefunden"
                + (advancedToggle.isSelected() ? " (erweiterte Suche)" : ""));
    }

    private void updateHostFilter() {
        Set<String> hosts = new TreeSet<String>();
        for (ArchiveDocument d : repo.findAllDocuments()) {
            if (d.getHost() != null && !d.getHost().isEmpty()) {
                hosts.add(d.getHost());
            }
        }
        hostFilter.removeAllItems();
        hostFilter.addItem("Alle Hosts");
        for (String h : hosts) {
            hostFilter.addItem(h);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Preview (with yellow search-term highlighting)
    // ═══════════════════════════════════════════════════════════

    private void showRunPreview() {
        int row = runTable.getSelectedRow();
        if (row < 0) { previewArea.setText(""); return; }
        ArchiveRun run = runTableModel.getRun(row);
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Run ═══\n");
        sb.append("ID:       ").append(run.getRunId()).append("\n");
        sb.append("Modus:    ").append(run.getMode()).append("\n");
        sb.append("Status:   ").append(run.getStatus()).append("\n");
        sb.append("Gestartet: ").append(formatTime(run.getCreatedAt())).append("\n");
        if (run.getEndedAt() > 0) {
            sb.append("Beendet:   ").append(formatTime(run.getEndedAt())).append("\n");
        }
        sb.append("Resources: ").append(run.getResourceCount()).append("\n");
        sb.append("Dokumente: ").append(run.getDocumentCount()).append("\n");
        if (run.getSeedUrls() != null && !run.getSeedUrls().isEmpty()) {
            sb.append("Seed-URLs: ").append(run.getSeedUrls()).append("\n");
        }
        if (run.getDomainPolicyJson() != null && !run.getDomainPolicyJson().isEmpty()
                && !"{}".equals(run.getDomainPolicyJson())) {
            sb.append("Policy:    ").append(run.getDomainPolicyJson()).append("\n");
        }
        sb.append("\nDoppelklick → Dokumente dieses Runs anzeigen");
        setPreviewText(sb.toString());
    }

    private void showDocPreview() {
        int row = dataTable.getSelectedRow();
        if (row < 0) { previewArea.setText(""); return; }
        ArchiveDocument doc = docTableModel.getDocument(row);
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Dokument ═══\n");
        sb.append("Titel:    ").append(doc.getTitle()).append("\n");
        sb.append("URL:      ").append(doc.getCanonicalUrl()).append("\n");
        sb.append("Typ:      ").append(doc.getKind()).append("\n");
        sb.append("Host:     ").append(doc.getHost()).append("\n");
        sb.append("Sprache:  ").append(doc.getLanguage()).append("\n");
        sb.append("Wörter:   ").append(doc.getWordCount()).append("\n");
        sb.append("Erstellt: ").append(formatTime(doc.getCreatedAt())).append("\n");
        sb.append("Run-ID:   ").append(doc.getRunId()).append("\n");
        sb.append("Doc-ID:   ").append(doc.getDocId()).append("\n\n");

        if (doc.getExcerpt() != null && !doc.getExcerpt().isEmpty()) {
            sb.append("── Excerpt ──\n").append(doc.getExcerpt()).append("\n\n");
        }

        // Load full text from stored file
        if (doc.getTextContentPath() != null && !doc.getTextContentPath().isEmpty()) {
            String text = storageService.readContent(doc.getTextContentPath(), 10000);
            if (text != null) {
                sb.append("── Text ──\n").append(text);
            }
        }

        setPreviewText(sb.toString());
    }

    /**
     * Sets the preview text and highlights all occurrences of the current
     * search query in yellow.
     */
    private void setPreviewText(String text) {
        previewArea.setText(text);
        previewArea.setCaretPosition(0);
        SearchHighlighter.highlightTextArea(previewArea, currentSearchQuery, Color.YELLOW);
    }

    // ═══════════════════════════════════════════════════════════
    //  Delete
    // ═══════════════════════════════════════════════════════════

    private void deleteEntries() {
        int currentView = viewSelector.getSelectedIndex();

        if (currentView == 1) {
            // ── Document view: delete checked documents ──
            List<ArchiveDocument> selected = docTableModel.getSelectedDocuments();
            if (selected.isEmpty()) {
                int count = docTableModel.getRowCount();
                if (count == 0) return;
                int result = JOptionPane.showConfirmDialog(mainPanel,
                        "Es sind keine Einträge markiert.\nAlle " + count + " Einträge löschen?",
                        "Archiv leeren", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    repo.deleteAllDocuments();
                    refresh();
                    previewArea.setText("");
                }
            } else {
                int result = JOptionPane.showConfirmDialog(mainPanel,
                        selected.size() + " markierte Einträge löschen?",
                        "Einträge löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    for (ArchiveDocument doc : selected) {
                        repo.deleteDocument(doc.getDocId());
                    }
                    refresh();
                    previewArea.setText("");
                }
            }
        } else {
            // ── Run view: delete selected run ──
            int selectedRow = runTable.getSelectedRow();
            if (selectedRow < 0) {
                int count = runTableModel.getRowCount();
                if (count == 0) return;
                int result = JOptionPane.showConfirmDialog(mainPanel,
                        "Kein Run ausgewählt.\nAlle " + count + " Runs mit Dokumenten und Resources löschen?",
                        "Alles löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    repo.deleteAllDocuments();
                    refresh();
                    previewArea.setText("");
                }
            } else {
                ArchiveRun run = runTableModel.getRun(selectedRow);
                String shortId = run.getRunId().substring(0, Math.min(8, run.getRunId().length()));
                int result = JOptionPane.showConfirmDialog(mainPanel,
                        "Run '" + shortId + "…' mit allen Dokumenten löschen?",
                        "Run löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    repo.deleteRun(run.getRunId());
                    refresh();
                    previewArea.setText("");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private static String formatTime(long epochMillis) {
        if (epochMillis <= 0) return "–";
        return DATE_FMT.format(new Date(epochMillis));
    }

    // ── ConnectionTab interface ──

    @Override public String getTitle() { return "💾 Cache"; }
    @Override public String getTooltip() { return "Lokal zwischengespeicherte Inhalte (Web, FTP, NDV, Mail, BetaView)"; }
    @Override public JComponent getComponent() { return mainPanel; }
    @Override public void onClose() { /* nothing */ }
    @Override public void saveIfApplicable() { /* read-only */ }
    @Override public String getContent() { return ""; }
    @Override public void markAsChanged() { /* not applicable */ }
    @Override public String getPath() { return "cache://"; }
    @Override public Type getType() { return Type.CONNECTION; }

    @Override
    public void focusSearchField() {
        searchField.requestFocusInWindow();
    }

    @Override
    public void searchFor(String searchPattern) {
        searchField.setText(searchPattern);
        filterDocuments();
    }

    // ═══════════════════════════════════════════════════════════
    //  Table Models
    // ═══════════════════════════════════════════════════════════

    private static class RunTableModel extends AbstractTableModel {
        private List<ArchiveRun> runs = new ArrayList<ArchiveRun>();
        private static final String[] COLUMNS = {"Modus", "Gestartet", "Status", "Resources", "Dokumente"};

        void setRuns(List<ArchiveRun> runs) {
            this.runs = runs != null ? runs : new ArrayList<ArchiveRun>();
            fireTableDataChanged();
        }

        ArchiveRun getRun(int row) { return runs.get(row); }

        @Override public int getRowCount() { return runs.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            ArchiveRun r = runs.get(row);
            switch (col) {
                case 0: return r.getMode();
                case 1: return formatTime(r.getCreatedAt());
                case 2: return r.getStatus();
                case 3: return r.getResourceCount();
                case 4: return r.getDocumentCount();
                default: return "";
            }
        }
    }

    private static class DocumentTableModel extends AbstractTableModel {
        private List<ArchiveDocument> docs = new ArrayList<ArchiveDocument>();
        private final Set<Integer> selectedRows = new HashSet<Integer>();
        private static final String[] COLUMNS = {"✓", "Titel", "Typ", "Erstellt", "Host", "Wörter"};

        void setDocuments(List<ArchiveDocument> docs) {
            this.docs = docs != null ? docs : new ArrayList<ArchiveDocument>();
            this.selectedRows.clear();
            fireTableDataChanged();
        }

        ArchiveDocument getDocument(int row) { return docs.get(row); }

        List<ArchiveDocument> getSelectedDocuments() {
            List<ArchiveDocument> result = new ArrayList<ArchiveDocument>();
            for (int row : selectedRows) {
                if (row < docs.size()) {
                    result.add(docs.get(row));
                }
            }
            return result;
        }

        @Override public int getRowCount() { return docs.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col == 0) return Boolean.class;
            return Object.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 0;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 0 && value instanceof Boolean) {
                if ((Boolean) value) {
                    selectedRows.add(row);
                } else {
                    selectedRows.remove(row);
                }
                fireTableCellUpdated(row, col);
            }
        }

        @Override
        public Object getValueAt(int row, int col) {
            ArchiveDocument d = docs.get(row);
            switch (col) {
                case 0: return selectedRows.contains(row);
                case 1: return d.getTitle();
                case 2: return d.getKind();
                case 3: return formatTime(d.getCreatedAt());
                case 4: return d.getHost();
                case 5: return d.getWordCount();
                default: return "";
            }
        }
    }
}
