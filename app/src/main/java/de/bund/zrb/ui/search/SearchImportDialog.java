package de.bund.zrb.ui.search;

import de.bund.zrb.search.io.SearchDataExportImportService;
import de.bund.zrb.search.io.SearchDataExportImportService.ImportEntry;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Modal dialog for importing search index + archive data from a ZIP file.
 * Shows duplicate conflicts and lets the user decide per file or bulk.
 */
public final class SearchImportDialog extends JDialog {

    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Bereit.");
    private final JButton importButton = new JButton("📥 Importieren");
    private final JButton closeButton = new JButton("Schließen");

    private File zipFile;
    private List<ImportEntry> entries = new ArrayList<ImportEntry>();
    private ConflictTableModel conflictModel;
    private JTable conflictTable;

    public SearchImportDialog(Window owner) {
        super(owner, "Suchdaten importieren", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(700, 500));
        setPreferredSize(new Dimension(800, 560));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(new EmptyBorder(12, 16, 12, 16));

        // File chooser panel
        JPanel filePanel = new JPanel(new BorderLayout(6, 0));
        filePanel.setBorder(BorderFactory.createTitledBorder("ZIP-Datei auswählen"));
        JTextField fileField = new JTextField();
        fileField.setEditable(false);
        JButton browseBtn = new JButton("Durchsuchen…");
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Export-ZIP auswählen");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("ZIP-Archiv (*.zip)", "zip"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                zipFile = fc.getSelectedFile();
                fileField.setText(zipFile.getAbsolutePath());
                scanZipFile();
            }
        });
        filePanel.add(fileField, BorderLayout.CENTER);
        filePanel.add(browseBtn, BorderLayout.EAST);
        content.add(filePanel, BorderLayout.NORTH);

        // Conflict table
        conflictModel = new ConflictTableModel();
        conflictTable = new JTable(conflictModel);
        conflictTable.setRowHeight(22);
        conflictTable.getColumnModel().getColumn(0).setPreferredWidth(350); // Path
        conflictTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // Size
        conflictTable.getColumnModel().getColumn(2).setPreferredWidth(80);  // Status
        conflictTable.getColumnModel().getColumn(3).setPreferredWidth(110); // Action

        // Color rows: green for new, yellow for conflict
        conflictTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected && row < entries.size()) {
                    ImportEntry entry = entries.get(row);
                    if (entry.exists) {
                        c.setBackground(new Color(255, 255, 200)); // yellow for conflict
                    } else {
                        c.setBackground(new Color(220, 255, 220)); // green for new
                    }
                }
                return c;
            }
        });

        JScrollPane tableScroll = new JScrollPane(conflictTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Dateien im Export"));

        // Bulk action buttons
        JPanel bulkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton overwriteAllBtn = new JButton("Alle überschreiben");
        overwriteAllBtn.addActionListener(e -> setAllConflicts(true));
        JButton skipAllBtn = new JButton("Alle überspringen");
        skipAllBtn.addActionListener(e -> setAllConflicts(false));
        JLabel bulkHint = new JLabel("  (betrifft nur Duplikate)");
        bulkHint.setForeground(Color.GRAY);
        bulkPanel.add(overwriteAllBtn);
        bulkPanel.add(skipAllBtn);
        bulkPanel.add(bulkHint);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 4));
        centerPanel.add(tableScroll, BorderLayout.CENTER);
        centerPanel.add(bulkPanel, BorderLayout.SOUTH);
        content.add(centerPanel, BorderLayout.CENTER);

        // Progress + buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        progressBar.setStringPainted(true);
        progressBar.setString("");
        bottomPanel.add(progressBar, BorderLayout.NORTH);
        bottomPanel.add(statusLabel, BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        importButton.setEnabled(false);
        importButton.addActionListener(e -> doImport());
        closeButton.addActionListener(e -> dispose());
        buttonBar.add(importButton);
        buttonBar.add(closeButton);
        bottomPanel.add(buttonBar, BorderLayout.SOUTH);

        content.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    private void scanZipFile() {
        if (zipFile == null) return;
        statusLabel.setText("ZIP wird gescannt…");
        importButton.setEnabled(false);

        new SwingWorker<List<ImportEntry>, Void>() {
            @Override
            protected List<ImportEntry> doInBackground() throws Exception {
                return SearchDataExportImportService.scanZip(zipFile);
            }

            @Override
            protected void done() {
                try {
                    entries = get();
                    conflictModel.fireTableDataChanged();
                    long conflicts = 0;
                    for (ImportEntry e : entries) {
                        if (e.exists) conflicts++;
                    }
                    statusLabel.setText(entries.size() + " Dateien gefunden, "
                            + conflicts + " Duplikate.");
                    importButton.setEnabled(true);

                    // Default: new files = import, conflicts = overwrite
                    for (ImportEntry e : entries) {
                        e.overwrite = true;
                    }
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    statusLabel.setText("❌ Fehler: " + msg);
                }
            }
        }.execute();
    }

    private void setAllConflicts(boolean overwrite) {
        for (ImportEntry e : entries) {
            if (e.exists) {
                e.overwrite = overwrite;
            }
        }
        conflictModel.fireTableDataChanged();
    }

    private void doImport() {
        // Check for unresolved conflicts
        for (ImportEntry e : entries) {
            if (e.exists && e.overwrite == null) {
                JOptionPane.showMessageDialog(this,
                        "Bitte alle Konflikte auflösen (Überschreiben/Überspringen).",
                        "Import", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        importButton.setEnabled(false);
        closeButton.setEnabled(false);

        new SwingWorker<Void, int[]>() {
            private String lastMessage = "";

            @Override
            protected Void doInBackground() throws Exception {
                SearchDataExportImportService.importFromZip(zipFile, entries,
                        (pct, msg) -> {
                            lastMessage = msg;
                            publish(new int[]{pct});
                        });
                return null;
            }

            @Override
            protected void process(java.util.List<int[]> chunks) {
                int[] last = chunks.get(chunks.size() - 1);
                progressBar.setValue(last[0]);
                progressBar.setString(last[0] + "%");
                statusLabel.setText(lastMessage);
            }

            @Override
            protected void done() {
                importButton.setEnabled(true);
                closeButton.setEnabled(true);
                try {
                    get();
                    progressBar.setValue(100);
                    progressBar.setString("100%");
                    statusLabel.setText("✅ Import abgeschlossen.");
                    JOptionPane.showMessageDialog(SearchImportDialog.this,
                            "Import erfolgreich!\nLucene-Index und Archiv wurden aktualisiert.",
                            "Import", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    statusLabel.setText("❌ Fehler: " + msg);
                    JOptionPane.showMessageDialog(SearchImportDialog.this,
                            "Import fehlgeschlagen:\n" + msg,
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ═══════════════════════════════════════════════════════════
    //  Table model
    // ═══════════════════════════════════════════════════════════

    private class ConflictTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"Datei", "Größe", "Status", "Aktion"};

        @Override
        public int getRowCount() { return entries.size(); }

        @Override
        public int getColumnCount() { return COLUMNS.length; }

        @Override
        public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public boolean isCellEditable(int row, int col) {
            // Only the "Action" column is editable for conflicts
            return col == 3 && entries.get(row).exists;
        }

        @Override
        public Object getValueAt(int row, int col) {
            ImportEntry e = entries.get(row);
            switch (col) {
                case 0: return e.zipPath;
                case 1: return e.size > 0 ? formatSize(e.size) : "–";
                case 2: return e.exists ? "⚠ Duplikat" : "✅ Neu";
                case 3:
                    if (!e.exists) return "Importieren";
                    return Boolean.TRUE.equals(e.overwrite) ? "Überschreiben" : "Überspringen";
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 3 && row < entries.size()) {
                ImportEntry e = entries.get(row);
                if (e.exists) {
                    // Toggle on click
                    e.overwrite = !Boolean.TRUE.equals(e.overwrite);
                    fireTableCellUpdated(row, col);
                }
            }
        }

        @Override
        public Class<?> getColumnClass(int col) { return String.class; }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}

