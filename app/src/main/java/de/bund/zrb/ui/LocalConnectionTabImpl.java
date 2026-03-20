package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.indexing.model.SourceType;
import de.bund.zrb.indexing.ui.IndexingSidebar;
import de.bund.zrb.model.Settings;
import de.bund.zrb.security.SecurityFilterService;
import de.bund.zrb.service.FtpSourceCacheService;
import de.bund.zrb.service.LocalSourceCacheService;
import de.zrb.bund.newApi.ui.ConnectionTab;
import de.zrb.bund.newApi.ui.FindBarPanel;
import de.zrb.bund.newApi.ui.Navigable;

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
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class LocalConnectionTabImpl implements ConnectionTab, Navigable {


    /** Sort modes for file list. */
    enum SortMode {
        NAME_ASC("Name ↑"), NAME_DESC("Name ↓"),
        SIZE_DESC("Größe ↓"), DATE_DESC("Datum ↓"), EXT("Erweiterung");
        final String label;
        SortMode(String l) { this.label = l; }
        @Override public String toString() { return label; }
    }

    private final FileService fileService;
    private final LocalSourceCacheService cacheService;
    private final JPanel mainPanel;
    private final JTextField pathField = new JTextField();
    private DefaultListModel<Object> listModel = new DefaultListModel<Object>();
    private final JList<Object> fileList;
    private final TabbedPaneManager tabbedPaneManager;
    private final DocumentPreviewOpener previewOpener;

    private final FindBarPanel searchBar = new FindBarPanel("Regex-Filter\u2026");
    private final JLabel overlayLabel = new JLabel();
    private final JPanel listContainer = new JPanel();
    private final JLabel statusLabel = new JLabel(" ");

    // Full data for filtering
    private List<FileNode> currentNodes = new ArrayList<FileNode>();

    // Navigation history
    private final List<String> backHistory = new ArrayList<String>();
    private final List<String> forwardHistory = new ArrayList<String>();
    private JButton backButton;
    private JButton forwardButton;

    // Sidebar
    private IndexingSidebar indexingSidebar;
    private JToggleButton detailsButton;
    private boolean sidebarVisible = false;

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

    // State persistence prefix
    private static final String STATE_PREFIX = "local.nav.";

    // Listener for security filter changes → re-trigger auto-prefetch
    private final Runnable securityChangeListener = new Runnable() {
        @Override
        public void run() {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    if (!currentNodes.isEmpty()) {
                        triggerSourcePrefetch();
                    }
                }
            });
        }
    };

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    public LocalConnectionTabImpl(TabbedPaneManager tabbedPaneManager) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.previewOpener = new DocumentPreviewOpener(tabbedPaneManager);
        this.mainPanel = new JPanel(new BorderLayout());
        this.fileList = new JList<Object>(listModel);
        this.fileService = new FileServiceFactory().createLocal();
        this.cacheService = LocalSourceCacheService.getInstance();

        restoreNavigatorState();
        initUI();
        loadDirectory(defaultRootPath(), false);
    }

    private void initUI() {
        // ── Path panel ──
        JPanel pathPanel = new JPanel(new BorderLayout());

        JButton refreshButton = new JButton("🔄");
        refreshButton.setToolTipText("Aktuellen Pfad neu laden");
        refreshButton.setMargin(new Insets(0, 0, 0, 0));
        refreshButton.setFont(refreshButton.getFont().deriveFont(Font.PLAIN, 18f));
        refreshButton.addActionListener(e -> loadDirectory(pathField.getText()));

        backButton = new JButton("⏴");
        backButton.setToolTipText("Zurück zum vorherigen Verzeichnis");
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
        fileList.setCellRenderer(new LocalCellRenderer());
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
        extensionTree.setCellRenderer(new LocalTreeCellRenderer());
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

        // ── Sidebar setup ──
        indexingSidebar = new IndexingSidebar(SourceType.LOCAL);
        indexingSidebar.setVisible(sidebarVisible);
        indexingSidebar.setCustomIndexAction(this::indexSelectedOrAll);
        indexingSidebar.setCustomStatusSupplier(this::computeLocalCacheStatus);
        indexingSidebar.setSecurityGroup("LOCAL");
        indexingSidebar.setSecurityPathSupplier(this::getSelectedPrefixedPaths);
        indexingSidebar.setClearCacheAction(this::clearDirectoryCache);

        // Re-trigger auto-prefetch when security rules change (e.g. user whitelists a path)
        SecurityFilterService.getInstance().addChangeListener(securityChangeListener);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(listContainer, BorderLayout.CENTER);
        mainPanel.add(indexingSidebar, BorderLayout.EAST);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        updateNavigationButtons();
    }

    // ═══════════════════════════════════════════════════════════
    //  Navigator Toolbar
    // ═══════════════════════════════════════════════════════════

    private JPanel createNavigatorToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        toolbar.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));

        // Group by Extension toggle
        groupToggle = new JToggleButton("📁");
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
        JButton sortButton = new JButton("↕");
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
        JToggleButton detailsToggle = new JToggleButton("📋");
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
        JButton collapseButton = new JButton("⏶");
        collapseButton.setToolTipText("Alle zuklappen");
        collapseButton.setMargin(new Insets(1, 4, 1, 4));
        collapseButton.setFocusable(false);
        collapseButton.addActionListener(e -> collapseAllTreeNodes());
        toolbar.add(collapseButton);

        JButton expandButton = new JButton("⏷");
        expandButton.setToolTipText("Alle aufklappen");
        expandButton.setMargin(new Insets(1, 4, 1, 4));
        expandButton.setFocusable(false);
        expandButton.addActionListener(e -> expandAllTreeNodes());
        toolbar.add(expandButton);

        toolbar.add(Box.createHorizontalStrut(8));

        // Hide Blacklisted toggle
        JToggleButton hideBlacklistedToggle = new JToggleButton("🔒");
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
        JButton newFolderButton = new JButton("📂⁺");
        newFolderButton.setToolTipText("Neuen Ordner anlegen");
        newFolderButton.setMargin(new Insets(1, 4, 1, 4));
        newFolderButton.setFocusable(false);
        newFolderButton.addActionListener(e -> createNewFolder());
        toolbar.add(newFolderButton);

        // New File
        JButton newFileButton = new JButton("📄⁺");
        newFileButton.setToolTipText("Neue Datei anlegen");
        newFileButton.setMargin(new Insets(1, 4, 1, 4));
        newFileButton.setFocusable(false);
        newFileButton.addActionListener(e -> createNewFile());
        toolbar.add(newFileButton);

        toolbar.add(Box.createHorizontalStrut(4));

        // Delete
        JButton deleteButton = new JButton("🗑");
        deleteButton.setToolTipText("Ausgewählte Einträge löschen");
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
        return "🖥 Dieser Computer";
    }

    @Override
    public String getTooltip() {
        return "Lokales Dateisystem";
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        SecurityFilterService.getInstance().removeChangeListener(securityChangeListener);
        try {
            fileService.close();
        } catch (Exception ignore) {
            // ignore
        }
        cacheService.shutdown();
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
    public void markAsChanged() {
        // not used
    }

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

    @Override
    public void searchFor(String searchPattern) {
        if (searchPattern == null) return;
        searchBar.setText(searchPattern.trim());
        applyFilter();
    }

    // ═══════════════════════════════════════════════════════════
    //  Navigation
    // ═══════════════════════════════════════════════════════════

    public void loadDirectory(String path) {
        loadDirectory(path, true);
    }

    private void loadDirectory(String path, boolean addToHistory) {
        String targetPath = normalizePath(path);
        String currentPath = pathField.getText();
        if (addToHistory && !currentPath.isEmpty() && !currentPath.equals(targetPath)) {
            backHistory.add(currentPath);
            forwardHistory.clear();
        }
        pathField.setText(targetPath);
        updateNavigationButtons();
        updateFileList();
        tabbedPaneManager.refreshStarForTab(this);
        if (sidebarVisible) {
            indexingSidebar.setCurrentPath(targetPath);
        }
    }

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
                loadDirectory(node.getPath());
                return;
            }
            if (ctrlDown) {
                openDocumentPreview(node.getPath(), node.getName());
            } else {
                openLocalFile(node.getPath());
            }
        }
    }

    private void handleTreeDoubleClick() {
        handleItemActivation(false);
    }

    @Override
    public void navigateBack() {
        if (backHistory.isEmpty()) {
            return;
        }
        String currentPath = normalizePath(pathField.getText());
        forwardHistory.add(currentPath);
        String previousPath = backHistory.remove(backHistory.size() - 1);
        pathField.setText(previousPath);
        updateNavigationButtons();
        updateFileList();
        tabbedPaneManager.refreshStarForTab(this);
    }

    @Override
    public void navigateForward() {
        if (forwardHistory.isEmpty()) {
            return;
        }
        String currentPath = normalizePath(pathField.getText());
        backHistory.add(currentPath);
        String nextPath = forwardHistory.remove(forwardHistory.size() - 1);
        pathField.setText(nextPath);
        updateNavigationButtons();
        updateFileList();
        tabbedPaneManager.refreshStarForTab(this);
    }

    @Override
    public boolean canNavigateBack() {
        return !backHistory.isEmpty();
    }

    @Override
    public boolean canNavigateForward() {
        return !forwardHistory.isEmpty();
    }

    private void updateNavigationButtons() {
        if (backButton != null) {
            backButton.setEnabled(!backHistory.isEmpty());
        }
        if (forwardButton != null) {
            forwardButton.setEnabled(!forwardHistory.isEmpty());
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

    private void updateSidebarInfo() {
        try {
            String currentPath = pathField.getText();
            indexingSidebar.setCurrentPath(currentPath);

            List<FileNode> sel = collectSelectedFileNodes();
            if (sel.isEmpty()) {
                indexingSidebar.setScopeInfo("Alle " + currentNodes.size() + " Einträge");
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
            System.err.println("[LocalConnectionTab] Error updating sidebar info: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Context Menu & Selection
    // ═══════════════════════════════════════════════════════════

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

        JMenuItem newFolderItem = new JMenuItem("📂 Neuer Ordner…");
        newFolderItem.addActionListener(ev -> createNewFolder());
        menu.add(newFolderItem);

        JMenuItem newFileItem = new JMenuItem("📄 Neue Datei…");
        newFileItem.addActionListener(ev -> createNewFile());
        menu.add(newFileItem);

        List<FileNode> sel = collectSelectedFileNodes();
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
                List<FileNode> nodes = fileService.list(pathField.getText());

                SwingUtilities.invokeLater(() -> {
                    currentNodes = nodes == null ? new ArrayList<FileNode>() : nodes;
                    applyFilter();

                    if (currentNodes.isEmpty()) {
                        showOverlayMessage("Keine Einträge gefunden", Color.ORANGE.darker());
                    } else {
                        hideOverlay();
                    }

                    statusLabel.setText(currentNodes.size() + " Einträge");

                    if (sidebarVisible) updateSidebarInfo();

                    // Auto-prefetch text files for Lucene indexing
                    triggerSourcePrefetch();
                });
            } catch (Exception e) {
                System.err.println("[LocalConnectionTab] Error loading directory: " + e.getMessage());
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
        for (FileNode node : currentNodes) {
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
        if (hideBlacklisted) {
            SecurityFilterService sfs = SecurityFilterService.getInstance();
            String currentPath = pathField.getText();
            List<FileNode> allowed = new ArrayList<FileNode>();
            for (FileNode node : filtered) {
                String prefixedPath = "local://" + currentPath + "/" + node.getName();
                if (sfs.isAllowed("LOCAL", prefixedPath)) {
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
        Collections.sort(items, new Comparator<FileNode>() {
            @Override
            public int compare(FileNode a, FileNode b) {
                // Directories always first
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
        expandAllTreeNodes();
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

    /** List cell renderer — shows icon + name + optional details (size, date). */
    private class LocalCellRenderer extends DefaultListCellRenderer {
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
            }
            return this;
        }
    }

    /** Tree cell renderer for grouped view. */
    private class LocalTreeCellRenderer extends DefaultTreeCellRenderer {
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
     * Enables SearchEverywhere to find local files by content.
     * Respects SecurityFilterService — skips if path is not allowed.
     */
    private void triggerSourcePrefetch() {
        String currentPath = pathField.getText();
        if (currentPath == null || currentPath.isEmpty()) return;
        if (cacheService.isPrefetching(currentPath)) return;

        // Filter text files by security rules (file-level check).
        // This handles both directory-level and file-level whitelist entries,
        // and respects individual blacklist entries within an allowed directory.
        SecurityFilterService sfs = SecurityFilterService.getInstance();
        String pathPrefix = "local://" + currentPath.replace('\\', '/');
        List<FileNode> textFiles = getTextFileNodes();
        List<FileNode> allowedFiles = new ArrayList<FileNode>();
        for (FileNode fn : textFiles) {
            String filePath = pathPrefix + "/" + fn.getName();
            if (sfs.isAllowed("LOCAL", filePath)) {
                allowedFiles.add(fn);
            }
        }
        if (allowedFiles.isEmpty()) return;

        if (sidebarVisible) {
            indexingSidebar.setCurrentPath(currentPath);
            indexingSidebar.setScopeInfo("Auto-Prefetch: " + allowedFiles.size() + " Textdateien");
        }

        cacheService.prefetchDirectory(currentPath, allowedFiles,
                new LocalSourceCacheService.PrefetchCallback() {
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
                        statusLabel.setText(currentNodes.size() + " Einträge");
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

        if (filesToIndex.isEmpty()) {
            indexingSidebar.updateError("Keine Textdateien gefunden");
            return;
        }

        String currentPath = pathField.getText();
        indexingSidebar.setCurrentPath(currentPath);
        if (wasSelected) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(filesToIndex.size(), 4); i++) {
                if (i > 0) sb.append(", ");
                sb.append(filesToIndex.get(i).getName());
            }
            if (filesToIndex.size() > 4) sb.append(" … (+" + (filesToIndex.size() - 4) + ")");
            indexingSidebar.setScopeInfo("Auswahl: " + filesToIndex.size() + " Dateien");
        } else {
            indexingSidebar.setScopeInfo("Alle " + filesToIndex.size() + " Textdateien");
        }

        cacheService.prefetchDirectory(currentPath, filesToIndex,
                new LocalSourceCacheService.PrefetchCallback() {
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

    /** Collect only text files (non-directory, text extension) from current directory. */
    private List<FileNode> getTextFileNodes() {
        List<FileNode> result = new ArrayList<FileNode>();
        for (FileNode node : currentNodes) {
            if (!node.isDirectory() && FtpSourceCacheService.isTextFile(node.getName())) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Compute cache-aware status for the IndexingSidebar's custom status supplier.
     * Returns [statusText, colorHex, itemCountText, lastIndexedText].
     */
    private String[] computeLocalCacheStatus() {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
        String currentPath = pathField.getText();

        if (currentPath == null || currentPath.isEmpty()) {
            return new String[]{"⬜ Nicht gecacht", "#999999", "-", "-"};
        }

        int memCached = cacheService.getMemoryCacheSize(currentPath);
        int h2Cached = cacheService.getH2CacheSize(currentPath);
        boolean prefetching = cacheService.isPrefetching(currentPath);
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

    /**
     * Clear cached local sources for the current directory.
     */
    private void clearDirectoryCache() {
        String currentPath = pathField.getText();
        if (currentPath == null || currentPath.isEmpty()) {
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
            cacheService.invalidateDirectory(currentPath);
            statusLabel.setText("Cache für " + currentPath + " geleert");
            if (sidebarVisible) {
                indexingSidebar.refreshStatus();
            }
        }
    }

    /**
     * Build prefixed paths from the current selection (for SecurityFilterService).
     * Format: "local://path/to/file"
     */
    private List<String> getSelectedPrefixedPaths() {
        List<FileNode> selected = collectSelectedFileNodes();
        String currentPath = pathField.getText();

        if (selected.isEmpty()) {
            return Collections.singletonList("local://" + currentPath.replace('\\', '/'));
        }

        List<String> paths = new ArrayList<String>();
        for (FileNode fn : selected) {
            String fullPath = fn.getPath();
            if (fullPath == null || fullPath.isEmpty()) {
                fullPath = joinPath(currentPath, fn.getName());
            }
            paths.add("local://" + fullPath.replace('\\', '/'));
        }
        return paths;
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
    //  Status Bar
    // ═══════════════════════════════════════════════════════════

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());

        // Filter bar
        searchBar.getTextField().setToolTipText("<html>Regex-Filter f\u00fcr Dateinamen<br>Beispiel: <code>\\.JCL$</code></html>");

        searchBar.getTextField().getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        // Status
        statusLabel.setForeground(Color.GRAY);

        statusBar.add(searchBar, BorderLayout.CENTER);
        statusBar.add(statusLabel, BorderLayout.EAST);

        return statusBar;
    }

    // ═══════════════════════════════════════════════════════════
    //  File Operations
    // ═══════════════════════════════════════════════════════════

    private void createNewFile() {
        String name = JOptionPane.showInputDialog(mainPanel, "Name der neuen Datei:", "Neue Datei", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;

        try {
            String target = joinPath(pathField.getText(), name);
            FilePayload payload = FilePayload.fromBytes(new byte[0], Charset.defaultCharset(), false);
            fileService.writeFile(target, payload);
            loadDirectory(pathField.getText());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createNewFolder() {
        String name = JOptionPane.showInputDialog(mainPanel, "Name des neuen Ordners:", "Neuer Ordner", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;

        try {
            String target = joinPath(pathField.getText(), name.trim());
            if (fileService.createDirectory(target)) {
                loadDirectory(pathField.getText());
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Ordner konnte nicht angelegt werden.", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler beim Anlegen des Ordners:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedEntries() {
        List<FileNode> selected = collectSelectedFileNodes();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "Bitte erst Einträge auswählen.");
            return;
        }

        StringBuilder fileNames = new StringBuilder();
        for (int i = 0; i < Math.min(selected.size(), 5); i++) {
            fileNames.append("• ").append(selected.get(i).getName()).append("\n");
        }
        if (selected.size() > 5) {
            fileNames.append("… und ").append(selected.size() - 5).append(" weitere\n");
        }

        int confirm = JOptionPane.showConfirmDialog(mainPanel,
                "Wirklich " + selected.size() + " Eintrag/Einträge löschen?\n\n" + fileNames,
                "Löschen bestätigen", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        String currentPath = pathField.getText();
        int success = 0;
        int failed = 0;
        StringBuilder errors = new StringBuilder();

        for (FileNode node : selected) {
            try {
                String target = joinPath(currentPath, node.getName());
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

        loadDirectory(currentPath);
    }

    private void openLocalFile(String path) {
        try {
            FilePayload payload = fileService.readFile(path);
            // IMPORTANT: Use getEditorText() for proper RECORD_STRUCTURE handling
            String content = payload.getEditorText();
            VirtualResource resource = new VirtualResource(de.bund.zrb.files.path.VirtualResourceRef.of(path), VirtualResourceKind.FILE, path, true);
            tabbedPaneManager.openFileTab(resource, content, null, null, false);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler beim Öffnen:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openDocumentPreview(String path, String fileName) {
        previewOpener.openPreviewAsync(fileService, path, fileName, mainPanel);
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private String defaultRootPath() {
        if (File.separatorChar == '\\') {
            return "C:\\";
        }
        return "/";
    }

    private String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return defaultRootPath();
        }
        Path p = Paths.get(path.trim());
        return p.toAbsolutePath().normalize().toString();
    }

    private String joinPath(String parent, String name) {
        Path p = Paths.get(normalizePath(parent)).resolve(name);
        return p.toAbsolutePath().normalize().toString();
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
        if (upper.equals("JAVA")) return "☕";
        if (upper.equals("PY")) return "🐍";
        if (upper.equals("JS") || upper.equals("TS")) return "📜";
        if (upper.equals("MD")) return "📖";
        if (upper.equals("ZIP") || upper.equals("JAR") || upper.equals("GZ") || upper.equals("TAR")) return "📦";
        if (upper.equals("EXE") || upper.equals("BAT") || upper.equals("SH") || upper.equals("CMD")) return "▶";
        if (upper.equals("PNG") || upper.equals("JPG") || upper.equals("JPEG") || upper.equals("GIF") || upper.equals("BMP") || upper.equals("SVG")) return "🖼";
        return "📄";
    }
}
