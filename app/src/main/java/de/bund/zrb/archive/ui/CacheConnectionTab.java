package de.bund.zrb.archive.ui;

import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.model.ArchiveRun;
import de.bund.zrb.archive.service.ArchiveService;
import de.bund.zrb.archive.service.ResourceStorageService;
import de.bund.zrb.archive.store.CacheRepository;
import de.bund.zrb.search.SearchHighlighter;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.newApi.ui.ConnectionTab;
import de.zrb.bund.newApi.ui.SearchBarPanel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Connection tab for the Cache system.
 * Shows locally cached content from all sources (Web, Wiki, Confluence, FTP, NDV, BetaView)
 * stored in the H2 {@code archive_entries} table.
 * <p>
 * Features:
 * <ul>
 *   <li>Source type filter based on URL scheme</li>
 *   <li>Search with highlighting</li>
 *   <li>Checkboxes for selective delete + toggle-all via header click</li>
 *   <li>"Alle löschen" button deletes all currently displayed entries</li>
 * </ul>
 * <p>
 * Note: Mails are NOT shown here — they are only indexed (in MailMetadataIndex),
 * not cached. Cache entries are created when the user accesses content or by
 * prefetch services (Wiki, Confluence).
 */
public class CacheConnectionTab implements ConnectionTab {

    private final JPanel mainPanel;
    private final CacheRepository repo;
    private final ResourceStorageService storageService;
    private final JTextArea previewArea;
    private final SearchBarPanel searchBar;
    private final JLabel statusLabel;
    private final TabbedPaneManager tabbedPaneManager;

    // View state
    private final JComboBox<String> viewSelector;
    private final JComboBox<String> sourceTypeFilter;
    private final JComboBox<String> hostFilter;
    private final JPanel hostFilterPanel;
    private final RunTableModel runTableModel;
    private final CacheEntryTableModel entryTableModel;
    private final JTable runTable;
    private final JTable dataTable;
    private final CardLayout cardLayout;
    private final JPanel tableCards;

    /** Current search query – used for yellow highlighting in preview. */
    private String currentSearchQuery = null;
    private final JToggleButton advancedToggle;

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    static {
        DATE_FMT.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
    }

    // ── Source type definitions (index → URL prefix for filtering) ──
    // 0=Alle, 1=Web, 2=Wiki, 3=Confluence, 4=FTP, 5=NDV, 6=BetaView
    private static final String[] SOURCE_LABELS = {
            "Alle Quellen", "🌐 Web", "📖 Wiki", "📚 Confluence",
            "📁 FTP", "🖥 NDV", "📘 BetaView"
    };
    /** URL prefixes per source type index.  null = all / no filter. */
    private static final String[] URL_PREFIXES = {
            null, "http", "wiki://", "confluence://",
            "ftp://", "ndv://", "betaview://"
    };

    public CacheConnectionTab(TabbedPaneManager tabbedPaneManager) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.repo = CacheRepository.getInstance();
        this.storageService = ArchiveService.getInstance().getStorageService();
        this.mainPanel = new JPanel(new BorderLayout(4, 4));

        // ── Toolbar ──
        JPanel toolbar = new JPanel(new BorderLayout(4, 0));

        // Left: source type → view selector → refresh → delete → delete all
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        sourceTypeFilter = new JComboBox<>(SOURCE_LABELS);
        sourceTypeFilter.addActionListener(e -> {
            updateFilterVisibility();
            switchView();
        });
        leftPanel.add(sourceTypeFilter);

        viewSelector = new JComboBox<>(new String[]{"📁 Runs", "📄 Einträge"});
        viewSelector.addActionListener(e -> switchView());
        leftPanel.add(viewSelector);

        JButton refreshBtn = new JButton("🔄");
        refreshBtn.setToolTipText("Aktualisieren");
        refreshBtn.addActionListener(e -> refresh());
        leftPanel.add(refreshBtn);

        JButton deleteBtn = new JButton("🗑 Markierte löschen");
        deleteBtn.setToolTipText("Markierte Einträge löschen");
        deleteBtn.addActionListener(e -> deleteSelected());
        leftPanel.add(deleteBtn);

        JButton deleteAllBtn = new JButton("🗑 Alle löschen");
        deleteAllBtn.setToolTipText("Alle aktuell angezeigten Einträge löschen");
        deleteAllBtn.addActionListener(e -> deleteAllVisible());
        leftPanel.add(deleteAllBtn);

        // Center: search + advanced toggle
        searchBar = new SearchBarPanel("Cache durchsuchen…", "Cache durchsuchen (Enter)");
        searchBar.addSearchAction(e -> filterEntries());
        advancedToggle = new JToggleButton("\u2699");
        advancedToggle.setToolTipText("Erweiterte Suche: AND OR NOT \"phrase\"\n\n"
                + "Beispiele:\n"
                + "  bundestag AND asyl  (beide Begriffe)\n"
                + "  klima OR energie    (einer der Begriffe)\n"
                + "  \"exakte phrase\"     (wörtlich)");
        advancedToggle.setFocusable(false);
        advancedToggle.setMargin(new Insets(2, 6, 2, 6));
        searchBar.addEastComponent(advancedToggle);

        // Right: host filter (only visible for Web)
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

        hostFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        hostFilter = new JComboBox<>(new String[]{"Alle Hosts"});
        hostFilter.addActionListener(e -> filterEntries());
        hostFilterPanel.add(new JLabel("Host:"));
        hostFilterPanel.add(hostFilter);
        filterPanel.add(hostFilterPanel);

        toolbar.add(leftPanel, BorderLayout.WEST);
        toolbar.add(searchBar, BorderLayout.CENTER);
        toolbar.add(filterPanel, BorderLayout.EAST);
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // ── Tables (Card Layout for Run vs Entry view) ──
        cardLayout = new CardLayout();
        tableCards = new JPanel(cardLayout);

        // Run table (Web research only)
        runTableModel = new RunTableModel();
        runTable = new JTable(runTableModel);
        runTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        runTable.getColumnModel().getColumn(0).setMaxWidth(80);
        runTable.getColumnModel().getColumn(2).setMaxWidth(100);
        runTable.getColumnModel().getColumn(3).setMaxWidth(80);
        runTable.getColumnModel().getColumn(4).setMaxWidth(80);
        runTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showRunPreview();
        });
        runTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = runTable.getSelectedRow();
                    if (row >= 0) {
                        viewSelector.setSelectedIndex(1);
                        loadAllEntries();
                    }
                }
            }
        });
        tableCards.add(new JScrollPane(runTable), "RUNS");

        // Cache entry table (with checkbox column)
        entryTableModel = new CacheEntryTableModel();
        dataTable = new JTable(entryTableModel);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataTable.getColumnModel().getColumn(0).setMaxWidth(30);   // ✓
        dataTable.getColumnModel().getColumn(0).setMinWidth(30);
        dataTable.getColumnModel().getColumn(2).setMaxWidth(100);  // Typ
        dataTable.getColumnModel().getColumn(3).setMaxWidth(130);  // Gecacht
        dataTable.getColumnModel().getColumn(4).setMaxWidth(80);   // Größe

        // Toggle-all checkbox on header click
        JTableHeader header = dataTable.getTableHeader();
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = header.columnAtPoint(e.getPoint());
                if (col == 0) {
                    entryTableModel.toggleAllSelection();
                }
            }
        });

        // Highlighting renderer for Titel column (col 1)
        dataTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? Color.WHITE : new Color(245, 245, 250));
                }
                if (value != null && currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
                    setText("<html>" + SearchHighlighter.highlightHtml(
                            SearchHighlighter.escHtml(value.toString()), currentSearchQuery) + "</html>");
                }
                return this;
            }
        });

        dataTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showEntryPreview();
        });
        tableCards.add(new JScrollPane(dataTable), "ENTRIES");

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

    // ═══════════════════════════════════════════════════════════
    //  Dynamic filter visibility
    // ═══════════════════════════════════════════════════════════

    private void updateFilterVisibility() {
        boolean isWeb = sourceTypeFilter.getSelectedIndex() == 1; // "🌐 Web"
        hostFilterPanel.setVisible(isWeb);
        mainPanel.revalidate();
    }

    // ═══════════════════════════════════════════════════════════
    //  View switching & refresh
    // ═══════════════════════════════════════════════════════════

    private void switchView() {
        int sourceIdx = sourceTypeFilter.getSelectedIndex();
        int selected = viewSelector.getSelectedIndex();

        // Runs view only makes sense for Web sources
        if (selected == 0 && (sourceIdx == 0 || sourceIdx == 1)) {
            cardLayout.show(tableCards, "RUNS");
            loadRuns();
        } else if (selected == 0 && sourceIdx >= 2) {
            // Non-web source: switch to entries
            viewSelector.setSelectedIndex(1);
            cardLayout.show(tableCards, "ENTRIES");
            loadAllEntries();
        } else {
            cardLayout.show(tableCards, "ENTRIES");
            loadAllEntries();
        }
    }

    private void refresh() {
        updateHostFilter();
        updateFilterVisibility();
        switchView();
    }

    // ═══════════════════════════════════════════════════════════
    //  Data loading — now from archive_entries (the real cache)
    // ═══════════════════════════════════════════════════════════

    private void loadRuns() {
        List<ArchiveRun> runs = repo.findAllRuns();
        runTableModel.setRuns(runs);
        int totalRes = 0, totalDoc = 0;
        for (ArchiveRun r : runs) { totalRes += r.getResourceCount(); totalDoc += r.getDocumentCount(); }
        statusLabel.setText(runs.size() + " Runs │ " + totalRes + " Resources │ " + totalDoc + " Dokumente");
    }

    private void loadAllEntries() {
        int sourceIdx = sourceTypeFilter.getSelectedIndex();
        currentSearchQuery = null;

        List<ArchiveEntry> entries;
        if (sourceIdx == 0) {
            // All sources
            entries = repo.findAll();
        } else if (sourceIdx == 1) {
            // Web: http:// and https://
            List<ArchiveEntry> http = repo.findByUrlPrefix("http://");
            List<ArchiveEntry> https = repo.findByUrlPrefix("https://");
            entries = new ArrayList<ArchiveEntry>(http.size() + https.size());
            entries.addAll(http);
            entries.addAll(https);
            // Sort combined by timestamp descending
            Collections.sort(entries, new Comparator<ArchiveEntry>() {
                @Override
                public int compare(ArchiveEntry a, ArchiveEntry b) {
                    return Long.compare(b.getCrawlTimestamp(), a.getCrawlTimestamp());
                }
            });
        } else {
            String prefix = URL_PREFIXES[sourceIdx];
            entries = prefix != null ? repo.findByUrlPrefix(prefix) : repo.findAll();
        }

        entryTableModel.setEntries(entries);
        String label = sourceIdx == 0 ? "Alle Quellen" : SOURCE_LABELS[sourceIdx];
        statusLabel.setText(entries.size() + " Cache-Einträge" + (sourceIdx > 0 ? " (" + label + ")" : ""));
    }

    // ═══════════════════════════════════════════════════════════
    //  Filtering / Search
    // ═══════════════════════════════════════════════════════════

    private void filterEntries() {
        int sourceIdx = sourceTypeFilter.getSelectedIndex();

        String query = searchBar.getText();
        String rawQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;
        currentSearchQuery = rawQuery;

        // Switch to entry view
        viewSelector.setSelectedIndex(1);
        cardLayout.show(tableCards, "ENTRIES");

        if (rawQuery == null) {
            loadAllEntries();
            return;
        }

        // Determine URL prefix for current source type
        String urlPrefix;
        if (sourceIdx == 0) {
            urlPrefix = null;
        } else if (sourceIdx == 1) {
            urlPrefix = "http"; // matches http:// and https://
        } else {
            urlPrefix = URL_PREFIXES[sourceIdx];
        }

        List<String> terms = SearchHighlighter.extractSearchTerms(rawQuery);
        if (terms.isEmpty()) {
            loadAllEntries();
            return;
        }

        // Search in DB
        List<ArchiveEntry> results = repo.searchEntries(terms.get(0), urlPrefix, 2000);

        // Client-side AND-filter for additional terms
        if (advancedToggle.isSelected() && terms.size() > 1) {
            for (int i = 1; i < terms.size(); i++) {
                final String term = terms.get(i).toLowerCase();
                List<ArchiveEntry> filtered = new ArrayList<ArchiveEntry>();
                for (ArchiveEntry e : results) {
                    boolean match = (e.getTitle() != null && e.getTitle().toLowerCase().contains(term))
                            || (e.getUrl() != null && e.getUrl().toLowerCase().contains(term));
                    if (match) filtered.add(e);
                }
                results = filtered;
            }
        }

        entryTableModel.setEntries(results);
        statusLabel.setText(results.size() + " Einträge gefunden"
                + (advancedToggle.isSelected() ? " (erweiterte Suche)" : ""));
    }

    private void updateHostFilter() {
        Set<String> hosts = new TreeSet<String>();
        for (ArchiveEntry e : repo.findAll()) {
            // Use the host field if available (from catalog entries)
            if (e.getHost() != null && !e.getHost().isEmpty()) {
                hosts.add(e.getHost());
                continue;
            }
            // Fallback: extract from URL
            String url = e.getUrl();
            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                try {
                    java.net.URL u = new java.net.URL(url);
                    hosts.add(u.getHost());
                } catch (Exception ignored) {}
            }
        }
        hostFilter.removeAllItems();
        hostFilter.addItem("Alle Hosts");
        for (String h : hosts) {
            hostFilter.addItem(h);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Preview
    // ═══════════════════════════════════════════════════════════

    private void showRunPreview() {
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
                && !"{}".equals(run.getDomainPolicyJson())) {
            sb.append("Policy:    ").append(run.getDomainPolicyJson()).append("\n");
        }
        sb.append("\nDoppelklick → Einträge anzeigen");
        setPreviewText(sb.toString());
    }

    private void showEntryPreview() {
        int row = dataTable.getSelectedRow();
        if (row < 0) { previewArea.setText(""); return; }
        ArchiveEntry entry = entryTableModel.getEntry(row);
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Cache-Eintrag ═══\n");
        sb.append("Titel:     ").append(entry.getTitle()).append("\n");
        sb.append("URL:       ").append(entry.getUrl()).append("\n");
        sb.append("Typ:       ").append(guessSourceType(entry.getUrl())).append("\n");
        sb.append("MIME:      ").append(entry.getMimeType()).append("\n");
        sb.append("Status:    ").append(entry.getStatus()).append("\n");
        sb.append("Größe:     ").append(formatSize(entry.getFileSizeBytes())).append("\n");
        sb.append("Gecacht:   ").append(formatTime(entry.getCrawlTimestamp())).append("\n");
        sb.append("Indexiert: ").append(entry.getLastIndexed() > 0 ? formatTime(entry.getLastIndexed()) : "–").append("\n");
        sb.append("Quelle:    ").append(entry.getSourceId()).append("\n");
        sb.append("Entry-ID:  ").append(entry.getEntryId()).append("\n");
        // Catalog fields (from research runs)
        if (entry.isFromResearchRun()) {
            sb.append("\n── Recherche ──\n");
            sb.append("Run-ID:    ").append(entry.getRunId()).append("\n");
            sb.append("Art:       ").append(entry.getKind()).append("\n");
            sb.append("Host:      ").append(entry.getHost()).append("\n");
            sb.append("Sprache:   ").append(entry.getLanguage()).append("\n");
            sb.append("Wörter:    ").append(entry.getWordCount()).append("\n");
        }
        if (entry.getExcerpt() != null && !entry.getExcerpt().isEmpty()) {
            sb.append("\n── Auszug ──\n");
            sb.append(entry.getExcerpt()).append("\n");
        }
        if (entry.getErrorMessage() != null && !entry.getErrorMessage().isEmpty()) {
            sb.append("\n⚠ Fehler: ").append(entry.getErrorMessage()).append("\n");
        }
        setPreviewText(sb.toString());
    }

    private void setPreviewText(String text) {
        previewArea.setText(text);
        previewArea.setCaretPosition(0);
        SearchHighlighter.highlightTextArea(previewArea, currentSearchQuery, Color.YELLOW);
    }

    // ═══════════════════════════════════════════════════════════
    //  Delete
    // ═══════════════════════════════════════════════════════════

    /** Delete only the checked entries. */
    private void deleteSelected() {
        int currentView = viewSelector.getSelectedIndex();

        if (currentView == 1) {
            // Entry view
            List<ArchiveEntry> selected = entryTableModel.getSelectedEntries();
            if (selected.isEmpty()) {
                statusLabel.setText("Keine Einträge markiert. Nutze die Checkboxen oder 'Alle löschen'.");
                return;
            }
            int result = JOptionPane.showConfirmDialog(mainPanel,
                    selected.size() + " markierte Einträge aus dem Cache löschen?",
                    "Einträge löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                for (ArchiveEntry entry : selected) {
                    repo.delete(entry.getEntryId());
                }
                refresh();
                previewArea.setText("");
                statusLabel.setText(selected.size() + " Einträge gelöscht.");
            }
        } else {
            // Run view: delete selected run
            int selectedRow = runTable.getSelectedRow();
            if (selectedRow < 0) {
                statusLabel.setText("Bitte einen Run auswählen.");
                return;
            }
            ArchiveRun run = runTableModel.getRun(selectedRow);
            String shortId = run.getRunId().substring(0, Math.min(8, run.getRunId().length()));
            int result = JOptionPane.showConfirmDialog(mainPanel,
                    "Run '" + shortId + "…' mit allen Dokumenten löschen?",
                    "Run löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                repo.deleteRun(run.getRunId());
                refresh();
                previewArea.setText("");
            }
        }
    }

    /** Delete ALL currently visible entries (respects source filter). */
    private void deleteAllVisible() {
        int currentView = viewSelector.getSelectedIndex();

        if (currentView == 1) {
            int count = entryTableModel.getRowCount();
            if (count == 0) {
                statusLabel.setText("Keine Einträge zum Löschen vorhanden.");
                return;
            }

            int sourceIdx = sourceTypeFilter.getSelectedIndex();
            String label = sourceIdx == 0 ? "ALLE " + count + " Cache-Einträge"
                    : "alle " + count + " " + SOURCE_LABELS[sourceIdx] + "-Einträge";

            int result = JOptionPane.showConfirmDialog(mainPanel,
                    label + " aus dem Cache löschen?\n\n"
                            + "⚠ Diese Aktion kann nicht rückgängig gemacht werden.",
                    "Alle löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                if (sourceIdx == 0) {
                    repo.deleteAll();
                } else if (sourceIdx == 1) {
                    repo.deleteByUrlPrefix("http://");
                    repo.deleteByUrlPrefix("https://");
                } else {
                    String prefix = URL_PREFIXES[sourceIdx];
                    if (prefix != null) repo.deleteByUrlPrefix(prefix);
                }
                refresh();
                previewArea.setText("");
                statusLabel.setText(label + " gelöscht.");
            }
        } else {
            // Run view: delete all runs
            int count = runTableModel.getRowCount();
            if (count == 0) return;
            int result = JOptionPane.showConfirmDialog(mainPanel,
                    "Alle " + count + " Runs mit Dokumenten und Resources löschen?",
                    "Alles löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                repo.deleteAllDocuments();
                refresh();
                previewArea.setText("");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    static String formatTime(long epochMillis) {
        if (epochMillis <= 0) return "–";
        return DATE_FMT.format(new Date(epochMillis));
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "–";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Guess the source type from a URL. */
    static String guessSourceType(String url) {
        if (url == null) return "?";
        String lower = url.toLowerCase();
        if (lower.startsWith("wiki://")) return "📖 Wiki";
        if (lower.startsWith("confluence://")) return "📚 Confluence";
        if (lower.startsWith("http://") || lower.startsWith("https://")) return "🌐 Web";
        if (lower.startsWith("ftp://")) return "📁 FTP";
        if (lower.startsWith("ndv://")) return "🖥 NDV";
        if (lower.startsWith("betaview://")) return "📘 BetaView";
        if (lower.startsWith("/") || lower.matches("^[a-zA-Z]:.*")) return "💻 Lokal";
        return "📄 Sonstige";
    }

    // ── ConnectionTab interface ──

    @Override public String getTitle() { return "💾 Cache"; }
    @Override public String getTooltip() { return "Lokal zwischengespeicherte Inhalte (H2 archive_entries)"; }
    @Override public JComponent getComponent() { return mainPanel; }
    @Override public void onClose() { /* nothing */ }
    @Override public void saveIfApplicable() { /* read-only */ }
    @Override public String getContent() { return ""; }
    @Override public void markAsChanged() { /* not applicable */ }
    @Override public String getPath() { return "cache://"; }
    @Override public Type getType() { return Type.CONNECTION; }

    @Override
    public void focusSearchField() {
        searchBar.focusAndSelectAll();
    }

    @Override
    public void searchFor(String searchPattern) {
        searchBar.setText(searchPattern);
        filterEntries();
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

    /**
     * Table model for cache entries ({@code archive_entries}).
     * Columns: ✓, Titel, Typ, Gecacht, Größe, URL
     */
    private static class CacheEntryTableModel extends AbstractTableModel {
        private List<ArchiveEntry> entries = new ArrayList<ArchiveEntry>();
        private final Set<Integer> selectedRows = new HashSet<Integer>();
        private static final String[] COLUMNS = {"✓", "Titel", "Typ", "Gecacht", "Größe", "URL"};

        void setEntries(List<ArchiveEntry> entries) {
            this.entries = entries != null ? entries : new ArrayList<ArchiveEntry>();
            this.selectedRows.clear();
            fireTableDataChanged();
        }

        ArchiveEntry getEntry(int row) { return entries.get(row); }

        List<ArchiveEntry> getSelectedEntries() {
            List<ArchiveEntry> result = new ArrayList<ArchiveEntry>();
            for (int row : selectedRows) {
                if (row < entries.size()) {
                    result.add(entries.get(row));
                }
            }
            return result;
        }

        void toggleAllSelection() {
            int total = entries.size();
            if (total == 0) return;
            if (selectedRows.isEmpty()) {
                for (int i = 0; i < total; i++) selectedRows.add(i);
            } else if (selectedRows.size() == total) {
                selectedRows.clear();
            } else {
                Set<Integer> inverted = new HashSet<Integer>();
                for (int i = 0; i < total; i++) {
                    if (!selectedRows.contains(i)) inverted.add(i);
                }
                selectedRows.clear();
                selectedRows.addAll(inverted);
            }
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 0 ? Boolean.class : Object.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) { return col == 0; }

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
            ArchiveEntry e = entries.get(row);
            switch (col) {
                case 0: return selectedRows.contains(row);
                case 1: return e.getTitle() != null && !e.getTitle().isEmpty() ? e.getTitle() : "(kein Titel)";
                case 2: return guessSourceType(e.getUrl());
                case 3: return formatTime(e.getCrawlTimestamp());
                case 4: return formatSize(e.getFileSizeBytes());
                case 5: return e.getUrl();
                default: return "";
            }
        }

        private static String formatSize(long bytes) {
            if (bytes <= 0) return "–";
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
