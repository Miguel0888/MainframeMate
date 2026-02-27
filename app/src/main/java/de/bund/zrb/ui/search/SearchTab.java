package de.bund.zrb.ui.search;

import de.bund.zrb.search.SearchResult;
import de.bund.zrb.search.SearchService;
import de.bund.zrb.search.SearchHighlighter;
import de.zrb.bund.newApi.ui.FtpTab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Future;

/**
 * High-end search tab with faceted filtering, highlighting, sorting,
 * saved searches, and keyboard navigation.
 */
public class SearchTab extends JPanel implements FtpTab {

    private final SearchService searchService = SearchService.getInstance();
    private final de.bund.zrb.ui.TabbedPaneManager tabManager;

    // ── UI Components ──
    private final JTextField searchField;
    private final JButton searchButton;
    private final JToggleButton advancedToggle;
    private final JTable resultTable;
    private final DefaultTableModel tableModel;
    private final JEditorPane previewPane;
    private final JLabel statusLabel;

    // Source filter checkboxes
    private final JCheckBox cbLocal, cbFtp, cbNdv, cbMail, cbArchive;
    private final JLabel ragStatusLabel;

    // Sorting
    private final JComboBox<String> sortCombo;

    // Max results
    private final JSpinner maxResultsSpinner;

    // Facet panel components
    private final JPanel facetPanel;
    private final JList<String> savedSearchList;
    private final DefaultListModel<String> savedSearchModel;

    // State
    private final List<SearchResult> currentResults = new ArrayList<>();
    private Future<?> currentSearch = null;
    private String lastQuery = "";

    // Saved searches persistence
    private final List<SavedSearch> savedSearches = new ArrayList<>();
    private static final String SAVED_SEARCHES_KEY = "savedSearches";

    public SearchTab() {
        this(null);
    }

    public SearchTab(de.bund.zrb.ui.TabbedPaneManager tabManager) {
        this.tabManager = tabManager;
        setLayout(new BorderLayout(0, 0));

        // ════════════════════════════════════════════════════════════
        //  TOP: Search bar + filters
        // ════════════════════════════════════════════════════════════
        JPanel headerPanel = new JPanel(new BorderLayout(0, 2));
        headerPanel.setBorder(new EmptyBorder(4, 4, 0, 4));

        // Search input row
        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchField = new JTextField();
        searchField.setFont(searchField.getFont().deriveFont(14f));
        searchField.putClientProperty("JTextField.placeholderText",
                "Suchbegriff eingeben\u2026 (Lucene-Syntax: AND OR NOT \"phrase\" field:value)");
        searchField.addActionListener(e -> performSearch());
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchField.setText("");
                    clearResults();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (resultTable.getRowCount() > 0) {
                        resultTable.setRowSelectionInterval(0, 0);
                        resultTable.requestFocusInWindow();
                    }
                }
            }
        });

        searchButton = new JButton("\uD83D\uDD0D Suchen");
        searchButton.setFocusable(false);
        searchButton.addActionListener(e -> performSearch());

        advancedToggle = new JToggleButton("\u2699");
        advancedToggle.setToolTipText("Erweiterte Suche: Lucene-Syntax\n\n"
                + "Beispiele:\n"
                + "  \"exakte phrase\"\n"
                + "  hamburg~1 (Fuzzy)\n"
                + "  type:pdf AND author:mueller");
        advancedToggle.setFocusable(false);
        advancedToggle.setMargin(new Insets(2, 6, 2, 6));

        JPanel searchButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        searchButtons.add(searchButton);
        searchButtons.add(advancedToggle);

        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchButtons, BorderLayout.EAST);
        headerPanel.add(searchRow, BorderLayout.NORTH);

        // Filter row
        JPanel filterRow = new JPanel(new BorderLayout());

        JPanel sourcePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 1));
        cbLocal = new JCheckBox("\uD83D\uDCC1 Lokal", true);
        cbFtp = new JCheckBox("\uD83C\uDF10 FTP", true);
        cbNdv = new JCheckBox("\uD83D\uDD17 NDV", true);
        cbMail = new JCheckBox("\uD83D\uDCE7 Mail", true);
        cbArchive = new JCheckBox("\uD83D\uDCE6 Archiv", true);

        int semSize = de.bund.zrb.rag.service.RagService.getInstance().getSemanticIndexSize();
        ragStatusLabel = new JLabel(semSize > 0
                ? "\uD83E\uDD16 Hybrid (" + semSize + " Emb.)"
                : "\uD83D\uDCDD BM25");
        ragStatusLabel.setForeground(semSize > 0 ? new Color(76, 175, 80) : Color.GRAY);
        ragStatusLabel.setToolTipText(semSize > 0
                ? "Hybrid-Suche aktiv: BM25 + " + semSize + " Embeddings"
                : "Nur Volltextsuche. Aktiviere Embeddings in Indexierungs-Regeln.");

        sourcePanel.add(cbLocal);
        sourcePanel.add(cbFtp);
        sourcePanel.add(cbNdv);
        sourcePanel.add(cbMail);
        sourcePanel.add(cbArchive);
        sourcePanel.add(Box.createHorizontalStrut(8));
        sourcePanel.add(ragStatusLabel);

        JPanel sortPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 1));
        sortPanel.add(new JLabel("Sortierung:"));
        sortCombo = new JComboBox<>(new String[]{
                "Relevanz \u2193", "Name A\u2192Z", "Name Z\u2192A", "Pfad A\u2192Z"});
        sortCombo.setToolTipText("Ergebnisse sortieren");
        sortCombo.addActionListener(e -> applySorting());
        sortPanel.add(sortCombo);
        sortPanel.add(Box.createHorizontalStrut(8));
        sortPanel.add(new JLabel("Max:"));
        maxResultsSpinner = new JSpinner(new SpinnerNumberModel(50, 10, 500, 10));
        sortPanel.add(maxResultsSpinner);

        filterRow.add(sourcePanel, BorderLayout.WEST);
        filterRow.add(sortPanel, BorderLayout.EAST);
        headerPanel.add(filterRow, BorderLayout.SOUTH);

        add(headerPanel, BorderLayout.NORTH);

        // ════════════════════════════════════════════════════════════
        //  LEFT: Facet panel (Saved Searches + Quick Filters)
        // ════════════════════════════════════════════════════════════
        facetPanel = new JPanel(new BorderLayout(0, 8));
        facetPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
        facetPanel.setPreferredSize(new Dimension(180, 0));

        // Quick filter buttons
        JPanel quickFilters = new JPanel(new GridLayout(0, 1, 2, 2));
        quickFilters.setBorder(BorderFactory.createTitledBorder("Schnellfilter"));
        String[] quickLabels = {"\uD83D\uDCE7 Nur Mails", "\uD83D\uDCC4 Nur PDFs",
                "\uD83D\uDCDD Nur Text", "\uD83D\uDCCA Nur Excel"};
        String[] quickValues = {"mail", "pdf", "txt", "excel"};
        for (int i = 0; i < quickLabels.length; i++) {
            String label = quickLabels[i];
            String val = quickValues[i];
            JButton qb = new JButton(label);
            qb.setHorizontalAlignment(SwingConstants.LEFT);
            qb.setBorderPainted(false);
            qb.setContentAreaFilled(false);
            qb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            qb.setMargin(new Insets(2, 4, 2, 4));
            qb.addActionListener(e -> applyQuickFilter(val));
            quickFilters.add(qb);
        }
        facetPanel.add(quickFilters, BorderLayout.NORTH);

        // Saved searches
        JPanel savedPanel = new JPanel(new BorderLayout(0, 2));
        savedPanel.setBorder(BorderFactory.createTitledBorder("Gespeicherte Suchen"));
        savedSearchModel = new DefaultListModel<>();
        savedSearchList = new JList<>(savedSearchModel);
        savedSearchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        savedSearchList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) loadSavedSearch();
            }
        });

        JPanel savedButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JButton saveBtn = new JButton("\uD83D\uDCBE");
        saveBtn.setToolTipText("Aktuelle Suche speichern");
        saveBtn.setMargin(new Insets(0, 2, 0, 2));
        saveBtn.addActionListener(e -> saveCurrentSearch());
        JButton delBtn = new JButton("\uD83D\uDDD1");
        delBtn.setToolTipText("Gespeicherte Suche l\u00f6schen");
        delBtn.setMargin(new Insets(0, 2, 0, 2));
        delBtn.addActionListener(e -> deleteSavedSearch());
        savedButtons.add(saveBtn);
        savedButtons.add(delBtn);

        savedPanel.add(new JScrollPane(savedSearchList), BorderLayout.CENTER);
        savedPanel.add(savedButtons, BorderLayout.SOUTH);
        facetPanel.add(savedPanel, BorderLayout.CENTER);

        loadSavedSearches();

        // ════════════════════════════════════════════════════════════
        //  CENTER: Results table
        // ════════════════════════════════════════════════════════════
        String[] columns = {"Quelle", "Dokument", "Pfad", "Treffer-Kontext", "Score"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        resultTable = new JTable(tableModel);
        resultTable.setRowHeight(32);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.setShowGrid(false);
        resultTable.setIntercellSpacing(new Dimension(4, 2));

        // Column widths
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(55);
        resultTable.getColumnModel().getColumn(0).setMaxWidth(65);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(220);
        resultTable.getColumnModel().getColumn(3).setPreferredWidth(400);
        resultTable.getColumnModel().getColumn(4).setPreferredWidth(55);
        resultTable.getColumnModel().getColumn(4).setMaxWidth(65);


        // Custom renderer for snippet column with highlighting + alternating rows
        DefaultTableCellRenderer snippetRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? Color.WHITE : new Color(245, 245, 250));
                }
                if (value != null) {
                    setText("<html>" + highlightTerms(escHtml(value.toString()), lastQuery) + "</html>");
                }
                return this;
            }
        };
        resultTable.getColumnModel().getColumn(3).setCellRenderer(snippetRenderer);

        // Alternating row colors for other columns
        DefaultTableCellRenderer altRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(245, 245, 250));
                }
                return c;
            }
        };
        for (int c = 0; c < resultTable.getColumnCount(); c++) {
            if (c != 3) { // skip snippet column (has highlighting)
                resultTable.getColumnModel().getColumn(c).setCellRenderer(altRenderer);
            }
        }

        // Selection -> preview
        resultTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updatePreview();
        });

        // Double-click / Enter -> open; Single-click on star -> toggle bookmark
        resultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = resultTable.rowAtPoint(e.getPoint());
                int col = resultTable.columnAtPoint(e.getPoint());

                if (row < 0) return;

                if (e.getClickCount() == 1 && col == 1) {
                    // Check if click is on the star area (right side of the cell)
                    Rectangle cellRect = resultTable.getCellRect(row, col, true);
                    int clickXInCell = e.getX() - cellRect.x;
                    int cellWidth = cellRect.width;
                    // Star is at the right side – consider last ~30px as star area
                    if (clickXInCell > cellWidth - 30) {
                        toggleBookmarkForRow(row);
                        resultTable.repaint();
                    }
                } else if (e.getClickCount() == 2) {
                    openSelectedResult();
                }
            }
        });
        resultTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    openSelectedResult();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchField.requestFocusInWindow();
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(resultTable);

        // ════════════════════════════════════════════════════════════
        //  BOTTOM: Preview pane + Status
        // ════════════════════════════════════════════════════════════
        previewPane = new JEditorPane();
        previewPane.setContentType("text/html");
        previewPane.setEditable(false);
        previewPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        previewPane.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Handle star-click in preview
        previewPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED
                    && e.getDescription() != null && e.getDescription().equals("toggle-bookmark")) {
                int row = resultTable.getSelectedRow();
                if (row >= 0) {
                    toggleBookmarkForRow(row);
                    updatePreview(); // refresh star
                }
            }
        });

        JScrollPane previewScroll = new JScrollPane(previewPane);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Vorschau"));
        previewScroll.setPreferredSize(new Dimension(0, 180));

        statusLabel = new JLabel("Bereit. Tippe einen Suchbegriff ein und dr\u00fccke Enter.");
        statusLabel.setBorder(new EmptyBorder(2, 6, 2, 6));
        statusLabel.setForeground(Color.GRAY);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(previewScroll, BorderLayout.CENTER);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        // ════════════════════════════════════════════════════════════
        //  Layout assembly
        // ════════════════════════════════════════════════════════════
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, bottomPanel);
        verticalSplit.setResizeWeight(0.65);
        verticalSplit.setDividerLocation(300);

        JSplitPane horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, facetPanel, verticalSplit);
        horizontalSplit.setDividerLocation(180);

        add(horizontalSplit, BorderLayout.CENTER);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Search execution
    // ═══════════════════════════════════════════════════════════════

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        lastQuery = query;

        if (currentSearch != null && !currentSearch.isDone()) {
            currentSearch.cancel(true);
        }

        clearResults();
        searchButton.setEnabled(false);
        statusLabel.setText("\uD83D\uDD0D Suche nach: \"" + query + "\"\u2026");
        statusLabel.setForeground(new Color(255, 152, 0));

        Set<SearchResult.SourceType> sources = getSelectedSources();
        int maxResults = (Integer) maxResultsSpinner.getValue();

        long startTime = System.currentTimeMillis();

        new SwingWorker<List<SearchResult>, Void>() {
            @Override
            protected List<SearchResult> doInBackground() {
                return searchService.search(query, sources, maxResults, false);
            }

            @Override
            protected void done() {
                try {
                    List<SearchResult> results = get();
                    currentResults.clear();
                    currentResults.addAll(results);
                    populateTable(results);

                    long elapsed = System.currentTimeMillis() - startTime;
                    int sem = de.bund.zrb.rag.service.RagService.getInstance().getSemanticIndexSize();
                    String mode = sem > 0 ? "Hybrid (BM25 + " + sem + " Embeddings)" : "BM25";
                    statusLabel.setText("\u2705 " + results.size() + " Ergebnis(se) in "
                            + elapsed + " ms (" + mode + ")");
                    statusLabel.setForeground(new Color(76, 175, 80));

                    if (results.isEmpty()) {
                        statusLabel.setText("Keine Ergebnisse f\u00fcr: \"" + query
                                + "\"  \u2013 Tipp: Suchbegriff k\u00fcrzen, Filter lockern "
                                + "oder Schreibweise pr\u00fcfen.");
                        statusLabel.setForeground(new Color(158, 158, 158));
                    }

                    // Update RAG status
                    ragStatusLabel.setText(sem > 0
                            ? "\uD83E\uDD16 Hybrid (" + sem + " Emb.)" : "\uD83D\uDCDD BM25");
                    ragStatusLabel.setForeground(sem > 0 ? new Color(76, 175, 80) : Color.GRAY);
                } catch (Exception e) {
                    statusLabel.setText("\u274C Fehler: " + e.getMessage());
                    statusLabel.setForeground(new Color(244, 67, 54));
                } finally {
                    searchButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void populateTable(List<SearchResult> results) {
        tableModel.setRowCount(0);
        for (SearchResult r : results) {
            tableModel.addRow(new Object[]{
                    r.getSource().getIcon(),
                    r.getDocumentName(),
                    r.getPath(),
                    truncate(r.getSnippet(), 200),
                    String.format("%.2f", r.getScore())
            });
        }
    }

    private void clearResults() {
        tableModel.setRowCount(0);
        currentResults.clear();
        previewPane.setText("");
    }

    private Set<SearchResult.SourceType> getSelectedSources() {
        Set<SearchResult.SourceType> sources = new LinkedHashSet<>();
        if (cbLocal.isSelected()) sources.add(SearchResult.SourceType.LOCAL);
        if (cbFtp.isSelected()) sources.add(SearchResult.SourceType.FTP);
        if (cbNdv.isSelected()) sources.add(SearchResult.SourceType.NDV);
        if (cbMail.isSelected()) sources.add(SearchResult.SourceType.MAIL);
        if (cbArchive.isSelected()) sources.add(SearchResult.SourceType.ARCHIVE);
        return sources;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Sorting
    // ═══════════════════════════════════════════════════════════════

    private void applySorting() {
        if (currentResults.isEmpty()) return;
        int idx = sortCombo.getSelectedIndex();
        List<SearchResult> sorted = new ArrayList<>(currentResults);
        switch (idx) {
            case 0: Collections.sort(sorted); break;
            case 1: sorted.sort(Comparator.comparing(r -> r.getDocumentName().toLowerCase())); break;
            case 2: sorted.sort(Comparator.comparing(
                    (SearchResult r) -> r.getDocumentName().toLowerCase()).reversed()); break;
            case 3: sorted.sort(Comparator.comparing(r -> r.getPath().toLowerCase())); break;
        }
        currentResults.clear();
        currentResults.addAll(sorted);
        populateTable(sorted);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Quick filters
    // ═══════════════════════════════════════════════════════════════

    private void applyQuickFilter(String type) {
        switch (type) {
            case "mail":
                cbLocal.setSelected(false); cbFtp.setSelected(false);
                cbNdv.setSelected(false); cbMail.setSelected(true);
                break;
            case "pdf":
                searchField.setText(searchField.getText().trim() + " *.pdf");
                break;
            case "txt":
                searchField.setText(searchField.getText().trim() + " *.txt");
                break;
            case "excel":
                searchField.setText(searchField.getText().trim() + " *.xls*");
                break;
        }
        if (!searchField.getText().trim().isEmpty()) performSearch();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Preview
    // ═══════════════════════════════════════════════════════════════

    private void updatePreview() {
        int row = resultTable.getSelectedRow();
        if (row < 0 || row >= resultTable.getRowCount()) { previewPane.setText(""); return; }
        int modelRow = resultTable.convertRowIndexToModel(row);
        if (modelRow < 0 || modelRow >= currentResults.size()) { previewPane.setText(""); return; }

        SearchResult r = currentResults.get(modelRow);
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:SansSerif;font-size:12px;padding:8px;'>");
        html.append("<div style='color:#666;font-size:11px;margin-bottom:4px;'>");
        html.append(escHtml(r.getSource().getIcon())).append(" ").append(escHtml(r.getSource().getLabel()));
        html.append(" &nbsp;|&nbsp; ").append(escHtml(r.getPath()));
        html.append("</div>");
        html.append("<div style='font-size:14px;font-weight:bold;margin-bottom:6px;'>");
        html.append("\uD83D\uDCC4 ").append(escHtml(r.getDocumentName()));
        // Bookmark star – clickable
        boolean bookmarked = isResultBookmarked(modelRow);
        String starChar = bookmarked ? "\u2605" : "\u2606";
        String starColor = bookmarked ? "#FFC800" : "#999999";
        html.append(" <a href='toggle-bookmark' style='text-decoration:none;font-size:16px;color:")
                .append(starColor).append(";'>").append(starChar).append("</a>");
        html.append("</div>");

        if (r.getHeading() != null && !r.getHeading().isEmpty()) {
            html.append("<div style='color:#1976D2;margin-bottom:6px;'>\uD83D\uDCCC ")
                    .append(escHtml(r.getHeading())).append("</div>");
        }

        html.append("<div style='background:#FFFDE7;border:1px solid #FFF9C4;padding:6px;border-radius:4px;'>");
        html.append(highlightTerms(escHtml(r.getSnippet()), lastQuery));
        html.append("</div>");
        html.append("<div style='margin-top:8px;color:#999;font-size:10px;'>");
        html.append("\u2B50 Score: ").append(String.format("%.4f", r.getScore()));
        html.append(" &nbsp;|&nbsp; Chunk: ").append(escHtml(r.getChunkId()));
        html.append("</div></body></html>");

        previewPane.setText(html.toString());
        previewPane.setCaretPosition(0);
    }

    private void openSelectedResult() {
        int row = resultTable.getSelectedRow();
        if (row < 0) return;
        int modelRow = resultTable.convertRowIndexToModel(row);
        if (modelRow < 0 || modelRow >= currentResults.size()) return;

        SearchResult result = currentResults.get(modelRow);
        String rawPath = result.getDocumentId(); // documentId is the full path used during indexing

        if (tabManager == null) {
            // Fallback: show info dialog if no tabManager available
            JOptionPane.showMessageDialog(this,
                    "Dokument: " + result.getDocumentName()
                            + "\nPfad: " + rawPath,
                    "Suchergebnis", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            // ARCHIVE results use docId (UUID) — not a filesystem path.
            // Load content from the archive repository and open directly.
            if (result.getSource() == SearchResult.SourceType.ARCHIVE) {
                openArchiveResult(result);
                return;
            }

            // Build prefixed path based on source type so the routing in
            // MainFrame.openFileOrDirectory() can dispatch to the correct backend
            String prefixedPath = de.bund.zrb.files.path.VirtualResourceRef.buildPrefixedPath(
                    sourceTypeToBackend(result.getSource()), rawPath);

            // Route through MainframeContext which handles mail://, ndv://, local://, ftp:
            tabManager.getMainframeContext()
                    .openFileOrDirectory(prefixedPath, null, lastQuery, null);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Fehler beim \u00d6ffnen:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Open an ARCHIVE search result by loading the document content from H2 + Data Lake
     * and displaying it in a new file tab.
     */
    private void openArchiveResult(SearchResult result) {
        String docId = result.getDocumentId();
        try {
            de.bund.zrb.archive.store.ArchiveRepository repo =
                    de.bund.zrb.archive.store.ArchiveRepository.getInstance();
            de.bund.zrb.archive.model.ArchiveDocument doc = repo.findDocumentById(docId);

            if (doc == null) {
                JOptionPane.showMessageDialog(this,
                        "Archiv-Dokument nicht gefunden: " + docId,
                        "Archiv-Fehler", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Load text content from Data Lake storage
            String content = null;
            if (doc.getTextContentPath() != null && !doc.getTextContentPath().isEmpty()) {
                de.bund.zrb.archive.service.ArchiveService archiveService =
                        de.bund.zrb.archive.service.ArchiveService.getInstance();
                content = archiveService.getStorageService().readContent(doc.getTextContentPath());
            }

            if (content == null || content.isEmpty()) {
                // Fallback: show excerpt if no full content available
                content = doc.getExcerpt() != null ? doc.getExcerpt() : "(Kein Inhalt verfügbar)";
            }

            // Build a display title
            String title = doc.getTitle() != null && !doc.getTitle().isEmpty()
                    ? doc.getTitle()
                    : result.getDocumentName();
            String url = doc.getCanonicalUrl() != null ? doc.getCanonicalUrl() : "";

            // Open as a virtual file tab
            String sourceName = "📦 " + title;
            if (!url.isEmpty()) {
                sourceName += " (" + url + ")";
            }

            de.bund.zrb.ui.VirtualResource resource = new de.bund.zrb.ui.VirtualResource(
                    de.bund.zrb.files.path.VirtualResourceRef.of(sourceName),
                    de.bund.zrb.ui.VirtualResourceKind.FILE,
                    null,
                    true);

            tabManager.openFileTab(resource, content, null, lastQuery, false);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Fehler beim Laden des Archiv-Dokuments:\n" + e.getMessage(),
                    "Archiv-Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Bookmarking (Star)
    // ═══════════════════════════════════════════════════════════════

    private boolean isResultBookmarked(int modelRow) {
        if (tabManager == null || modelRow < 0 || modelRow >= currentResults.size()) return false;
        de.bund.zrb.ui.drawer.LeftDrawer drawer = tabManager.getBookmarkDrawer();
        if (drawer == null) return false;
        SearchResult r = currentResults.get(modelRow);
        return drawer.isBookmarked(r.getDocumentId(), sourceTypeToBackend(r.getSource()));
    }

    private void toggleBookmarkForRow(int viewRow) {
        if (tabManager == null) return;
        int modelRow = resultTable.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= currentResults.size()) return;
        de.bund.zrb.ui.drawer.LeftDrawer drawer = tabManager.getBookmarkDrawer();
        if (drawer == null) return;
        SearchResult r = currentResults.get(modelRow);
        drawer.toggleBookmark(r.getDocumentId(), sourceTypeToBackend(r.getSource()));
    }

    private static String sourceTypeToBackend(SearchResult.SourceType type) {
        switch (type) {
            case LOCAL: return "LOCAL";
            case FTP:   return "FTP";
            case NDV:   return "NDV";
            case MAIL:  return "MAIL";
            case ARCHIVE: return "ARCHIVE";
            default:    return "LOCAL";
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Saved Searches
    // ═══════════════════════════════════════════════════════════════

    private void saveCurrentSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;
        String name = JOptionPane.showInputDialog(this, "Name f\u00fcr die Suche:", query);
        if (name == null || name.trim().isEmpty()) return;
        savedSearches.add(new SavedSearch(name.trim(), query));
        savedSearchModel.addElement("\uD83D\uDD16 " + name.trim());
        persistSavedSearches();
    }

    private void loadSavedSearch() {
        int idx = savedSearchList.getSelectedIndex();
        if (idx < 0 || idx >= savedSearches.size()) return;
        searchField.setText(savedSearches.get(idx).query);
        performSearch();
    }

    private void deleteSavedSearch() {
        int idx = savedSearchList.getSelectedIndex();
        if (idx < 0 || idx >= savedSearches.size()) return;
        savedSearches.remove(idx);
        savedSearchModel.remove(idx);
        persistSavedSearches();
    }

    private void loadSavedSearches() {
        try {
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(SearchTab.class);
            String json = prefs.get(SAVED_SEARCHES_KEY, "[]");
            savedSearches.clear();
            savedSearchModel.clear();
            if (json.contains("\"name\"")) {
                String[] entries = json.split("\\},\\{");
                for (String entry : entries) {
                    String n = extractJsonValue(entry, "name");
                    String q = extractJsonValue(entry, "query");
                    if (n != null && q != null) {
                        savedSearches.add(new SavedSearch(n, q));
                        savedSearchModel.addElement("\uD83D\uDD16 " + n);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void persistSavedSearches() {
        try {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < savedSearches.size(); i++) {
                if (i > 0) sb.append(",");
                SavedSearch ss = savedSearches.get(i);
                sb.append("{\"name\":\"").append(escJson(ss.name))
                        .append("\",\"query\":\"").append(escJson(ss.query)).append("\"}");
            }
            sb.append("]");
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(SearchTab.class);
            prefs.put(SAVED_SEARCHES_KEY, sb.toString());
        } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    static String highlightTerms(String text, String query) {
        return SearchHighlighter.highlightHtml(text, query);
    }

    private static String escHtml(String s) {
        return SearchHighlighter.escHtml(s);
    }

    private static String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractJsonValue(String json, String key) {
        int idx = json.indexOf("\"" + key + "\":\"");
        if (idx < 0) return null;
        int start = idx + key.length() + 4;
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "\u2026" : s;
    }

    // ═══════════════════════════════════════════════════════════════
    //  FtpTab interface
    // ═══════════════════════════════════════════════════════════════

    @Override public String getTitle() { return "\uD83D\uDD0E Suche"; }
    @Override public String getTooltip() { return "\u00dcbergreifende Volltextsuche (Lucene)"; }
    @Override public JComponent getComponent() { return this; }
    @Override public void onClose() {
        if (currentSearch != null && !currentSearch.isDone()) currentSearch.cancel(true);
    }
    @Override public void saveIfApplicable() {}
    @Override public void focusSearchField() { searchField.requestFocusInWindow(); searchField.selectAll(); }
    @Override public void searchFor(String searchPattern) { searchField.setText(searchPattern); performSearch(); }
    @Override public String getContent() { return ""; }
    @Override public void markAsChanged() {}
    @Override public String getPath() { return "search://global"; }
    @Override public Type getType() { return Type.PREVIEW; }

    // ═══════════════════════════════════════════════════════════════
    //  Inner classes
    // ═══════════════════════════════════════════════════════════════

    private static class SavedSearch {
        final String name;
        final String query;
        SavedSearch(String name, String query) { this.name = name; this.query = query; }
    }
}
