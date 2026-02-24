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
    private JSpinner startHourSpinner;
    private JSpinner startMinuteSpinner;
    private JSpinner maxDurationSpinner;
    private JComboBox<IndexDirection> indexDirectionCombo;
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

        JButton cloneButton = new JButton("📋 Klonen");
        cloneButton.addActionListener(e -> cloneSelectedSource());
        tableButtons.add(cloneButton);

        JButton removeButton = new JButton("➖ Entfernen");
        removeButton.addActionListener(e -> removeSource());
        tableButtons.add(removeButton);

        tableButtons.add(Box.createHorizontalStrut(10));

        JButton presetsButton = new JButton("📦 Vorlagen laden");
        presetsButton.addActionListener(e -> loadPresets());
        tableButtons.add(presetsButton);

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

        schedPanel.add(label("Startzeit (Stunde):"), gbc); gbc.gridx = 1;
        startHourSpinner = new JSpinner(new SpinnerNumberModel(12, 0, 23, 1));
        schedPanel.add(startHourSpinner, gbc);
        gbc.gridy++; gbc.gridx = 0;

        schedPanel.add(label("Startzeit (Minute):"), gbc); gbc.gridx = 1;
        startMinuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 5));
        schedPanel.add(startMinuteSpinner, gbc);
        gbc.gridy++; gbc.gridx = 0;

        schedPanel.add(label("Max. Dauer (Min., 0=∞):"), gbc); gbc.gridx = 1;
        maxDurationSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 480, 5));
        schedPanel.add(maxDurationSpinner, gbc);
        gbc.gridy++; gbc.gridx = 0;

        schedPanel.add(label("Reihenfolge:"), gbc); gbc.gridx = 1;
        indexDirectionCombo = new JComboBox<>(IndexDirection.values());
        schedPanel.add(indexDirectionCombo, gbc);
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

        // Top: Item counts + action buttons
        JPanel topPanel = new JPanel(new BorderLayout());

        itemCountLabel = new JLabel("<html>Wähle eine Quelle aus.</html>");
        itemCountLabel.setBorder(new EmptyBorder(8, 8, 8, 8));
        topPanel.add(itemCountLabel, BorderLayout.CENTER);

        // Small icon buttons (no margin/padding)
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        actionButtons.setBorder(new EmptyBorder(4, 0, 4, 4));

        JButton showIndexBtn = new JButton("📋");
        showIndexBtn.setToolTipText("Alle indexierten Dokumente anzeigen");
        showIndexBtn.setMargin(new Insets(0, 0, 0, 0));
        showIndexBtn.setFont(showIndexBtn.getFont().deriveFont(14f));
        showIndexBtn.setBorderPainted(false);
        showIndexBtn.setContentAreaFilled(false);
        showIndexBtn.setFocusable(false);
        showIndexBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        showIndexBtn.addActionListener(e -> showIndexedDocuments());
        actionButtons.add(showIndexBtn);

        JButton reindexBtn = new JButton("🔄");
        reindexBtn.setToolTipText("Index komplett neu aufbauen (löscht alle Status-Daten und indexiert alles neu)");
        reindexBtn.setMargin(new Insets(0, 0, 0, 0));
        reindexBtn.setFont(reindexBtn.getFont().deriveFont(14f));
        reindexBtn.setBorderPainted(false);
        reindexBtn.setContentAreaFilled(false);
        reindexBtn.setFocusable(false);
        reindexBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        reindexBtn.addActionListener(e -> forceReindexSelected());
        actionButtons.add(reindexBtn);

        topPanel.add(actionButtons, BorderLayout.EAST);
        panel.add(topPanel, BorderLayout.NORTH);

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

    private void cloneSelectedSource() {
        int row = sourceTable.getSelectedRow();
        if (row < 0) {
            statusLabel.setText("Bitte Quelle zum Klonen auswählen.");
            return;
        }
        IndexSource original = tableModel.getSourceAt(row);

        // Create a deep copy via JSON serialization
        com.google.gson.Gson gson = new com.google.gson.Gson();
        IndexSource clone = gson.fromJson(gson.toJson(original), IndexSource.class);
        clone.setSourceId(java.util.UUID.randomUUID().toString());
        clone.setName(original.getName() + " (Kopie)");

        service.saveSource(clone);
        refreshTable();
        // Select the clone
        int newRow = tableModel.getRowCount() - 1;
        sourceTable.setRowSelectionInterval(newRow, newRow);
        loadSelectedSource();
        statusLabel.setText("Quelle geklont: " + clone.getName());
    }

    private void loadPresets() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Standard-Vorlagen hinzufügen?\n\n"
                        + "• Eigene Dateien (PDF, DOCX, etc.)\n"
                        + "• E-Mails (OST/PST)\n"
                        + "• Kalender / Termine\n\n"
                        + "Bestehende Quellen bleiben erhalten.",
                "Vorlagen laden", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) return;

        for (IndexSource preset : IndexSourcePresets.allDefaults()) {
            service.saveSource(preset);
        }
        refreshTable();
        statusLabel.setText("3 Vorlagen hinzugefügt: Eigene Dateien, E-Mails, Kalender");
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

    /**
     * Force a complete re-index of the selected source.
     * Clears all item statuses + Lucene index and runs from scratch.
     */
    private void forceReindexSelected() {
        int row = sourceTable.getSelectedRow();
        if (row < 0) {
            statusLabel.setText("Bitte Quelle zum Reindexieren auswählen.");
            return;
        }
        IndexSource source = tableModel.getSourceAt(row);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Index für '" + source.getName() + "' komplett neu aufbauen?\n\n"
                        + "• Alle bisherigen Status-Daten werden gelöscht\n"
                        + "• Der Lucene-Index wird geleert\n"
                        + "• Alle Dateien werden neu indexiert\n\n"
                        + "Dies kann bei vielen Dateien einige Zeit dauern.",
                "Reindex bestätigen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        service.forceReindex(source.getSourceId());
        statusLabel.setText("🔄 Reindex gestartet: " + source.getName());
    }

    /**
     * Show all documents currently in the Lucene index in a scrollable dialog.
     */
    private void showIndexedDocuments() {
        de.bund.zrb.rag.service.RagService rag = de.bund.zrb.rag.service.RagService.getInstance();
        java.util.Map<String, String> docs = rag.listAllIndexedDocuments();

        if (docs.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Der Index ist leer.\n\nBitte zuerst eine Indexierung starten.",
                    "Index leer", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Build table data
        String[] columns = {"#", "Dateiname / Quelle", "Dokument-ID (intern)"};
        Object[][] data = new Object[docs.size()][3];
        int i = 0;
        for (java.util.Map.Entry<String, String> entry : docs.entrySet()) {
            data[i][0] = i + 1;
            data[i][1] = entry.getValue();
            data[i][2] = entry.getKey();
            i++;
        }

        JTable table = new JTable(data, columns) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(2).setPreferredWidth(400);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(750, 400));

        JLabel summaryLabel = new JLabel(docs.size() + " Dokumente im Index");
        summaryLabel.setBorder(new EmptyBorder(4, 4, 4, 4));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(summaryLabel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(this, panel,
                "Indexierte Dokumente (" + docs.size() + ")",
                JOptionPane.PLAIN_MESSAGE);
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
        startHourSpinner.setValue(source.getStartHour());
        startMinuteSpinner.setValue(source.getStartMinute());
        maxDurationSpinner.setValue(source.getMaxDurationMinutes());
        indexDirectionCombo.setSelectedItem(source.getIndexDirection());
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
        source.setStartHour((Integer) startHourSpinner.getValue());
        source.setStartMinute((Integer) startMinuteSpinner.getValue());
        source.setMaxDurationMinutes((Integer) maxDurationSpinner.getValue());
        source.setIndexDirection((IndexDirection) indexDirectionCombo.getSelectedItem());
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
