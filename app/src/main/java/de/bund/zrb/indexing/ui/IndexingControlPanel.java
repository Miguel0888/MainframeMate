package de.bund.zrb.indexing.ui;

import de.bund.zrb.indexing.model.*;
import de.bund.zrb.indexing.service.IndexingService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * Indexing Control Panel – full admin UI for managing index sources and monitoring runs.
 *
 * Features:
 * - Source table with status indicators
 * - Add/Edit/Remove sources
 * - Run Now / Run All / Stop
 * - Run history and item counts per source
 * - Fine-grained policy editing per source
 *
 * Accessible via: Einstellungen → Indexierung...
 */
public class IndexingControlPanel extends JDialog implements IndexingService.IndexingListener {

    private static final Logger LOG = Logger.getLogger(IndexingControlPanel.class.getName());
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private final IndexingService service;
    private final SourceTableModel tableModel;
    private final JTable sourceTable;
    private final JLabel statusLabel;
    private final JPanel detailPanel;

    // Detail fields
    private JTextField nameField;
    private JComboBox<SourceType> typeCombo;
    private JCheckBox enabledCheck;
    private JTextArea scopeArea;
    private JTextField includeField;
    private JTextField excludeField;
    private JSpinner maxDepthSpinner;
    private JSpinner maxFileSizeSpinner;
    private JComboBox<ScheduleMode> scheduleModeCombo;
    private JSpinner intervalSpinner;
    private JComboBox<ChangeDetectionMode> changeDetectionCombo;
    private JCheckBox fulltextCheck;
    private JCheckBox embeddingCheck;
    private JSpinner chunkSizeSpinner;
    private JSpinner chunkOverlapSpinner;
    private JSpinner maxChunksSpinner;

    // Run history
    private JTextArea historyArea;
    private JLabel itemCountLabel;

    public IndexingControlPanel(JFrame parent) {
        super(parent, "Indexierung – Control Panel", true);
        this.service = IndexingService.getInstance();

        setSize(900, 700);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // Init status label early (used in button handlers)
        statusLabel = new JLabel("Bereit");

        // ── Top: Source table ──
        tableModel = new SourceTableModel();
        sourceTable = new JTable(tableModel);
        sourceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sourceTable.setRowHeight(24);
        sourceTable.getColumnModel().getColumn(0).setPreferredWidth(180); // Name
        sourceTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // Type
        sourceTable.getColumnModel().getColumn(2).setPreferredWidth(60);  // Enabled
        sourceTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Schedule
        sourceTable.getColumnModel().getColumn(4).setPreferredWidth(130); // Last Run
        sourceTable.getColumnModel().getColumn(5).setPreferredWidth(60);  // Status

        sourceTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelectedSource();
        });

        JScrollPane tableScroll = new JScrollPane(sourceTable);
        tableScroll.setPreferredSize(new Dimension(0, 200));

        // Table buttons
        JPanel tableButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("➕ Neue Quelle");
        addButton.addActionListener(e -> addSource());
        tableButtons.add(addButton);

        JButton removeButton = new JButton("➖ Entfernen");
        removeButton.addActionListener(e -> removeSource());
        tableButtons.add(removeButton);

        tableButtons.add(Box.createHorizontalStrut(20));

        JButton runNowButton = new JButton("▶ Jetzt indexieren");
        runNowButton.addActionListener(e -> runSelectedNow());
        tableButtons.add(runNowButton);

        JButton runAllButton = new JButton("▶▶ Alle indexieren");
        runAllButton.addActionListener(e -> {
            service.runAll();
            statusLabel.setText("Alle Quellen werden indexiert...");
        });
        tableButtons.add(runAllButton);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(tableScroll, BorderLayout.CENTER);
        topPanel.add(tableButtons, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // ── Center: Detail panel (split: config left, history right) ──
        detailPanel = new JPanel(new BorderLayout());
        detailPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JSplitPane detailSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        detailSplit.setLeftComponent(createConfigPanel());
        detailSplit.setRightComponent(createHistoryPanel());
        detailSplit.setDividerLocation(500);
        detailSplit.setResizeWeight(0.6);
        detailPanel.add(detailSplit, BorderLayout.CENTER);

        add(detailPanel, BorderLayout.CENTER);

        // ── Bottom: Status bar + Save/Close ──
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(4, 8, 4, 8));

        bottomPanel.add(statusLabel, BorderLayout.WEST);

        JPanel closeButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("💾 Speichern");
        saveButton.addActionListener(e -> saveSelectedSource());
        closeButtons.add(saveButton);

        JButton closeButton = new JButton("Schließen");
        closeButton.addActionListener(e -> dispose());
        closeButtons.add(closeButton);
        bottomPanel.add(closeButtons, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        // Register listener
        service.addListener(this);

        // Load data
        refreshTable();
    }

    @Override
    public void dispose() {
        service.removeListener(this);
        super.dispose();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Config panel (left side of details)
    // ═══════════════════════════════════════════════════════════════

    private JScrollPane createConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(4, 4, 4, 4));

        // ── Grundeinstellungen ──
        JPanel basicPanel = new JPanel(new GridBagLayout());
        basicPanel.setBorder(new TitledBorder("Grundeinstellungen"));
        GridBagConstraints gbc = gbc();

        basicPanel.add(label("Name:"), gbc); gbc.gridx = 1;
        nameField = new JTextField(20);
        basicPanel.add(nameField, gbc);
        gbc.gridy++; gbc.gridx = 0;

        basicPanel.add(label("Typ:"), gbc); gbc.gridx = 1;
        typeCombo = new JComboBox<>(SourceType.values());
        basicPanel.add(typeCombo, gbc);
        gbc.gridy++; gbc.gridx = 0;

        basicPanel.add(label("Aktiviert:"), gbc); gbc.gridx = 1;
        enabledCheck = new JCheckBox();
        enabledCheck.setSelected(true);
        basicPanel.add(enabledCheck, gbc);
        gbc.gridy++; gbc.gridx = 0;

        panel.add(basicPanel);

        // ── Scope ──
        JPanel scopePanel = new JPanel(new BorderLayout());
        scopePanel.setBorder(new TitledBorder("Scope (Pfade, je Zeile einer)"));
        scopeArea = new JTextArea(3, 30);
        scopeArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        scopePanel.add(new JScrollPane(scopeArea), BorderLayout.CENTER);
        panel.add(scopePanel);

        // ── Filter ──
        JPanel filterPanel = new JPanel(new GridBagLayout());
        filterPanel.setBorder(new TitledBorder("Filter & Limits"));
        gbc = gbc();

        filterPanel.add(label("Include (*.pdf,*.docx):"), gbc); gbc.gridx = 1;
        includeField = new JTextField(20);
        filterPanel.add(includeField, gbc);
        gbc.gridy++; gbc.gridx = 0;

        filterPanel.add(label("Exclude (*.tmp):"), gbc); gbc.gridx = 1;
        excludeField = new JTextField(20);
        filterPanel.add(excludeField, gbc);
        gbc.gridy++; gbc.gridx = 0;

        filterPanel.add(label("Max. Tiefe:"), gbc); gbc.gridx = 1;
        maxDepthSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        filterPanel.add(maxDepthSpinner, gbc);
        gbc.gridy++; gbc.gridx = 0;

        filterPanel.add(label("Max. Dateigröße (MB):"), gbc); gbc.gridx = 1;
        maxFileSizeSpinner = new JSpinner(new SpinnerNumberModel(50, 1, 1000, 10));
        filterPanel.add(maxFileSizeSpinner, gbc);
        gbc.gridy++; gbc.gridx = 0;

        panel.add(filterPanel);

        // ── Schedule ──
        JPanel schedPanel = new JPanel(new GridBagLayout());
        schedPanel.setBorder(new TitledBorder("Zeitplan"));
        gbc = gbc();

        schedPanel.add(label("Modus:"), gbc); gbc.gridx = 1;
        scheduleModeCombo = new JComboBox<>(ScheduleMode.values());
        schedPanel.add(scheduleModeCombo, gbc);
        gbc.gridy++; gbc.gridx = 0;

        schedPanel.add(label("Intervall (Min.):"), gbc); gbc.gridx = 1;
        intervalSpinner = new JSpinner(new SpinnerNumberModel(60, 1, 1440, 5));
        schedPanel.add(intervalSpinner, gbc);
        gbc.gridy++; gbc.gridx = 0;

        schedPanel.add(label("Änderungserkennung:"), gbc); gbc.gridx = 1;
        changeDetectionCombo = new JComboBox<>(ChangeDetectionMode.values());
        schedPanel.add(changeDetectionCombo, gbc);

        panel.add(schedPanel);

        // ── Processing ──
        JPanel procPanel = new JPanel(new GridBagLayout());
        procPanel.setBorder(new TitledBorder("Verarbeitung"));
        gbc = gbc();

        procPanel.add(label("Volltext-Index:"), gbc); gbc.gridx = 1;
        fulltextCheck = new JCheckBox(); fulltextCheck.setSelected(true);
        procPanel.add(fulltextCheck, gbc);
        gbc.gridy++; gbc.gridx = 0;

        procPanel.add(label("Embedding/Vektor:"), gbc); gbc.gridx = 1;
        embeddingCheck = new JCheckBox(); embeddingCheck.setSelected(true);
        procPanel.add(embeddingCheck, gbc);
        gbc.gridy++; gbc.gridx = 0;

        procPanel.add(label("Chunk-Größe:"), gbc); gbc.gridx = 1;
        chunkSizeSpinner = new JSpinner(new SpinnerNumberModel(512, 64, 4096, 64));
        procPanel.add(chunkSizeSpinner, gbc);
        gbc.gridy++; gbc.gridx = 0;

        procPanel.add(label("Chunk-Overlap:"), gbc); gbc.gridx = 1;
        chunkOverlapSpinner = new JSpinner(new SpinnerNumberModel(64, 0, 512, 16));
        procPanel.add(chunkOverlapSpinner, gbc);
        gbc.gridy++; gbc.gridx = 0;

        procPanel.add(label("Max. Chunks/Item:"), gbc); gbc.gridx = 1;
        maxChunksSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 1000, 10));
        procPanel.add(maxChunksSpinner, gbc);

        panel.add(procPanel);

        // ── Info text ──
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(new EmptyBorder(8, 4, 4, 4));
        JLabel infoLabel = new JLabel("<html><small>"
                + "<b>Inkrementelle Indexierung:</b> Nur Änderungen seit dem letzten Lauf werden verarbeitet.<br>"
                + "Modus MTIME_SIZE: schnell (Timestamp+Größe). CONTENT_HASH: sicher (SHA-256).<br>"
                + "MTIME_THEN_HASH: Kombination – schnell wenn eindeutig, Hash bei Unklarheit.<br><br>"
                + "<b>Lückenfreiheit:</b> Ein Lauf gilt erst als erfolgreich, wenn alle Items verarbeitet sind.<br>"
                + "Bei Abbruch/Fehler wird der High-Water-Mark NICHT aktualisiert → nächster Lauf wiederholt."
                + "</small></html>");
        infoLabel.setForeground(Color.GRAY);
        infoPanel.add(infoLabel, BorderLayout.CENTER);
        panel.add(infoPanel);

        return new JScrollPane(panel);
    }

    // ═══════════════════════════════════════════════════════════════
    //  History panel (right side of details)
    // ═══════════════════════════════════════════════════════════════

    private JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Status & History"));

        // Item counts
        itemCountLabel = new JLabel("<html>Wähle eine Quelle aus.</html>");
        itemCountLabel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(itemCountLabel, BorderLayout.NORTH);

        // Run history
        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        panel.add(new JScrollPane(historyArea), BorderLayout.CENTER);

        return panel;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Actions
    // ═══════════════════════════════════════════════════════════════

    private void addSource() {
        IndexSource source = new IndexSource();
        source.setName("Neue Quelle");
        service.saveSource(source);
        refreshTable();
        // Select the new source
        int row = tableModel.getRowCount() - 1;
        sourceTable.setRowSelectionInterval(row, row);
        loadSelectedSource();
    }

    private void removeSource() {
        int row = sourceTable.getSelectedRow();
        if (row < 0) return;
        IndexSource source = tableModel.getSourceAt(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Quelle '" + source.getName() + "' und alle Indexdaten entfernen?",
                "Quelle entfernen", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            service.removeSource(source.getSourceId());
            refreshTable();
        }
    }

    private void runSelectedNow() {
        int row = sourceTable.getSelectedRow();
        if (row < 0) {
            statusLabel.setText("Bitte Quelle auswählen.");
            return;
        }
        IndexSource source = tableModel.getSourceAt(row);
        service.runNow(source.getSourceId());
        statusLabel.setText("Indexierung gestartet: " + source.getName());
    }

    private void saveSelectedSource() {
        int row = sourceTable.getSelectedRow();
        if (row < 0) return;
        IndexSource source = tableModel.getSourceAt(row);
        applyFieldsToSource(source);
        service.saveSource(source);
        refreshTable();
        statusLabel.setText("Gespeichert: " + source.getName());
    }

    private void loadSelectedSource() {
        int row = sourceTable.getSelectedRow();
        if (row < 0) return;
        IndexSource source = tableModel.getSourceAt(row);

        nameField.setText(source.getName());
        typeCombo.setSelectedItem(source.getSourceType());
        enabledCheck.setSelected(source.isEnabled());

        StringBuilder scopeText = new StringBuilder();
        for (String p : source.getScopePaths()) scopeText.append(p).append("\n");
        scopeArea.setText(scopeText.toString().trim());

        includeField.setText(String.join(",", source.getIncludePatterns()));
        excludeField.setText(String.join(",", source.getExcludePatterns()));
        maxDepthSpinner.setValue(source.getMaxDepth());
        maxFileSizeSpinner.setValue((int) (source.getMaxFileSizeBytes() / (1024 * 1024)));
        scheduleModeCombo.setSelectedItem(source.getScheduleMode());
        intervalSpinner.setValue(source.getIntervalMinutes());
        changeDetectionCombo.setSelectedItem(source.getChangeDetection());
        fulltextCheck.setSelected(source.isFulltextEnabled());
        embeddingCheck.setSelected(source.isEmbeddingEnabled());
        chunkSizeSpinner.setValue(source.getChunkSize());
        chunkOverlapSpinner.setValue(source.getChunkOverlap());
        maxChunksSpinner.setValue(source.getMaxChunksPerItem());

        // Load item counts
        Map<IndexItemState, Integer> counts = service.getItemCounts(source.getSourceId());
        int total = 0;
        for (int v : counts.values()) total += v;
        itemCountLabel.setText("<html>"
                + "<b>Items: " + total + "</b><br>"
                + "✅ Indexiert: " + counts.getOrDefault(IndexItemState.INDEXED, 0) + "<br>"
                + "⏳ Ausstehend: " + counts.getOrDefault(IndexItemState.PENDING, 0) + "<br>"
                + "❌ Fehler: " + counts.getOrDefault(IndexItemState.ERROR, 0) + "<br>"
                + "⬜ Übersprungen: " + counts.getOrDefault(IndexItemState.SKIPPED, 0) + "<br>"
                + "🗑 Gelöscht: " + counts.getOrDefault(IndexItemState.DELETED, 0)
                + "</html>");

        // Load run history
        List<IndexRunStatus> runs = service.getRunHistory(source.getSourceId());
        StringBuilder histText = new StringBuilder();
        for (IndexRunStatus run : runs) {
            String state = run.getRunState().name();
            String date = run.getStartedAt() > 0 ? DATE_FMT.format(new Date(run.getStartedAt())) : "?";
            long dur = run.getDurationMs();
            histText.append(date).append("  ").append(state)
                    .append("  (").append(dur / 1000).append("s)")
                    .append("  scanned=").append(run.getItemsScanned())
                    .append(" new=").append(run.getItemsNew())
                    .append(" changed=").append(run.getItemsChanged())
                    .append(" errors=").append(run.getItemsErrored());
            if (run.getLastError() != null) {
                histText.append("\n  ⚠ ").append(run.getLastError());
            }
            histText.append("\n");
        }
        if (runs.isEmpty()) histText.append("(Noch keine Läufe)");
        historyArea.setText(histText.toString());
        historyArea.setCaretPosition(0);
    }

    private void applyFieldsToSource(IndexSource source) {
        source.setName(nameField.getText().trim());
        source.setSourceType((SourceType) typeCombo.getSelectedItem());
        source.setEnabled(enabledCheck.isSelected());

        // Scope paths
        String[] lines = scopeArea.getText().split("\\n");
        List<String> paths = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) paths.add(trimmed);
        }
        source.setScopePaths(paths);

        // Include/Exclude
        source.setIncludePatterns(splitComma(includeField.getText()));
        source.setExcludePatterns(splitComma(excludeField.getText()));

        source.setMaxDepth((Integer) maxDepthSpinner.getValue());
        source.setMaxFileSizeBytes(((Integer) maxFileSizeSpinner.getValue()) * 1024L * 1024L);
        source.setScheduleMode((ScheduleMode) scheduleModeCombo.getSelectedItem());
        source.setIntervalMinutes((Integer) intervalSpinner.getValue());
        source.setChangeDetection((ChangeDetectionMode) changeDetectionCombo.getSelectedItem());
        source.setFulltextEnabled(fulltextCheck.isSelected());
        source.setEmbeddingEnabled(embeddingCheck.isSelected());
        source.setChunkSize((Integer) chunkSizeSpinner.getValue());
        source.setChunkOverlap((Integer) chunkOverlapSpinner.getValue());
        source.setMaxChunksPerItem((Integer) maxChunksSpinner.getValue());
    }

    private void refreshTable() {
        tableModel.setSources(service.getAllSources());
    }

    // ═══════════════════════════════════════════════════════════════
    //  IndexingListener callbacks (for live updates)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void onRunStarted(String sourceId) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("⏳ Indexierung läuft: " + sourceId);
            refreshTable();
        });
    }

    @Override
    public void onRunCompleted(String sourceId, IndexRunStatus result) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("✅ Abgeschlossen: " + sourceId
                    + " (" + result.getItemsScanned() + " gescannt, "
                    + result.getItemsNew() + " neu, "
                    + result.getItemsChanged() + " geändert)");
            refreshTable();
            loadSelectedSource(); // refresh detail view
        });
    }

    @Override
    public void onRunFailed(String sourceId, String error) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("❌ Fehlgeschlagen: " + sourceId + " – " + error);
            refreshTable();
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    private static GridBagConstraints gbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.5;
        return gbc;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 12f));
        return l;
    }

    private static List<String> splitComma(String text) {
        List<String> list = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return list;
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) list.add(trimmed);
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Source Table Model
    // ═══════════════════════════════════════════════════════════════

    private class SourceTableModel extends AbstractTableModel {
        private List<IndexSource> sources = new ArrayList<>();
        private final String[] COLUMNS = {"Name", "Typ", "Aktiv", "Zeitplan", "Letzter Lauf", "Status"};

        void setSources(List<IndexSource> sources) {
            this.sources = sources;
            fireTableDataChanged();
        }

        IndexSource getSourceAt(int row) {
            return sources.get(row);
        }

        @Override public int getRowCount() { return sources.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            IndexSource s = sources.get(row);
            switch (col) {
                case 0: return s.getName();
                case 1: return s.getSourceType().getDisplayName();
                case 2: return s.isEnabled() ? "✅" : "⬜";
                case 3: return s.getScheduleMode().name();
                case 4:
                    IndexRunStatus last = service.getLastSuccessfulRun(s.getSourceId());
                    return last != null ? DATE_FMT.format(new Date(last.getCompletedAt())) : "–";
                case 5:
                    if (service.isRunning(s.getSourceId())) return "⏳";
                    IndexRunStatus lastRun = service.getLastSuccessfulRun(s.getSourceId());
                    return lastRun != null ? "✅" : "–";
                default: return "";
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Static factory method
    // ═══════════════════════════════════════════════════════════════

    public static void show(JFrame parent) {
        new IndexingControlPanel(parent).setVisible(true);
    }
}
