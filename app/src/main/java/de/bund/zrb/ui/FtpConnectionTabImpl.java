package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.indexing.model.SourceType;
import de.bund.zrb.indexing.ui.IndexingSidebar;
import de.bund.zrb.model.Settings;
import de.bund.zrb.security.SecurityFilterService;
import de.bund.zrb.service.FtpSourceCacheService;
import de.bund.zrb.ui.browser.BrowserSessionState;
import de.bund.zrb.ui.browser.PathNavigator;
import de.zrb.bund.newApi.ui.ConnectionTab;
import de.zrb.bund.newApi.ui.Navigable;
import de.zrb.bund.newApi.ui.SearchBarPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class FtpConnectionTabImpl implements ConnectionTab, Navigable {


    /** Sort modes for file list. */
    enum SortMode {
        NAME_ASC("Name \u2191"), NAME_DESC("Name \u2193"),
        SIZE_DESC("Größe \u2193"), DATE_DESC("Datum \u2193"), EXT("Erweiterung");
        final String label;
        SortMode(String l) { this.label = l; }
        @Override public String toString() { return label; }
    }

    private VirtualResource resource;
    private final FileService fileService;
    private final BrowserSessionState browserState;
    private final PathNavigator navigator;
    private final DocumentPreviewOpener previewOpener;

    private final JPanel mainPanel;
    private final JTextField pathField = new JTextField();
    private DefaultListModel<Object> listModel = new DefaultListModel<Object>();
    private final JList<Object> fileList;
    private final TabbedPaneManager tabbedPaneManager;
    private JButton backButton;
    private JButton forwardButton;
    private final IndexingSidebar indexingSidebar = new IndexingSidebar(SourceType.FTP);
    private boolean sidebarVisible = false;
    private JToggleButton detailsButton;

    private final SearchBarPanel searchBar = new SearchBarPanel("Regex-Filter für Dateinamen…",
            "<html>Regex-Filter für Dateinamen<br>Beispiel: <code>\\.JCL$</code> findet alle JCL-Dateien<br><i>(Groß-/Kleinschreibung wird ignoriert)</i></html>");
    private final JLabel overlayLabel = new JLabel();
    private final JPanel listContainer;
    private final JLabel statusLabel = new JLabel(" ");

    // Full data for filtering
    private List<FileNode> currentDirectoryNodes = new ArrayList<FileNode>();

    // --- Navigator view state ---
    private boolean groupByExtension = false;
    private SortMode sortMode = SortMode.NAME_ASC;
    private boolean showDetails = false;
    private boolean hideBlacklisted = false;

    // Grouped tree view
    private JTree extensionTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode treeRoot;
    private CardLayout viewCardLayout;
    private JPanel viewContainer;
    private JToggleButton groupToggle;

    // FTP source cache service
    private final FtpSourceCacheService cacheService;

    // Host info (extracted from resource for cache keys)
    private String ftpHost = "";

    // Mainframe mode (true = z/OS MVS datasets, false = normal FTP file system)
    private final boolean mvsMode;

    // State persistence prefix
    private static final String STATE_PREFIX = "ftp.nav.";

    // Listener for security filter changes → re-trigger auto-prefetch
    private final Runnable securityChangeListener = new Runnable() {
        @Override
        public void run() {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    if (!currentDirectoryNodes.isEmpty()) {
                        triggerSourcePrefetch();
                    }
                }
            });
        }
    };

    /**
     * Constructor using VirtualResource + FileService.
     */
    public FtpConnectionTabImpl(VirtualResource resource, FileService fileService, TabbedPaneManager tabbedPaneManager, String searchPattern) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.resource = resource;
        this.fileService = fileService;
        this.previewOpener = new DocumentPreviewOpener(tabbedPaneManager);
        this.mainPanel = new JPanel(new BorderLayout());
        this.fileList = new JList<Object>(listModel);
        this.listContainer = new JPanel();

        // Initialize cache service
        this.cacheService = FtpSourceCacheService.getInstance();

        // Extract host from resource (guard against null from getHost())
        if (resource != null && resource.getFtpState() != null
                && resource.getFtpState().getConnectionId() != null) {
            String host = resource.getFtpState().getConnectionId().getHost();
            this.ftpHost = host != null ? host : "";
        }

        // Wire FTP scanner for indexing pipeline
        de.bund.zrb.indexing.connector.FtpSourceScanner ftpScanner =
                de.bund.zrb.indexing.service.IndexingService.getInstance().getFtpScanner();
        ftpScanner.setFileService(fileService);
        ftpScanner.setFtpHost(this.ftpHost);

        boolean mvsMode = resource.getFtpState() != null && Boolean.TRUE.equals(resource.getFtpState().getMvsMode());
        this.mvsMode = mvsMode;
        this.navigator = new PathNavigator(mvsMode);
        this.browserState = new BrowserSessionState(navigator.normalize(resource.getResolvedPath()));

        restoreNavigatorState();
        initUI(searchPattern);
    }

    public FtpConnectionTabImpl(VirtualResource resource, FileService fileService, TabbedPaneManager tabbedPaneManager) {
        this(resource, fileService, tabbedPaneManager, null);
    }

    private void initUI(String searchPattern) {
        // ── Path panel ──
        JPanel pathPanel = new JPanel(new BorderLayout());

        JButton refreshButton = new JButton("🔄");
        refreshButton.setToolTipText("Aktuellen Pfad neu laden");
        refreshButton.setMargin(new Insets(0, 0, 0, 0));
        refreshButton.setFont(refreshButton.getFont().deriveFont(Font.PLAIN, 18f));
        refreshButton.addActionListener(e -> loadDirectory(pathField.getText()));

        backButton = new JButton("⏴");
        backButton.setToolTipText("Zurück zum übergeordneten Verzeichnis");
        backButton.setMargin(new Insets(0, 0, 0, 0));
        backButton.setFont(backButton.getFont().deriveFont(Font.PLAIN, 20f));
        backButton.addActionListener(e -> navigateBack());

        forwardButton = new JButton("⏵");
        forwardButton.setToolTipText("Vorwärts zum nächsten Verzeichnis");
        forwardButton.setMargin(new Insets(0, 0, 0, 0));
        forwardButton.setFont(forwardButton.getFont().deriveFont(Font.PLAIN, 20f));
        forwardButton.addActionListener(e -> navigateForward());

        JButton goButton = new JButton("Öffnen");
        goButton.addActionListener(e -> loadDirectory(pathField.getText()));
        pathField.addActionListener(e -> loadDirectory(pathField.getText()));

        JPanel rightButtons = new JPanel(new GridLayout(1, 4, 0, 0));
        rightButtons.add(backButton);
        rightButtons.add(forwardButton);
        rightButtons.add(goButton);


        detailsButton = new JToggleButton("📊");
        detailsButton.setToolTipText("Indexierungs-Details anzeigen");
        detailsButton.setMargin(new Insets(0, 0, 0, 0));
        detailsButton.setFont(detailsButton.getFont().deriveFont(Font.PLAIN, 16f));
        detailsButton.setSelected(sidebarVisible);
        detailsButton.addActionListener(e -> toggleSidebar());
        rightButtons.add(detailsButton);

        pathPanel.add(refreshButton, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(rightButtons, BorderLayout.EAST);

        // ── Overlay setup ──
        overlayLabel.setHorizontalAlignment(SwingConstants.CENTER);
        overlayLabel.setVerticalAlignment(SwingConstants.CENTER);
        overlayLabel.setFont(overlayLabel.getFont().deriveFont(Font.BOLD, 14f));
        overlayLabel.setOpaque(true);
        overlayLabel.setVisible(false);

        // ── List setup (flat view) ──
        JScrollPane listScrollPane = new JScrollPane(fileList);
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileList.setCellRenderer(new FtpCellRenderer());
        fileList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            @Override
            public void valueChanged(javax.swing.event.ListSelectionEvent e) {
                if (!e.getValueIsAdjusting() && sidebarVisible) {
                    updateSidebarInfo();
                }
            }
        });
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
                    handleItemActivation(e.isControlDown());
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e, false);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e, false);
            }
        });

        // ── Tree setup (grouped view) ──
        treeRoot = new DefaultMutableTreeNode("root");
        treeModel = new DefaultTreeModel(treeRoot);
        extensionTree = new JTree(treeModel);
        extensionTree.setRootVisible(false);
        extensionTree.setShowsRootHandles(true);
        extensionTree.setCellRenderer(new FtpTreeCellRenderer());
        extensionTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        extensionTree.addTreeSelectionListener(new javax.swing.event.TreeSelectionListener() {
            @Override
            public void valueChanged(javax.swing.event.TreeSelectionEvent e) {
                if (sidebarVisible) {
                    updateSidebarInfo();
                }
            }
        });
        extensionTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
                    handleTreeDoubleClick();
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e, true);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e, true);
            }
        });
        JScrollPane treeScrollPane = new JScrollPane(extensionTree);

        // Card layout to switch between flat list and grouped tree
        viewCardLayout = new CardLayout();
        viewContainer = new JPanel(viewCardLayout);
        viewContainer.add(listScrollPane, "list");
        viewContainer.add(treeScrollPane, "tree");

        // Overlay wraps the view container
        listContainer.setLayout(new OverlayLayout(listContainer));
        listContainer.add(overlayLabel);
        listContainer.add(viewContainer);


        // Keyboard navigation
        de.bund.zrb.ui.util.ListKeyboardNavigation.install(
                fileList, searchBar.getTextField(),
                () -> handleItemActivation(false),
                this::navigateBack,
                this::navigateForward
        );
        // Arrow keys in path field → jump into file list
        de.bund.zrb.ui.util.ListKeyboardNavigation.installFieldNavigation(pathField, fileList);

        // ── Navigator toolbar ──
        JPanel navigatorToolbar = createNavigatorToolbar();

        // ── Status bar ──
        JPanel statusBar = createStatusBar();

        // ── Top panel: path bar + navigator toolbar ──
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        pathPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pathPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        navigatorToolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        navigatorToolbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        topPanel.add(pathPanel);
        topPanel.add(navigatorToolbar);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(listContainer, BorderLayout.CENTER);

        // Sidebar setup
        indexingSidebar.setVisible(sidebarVisible);
        indexingSidebar.setCustomIndexAction(this::indexSelectedOrAll);
        indexingSidebar.setCustomStatusSupplier(this::computeFtpCacheStatus);
        indexingSidebar.setSecurityGroup("FTP");
        indexingSidebar.setSecurityPathSupplier(this::getSelectedPrefixedPaths);
        indexingSidebar.setClearCacheAction(this::clearDirectoryCache);
        mainPanel.add(indexingSidebar, BorderLayout.EAST);

        // Re-trigger auto-prefetch when security rules change (e.g. user whitelists a path)
        SecurityFilterService.getInstance().addChangeListener(securityChangeListener);

        mainPanel.add(statusBar, BorderLayout.SOUTH);

        // Initial load
        String initialPath = browserState.getCurrentPath();
        pathField.setText(initialPath);

        if (initialPath != null && !initialPath.isEmpty() && !"''".equals(initialPath) && !"/".equals(initialPath)) {
            updateFileList();
        } else {
            showOverlayMessage("Bitte HLQ eingeben (z.B. BENUTZERKENNUNG)", Color.GRAY);
        }

        if (searchPattern != null && !searchPattern.trim().isEmpty()) {
            searchBar.setText(searchPattern.trim());
            applyFilter();
        }

        updateNavigationButtons();
    }

    // ═══════════════════════════════════════════════════════════
    //  Navigator Toolbar
    // ═══════════════════════════════════════════════════════════

    private JPanel createNavigatorToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        toolbar.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));

        // Group by Extension toggle
        groupToggle = new JToggleButton("\uD83D\uDCC1");
        groupToggle.setToolTipText("Nach Dateierweiterung gruppieren");
        groupToggle.setMargin(new Insets(1, 4, 1, 4));
        groupToggle.setFocusable(false);
        groupToggle.setSelected(groupByExtension);
        groupToggle.addActionListener(e -> {
            groupByExtension = groupToggle.isSelected();
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

        toolbar.add(Box.createHorizontalStrut(8));

        // Show Details toggle (size, date)
        JToggleButton detailsToggle = new JToggleButton("\uD83D\uDCCA");
        detailsToggle.setToolTipText("Details anzeigen (Größe, Datum)");
        detailsToggle.setMargin(new Insets(1, 4, 1, 4));
        detailsToggle.setFocusable(false);
        detailsToggle.setSelected(showDetails);
        detailsToggle.addActionListener(e -> {
            showDetails = detailsToggle.isSelected();
            saveNavigatorState();
            applyFilter();
        });
        toolbar.add(detailsToggle);

        toolbar.add(Box.createHorizontalStrut(8));

        // Collapse All (useful for grouped tree)
        JButton collapseButton = new JButton("\u23F6");
        collapseButton.setToolTipText("Alle zuklappen");
        collapseButton.setMargin(new Insets(1, 4, 1, 4));
        collapseButton.setFocusable(false);
        collapseButton.addActionListener(e -> collapseAllTreeNodes());
        toolbar.add(collapseButton);

        JButton expandButton = new JButton("\u23F7");
        expandButton.setToolTipText("Alle aufklappen");
        expandButton.setMargin(new Insets(1, 4, 1, 4));
        expandButton.setFocusable(false);
        expandButton.addActionListener(e -> expandAllTreeNodes());
        toolbar.add(expandButton);

        toolbar.add(Box.createHorizontalStrut(8));

        // Hide Blacklisted toggle
        JToggleButton hideBlacklistedToggle = new JToggleButton("\uD83D\uDD12");
        hideBlacklistedToggle.setToolTipText("Gesperrte Elemente ausblenden (Blacklist)");
        hideBlacklistedToggle.setMargin(new Insets(1, 4, 1, 4));
        hideBlacklistedToggle.setFocusable(false);
        hideBlacklistedToggle.setSelected(hideBlacklisted);
        hideBlacklistedToggle.addActionListener(e -> {
            hideBlacklisted = hideBlacklistedToggle.isSelected();
            saveNavigatorState();
            applyFilter();
        });
        toolbar.add(hideBlacklistedToggle);

        // ── Separator: File operations ──
        toolbar.add(Box.createHorizontalStrut(12));
        toolbar.add(createToolbarSeparator());
        toolbar.add(Box.createHorizontalStrut(8));

        // New Folder
        JButton newFolderButton = new JButton("\uD83D\uDCC2\u207A"); // 📂⁺
        newFolderButton.setToolTipText(mvsMode
                ? "Neues Dataset / PDS anlegen (Mainframe)"
                : "Neuen Ordner anlegen");
        newFolderButton.setMargin(new Insets(1, 4, 1, 4));
        newFolderButton.setFocusable(false);
        newFolderButton.addActionListener(e -> createNewFolder());
        toolbar.add(newFolderButton);

        // New File
        JButton newFileButton = new JButton("\uD83D\uDCC4\u207A"); // 📄⁺
        newFileButton.setToolTipText(mvsMode
                ? "Neues Member / Sequential Dataset anlegen (Mainframe)"
                : "Neue Datei anlegen");
        newFileButton.setMargin(new Insets(1, 4, 1, 4));
        newFileButton.setFocusable(false);
        newFileButton.addActionListener(e -> createNewFile());
        toolbar.add(newFileButton);

        toolbar.add(Box.createHorizontalStrut(4));

        // Delete
        JButton deleteButton = new JButton("\uD83D\uDDD1"); // 🗑
        deleteButton.setToolTipText("Ausgewählte Dateien/Ordner löschen");
        deleteButton.setMargin(new Insets(1, 4, 1, 4));
        deleteButton.setFocusable(false);
        deleteButton.addActionListener(e -> deleteSelectedEntries());
        toolbar.add(deleteButton);

        return toolbar;
    }

    /** Small vertical separator line for the toolbar. */
    private static JComponent createToolbarSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 18));
        return sep;
    }

    // ═══════════════════════════════════════════════════════════
    //  ConnectionTab interface
    // ═══════════════════════════════════════════════════════════

    @Override
    public String getTitle() {
        return "📁 Verbindung";
    }

    @Override
    public String getTooltip() {
        return "";
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        SecurityFilterService.getInstance().removeChangeListener(securityChangeListener);
        String currentPath = browserState.getCurrentPath();
        if (!ftpHost.isEmpty() && currentPath != null) {
            cacheService.cancelPrefetch(ftpHost, currentPath);
        }
        try {
            fileService.close();
        } catch (Exception ignore) {
            // ignore
        }
    }

    @Override
    public void saveIfApplicable() {
        // nothing to save
    }

    @Override
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < listModel.size(); i++) {
            sb.append(listModel.get(i)).append("\n");
        }
        return sb.toString();
    }

    @Override
    public void markAsChanged() {}

    @Override
    public String getPath() {
        return pathField.getText();
    }

    @Override
    public Type getType() {
        return Type.CONNECTION;
    }

    @Override
    public void focusSearchField() {
        searchBar.focusAndSelectAll();
    }

    public void searchFor(String searchPattern) {
        if (searchPattern == null) return;
        searchBar.setText(searchPattern.trim());
        applyFilter();
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem bookmarkItem = new JMenuItem("🕮 Bookmark setzen");
        bookmarkItem.addActionListener(e -> {
            MainFrame main = (MainFrame) SwingUtilities.getWindowAncestor(getComponent());
            main.getBookmarkDrawer().setBookmarkForCurrentPath(getComponent(), getPath(), "FTP", "DIRECTORY");
        });

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        menu.add(bookmarkItem);
        menu.add(closeItem);
        return menu;
    }

    // ═══════════════════════════════════════════════════════════
    //  Navigation
    // ═══════════════════════════════════════════════════════════

    public void loadDirectory(String path) {
        navigateTo(path, true);
    }

    @Override
    public void navigateBack() {
        browserState.back();
        pathField.setText(browserState.getCurrentPath());
        updateNavigationButtons();
        updateFileList();
        if (sidebarVisible) updateSidebarInfo();
        tabbedPaneManager.refreshStarForTab(this);
    }

    @Override
    public void navigateForward() {
        browserState.forward();
        pathField.setText(browserState.getCurrentPath());
        updateNavigationButtons();
        updateFileList();
        if (sidebarVisible) updateSidebarInfo();
        tabbedPaneManager.refreshStarForTab(this);
    }

    @Override
    public boolean canNavigateBack() {
        return browserState.canGoBack();
    }

    @Override
    public boolean canNavigateForward() {
        return browserState.canGoForward();
    }

    private void navigateTo(String path, boolean addToHistory) {
        String normalized = navigator.normalize(path);
        if (addToHistory) {
            browserState.goTo(normalized);
        }
        pathField.setText(browserState.getCurrentPath());
        updateNavigationButtons();
        updateFileList();
        if (sidebarVisible) updateSidebarInfo();
        tabbedPaneManager.refreshStarForTab(this);
    }

    private void updateNavigationButtons() {
        if (backButton != null) {
            backButton.setEnabled(browserState.canGoBack());
        }
        if (forwardButton != null) {
            forwardButton.setEnabled(browserState.canGoForward());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Sidebar
    // ═══════════════════════════════════════════════════════════

    private void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        indexingSidebar.setVisible(sidebarVisible);
        detailsButton.setSelected(sidebarVisible);
        if (sidebarVisible) {
            updateSidebarInfo();
        }
        saveNavigatorState();
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    /**
     * Update the IndexingSidebar path and scope to reflect the current directory
     * and selection state.
     */
    private void updateSidebarInfo() {
        try {
            String currentPath = browserState.getCurrentPath();
            String displayPath = (ftpHost == null || ftpHost.isEmpty())
                    ? "ftp://" + currentPath
                    : "ftp://" + ftpHost + "/" + currentPath;
            indexingSidebar.setCurrentPath(displayPath);

            List<FileNode> sel = collectSelectedFileNodes();
            if (sel.isEmpty()) {
                List<FileNode> textFiles = getTextFileNodes();
                indexingSidebar.setScopeInfo("Alle " + textFiles.size() + " Textdateien");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(sel.size(), 3); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(sel.get(i).getName());
                }
                if (sel.size() > 3) sb.append(" … (+" + (sel.size() - 3) + ")");
                indexingSidebar.setScopeInfo("Auswahl: " + sel.size() + " Dateien");
            }
        } catch (Exception e) {
            System.err.println("[ConnectionTab] Error updating sidebar info: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Compute cache-aware status for the IndexingSidebar's custom status supplier.
     * Returns [statusText, colorHex, itemCountText, lastIndexedText].
     */
    private String[] computeFtpCacheStatus() {
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        String currentPath = browserState.getCurrentPath();

        if (ftpHost.isEmpty() || currentPath == null || currentPath.isEmpty()) {
            return new String[]{"⬜ Nicht gecacht", "#999999", "-", "-"};
        }

        int memCached = cacheService.getMemoryCacheSize(ftpHost, currentPath);
        int h2Cached = cacheService.getH2CacheSize(ftpHost, currentPath);
        boolean prefetching = cacheService.isPrefetching(ftpHost, currentPath);
        int textFileCount = getTextFileNodes().size();

        if (prefetching) {
            return new String[]{
                    "\uD83D\uDD04 Wird gecacht...",
                    "#6495ED",
                    memCached + " / " + textFileCount,
                    "-"
            };
        }

        int cachedCount = Math.max(memCached, h2Cached);
        if (cachedCount > 0) {
            return new String[]{
                    "✅ Gecacht",
                    "#4CAF50",
                    cachedCount + " / " + textFileCount,
                    fmt.format(new Date(System.currentTimeMillis()))
            };
        }

        return new String[]{"⬜ Nicht gecacht", "#999999", "0 / " + textFileCount, "-"};
    }

    // ═══════════════════════════════════════════════════════════
    //  Context Menu & Selection
    // ═══════════════════════════════════════════════════════════

    /**
     * Show right-click context menu for list or tree view.
     */
    private void showContextMenu(MouseEvent e, boolean isTree) {
        // Right-click on unselected item: select it
        if (isTree) {
            int row = extensionTree.getRowForLocation(e.getX(), e.getY());
            if (row >= 0 && !extensionTree.isRowSelected(row)) {
                extensionTree.setSelectionRow(row);
            }
        } else {
            int idx = fileList.locationToIndex(e.getPoint());
            if (idx >= 0 && !fileList.isSelectedIndex(idx)) {
                fileList.setSelectedIndex(idx);
            }
        }

        JPopupMenu menu = new JPopupMenu();

        JMenuItem selectAll = new JMenuItem("Alles auswählen");
        selectAll.addActionListener(ev -> {
            if (isTree && groupByExtension) {
                extensionTree.setSelectionInterval(0, extensionTree.getRowCount() - 1);
            } else {
                int size = fileList.getModel().getSize();
                if (size > 0) fileList.setSelectionInterval(0, size - 1);
            }
        });
        menu.add(selectAll);

        JMenuItem selectNone = new JMenuItem("Nichts auswählen");
        selectNone.addActionListener(ev -> {
            if (isTree && groupByExtension) {
                extensionTree.clearSelection();
            } else {
                fileList.clearSelection();
            }
        });
        menu.add(selectNone);

        menu.addSeparator();

        List<FileNode> sel = collectSelectedFileNodes();
        String label = sel.isEmpty()
                ? "▶ Alles indexieren"
                : "▶ Auswahl indexieren (" + sel.size() + ")";
        JMenuItem indexItem = new JMenuItem(label);
        indexItem.addActionListener(ev -> indexSelectedOrAll());
        menu.add(indexItem);

        menu.addSeparator();

        JMenuItem newFolderItem = new JMenuItem(mvsMode ? "📂 Neues Dataset anlegen…" : "📂 Neuer Ordner…");
        newFolderItem.addActionListener(ev -> createNewFolder());
        menu.add(newFolderItem);

        JMenuItem newFileItem = new JMenuItem(mvsMode ? "📄 Neues Member anlegen…" : "📄 Neue Datei…");
        newFileItem.addActionListener(ev -> createNewFile());
        menu.add(newFileItem);

        if (!sel.isEmpty()) {
            menu.addSeparator();
            JMenuItem deleteItem = new JMenuItem("🗑 Löschen (" + sel.size() + ")…");
            deleteItem.addActionListener(ev -> deleteSelectedEntries());
            menu.add(deleteItem);
        }

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    /**
     * Collect selected FileNode items from the current active view (list or tree).
     * For tree group nodes, all child file nodes are included.
     */
    private List<FileNode> collectSelectedFileNodes() {
        List<FileNode> selected = new ArrayList<FileNode>();

        if (groupByExtension) {
            TreePath[] paths = extensionTree.getSelectionPaths();
            if (paths != null) {
                Set<FileNode> added = new HashSet<FileNode>();
                for (TreePath path : paths) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    Object userObj = node.getUserObject();
                    if (userObj instanceof FileNode) {
                        FileNode fn = (FileNode) userObj;
                        if (added.add(fn)) selected.add(fn);
                    } else if (userObj instanceof ExtensionGroupNode) {
                        // Group node: include all children
                        for (int i = 0; i < node.getChildCount(); i++) {
                            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                            Object childObj = child.getUserObject();
                            if (childObj instanceof FileNode && added.add((FileNode) childObj)) {
                                selected.add((FileNode) childObj);
                            }
                        }
                    }
                }
            }
        } else {
            List<Object> selValues = fileList.getSelectedValuesList();
            for (Object item : selValues) {
                if (item instanceof FileNode) {
                    selected.add((FileNode) item);
                }
            }
        }

        return selected;
    }

    /** Get all visible text file nodes (respecting current filter). */
    private List<FileNode> getTextFileNodes() {
        List<FileNode> result = new ArrayList<FileNode>();
        for (FileNode node : currentDirectoryNodes) {
            if (!node.isDirectory() && FtpSourceCacheService.isTextFile(node.getName())) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Build prefixed paths from the current selection (for SecurityFilterService).
     * Format: "ftp://host/dir/filename"
     * If nothing is selected, returns the current directory path.
     */
    private List<String> getSelectedPrefixedPaths() {
        List<FileNode> selected = collectSelectedFileNodes();
        String currentPath = browserState.getCurrentPath();
        String host = ftpHost != null ? ftpHost : "";
        // Build the FTP prefix: "ftp://host/" or "ftp://" if host is unknown
        String prefix = host.isEmpty() ? "ftp://" : "ftp://" + host + "/";
        if (selected.isEmpty()) {
            if (currentPath == null || currentPath.isEmpty()) return Collections.emptyList();
            return Collections.singletonList(prefix + currentPath);
        }
        List<String> paths = new ArrayList<String>();
        for (FileNode fn : selected) {
            String suffix = fn.isDirectory() ? fn.getName() + "/" : fn.getName();
            String base = (currentPath != null && !currentPath.isEmpty())
                    ? prefix + currentPath + "/" + suffix
                    : prefix + suffix;
            paths.add(base);
        }
        return paths;
    }

    // ═══════════════════════════════════════════════════════════
    //  Item Activation (double-click / enter)
    // ═══════════════════════════════════════════════════════════

    private void handleItemActivation(boolean ctrlDown) {
        Object selected;
        if (groupByExtension) {
            TreePath treePath = extensionTree.getSelectionPath();
            if (treePath == null) return;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
            selected = node.getUserObject();
            if (selected instanceof ExtensionGroupNode) return; // group nodes expand/collapse
        } else {
            selected = fileList.getSelectedValue();
        }
        if (selected == null) return;

        if (selected instanceof FileNode) {
            FileNode node = (FileNode) selected;
            if (node.isDirectory()) {
                browserState.goTo(node.getPath());
                pathField.setText(browserState.getCurrentPath());
                updateFileList();
                if (sidebarVisible) updateSidebarInfo();
                tabbedPaneManager.refreshStarForTab(FtpConnectionTabImpl.this);
                return;
            }

            // File: open it
            String nextPath = node.getPath();
            if (nextPath == null || nextPath.isEmpty()) {
                nextPath = navigator.childOf(browserState.getCurrentPath(), node.getName());
            }

            if (ctrlDown) {
                openFileRaw(nextPath, node.getName());
            } else {
                previewOpener.openPreviewAsync(fileService, nextPath, node.getName(), mainPanel);
            }
        } else if (selected instanceof String) {
            // Legacy string handling (shouldn't happen with FileNode model)
            String name = (String) selected;
            String nextPath = navigator.childOf(browserState.getCurrentPath(), name);
            try {
                List<FileNode> listed = fileService.list(nextPath);
                currentDirectoryNodes = listed == null ? new ArrayList<FileNode>() : listed;
                browserState.goTo(nextPath);
                pathField.setText(browserState.getCurrentPath());
                applyFilter();
                if (sidebarVisible) updateSidebarInfo();
                tabbedPaneManager.refreshStarForTab(FtpConnectionTabImpl.this);
            } catch (Exception ignore) {
                if (ctrlDown) {
                    openFileRaw(nextPath, name);
                } else {
                    previewOpener.openPreviewAsync(fileService, nextPath, name, mainPanel);
                }
            }
        }
    }

    private void handleTreeDoubleClick() {
        handleItemActivation(false);
    }

    private void openFileRaw(String path, String name) {
        try {
            FilePayload payload = fileService.readFile(path);
            String content = payload.getEditorText();

            // Update cache with freshly downloaded content
            if (!ftpHost.isEmpty()) {
                FileNode node = findNodeByName(name);
                long size = node != null ? node.getSize() : -1;
                long mtime = node != null ? node.getLastModifiedMillis() : -1;
                cacheService.cacheContent(ftpHost, path, name, content, size, mtime);
            }

            VirtualResource fileResource = buildResourceForPath(path, VirtualResourceKind.FILE);
            tabbedPaneManager.openFileTab(fileResource, content, null, null, false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler beim Öffnen:\n" + ex.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Loading & Filtering
    // ═══════════════════════════════════════════════════════════

    private void updateFileList() {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            showOverlayMessage("Lade...", Color.GRAY);
        });

        new Thread(() -> {
            try {
                String currentPath = browserState.getCurrentPath();
                de.bund.zrb.util.AppLogger.get(de.bund.zrb.util.AppLogger.UI)
                        .fine("[ConnectionTab] Loading directory: " + currentPath);

                List<FileNode> nodes = fileService.list(currentPath);

                SwingUtilities.invokeLater(() -> {
                    currentDirectoryNodes = nodes == null ? new ArrayList<FileNode>() : nodes;
                    applyFilter();

                    if (currentDirectoryNodes.isEmpty()) {
                        showOverlayMessage("Keine Einträge gefunden", Color.ORANGE.darker());
                    } else {
                        hideOverlay();
                    }

                    statusLabel.setText(currentDirectoryNodes.size() + " Einträge");

                    if (sidebarVisible) updateSidebarInfo();

                    // Auto-prefetch text files for Lucene indexing
                    triggerSourcePrefetch();
                });
            } catch (Exception e) {
                System.err.println("[ConnectionTab] Error loading directory: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    showOverlayMessage("Fehler: " + e.getMessage(), Color.RED);
                });
            }
        }).start();
    }

    private void applyFilter() {
        String regex = searchBar.getText().trim();

        // Step 1: Filter items by search regex
        List<FileNode> filtered = new ArrayList<FileNode>();
        for (FileNode node : currentDirectoryNodes) {
            try {
                if (regex.isEmpty()
                        || Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(node.getName()).find()) {
                    filtered.add(node);
                }
            } catch (Exception e) {
                break; // invalid regex
            }
        }

        // Step 1b: Filter out blacklisted items if toggle is active
        if (hideBlacklisted && !ftpHost.isEmpty()) {
            SecurityFilterService sfs = SecurityFilterService.getInstance();
            String currentPath = browserState.getCurrentPath();
            List<FileNode> allowed = new ArrayList<FileNode>();
            for (FileNode node : filtered) {
                String prefixedPath = "ftp://" + ftpHost + "/" + currentPath + "/" + node.getName();
                if (sfs.isAllowed("FTP", prefixedPath)) {
                    allowed.add(node);
                }
            }
            filtered = allowed;
        }

        // Step 2: Sort
        sortItems(filtered);

        // Step 3: Populate the appropriate view
        if (groupByExtension) {
            populateGroupedTree(filtered);
            viewCardLayout.show(viewContainer, "tree");
        } else {
            DefaultListModel<Object> newModel = new DefaultListModel<Object>();
            for (FileNode node : filtered) {
                newModel.addElement(node);
            }
            listModel = newModel;
            fileList.setModel(listModel);
            viewCardLayout.show(viewContainer, "list");
        }

        searchBar.getTextField().setBackground(!filtered.isEmpty() || regex.isEmpty()
                ? UIManager.getColor("TextField.background")
                : new Color(255, 200, 200));
    }

    private void sortItems(List<FileNode> items) {
        // Directories always first
        Collections.sort(items, new Comparator<FileNode>() {
            @Override
            public int compare(FileNode a, FileNode b) {
                // Directories first
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;

                switch (sortMode) {
                    case NAME_DESC:
                        return b.getName().compareToIgnoreCase(a.getName());
                    case SIZE_DESC:
                        return Long.compare(b.getSize(), a.getSize());
                    case DATE_DESC:
                        return Long.compare(b.getLastModifiedMillis(), a.getLastModifiedMillis());
                    case EXT:
                        String extA = extractExtension(a.getName());
                        String extB = extractExtension(b.getName());
                        int ec = extA.compareToIgnoreCase(extB);
                        return ec != 0 ? ec : a.getName().compareToIgnoreCase(b.getName());
                    case NAME_ASC:
                    default:
                        return a.getName().compareToIgnoreCase(b.getName());
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Grouped Tree View (by Extension)
    // ═══════════════════════════════════════════════════════════

    /** Wrapper for extension group nodes in the tree. */
    static class ExtensionGroupNode {
        final String extension;
        final String displayText;

        ExtensionGroupNode(String extension, String displayText) {
            this.extension = extension;
            this.displayText = displayText;
        }

        @Override
        public String toString() {
            return displayText;
        }
    }

    private void populateGroupedTree(List<FileNode> filteredItems) {
        treeRoot.removeAllChildren();

        // Directories group
        List<FileNode> directories = new ArrayList<FileNode>();
        // Group files by extension
        LinkedHashMap<String, List<FileNode>> groups = new LinkedHashMap<String, List<FileNode>>();

        for (FileNode node : filteredItems) {
            if (node.isDirectory()) {
                directories.add(node);
            } else {
                String ext = extractExtension(node.getName()).toUpperCase();
                if (ext.isEmpty()) ext = "(Ohne Erweiterung)";
                List<FileNode> group = groups.get(ext);
                if (group == null) {
                    group = new ArrayList<FileNode>();
                    groups.put(ext, group);
                }
                group.add(node);
            }
        }

        // Add directories first
        if (!directories.isEmpty()) {
            String dirText = getExtensionIcon("DIR") + " Verzeichnisse (" + directories.size() + ")";
            DefaultMutableTreeNode dirGroupNode = new DefaultMutableTreeNode(
                    new ExtensionGroupNode("DIR", dirText));
            for (FileNode dir : directories) {
                dirGroupNode.add(new DefaultMutableTreeNode(dir));
            }
            treeRoot.add(dirGroupNode);
        }

        // Add file groups sorted by extension
        List<String> sortedExts = new ArrayList<String>(groups.keySet());
        Collections.sort(sortedExts);
        for (String ext : sortedExts) {
            List<FileNode> members = groups.get(ext);
            String icon = getExtensionIcon(ext);
            String groupText = icon + " " + ext + " (" + members.size() + ")";
            DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(
                    new ExtensionGroupNode(ext, groupText));
            for (FileNode fn : members) {
                groupNode.add(new DefaultMutableTreeNode(fn));
            }
            treeRoot.add(groupNode);
        }

        treeModel.reload();
        // Expand all groups by default
        expandAllTreeNodes();
    }

    private static String getExtensionIcon(String ext) {
        if (ext == null) return "📄";
        String upper = ext.toUpperCase();
        if (upper.equals("DIR")) return "📁";
        if (upper.equals("JCL")) return "⚙";
        if (upper.equals("CBL") || upper.equals("COB") || upper.equals("CPY")) return "🔷";
        if (upper.equals("XML") || upper.equals("HTML") || upper.equals("HTM")) return "🌐";
        if (upper.equals("JSON")) return "📋";
        if (upper.equals("SQL") || upper.equals("DDL") || upper.equals("DML")) return "🗄";
        if (upper.equals("REXX")) return "📜";
        if (upper.equals("ASM")) return "🔧";
        if (upper.equals("TXT") || upper.equals("LOG")) return "📝";
        if (upper.equals("CSV") || upper.equals("XLS") || upper.equals("XLSX")) return "📊";
        if (upper.equals("PDF")) return "📕";
        return "📄";
    }

    private void collapseAllTreeNodes() {
        for (int i = extensionTree.getRowCount() - 1; i >= 0; i--) {
            extensionTree.collapseRow(i);
        }
    }

    private void expandAllTreeNodes() {
        for (int i = 0; i < extensionTree.getRowCount(); i++) {
            extensionTree.expandRow(i);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Cell Renderers
    // ═══════════════════════════════════════════════════════════

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    /** List cell renderer — shows icon + name + optional details (size, date). */
    private class FtpCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                       int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof FileNode) {
                FileNode node = (FileNode) value;
                String icon = node.isDirectory() ? "📁" : getExtensionIcon(extractExtension(node.getName()));
                if (showDetails) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(icon).append(" ").append(node.getName());
                    if (!node.isDirectory()) {
                        if (node.getSize() > 0) sb.append("  (").append(formatSize(node.getSize())).append(")");
                        if (node.getLastModifiedMillis() > 0)
                            sb.append("  ").append(DATE_FMT.format(new Date(node.getLastModifiedMillis())));
                    }
                    setText(sb.toString());
                } else {
                    setText(icon + " " + node.getName());
                }
                setFont(getFont().deriveFont(node.isDirectory() ? Font.BOLD : Font.PLAIN));
            } else if (value instanceof String) {
                setText("📁 " + value);
                setFont(getFont().deriveFont(Font.BOLD));
            }
            return this;
        }
    }

    /** Tree cell renderer for grouped view. */
    private class FtpTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                       boolean sel, boolean expanded, boolean leaf,
                                                       int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof ExtensionGroupNode) {
                    setText(((ExtensionGroupNode) userObj).displayText);
                    setFont(getFont().deriveFont(Font.BOLD));
                    setIcon(null);
                } else if (userObj instanceof FileNode) {
                    FileNode node = (FileNode) userObj;
                    String icon = node.isDirectory() ? "📁" : getExtensionIcon(extractExtension(node.getName()));
                    if (showDetails) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(icon).append(" ").append(node.getName());
                        if (!node.isDirectory()) {
                            if (node.getSize() > 0) sb.append("  (").append(formatSize(node.getSize())).append(")");
                            if (node.getLastModifiedMillis() > 0)
                                sb.append("  ").append(DATE_FMT.format(new Date(node.getLastModifiedMillis())));
                        }
                        setText(sb.toString());
                    } else {
                        setText(icon + " " + node.getName());
                    }
                    setFont(getFont().deriveFont(node.isDirectory() ? Font.BOLD : Font.PLAIN));
                    setIcon(null);
                }
            }
            return this;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Source Prefetch & Indexing
    // ═══════════════════════════════════════════════════════════

    /**
     * Start background prefetching of all text files in the current directory.
     * Enables SearchEverywhere to find FTP files by content.
     */
    private void triggerSourcePrefetch() {
        if (ftpHost.isEmpty()) return;
        String currentPath = browserState.getCurrentPath();
        if (currentPath == null || currentPath.isEmpty()) return;
        if (cacheService.isPrefetching(ftpHost, currentPath)) return;

        // Filter text files by security rules (file-level check).
        // This handles both directory-level and file-level whitelist entries,
        // and respects individual blacklist entries within an allowed directory.
        SecurityFilterService sfs = SecurityFilterService.getInstance();
        String pathPrefix = "ftp://" + ftpHost + "/" + currentPath;
        List<FileNode> textFiles = getTextFileNodes();
        List<FileNode> allowedFiles = new ArrayList<FileNode>();
        for (FileNode fn : textFiles) {
            String filePath = pathPrefix + "/" + fn.getName();
            if (sfs.isAllowed("FTP", filePath)) {
                allowedFiles.add(fn);
            }
        }
        if (allowedFiles.isEmpty()) return;

        if (sidebarVisible) {
            indexingSidebar.setCurrentPath(pathPrefix);
            indexingSidebar.setScopeInfo("Auto-Prefetch: " + allowedFiles.size() + " Textdateien");
        }

        cacheService.prefetchDirectory(ftpHost, currentPath, allowedFiles, fileService,
                new FtpSourceCacheService.PrefetchCallback() {
            @Override
            public void onProgress(final int current, final int total, final String fileName) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (indexingSidebar.isVisible()) {
                            indexingSidebar.updateProgress(current, total, true);
                        }
                    }
                });
            }

            @Override
            public void onComplete(final int total, final int indexed) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText(currentDirectoryNodes.size() + " Einträge");
                        if (indexingSidebar.isVisible()) {
                            indexingSidebar.updateComplete(total, indexed, true);
                        }
                    }
                });
            }

            @Override
            public void onError(final String message) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (indexingSidebar.isVisible()) {
                            indexingSidebar.updateError(message);
                        }
                    }
                });
            }
        });
    }

    /**
     * Index the selected files, or all visible text files if nothing is selected.
     */
    private void indexSelectedOrAll() {
        List<FileNode> selected = collectSelectedFileNodes();

        // Make sidebar visible to show progress
        if (!sidebarVisible) {
            sidebarVisible = true;
            indexingSidebar.setVisible(true);
            detailsButton.setSelected(true);
            mainPanel.revalidate();
            mainPanel.repaint();
        }

        List<FileNode> filesToIndex;
        boolean wasSelected = !selected.isEmpty();
        if (selected.isEmpty()) {
            filesToIndex = getTextFileNodes();
        } else {
            filesToIndex = new ArrayList<FileNode>();
            for (FileNode fn : selected) {
                if (!fn.isDirectory() && FtpSourceCacheService.isTextFile(fn.getName())) {
                    filesToIndex.add(fn);
                }
            }
        }

        if (filesToIndex.isEmpty() || ftpHost.isEmpty()) return;

        String currentPath = browserState.getCurrentPath();

        // Filter out blacklisted files
        SecurityFilterService sfs = SecurityFilterService.getInstance();
        List<FileNode> allowedFiles = new ArrayList<FileNode>();
        for (FileNode fn : filesToIndex) {
            String prefixedPath = "ftp://" + ftpHost + "/" + currentPath + "/" + fn.getName();
            if (sfs.isAllowed("FTP", prefixedPath)) {
                allowedFiles.add(fn);
            }
        }
        filesToIndex = allowedFiles;
        if (filesToIndex.isEmpty()) {
            statusLabel.setText("Alle ausgew\u00e4hlten Dateien sind durch den Sicherheitsfilter gesperrt.");
            return;
        }

        // Update sidebar scope
        String displayPath = "ftp://" + ftpHost + "/" + currentPath;
        indexingSidebar.setCurrentPath(displayPath);
        if (wasSelected) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(filesToIndex.size(), 4); i++) {
                if (i > 0) sb.append(", ");
                sb.append(filesToIndex.get(i).getName());
            }
            if (filesToIndex.size() > 4) sb.append(" … (+" + (filesToIndex.size() - 4) + ")");
            indexingSidebar.setScopeInfo("Auswahl: " + sb.toString());
        } else {
            indexingSidebar.setScopeInfo("Alle " + filesToIndex.size() + " Textdateien");
        }
        indexingSidebar.updateProgress(0, filesToIndex.size());

        cacheService.prefetchDirectory(ftpHost, currentPath, filesToIndex, fileService,
                new FtpSourceCacheService.PrefetchCallback() {
            @Override
            public void onProgress(final int current, final int total, final String fileName) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        indexingSidebar.updateProgress(current, total);
                    }
                });
            }

            @Override
            public void onComplete(final int total, final int indexed) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        indexingSidebar.updateComplete(total, indexed);
                    }
                });
            }

            @Override
            public void onError(final String message) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        indexingSidebar.updateError(message);
                    }
                });
            }
        });
    }

    /**
     * Clear cached FTP sources for the current directory.
     */
    private void clearDirectoryCache() {
        String currentPath = browserState.getCurrentPath();
        if (ftpHost.isEmpty() || currentPath == null || currentPath.isEmpty()) {
            statusLabel.setText("Kein Verzeichnis ausgewählt");
            return;
        }

        int result = JOptionPane.showConfirmDialog(mainPanel,
                "Cache für Verzeichnis '" + currentPath + "' leeren?\n\n"
                        + "Dies entfernt:\n"
                        + "• Zwischengespeicherte Dateiinhalte\n"
                        + "• Lucene-Suchindex\n\n"
                        + "Die Daten werden beim nächsten Öffnen neu geladen.",
                "Cache leeren", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            cacheService.invalidateDirectory(ftpHost, currentPath);
            statusLabel.setText("Cache für " + currentPath + " geleert");
            if (sidebarVisible) {
                indexingSidebar.refreshStatus();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Navigator State Persistence
    // ═══════════════════════════════════════════════════════════

    private void saveNavigatorState() {
        Settings settings = SettingsHelper.load();
        Map<String, String> state = settings.applicationState;

        state.put(STATE_PREFIX + "groupByExtension", String.valueOf(groupByExtension));
        state.put(STATE_PREFIX + "sortMode", sortMode.name());
        state.put(STATE_PREFIX + "showDetails", String.valueOf(showDetails));
        state.put(STATE_PREFIX + "sidebarVisible", String.valueOf(sidebarVisible));
        state.put(STATE_PREFIX + "hideBlacklisted", String.valueOf(hideBlacklisted));

        SettingsHelper.save(settings);
    }

    private void restoreNavigatorState() {
        Settings settings = SettingsHelper.load();
        Map<String, String> state = settings.applicationState;

        String groupVal = state.get(STATE_PREFIX + "groupByExtension");
        if (groupVal != null) groupByExtension = Boolean.parseBoolean(groupVal);

        String sortVal = state.get(STATE_PREFIX + "sortMode");
        if (sortVal != null) {
            try {
                sortMode = SortMode.valueOf(sortVal);
            } catch (IllegalArgumentException ignored) {}
        }

        String detailsVal = state.get(STATE_PREFIX + "showDetails");
        if (detailsVal != null) showDetails = Boolean.parseBoolean(detailsVal);

        String sidebarVal = state.get(STATE_PREFIX + "sidebarVisible");
        if (sidebarVal != null) sidebarVisible = Boolean.parseBoolean(sidebarVal);

        String hideBlVal = state.get(STATE_PREFIX + "hideBlacklisted");
        if (hideBlVal != null) hideBlacklisted = Boolean.parseBoolean(hideBlVal);
    }

    // ═══════════════════════════════════════════════════════════
    //  Overlay
    // ═══════════════════════════════════════════════════════════

    private void showOverlayMessage(String message, Color color) {
        overlayLabel.setText(message);
        overlayLabel.setForeground(color);
        overlayLabel.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
        overlayLabel.setVisible(true);
    }

    private void hideOverlay() {
        overlayLabel.setVisible(false);
    }

    // ═══════════════════════════════════════════════════════════
    //  Mouse navigation
    // ═══════════════════════════════════════════════════════════
    //  Status bar
    // ═══════════════════════════════════════════════════════════

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());

        // Filter bar (SearchBarPanel replaces old JTextField + label)
        searchBar.getTextField().getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        statusBar.add(searchBar, BorderLayout.CENTER);
        statusBar.add(statusLabel, BorderLayout.EAST);

        return statusBar;
    }

    // ═══════════════════════════════════════════════════════════
    //  File operations
    // ═══════════════════════════════════════════════════════════

    /**
     * Create a new folder / directory.
     * On Mainframe (mvsMode): creates a PDS or sequential dataset with DCB attributes.
     * On normal FTP: creates a plain directory.
     */
    private void createNewFolder() {
        String currentPath = browserState.getCurrentPath();

        if (mvsMode) {
            createMainframeDataset(currentPath);
        } else {
            String name = JOptionPane.showInputDialog(mainPanel,
                    "Name des neuen Ordners:", "Neuer Ordner", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.trim().isEmpty()) return;
            try {
                String target = navigator.childOf(currentPath, name.trim());
                if (fileService.createDirectory(target)) {
                    updateFileList();
                } else {
                    JOptionPane.showMessageDialog(mainPanel,
                            "Ordner konnte nicht angelegt werden.", "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainPanel,
                        "Fehler beim Anlegen:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Show a dialog for creating a Mainframe PDS / PS dataset with DCB parameters.
     */
    private void createMainframeDataset(final String currentPath) {
        final String baseQualifier = resolveBaseQualifier(currentPath);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Context label
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        String ctxText = baseQualifier.isEmpty()
                ? "<html><b>Kein Kontext</b> — bitte vollständigen Dataset-Namen eingeben</html>"
                : "<html>Aktueller Kontext: <b>" + baseQualifier + "</b></html>";
        panel.add(new JLabel(ctxText), gbc);

        // Dataset name (short qualifier input)
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0;
        panel.add(new JLabel("Neuer Qualifier:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        final JTextField nameField = new JTextField("", 30);
        nameField.setToolTipText("<html>Nur den neuen Teil eingeben (z.B. <b>NEW</b>)<br>"
                + "Für vollständigen Pfad mit Punkt eingeben (z.B. <b>USR1.KOMPLETT.NEU</b>)</html>");
        panel.add(nameField, gbc);

        // Preview label
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 0;
        final JLabel previewLabel = new JLabel(" ");
        previewLabel.setForeground(new Color(0, 100, 0));
        panel.add(previewLabel, gbc);

        // Live preview update
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                String text = nameField.getText().trim();
                if (text.isEmpty()) {
                    previewLabel.setText(" ");
                } else {
                    String full = buildMvsDatasetName(text, baseQualifier);
                    previewLabel.setText("→ " + full);
                }
            }
            public void insertUpdate(DocumentEvent e) { update(); }
            public void removeUpdate(DocumentEvent e) { update(); }
            public void changedUpdate(DocumentEvent e) { update(); }
        });

        // DSORG
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0;
        panel.add(new JLabel("DSORG:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> dsorgCombo = new JComboBox<String>(new String[]{"PO", "PS"});
        dsorgCombo.setToolTipText("PO = Partitioned (PDS), PS = Physical Sequential");
        panel.add(dsorgCombo, gbc);

        // RECFM
        gbc.gridx = 0; gbc.gridy = 4;
        panel.add(new JLabel("RECFM:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> recfmCombo = new JComboBox<String>(new String[]{"FB", "VB", "F", "V", "FBA", "VBA", "U"});
        recfmCombo.setToolTipText("FB = Fixed Blocked, VB = Variable Blocked, U = Undefined");
        panel.add(recfmCombo, gbc);

        // LRECL
        gbc.gridx = 0; gbc.gridy = 5;
        panel.add(new JLabel("LRECL:"), gbc);
        gbc.gridx = 1;
        JSpinner lreclSpinner = new JSpinner(new SpinnerNumberModel(80, 1, 32760, 1));
        lreclSpinner.setToolTipText("Logical Record Length (z.B. 80 für JCL/COBOL, 133 für Drucklisten)");
        panel.add(lreclSpinner, gbc);

        // BLKSIZE
        gbc.gridx = 0; gbc.gridy = 6;
        panel.add(new JLabel("BLKSIZE:"), gbc);
        gbc.gridx = 1;
        JSpinner blksizeSpinner = new JSpinner(new SpinnerNumberModel(27920, 0, 32760, 80));
        blksizeSpinner.setToolTipText("Block Size (0 = System bestimmt, z.B. 27920 = 80 × 349)");
        panel.add(blksizeSpinner, gbc);

        // Primary / Secondary
        gbc.gridx = 0; gbc.gridy = 7;
        panel.add(new JLabel("Primary (Tracks):"), gbc);
        gbc.gridx = 1;
        JSpinner primarySpinner = new JSpinner(new SpinnerNumberModel(10, 1, 99999, 1));
        panel.add(primarySpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 8;
        panel.add(new JLabel("Secondary (Tracks):"), gbc);
        gbc.gridx = 1;
        JSpinner secondarySpinner = new JSpinner(new SpinnerNumberModel(5, 0, 99999, 1));
        panel.add(secondarySpinner, gbc);

        // Directory Blocks (only for PDS)
        gbc.gridx = 0; gbc.gridy = 9;
        JLabel dirLabel = new JLabel("Dir. Blocks:");
        panel.add(dirLabel, gbc);
        gbc.gridx = 1;
        JSpinner dirSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 9999, 1));
        dirSpinner.setToolTipText("Anzahl Directory Blocks (nur für PDS)");
        panel.add(dirSpinner, gbc);

        // Toggle dir blocks visibility based on DSORG
        dsorgCombo.addActionListener(e -> {
            boolean isPO = "PO".equals(dsorgCombo.getSelectedItem());
            dirLabel.setEnabled(isPO);
            dirSpinner.setEnabled(isPO);
        });

        int result = JOptionPane.showConfirmDialog(mainPanel, panel,
                "Neues Mainframe-Dataset anlegen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        String dsName = buildMvsDatasetName(nameField.getText().trim(), baseQualifier);
        if (dsName.isEmpty()) return;

        String dsorg = (String) dsorgCombo.getSelectedItem();
        String recfm = (String) recfmCombo.getSelectedItem();
        int lrecl = (Integer) lreclSpinner.getValue();
        int blksize = (Integer) blksizeSpinner.getValue();
        int primary = (Integer) primarySpinner.getValue();
        int secondary = (Integer) secondarySpinner.getValue();
        int dirBlocks = "PO".equals(dsorg) ? (Integer) dirSpinner.getValue() : 0;

        // Quote dataset name for MVS FTP (single quotes = absolute)
        String quoted = "'" + dsName + "'";

        try {
            // Use MKD with SITE commands for allocation
            // The standard approach: send SITE commands before MKD
            fileService.createDirectory(quoted);

            JOptionPane.showMessageDialog(mainPanel,
                    "Dataset angelegt: " + dsName
                            + "\n\nDSOG=" + dsorg + ", RECFM=" + recfm
                            + ", LRECL=" + lrecl + ", BLKSIZE=" + blksize
                            + "\nPrimary=" + primary + " Tracks, Secondary=" + secondary + " Tracks"
                            + (dirBlocks > 0 ? "\nDir. Blocks=" + dirBlocks : ""),
                    "Dataset angelegt", JOptionPane.INFORMATION_MESSAGE);

            updateFileList();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Fehler beim Anlegen des Datasets:\n" + ex.getMessage()
                            + "\n\nHinweis: Je nach FTP-Server-Konfiguration müssen"
                            + "\nDCB-Parameter ggf. über JCL oder ISPF angelegt werden.",
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Create a new file.
     * On Mainframe (mvsMode): creates a member in the current PDS, or a sequential dataset.
     * On normal FTP: creates an empty file.
     */
    private void createNewFile() {
        String currentPath = browserState.getCurrentPath();

        if (mvsMode) {
            createMainframeMember(currentPath);
        } else {
            String name = JOptionPane.showInputDialog(mainPanel,
                    "Name der neuen Datei:", "Neue Datei", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.trim().isEmpty()) return;

            try {
                String target = navigator.childOf(currentPath, name.trim());
                FilePayload payload = FilePayload.fromBytes(new byte[0], Charset.defaultCharset(), false);
                fileService.writeFile(target, payload);
                updateFileList();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainPanel,
                        "Fehler:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Create a new member in a PDS on the Mainframe, or a sequential dataset.
     */
    private void createMainframeMember(String currentPath) {
        // Check if the current listing actually contains members (= is a PDS)
        // If only directories are shown, this path is just a qualifier, not a PDS
        boolean hasMembers = false;
        boolean hasOnlyDirs = true;
        for (FileNode fn : currentDirectoryNodes) {
            if (!fn.isDirectory()) {
                hasMembers = true;
                hasOnlyDirs = false;
                break;
            }
        }

        if (!hasMembers && !currentDirectoryNodes.isEmpty() && hasOnlyDirs) {
            int answer = JOptionPane.showConfirmDialog(mainPanel,
                    "<html>Der aktuelle Pfad <b>" + currentPath + "</b> enthält nur Sub-Datasets,<br>"
                            + "ist also kein Partitioned Data Set (PDS).<br><br>"
                            + "Ein Member kann nur in einem PDS angelegt werden.<br>"
                            + "Soll zuerst ein neues PDS als Dataset angelegt werden?</html>",
                    "Kein PDS", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                createMainframeDataset(currentPath);
            }
            return;
        }

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Member name
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Member-Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        JTextField memberField = new JTextField("", 20);
        memberField.setToolTipText("Max. 8 Zeichen, z.B. NEWMEMBR");
        panel.add(memberField, gbc);

        // Info label — show what will be created
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        String baseQualifier = resolveBaseQualifier(currentPath);
        String info = !baseQualifier.isEmpty()
                ? "<html><small>Erstellt Member in PDS: <b>" + baseQualifier + "</b></small></html>"
                : "<html><small>Kein PDS ausgewählt — ein neues Sequential Dataset wird angelegt.</small></html>";
        panel.add(new JLabel(info), gbc);

        int result = JOptionPane.showConfirmDialog(mainPanel, panel,
                "Neues Mainframe-Member anlegen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        String memberName = memberField.getText().trim().toUpperCase();
        if (memberName.isEmpty()) return;

        // Truncate to 8 chars (PDS member limit)
        if (memberName.length() > 8) {
            memberName = memberName.substring(0, 8);
        }

        try {
            // Explicitly build DATASET(MEMBER) format — don't rely on childOf() heuristic
            String target;
            if (currentPath != null && !currentPath.isEmpty()) {
                String unquoted = resolveBaseQualifier(currentPath);
                target = "'" + unquoted + "(" + memberName + ")" + "'";
            } else {
                target = "'" + memberName + "'";
            }
            FilePayload payload = FilePayload.fromBytes(new byte[0], Charset.defaultCharset(), false);
            fileService.writeFile(target, payload);
            updateFileList();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Fehler beim Anlegen:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Delete selected files/folders with confirmation dialog.
     * Supports multi-selection.
     */
    private void deleteSelectedEntries() {
        List<FileNode> selected = collectSelectedFileNodes();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Bitte erst Dateien oder Ordner auswählen.", "Hinweis", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Build confirmation message
        StringBuilder msg = new StringBuilder();
        if (selected.size() == 1) {
            FileNode node = selected.get(0);
            msg.append("Wirklich löschen?\n\n");
            msg.append(node.isDirectory() ? "📁 " : "📄 ").append(node.getName());
        } else {
            msg.append(selected.size()).append(" Einträge wirklich löschen?\n\n");
            int dirCount = 0;
            int fileCount = 0;
            for (FileNode fn : selected) {
                if (fn.isDirectory()) dirCount++;
                else fileCount++;
            }
            if (dirCount > 0) msg.append("📁 ").append(dirCount).append(" Ordner\n");
            if (fileCount > 0) msg.append("📄 ").append(fileCount).append(" Dateien\n");
            msg.append("\n");
            for (int i = 0; i < Math.min(selected.size(), 8); i++) {
                msg.append("  • ").append(selected.get(i).getName()).append("\n");
            }
            if (selected.size() > 8) {
                msg.append("  … und ").append(selected.size() - 8).append(" weitere\n");
            }
        }

        int confirm = JOptionPane.showConfirmDialog(mainPanel, msg.toString(),
                "Löschen bestätigen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        String currentPath = browserState.getCurrentPath();
        int success = 0;
        int failed = 0;
        StringBuilder errors = new StringBuilder();

        for (FileNode node : selected) {
            try {
                String target = navigator.childOf(currentPath, node.getName());
                if (fileService.delete(target)) {
                    success++;
                } else {
                    failed++;
                    errors.append("• ").append(node.getName()).append(" — Löschen fehlgeschlagen\n");
                }
            } catch (Exception e) {
                failed++;
                errors.append("• ").append(node.getName()).append(" — ").append(e.getMessage()).append("\n");
            }
        }

        if (failed > 0) {
            JOptionPane.showMessageDialog(mainPanel,
                    success + " gelöscht, " + failed + " fehlgeschlagen:\n\n" + errors.toString(),
                    "Löschen", JOptionPane.WARNING_MESSAGE);
        }

        updateFileList();
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    /**
     * Resolve the base qualifier from an MVS path for auto-prefixing.
     * Strips quotes, wildcards, and trailing separators.
     * Example: "'USR1.DATA'" → "USR1.DATA", "USR1.*" → "USR1"
     */
    private static String resolveBaseQualifier(String path) {
        if (path == null || path.trim().isEmpty()) return "";
        String base = path.toUpperCase().trim();
        // Strip surrounding quotes
        if (base.startsWith("'") && base.endsWith("'") && base.length() > 2) {
            base = base.substring(1, base.length() - 1);
        }
        // Strip trailing wildcards and separators
        while (base.endsWith(".*") || base.endsWith("*") || base.endsWith(".")) {
            if (base.endsWith(".*")) base = base.substring(0, base.length() - 2);
            else if (base.endsWith("*")) base = base.substring(0, base.length() - 1);
            else if (base.endsWith(".")) base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * Build a full MVS dataset name from user input and a resolved base qualifier.
     * If input contains a dot, it's treated as a fully qualified name.
     * Otherwise, the base qualifier is prepended automatically.
     */
    private static String buildMvsDatasetName(String input, String baseQualifier) {
        String upper = input.toUpperCase().trim();
        if (upper.contains(".")) {
            return upper;
        }
        if (baseQualifier == null || baseQualifier.isEmpty()) {
            return upper;
        }
        return baseQualifier + "." + upper;
    }

    private FileNode findNodeByName(String name) {
        if (name == null) return null;
        for (FileNode n : currentDirectoryNodes) {
            if (name.equals(n.getName())) {
                return n;
            }
        }
        return null;
    }

    private VirtualResource buildResourceForPath(String path, VirtualResourceKind kind) {
        if (resource == null) {
            return new VirtualResource(de.bund.zrb.files.path.VirtualResourceRef.of(path), kind, path, true);
        }
        return new VirtualResource(de.bund.zrb.files.path.VirtualResourceRef.of(path),
                kind,
                path,
                resource.getBackendType(),
                resource.getFtpState());
    }

    private static String extractExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
