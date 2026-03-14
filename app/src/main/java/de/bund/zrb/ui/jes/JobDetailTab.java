package de.bund.zrb.ui.jes;

import de.bund.zrb.files.impl.ftp.jes.JesFtpService;
import de.bund.zrb.files.impl.ftp.jes.JesJob;
import de.bund.zrb.files.impl.ftp.jes.JesSpoolFile;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.zrb.bund.newApi.ui.FtpTab;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tab showing spool details for a single JES job.
 * <p>
 * Analogous to FileTab but for JES spool output: lists spool files on the left,
 * shows content of the selected spool file on the right.
 */
public class JobDetailTab implements FtpTab {

    private static final Logger LOG = Logger.getLogger(JobDetailTab.class.getName());
    private static final String[] SPOOL_COLUMNS = {"#", "DD-Name", "Step", "Proc", "Class", "Records", "Bytes"};

    private final JesFtpService service;
    private final JesJob job;

    private final JPanel mainPanel;
    private final JTable spoolTable;
    private final SpoolTableModel spoolModel;
    private final JTextArea contentArea;
    private final JLabel statusLabel;
    private final JTextField searchField;
    private final JLabel searchCountLabel;
    private int lastSearchPos = 0;

    public JobDetailTab(JesFtpService service, JesJob job) {
        this.service = service;
        this.job = job;
        this.spoolModel = new SpoolTableModel();

        mainPanel = new JPanel(new BorderLayout(0, 4));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // ── Info header ─────────────────────────────────────────────
        JPanel infoPanel = new JPanel(new BorderLayout(8, 0));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Job-Info"));

        JPanel infoLabels = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        infoLabels.add(createInfoLabel("Job-ID:", job.getJobId()));
        infoLabels.add(createInfoLabel("Name:", job.getJobName()));
        infoLabels.add(createInfoLabel("Owner:", job.getOwner()));
        infoLabels.add(createInfoLabel("Status:", job.getStatus()));
        if (job.getRetCode() != null && !job.getRetCode().isEmpty()) {
            infoLabels.add(createInfoLabel("RC:", job.getRetCode()));
        }
        if (job.getJobClass() != null && !job.getJobClass().isEmpty()) {
            infoLabels.add(createInfoLabel("Class:", job.getJobClass()));
        }
        infoPanel.add(infoLabels, BorderLayout.CENTER);

        JPanel infoButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        JButton allButton = new JButton("📑 Gesamter Output");
        allButton.setToolTipText("Alle Spool-Files zusammen laden");
        allButton.addActionListener(e -> loadAllSpool());
        infoButtons.add(allButton);

        JButton deleteButton = new JButton("🗑️ Job löschen");
        deleteButton.addActionListener(e -> deleteJob());
        infoButtons.add(deleteButton);
        infoPanel.add(infoButtons, BorderLayout.EAST);

        mainPanel.add(infoPanel, BorderLayout.NORTH);

        // ── Split pane: spool list (top) + content (bottom) ─────────
        spoolTable = new JTable(spoolModel);
        spoolTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        spoolTable.setRowHeight(22);
        spoolTable.setFillsViewportHeight(true);
        spoolTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelectedSpool();
        });
        spoolTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) loadSelectedSpool();
            }
        });

        JScrollPane spoolScroll = new JScrollPane(spoolTable);
        spoolScroll.setPreferredSize(new Dimension(0, 160));

        contentArea = new JTextArea();
        contentArea.setEditable(false);
        contentArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        contentArea.setTabSize(8);
        JScrollPane contentScroll = new JScrollPane(contentArea);

        // Content panel: just the content (search bar moves to bottom)
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(contentScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spoolScroll, contentPanel);
        splitPane.setDividerLocation(160);
        splitPane.setResizeWeight(0.3);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // ── Bottom bar: [Copy] [🔍 Search ↓ count] ... [status] ────
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        // Right: Status (initialized first so other listeners can reference it)
        statusLabel = new JLabel("Lade Spool-Liste…");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));

        // Left: Copy button
        JButton copyButton = new JButton("📋 Kopieren");
        copyButton.setToolTipText("Angezeigten Inhalt in die Zwischenablage kopieren");
        copyButton.addActionListener(e -> {
            String text = contentArea.getText();
            if (text != null && !text.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(text), null);
                statusLabel.setText("✅ In Zwischenablage kopiert");
            }
        });
        bottomPanel.add(copyButton, BorderLayout.WEST);

        // Center: Search bar (fills remaining space)
        searchField = new JTextField();
        searchField.setToolTipText("Suche im Spool-Output (z.B. JOBLIB, IEF, ABEND)");
        searchField.addActionListener(e -> searchInContent());

        JButton searchBtn = new JButton("🔍");
        searchBtn.setMargin(new Insets(1, 4, 1, 4));
        searchBtn.setFocusable(false);
        searchBtn.addActionListener(e -> searchInContent());

        JButton searchNextBtn = new JButton("↓");
        searchNextBtn.setMargin(new Insets(1, 4, 1, 4));
        searchNextBtn.setFocusable(false);
        searchNextBtn.setToolTipText("Nächstes Ergebnis");
        searchNextBtn.addActionListener(e -> searchNextInContent());

        searchCountLabel = new JLabel("");
        searchCountLabel.setFont(searchCountLabel.getFont().deriveFont(Font.PLAIN, 11f));
        searchCountLabel.setForeground(Color.GRAY);

        JPanel searchPanel = new JPanel(new BorderLayout(4, 0));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        searchPanel.add(searchField, BorderLayout.CENTER);
        JPanel searchButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        searchButtons.add(searchBtn);
        searchButtons.add(searchNextBtn);
        searchButtons.add(searchCountLabel);
        searchPanel.add(searchButtons, BorderLayout.EAST);
        bottomPanel.add(searchPanel, BorderLayout.CENTER);

        // Add status to right
        bottomPanel.add(statusLabel, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Load spool files initially
        loadSpoolList();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Data loading
    // ═══════════════════════════════════════════════════════════════════

    /** Cached full output – populated when we had to fall back to full-output parsing. */
    private String cachedFullOutput;

    /**
     * Map from spool-table row → line range in cachedFullOutput (start inclusive, end exclusive).
     * Only populated when sections were parsed from full output.
     */
    private final List<int[]> sectionLineRanges = new ArrayList<>();

    private void loadSpoolList() {
        new SwingWorker<List<JesSpoolFile>, Void>() {
            @Override
            protected List<JesSpoolFile> doInBackground() throws Exception {
                return service.listSpoolFiles(job.getJobId());
            }

            @Override
            protected void done() {
                try {
                    List<JesSpoolFile> files = get();
                    if (!files.isEmpty()) {
                        spoolModel.setFiles(files);
                        statusLabel.setText(files.size() + " Spool-File(s)");
                        spoolTable.setRowSelectionInterval(0, 0);
                    } else {
                        // Spool list parsing returned nothing – load full output and parse sections
                        statusLabel.setText("⏳ Lade gesamten Output und analysiere Sections…");
                        loadFullOutputAndParseSections();
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "[JES] Failed to load spool list for " + job.getJobId(), cause);
                    statusLabel.setText("⏳ Lade gesamten Output…");
                    // FTP listing failed entirely – try full output
                    loadFullOutputAndParseSections();
                }
            }
        }.execute();
    }

    /**
     * Load the concatenated full output, parse DD sections from it,
     * populate the spool table, and show the full content.
     */
    private void loadFullOutputAndParseSections() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return service.getAllSpoolContent(job.getJobId());
            }

            @Override
            protected void done() {
                try {
                    String fullOutput = get();
                    cachedFullOutput = fullOutput;
                    contentArea.setText(fullOutput);
                    contentArea.setCaretPosition(0);

                    // Parse sections from the output
                    List<JesSpoolFile> sections = JesFtpService.parseSpoolSectionsFromOutput(fullOutput);
                    if (!sections.isEmpty()) {
                        // Also compute line ranges for each section so we can scroll to them
                        computeSectionRanges(fullOutput);
                        spoolModel.setFiles(sections);
                        statusLabel.setText(sections.size() + " Section(s) aus Output erkannt");
                        spoolTable.setRowSelectionInterval(0, 0);
                    } else {
                        statusLabel.setText("✅ Gesamter Output geladen (keine Sections erkannt)");
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "[JES] Failed to load full output for " + job.getJobId(), cause);
                    statusLabel.setText("❌ Fehler beim Laden");
                    contentArea.setText("Fehler: " + cause.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Compute line ranges for each section in the full output, delimited by JES separator lines.
     */
    private void computeSectionRanges(String fullOutput) {
        sectionLineRanges.clear();
        String[] lines = fullOutput.split("\\r?\\n");
        int sectionStart = 0;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.contains("END OF JES SPOOL FILE")
                    || trimmed.contains("END OF JES2 SPOOL")
                    || trimmed.matches("^\\s*!!\\s+END\\s+.*!!\\s*$")) {
                sectionLineRanges.add(new int[]{sectionStart, i});
                sectionStart = i + 1;
            }
        }
        // Last section (after last separator or entire output if no separators found)
        if (sectionStart < lines.length) {
            sectionLineRanges.add(new int[]{sectionStart, lines.length});
        }
    }

    private void loadSelectedSpool() {
        int row = spoolTable.getSelectedRow();
        if (row < 0) return;

        JesSpoolFile sf = spoolModel.getFileAt(row);

        // If we have cached full output with section ranges, scroll to that section
        if (cachedFullOutput != null && row < sectionLineRanges.size()) {
            int[] range = sectionLineRanges.get(row);
            String[] allLines = cachedFullOutput.split("\\r?\\n");
            StringBuilder sb = new StringBuilder();
            int endLine = Math.min(range[1], allLines.length);
            for (int i = range[0]; i < endLine; i++) {
                sb.append(allLines[i]).append('\n');
            }
            contentArea.setText(sb.toString());
            contentArea.setCaretPosition(0);
            int lineCount = endLine - range[0];
            statusLabel.setText("✅ " + sf.getDdName() + " (" + lineCount + " Zeilen)");
            return;
        }

        // Normal mode: load from FTP
        statusLabel.setText("⏳ Lade " + sf.getDdName() + "…");
        contentArea.setText("Lade " + sf.getDdName() + " (Spool #" + sf.getId() + ")…");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return service.getSpoolContent(job.getJobId(), sf.getId());
            }

            @Override
            protected void done() {
                try {
                    String content = get();
                    contentArea.setText(content);
                    contentArea.setCaretPosition(0);
                    statusLabel.setText("✅ " + sf.getDdName() + " geladen (" + sf.getRecordCount() + " Records)");
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    contentArea.setText("Fehler beim Laden:\n" + cause.getMessage());
                    statusLabel.setText("❌ Fehler");
                }
            }
        }.execute();
    }

    private void loadAllSpool() {
        // If we already have the full output cached, just show it
        if (cachedFullOutput != null) {
            contentArea.setText(cachedFullOutput);
            contentArea.setCaretPosition(0);
            statusLabel.setText("✅ Gesamter Output angezeigt");
            spoolTable.clearSelection();
            return;
        }

        statusLabel.setText("⏳ Lade gesamten Output…");
        contentArea.setText("Lade alle Spool-Files für " + job.getJobId() + "…");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return service.getAllSpoolContent(job.getJobId());
            }

            @Override
            protected void done() {
                try {
                    String content = get();
                    contentArea.setText(content);
                    contentArea.setCaretPosition(0);
                    statusLabel.setText("✅ Gesamter Output geladen");
                    spoolTable.clearSelection();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    contentArea.setText("Fehler beim Laden:\n" + cause.getMessage());
                    statusLabel.setText("❌ Fehler");
                }
            }
        }.execute();
    }

    private void deleteJob() {
        int confirm = JOptionPane.showConfirmDialog(mainPanel,
                "Job " + job.getJobId() + " (" + job.getJobName() + ") wirklich löschen?\n"
                        + "Der Spool-Output wird unwiderruflich entfernt.",
                "Job löschen?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return service.deleteJob(job.getJobId());
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        statusLabel.setText("✅ " + job.getJobId() + " gelöscht");
                        contentArea.setText("Job wurde gelöscht.");
                    } else {
                        statusLabel.setText("❌ Löschen fehlgeschlagen");
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    statusLabel.setText("❌ Fehler");
                    JOptionPane.showMessageDialog(mainPanel,
                            "Fehler beim Löschen:\n" + cause.getMessage(),
                            "JES-Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Search
    // ═══════════════════════════════════════════════════════════════════

    private void searchInContent() {
        lastSearchPos = 0;
        searchNextInContent();
    }

    private void searchNextInContent() {
        String query = searchField.getText();
        if (query == null || query.isEmpty()) return;

        String text = contentArea.getText();
        if (text == null || text.isEmpty()) return;

        String textLower = text.toLowerCase();
        String queryLower = query.toLowerCase();

        // Count total matches
        int count = 0;
        int idx = 0;
        while ((idx = textLower.indexOf(queryLower, idx)) >= 0) {
            count++;
            idx += queryLower.length();
        }

        // Find next match from lastSearchPos
        int pos = textLower.indexOf(queryLower, lastSearchPos);
        if (pos < 0 && lastSearchPos > 0) {
            // Wrap around
            pos = textLower.indexOf(queryLower, 0);
        }

        if (pos >= 0) {
            contentArea.setCaretPosition(pos);
            contentArea.select(pos, pos + query.length());
            contentArea.requestFocusInWindow();
            lastSearchPos = pos + query.length();
            searchCountLabel.setText(count + " Treffer");
        } else {
            searchCountLabel.setText("Nicht gefunden");
            lastSearchPos = 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════

    private static JPanel createInfoLabel(String label, String value) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        p.add(l);
        p.add(new JLabel(value != null ? value : "–"));
        return p;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FtpTab interface
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public String getTitle() {
        return "🖨️ " + job.getJobId() + " " + job.getJobName();
    }

    @Override
    public String getTooltip() {
        return "JES Spool – " + job.getJobId() + " " + job.getJobName()
                + " (" + job.getStatus() + ")"
                + (job.getRetCode() != null ? " RC=" + job.getRetCode() : "");
    }

    @Override public JComponent getComponent() { return mainPanel; }
    @Override public void onClose() { /* service is shared with ConnectionTab – don't close */ }
    @Override public void saveIfApplicable() { }
    @Override public void focusSearchField() {
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }
    @Override public void searchFor(String s) {
        if (s != null && !s.isEmpty()) {
            searchField.setText(s);
            searchInContent();
        }
    }

    @Override
    public String getPath() {
        return "jes://" + service.getUser() + "@" + service.getHost() + "/" + job.getJobId();
    }

    @Override
    public Type getType() { return Type.FILE; }

    @Override
    public String getContent() {
        return contentArea.getText();
    }

    @Override
    public void markAsChanged() { }

    // ═══════════════════════════════════════════════════════════════════
    //  Spool table model
    // ═══════════════════════════════════════════════════════════════════

    private static class SpoolTableModel extends AbstractTableModel {
        private List<JesSpoolFile> files = new ArrayList<JesSpoolFile>();

        void setFiles(List<JesSpoolFile> files) {
            this.files = files != null ? files : new ArrayList<JesSpoolFile>();
            fireTableDataChanged();
        }

        JesSpoolFile getFileAt(int row) { return files.get(row); }

        @Override public int getRowCount() { return files.size(); }
        @Override public int getColumnCount() { return SPOOL_COLUMNS.length; }
        @Override public String getColumnName(int col) { return SPOOL_COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            JesSpoolFile f = files.get(row);
            switch (col) {
                case 0: return f.getId();
                case 1: return f.getDdName();
                case 2: return f.getStepName();
                case 3: return f.getProcStep();
                case 4: return f.getDsClass();
                case 5: return f.getRecordCount();
                case 6: return f.getByteCount();
                default: return "";
            }
        }
    }
}

