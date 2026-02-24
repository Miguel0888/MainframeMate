package de.bund.zrb.ui.search;

import de.bund.zrb.search.SearchResult;
import de.bund.zrb.search.SearchService;
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
 * Tab for global full-text search across all indexed sources.
 *
 * Features:
 * - Search field with Enter-to-search
 * - Source filter checkboxes (Local, FTP, NDV, Mail)
 * - RAG toggle (hybrid BM25+embedding search)
 * - Results table with source icon, name, path, snippet, score
 * - Double-click to open result
 * - Snippet preview at bottom
 */
public class SearchTab extends JPanel implements FtpTab {

    private final SearchService searchService = SearchService.getInstance();

    // UI components
    private final JTextField searchField;
    private final JButton searchButton;
    private final JTable resultTable;
    private final DefaultTableModel tableModel;
    private final JTextArea previewArea;
    private final JLabel statusLabel;

    // Source filter checkboxes
    private final JCheckBox cbLocal;
    private final JCheckBox cbFtp;
    private final JCheckBox cbNdv;
    private final JCheckBox cbMail;
    private final JLabel ragStatusLabel;

    // Max results spinner
    private final JSpinner maxResultsSpinner;

    // Current results (for double-click handling)
    private final List<SearchResult> currentResults = new ArrayList<>();
    private Future<?> currentSearch = null;

    // Callback for opening files
    private Runnable onOpenCallback;

    public SearchTab() {
        setLayout(new BorderLayout(0, 4));
        setBorder(new EmptyBorder(4, 4, 4, 4));

        // ── Top: Search bar ──
        JPanel topPanel = new JPanel(new BorderLayout(4, 0));

        searchField = new JTextField();
        searchField.setFont(searchField.getFont().deriveFont(14f));
        searchField.putClientProperty("JTextField.placeholderText", "Suchbegriff eingeben…");
        searchField.addActionListener(e -> performSearch());

        searchButton = new JButton("🔍 Suchen");
        searchButton.setFocusable(false);
        searchButton.addActionListener(e -> performSearch());

        topPanel.add(searchField, BorderLayout.CENTER);
        topPanel.add(searchButton, BorderLayout.EAST);

        // ── Filter bar ──
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        filterPanel.setBorder(BorderFactory.createTitledBorder("Quellen"));

        cbLocal = new JCheckBox("📁 Lokal", true);
        cbFtp = new JCheckBox("🌐 FTP", true);
        cbNdv = new JCheckBox("🔗 NDV", true);
        cbMail = new JCheckBox("📧 Mail", true);

        // RAG status – automatic, not a toggle
        int semSize = de.bund.zrb.rag.service.RagService.getInstance().getSemanticIndexSize();
        if (semSize > 0) {
            ragStatusLabel = new JLabel("🤖 Hybrid (" + semSize + " Embeddings)");
            ragStatusLabel.setToolTipText("Semantic-Suche ist aktiv: " + semSize + " Chunks mit Embeddings gefunden. Dokumente mit Embeddings werden automatisch besser gerankt.");
        } else {
            ragStatusLabel = new JLabel("📝 BM25");
            ragStatusLabel.setToolTipText("Nur Volltextsuche aktiv. Aktiviere Embeddings in den Indexierungs-Regeln für Hybrid-Suche.");
        }
        ragStatusLabel.setForeground(semSize > 0 ? new Color(76, 175, 80) : Color.GRAY);

        filterPanel.add(cbLocal);
        filterPanel.add(cbFtp);
        filterPanel.add(cbNdv);
        filterPanel.add(cbMail);
        filterPanel.add(Box.createHorizontalStrut(16));
        filterPanel.add(ragStatusLabel);
        filterPanel.add(Box.createHorizontalStrut(16));
        filterPanel.add(new JLabel("Max:"));
        maxResultsSpinner = new JSpinner(new SpinnerNumberModel(50, 10, 500, 10));
        maxResultsSpinner.setToolTipText("Maximale Anzahl Ergebnisse");
        filterPanel.add(maxResultsSpinner);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(topPanel, BorderLayout.NORTH);
        headerPanel.add(filterPanel, BorderLayout.SOUTH);

        add(headerPanel, BorderLayout.NORTH);

        // ── Center: Results table ──
        String[] columns = {"Quelle", "Dokument", "Pfad / Ordner", "Vorschau", "Score"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        resultTable = new JTable(tableModel);
        resultTable.setRowHeight(28);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.setAutoCreateRowSorter(true);

        // Column widths
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(60);   // Quelle
        resultTable.getColumnModel().getColumn(0).setMaxWidth(80);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(200);  // Dokument
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(250);  // Pfad
        resultTable.getColumnModel().getColumn(3).setPreferredWidth(400);  // Vorschau
        resultTable.getColumnModel().getColumn(4).setPreferredWidth(60);   // Score
        resultTable.getColumnModel().getColumn(4).setMaxWidth(80);

        // Selection listener → preview
        resultTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updatePreview();
            }
        });

        // Double-click → open
        resultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedResult();
                }
            }
        });

        // ── Bottom: Preview + Status ──
        previewArea = new JTextArea(5, 0);
        previewArea.setEditable(false);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        previewArea.setBorder(BorderFactory.createTitledBorder("Vorschau"));

        statusLabel = new JLabel("Bereit.");
        statusLabel.setBorder(new EmptyBorder(2, 4, 2, 4));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JScrollPane(previewArea), BorderLayout.CENTER);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        // ── Split: Table + Preview ──
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(resultTable), bottomPanel);
        splitPane.setResizeWeight(0.7);
        splitPane.setDividerLocation(300);

        add(splitPane, BorderLayout.CENTER);

        // Keyboard shortcut: Escape clears search
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchField.setText("");
                    clearResults();
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  Search execution
    // ═══════════════════════════════════════════════════════════════

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        // Cancel previous search
        if (currentSearch != null && !currentSearch.isDone()) {
            currentSearch.cancel(true);
        }

        clearResults();
        searchButton.setEnabled(false);
        statusLabel.setText("🔍 Suche nach: \"" + query + "\"…");
        statusLabel.setForeground(new Color(255, 152, 0));

        Set<SearchResult.SourceType> sources = getSelectedSources();
        int maxResults = (Integer) maxResultsSpinner.getValue();
        boolean useRag = false; // Hybrid search runs automatically when embeddings available

        long startTime = System.currentTimeMillis();

        // Run search in background
        new SwingWorker<List<SearchResult>, SearchResult>() {
            @Override
            protected List<SearchResult> doInBackground() {
                return searchService.search(query, sources, maxResults, useRag);
            }

            @Override
            protected void done() {
                try {
                    List<SearchResult> results = get();
                    currentResults.clear();
                    currentResults.addAll(results);
                    tableModel.setRowCount(0);

                    for (SearchResult r : results) {
                        tableModel.addRow(new Object[]{
                                r.getSource().getIcon() + " " + r.getSource().getLabel(),
                                r.getDocumentName(),
                                r.getPath(),
                                r.getSnippet(),
                                String.format("%.2f", r.getScore())
                        });
                    }

                    long elapsed = System.currentTimeMillis() - startTime;
                    int semSize = de.bund.zrb.rag.service.RagService.getInstance().getSemanticIndexSize();
                    String mode = semSize > 0 ? "Hybrid (BM25 + " + semSize + " Embeddings)" : "BM25";
                    statusLabel.setText("✅ " + results.size() + " Ergebnis(se) in " + elapsed + " ms (" + mode + ")");
                    statusLabel.setForeground(new Color(76, 175, 80));

                    if (results.isEmpty()) {
                        statusLabel.setText("Keine Ergebnisse für: \"" + query + "\"");
                        statusLabel.setForeground(Color.GRAY);
                    }
                } catch (Exception e) {
                    statusLabel.setText("❌ Fehler: " + e.getMessage());
                    statusLabel.setForeground(new Color(244, 67, 54));
                } finally {
                    searchButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void clearResults() {
        tableModel.setRowCount(0);
        currentResults.clear();
        previewArea.setText("");
    }

    private Set<SearchResult.SourceType> getSelectedSources() {
        Set<SearchResult.SourceType> sources = new LinkedHashSet<>();
        if (cbLocal.isSelected()) sources.add(SearchResult.SourceType.LOCAL);
        if (cbFtp.isSelected()) sources.add(SearchResult.SourceType.FTP);
        if (cbNdv.isSelected()) sources.add(SearchResult.SourceType.NDV);
        if (cbMail.isSelected()) sources.add(SearchResult.SourceType.MAIL);
        return sources;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Preview & Open
    // ═══════════════════════════════════════════════════════════════

    private void updatePreview() {
        int row = resultTable.getSelectedRow();
        if (row < 0 || row >= currentResults.size()) {
            previewArea.setText("");
            return;
        }

        // Convert view row to model row (in case of sorting)
        int modelRow = resultTable.convertRowIndexToModel(row);
        if (modelRow < 0 || modelRow >= currentResults.size()) {
            previewArea.setText("");
            return;
        }

        SearchResult result = currentResults.get(modelRow);
        StringBuilder sb = new StringBuilder();
        sb.append("📄 ").append(result.getDocumentName()).append("\n");
        sb.append("📍 ").append(result.getPath()).append("\n");
        if (result.getHeading() != null && !result.getHeading().isEmpty()) {
            sb.append("📌 ").append(result.getHeading()).append("\n");
        }
        sb.append("⭐ Score: ").append(String.format("%.4f", result.getScore()));
        sb.append("  |  Quelle: ").append(result.getSource().getLabel()).append("\n");
        sb.append("────────────────────────────────────────────────────────────").append("\n");
        sb.append(result.getSnippet());

        previewArea.setText(sb.toString());
        previewArea.setCaretPosition(0);
    }

    private void openSelectedResult() {
        int row = resultTable.getSelectedRow();
        if (row < 0) return;
        int modelRow = resultTable.convertRowIndexToModel(row);
        if (modelRow < 0 || modelRow >= currentResults.size()) return;

        SearchResult result = currentResults.get(modelRow);
        // TODO: Open file/mail based on source type and documentId
        // For now: show info dialog
        JOptionPane.showMessageDialog(this,
                "Dokument: " + result.getDocumentName()
                        + "\nPfad: " + result.getPath()
                        + "\nQuelle: " + result.getSource().getLabel()
                        + "\nDocument-ID: " + result.getDocumentId(),
                "Suchergebnis öffnen",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // ═══════════════════════════════════════════════════════════════
    //  FtpTab interface
    // ═══════════════════════════════════════════════════════════════

    @Override
    public String getTitle() { return "🔎 Suche"; }

    @Override
    public String getTooltip() { return "Übergreifende Volltextsuche"; }

    @Override
    public JComponent getComponent() { return this; }

    @Override
    public void onClose() {
        if (currentSearch != null && !currentSearch.isDone()) {
            currentSearch.cancel(true);
        }
    }

    @Override
    public void saveIfApplicable() { /* read-only */ }

    @Override
    public void focusSearchField() {
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    @Override
    public void searchFor(String searchPattern) {
        searchField.setText(searchPattern);
        performSearch();
    }

    // ─── Bookmarkable ───

    @Override
    public String getContent() { return ""; }

    @Override
    public void markAsChanged() { /* read-only */ }

    @Override
    public String getPath() { return "search://global"; }

    @Override
    public Type getType() { return Type.PREVIEW; }
}
