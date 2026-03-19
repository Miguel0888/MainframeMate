package de.bund.zrb.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.confluence.ConfluenceRestClient;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ConnectionTab for browsing Confluence Data Center spaces and pages.
 * <p>
 * Layout: vertical split — top: space selector + search + results table,
 * bottom: HTML preview of selected page content.
 * <p>
 * Authentication uses mTLS (client certificate from Windows-MY) + Basic Auth.
 */
public class ConfluenceConnectionTab implements ConnectionTab {

    private static final Logger LOG = Logger.getLogger(ConfluenceConnectionTab.class.getName());
    private static final int PAGE_SIZE = 25;

    private final JPanel mainPanel;
    private final ConfluenceRestClient client;
    private final String baseUrl;

    // ── UI components ──
    private final JComboBox<SpaceItem> spaceSelector;
    private final JTextField searchField;
    private final JTextField filterField;
    private final JEditorPane htmlPane;
    private final JLabel statusLabel;

    // ── Results table ──
    private final PageTableModel pageModel;
    private final JTable pageTable;
    private final TableRowSorter<PageTableModel> pageSorter;

    // ── State ──
    private int currentStart = 0;
    private String currentSpaceKey = null;

    public ConfluenceConnectionTab(ConfluenceRestClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.mainPanel = new JPanel(new BorderLayout(0, 0));

        // ═══════════════════════════════════════════════════════════
        //  Top panel: space selector + search
        // ═══════════════════════════════════════════════════════════
        JPanel topControls = new JPanel();
        topControls.setLayout(new BoxLayout(topControls, BoxLayout.Y_AXIS));
        topControls.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Row 1: Space selector + Load button
        JPanel spaceRow = new JPanel(new BorderLayout(4, 0));
        spaceRow.add(new JLabel("Space: "), BorderLayout.WEST);
        spaceSelector = new JComboBox<SpaceItem>();
        spaceSelector.setMaximumRowCount(20);
        spaceSelector.addActionListener(e -> {
            SpaceItem sel = (SpaceItem) spaceSelector.getSelectedItem();
            if (sel != null) {
                currentSpaceKey = sel.key;
                currentStart = 0;
                loadPages();
            }
        });
        spaceRow.add(spaceSelector, BorderLayout.CENTER);

        JButton refreshBtn = new JButton("🔄 Spaces laden");
        refreshBtn.setToolTipText("Spaces vom Server laden");
        refreshBtn.addActionListener(e -> loadSpaces());
        spaceRow.add(refreshBtn, BorderLayout.EAST);
        spaceRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        topControls.add(spaceRow);
        topControls.add(Box.createVerticalStrut(4));

        // Row 2: CQL Search
        JPanel searchPanel = new JPanel(new BorderLayout(4, 0));
        searchPanel.add(new JLabel("🔍 CQL: "), BorderLayout.WEST);
        searchField = new JTextField();
        searchField.setToolTipText("Confluence-Suche (CQL). z.B. type=page AND text~\"Suchbegriff\"");
        searchField.addActionListener(e -> searchConfluence());
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        topControls.add(searchPanel);

        // ═══════════════════════════════════════════════════════════
        //  Results table
        // ═══════════════════════════════════════════════════════════
        pageModel = new PageTableModel();
        pageTable = new JTable(pageModel);
        pageTable.setRowHeight(22);
        pageTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pageTable.getColumnModel().getColumn(0).setPreferredWidth(350);
        pageTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        pageTable.getColumnModel().getColumn(2).setPreferredWidth(60);

        pageSorter = new TableRowSorter<PageTableModel>(pageModel);
        pageTable.setRowSorter(pageSorter);

        // Single-click → preview
        pageTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int viewRow = pageTable.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = pageTable.convertRowIndexToModel(viewRow);
                    PageItem item = pageModel.getItemAt(modelRow);
                    if (item != null) loadPreview(item);
                }
            }
        });

        // Double-click → open in browser
        pageTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedInBrowser();
                }
            }
        });

        JScrollPane resultScroll = new JScrollPane(pageTable);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Seiten"));

        // Pagination bar
        JPanel pageBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton prevBtn = new JButton("◀ Zurück");
        prevBtn.addActionListener(e -> {
            if (currentStart >= PAGE_SIZE) {
                currentStart -= PAGE_SIZE;
                loadPages();
            }
        });
        JButton nextBtn = new JButton("Weiter ▶");
        nextBtn.addActionListener(e -> {
            currentStart += PAGE_SIZE;
            loadPages();
        });
        pageBar.add(prevBtn);
        pageBar.add(nextBtn);
        statusLabel = new JLabel(" ");
        pageBar.add(statusLabel);

        // Filter bar for local regex filtering
        filterField = new JTextField();
        filterField.setToolTipText("Ergebnisse filtern (Regex)");
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        JPanel filterBar = new JPanel(new BorderLayout(2, 0));
        filterBar.add(new JLabel(" 🔎 "), BorderLayout.WEST);
        filterBar.add(filterField, BorderLayout.CENTER);

        JPanel topHalf = new JPanel(new BorderLayout(0, 0));
        topHalf.add(topControls, BorderLayout.NORTH);

        JPanel resultPanel = new JPanel(new BorderLayout(0, 0));
        resultPanel.add(resultScroll, BorderLayout.CENTER);
        resultPanel.add(pageBar, BorderLayout.SOUTH);
        topHalf.add(resultPanel, BorderLayout.CENTER);

        // ═══════════════════════════════════════════════════════════
        //  HTML preview pane
        // ═══════════════════════════════════════════════════════════
        htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        htmlPane.setContentType("text/html");
        JScrollPane htmlScroll = new JScrollPane(htmlPane);
        htmlScroll.setBorder(BorderFactory.createTitledBorder("Vorschau"));

        // ═══════════════════════════════════════════════════════════
        //  Main split: top/bottom
        // ═══════════════════════════════════════════════════════════
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topHalf, htmlScroll);
        mainSplit.setDividerLocation(300);
        mainSplit.setResizeWeight(0.4);
        mainPanel.add(mainSplit, BorderLayout.CENTER);
        mainPanel.add(filterBar, BorderLayout.SOUTH);

        // Trigger initial load of spaces
        SwingUtilities.invokeLater(this::loadSpaces);
    }

    // ═══════════════════════════════════════════════════════════
    //  Data loading
    // ═══════════════════════════════════════════════════════════

    private void loadSpaces() {
        statusLabel.setText("⏳ Lade Spaces…");
        mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<List<SpaceItem>, Void>() {
            @Override
            protected List<SpaceItem> doInBackground() {
                List<SpaceItem> spaces = new ArrayList<SpaceItem>();
                int start = 0;
                boolean hasMore = true;
                while (hasMore) {
                    String json = client.getSpacesJson(start, 100);
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    JsonArray results = root.getAsJsonArray("results");
                    if (results != null) {
                        for (JsonElement el : results) {
                            JsonObject sp = el.getAsJsonObject();
                            String key = sp.get("key").getAsString();
                            String name = sp.has("name") ? sp.get("name").getAsString() : key;
                            spaces.add(new SpaceItem(key, name));
                        }
                    }
                    // Pagination
                    int size = root.has("size") ? root.get("size").getAsInt() : 0;
                    hasMore = size > 0 && root.has("_links")
                            && root.getAsJsonObject("_links").has("next");
                    start += size;
                }
                return spaces;
            }

            @Override
            protected void done() {
                mainPanel.setCursor(Cursor.getDefaultCursor());
                try {
                    List<SpaceItem> spaces = get();
                    spaceSelector.removeAllItems();
                    for (SpaceItem sp : spaces) {
                        spaceSelector.addItem(sp);
                    }
                    statusLabel.setText(spaces.size() + " Spaces geladen");
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Confluence] Spaces laden fehlgeschlagen", e);
                    statusLabel.setText("❌ Fehler: " + e.getMessage());
                    JOptionPane.showMessageDialog(mainPanel,
                            "Confluence-Spaces konnten nicht geladen werden:\n" + e.getMessage(),
                            "Verbindungsfehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void loadPages() {
        if (currentSpaceKey == null) return;
        statusLabel.setText("⏳ Lade Seiten für " + currentSpaceKey + "…");
        mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        final String spaceKey = currentSpaceKey;
        final int start = currentStart;

        new SwingWorker<List<PageItem>, Void>() {
            @Override
            protected List<PageItem> doInBackground() {
                List<PageItem> pages = new ArrayList<PageItem>();
                String json = client.getContentJson(spaceKey, start, PAGE_SIZE);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray results = root.getAsJsonArray("results");
                if (results != null) {
                    for (JsonElement el : results) {
                        JsonObject pg = el.getAsJsonObject();
                        String id = pg.get("id").getAsString();
                        String title = pg.get("title").getAsString();
                        String type = pg.has("type") ? pg.get("type").getAsString() : "page";
                        int version = 0;
                        if (pg.has("version") && pg.getAsJsonObject("version").has("number")) {
                            version = pg.getAsJsonObject("version").get("number").getAsInt();
                        }
                        pages.add(new PageItem(id, title, type, spaceKey, version));
                    }
                }
                return pages;
            }

            @Override
            protected void done() {
                mainPanel.setCursor(Cursor.getDefaultCursor());
                try {
                    List<PageItem> pages = get();
                    pageModel.setItems(pages);
                    statusLabel.setText(spaceKey + ": " + pages.size() + " Ergebnisse (ab " + start + ")");
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Confluence] Seiten laden fehlgeschlagen", e);
                    statusLabel.setText("❌ " + e.getMessage());
                }
            }
        }.execute();
    }

    private void searchConfluence() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        statusLabel.setText("⏳ Suche: " + query + "…");
        mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<List<PageItem>, Void>() {
            @Override
            protected List<PageItem> doInBackground() {
                List<PageItem> pages = new ArrayList<PageItem>();
                // If the query doesn't contain CQL operators, wrap it in a simple text search
                String cql = query;
                if (!cql.contains("=") && !cql.contains("~")) {
                    cql = "type=page AND text~\"" + query.replace("\"", "\\\"") + "\"";
                }
                String json = client.searchJson(cql, 0, PAGE_SIZE);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray results = root.getAsJsonArray("results");
                if (results != null) {
                    for (JsonElement el : results) {
                        JsonObject pg = el.getAsJsonObject();
                        String id = pg.get("id").getAsString();
                        String title = pg.get("title").getAsString();
                        String type = pg.has("type") ? pg.get("type").getAsString() : "page";
                        String spKey = "";
                        if (pg.has("space") && pg.getAsJsonObject("space").has("key")) {
                            spKey = pg.getAsJsonObject("space").get("key").getAsString();
                        }
                        int version = 0;
                        if (pg.has("version") && pg.getAsJsonObject("version").has("number")) {
                            version = pg.getAsJsonObject("version").get("number").getAsInt();
                        }
                        pages.add(new PageItem(id, title, type, spKey, version));
                    }
                }
                return pages;
            }

            @Override
            protected void done() {
                mainPanel.setCursor(Cursor.getDefaultCursor());
                try {
                    List<PageItem> pages = get();
                    pageModel.setItems(pages);
                    statusLabel.setText("Suche: " + pages.size() + " Treffer");
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Confluence] Suche fehlgeschlagen", e);
                    statusLabel.setText("❌ " + e.getMessage());
                }
            }
        }.execute();
    }

    private void loadPreview(PageItem item) {
        statusLabel.setText("⏳ Lade Vorschau: " + item.title + "…");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                String json = client.getContentByIdJson(item.id);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                if (root.has("body") && root.getAsJsonObject("body").has("view")) {
                    return root.getAsJsonObject("body").getAsJsonObject("view")
                            .get("value").getAsString();
                }
                return "<p><i>Kein Inhalt verfügbar.</i></p>";
            }

            @Override
            protected void done() {
                try {
                    String html = get();
                    // Wrap in basic HTML with base URL for relative links
                    String fullHtml = "<html><head><base href=\"" + baseUrl + "\">"
                            + "<style>body{font-family:sans-serif;font-size:13px;padding:8px;}"
                            + "table{border-collapse:collapse;} td,th{border:1px solid #ccc;padding:4px;}"
                            + "img{max-width:100%;}</style></head><body>"
                            + html + "</body></html>";
                    htmlPane.setText(fullHtml);
                    htmlPane.setCaretPosition(0);
                    statusLabel.setText("Vorschau: " + item.title);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Confluence] Vorschau laden fehlgeschlagen", e);
                    statusLabel.setText("❌ " + e.getMessage());
                }
            }
        }.execute();
    }

    private void openSelectedInBrowser() {
        int viewRow = pageTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = pageTable.convertRowIndexToModel(viewRow);
        PageItem item = pageModel.getItemAt(modelRow);
        if (item == null) return;
        try {
            String url = baseUrl + "/pages/viewpage.action?pageId=" + item.id;
            Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Confluence] Browser öffnen fehlgeschlagen", e);
        }
    }

    private void applyFilter() {
        String text = filterField.getText().trim();
        if (text.isEmpty()) {
            pageSorter.setRowFilter(null);
        } else {
            try {
                pageSorter.setRowFilter(javax.swing.RowFilter.regexFilter(
                        "(?i)" + text, 0)); // column 0 = title
            } catch (java.util.regex.PatternSyntaxException ignore) {
                // invalid regex — ignore
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ConnectionTab interface
    // ═══════════════════════════════════════════════════════════

    @Override
    public String getTitle() { return "📚 Confluence"; }

    @Override
    public String getTooltip() { return "Confluence: " + baseUrl; }

    @Override
    public JComponent getComponent() { return mainPanel; }

    @Override
    public void onClose() { /* stateless HTTP — nothing to close */ }

    @Override
    public void saveIfApplicable() { /* read-only */ }

    @Override
    public String getContent() { return ""; }

    @Override
    public void markAsChanged() { }

    @Override
    public String getPath() { return "confluence://" + baseUrl; }

    @Override
    public Type getType() { return Type.CONNECTION; }

    @Override
    public void focusSearchField() {
        searchField.requestFocusInWindow();
    }

    @Override
    public void searchFor(String searchPattern) {
        searchField.setText(searchPattern);
        searchConfluence();
    }

    // ═══════════════════════════════════════════════════════════
    //  Inner model classes
    // ═══════════════════════════════════════════════════════════

    private static final class SpaceItem {
        final String key;
        final String name;

        SpaceItem(String key, String name) {
            this.key = key;
            this.name = name;
        }

        @Override
        public String toString() { return key + " — " + name; }
    }

    static final class PageItem {
        final String id;
        final String title;
        final String type;
        final String spaceKey;
        final int version;

        PageItem(String id, String title, String type, String spaceKey, int version) {
            this.id = id;
            this.title = title;
            this.type = type;
            this.spaceKey = spaceKey;
            this.version = version;
        }
    }

    private static final class PageTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Titel", "Space", "Version"};
        private final List<PageItem> items = new ArrayList<PageItem>();

        void setItems(List<PageItem> newItems) {
            items.clear();
            items.addAll(newItems);
            fireTableDataChanged();
        }

        PageItem getItemAt(int row) {
            return row >= 0 && row < items.size() ? items.get(row) : null;
        }

        @Override public int getRowCount() { return items.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int c) { return COLUMNS[c]; }

        @Override
        public Object getValueAt(int row, int col) {
            PageItem p = items.get(row);
            switch (col) {
                case 0: return p.title;
                case 1: return p.spaceKey;
                case 2: return p.version;
                default: return "";
            }
        }
    }
}

