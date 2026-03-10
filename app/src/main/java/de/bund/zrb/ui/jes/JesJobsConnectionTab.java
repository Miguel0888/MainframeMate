package de.bund.zrb.ui.jes;

import de.bund.zrb.files.impl.ftp.jes.JesFtpService;
import de.bund.zrb.files.impl.ftp.jes.JesJob;
import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.ui.util.ListKeyboardNavigation;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * ConnectionTab for browsing z/OS JES jobs.
 * <p>
 * Provides filter fields (Owner, Jobname, Status), a JTable for results,
 * and actions like Open (spool viewer), Delete and Refresh.
 */
public class JesJobsConnectionTab implements ConnectionTab {

    private static final Logger LOG = Logger.getLogger(JesJobsConnectionTab.class.getName());

    private static final String[] STATUS_OPTIONS = {"ALL", "OUTPUT", "ACTIVE", "INPUT"};
    private static final String[] COLUMN_NAMES = {"Job-ID", "Jobname", "Owner", "Status", "RC", "Class", "Spool"};

    private final JesFtpService service;
    private final TabbedPaneManager tabManager;

    private final JPanel mainPanel;
    private final JTextField ownerField;
    private final JTextField jobNameField;
    private final JComboBox<String> statusCombo;
    private final JTextField searchField = new JTextField();
    private final JButton searchButton;
    private final JButton refreshButton;
    private final JButton deleteButton;
    private final JTable jobTable;
    private final JobTableModel tableModel;
    private final JLabel statusLabel;

    /** All jobs from last server query (unfiltered). */
    private List<JesJob> allJobs = new ArrayList<JesJob>();

    public JesJobsConnectionTab(JesFtpService service, TabbedPaneManager tabManager) {
        this.service = service;
        this.tabManager = tabManager;
        this.tableModel = new JobTableModel();

        mainPanel = new JPanel(new BorderLayout(0, 4));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ── Filter bar ──────────────────────────────────────────────
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filterPanel.setBorder(BorderFactory.createTitledBorder("Filter"));

        filterPanel.add(new JLabel("Owner:"));
        ownerField = new JTextField(service.getUser(), 10);
        ownerField.setToolTipText("Job-Owner (z.B. Ihr Benutzer oder * für alle)");
        filterPanel.add(ownerField);

        filterPanel.add(new JLabel("Jobname:"));
        jobNameField = new JTextField("*", 10);
        jobNameField.setToolTipText("Jobname-Pattern (z.B. MYJOB* oder *)");
        filterPanel.add(jobNameField);

        filterPanel.add(new JLabel("Status:"));
        statusCombo = new JComboBox<String>(STATUS_OPTIONS);
        statusCombo.setSelectedItem("ALL");
        statusCombo.setToolTipText("ALL = alle, OUTPUT = fertig, ACTIVE = laufend, INPUT = wartend");
        filterPanel.add(statusCombo);

        searchButton = new JButton("🔍 Suchen");
        searchButton.addActionListener(e -> doSearch());
        filterPanel.add(searchButton);

        refreshButton = new JButton("🔄 Aktualisieren");
        refreshButton.addActionListener(e -> doSearch());
        filterPanel.add(refreshButton);

        mainPanel.add(filterPanel, BorderLayout.NORTH);

        // ── Job table ───────────────────────────────────────────────
        jobTable = new JTable(tableModel);
        jobTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jobTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        jobTable.setRowHeight(24);
        jobTable.setFillsViewportHeight(true);

        // Color-code the Status column
        jobTable.getColumnModel().getColumn(3).setCellRenderer(new StatusCellRenderer());

        // Double-click opens spool viewer
        jobTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedJob();
                }
            }
        });

        // Context menu
        JPopupMenu popup = new JPopupMenu();
        JMenuItem openItem = new JMenuItem("📄 Spool anzeigen");
        openItem.addActionListener(e -> openSelectedJob());
        popup.add(openItem);

        JMenuItem deleteItem = new JMenuItem("🗑️ Job löschen");
        deleteItem.addActionListener(e -> deleteSelectedJob());
        popup.add(deleteItem);

        popup.addSeparator();
        JMenuItem refreshItem = new JMenuItem("🔄 Aktualisieren");
        refreshItem.addActionListener(e -> doSearch());
        popup.add(refreshItem);

        jobTable.setComponentPopupMenu(popup);

        JScrollPane scrollPane = new JScrollPane(jobTable);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // ── Status bar: buttons | search field | status label ───────
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        deleteButton = new JButton("🗑️ Löschen");
        deleteButton.addActionListener(e -> deleteSelectedJob());
        buttonPanel.add(deleteButton);

        statusBar.add(buttonPanel, BorderLayout.WEST);

        // Search / filter field (local, instant, like in other connection tabs)
        JPanel searchBarPanel = createFilterBar();
        statusBar.add(searchBarPanel, BorderLayout.CENTER);

        statusLabel = new JLabel("Bereit");
        statusBar.add(statusLabel, BorderLayout.EAST);

        mainPanel.add(statusBar, BorderLayout.SOUTH);

        // Enter in filter fields triggers server search
        ownerField.addActionListener(e -> doSearch());
        jobNameField.addActionListener(e -> doSearch());

        // ── Keyboard navigation: search field ↔ table ───────────────
        ListKeyboardNavigation.install(jobTable, searchField,
                this::openSelectedJob, null, null);

        // ── Initial load ────────────────────────────────────────────
        doSearch();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Local filter bar (same pattern as other ConnectionTabs)
    // ═══════════════════════════════════════════════════════════════════

    private JPanel createFilterBar() {
        JPanel panel = new JPanel(new BorderLayout());
        searchField.setToolTipText(
                "<html>Regex-Filter für Job-ID / Jobname<br>"
                + "Beispiel: <code>JOB123</code> oder <code>MY.*</code><br>"
                + "<i>(Groß-/Kleinschreibung wird ignoriert)</i></html>");
        panel.add(new JLabel("🔎 ", JLabel.RIGHT), BorderLayout.WEST);
        panel.add(searchField, BorderLayout.CENTER);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { applyLocalFilter(); }
            public void removeUpdate(DocumentEvent e)  { applyLocalFilter(); }
            public void changedUpdate(DocumentEvent e)  { applyLocalFilter(); }
        });

        return panel;
    }

    private void applyLocalFilter() {
        String regex = searchField.getText().trim();

        if (regex.isEmpty()) {
            tableModel.setJobs(allJobs);
            statusLabel.setText(allJobs.size() + " Job(s)");
            searchField.setBackground(UIManager.getColor("TextField.background"));
            return;
        }

        try {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            List<JesJob> filtered = new ArrayList<JesJob>();
            for (JesJob job : allJobs) {
                if (pattern.matcher(job.getJobId()).find()
                        || pattern.matcher(job.getJobName()).find()) {
                    filtered.add(job);
                }
            }
            tableModel.setJobs(filtered);
            statusLabel.setText(filtered.size() + " / " + allJobs.size() + " Job(s)");
            searchField.setBackground(filtered.isEmpty()
                    ? new Color(255, 200, 200)
                    : UIManager.getColor("TextField.background"));
        } catch (Exception e) {
            // invalid regex → show all, red background
            tableModel.setJobs(allJobs);
            searchField.setBackground(new Color(255, 200, 200));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Actions
    // ═══════════════════════════════════════════════════════════════════

    private void doSearch() {
        String owner = ownerField.getText().trim();
        String jobName = jobNameField.getText().trim();
        String status = (String) statusCombo.getSelectedItem();

        searchButton.setEnabled(false);
        refreshButton.setEnabled(false);
        statusLabel.setText("⏳ Suche…");

        new SwingWorker<List<JesJob>, Void>() {
            @Override
            protected List<JesJob> doInBackground() throws Exception {
                return service.listJobs(owner, jobName, status);
            }

            @Override
            protected void done() {
                searchButton.setEnabled(true);
                refreshButton.setEnabled(true);
                try {
                    List<JesJob> jobs = get();
                    allJobs = jobs;
                    applyLocalFilter();
                    statusLabel.setText(jobs.size() + " Job(s) gefunden");
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "[JES] Search failed", cause);
                    statusLabel.setText("❌ Fehler");
                    JOptionPane.showMessageDialog(mainPanel,
                            "Suche fehlgeschlagen:\n" + cause.getMessage(),
                            "JES-Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void openSelectedJob() {
        int row = jobTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(mainPanel, "Bitte zuerst einen Job auswählen.",
                    "Kein Job gewählt", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JesJob job = tableModel.getJobAt(row);
        JobDetailTab detailTab = new JobDetailTab(service, job);
        tabManager.addTab(detailTab);
    }

    private void deleteSelectedJob() {
        int row = jobTable.getSelectedRow();
        if (row < 0) return;

        JesJob job = tableModel.getJobAt(row);
        int confirm = JOptionPane.showConfirmDialog(mainPanel,
                "Job " + job.getJobId() + " (" + job.getJobName() + ") wirklich löschen?\n"
                        + "Der Spool-Output wird unwiderruflich entfernt.",
                "Job löschen?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        statusLabel.setText("⏳ Lösche " + job.getJobId() + "…");
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return service.deleteJob(job.getJobId());
            }

            @Override
            protected void done() {
                try {
                    boolean ok = get();
                    if (ok) {
                        statusLabel.setText("✅ " + job.getJobId() + " gelöscht");
                        doSearch(); // refresh
                    } else {
                        statusLabel.setText("❌ Löschen fehlgeschlagen");
                        JOptionPane.showMessageDialog(mainPanel,
                                "Job " + job.getJobId() + " konnte nicht gelöscht werden.\n"
                                        + "Möglicherweise fehlt die Berechtigung.",
                                "Löschen fehlgeschlagen", JOptionPane.WARNING_MESSAGE);
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
    //  ConnectionTab interface
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public String getTitle() {
        return "📋 JES-Jobs " + service.getHost();
    }

    @Override
    public String getTooltip() {
        return "JES Jobs – " + service.getUser() + "@" + service.getHost();
    }

    @Override
    public JComponent getComponent() { return mainPanel; }

    @Override
    public void onClose() {
        service.close();
    }

    @Override public void saveIfApplicable() { }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());
        menu.add(closeItem);
        return menu;
    }

    @Override public void focusSearchField() {
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }
    @Override public void searchFor(String s) {
        if (s != null) {
            searchField.setText(s.trim());
            applyLocalFilter();
        }
    }
    @Override public String getContent() { return ""; }
    @Override public void markAsChanged() { }

    @Override
    public String getPath() { return "jes://" + service.getUser() + "@" + service.getHost(); }

    @Override
    public Type getType() { return Type.CONNECTION; }

    // ═══════════════════════════════════════════════════════════════════
    //  Table model
    // ═══════════════════════════════════════════════════════════════════

    private static class JobTableModel extends AbstractTableModel {
        private List<JesJob> jobs = new ArrayList<JesJob>();

        void setJobs(List<JesJob> jobs) {
            this.jobs = jobs != null ? jobs : new ArrayList<JesJob>();
            fireTableDataChanged();
        }

        JesJob getJobAt(int row) {
            return jobs.get(row);
        }

        @Override public int getRowCount() { return jobs.size(); }
        @Override public int getColumnCount() { return COLUMN_NAMES.length; }
        @Override public String getColumnName(int col) { return COLUMN_NAMES[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            JesJob j = jobs.get(row);
            switch (col) {
                case 0: return j.getJobId();
                case 1: return j.getJobName();
                case 2: return j.getOwner();
                case 3: return j.getStatus();
                case 4: return j.getRetCode() != null ? j.getRetCode() : "";
                case 5: return j.getJobClass();
                case 6: return j.getSpoolFileCount();
                default: return "";
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Status cell renderer (color-coded)
    // ═══════════════════════════════════════════════════════════════════

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected && value != null) {
                String status = value.toString().toUpperCase();
                switch (status) {
                    case "OUTPUT":
                        c.setForeground(new Color(0, 128, 0));
                        break;
                    case "ACTIVE":
                        c.setForeground(new Color(0, 80, 200));
                        break;
                    case "INPUT":
                        c.setForeground(new Color(180, 130, 0));
                        break;
                    default:
                        c.setForeground(table.getForeground());
                        break;
                }
            }
            return c;
        }
    }
}

