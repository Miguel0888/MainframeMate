package de.bund.zrb.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.confluence.ConfluencePrefetchService;
import de.bund.zrb.confluence.ConfluenceRestClient;
import de.bund.zrb.wiki.domain.OutlineNode;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ConnectionTab for browsing Confluence Data Center spaces and pages.
 * <p>
 * Layout: vertical split — top: space selector + search + results table,
 * bottom: HTML preview of selected page content.
 * <p>
 * Outline is extracted from HTML headings and delegated to the RightDrawer.
 * Relations (ancestors, children, labels) are shown in the LeftDrawer.
 * <p>
 * Authentication uses mTLS (client certificate from Windows-MY) + Basic Auth.
 */
public class ConfluenceConnectionTab implements ConnectionTab {

    private static final Logger LOG = Logger.getLogger(ConfluenceConnectionTab.class.getName());
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};

    private final JPanel mainPanel;
    private final ConfluenceRestClient client;
    private final String baseUrl;

    // ── UI components ──
    private final JPanel spaceCheckboxPanel;
    private final List<JCheckBox> spaceCheckboxes = new ArrayList<JCheckBox>();
    private final JTextField searchField;
    private final JTextField filterField;
    private final JEditorPane htmlPane;
    private final JLabel statusLabel;
    private final JLabel pageInfoLabel;
    private final JButton prevBtn;
    private final JButton nextBtn;
    private final JButton indexBtn;
    private final JComboBox<Integer> pageSizeCombo;

    // ── Results table ──
    private final PageTableModel pageModel;
    private final JTable pageTable;
    private final TableRowSorter<PageTableModel> pageSorter;

    // ── State ──
    private int currentStart = 0;
    private int pageSize = DEFAULT_PAGE_SIZE;
    private int totalResults = -1; // -1 = unknown
    private String currentCql = null;

    /** Currently previewed page. */
    private PageItem currentPreviewItem;
    /** Parsed outline of the currently previewed page. */
    private OutlineNode currentOutline;
    /** Raw HTML body of the current preview (for outline scroll anchors). */
    private String currentHtmlBody;

    // ── Callbacks ──
    /** Callback to notify about outline changes (for RightDrawer). */
    private OutlineCallback outlineCallback;
    /** Callback to update the dependency/relations panel (LeftDrawer) for the previewed page. */
    private DependencyCallback dependencyCallback;
    /** Callback to persist state changes immediately (e.g. on checkbox toggle). */
    private Runnable stateSaveCallback;
    /** Callback to open a page as a dedicated reader tab. */
    private OpenCallback openCallback;
    /** Prefetch service for background loading of search result pages. */
    private ConfluencePrefetchService prefetchService;
    /** Callback to index a page into the search index. */
    private IndexCallback indexCallback;

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

        // Row 1: [🔄 Refresh] [Spaces: checkboxes …] [📥 Indexieren]
        JPanel spaceRow = new JPanel(new BorderLayout(4, 0));

        JButton refreshBtn = new JButton("🔄");
        refreshBtn.setToolTipText("Spaces vom Server neu laden");
        refreshBtn.setMargin(new Insets(2, 6, 2, 6));
        refreshBtn.setFocusable(false);
        refreshBtn.addActionListener(e -> loadSpaces());
        spaceRow.add(refreshBtn, BorderLayout.WEST);

        spaceCheckboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        spaceRow.add(spaceCheckboxPanel, BorderLayout.CENTER);

        indexBtn = new JButton("📥 Indexieren");
        indexBtn.setToolTipText("Aktuelle Seite in den Suchindex aufnehmen");
        indexBtn.setFocusable(false);
        indexBtn.setEnabled(false);
        indexBtn.addActionListener(e -> indexCurrentPreview());
        spaceRow.add(indexBtn, BorderLayout.EAST);

        topControls.add(spaceRow);
        topControls.add(Box.createVerticalStrut(4));

        // Row 2: [🔍 Search field] [◀ Seite X von Y ▶] [25▼]
        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.add(new JLabel("🔍"), BorderLayout.WEST);
        searchField = new JTextField();
        searchField.setToolTipText(
                "<html><b>Confluence durchsuchen</b> (Enter)<br>"
                + "Freitext-Suche oder CQL-Syntax:<br>"
                + "<br>"
                + "<b>Freitext:</b> Suchbegriff<br>"
                + "<b>Exakt:</b> text~\"exakter Ausdruck\"<br>"
                + "<b>Titel:</b> title=\"Seitenname\"<br>"
                + "<b>Label:</b> label=mein-label<br>"
                + "<b>Typ:</b> type=page AND text~\"Begriff\"<br>"
                + "<b>Kombiniert:</b> space=KEY AND title~\"Test\"<br>"
                + "<br>"
                + "Ausgewählte Spaces werden automatisch als Filter ergänzt.</html>");
        searchField.addActionListener(e -> searchConfluence());
        // Arrow keys: move to result table
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int rowCount = pageTable.getRowCount();
                if (rowCount == 0) return;
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    pageTable.setRowSelectionInterval(0, 0);
                    pageTable.scrollRectToVisible(pageTable.getCellRect(0, 0, true));
                    pageTable.requestFocusInWindow();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    int last = rowCount - 1;
                    pageTable.setRowSelectionInterval(last, last);
                    pageTable.scrollRectToVisible(pageTable.getCellRect(last, 0, true));
                    pageTable.requestFocusInWindow();
                    e.consume();
                }
            }
        });
        searchRow.add(searchField, BorderLayout.CENTER);

        // Pagination controls: [◀] [Seite X von Y] [▶] [25▼] — right of search
        JPanel paginationPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));

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

        // Double-click → open as reader tab (fallback: browser)
        pageTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedAsReaderTab();
                }
            }
        });

        // Enter → open as reader tab (fallback: browser)
        pageTable.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openAsReaderTab");
        pageTable.getActionMap().put("openAsReaderTab", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSelectedAsReaderTab();
            }
        });

        JScrollPane resultScroll = new JScrollPane(pageTable);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Seiten"));

        prevBtn = new JButton("◀");
        prevBtn.setMargin(new Insets(1, 4, 1, 4));
        prevBtn.setToolTipText("Vorherige Seite");
        prevBtn.setFocusable(false);
        prevBtn.setEnabled(false);
        prevBtn.addActionListener(e -> {
            if (currentStart > 0 && currentCql != null) {
                currentStart = Math.max(0, currentStart - pageSize);
                executeSearch(currentCql, currentStart);
            }
        });

        nextBtn = new JButton("▶");
        nextBtn.setMargin(new Insets(1, 4, 1, 4));
        nextBtn.setToolTipText("Nächste Seite");
        nextBtn.setFocusable(false);
        nextBtn.setEnabled(false);
        nextBtn.addActionListener(e -> {
            if (currentCql != null) {
                currentStart += pageSize;
                executeSearch(currentCql, currentStart);
            }
        });

        pageInfoLabel = new JLabel(" ");
        pageInfoLabel.setFont(pageInfoLabel.getFont().deriveFont(Font.BOLD));

        pageSizeCombo = new JComboBox<Integer>();
        for (int sz : PAGE_SIZE_OPTIONS) {
            pageSizeCombo.addItem(sz);
        }
        pageSizeCombo.setSelectedItem(DEFAULT_PAGE_SIZE);
        pageSizeCombo.setToolTipText("Ergebnisse pro Seite");
        pageSizeCombo.setMaximumRowCount(PAGE_SIZE_OPTIONS.length);
        pageSizeCombo.addActionListener(e -> {
            Object sel = pageSizeCombo.getSelectedItem();
            if (sel instanceof Integer) {
                pageSize = (Integer) sel;
                if (currentCql != null) {
                    currentStart = 0;
                    executeSearch(currentCql, 0);
                }
            }
        });

        paginationPanel.add(prevBtn);
        paginationPanel.add(pageInfoLabel);
        paginationPanel.add(nextBtn);
        paginationPanel.add(Box.createHorizontalStrut(4));
        paginationPanel.add(pageSizeCombo);
        searchRow.add(paginationPanel, BorderLayout.EAST);
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        topControls.add(searchRow);

        // Status label below search row
        statusLabel = new JLabel(" ");

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
        resultPanel.add(statusLabel, BorderLayout.SOUTH);
        topHalf.add(resultPanel, BorderLayout.CENTER);

        // ═══════════════════════════════════════════════════════════
        //  HTML preview pane
        // ═══════════════════════════════════════════════════════════
        htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        htmlPane.setContentType("text/html");
        htmlPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                handleLink(e);
            }
        });
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
                            String homepageId = null;
                            if (sp.has("homepage") && sp.get("homepage").isJsonObject()) {
                                JsonObject hp = sp.getAsJsonObject("homepage");
                                if (hp.has("id")) {
                                    homepageId = hp.get("id").getAsString();
                                }
                            }
                            spaces.add(new SpaceItem(key, name, homepageId));
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
                    // Rebuild checkbox panel
                    spaceCheckboxPanel.removeAll();
                    spaceCheckboxes.clear();
                    for (SpaceItem sp : spaces) {
                        JCheckBox cb = new JCheckBox("", true);
                        cb.setToolTipText(sp.key + " — " + sp.name);
                        cb.putClientProperty("spaceItem", sp);
                        cb.addItemListener(e -> {
                            if (stateSaveCallback != null) stateSaveCallback.run();
                        });
                        spaceCheckboxes.add(cb);

                        // Underlined link label — opens space homepage in Reader
                        JLabel link = new JLabel("<html><u>" + escHtml(sp.key) + "</u></html>");
                        link.setToolTipText(sp.key + " — " + sp.name);
                        link.setForeground(UIManager.getColor("Hyperlink.foreground") != null
                                ? UIManager.getColor("Hyperlink.foreground")
                                : new Color(0x0066CC));
                        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        if (sp.homepageId != null) {
                            final String hpId = sp.homepageId;
                            link.addMouseListener(new MouseAdapter() {
                                @Override
                                public void mouseClicked(MouseEvent e) {
                                    openPageByIdAsReaderTab(hpId);
                                }
                            });
                        }

                        // Group checkbox + link as a compact pair
                        JPanel pair = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                        pair.setOpaque(false);
                        pair.add(cb);
                        pair.add(link);
                        spaceCheckboxPanel.add(pair);
                    }
                    // Restore persisted checkbox states
                    if (stateSaveCallback != null) {
                        try {
                            de.bund.zrb.model.Settings s = de.bund.zrb.helper.SettingsHelper.load();
                            restoreApplicationState(s.applicationState);
                        } catch (Exception ignore) {
                            // Settings not available yet — keep defaults
                        }
                    }
                    spaceCheckboxPanel.revalidate();
                    spaceCheckboxPanel.repaint();
                    statusLabel.setText(spaces.size() + " Spaces geladen");
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Confluence] Spaces laden fehlgeschlagen", e);
                    Throwable cause = e;
                    while (cause.getCause() != null && cause.getCause() != cause) {
                        cause = cause.getCause();
                    }
                    String rootMsg = cause.getClass().getSimpleName()
                            + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
                    statusLabel.setText("❌ " + rootMsg);
                    JOptionPane.showMessageDialog(mainPanel,
                            "Confluence-Spaces konnten nicht geladen werden:\n\n" + rootMsg,
                            "Verbindungsfehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void searchConfluence() {
        String query = searchField.getText().trim();

        // Build CQL
        String cql;
        if (query.isEmpty()) {
            // Empty search → list pages from checked spaces
            cql = "type=page";
        } else if (!query.contains("=") && !query.contains("~")) {
            cql = "type=page AND text~\"" + query.replace("\"", "\\\"") + "\"";
        } else {
            cql = query;
        }

        // Add space filter from checked checkboxes
        List<String> checkedKeys = getCheckedSpaceKeys();
        if (!checkedKeys.isEmpty() && checkedKeys.size() < spaceCheckboxes.size()) {
            // Only add filter if not all spaces are checked (= all = no filter needed)
            StringBuilder spaceFilter = new StringBuilder("space in (");
            for (int i = 0; i < checkedKeys.size(); i++) {
                if (i > 0) spaceFilter.append(",");
                spaceFilter.append("\"").append(checkedKeys.get(i)).append("\"");
            }
            spaceFilter.append(")");
            cql = spaceFilter + " AND " + cql;
        }

        currentCql = cql;
        currentStart = 0;
        executeSearch(cql, 0);
    }

    /** Execute a CQL search with the given offset and populate the results table. */
    private void executeSearch(final String cql, final int start) {
        statusLabel.setText("⏳ Suche…");
        prevBtn.setEnabled(false);
        nextBtn.setEnabled(false);
        mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        final int limit = pageSize;

        new SwingWorker<SearchResult, Void>() {
            @Override
            protected SearchResult doInBackground() {
                List<PageItem> pages = new ArrayList<PageItem>();
                String json = client.searchJson(cql, start, limit);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();

                int total = root.has("totalSize") ? root.get("totalSize").getAsInt()
                        : root.has("total") ? root.get("total").getAsInt() : -1;

                // Check whether the API signals more results via _links.next
                boolean hasNext = root.has("_links")
                        && root.getAsJsonObject("_links").has("next");

                JsonArray results = root.getAsJsonArray("results");
                if (results != null) {
                    for (JsonElement el : results) {
                        JsonObject pg = el.getAsJsonObject();
                        // Search results may wrap content in a "content" object
                        JsonObject content = pg.has("content") ? pg.getAsJsonObject("content") : pg;
                        String id = content.has("id") ? content.get("id").getAsString() : "";
                        String title = content.has("title") ? content.get("title").getAsString() : "";
                        String type = content.has("type") ? content.get("type").getAsString() : "page";
                        String spKey = "";
                        if (content.has("space") && content.getAsJsonObject("space").has("key")) {
                            spKey = content.getAsJsonObject("space").get("key").getAsString();
                        }
                        // Fallback: space from _expandable
                        if (spKey.isEmpty() && content.has("_expandable")
                                && content.getAsJsonObject("_expandable").has("space")) {
                            String spacePath = content.getAsJsonObject("_expandable").get("space").getAsString();
                            if (spacePath.contains("/")) {
                                spKey = spacePath.substring(spacePath.lastIndexOf("/") + 1);
                            }
                        }
                        int version = 0;
                        if (content.has("version") && content.getAsJsonObject("version").has("number")) {
                            version = content.getAsJsonObject("version").get("number").getAsInt();
                        }
                        if (!id.isEmpty()) {
                            pages.add(new PageItem(id, title, type, spKey, version));
                        }
                    }
                }
                return new SearchResult(pages, total, hasNext);
            }

            @Override
            protected void done() {
                mainPanel.setCursor(Cursor.getDefaultCursor());
                try {
                    SearchResult result = get();
                    totalResults = result.totalSize;
                    pageModel.setItems(result.pages);
                    updatePaginationUi(start, result.pages.size(), result.hasNextLink);
                    if (!result.pages.isEmpty() && pageTable.getRowCount() > 0) {
                        pageTable.setRowSelectionInterval(0, 0);
                    }
                    // Prefetch all result pages in background
                    triggerPrefetchForResults(result.pages);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Confluence] Suche fehlgeschlagen", e);
                    statusLabel.setText("❌ " + e.getMessage());
                    pageInfoLabel.setText(" ");
                }
            }
        }.execute();
    }

    /** Trigger background prefetch for all pages in the search result. */
    private void triggerPrefetchForResults(List<PageItem> pages) {
        if (prefetchService == null || pages.isEmpty()) return;
        List<String> ids = new ArrayList<String>();
        for (PageItem p : pages) {
            ids.add(p.id);
        }
        prefetchService.prefetchPages(ids, 0);
    }

    /** Trigger prefetch starting from the currently selected row. */
    private void triggerPrefetchFromCursor() {
        if (prefetchService == null) return;
        int viewRow = pageTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = pageTable.convertRowIndexToModel(viewRow);

        List<String> ids = new ArrayList<String>();
        for (int i = 0; i < pageModel.getRowCount(); i++) {
            PageItem p = pageModel.getItemAt(i);
            if (p != null) ids.add(p.id);
        }
        if (!ids.isEmpty()) {
            prefetchService.prefetchPages(ids, Math.max(0, modelRow));
        }
    }

    /** Update pagination buttons and page info label after a search completes. */
    private void updatePaginationUi(int start, int fetchedCount, boolean hasNextLink) {
        int currentPage = (start / pageSize) + 1;

        if (totalResults > 0) {
            int totalPages = Math.max(1, (int) Math.ceil((double) totalResults / pageSize));
            pageInfoLabel.setText(currentPage + "/" + totalPages);
            statusLabel.setText(totalResults + " Treffer gesamt");
            prevBtn.setEnabled(currentPage > 1);
            nextBtn.setEnabled(currentPage < totalPages);
        } else {
            // totalSize unknown or 0 — use hasNextLink from the API
            boolean hasMore = hasNextLink || fetchedCount >= pageSize;
            pageInfoLabel.setText(hasMore
                    ? currentPage + "/…"
                    : String.valueOf(currentPage));
            statusLabel.setText(fetchedCount + " Treffer" + (hasMore ? " (weitere vorhanden)" : ""));
            prevBtn.setEnabled(start > 0);
            nextBtn.setEnabled(hasMore);
        }
    }

    /** Wrapper for search results including totalSize from the API. */
    private static final class SearchResult {
        final List<PageItem> pages;
        final int totalSize;       // -1 if unknown
        final boolean hasNextLink; // true if _links.next was present

        SearchResult(List<PageItem> pages, int totalSize, boolean hasNextLink) {
            this.pages = pages;
            this.totalSize = totalSize;
            this.hasNextLink = hasNextLink;
        }
    }

    private void loadPreview(PageItem item) {
        // Check in-memory prefetch cache first (O(1))
        if (prefetchService != null) {
            ConfluencePrefetchService.CachedPage cached = prefetchService.getCached(item.id);
            if (cached != null) {
                applyPreview(item, cached.html());
                triggerPrefetchFromCursor();
                return;
            }
        }

        statusLabel.setText("⏳ Lade Vorschau: " + item.title + "…");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                if (prefetchService != null) {
                    // loadPriority uses its own dedicated thread, never blocked by prefetch pool
                    ConfluencePrefetchService.CachedPage page = prefetchService.loadPriority(item.id);
                    if (page != null) return page.html();
                }
                // Fallback: direct load
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
                    applyPreview(item, html);
                    triggerPrefetchFromCursor();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Confluence] Vorschau laden fehlgeschlagen", e);
                    statusLabel.setText("❌ " + e.getMessage());
                }
            }
        }.execute();
    }

    /** Apply a loaded page to the preview pane, extract outline, and trigger relation callbacks. */
    private void applyPreview(PageItem item, String html) {
        currentPreviewItem = item;
        currentHtmlBody = html;

        // Store in prefetch cache for instant re-selection
        if (prefetchService != null) {
            prefetchService.putInCache(item.id,
                    new ConfluencePrefetchService.CachedPage(item.id, item.title, html));
        }

        // Inject id attributes into headings that don't have one (needed for scrollToReference)
        String enrichedHtml = injectHeadingAnchors(html);

        // Wrap HTML for display
        String fullHtml = "<html><head><base href=\"" + escHtml(baseUrl) + "\">"
                + "<style>body{font-family:sans-serif;font-size:13px;padding:8px;}"
                + "table{border-collapse:collapse;} td,th{border:1px solid #ccc;padding:4px;}"
                + "img{max-width:100%;} h1,h2,h3{color:#333;} a{color:#0645ad;}"
                + "pre,code{background:#f4f4f4;padding:4px;}</style></head><body>"
                + enrichedHtml + "</body></html>";
        htmlPane.setText(fullHtml);
        htmlPane.setCaretPosition(0);
        statusLabel.setText("✅ Vorschau: " + item.title);

        // Enable index button when a preview is loaded
        indexBtn.setEnabled(indexCallback != null);

        // Load images asynchronously
        loadPreviewImagesAsync(html);

        // Parse outline from HTML headings
        currentOutline = parseOutlineSimple(html, item.title);

        // Notify RightDrawer
        if (outlineCallback != null) {
            outlineCallback.onOutlineChanged(currentOutline, item.title);
        }

        // Notify LeftDrawer (relations)
        if (dependencyCallback != null) {
            dependencyCallback.onDependenciesChanged(item);
        }
    }

    /**
     * Index the currently previewed Confluence page into the search index.
     */
    private void indexCurrentPreview() {
        if (currentPreviewItem == null || currentHtmlBody == null) {
            statusLabel.setText("⚠️ Keine Seite zum Indexieren geladen");
            return;
        }
        if (indexCallback == null) {
            statusLabel.setText("⚠️ Indexierung nicht verfügbar");
            return;
        }

        final String pageId = currentPreviewItem.id;
        final String title = currentPreviewItem.title;
        final String spaceKey = currentPreviewItem.spaceKey;
        final String html = currentHtmlBody;

        statusLabel.setText("⏳ Indexiere: " + title + "…");

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return indexCallback.indexPage(pageId, title, spaceKey, html);
            }

            @Override
            protected void done() {
                try {
                    int chunks = get();
                    if (chunks > 0) {
                        statusLabel.setText("✅ Indexiert: " + title + " (" + chunks + " Chunks)");
                    } else if (chunks == 0) {
                        statusLabel.setText("⚠️ Kein Text extrahiert: " + title);
                    } else {
                        statusLabel.setText("❌ Indexierung fehlgeschlagen: " + title);
                    }
                } catch (Exception ex) {
                    statusLabel.setText("❌ Fehler: " + ex.getMessage());
                    LOG.log(Level.WARNING, "[Confluence] Index failed for: " + title, ex);
                }
            }
        }.execute();
    }

    private void handleLink(HyperlinkEvent e) {
        String desc = e.getDescription();
        if (desc != null && desc.startsWith("#")) {
            htmlPane.scrollToReference(desc.substring(1));
            return;
        }

        String url = e.getURL() != null ? e.getURL().toString() : desc;
        if (url == null) return;

        try {
            Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception ex) {
            LOG.log(Level.FINE, "[Confluence] Could not open link", ex);
        }
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

    /**
     * Open the selected page as a ConfluenceReaderTab.
     * If the current preview matches, reuse it; otherwise load fresh from REST.
     * Falls back to opening in external browser if no OpenCallback is set.
     */
    private void openSelectedAsReaderTab() {
        int viewRow = pageTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = pageTable.convertRowIndexToModel(viewRow);
        PageItem item = pageModel.getItemAt(modelRow);
        if (item == null) return;

        // No callback → fallback to browser
        if (openCallback == null) {
            openSelectedInBrowser();
            return;
        }

        // 1) Current preview matches → reuse
        if (currentPreviewItem != null && item.id.equals(currentPreviewItem.id)
                && currentHtmlBody != null) {
            OutlineNode outline = parseOutlineSimple(currentHtmlBody, item.title);
            openCallback.openConfluencePage(client, baseUrl, item.id, item.title,
                    currentHtmlBody, outline);
            return;
        }

        // 2) Load from REST in background
        statusLabel.setText("⏳ Öffne: " + item.title + "…");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                String json = client.getContentByIdJson(item.id);
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                if (root.has("body") && root.getAsJsonObject("body").has("view")) {
                    return root.getAsJsonObject("body").getAsJsonObject("view")
                            .get("value").getAsString();
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    String html = get();
                    if (html != null) {
                        OutlineNode outline = parseOutlineSimple(html, item.title);
                        openCallback.openConfluencePage(client, baseUrl, item.id, item.title,
                                html, outline);
                        statusLabel.setText("✅ Geöffnet: " + item.title);
                    } else {
                        statusLabel.setText("❌ Kein Inhalt verfügbar");
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Confluence] Reader-Tab öffnen fehlgeschlagen", e);
                    statusLabel.setText("❌ " + e.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Open a Confluence page by its ID as a reader tab.
     * Called from {@link ConfluenceReaderTab} link callback.
     */
    public void openPageByIdAsReaderTab(String pageId) {
        if (openCallback == null) return;
        statusLabel.setText("⏳ Lade Seite " + pageId + "…");
        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() {
                String json = client.getContentByIdJson(pageId);
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                String title = root.has("title") ? root.get("title").getAsString() : "Seite " + pageId;
                String html = "";
                if (root.has("body") && root.getAsJsonObject("body").has("view")) {
                    html = root.getAsJsonObject("body").getAsJsonObject("view")
                            .get("value").getAsString();
                }
                return new String[]{title, html};
            }

            @Override
            protected void done() {
                try {
                    String[] result = get();
                    if (result[1] != null && !result[1].isEmpty()) {
                        OutlineNode outline = parseOutlineSimple(result[1], result[0]);
                        openCallback.openConfluencePage(client, baseUrl, pageId, result[0],
                                result[1], outline);
                        statusLabel.setText("✅ Geöffnet: " + result[0]);
                    } else {
                        statusLabel.setText("❌ Kein Inhalt verfügbar");
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Confluence] Seite laden fehlgeschlagen", e);
                    statusLabel.setText("❌ " + e.getMessage());
                }
            }
        }.execute();
    }

    // ═══════════════════════════════════════════════════════════
    //  Async image loading for preview pane
    // ═══════════════════════════════════════════════════════════

    /** Pattern to find img src attributes in HTML. */
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile(
            "<img\\s[^>]*src\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    /**
     * Scans HTML for img tags and loads each image via the ConfluenceRestClient
     * in background, then injects them into the JEditorPane's image cache.
     */
    @SuppressWarnings("unchecked")
    private void loadPreviewImagesAsync(String html) {
        if (html == null || client == null) return;

        // Build the full HTML that was set on the pane (same as in applyPreview)
        final String previewFullHtml = "<html><head><base href=\"" + escHtml(baseUrl) + "\">"
                + "<style>body{font-family:sans-serif;font-size:13px;padding:8px;}"
                + "table{border-collapse:collapse;} td,th{border:1px solid #ccc;padding:4px;}"
                + "img{max-width:100%;} h1,h2,h3{color:#333;} a{color:#0645ad;}"
                + "pre,code{background:#f4f4f4;padding:4px;}</style></head><body>"
                + injectHeadingAnchors(html) + "</body></html>";

        new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() {
                Matcher matcher = IMG_SRC_PATTERN.matcher(html);
                while (matcher.find()) {
                    String src = matcher.group(1);
                    try {
                        String downloadPath = resolveImagePath(src);
                        if (downloadPath == null) continue;

                        byte[] data = client.getBytes(downloadPath);
                        if (data == null || data.length == 0) continue;

                        java.awt.Image image = javax.imageio.ImageIO.read(
                                new java.io.ByteArrayInputStream(data));
                        if (image != null) {
                            java.net.URL imageUrl = resolveImageUrl(src);
                            if (imageUrl != null) {
                                publish(new Object[]{imageUrl, image});
                            }
                        }
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "[Confluence] Preview image load failed: " + src, e);
                    }
                }
                return null;
            }

            @Override
            protected void process(java.util.List<Object[]> chunks) {
                javax.swing.text.Document doc = htmlPane.getDocument();
                if (!(doc instanceof javax.swing.text.html.HTMLDocument)) return;

                java.util.Dictionary<java.net.URL, java.awt.Image> cache =
                        (java.util.Dictionary<java.net.URL, java.awt.Image>)
                                doc.getProperty("imageCache");
                if (cache == null) {
                    cache = new java.util.Hashtable<java.net.URL, java.awt.Image>();
                    doc.putProperty("imageCache", cache);
                }

                for (Object[] pair : chunks) {
                    java.net.URL url = (java.net.URL) pair[0];
                    java.awt.Image img = (java.awt.Image) pair[1];
                    cache.put(url, img);
                }
            }

            @Override
            protected void done() {
                // Re-set HTML to force ImageViews to pick up the cached images
                // (ImageView caches "load failed" state and won't retry on repaint alone)
                try {
                    htmlPane.setText(previewFullHtml);
                    htmlPane.setCaretPosition(0);
                } catch (Exception e) {
                    LOG.log(Level.FINE, "[Confluence] Failed to refresh preview after image load", e);
                }
            }
        }.execute();
    }

    /** Resolve an img src to a relative REST path for the ConfluenceRestClient. */
    private String resolveImagePath(String src) {
        if (src == null || src.isEmpty()) return null;
        if (src.startsWith(baseUrl)) return src.substring(baseUrl.length());
        if (src.startsWith("/")) return src;
        if (src.startsWith("data:") || src.startsWith("http://") || src.startsWith("https://")) return null;
        return "/" + src;
    }

    /** Resolve the img src to a full URL (as the HTMLDocument would key it). */
    private java.net.URL resolveImageUrl(String src) {
        try {
            if (src.startsWith("http://") || src.startsWith("https://")) return new java.net.URL(src);
            if (src.startsWith("/")) return new java.net.URL(baseUrl + src);
            return new java.net.URL(baseUrl + "/" + src);
        } catch (java.net.MalformedURLException e) {
            return null;
        }
    }

    /** @return keys of all checked space checkboxes. */
    private List<String> getCheckedSpaceKeys() {
        List<String> keys = new ArrayList<String>();
        for (JCheckBox cb : spaceCheckboxes) {
            if (cb.isSelected()) {
                SpaceItem sp = (SpaceItem) cb.getClientProperty("spaceItem");
                if (sp != null) keys.add(sp.key);
            }
        }
        return keys;
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
    //  Outline parsing from HTML
    // ═══════════════════════════════════════════════════════════

    /** Regex to find h1–h6 tags with optional id/name attributes. */
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "<(h([1-6]))(?:\\s[^>]*)?>(.+?)</\\1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Extract anchor from id or name attribute. */
    private static final Pattern ANCHOR_PATTERN = Pattern.compile(
            "(?:id|name)\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    /**
     * Parse HTML headings into an {@link OutlineNode} tree.
     */
    static OutlineNode parseOutlineFromHtml(String html, String pageTitle) {
        if (html == null || html.isEmpty()) {
            return new OutlineNode(pageTitle, "", Collections.<OutlineNode>emptyList());
        }

        List<int[]> levels = new ArrayList<int[]>(); // [level, index in flatNodes]
        List<OutlineNode> flatNodes = new ArrayList<OutlineNode>();

        Matcher matcher = HEADING_PATTERN.matcher(html);
        while (matcher.find()) {
            int level = Integer.parseInt(matcher.group(2));
            String fullTag = matcher.group(0);
            String text = stripHtmlTags(matcher.group(3)).trim();
            if (text.isEmpty()) continue;

            // Try to extract an anchor
            String anchor = "";
            Matcher anchorMatcher = ANCHOR_PATTERN.matcher(fullTag);
            if (anchorMatcher.find()) {
                anchor = anchorMatcher.group(1);
            } else {
                // Generate anchor from text
                anchor = text.replaceAll("[^\\w]+", "-").toLowerCase();
            }

            flatNodes.add(new OutlineNode(text, anchor, null)); // children set later
            levels.add(new int[]{level, flatNodes.size() - 1});
        }

        if (flatNodes.isEmpty()) {
            return new OutlineNode(pageTitle, "", Collections.<OutlineNode>emptyList());
        }

        // Build tree from flat heading list
        return buildOutlineTree(pageTitle, levels, flatNodes);
    }

    /** Build a hierarchical outline from a flat heading list. */
    private static OutlineNode buildOutlineTree(String pageTitle, List<int[]> levels,
                                                List<OutlineNode> flatNodes) {
        // Stack-based tree building
        List<OutlineNode> rootChildren = new ArrayList<OutlineNode>();
        // stack: [node, level]
        List<Object[]> stack = new ArrayList<Object[]>();

        for (int i = 0; i < levels.size(); i++) {
            int level = levels.get(i)[0];
            OutlineNode node = flatNodes.get(i);

            // Pop stack until we find a parent with lower level
            while (!stack.isEmpty()) {
                int parentLevel = (Integer) stack.get(stack.size() - 1)[1];
                if (parentLevel >= level) {
                    stack.remove(stack.size() - 1);
                } else {
                    break;
                }
            }

            // Wrap node with mutable children list
            OutlineNode withChildren = new OutlineNode(node.text(), node.anchor(),
                    new ArrayList<OutlineNode>());

            if (stack.isEmpty()) {
                rootChildren.add(withChildren);
            } else {
                OutlineNode parent = (OutlineNode) stack.get(stack.size() - 1)[0];
                // OutlineNode children are unmodifiable → we need to rebuild
                // We use a workaround: collect and rebuild at the end
                ((List<OutlineNode>) stack.get(stack.size() - 1)[2]).add(withChildren);
            }

            List<OutlineNode> childCollector = new ArrayList<OutlineNode>();
            stack.add(new Object[]{withChildren, level, childCollector});
        }

        // Rebuild tree bottom-up with proper children
        return new OutlineNode(pageTitle, "",
                rebuildChildren(rootChildren, levels, flatNodes));
    }

    /**
     * Simpler outline tree building: iterative approach.
     */
    static OutlineNode parseOutlineSimple(String html, String pageTitle) {
        if (html == null || html.isEmpty()) {
            return new OutlineNode(pageTitle, "", Collections.<OutlineNode>emptyList());
        }

        // Collect headings
        List<HeadingEntry> headings = new ArrayList<HeadingEntry>();
        Matcher matcher = HEADING_PATTERN.matcher(html);
        while (matcher.find()) {
            int level = Integer.parseInt(matcher.group(2));
            String fullTag = matcher.group(0);
            String text = stripHtmlTags(matcher.group(3)).trim();
            if (text.isEmpty()) continue;

            String anchor = "";
            Matcher anchorMatcher = ANCHOR_PATTERN.matcher(fullTag);
            if (anchorMatcher.find()) {
                anchor = anchorMatcher.group(1);
            } else {
                anchor = text.replaceAll("[^\\w]+", "-").toLowerCase();
            }
            headings.add(new HeadingEntry(level, text, anchor));
        }

        List<OutlineNode> rootChildren = buildRecursive(headings, 0, headings.size(), 0);
        return new OutlineNode(pageTitle, "", rootChildren);
    }

    private static List<OutlineNode> buildRecursive(List<HeadingEntry> headings,
                                                    int from, int to, int parentLevel) {
        List<OutlineNode> result = new ArrayList<OutlineNode>();
        int i = from;
        while (i < to) {
            HeadingEntry h = headings.get(i);
            // Find extent of children: all following entries with level > h.level
            int j = i + 1;
            while (j < to && headings.get(j).level > h.level) {
                j++;
            }
            List<OutlineNode> children = buildRecursive(headings, i + 1, j, h.level);
            result.add(new OutlineNode(h.text, h.anchor, children));
            i = j;
        }
        return result;
    }

    /** We override the initial parseOutlineFromHtml to use the simpler approach. */
    // The parseOutlineFromHtml above has a complex builder; let's use the simple one.
    // We keep both but the actual call uses parseOutlineSimple.

    private static List<OutlineNode> rebuildChildren(List<OutlineNode> nodes,
                                                     List<int[]> levels,
                                                     List<OutlineNode> flatNodes) {
        // Fallback: return as-is
        return nodes;
    }

    private static String stripHtmlTags(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "");
    }

    /** Pattern to match h1-h6 opening tags, capturing tag name and attributes. */
    private static final Pattern H_OPEN_PATTERN = Pattern.compile(
            "<(h[1-6])(\\s[^>]*)?>",
            Pattern.CASE_INSENSITIVE);

    /**
     * Inject {@code id} attributes into heading tags that don't have one,
     * so that {@link JEditorPane#scrollToReference(String)} can find them.
     * The generated id matches the anchor produced by {@link #parseOutlineSimple}.
     */
    static String injectHeadingAnchors(String html) {
        if (html == null) return html;
        StringBuffer sb = new StringBuffer();
        Matcher m = H_OPEN_PATTERN.matcher(html);
        while (m.find()) {
            String tag = m.group(1);      // "h2"
            String attrs = m.group(2);    // e.g. ' class="..."' or null

            // Already has an id? Keep as-is.
            if (attrs != null && Pattern.compile("\\bid\\s*=", Pattern.CASE_INSENSITIVE).matcher(attrs).find()) {
                continue; // no replacement needed
            }

            // Extract text between this opening tag and its closing tag to generate an anchor
            int closeStart = html.indexOf("</" + tag, m.end());
            if (closeStart < 0) continue;
            String innerHtml = html.substring(m.end(), closeStart);
            String text = stripHtmlTags(innerHtml).trim();
            if (text.isEmpty()) continue;

            String anchor = text.replaceAll("[^\\w]+", "-").toLowerCase();
            String replacement = "<" + tag + (attrs != null ? attrs : "") + " id=\"" + anchor + "\">";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static final class HeadingEntry {
        final int level;
        final String text;
        final String anchor;

        HeadingEntry(int level, String text, String anchor) {
            this.level = level;
            this.text = text;
            this.anchor = anchor;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Public accessors (for TabbedPaneManager integration)
    // ═══════════════════════════════════════════════════════════

    /** @return the currently previewed page item, or {@code null}. */
    public PageItem getCurrentPreviewItem() { return currentPreviewItem; }

    /** @return the currently parsed outline, or {@code null}. */
    public OutlineNode getCurrentOutline() { return currentOutline; }

    /** @return the currently previewed page title, or {@code null}. */
    public String getCurrentPageTitle() {
        return currentPreviewItem != null ? currentPreviewItem.title : null;
    }

    /** @return the base URL of this Confluence instance. */
    public String getBaseUrl() { return baseUrl; }

    /** @return the raw HTML body of the current preview (for link extraction). */
    public String getCurrentHtmlBody() { return currentHtmlBody; }

    /** @return the REST client. */
    public ConfluenceRestClient getClient() { return client; }

    /**
     * Scroll the preview pane to a heading anchor (called from RightDrawer outline).
     */
    public void scrollToAnchor(String anchor) {
        if (anchor != null) {
            htmlPane.scrollToReference(anchor);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Callbacks
    // ═══════════════════════════════════════════════════════════

    public void setOutlineCallback(OutlineCallback callback) {
        this.outlineCallback = callback;
    }

    public void setDependencyCallback(DependencyCallback callback) {
        this.dependencyCallback = callback;
    }

    public void setOpenCallback(OpenCallback callback) {
        this.openCallback = callback;
    }

    public void setPrefetchService(ConfluencePrefetchService service) {
        this.prefetchService = service;
    }

    public void setIndexCallback(IndexCallback callback) {
        this.indexCallback = callback;
        // Enable index button if a preview is already loaded
        indexBtn.setEnabled(callback != null && currentPreviewItem != null);
    }

    /** Callback to notify about outline changes (for RightDrawer). */
    public interface OutlineCallback {
        void onOutlineChanged(OutlineNode outline, String pageTitle);
    }

    /** Callback to update the dependency/relations panel (LeftDrawer) for the previewed page. */
    public interface DependencyCallback {
        void onDependenciesChanged(PageItem item);
    }

    /** Callback to open a Confluence page as a dedicated reader tab. */
    public interface OpenCallback {
        void openConfluencePage(ConfluenceRestClient client, String baseUrl,
                                String pageId, String pageTitle,
                                String htmlContent, OutlineNode outline);
    }

    /** Callback to index a Confluence page into the search index. */
    public interface IndexCallback {
        /**
         * @param pageId   Confluence page ID
         * @param title    page title
         * @param spaceKey space key
         * @param html     HTML body content
         * @return number of chunks indexed, or -1 on failure
         */
        int indexPage(String pageId, String title, String spaceKey, String html);
    }

    // ═══════════════════════════════════════════════════════════
    //  Application State persistence (space checkbox selection)
    // ═══════════════════════════════════════════════════════════

    public void setStateSaveCallback(Runnable callback) {
        this.stateSaveCallback = callback;
    }

    /** Write current checkbox states into the applicationState map. */
    public void addApplicationState(Map<String, String> state) {
        if (state == null) return;
        for (JCheckBox cb : spaceCheckboxes) {
            SpaceItem sp = (SpaceItem) cb.getClientProperty("spaceItem");
            if (sp != null) {
                state.put("confluence.space.checked." + sp.key, String.valueOf(cb.isSelected()));
            }
        }
    }

    /** Restore checkbox states from the applicationState map. */
    public void restoreApplicationState(Map<String, String> state) {
        if (state == null) return;
        for (JCheckBox cb : spaceCheckboxes) {
            SpaceItem sp = (SpaceItem) cb.getClientProperty("spaceItem");
            if (sp != null) {
                String val = state.get("confluence.space.checked." + sp.key);
                if (val != null) {
                    cb.setSelected(Boolean.parseBoolean(val));
                }
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
    public void onClose() {
        if (prefetchService != null) {
            prefetchService.shutdown();
        }
    }

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
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ═══════════════════════════════════════════════════════════
    //  Inner model classes
    // ═══════════════════════════════════════════════════════════

    private static final class SpaceItem {
        final String key;
        final String name;
        final String homepageId;

        SpaceItem(String key, String name, String homepageId) {
            this.key = key;
            this.name = name;
            this.homepageId = homepageId;
        }

        @Override
        public String toString() { return key + " — " + name; }
    }

    public static final class PageItem {
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

