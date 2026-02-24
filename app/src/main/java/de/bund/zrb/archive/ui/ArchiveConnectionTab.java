package de.bund.zrb.archive.ui;

import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.model.ArchiveEntryStatus;
import de.bund.zrb.archive.store.ArchiveRepository;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Connection tab for the Archive system.
 * Shows all archived documents (web snapshots + manually imported files)
 * with a preview panel.
 */
public class ArchiveConnectionTab implements ConnectionTab {

    private final JPanel mainPanel;
    private final ArchiveRepository repo;
    private final ArchiveTableModel tableModel;
    private final JTable archiveTable;
    private final JTextArea previewArea;
    private final JTextField searchField;
    private final JLabel statusLabel;
    private final TabbedPaneManager tabbedPaneManager;

    public ArchiveConnectionTab(TabbedPaneManager tabbedPaneManager) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.repo = ArchiveRepository.getInstance();
        this.mainPanel = new JPanel(new BorderLayout(4, 4));

        // ── Toolbar ──
        JPanel toolbar = new JPanel(new BorderLayout(4, 0));
        JButton refreshBtn = new JButton("🔄");
        refreshBtn.setToolTipText("Aktualisieren");
        refreshBtn.addActionListener(e -> loadEntries());

        searchField = new JTextField();
        searchField.setToolTipText("Archiv durchsuchen...");
        searchField.addActionListener(e -> filterEntries());

        JButton importBtn = new JButton("📥 Import");
        importBtn.setToolTipText("Datei ins Archiv importieren");
        importBtn.addActionListener(e -> importFile());

        toolbar.add(refreshBtn, BorderLayout.WEST);
        toolbar.add(searchField, BorderLayout.CENTER);
        toolbar.add(importBtn, BorderLayout.EAST);
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // ── Table ──
        tableModel = new ArchiveTableModel();
        archiveTable = new JTable(tableModel);
        archiveTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        archiveTable.getColumnModel().getColumn(0).setMaxWidth(40);
        archiveTable.getColumnModel().getColumn(2).setMaxWidth(80);
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
        List<ArchiveEntry> entries = repo.findAll();
        tableModel.setEntries(entries);
        updateStatus(entries);
    }

    private void filterEntries() {
        String query = searchField.getText();
        if (query == null || query.trim().isEmpty()) {
            loadEntries();
            return;
        }
        String lower = query.toLowerCase();
        List<ArchiveEntry> all = repo.findAll();
        List<ArchiveEntry> filtered = new ArrayList<ArchiveEntry>();
        for (ArchiveEntry e : all) {
            if (e.getTitle().toLowerCase().contains(lower)
                    || e.getUrl().toLowerCase().contains(lower)) {
                filtered.add(e);
            }
        }
        tableModel.setEntries(filtered);
        updateStatus(filtered);
    }

    private void showPreview() {
        int row = archiveTable.getSelectedRow();
        if (row < 0) {
            previewArea.setText("");
            return;
        }
        ArchiveEntry entry = tableModel.getEntry(row);
        if (entry.getSnapshotPath() != null && !entry.getSnapshotPath().isEmpty()) {
            try {
                String home = System.getProperty("user.home");
                File snapshotFile = new File(home + File.separator + ".mainframemate"
                        + File.separator + "archive" + File.separator + "snapshots"
                        + File.separator + entry.getSnapshotPath());
                if (snapshotFile.exists()) {
                    byte[] bytes = Files.readAllBytes(snapshotFile.toPath());
                    previewArea.setText(new String(bytes, StandardCharsets.UTF_8));
                    previewArea.setCaretPosition(0);
                    return;
                }
            } catch (Exception ex) {
                previewArea.setText("Fehler: " + ex.getMessage());
                return;
            }
        }
        previewArea.setText("Titel: " + entry.getTitle()
                + "\nURL: " + entry.getUrl()
                + "\nStatus: " + entry.getStatus()
                + "\nMIME: " + entry.getMimeType()
                + "\n\n(Kein Snapshot gefunden)");
    }

    private void importFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Dateien ins Archiv importieren");
        int result = chooser.showOpenDialog(mainPanel);
        if (result == JFileChooser.APPROVE_OPTION) {
            for (File file : chooser.getSelectedFiles()) {
                try {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    String content = new String(bytes, StandardCharsets.UTF_8);

                    // Copy file to archive/snapshots/manual/
                    String home = System.getProperty("user.home");
                    File manualDir = new File(home + File.separator + ".mainframemate"
                            + File.separator + "archive" + File.separator + "snapshots" + File.separator + "manual");
                    if (!manualDir.exists()) manualDir.mkdirs();

                    File target = new File(manualDir, file.getName());
                    Files.copy(file.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                    ArchiveEntry entry = new ArchiveEntry();
                    entry.setTitle(file.getName());
                    entry.setMimeType(guessMimeType(file.getName()));
                    entry.setSnapshotPath("manual" + File.separator + file.getName());
                    entry.setContentLength(content.length());
                    entry.setFileSizeBytes(file.length());
                    entry.setCrawlTimestamp(System.currentTimeMillis());
                    entry.setLastIndexed(System.currentTimeMillis());
                    entry.setStatus(ArchiveEntryStatus.INDEXED);
                    repo.save(entry);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(mainPanel,
                            "Import fehlgeschlagen: " + file.getName() + "\n" + ex.getMessage(),
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
            loadEntries();
        }
    }

    private String guessMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "application/octet-stream";
    }

    private void updateStatus(List<ArchiveEntry> entries) {
        long totalSize = 0;
        for (ArchiveEntry e : entries) totalSize += e.getFileSizeBytes();
        statusLabel.setText(entries.size() + " Dokumente │ " + formatSize(totalSize));
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }


    // ── ConnectionTab / FtpTab interface ──

    @Override
    public String getTitle() {
        return "📦 Archiv";
    }

    @Override
    public String getTooltip() {
        return "Archivierte Webseiten und Dokumente";
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        // nothing to clean up
    }

    @Override
    public void saveIfApplicable() {
        // read-only tab
    }

    @Override
    public String getContent() {
        return "";
    }

    @Override
    public void markAsChanged() {
        // not applicable
    }

    @Override
    public String getPath() {
        return "archive://";
    }

    @Override
    public Type getType() {
        return Type.CONNECTION;
    }

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

    private static class ArchiveTableModel extends AbstractTableModel {
        private List<ArchiveEntry> entries = new ArrayList<ArchiveEntry>();
        private final String[] COLUMNS = {"", "Titel", "Status", "URL"};

        void setEntries(List<ArchiveEntry> entries) {
            this.entries = entries != null ? entries : new ArrayList<ArchiveEntry>();
            fireTableDataChanged();
        }

        ArchiveEntry getEntry(int row) {
            return entries.get(row);
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            ArchiveEntry e = entries.get(row);
            switch (col) {
                case 0:
                    return e.getUrl() != null && !e.getUrl().isEmpty() ? "🌐" : "📄";
                case 1: return e.getTitle();
                case 2: return e.getStatus().name();
                case 3: return e.getUrl();
                default: return "";
            }
        }
    }
}
