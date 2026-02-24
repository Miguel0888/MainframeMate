package de.bund.zrb.archive.ui;

import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.model.ArchiveEntryStatus;
import de.bund.zrb.archive.model.WebCacheEntry;
import de.bund.zrb.archive.store.ArchiveRepository;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog to view and manage the Web-Cache for a given IndexSource.
 * Shows all discovered URLs with their status, and a preview panel.
 */
public class WebCacheDialog extends JDialog {

    private final ArchiveRepository repo;
    private final String sourceId;
    private final String sourceName;
    private final JTable urlTable;
    private final CacheTableModel tableModel;
    private final JTextArea previewArea;
    private final JLabel statusBar;

    public WebCacheDialog(Frame owner, String sourceId, String sourceName) {
        super(owner, "Web-Cache: " + sourceName, true);
        this.repo = ArchiveRepository.getInstance();
        this.sourceId = sourceId;
        this.sourceName = sourceName;

        setSize(900, 600);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(4, 4));

        // ── Left: URL table ──
        tableModel = new CacheTableModel();
        urlTable = new JTable(tableModel);
        urlTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        urlTable.getColumnModel().getColumn(0).setMaxWidth(40);
        urlTable.getColumnModel().getColumn(0).setCellRenderer(new StatusIconRenderer());
        urlTable.getColumnModel().getColumn(2).setMaxWidth(50);

        urlTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showPreview();
            }
        });

        JScrollPane tableScroll = new JScrollPane(urlTable);
        tableScroll.setPreferredSize(new Dimension(450, 500));

        // Search field
        JTextField searchField = new JTextField();
        searchField.setToolTipText("URLs filtern...");
        searchField.addActionListener(e -> filterUrls(searchField.getText()));

        JPanel searchPanel = new JPanel(new BorderLayout(4, 0));
        searchPanel.add(new JLabel("🔍 "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        JPanel leftPanel = new JPanel(new BorderLayout(4, 4));
        leftPanel.add(searchPanel, BorderLayout.NORTH);
        leftPanel.add(tableScroll, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton addUrlBtn = new JButton("➕ URL hinzufügen");
        addUrlBtn.addActionListener(e -> addUrl());
        JButton deleteBtn = new JButton("🗑️");
        deleteBtn.setToolTipText("Ausgewählte URL entfernen");
        deleteBtn.addActionListener(e -> deleteSelected());
        JButton refreshBtn = new JButton("🔄");
        refreshBtn.setToolTipText("Aktualisieren");
        refreshBtn.addActionListener(e -> loadData());
        buttonPanel.add(addUrlBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(refreshBtn);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);

        // ── Right: Preview ──
        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setPreferredSize(new Dimension(400, 500));

        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder("Vorschau"));
        previewPanel.add(previewScroll, BorderLayout.CENTER);

        // ── SplitPane ──
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, previewPanel);
        splitPane.setDividerLocation(450);
        add(splitPane, BorderLayout.CENTER);

        // ── Status bar ──
        statusBar = new JLabel(" ");
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        add(statusBar, BorderLayout.SOUTH);

        loadData();
    }

    private void loadData() {
        List<WebCacheEntry> entries = repo.getWebCacheEntries(sourceId);
        tableModel.setEntries(entries);
        updateStatusBar();
    }

    private void filterUrls(String query) {
        if (query == null || query.trim().isEmpty()) {
            loadData();
            return;
        }
        String lower = query.toLowerCase();
        List<WebCacheEntry> all = repo.getWebCacheEntries(sourceId);
        List<WebCacheEntry> filtered = new ArrayList<WebCacheEntry>();
        for (WebCacheEntry e : all) {
            if (e.getUrl().toLowerCase().contains(lower)) {
                filtered.add(e);
            }
        }
        tableModel.setEntries(filtered);
    }

    private void showPreview() {
        int row = urlTable.getSelectedRow();
        if (row < 0 || row >= tableModel.getRowCount()) {
            previewArea.setText("");
            return;
        }
        WebCacheEntry entry = tableModel.getEntry(row);
        if (entry.getArchiveEntryId() != null && !entry.getArchiveEntryId().isEmpty()) {
            ArchiveEntry archiveEntry = repo.findById(entry.getArchiveEntryId());
            if (archiveEntry != null) {
                // Load snapshot text
                try {
                    String home = System.getProperty("user.home");
                    java.io.File snapshotFile = new java.io.File(
                            home + java.io.File.separator + ".mainframemate"
                                    + java.io.File.separator + "archive"
                                    + java.io.File.separator + "snapshots"
                                    + java.io.File.separator + archiveEntry.getSnapshotPath());
                    if (snapshotFile.exists()) {
                        byte[] bytes = java.nio.file.Files.readAllBytes(snapshotFile.toPath());
                        previewArea.setText(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                        previewArea.setCaretPosition(0);
                        return;
                    }
                } catch (Exception ex) {
                    previewArea.setText("Fehler beim Laden: " + ex.getMessage());
                    return;
                }
            }
        }
        previewArea.setText("URL: " + entry.getUrl()
                + "\nStatus: " + entry.getStatus()
                + "\nTiefe: " + entry.getDepth()
                + "\nGefunden von: " + entry.getParentUrl()
                + "\n\n(Kein Snapshot vorhanden)");
    }

    private void addUrl() {
        String url = JOptionPane.showInputDialog(this, "URL eingeben:", "URL hinzufügen", JOptionPane.PLAIN_MESSAGE);
        if (url != null && !url.trim().isEmpty()) {
            WebCacheEntry entry = new WebCacheEntry();
            entry.setUrl(url.trim());
            entry.setSourceId(sourceId);
            entry.setStatus(ArchiveEntryStatus.PENDING);
            entry.setDepth(0);
            entry.setDiscoveredAt(System.currentTimeMillis());
            repo.addWebCacheEntry(entry);
            loadData();
        }
    }

    private void deleteSelected() {
        // Not implementing full delete from web_cache for now – just reload
        JOptionPane.showMessageDialog(this, "Funktion noch nicht implementiert.", "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateStatusBar() {
        int total = repo.countBySourceId(sourceId);
        int indexed = repo.countByStatus(sourceId, ArchiveEntryStatus.INDEXED);
        int pending = repo.countByStatus(sourceId, ArchiveEntryStatus.PENDING);
        int failed = repo.countByStatus(sourceId, ArchiveEntryStatus.FAILED);
        statusBar.setText(total + " URLs │ " + indexed + " indexiert │ " + pending + " ausstehend │ " + failed + " fehlgeschlagen");
    }

    // ═══════════════════════════════════════════════════════════
    //  Table model
    // ═══════════════════════════════════════════════════════════

    private static class CacheTableModel extends AbstractTableModel {
        private List<WebCacheEntry> entries = new ArrayList<WebCacheEntry>();
        private final String[] COLUMNS = {"", "URL", "Tiefe"};

        void setEntries(List<WebCacheEntry> entries) {
            this.entries = entries != null ? entries : new ArrayList<WebCacheEntry>();
            fireTableDataChanged();
        }

        WebCacheEntry getEntry(int row) {
            return entries.get(row);
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            WebCacheEntry e = entries.get(row);
            switch (col) {
                case 0: return e.getStatus();
                case 1: return e.getUrl();
                case 2: return e.getDepth();
                default: return "";
            }
        }
    }

    private static class StatusIconRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (value instanceof ArchiveEntryStatus) {
                switch ((ArchiveEntryStatus) value) {
                    case PENDING: setText("⏳"); break;
                    case CRAWLED: setText("🔄"); break;
                    case INDEXED: setText("✅"); break;
                    case FAILED:  setText("❌"); break;
                    default: setText("?");
                }
            }
            setHorizontalAlignment(CENTER);
            return this;
        }
    }
}
