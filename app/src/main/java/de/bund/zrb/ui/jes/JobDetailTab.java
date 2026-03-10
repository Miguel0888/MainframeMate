package de.bund.zrb.ui.jes;

import de.bund.zrb.files.impl.ftp.jes.JesFtpService;
import de.bund.zrb.files.impl.ftp.jes.JesJob;
import de.bund.zrb.files.impl.ftp.jes.JesSpoolFile;
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

    public JobDetailTab(JesFtpService service, JesJob job) {
        this.service = service;
        this.job = job;
        this.spoolModel = new SpoolTableModel();

        mainPanel = new JPanel(new BorderLayout(0, 4));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // ── Info header ─────────────────────────────────────────────
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Job-Info"));

        infoPanel.add(createInfoLabel("Job-ID:", job.getJobId()));
        infoPanel.add(createInfoLabel("Name:", job.getJobName()));
        infoPanel.add(createInfoLabel("Owner:", job.getOwner()));
        infoPanel.add(createInfoLabel("Status:", job.getStatus()));
        if (job.getRetCode() != null && !job.getRetCode().isEmpty()) {
            infoPanel.add(createInfoLabel("RC:", job.getRetCode()));
        }
        if (job.getJobClass() != null && !job.getJobClass().isEmpty()) {
            infoPanel.add(createInfoLabel("Class:", job.getJobClass()));
        }

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

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spoolScroll, contentScroll);
        splitPane.setDividerLocation(160);
        splitPane.setResizeWeight(0.3);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // ── Bottom bar ──────────────────────────────────────────────
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        statusLabel = new JLabel("Lade Spool-Liste…");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        JButton allButton = new JButton("📑 Gesamter Output");
        allButton.setToolTipText("Alle Spool-Files zusammen laden");
        allButton.addActionListener(e -> loadAllSpool());
        buttonPanel.add(allButton);

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
        buttonPanel.add(copyButton);

        JButton deleteButton = new JButton("🗑️ Job löschen");
        deleteButton.addActionListener(e -> deleteJob());
        buttonPanel.add(deleteButton);

        bottomPanel.add(buttonPanel, BorderLayout.WEST);

        bottomPanel.add(statusLabel, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Load spool files initially
        loadSpoolList();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Data loading
    // ═══════════════════════════════════════════════════════════════════

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
                    spoolModel.setFiles(files);
                    statusLabel.setText(files.size() + " Spool-File(s)");
                    if (!files.isEmpty()) {
                        spoolTable.setRowSelectionInterval(0, 0);
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "[JES] Failed to load spool list for " + job.getJobId(), cause);
                    statusLabel.setText("❌ Fehler beim Laden der Spool-Liste");
                    contentArea.setText("Fehler: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void loadSelectedSpool() {
        int row = spoolTable.getSelectedRow();
        if (row < 0) return;

        JesSpoolFile sf = spoolModel.getFileAt(row);
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
    @Override public void focusSearchField() { }
    @Override public void searchFor(String s) { }

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

