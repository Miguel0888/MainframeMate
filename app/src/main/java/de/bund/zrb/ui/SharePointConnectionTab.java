package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.security.SecurityFilterService;
import de.bund.zrb.sharepoint.SharePointCacheService;
import de.bund.zrb.sharepoint.SharePointAuthenticator;
import de.bund.zrb.sharepoint.SharePointPathUtil;
import de.bund.zrb.sharepoint.SharePointSite;
import de.bund.zrb.sharepoint.SharePointSiteStore;
import de.zrb.bund.api.Bookmarkable;
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
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SharePoint connection tab — mounts selected SharePoint sites as WebDAV
 * network paths and provides a local-file-system-like browser.
 * <p>
 * The user configures which sites are available via Settings → SharePoint.
 * Each selected site is mapped to a Windows WebDAV UNC path
 * ({@code \\host@SSL\DavWWWRoot\...}) and navigated using the standard
 * {@link FileService} (VfsLocalFileService), exactly like the local
 * file browser tab.
 * <p>
 * Files opened for the first time are automatically cached in H2 + Lucene
 * (via {@link SharePointCacheService}) so they become searchable through
 * SearchEverywhere with the "SP" prefix.
 */
public class SharePointConnectionTab implements ConnectionTab, Navigable, Bookmarkable {

    private static final Logger LOG = Logger.getLogger(SharePointConnectionTab.class.getName());
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    // ── UI components ───────────────────────────────────────
    private final JPanel mainPanel;
    private final JTree siteTree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultListModel<Object> listModel;
    private final JList<Object> fileList;
    private final JTextField pathField;
    private final SearchBarPanel searchBar;
    private final JLabel statusLabel;
    private final JTextArea previewArea;
    private JButton backButton;
    private JButton forwardButton;

    // ── State ───────────────────────────────────────────────
    private final FileService fileService;
    private final SharePointCacheService cacheService = SharePointCacheService.getInstance();
    private final List<SharePointSite> selectedSites = new ArrayList<SharePointSite>();
    private String currentPath = "";
    private String currentSiteName = "";
    private List<FileNode> currentNodes = new ArrayList<FileNode>();
    private final List<String> backHistory = new ArrayList<String>();
    private final List<String> forwardHistory = new ArrayList<String>();

    // Callback for left drawer
    private java.util.function.Consumer<List<de.bund.zrb.ui.drawer.LeftDrawer.RelationEntry>> linksCallback;

    // TabbedPaneManager reference (optional, for opening files in new tabs)
    private TabbedPaneManager tabbedPaneManager;

    public SharePointConnectionTab() {
        this.fileService = new FileServiceFactory().createLocal();
        this.mainPanel = new JPanel(new BorderLayout(0, 2));
        this.listModel = new DefaultListModel<Object>();
        this.fileList = new JList<Object>(listModel);
        this.pathField = new JTextField();
        this.searchBar = new SearchBarPanel("Dateien filtern…", "Dateien filtern…");
        this.statusLabel = new JLabel(" ");
        this.previewArea = new JTextArea();

        // Load selected SharePoint sites from settings
        Settings settings = SettingsHelper.load();
        loadSitesFromSettings(settings);

        // ── Left: Site tree ─────────────────────────────────
        rootNode = new DefaultMutableTreeNode("SharePoint-Sites");
        treeModel = new DefaultTreeModel(rootNode);
        siteTree = new JTree(treeModel);
        siteTree.setRootVisible(true);
        siteTree.setShowsRootHandles(true);
        siteTree.setCellRenderer(new SharePointTreeCellRenderer());
        siteTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) handleTreeDoubleClick();
            }
        });

        // Filter
        JPanel filterPanel = new JPanel(new BorderLayout(4, 0));
        filterPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        filterPanel.add(new JLabel("🔎"), BorderLayout.WEST);
        JTextField treeFilter = new JTextField();
        treeFilter.setToolTipText("Sites filtern…");
        treeFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyTreeFilter(treeFilter.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { applyTreeFilter(treeFilter.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { applyTreeFilter(treeFilter.getText()); }
        });
        filterPanel.add(treeFilter, BorderLayout.CENTER);

        JButton refreshBtn = new JButton("🔄");
        refreshBtn.setToolTipText("Sites neu laden");
        refreshBtn.setMargin(new Insets(2, 6, 2, 6));
        refreshBtn.addActionListener(e -> reloadSites());
        filterPanel.add(refreshBtn, BorderLayout.EAST);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(filterPanel, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(siteTree), BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(240, 0));

        // ── Center: File browser ────────────────────────────

        // Path + nav bar
        JPanel pathPanel = new JPanel(new BorderLayout(4, 0));
        pathPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        backButton = new JButton("◀");
        backButton.setToolTipText("Zurück");
        backButton.setMargin(new Insets(2, 4, 2, 4));
        backButton.setEnabled(false);
        backButton.addActionListener(e -> navigateBack());

        forwardButton = new JButton("▶");
        forwardButton.setToolTipText("Vorwärts");
        forwardButton.setMargin(new Insets(2, 4, 2, 4));
        forwardButton.setEnabled(false);
        forwardButton.addActionListener(e -> navigateForward());

        JButton upButton = new JButton("▲");
        upButton.setToolTipText("Übergeordnetes Verzeichnis");
        upButton.setMargin(new Insets(2, 4, 2, 4));
        upButton.addActionListener(e -> navigateUp());

        navButtons.add(backButton);
        navButtons.add(forwardButton);
        navButtons.add(upButton);

        pathField.addActionListener(e -> loadDirectory(pathField.getText(), true));
        pathPanel.add(navButtons, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);

        // File list
        fileList.setCellRenderer(new FileCellRenderer());
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) handleFileDoubleClick();
            }
        });
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showPreview();
        });

        // Search/filter
        searchBar.getTextField().getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFileFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFileFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFileFilter(); }
        });

        // Preview
        previewArea.setEditable(false);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel filePanel = new JPanel(new BorderLayout());
        filePanel.add(pathPanel, BorderLayout.NORTH);

        JSplitPane filePreviewSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(fileList), new JScrollPane(previewArea));
        filePreviewSplit.setDividerLocation(300);
        filePanel.add(filePreviewSplit, BorderLayout.CENTER);
        filePanel.add(searchBar, BorderLayout.SOUTH);

        // ── Assembly ────────────────────────────────────────
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, filePanel);
        mainSplit.setDividerLocation(240);
        mainSplit.setOneTouchExpandable(true);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        mainPanel.add(mainSplit, BorderLayout.CENTER);
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        // Populate tree
        rebuildTree(selectedSites);

        if (selectedSites.isEmpty()) {
            previewArea.setText("Keine SharePoint-Sites konfiguriert.\n\n"
                    + "M\u00f6glichkeit 1: \u00d6ffnen Sie Einstellungen \u2192 Passw\u00f6rter,\n"
                    + "legen Sie einen Eintrag mit Kategorie \"SP\" und SharePoint-URL an.\n\n"
                    + "M\u00f6glichkeit 2: \u00d6ffnen Sie Einstellungen \u2192 SharePoint,\n"
                    + "geben Sie die Parent-URL an, rufen Sie Links ab und haken Sie die Sites an.");
            statusLabel.setText("\u26a0 Keine Sites konfiguriert \u2013 siehe Einstellungen \u2192 Passw\u00f6rter oder SharePoint");
        } else {
            statusLabel.setText(selectedSites.size() + " SharePoint-Sites verfügbar");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Site tree management
    // ═══════════════════════════════════════════════════════════

    private void rebuildTree(List<SharePointSite> sites) {
        rootNode.removeAllChildren();
        for (SharePointSite site : sites) {
            rootNode.add(new DefaultMutableTreeNode(site));
        }
        treeModel.reload();
        siteTree.expandPath(new TreePath(rootNode.getPath()));
    }

    private void applyTreeFilter(String text) {
        String filter = text != null ? text.trim().toLowerCase() : "";
        rootNode.removeAllChildren();
        for (SharePointSite site : selectedSites) {
            if (filter.isEmpty()
                    || site.getName().toLowerCase().contains(filter)
                    || site.getUrl().toLowerCase().contains(filter)) {
                rootNode.add(new DefaultMutableTreeNode(site));
            }
        }
        treeModel.reload();
        siteTree.expandPath(new TreePath(rootNode.getPath()));
        statusLabel.setText(rootNode.getChildCount() + " von " + selectedSites.size() + " Sites");
    }

    private void reloadSites() {
        Settings settings = SettingsHelper.load();
        selectedSites.clear();
        loadSitesFromSettings(settings);
        rebuildTree(selectedSites);
        statusLabel.setText("✓ " + selectedSites.size() + " SharePoint-Sites geladen");
    }

    /**
     * Load SP sites from {@code passwordEntries} (category "SP").
     * Falls back to legacy {@code sharepointSitesJson} if no SP entries exist.
     */
    private void loadSitesFromSettings(Settings settings) {
        selectedSites.clear();

        // Primary: read from passwordEntries (category "SP")
        for (Settings.PasswordEntryMeta meta : settings.passwordEntries) {
            if (!"SP".equals(meta.category)) continue;
            String url = meta.url;
            if (url == null || url.isEmpty()) continue;
            String name = (meta.displayName != null && !meta.displayName.isEmpty())
                    ? meta.displayName : meta.id;
            selectedSites.add(new SharePointSite(name, url, true));
        }

        // Fallback: if no SP password entries, try legacy JSON
        if (selectedSites.isEmpty()) {
            List<SharePointSite> legacy = SharePointSiteStore.fromJson(settings.sharepointSitesJson);
            for (SharePointSite s : legacy) {
                if (s.isSelected()) selectedSites.add(s);
            }
        }
    }

    private void handleTreeDoubleClick() {
        TreePath path = siteTree.getSelectionPath();
        if (path == null || path.getPathCount() < 2) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObj = node.getUserObject();
        if (userObj instanceof SharePointSite) {
            SharePointSite site = (SharePointSite) userObj;
            currentSiteName = site.getName();
            String uncPath = site.toUncPath();

            // Authenticate before accessing the WebDAV share
            if (!SharePointAuthenticator.ensureAuthenticated(uncPath, mainPanel)) {
                statusLabel.setText("⚠ Anmeldung abgebrochen");
                return;
            }

            loadDirectory(uncPath, true);
            if (tabbedPaneManager != null) tabbedPaneManager.updateTitleFor(this);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Directory navigation (like LocalConnectionTabImpl)
    // ═══════════════════════════════════════════════════════════

    public void loadDirectory(String path, boolean addToHistory) {
        final String targetPath = path != null ? path.trim() : "";
        if (targetPath.isEmpty()) return;

        if (addToHistory && !currentPath.isEmpty() && !currentPath.equals(targetPath)) {
            backHistory.add(currentPath);
            forwardHistory.clear();
        }
        currentPath = targetPath;
        pathField.setText(targetPath);
        updateNavigationButtons();

        statusLabel.setText("⏳ Lade: " + targetPath + "…");
        previewArea.setText("");

        new SwingWorker<List<FileNode>, Void>() {
            @Override
            protected List<FileNode> doInBackground() throws Exception {
                return fileService.list(targetPath);
            }

            @Override
            protected void done() {
                try {
                    List<FileNode> nodes = get();
                    currentNodes = nodes;
                    applyFileFilter();
                    statusLabel.setText(nodes.size() + " Einträge in " + targetPath);

                    // Update left drawer
                    updateLeftDrawer(nodes);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[SP] Failed to list: " + targetPath, e);
                    currentNodes = Collections.emptyList();
                    listModel.clear();
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();

                    // Try authentication if listing failed (auth may have expired)
                    if (targetPath.startsWith("\\\\")) {
                        statusLabel.setText("🔑 Anmeldung erforderlich…");
                        SharePointAuthenticator.resetSession();
                        SwingUtilities.invokeLater(() -> {
                            if (SharePointAuthenticator.ensureAuthenticated(targetPath, mainPanel)) {
                                // Retry after successful authentication
                                loadDirectory(targetPath, false);
                            } else {
                                statusLabel.setText("✗ Anmeldung abgebrochen");
                                previewArea.setText("Anmeldung zu SharePoint wurde abgebrochen.\n\n"
                                        + "Sie können die Zugangsdaten in den Einstellungen → SharePoint konfigurieren.");
                            }
                        });
                        return;
                    }

                    statusLabel.setText("✗ Fehler: " + msg);
                    previewArea.setText("Fehler beim Laden des Verzeichnisses:\n\n" + msg
                            + "\n\nMögliche Ursachen:\n"
                            + "• Der WebClient-Dienst ist nicht gestartet\n"
                            + "• Keine Netzwerkverbindung zur SharePoint-Site\n"
                            + "• Authentifizierung erforderlich (Einstellungen → SharePoint)\n\n"
                            + "Tipp: Starten Sie den WebClient-Dienst:\n"
                            + "  net start WebClient");
                }
            }
        }.execute();
    }

    @Override
    public void navigateBack() {
        if (backHistory.isEmpty()) return;
        forwardHistory.add(currentPath);
        String prev = backHistory.remove(backHistory.size() - 1);
        loadDirectory(prev, false);
    }

    @Override
    public void navigateForward() {
        if (forwardHistory.isEmpty()) return;
        backHistory.add(currentPath);
        String next = forwardHistory.remove(forwardHistory.size() - 1);
        loadDirectory(next, false);
    }

    @Override
    public boolean canNavigateBack() {
        return !backHistory.isEmpty();
    }

    @Override
    public boolean canNavigateForward() {
        return !forwardHistory.isEmpty();
    }

    private void navigateUp() {
        if (currentPath.isEmpty()) return;
        // UNC path: go up one level
        String path = currentPath;
        if (path.endsWith("\\")) path = path.substring(0, path.length() - 1);
        int lastSep = path.lastIndexOf('\\');
        if (lastSep > 2) { // don't go above \\host
            loadDirectory(path.substring(0, lastSep), true);
        }
    }

    private void updateNavigationButtons() {
        backButton.setEnabled(!backHistory.isEmpty());
        forwardButton.setEnabled(!forwardHistory.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════
    //  File list management
    // ═══════════════════════════════════════════════════════════

    private void applyFileFilter() {
        String filter = searchBar.getText().trim().toLowerCase();
        listModel.clear();
        List<FileNode> filtered = new ArrayList<FileNode>();
        for (FileNode n : currentNodes) {
            if (filter.isEmpty() || n.getName().toLowerCase().contains(filter)) {
                filtered.add(n);
            }
        }
        // Sort: directories first, then by name
        Collections.sort(filtered, new Comparator<FileNode>() {
            @Override
            public int compare(FileNode a, FileNode b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (FileNode n : filtered) {
            listModel.addElement(n);
        }
    }

    private void handleFileDoubleClick() {
        Object selected = fileList.getSelectedValue();
        if (!(selected instanceof FileNode)) return;
        FileNode node = (FileNode) selected;

        if (node.isDirectory()) {
            loadDirectory(node.getPath(), true);
        } else {
            openFile(node);
        }
    }

    private void openFile(final FileNode node) {
        if (tabbedPaneManager != null) {
            try {
                FilePayload payload = fileService.readFile(node.getPath());
                String content = payload.getEditorText();

                VirtualResource resource = new VirtualResource(
                        de.bund.zrb.files.path.VirtualResourceRef.of(node.getPath()),
                        VirtualResourceKind.FILE, node.getPath(), true);
                tabbedPaneManager.openFileTab(resource, content, null, null, false);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(mainPanel,
                        "Fehler beim Öffnen:\n" + ex.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // Auto-cache in background
        autoCacheFile(node);
    }

    private void showPreview() {
        Object selected = fileList.getSelectedValue();
        if (!(selected instanceof FileNode)) {
            previewArea.setText("");
            return;
        }
        FileNode node = (FileNode) selected;

        if (node.isDirectory()) {
            previewArea.setText("📁 " + node.getName() + "\n\n(Doppelklick zum Öffnen)");
            return;
        }

        // Show file info
        StringBuilder sb = new StringBuilder();
        sb.append("═══ ").append(node.getName()).append(" ═══\n");
        sb.append("Pfad:     ").append(node.getPath()).append("\n");
        if (node.getSize() > 0) sb.append("Größe:    ").append(formatSize(node.getSize())).append("\n");
        if (node.getLastModifiedMillis() > 0) {
            sb.append("Geändert: ").append(DATE_FMT.format(new Date(node.getLastModifiedMillis()))).append("\n");
        }
        sb.append("\n");

        // Try to load text preview for small text files
        String ext = extractExtension(node.getName()).toLowerCase();
        boolean isText = "txt".equals(ext) || "csv".equals(ext) || "xml".equals(ext)
                || "json".equals(ext) || "html".equals(ext) || "htm".equals(ext)
                || "md".equals(ext) || "log".equals(ext) || "properties".equals(ext);
        if (isText && node.getSize() < 50_000) {
            try {
                FilePayload payload = fileService.readFile(node.getPath());
                String content = new String(payload.getBytes(), Charset.defaultCharset());
                sb.append("── Inhalt ──\n");
                if (content.length() > 5000) {
                    sb.append(content.substring(0, 5000)).append("\n\n… (abgeschnitten)");
                } else {
                    sb.append(content);
                }
            } catch (Exception e) {
                sb.append("(Vorschau nicht verfügbar: ").append(e.getMessage()).append(")");
            }
        } else if (!isText) {
            sb.append("(Binärdatei – keine Textvorschau)");
        } else {
            sb.append("(Datei zu groß für Vorschau)");
        }

        previewArea.setText(sb.toString());
        previewArea.setCaretPosition(0);
    }

    // ═══════════════════════════════════════════════════════════
    //  Auto-caching for search
    // ═══════════════════════════════════════════════════════════

    private void autoCacheFile(final FileNode node) {
        if (node.isDirectory()) return;

        SecurityFilterService sfs = SecurityFilterService.getInstance();
        String spPath = "sp://" + node.getPath();
        if (!sfs.isAllowed("SHAREPOINT", spPath)) {
            LOG.fine("[SP] Skipping cache for blacklisted: " + node.getPath());
            return;
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    FilePayload payload = fileService.readFile(node.getPath());
                    String content = new String(payload.getBytes(), Charset.defaultCharset());
                    String siteName = currentSiteName.isEmpty() ? "SharePoint" : currentSiteName;
                    cacheService.cacheContent(siteName, node.getPath(), node.getName(), content);
                } catch (Exception e) {
                    LOG.log(Level.FINE, "[SP] Cache failed for: " + node.getPath(), e);
                }
                return null;
            }
        }.execute();
    }

    // ═══════════════════════════════════════════════════════════
    //  Left drawer integration
    // ═══════════════════════════════════════════════════════════

    private void updateLeftDrawer(List<FileNode> nodes) {
        if (linksCallback == null) return;
        List<de.bund.zrb.ui.drawer.LeftDrawer.RelationEntry> entries =
                new ArrayList<de.bund.zrb.ui.drawer.LeftDrawer.RelationEntry>();
        for (FileNode n : nodes) {
            String icon = n.isDirectory() ? "📁" : "📄";
            entries.add(new de.bund.zrb.ui.drawer.LeftDrawer.RelationEntry(
                    icon + " " + n.getName(), n.getPath(), "SP_FILE"));
        }
        linksCallback.accept(entries);
    }

    // ═══════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════

    public void setLinksCallback(java.util.function.Consumer<List<de.bund.zrb.ui.drawer.LeftDrawer.RelationEntry>> callback) {
        this.linksCallback = callback;
    }

    public void setTabbedPaneManager(TabbedPaneManager manager) {
        this.tabbedPaneManager = manager;
    }

    /**
     * Navigate to a specific path (e.g. from bookmark or left drawer).
     */
    public void navigateToUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isEmpty()) return;

        // If it's a web URL, convert to UNC
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            pathOrUrl = SharePointPathUtil.toUncPath(pathOrUrl);
        }

        // Detect which site this belongs to
        for (SharePointSite site : selectedSites) {
            String unc = site.toUncPath();
            if (pathOrUrl.startsWith(unc)) {
                currentSiteName = site.getName();
                break;
            }
        }

        // Authenticate before accessing the WebDAV share
        if (pathOrUrl.startsWith("\\\\")) {
            if (!SharePointAuthenticator.ensureAuthenticated(pathOrUrl, mainPanel)) {
                statusLabel.setText("⚠ Anmeldung abgebrochen");
                return;
            }
        }

        File f = new File(pathOrUrl);
        if (f.isDirectory()) {
            loadDirectory(pathOrUrl, true);
        } else if (f.isFile()) {
            // Navigate to parent dir, then select file
            loadDirectory(f.getParent(), true);
        } else {
            loadDirectory(pathOrUrl, true);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String extractExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    private static String getExtensionIcon(String ext) {
        if (ext == null || ext.isEmpty()) return "📄";
        switch (ext.toLowerCase()) {
            case "pdf":  return "📕";
            case "doc": case "docx": return "📘";
            case "xls": case "xlsx": return "📗";
            case "ppt": case "pptx": return "📙";
            case "zip": case "7z": case "tar": case "gz": return "📦";
            case "jpg": case "jpeg": case "png": case "gif": case "bmp": return "🖼";
            case "txt": case "csv": case "log": return "📝";
            case "xml": case "json": case "html": case "htm": return "🌐";
            default: return "📄";
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ConnectionTab interface
    // ═══════════════════════════════════════════════════════════

    @Override
    public String getTitle() {
        if (!currentSiteName.isEmpty()) {
            String display = currentSiteName.length() > 20
                    ? currentSiteName.substring(0, 20) + "…" : currentSiteName;
            return "📊 " + display;
        }
        return "📊 SharePoint";
    }

    @Override
    public String getTooltip() {
        return currentPath.isEmpty() ? "SharePoint-Verbindung" : currentPath;
    }

    @Override
    public JComponent getComponent() { return mainPanel; }

    @Override
    public void onClose() {
        try {
            fileService.close();
        } catch (Exception ignore) {}
        cacheService.shutdown();
    }

    @Override
    public void saveIfApplicable() { /* read-only browsing */ }

    @Override
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < listModel.size(); i++) {
            sb.append(listModel.get(i)).append("\n");
        }
        return sb.toString();
    }

    @Override
    public void markAsChanged() { /* not applicable */ }

    @Override
    public String getPath() {
        return currentPath.isEmpty() ? "sp://" : "sp://" + currentPath;
    }

    @Override
    public Type getType() { return Type.CONNECTION; }

    @Override
    public void focusSearchField() {
        searchBar.focusAndSelectAll();
    }

    @Override
    public void searchFor(String searchPattern) {
        searchBar.setText(searchPattern);
        applyFileFilter();
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem refreshItem = new JMenuItem("🔄 Neu laden");
        refreshItem.addActionListener(e -> {
            if (!currentPath.isEmpty()) loadDirectory(currentPath, false);
        });
        menu.add(refreshItem);

        JMenuItem reloadSitesItem = new JMenuItem("🔄 Sites aktualisieren");
        reloadSitesItem.addActionListener(e -> reloadSites());
        menu.add(reloadSitesItem);

        if (!currentPath.isEmpty()) {
            JMenuItem copyPathItem = new JMenuItem("📋 Pfad kopieren");
            copyPathItem.addActionListener(e -> {
                java.awt.datatransfer.StringSelection sel =
                        new java.awt.datatransfer.StringSelection(currentPath);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            });
            menu.add(copyPathItem);
        }

        menu.addSeparator();

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());
        menu.add(closeItem);

        return menu;
    }

    // ═══════════════════════════════════════════════════════════
    //  Renderers
    // ═══════════════════════════════════════════════════════════

    private static class SharePointTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                       boolean sel, boolean expanded, boolean leaf,
                                                       int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof SharePointSite) {
                    setText("📊 " + ((SharePointSite) userObj).getName());
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (userObj instanceof String) {
                    setText("📊 " + userObj);
                    setFont(getFont().deriveFont(Font.BOLD));
                }
                setIcon(null);
            }
            return this;
        }
    }

    private class FileCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                       int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof FileNode) {
                FileNode node = (FileNode) value;
                String icon = node.isDirectory() ? "📁" : getExtensionIcon(extractExtension(node.getName()));
                StringBuilder sb = new StringBuilder();
                sb.append(icon).append(" ").append(node.getName());
                if (!node.isDirectory()) {
                    if (node.getSize() > 0) sb.append("  (").append(formatSize(node.getSize())).append(")");
                    if (node.getLastModifiedMillis() > 0) {
                        sb.append("  ").append(DATE_FMT.format(new Date(node.getLastModifiedMillis())));
                    }
                }
                setText(sb.toString());
                setFont(getFont().deriveFont(node.isDirectory() ? Font.BOLD : Font.PLAIN));
            }
            if (!isSelected) {
                setBackground(index % 2 == 0 ? Color.WHITE : new Color(245, 245, 250));
            }
            return this;
        }
    }
}

