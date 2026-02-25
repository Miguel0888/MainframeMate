package de.bund.zrb.archive.ui;

import de.bund.zrb.archive.model.ArchiveDocument;
import de.bund.zrb.archive.model.ArchiveResource;
import de.bund.zrb.archive.model.ArchiveRun;
import de.bund.zrb.archive.service.ArchiveService;
import de.bund.zrb.archive.service.ResourceStorageService;
import de.bund.zrb.archive.store.ArchiveRepository;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Connection tab for the Archive system.
 * Shows Runs → Documents → Resources hierarchy with preview panel.
 * Filterable by host, kind, and indexable status.
 */
public class ArchiveConnectionTab implements ConnectionTab {

    private final JPanel mainPanel;
    private final ArchiveRepository repo;
    private final ResourceStorageService storageService;
    private final JTextArea previewArea;
    private final JTextField searchField;
    private final JLabel statusLabel;
    private final TabbedPaneManager tabbedPaneManager;

    // View state
    private final JComboBox<String> viewSelector;
    private final JComboBox<String> hostFilter;
    private final JComboBox<String> kindFilter;
    private final RunTableModel runTableModel;
    private final DocumentTableModel docTableModel;
    private final JTable dataTable;
    private final CardLayout cardLayout;
    private final JPanel tableCards;

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    static {
        DATE_FMT.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
    }

    public ArchiveConnectionTab(TabbedPaneManager tabbedPaneManager) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.repo = ArchiveRepository.getInstance();
        this.storageService = ArchiveService.getInstance().getStorageService();
        this.mainPanel = new JPanel(new BorderLayout(4, 4));

        // ── Toolbar ──
        JPanel toolbar = new JPanel(new BorderLayout(4, 0));

        // Left: view selector + refresh
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        viewSelector = new JComboBox<>(new String[]{"📁 Runs", "📄 Dokumente"});
        viewSelector.addActionListener(e -> switchView());
        JButton refreshBtn = new JButton("🔄");
        refreshBtn.setToolTipText("Aktualisieren");
        refreshBtn.addActionListener(e -> refresh());
        leftPanel.add(viewSelector);
        leftPanel.add(refreshBtn);

        // Center: search
        searchField = new JTextField();
        searchField.setToolTipText("Katalog durchsuchen...");
        searchField.addActionListener(e -> filterDocuments());

        // Right: filters
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        hostFilter = new JComboBox<>(new String[]{"Alle Hosts"});
        hostFilter.addActionListener(e -> filterDocuments());
        kindFilter = new JComboBox<>(new String[]{"Alle Typen", "ARTICLE", "PAGE", "LISTING", "FEED_ENTRY", "OTHER"});
        kindFilter.addActionListener(e -> filterDocuments());
        filterPanel.add(new JLabel("Host:"));
        filterPanel.add(hostFilter);
        filterPanel.add(new JLabel("Typ:"));
        filterPanel.add(kindFilter);

        toolbar.add(leftPanel, BorderLayout.WEST);
        toolbar.add(searchField, BorderLayout.CENTER);
        toolbar.add(filterPanel, BorderLayout.EAST);
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // ── Tables (Card Layout for Run vs Document view) ──
        cardLayout = new CardLayout();
        tableCards = new JPanel(cardLayout);

        // Run table
        runTableModel = new RunTableModel();
        JTable runTable = new JTable(runTableModel);
        runTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        runTable.getColumnModel().getColumn(0).setMaxWidth(80);
        runTable.getColumnModel().getColumn(2).setMaxWidth(100);
        runTable.getColumnModel().getColumn(3).setMaxWidth(80);
        runTable.getColumnModel().getColumn(4).setMaxWidth(80);
        runTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showRunPreview(runTable);
        });
        // Double-click: show documents for this run
        runTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = runTable.getSelectedRow();
                    if (row >= 0) {
                        ArchiveRun run = runTableModel.getRun(row);
                        viewSelector.setSelectedIndex(1); // switch to Documents
                        loadDocumentsForRun(run.getRunId());
                    }
                }
            }
        });
        tableCards.add(new JScrollPane(runTable), "RUNS");

        // Document table
        docTableModel = new DocumentTableModel();
        dataTable = new JTable(docTableModel);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataTable.getColumnModel().getColumn(0).setMaxWidth(40);
        dataTable.getColumnModel().getColumn(2).setMaxWidth(100);
        dataTable.getColumnModel().getColumn(3).setMaxWidth(120);
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

    private void switchView() {
        int selected = viewSelector.getSelectedIndex();
        if (selected == 0) {
            cardLayout.show(tableCards, "RUNS");
            loadRuns();
        } else {
            cardLayout.show(tableCards, "DOCS");
            loadAllDocuments();
        }
    }

    private void refresh() {
        updateHostFilter();
        switchView();
    }

    private void loadRuns() {
        List<ArchiveRun> runs = repo.findAllRuns();
        runTableModel.setRuns(runs);
        int totalRes = 0, totalDoc = 0;
        for (ArchiveRun r : runs) { totalRes += r.getResourceCount(); totalDoc += r.getDocumentCount(); }
        statusLabel.setText(runs.size() + " Runs │ " + totalRes + " Resources │ " + totalDoc + " Dokumente");
    }

    private void loadAllDocuments() {
        List<ArchiveDocument> docs = repo.findAllDocuments();
        docTableModel.setDocuments(docs);
        statusLabel.setText(docs.size() + " Dokumente im Katalog");
    }

    private void loadDocumentsForRun(String runId) {
        List<ArchiveDocument> docs = repo.findDocumentsByRunId(runId);
        docTableModel.setDocuments(docs);
        statusLabel.setText(docs.size() + " Dokumente für Run " + runId.substring(0, 8) + "…");
    }

    private void filterDocuments() {
        String query = searchField.getText();
        String selectedHost = (String) hostFilter.getSelectedItem();
        String selectedKind = (String) kindFilter.getSelectedItem();

        String host = "Alle Hosts".equals(selectedHost) ? null : selectedHost;
        String titleFilter = (query != null && !query.trim().isEmpty()) ? query.trim() : null;

        List<ArchiveDocument> docs;
        if (titleFilter != null || host != null) {
            docs = repo.searchDocuments(titleFilter, host, 500);
        } else {
            docs = repo.findAllDocuments();
        }

        // Additional kind filter
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
        statusLabel.setText(docs.size() + " Dokumente gefunden");
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

    private void showRunPreview(JTable runTable) {
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
                && !run.getDomainPolicyJson().equals("{}")) {
            sb.append("Policy:    ").append(run.getDomainPolicyJson()).append("\n");
        }
        sb.append("\nDoppelklick → Dokumente dieses Runs anzeigen");
        previewArea.setText(sb.toString());
        previewArea.setCaretPosition(0);
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

        // Load full text
        if (doc.getTextContentPath() != null && !doc.getTextContentPath().isEmpty()) {
            String text = storageService.readContent(doc.getTextContentPath(), 10000);
            if (text != null) {
                sb.append("── Text ──\n").append(text);
            }
        }

        previewArea.setText(sb.toString());
        previewArea.setCaretPosition(0);
    }

    private static String formatTime(long epochMillis) {
        if (epochMillis <= 0) return "–";
        return DATE_FMT.format(new Date(epochMillis));
    }

    // ── ConnectionTab interface ──

    @Override public String getTitle() { return "📦 Archiv"; }
    @Override public String getTooltip() { return "Data Lake + Katalog (Runs, Dokumente, Resources)"; }
    @Override public JComponent getComponent() { return mainPanel; }
    @Override public void onClose() { /* nothing */ }
    @Override public void saveIfApplicable() { /* read-only */ }
    @Override public String getContent() { return ""; }
    @Override public void markAsChanged() { /* not applicable */ }
    @Override public String getPath() { return "archive://"; }
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
        private static final String[] COLUMNS = {"", "Titel", "Typ", "Erstellt", "Host", "Wörter"};

        void setDocuments(List<ArchiveDocument> docs) {
            this.docs = docs != null ? docs : new ArrayList<ArchiveDocument>();
            fireTableDataChanged();
        }

        ArchiveDocument getDocument(int row) { return docs.get(row); }

        @Override public int getRowCount() { return docs.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            ArchiveDocument d = docs.get(row);
            switch (col) {
                case 0:
                    String kind = d.getKind();
                    if ("ARTICLE".equals(kind)) return "📰";
                    if ("LISTING".equals(kind)) return "📋";
                    if ("FEED_ENTRY".equals(kind)) return "📡";
                    return "📄";
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
