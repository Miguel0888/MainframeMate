package de.bund.zrb.archive.ui;

import de.bund.zrb.archive.model.ArchiveDocument;
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
 * Shows all archived documents (web snapshots) with a preview panel.
 * Delete: checkboxes in first column; if nothing checked, asks to delete all.
 */
public class ArchiveConnectionTab implements ConnectionTab {

    private final JPanel mainPanel;
    private final ArchiveRepository repo;
    private final ResourceStorageService storageService;
    private final DocumentTableModel tableModel;
    private final JTable archiveTable;
    private final JTextArea previewArea;
    private final JTextField searchField;
    private final JLabel statusLabel;
    private final TabbedPaneManager tabbedPaneManager;

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
        JButton refreshBtn = new JButton("🔄");
        refreshBtn.setToolTipText("Aktualisieren");
        refreshBtn.addActionListener(e -> loadEntries());

        searchField = new JTextField();
        searchField.setToolTipText("Archiv durchsuchen...");
        searchField.addActionListener(e -> filterEntries());

        JButton deleteBtn = new JButton("🗑 Löschen");
        deleteBtn.setToolTipText("Markierte oder alle Einträge löschen");
        deleteBtn.addActionListener(e -> deleteEntries());

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        rightButtons.add(deleteBtn);

        toolbar.add(refreshBtn, BorderLayout.WEST);
        toolbar.add(searchField, BorderLayout.CENTER);
        toolbar.add(rightButtons, BorderLayout.EAST);
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // ── Table ──
        tableModel = new DocumentTableModel();
        archiveTable = new JTable(tableModel);
        archiveTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Checkbox column
        archiveTable.getColumnModel().getColumn(0).setMaxWidth(30);
        archiveTable.getColumnModel().getColumn(0).setMinWidth(30);
        // Kind column
        archiveTable.getColumnModel().getColumn(2).setMaxWidth(80);
        // Date column
        archiveTable.getColumnModel().getColumn(3).setMaxWidth(130);
        archiveTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showPreview();
        });
        JScrollPane tableScroll = new JScrollPane(archiveTable);

        // ── Preview ──
        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JScrollPane previewScroll = new JScrollPane(previewArea);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, previewScroll);
        splitPane.setDividerLocation(250);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // ── Status ──
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        loadEntries();
    }

    private void loadEntries() {
        List<ArchiveDocument> docs = repo.findAllDocuments();
        tableModel.setDocuments(docs);
        statusLabel.setText(docs.size() + " Dokumente im Archiv");
    }

    private void filterEntries() {
        String query = searchField.getText();
        if (query == null || query.trim().isEmpty()) {
            loadEntries();
            return;
        }
        List<ArchiveDocument> docs = repo.searchDocuments(query.trim(), null, 500);
        tableModel.setDocuments(docs);
        statusLabel.setText(docs.size() + " Dokumente gefunden");
    }

    private void showPreview() {
        int row = archiveTable.getSelectedRow();
        if (row < 0) {
            previewArea.setText("");
            return;
        }
        ArchiveDocument doc = tableModel.getDocument(row);
        StringBuilder sb = new StringBuilder();
        sb.append("Titel:    ").append(doc.getTitle()).append("\n");
        sb.append("URL:      ").append(doc.getCanonicalUrl()).append("\n");
        sb.append("Typ:      ").append(doc.getKind()).append("\n");
        sb.append("Host:     ").append(doc.getHost()).append("\n");
        sb.append("Sprache:  ").append(doc.getLanguage()).append("\n");
        sb.append("Wörter:   ").append(doc.getWordCount()).append("\n");
        sb.append("Erstellt: ").append(formatTime(doc.getCreatedAt())).append("\n");
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

        previewArea.setText(sb.toString());
        previewArea.setCaretPosition(0);
    }

    private void deleteEntries() {
        List<ArchiveDocument> selected = tableModel.getSelectedDocuments();
        if (selected.isEmpty()) {
            // Nichts markiert → alles löschen?
            int count = tableModel.getRowCount();
            if (count == 0) return;
            int result = JOptionPane.showConfirmDialog(mainPanel,
                    "Es sind keine Einträge markiert.\nAlle " + count + " Einträge löschen?",
                    "Archiv leeren", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                repo.deleteAllDocuments();
                loadEntries();
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
                loadEntries();
                previewArea.setText("");
            }
        }
    }

    private static String formatTime(long epochMillis) {
        if (epochMillis <= 0) return "–";
        return DATE_FMT.format(new Date(epochMillis));
    }

    // ── ConnectionTab interface ──

    @Override public String getTitle() { return "📦 Archiv"; }
    @Override public String getTooltip() { return "Archivierte Webseiten und Dokumente"; }
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
        filterEntries();
    }

    // ═══════════════════════════════════════════════════════════

    private static class DocumentTableModel extends AbstractTableModel {
        private List<ArchiveDocument> docs = new ArrayList<ArchiveDocument>();
        private final Set<Integer> selectedRows = new HashSet<Integer>();
        private final String[] COLUMNS = {"✓", "Titel", "Typ", "Erstellt", "Host"};

        void setDocuments(List<ArchiveDocument> docs) {
            this.docs = docs != null ? docs : new ArrayList<ArchiveDocument>();
            this.selectedRows.clear();
            fireTableDataChanged();
        }

        ArchiveDocument getDocument(int row) {
            return docs.get(row);
        }

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
            return String.class;
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
                default: return "";
            }
        }
    }
}
