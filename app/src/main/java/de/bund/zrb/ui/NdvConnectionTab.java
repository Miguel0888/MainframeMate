package de.bund.zrb.ui;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ndv.NdvService;
import de.bund.zrb.ndv.NdvException;
import de.bund.zrb.ndv.NdvObjectInfo;
import de.bund.zrb.indexing.model.SourceType;
import de.bund.zrb.indexing.ui.IndexingSidebar;
import de.bund.zrb.ndv.core.api.ObjectKind;
import de.bund.zrb.ndv.core.api.ObjectType;
import de.bund.zrb.service.NdvSourceCacheService;
import de.zrb.bund.api.Bookmarkable;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * NDV (Natural Development Server) connection tab.
 * Allows browsing Natural libraries and objects via the NATSPOD protocol,
 * and opening source code in editor/preview tabs.
 *
 * Navigation levels:
 * 1. Libraries (root) - list of Natural libraries
 * 2. Objects - list of Natural objects in a library
 */
public class NdvConnectionTab implements ConnectionTab {

    private static final int MOUSE_BACK_BUTTON = 4;
    private static final int MOUSE_FORWARD_BUTTON = 5;

    /** Sort modes for object list (Natural Navigator style). */
    enum SortMode {
        NAME_ASC("Name \u2191"), NAME_DESC("Name \u2193"), TYPE("Typ"),
        DATE_DESC("Datum \u2193"), SIZE_DESC("Gr\u00f6\u00dfe \u2193");
        final String label;
        SortMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    /** Logical display order for type groups (matches NaturalONE Natural Navigator). */
    private static final int[] TYPE_GROUP_ORDER = {
        ObjectType.PROGRAM, ObjectType.SUBPROGRAM, ObjectType.SUBROUTINE,
        ObjectType.HELPROUTINE, ObjectType.FUNCTION, ObjectType.COPYCODE,
        ObjectType.LDA, ObjectType.GDA, ObjectType.PDA,
        ObjectType.MAP, ObjectType.DDM, ObjectType.CLASS,
        ObjectType.DIALOG, ObjectType.TEXT, ObjectType.NCP,
        ObjectType.ADAPTVIEW, ObjectType.ADAPTER, ObjectType.ERRMSG, ObjectType.RESOURCE
    };

    private final TabbedPaneManager tabbedPaneManager;
    private final NdvService service;
    private final String host;
    private final String user;

    // UI components
    private final JPanel mainPanel;
    private final JTextField pathField = new JTextField();
    private DefaultListModel<Object> listModel = new DefaultListModel<Object>();
    private final JList<Object> fileList;
    private final JTextField searchField = new JTextField();
    private final JLabel overlayLabel = new JLabel();
    private final JPanel listContainer;
    private final JLabel statusLabel = new JLabel(" ");
    private JButton backButton;
    private JButton forwardButton;
    private IndexingSidebar indexingSidebar;
    private boolean sidebarVisible = false;

    // --- Natural Navigator view state ---
    private boolean groupByType = false;
    private SortMode sortMode = SortMode.NAME_ASC;
    private final Set<Integer> hiddenTypes = new HashSet<Integer>();
    private boolean showDetails = false;
    private boolean linkWithEditor = false;

    // Grouped tree view
    private JTree objectTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode treeRoot;
    private CardLayout viewCardLayout;
    private JPanel viewContainer;
    private JToggleButton groupToggle;

    // Navigation state
    private enum BrowseLevel { LIBRARIES, OBJECTS }
    private BrowseLevel currentLevel = BrowseLevel.LIBRARIES;
    private String currentLibrary = null;

    // Full data for filtering
    private List<Object> allItems = new ArrayList<Object>();

    // History
    private final List<String> history = new ArrayList<String>();
    private int historyIndex = -1;

    // NDV source cache service (H2 + Lucene indexing for SearchEverywhere)
    private final NdvSourceCacheService cacheService;

    public NdvConnectionTab(TabbedPaneManager tabbedPaneManager, NdvService service) {
        this(tabbedPaneManager, service, true);
    }

    /**
     * @param autoLoadLibraries if false, skips initial loadLibraries() call.
     *        Use false when navigateToLibrary/navigateToLibraryAndOpen will be called immediately after.
     */
    public NdvConnectionTab(TabbedPaneManager tabbedPaneManager, NdvService service, boolean autoLoadLibraries) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.service = service;
        this.host = service.getHost();
        this.user = service.getUser();

        this.mainPanel = new JPanel(new BorderLayout());
        this.fileList = new JList<Object>(listModel);
        this.listContainer = new JPanel();

        // Initialize NDV source cache service
        this.cacheService = NdvSourceCacheService.getInstance();
        this.cacheService.setNdvService(service);

        fileList.setCellRenderer(new NdvCellRenderer());

        restoreNavigatorState();
        initUI();
        if (autoLoadLibraries) {
            loadLibraries();
        }
    }


    private void initUI() {
        // Path panel
        JPanel pathPanel = new JPanel(new BorderLayout());

        JButton refreshButton = new JButton("🔄");
        refreshButton.setToolTipText("Aktualisieren");
        refreshButton.addActionListener(e -> refresh());

        backButton = new JButton("⏴");
        backButton.setToolTipText("Zurück");
        backButton.setMargin(new Insets(0, 0, 0, 0));
        backButton.setFont(backButton.getFont().deriveFont(Font.PLAIN, 20f));
        backButton.addActionListener(e -> navigateBack());

        forwardButton = new JButton("⏵");
        forwardButton.setToolTipText("Vorwärts");
        forwardButton.setMargin(new Insets(0, 0, 0, 0));
        forwardButton.setFont(forwardButton.getFont().deriveFont(Font.PLAIN, 20f));
        forwardButton.addActionListener(e -> navigateForward());

        JButton goButton = new JButton("Öffnen");
        goButton.addActionListener(e -> navigateToPath());

        pathField.addActionListener(e -> navigateToPath());

        JPanel rightButtons = new JPanel(new GridLayout(1, 5, 0, 0));
        rightButtons.add(backButton);
        rightButtons.add(forwardButton);
        rightButtons.add(goButton);

        JButton clearCacheButton = new JButton("🗑");
        clearCacheButton.setToolTipText("NDV-Cache für diese Bibliothek leeren");
        clearCacheButton.setMargin(new Insets(0, 0, 0, 0));
        clearCacheButton.setFont(clearCacheButton.getFont().deriveFont(Font.PLAIN, 16f));
        clearCacheButton.addActionListener(e -> clearLibraryCache());
        rightButtons.add(clearCacheButton);

        JToggleButton detailsButton = new JToggleButton("📊");
        detailsButton.setToolTipText("Indexierungs-Details anzeigen");
        detailsButton.setMargin(new Insets(0, 0, 0, 0));
        detailsButton.setFont(detailsButton.getFont().deriveFont(Font.PLAIN, 16f));
        detailsButton.addActionListener(e -> toggleSidebar());
        rightButtons.add(detailsButton);

        pathPanel.add(refreshButton, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(rightButtons, BorderLayout.EAST);

        // Overlay setup
        overlayLabel.setHorizontalAlignment(SwingConstants.CENTER);
        overlayLabel.setVerticalAlignment(SwingConstants.CENTER);
        overlayLabel.setFont(overlayLabel.getFont().deriveFont(Font.BOLD, 14f));
        overlayLabel.setOpaque(true);
        overlayLabel.setVisible(false);

        // List setup (flat view)
        JScrollPane listScrollPane = new JScrollPane(fileList);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick();
                }
            }
        });

        // Tree setup (grouped view — Natural Navigator style)
        treeRoot = new DefaultMutableTreeNode("root");
        treeModel = new DefaultTreeModel(treeRoot);
        objectTree = new JTree(treeModel);
        objectTree.setRootVisible(false);
        objectTree.setShowsRootHandles(true);
        objectTree.setCellRenderer(new NdvTreeCellRenderer());
        objectTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleTreeDoubleClick();
                }
            }
        });
        JScrollPane treeScrollPane = new JScrollPane(objectTree);

        // Card layout to switch between flat list and grouped tree
        viewCardLayout = new CardLayout();
        viewContainer = new JPanel(viewCardLayout);
        viewContainer.add(listScrollPane, "list");
        viewContainer.add(treeScrollPane, "tree");

        // Overlay wraps the view container (loading messages overlay both views)
        listContainer.setLayout(new OverlayLayout(listContainer));
        listContainer.add(overlayLabel);
        listContainer.add(viewContainer);

        // Mouse navigation on all relevant components
        installMouseNavigation(pathField);
        installMouseNavigation(fileList);
        installMouseNavigation(objectTree);
        installMouseNavigation(mainPanel);

        // Keyboard navigation: Enter, Left/Right arrows, circular Up/Down
        de.bund.zrb.ui.util.ListKeyboardNavigation.install(
                fileList, searchField,
                this::handleDoubleClick,
                this::navigateBack,
                this::navigateForward
        );

        // Navigator toolbar (Natural Navigator style — grouping, sorting, type filter)
        JPanel navigatorToolbar = createNavigatorToolbar();

        // Status bar
        JPanel statusBar = createStatusBar();

        // Top panel: path bar + navigator toolbar
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        pathPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, pathPanel.getPreferredSize().height));
        navigatorToolbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, navigatorToolbar.getPreferredSize().height));
        topPanel.add(pathPanel);
        topPanel.add(navigatorToolbar);

        // Main layout
        indexingSidebar = new IndexingSidebar(SourceType.NDV);
        indexingSidebar.setVisible(false);
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(listContainer, BorderLayout.CENTER);
        mainPanel.add(indexingSidebar, BorderLayout.EAST);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        updateNavigationButtons();
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());

        JPanel filterPanel = new JPanel(new BorderLayout());
        filterPanel.add(new JLabel("🔎 "), BorderLayout.WEST);
        filterPanel.add(searchField, BorderLayout.CENTER);

        searchField.setToolTipText("Filter");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        statusLabel.setForeground(Color.GRAY);

        statusBar.add(filterPanel, BorderLayout.CENTER);
        statusBar.add(statusLabel, BorderLayout.EAST);

        return statusBar;
    }

    // ==================== Navigation ====================

    private void navigateToPath() {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            loadLibraries();
            addToHistory("");
        } else {
            openLibrary(path);
            addToHistory(path);
        }
        tabbedPaneManager.refreshStarForTab(this);
    }

    private void navigateBack() {
        if (historyIndex > 0) {
            historyIndex--;
            String path = history.get(historyIndex);
            if (path.isEmpty()) {
                loadLibraries();
            } else {
                openLibraryWithoutHistory(path);
            }
            updateNavigationButtons();
        } else if (currentLevel == BrowseLevel.OBJECTS) {
            loadLibraries();
            addToHistory("");
        }
        tabbedPaneManager.refreshStarForTab(this);
    }

    private void navigateForward() {
        if (historyIndex < history.size() - 1) {
            historyIndex++;
            String path = history.get(historyIndex);
            if (path.isEmpty()) {
                loadLibraries();
            } else {
                openLibraryWithoutHistory(path);
            }
            updateNavigationButtons();
        }
        tabbedPaneManager.refreshStarForTab(this);
    }

    private void addToHistory(String path) {
        // Remove forward history
        while (history.size() > historyIndex + 1) {
            history.remove(history.size() - 1);
        }
        history.add(path);
        historyIndex = history.size() - 1;
        updateNavigationButtons();
    }

    private void updateNavigationButtons() {
        backButton.setEnabled(historyIndex > 0 || currentLevel == BrowseLevel.OBJECTS);
        forwardButton.setEnabled(historyIndex < history.size() - 1);
    }

    private void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        indexingSidebar.setVisible(sidebarVisible);
        if (sidebarVisible) {
            indexingSidebar.setCurrentPath(pathField.getText());
        }
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private void installMouseNavigation(JComponent component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MOUSE_BACK_BUTTON) {
                    navigateBack();
                } else if (e.getButton() == MOUSE_FORWARD_BUTTON) {
                    navigateForward();
                }
            }
        });
    }

    // ==================== Loading ====================

    private void loadLibraries() {
        currentLevel = BrowseLevel.LIBRARIES;
        currentLibrary = null;
        pathField.setText("");
        tabbedPaneManager.refreshStarForTab(this);
        showOverlayMessage("Lade Bibliotheken...", Color.GRAY);
        statusLabel.setText("Laden...");

        SwingWorker<List<String>, Void> worker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return service.listLibraries("*");
            }

            @Override
            protected void done() {
                try {
                    List<String> libs = get();
                    allItems.clear();
                    allItems.addAll(libs);
                    applyFilter();
                    hideOverlay();
                    statusLabel.setText(libs.size() + " Bibliotheken");
                    if (libs.isEmpty()) {
                        showOverlayMessage("Keine Bibliotheken gefunden", new Color(200, 100, 0));
                    }
                } catch (Exception e) {
                    showOverlayMessage("Fehler: " + e.getMessage(), Color.RED);
                    statusLabel.setText("Fehler");
                    System.err.println("[NdvConnectionTab] Error loading libraries: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * Navigate to a specific library (public entry point for bookmarks).
     */
    public void navigateToLibrary(String library) {
        pendingObjectName = null;
        openLibrary(library);
    }

    /**
     * Navigate to a library and then automatically open a specific object by name.
     * Used by NDV bookmarks that reference a specific file.
     */
    public void navigateToLibraryAndOpen(String library, String objectName) {
        pendingObjectName = objectName;
        openLibrary(library);
    }

    /** Object to auto-open after library loading completes (set by navigateToLibraryAndOpen). */
    private volatile String pendingObjectName;

    private void openLibrary(String library) {
        openLibraryInternal(library);
        addToHistory(library);
    }

    private void openLibraryWithoutHistory(String library) {
        openLibraryInternal(library);
    }

    private void openLibraryInternal(String library) {
        currentLevel = BrowseLevel.OBJECTS;
        currentLibrary = library.toUpperCase();
        pathField.setText(currentLibrary);
        showOverlayMessage("Lade Objekte aus " + currentLibrary + "...", Color.GRAY);
        statusLabel.setText("Laden...");
        allItems.clear();

        SwingWorker<Void, List<NdvObjectInfo>> worker = new SwingWorker<Void, List<NdvObjectInfo>>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Logon to the library first
                try {
                    service.logon(currentLibrary);
                } catch (NdvException e) {
                    System.err.println("[NdvConnectionTab] Logon warning (may continue): " + e.getMessage());
                }
                service.listObjectsProgressive(currentLibrary, "*",
                        ObjectKind.SOURCE, 0, new de.bund.zrb.ndv.NdvClient.PageCallback() {
                            @Override
                            public boolean onPage(List<NdvObjectInfo> pageItems, int totalSoFar) {
                                publish(pageItems);
                                return !isCancelled();
                            }
                        });
                return null;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void process(List<List<NdvObjectInfo>> chunks) {
                for (List<NdvObjectInfo> page : chunks) {
                    allItems.addAll(page);
                }
                applyFilter();
                hideOverlay();
                statusLabel.setText(allItems.size() + " Objekte in " + currentLibrary + " (laden...)");
            }

            @Override
            protected void done() {
                try {
                    get(); // rethrow exceptions
                    applyFilter();
                    hideOverlay();
                    statusLabel.setText(allItems.size() + " Objekte in " + currentLibrary);
                    if (allItems.isEmpty()) {
                        showOverlayMessage("Keine Objekte in " + currentLibrary, new Color(200, 100, 0));
                    }
                    // Auto-open pending object from bookmark
                    autoOpenPendingObject();

                    // Build dependency graph in background (if not already cached)
                    triggerDependencyGraphBuild(currentLibrary);

                    // Prefetch all sources for Lucene indexing (SearchEverywhere + RAG)
                    triggerSourcePrefetch(currentLibrary);
                } catch (Exception e) {
                    if (!allItems.isEmpty()) {
                        // Partial results available
                        applyFilter();
                        hideOverlay();
                        statusLabel.setText(allItems.size() + " Objekte (unvollständig)");
                    } else {
                        showOverlayMessage("Fehler: " + e.getMessage(), Color.RED);
                        statusLabel.setText("Fehler");
                    }
                    System.err.println("[NdvConnectionTab] Error loading objects: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * If a pending object name was set (e.g. from a bookmark), find it in loaded items and open it.
     */
    private void autoOpenPendingObject() {
        String name = pendingObjectName;
        pendingObjectName = null;
        if (name == null || name.isEmpty()) return;

        // Strip extension if present (bookmarks may store "NAME.NSP" or just "NAME")
        String baseName = name;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            baseName = name.substring(0, dot);
        }
        final String searchName = baseName.toUpperCase();

        for (Object item : allItems) {
            if (item instanceof NdvObjectInfo) {
                NdvObjectInfo obj = (NdvObjectInfo) item;
                if (obj.getName().equalsIgnoreCase(searchName)) {
                    de.bund.zrb.util.AppLogger.get(de.bund.zrb.util.AppLogger.NDV).fine("[NdvConnectionTab] Auto-opening bookmarked object: " + obj.getName());
                    openSource(obj);
                    return;
                }
            }
        }
        System.err.println("[NdvConnectionTab] Bookmarked object not found: " + name);
    }

    private void refresh() {
        pendingObjectName = null; // clear on manual refresh
        if (currentLevel == BrowseLevel.LIBRARIES) {
            loadLibraries();
        } else if (currentLibrary != null) {
            openLibraryInternal(currentLibrary);
        }
    }

    /**
     * Build the dependency graph for the current library in a background thread.
     * Checks Lucene cache first — if a recent graph exists, skips the expensive
     * server download. Stores the result in both in-memory and Lucene caches.
     */
    private void triggerDependencyGraphBuild(final String library) {
        if (library == null || library.isEmpty()) return;

        // Check if already in-memory
        de.bund.zrb.service.NaturalDependencyGraph existing =
                tabbedPaneManager.getDependencyGraph(library);
        if (existing != null && existing.isBuilt()) {
            System.out.println("[NdvConnectionTab] Graph already in memory for: " + library);
            return;
        }

        // Check Lucene cache (fast — no server access needed)
        final de.bund.zrb.service.LuceneDependencyIndex depIndex =
                de.bund.zrb.service.LuceneDependencyIndex.getInstance();
        if (depIndex.hasLibrary(library)) {
            long buildTime = depIndex.getLibraryBuildTime(library);
            long ageHours = (System.currentTimeMillis() - buildTime) / (1000 * 60 * 60);
            if (ageHours < 24) {
                // Cache is recent enough, restore from Lucene in background
                new SwingWorker<de.bund.zrb.service.NaturalDependencyGraph, Void>() {
                    @Override
                    protected de.bund.zrb.service.NaturalDependencyGraph doInBackground() {
                        return depIndex.restoreGraph(library);
                    }

                    @Override
                    protected void done() {
                        try {
                            de.bund.zrb.service.NaturalDependencyGraph graph = get();
                            if (graph != null) {
                                // Register in TabbedPaneManager's memory cache
                                tabbedPaneManager.registerDependencyGraph(library, graph);
                                statusLabel.setText(statusLabel.getText()
                                        + "  |  🔗 Graph aus Cache geladen");
                            }
                        } catch (Exception e) {
                            System.err.println("[NdvConnectionTab] Cache restore failed: " + e.getMessage());
                        }
                    }
                }.execute();
                return;
            }
        }

        // Build fresh from server
        new SwingWorker<de.bund.zrb.service.NaturalDependencyGraph, String>() {
            @Override
            protected de.bund.zrb.service.NaturalDependencyGraph doInBackground() throws Exception {
                de.bund.zrb.service.NaturalDependencyGraphBuilder builder =
                        new de.bund.zrb.service.NaturalDependencyGraphBuilder(service);
                return builder.buildForLibrary(library,
                        new de.bund.zrb.service.NaturalDependencyGraphBuilder.ProgressCallback() {
                            @Override
                            public void onProgress(int current, int total, String objectName) {
                                publish("🔗 Graph: " + current + "/" + total + " " + objectName);
                            }

                            @Override
                            public void onComplete(de.bund.zrb.service.NaturalDependencyGraph graph) {
                                // stored via done()
                            }

                            @Override
                            public void onError(String message, Exception e) {
                                publish("⚠ Graph-Fehler: " + message);
                            }
                        });
            }

            @Override
            protected void process(java.util.List<String> messages) {
                if (!messages.isEmpty()) {
                    statusLabel.setText(messages.get(messages.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    de.bund.zrb.service.NaturalDependencyGraph graph = get();
                    if (graph != null && graph.isBuilt()) {
                        tabbedPaneManager.registerDependencyGraph(library, graph);
                        // Persist to Lucene
                        depIndex.storeGraph(graph);
                        statusLabel.setText(statusLabel.getText()
                                + "  |  🔗 " + graph.getKnownSources().size() + " Quellen analysiert");
                    }
                } catch (Exception e) {
                    System.err.println("[NdvConnectionTab] Graph build failed: " + e.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Start background prefetching of all source code in the library for Lucene indexing.
     * This enables SearchEverywhere to find Natural sources by content.
     * Skips objects that are already cached.
     */
    @SuppressWarnings("unchecked")
    private void triggerSourcePrefetch(final String library) {
        if (library == null || library.isEmpty()) return;
        if (cacheService.isPrefetching(library)) return; // already running

        // Collect NdvObjectInfo items from the loaded list
        final List<NdvObjectInfo> objects = new ArrayList<NdvObjectInfo>();
        for (Object item : allItems) {
            if (item instanceof NdvObjectInfo) {
                objects.add((NdvObjectInfo) item);
            }
        }

        if (objects.isEmpty()) return;

        cacheService.prefetchLibrary(library, objects, new NdvSourceCacheService.PrefetchCallback() {
            @Override
            public void onProgress(final int current, final int total, final String objectName) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("📥 Indizierung: " + current + "/" + total + " " + objectName);
                    }
                });
            }

            @Override
            public void onComplete(final int total, final int indexed) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText(allItems.size() + " Objekte in " + library
                                + "  |  📥 " + indexed + " Quellen indiziert");
                    }
                });
            }

            @Override
            public void onError(final String message) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("⚠ Indizierung: " + message);
                    }
                });
            }
        });
    }

    /**
     * Clear all cached NDV sources + Lucene index + dependency graph for the current library.
     * Triggered by the "Cache leeren" toolbar button.
     */
    private void clearLibraryCache() {
        if (currentLibrary == null) {
            statusLabel.setText("Keine Bibliothek ausgewählt");
            return;
        }

        int result = JOptionPane.showConfirmDialog(mainPanel,
                "Cache für Bibliothek '" + currentLibrary + "' leeren?\n\n"
                        + "Dies entfernt:\n"
                        + "• Zwischengespeicherte Quellcodes\n"
                        + "• Lucene-Suchindex\n"
                        + "• Dependency-Graph\n\n"
                        + "Die Daten werden beim nächsten Öffnen neu geladen.",
                "Cache leeren", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            cacheService.invalidateLibrary(currentLibrary);
            // Remove in-memory dependency graph
            tabbedPaneManager.removeDependencyGraph(currentLibrary);
            statusLabel.setText("Cache für " + currentLibrary + " geleert");
        }
    }

    // ==================== Natural Navigator Toolbar ====================

    /**
     * Create the Natural Navigator-style toolbar with grouping, sorting, and filter options.
     * Mirrors the NaturalONE "Natural Navigator" view menu options.
     */
    private JPanel createNavigatorToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        toolbar.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));

        // Group by Type toggle (main feature: "Toggles between grouping by object type and physical representation")
        groupToggle = new JToggleButton("\uD83D\uDCC1");
        groupToggle.setToolTipText("Nach Objekttyp gruppieren (Natural Navigator)");
        groupToggle.setMargin(new Insets(1, 4, 1, 4));
        groupToggle.setFocusable(false);
        groupToggle.setSelected(groupByType);
        groupToggle.addActionListener(e -> {
            groupByType = groupToggle.isSelected();
            saveNavigatorState();
            applyFilter();
        });
        toolbar.add(groupToggle);

        // Sort dropdown button
        JButton sortButton = new JButton("\u2195");
        sortButton.setToolTipText("Sortierung");
        sortButton.setMargin(new Insets(1, 4, 1, 4));
        sortButton.setFocusable(false);
        sortButton.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();
            for (final SortMode mode : SortMode.values()) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(mode.label, mode == sortMode);
                item.addActionListener(ev -> {
                    sortMode = mode;
                    saveNavigatorState();
                    applyFilter();
                });
                popup.add(item);
            }
            popup.show(sortButton, 0, sortButton.getHeight());
        });
        toolbar.add(sortButton);

        // Type filter dropdown
        JButton filterButton = new JButton("\uD83D\uDD3D");
        filterButton.setToolTipText("Typfilter \u2014 Objekttypen ein-/ausblenden");
        filterButton.setMargin(new Insets(1, 4, 1, 4));
        filterButton.setFocusable(false);
        filterButton.addActionListener(e -> {
            JPopupMenu popup = createTypeFilterMenu();
            popup.show(filterButton, 0, filterButton.getHeight());
        });
        toolbar.add(filterButton);

        toolbar.add(Box.createHorizontalStrut(8));

        // Show Details toggle (date, user, size)
        JToggleButton detailsToggle = new JToggleButton("\uD83D\uDCCA");
        detailsToggle.setToolTipText("Details anzeigen (Datum, Benutzer, Gr\u00f6\u00dfe)");
        detailsToggle.setMargin(new Insets(1, 4, 1, 4));
        detailsToggle.setFocusable(false);
        detailsToggle.setSelected(showDetails);
        detailsToggle.addActionListener(e -> {
            showDetails = detailsToggle.isSelected();
            saveNavigatorState();
            applyFilter();
        });
        toolbar.add(detailsToggle);

        // Link with Editor toggle
        JToggleButton linkButton = new JToggleButton("\uD83D\uDD17");
        linkButton.setToolTipText("Mit Editor verkn\u00fcpfen");
        linkButton.setMargin(new Insets(1, 4, 1, 4));
        linkButton.setFocusable(false);
        linkButton.setSelected(linkWithEditor);
        linkButton.addActionListener(e -> {
            linkWithEditor = linkButton.isSelected();
            saveNavigatorState();
        });
        toolbar.add(linkButton);

        toolbar.add(Box.createHorizontalStrut(8));

        // Collapse All (useful for grouped tree)
        JButton collapseButton = new JButton("\u23F7");
        collapseButton.setToolTipText("Alle zuklappen");
        collapseButton.setMargin(new Insets(1, 4, 1, 4));
        collapseButton.setFocusable(false);
        collapseButton.addActionListener(e -> collapseAllTreeNodes());
        toolbar.add(collapseButton);

        // Expand All
        JButton expandButton = new JButton("\u23F6");
        expandButton.setToolTipText("Alle aufklappen");
        expandButton.setMargin(new Insets(1, 4, 1, 4));
        expandButton.setFocusable(false);
        expandButton.addActionListener(e -> expandAllTreeNodes());
        toolbar.add(expandButton);

        return toolbar;
    }

    /**
     * Create the type filter popup menu with checkboxes for each Natural object type.
     * Allows hiding/showing specific types (e.g. only Programs, or hide DDMs).
     */
    @SuppressWarnings("unchecked")
    private JPopupMenu createTypeFilterMenu() {
        JPopupMenu popup = new JPopupMenu();

        // "Show All" resets all filters
        JMenuItem showAllItem = new JMenuItem("Alle anzeigen");
        showAllItem.addActionListener(e -> {
            hiddenTypes.clear();
            saveNavigatorState();
            applyFilter();
        });
        popup.add(showAllItem);

        // "Invert Selection" toggles visibility of all types
        JMenuItem invertItem = new JMenuItem("Auswahl invertieren");
        invertItem.addActionListener(e -> {
            Set<Integer> allTypes = new HashSet<Integer>();
            for (int typeId : TYPE_GROUP_ORDER) {
                allTypes.add(typeId);
            }
            Set<Integer> newHidden = new HashSet<Integer>();
            for (int typeId : allTypes) {
                if (!hiddenTypes.contains(typeId)) {
                    newHidden.add(typeId);
                }
            }
            hiddenTypes.clear();
            hiddenTypes.addAll(newHidden);
            saveNavigatorState();
            applyFilter();
        });
        popup.add(invertItem);
        popup.addSeparator();

        Hashtable<Integer, String> groupNames =
                (Hashtable<Integer, String>) ObjectType.getInstanceIdGroupName();
        for (int typeId : TYPE_GROUP_ORDER) {
            String name = groupNames.get(typeId);
            if (name == null) continue;
            final int tid = typeId;
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(
                    getGroupIcon(typeId) + " " + name, !hiddenTypes.contains(typeId));
            item.addActionListener(e -> {
                if (item.isSelected()) {
                    hiddenTypes.remove(tid);
                } else {
                    hiddenTypes.add(tid);
                }
                saveNavigatorState();
                applyFilter();
            });
            popup.add(item);
        }
        return popup;
    }

    // ==================== Navigator State Persistence ====================

    private static final String STATE_PREFIX = "ndv.nav.";

    /**
     * Save all Natural Navigator settings to applicationState (persisted in settings.json).
     * Called whenever any navigator toggle/sort/filter changes.
     */
    private void saveNavigatorState() {
        Settings settings = SettingsHelper.load();
        Map<String, String> state = settings.applicationState;

        state.put(STATE_PREFIX + "groupByType", String.valueOf(groupByType));
        state.put(STATE_PREFIX + "sortMode", sortMode.name());
        state.put(STATE_PREFIX + "showDetails", String.valueOf(showDetails));
        state.put(STATE_PREFIX + "linkWithEditor", String.valueOf(linkWithEditor));

        // Store hidden types as comma-separated int list (e.g. "16,128,8")
        if (hiddenTypes.isEmpty()) {
            state.put(STATE_PREFIX + "hiddenTypes", "");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Integer typeId : hiddenTypes) {
                if (sb.length() > 0) sb.append(",");
                sb.append(typeId);
            }
            state.put(STATE_PREFIX + "hiddenTypes", sb.toString());
        }

        SettingsHelper.save(settings);
    }

    /**
     * Restore Natural Navigator settings from applicationState.
     * Called during construction, before initUI() so toggle buttons reflect saved state.
     */
    private void restoreNavigatorState() {
        Settings settings = SettingsHelper.load();
        Map<String, String> state = settings.applicationState;

        String groupVal = state.get(STATE_PREFIX + "groupByType");
        if (groupVal != null) groupByType = Boolean.parseBoolean(groupVal);

        String sortVal = state.get(STATE_PREFIX + "sortMode");
        if (sortVal != null) {
            try {
                sortMode = SortMode.valueOf(sortVal);
            } catch (IllegalArgumentException ignored) {
                // keep default
            }
        }

        String detailsVal = state.get(STATE_PREFIX + "showDetails");
        if (detailsVal != null) showDetails = Boolean.parseBoolean(detailsVal);

        String linkVal = state.get(STATE_PREFIX + "linkWithEditor");
        if (linkVal != null) linkWithEditor = Boolean.parseBoolean(linkVal);

        String hiddenVal = state.get(STATE_PREFIX + "hiddenTypes");
        if (hiddenVal != null && !hiddenVal.isEmpty()) {
            hiddenTypes.clear();
            for (String part : hiddenVal.split(",")) {
                try {
                    hiddenTypes.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {
                    // skip invalid entries
                }
            }
        }
    }

    // ==================== View Switching ====================

    /**
     * Switch the visible view between flat list and grouped tree.
     */
    private void switchView() {
        if (groupByType && currentLevel == BrowseLevel.OBJECTS) {
            viewCardLayout.show(viewContainer, "tree");
        } else {
            viewCardLayout.show(viewContainer, "list");
        }
    }

    private void collapseAllTreeNodes() {
        for (int i = objectTree.getRowCount() - 1; i >= 0; i--) {
            objectTree.collapseRow(i);
        }
    }

    private void expandAllTreeNodes() {
        for (int i = 0; i < objectTree.getRowCount(); i++) {
            objectTree.expandRow(i);
        }
    }

    /**
     * Populate the grouped tree view from filtered items.
     * Groups NdvObjectInfo items by their ObjectType, ordered by TYPE_GROUP_ORDER.
     * Empty groups are hidden. Each group shows icon + plural name + count.
     */
    @SuppressWarnings("unchecked")
    private void populateGroupedTree(List<Object> filteredItems) {
        treeRoot.removeAllChildren();

        // Group items by type in display order
        LinkedHashMap<Integer, List<NdvObjectInfo>> groups = new LinkedHashMap<Integer, List<NdvObjectInfo>>();
        for (int typeId : TYPE_GROUP_ORDER) {
            groups.put(typeId, new ArrayList<NdvObjectInfo>());
        }

        // Distribute items into groups
        for (Object item : filteredItems) {
            if (item instanceof NdvObjectInfo) {
                NdvObjectInfo obj = (NdvObjectInfo) item;
                List<NdvObjectInfo> group = groups.get(obj.getType());
                if (group == null) {
                    // Unknown type: create ad-hoc group
                    group = new ArrayList<NdvObjectInfo>();
                    groups.put(obj.getType(), group);
                }
                group.add(obj);
            }
        }

        // Build tree nodes (skip empty groups)
        Hashtable<Integer, String> groupNames =
                (Hashtable<Integer, String>) ObjectType.getInstanceIdGroupName();
        for (Map.Entry<Integer, List<NdvObjectInfo>> entry : groups.entrySet()) {
            List<NdvObjectInfo> members = entry.getValue();
            if (members.isEmpty()) continue;

            int typeId = entry.getKey();
            String groupName = groupNames.containsKey(typeId)
                    ? groupNames.get(typeId) : "Sonstige";
            String icon = getGroupIcon(typeId);

            DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(
                    new TypeGroupNode(typeId, icon + " " + groupName + " (" + members.size() + ")"));

            for (NdvObjectInfo obj : members) {
                groupNode.add(new DefaultMutableTreeNode(obj));
            }

            treeRoot.add(groupNode);
        }

        treeModel.reload();

        // Expand all groups by default for quick overview
        expandAllTreeNodes();
    }

    /**
     * Sort filtered items according to the current sort mode.
     * Only sorts NdvObjectInfo items; library names (Strings) stay in original order.
     */
    private void sortItems(List<Object> items) {
        boolean hasObjects = !items.isEmpty() && items.get(0) instanceof NdvObjectInfo;
        if (!hasObjects) return;

        Collections.sort(items, new Comparator<Object>() {
            @Override
            public int compare(Object a, Object b) {
                if (!(a instanceof NdvObjectInfo) || !(b instanceof NdvObjectInfo)) return 0;
                NdvObjectInfo oa = (NdvObjectInfo) a;
                NdvObjectInfo ob = (NdvObjectInfo) b;
                switch (sortMode) {
                    case NAME_DESC:
                        return ob.getName().compareToIgnoreCase(oa.getName());
                    case TYPE:
                        int tc = oa.getTypeName().compareToIgnoreCase(ob.getTypeName());
                        return tc != 0 ? tc : oa.getName().compareToIgnoreCase(ob.getName());
                    case DATE_DESC:
                        return ob.getSourceDate().compareTo(oa.getSourceDate());
                    case SIZE_DESC:
                        return Integer.compare(ob.getSourceSize(), oa.getSourceSize());
                    case NAME_ASC:
                    default:
                        return oa.getName().compareToIgnoreCase(ob.getName());
                }
            }
        });
    }

    /**
     * Get the display icon for a Natural object type group (matches NdvObjectInfo.getIcon()).
     */
    private static String getGroupIcon(int typeId) {
        switch (typeId) {
            case ObjectType.PROGRAM:     return "\u25B6";
            case ObjectType.SUBPROGRAM:  return "\u2699";
            case ObjectType.SUBROUTINE:  return "\uD83D\uDD27";
            case ObjectType.COPYCODE:    return "\uD83D\uDCCB";
            case ObjectType.MAP:         return "\uD83D\uDDFA";
            case ObjectType.GDA:         return "\uD83C\uDF10";
            case ObjectType.LDA:         return "\uD83D\uDCCA";
            case ObjectType.PDA:         return "\uD83D\uDCD1";
            case ObjectType.DDM:         return "\uD83D\uDDC4";
            case ObjectType.HELPROUTINE: return "\u2753";
            case ObjectType.TEXT:        return "\uD83D\uDCDD";
            case ObjectType.CLASS:       return "\uD83C\uDFDB";
            case ObjectType.FUNCTION:    return "\u0192";
            default:                     return "\uD83D\uDCC1";
        }
    }

    /**
     * Handle double-click in the grouped tree view.
     * Opens NdvObjectInfo leaf nodes; group nodes expand/collapse automatically.
     */
    private void handleTreeDoubleClick() {
        TreePath path = objectTree.getSelectionPath();
        if (path == null) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();
        if (userObject instanceof NdvObjectInfo) {
            openSource((NdvObjectInfo) userObject);
        }
        // Group nodes: JTree toggles expand/collapse automatically on double-click
    }

    /** Wrapper for type group nodes in the tree (distinguishes from NdvObjectInfo leaf nodes). */
    static class TypeGroupNode {
        final int typeId;
        final String displayText;

        TypeGroupNode(int typeId, String displayText) {
            this.typeId = typeId;
            this.displayText = displayText;
        }

        @Override
        public String toString() {
            return displayText;
        }
    }

    // ==================== Double-click handling ====================

    private void handleDoubleClick() {
        Object selected = fileList.getSelectedValue();
        if (selected == null) return;

        if (selected instanceof String) {
            // Library name -> open library
            openLibrary((String) selected);
        } else if (selected instanceof NdvObjectInfo) {
            // Object -> download and open source
            NdvObjectInfo objInfo = (NdvObjectInfo) selected;
            openSource(objInfo);
        }
    }

    private void openSource(NdvObjectInfo objInfo) {
        if (!objInfo.hasSource()) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Dieses Objekt hat keinen Quellcode (Typ: " + objInfo.getTypeName() + ")",
                    "Kein Quellcode", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        statusLabel.setText("Lade Quellcode: " + objInfo.getName() + "...");
        mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return service.readSource(currentLibrary, objInfo);
            }

            @Override
            protected void done() {
                mainPanel.setCursor(Cursor.getDefaultCursor());
                try {
                    String source = get();
                    if (source == null || source.isEmpty()) {
                        JOptionPane.showMessageDialog(mainPanel,
                                "Leerer Quellcode für " + objInfo.getName(),
                                "Kein Inhalt", JOptionPane.WARNING_MESSAGE);
                        statusLabel.setText("Leerer Quellcode");
                        return;
                    }

                    // Update cache with freshly downloaded source (invalidate-on-open)
                    cacheService.cacheSource(currentLibrary, objInfo.getName(),
                            objInfo.getTypeExtension(), source);

                    // Build a VirtualResource with NDV backend (enables save via FileTabImpl)
                    String fullPath = currentLibrary + "/" + objInfo.getName() + "." + objInfo.getTypeExtension();
                    NdvResourceState ndvState = new NdvResourceState(service, currentLibrary, objInfo);
                    VirtualResource resource = new VirtualResource(
                            de.bund.zrb.files.path.VirtualResourceRef.of(fullPath),
                            VirtualResourceKind.FILE,
                            fullPath,
                            VirtualBackendType.NDV,
                            null,  // no FTP state
                            ndvState
                    );

                    // Open in FileTabImpl (editable, saveable, with search/compare)
                    FileTabImpl fileTab = new FileTabImpl(
                            tabbedPaneManager,
                            resource,
                            source,
                            null,   // sentenceType
                            null,   // searchPattern
                            false   // toCompare
                    );

                    tabbedPaneManager.addTab(fileTab);
                    statusLabel.setText("Geöffnet: " + objInfo.getName());
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (msg == null || msg.isEmpty()) {
                        msg = e.getClass().getSimpleName();
                    }
                    if (e instanceof java.util.concurrent.ExecutionException && e.getCause() != null) {
                        Throwable cause = e.getCause();
                        msg = cause.getClass().getSimpleName();
                        if (cause.getMessage() != null) {
                            msg += ": " + cause.getMessage();
                        }
                        if (cause instanceof NullPointerException) {
                            StackTraceElement[] st = cause.getStackTrace();
                            if (st.length > 0) {
                                msg += " at " + st[0].toString();
                            }
                        }
                    }
                    JOptionPane.showMessageDialog(mainPanel,
                            "Fehler beim Laden des Quellcodes:\n" + msg,
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText("Fehler beim Laden");
                    System.err.println("[NdvConnectionTab] Error reading source: " + msg);
                    if (e.getCause() != null) {
                        e.getCause().printStackTrace();
                    } else {
                        e.printStackTrace();
                    }
                }
            }
        };
        worker.execute();
    }

    // ==================== Filter ====================

    private void applyFilter() {
        String filter = searchField.getText().trim().toLowerCase();

        // Step 1: Filter items by search text and type visibility
        List<Object> filtered = new ArrayList<Object>();
        for (Object item : allItems) {
            // Type filter (only for NdvObjectInfo at object level)
            if (item instanceof NdvObjectInfo) {
                NdvObjectInfo obj = (NdvObjectInfo) item;
                if (!hiddenTypes.isEmpty() && hiddenTypes.contains(obj.getType())) {
                    continue;
                }
            }
            // Text filter
            String text;
            if (item instanceof String) {
                text = ((String) item).toLowerCase();
            } else if (item instanceof NdvObjectInfo) {
                text = ((NdvObjectInfo) item).toString().toLowerCase();
            } else {
                text = item.toString().toLowerCase();
            }
            if (filter.isEmpty() || text.contains(filter)) {
                filtered.add(item);
            }
        }

        // Step 2: Sort
        sortItems(filtered);

        // Step 3: Populate the appropriate view
        if (groupByType && currentLevel == BrowseLevel.OBJECTS) {
            populateGroupedTree(filtered);
            viewCardLayout.show(viewContainer, "tree");
        } else {
            // Build new model off-EDT-event-storm: create a fresh model, populate, then swap
            DefaultListModel<Object> newModel = new DefaultListModel<Object>();
            for (Object item : filtered) {
                newModel.addElement(item);
            }
            // Single atomic swap – only one UI repaint
            listModel = newModel;
            fileList.setModel(listModel);
            viewCardLayout.show(viewContainer, "list");
        }
    }

    // ==================== Overlay ====================

    private void showOverlayMessage(String message, Color color) {
        overlayLabel.setText(message);
        overlayLabel.setForeground(color);
        overlayLabel.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
        overlayLabel.setVisible(true);
    }

    private void hideOverlay() {
        overlayLabel.setVisible(false);
    }

    // ==================== Cell Renderers ====================

    /**
     * List cell renderer for flat view — shows icon + name + type + optional details.
     */
    private class NdvCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                       int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof String) {
                setText("\uD83D\uDCDA " + value);
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (value instanceof NdvObjectInfo) {
                NdvObjectInfo obj = (NdvObjectInfo) value;
                if (showDetails) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(obj.getIcon()).append(" ").append(obj.getName());
                    sb.append("  [").append(obj.getTypeName()).append("]");
                    if (!obj.getUser().isEmpty()) sb.append("  (").append(obj.getUser()).append(")");
                    if (!obj.getSourceDate().isEmpty()) sb.append("  ").append(obj.getSourceDate());
                    if (obj.getSourceSize() > 0) sb.append("  ").append(obj.getSourceSize()).append("B");
                    setText(sb.toString());
                } else {
                    setText(obj.getIcon() + " " + obj.getName() + "  [" + obj.getTypeName() + "]"
                            + (obj.getUser().isEmpty() ? "" : "  (" + obj.getUser() + ")"));
                }
                setFont(getFont().deriveFont(Font.PLAIN));
            }

            return this;
        }
    }

    /**
     * Tree cell renderer for grouped view — group nodes bold with icon+count, leaf nodes with details.
     */
    private class NdvTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                       boolean sel, boolean expanded, boolean leaf,
                                                       int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof TypeGroupNode) {
                    setText(((TypeGroupNode) userObj).displayText);
                    setFont(getFont().deriveFont(Font.BOLD));
                    setIcon(null);
                } else if (userObj instanceof NdvObjectInfo) {
                    NdvObjectInfo obj = (NdvObjectInfo) userObj;
                    if (showDetails) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(obj.getIcon()).append(" ").append(obj.getName());
                        if (!obj.getUser().isEmpty()) sb.append("  (").append(obj.getUser()).append(")");
                        if (!obj.getSourceDate().isEmpty()) sb.append("  ").append(obj.getSourceDate());
                        if (obj.getSourceSize() > 0) sb.append("  ").append(obj.getSourceSize()).append("B");
                        setText(sb.toString());
                    } else {
                        setText(obj.getIcon() + " " + obj.getName());
                    }
                    setFont(getFont().deriveFont(Font.PLAIN));
                    setIcon(null);
                }
            }
            return this;
        }
    }

    // ==================== ConnectionTab interface ====================

    @Override
    public String getTitle() {
        return "🔗 NDV: " + user + "@" + host;
    }

    @Override
    public String getTooltip() {
        return "NDV: " + user + "@" + host + ":" + service.getPort()
                + (currentLibrary != null ? " - " + currentLibrary : "");
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        // Cancel any running prefetches (but don't clear the persistent cache)
        if (currentLibrary != null) {
            cacheService.cancelPrefetch(currentLibrary);
        }
        try {
            service.close();
        } catch (IOException e) {
            System.err.println("[NdvConnectionTab] Error closing: " + e.getMessage());
        }
    }

    @Override
    public void saveIfApplicable() {
        // Not applicable
    }

    @Override
    public String getContent() {
        return "";
    }

    @Override
    public void markAsChanged() {
        // Not applicable
    }

    @Override
    public String getPath() {
        // Return raw path (library name only, no protocol prefix).
        // The bookmark system adds the "ndv://" prefix via BookmarkEntry.buildPath("NDV", ...).
        if (currentLibrary != null) {
            return currentLibrary;
        }
        return "";
    }

    @Override
    public Bookmarkable.Type getType() {
        return Bookmarkable.Type.CONNECTION;
    }

    @Override
    public void focusSearchField() {
        searchField.requestFocusInWindow();
    }

    @Override
    public void searchFor(String searchPattern) {
        searchField.setText(searchPattern);
        applyFilter();
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        JMenuItem refreshItem = new JMenuItem("🔄 Aktualisieren");
        refreshItem.addActionListener(e -> refresh());

        menu.add(refreshItem);
        menu.addSeparator();
        menu.add(closeItem);

        return menu;
    }
}

