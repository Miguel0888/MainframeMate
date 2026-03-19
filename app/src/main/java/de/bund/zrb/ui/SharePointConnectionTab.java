package de.bund.zrb.ui;

import de.bund.zrb.browser.Wd4jBrowserSessionAdapter;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.mcpserver.browser.BrowserLauncher;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.model.Settings;
import de.bund.zrb.security.SecurityFilterService;
import de.bund.zrb.sharepoint.SharePointCacheService;
import de.bund.zrb.sharepoint.SharePointSite;
import de.zrb.bund.api.Bookmarkable;
import de.zrb.bund.newApi.browser.NavigationResult;
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
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SharePoint connection tab — lists SharePoint sites extracted from a configured
 * parent page and provides a file-browser-like view of each site's pages.
 * <p>
 * When a page is visited for the first time, its content is automatically cached
 * in H2 + Lucene (via {@link SharePointCacheService}), making it discoverable
 * through SearchEverywhere with the "SP" prefix.
 * <p>
 * Uses the same headless browser infrastructure as {@link BrowserConnectionTab}.
 */
public class SharePointConnectionTab implements ConnectionTab, Bookmarkable {

    private static final Logger LOG = Logger.getLogger(SharePointConnectionTab.class.getName());

    private final JPanel mainPanel;
    private final JTree siteTree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final JTextArea contentArea;
    private final JLabel statusLabel;
    private final JTextField searchField;
    private final JButton refreshButton;

    private BrowserSession browserSession;
    private de.zrb.bund.newApi.browser.BrowserSession sessionAdapter;
    private Thread shutdownHook;

    private final SharePointCacheService cacheService = SharePointCacheService.getInstance();
    private final List<SharePointSite> allSites = new ArrayList<SharePointSite>();
    private String currentSiteName = "";
    private String currentPageUrl = "";
    private String currentPageTitle = "";
    private String currentContent = "";

    // Callback for updating left drawer relations
    private java.util.function.Consumer<List<de.bund.zrb.ui.drawer.LeftDrawer.RelationEntry>> linksCallback;

    public SharePointConnectionTab() {
        mainPanel = new JPanel(new BorderLayout(0, 2));

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
                if (e.getClickCount() == 2) {
                    handleTreeDoubleClick();
                }
            }
        });

        // ── Search / filter ─────────────────────────────────
        searchField = new JTextField();
        searchField.setToolTipText("Sites filtern…");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        JPanel filterPanel = new JPanel(new BorderLayout(4, 0));
        filterPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        filterPanel.add(new JLabel("🔎"), BorderLayout.WEST);
        filterPanel.add(searchField, BorderLayout.CENTER);

        refreshButton = new JButton("🔄");
        refreshButton.setToolTipText("Sites neu laden");
        refreshButton.setMargin(new Insets(2, 6, 2, 6));
        refreshButton.addActionListener(e -> loadSitesInBackground());
        filterPanel.add(refreshButton, BorderLayout.EAST);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(filterPanel, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(siteTree), BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(280, 0));

        // ── Right: Content area ─────────────────────────────
        contentArea = new JTextArea();
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        contentArea.setText("SharePoint-Verbindung wird initialisiert…\n\n"
                + "Konfigurieren Sie die Parent-Seite unter\n"
                + "Einstellungen → SharePoint.");

        // ── Status bar ──────────────────────────────────────
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        // ── Assembly ────────────────────────────────────────
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, new JScrollPane(contentArea));
        splitPane.setDividerLocation(280);
        splitPane.setOneTouchExpandable(true);

        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(statusLabel, BorderLayout.SOUTH);
    }

    // ═══════════════════════════════════════════════════════════
    //  Browser lifecycle
    // ═══════════════════════════════════════════════════════════

    /**
     * Launch the headless browser (called from SwingWorker background thread).
     */
    public void launchBrowser() throws Exception {
        Settings settings = SettingsHelper.load();

        String browserPath = resolveBrowserPath(settings);
        boolean headless = settings.browserHeadless;
        int debugPort = settings.browserDebugPort;

        LOG.info("[SP] Launching browser: " + browserPath + " (headless=" + headless + ")");

        browserSession = new BrowserSession();
        browserSession.launchAndConnect(browserPath, new ArrayList<String>(), headless, 30000L, debugPort);
        sessionAdapter = new Wd4jBrowserSessionAdapter(browserSession);

        LOG.info("[SP] Browser connected, contextId=" + browserSession.getContextId());

        installShutdownHook();
    }

    /**
     * Load SharePoint sites from the configured parent page.
     */
    public void loadSitesInBackground() {
        Settings settings = SettingsHelper.load();
        final String parentUrl = settings.sharepointParentPageUrl;

        if (parentUrl == null || parentUrl.trim().isEmpty()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    statusLabel.setText("⚠ Keine Parent-Seite konfiguriert. Bitte unter Einstellungen → SharePoint konfigurieren.");
                    contentArea.setText("Keine Parent-Seite konfiguriert.\n\n"
                            + "Öffnen Sie Einstellungen → SharePoint und geben Sie die URL\n"
                            + "der Seite an, auf der Ihre SharePoint-Links aufgelistet sind.");
                }
            });
            return;
        }

        if (sessionAdapter == null) {
            statusLabel.setText("⚠ Browser nicht verbunden.");
            return;
        }

        statusLabel.setText("⏳ Lade SharePoint-Sites von: " + parentUrl + "…");

        new SwingWorker<List<SharePointSite>, Void>() {
            @Override
            protected List<SharePointSite> doInBackground() throws Exception {
                sessionAdapter.navigate(parentUrl);
                Thread.sleep(2000); // let page render

                String html = sessionAdapter.getDomSnapshot();
                return extractSharePointLinks(html, parentUrl);
            }

            @Override
            protected void done() {
                try {
                    List<SharePointSite> sites = get();
                    allSites.clear();
                    allSites.addAll(sites);
                    rebuildTree(sites);
                    statusLabel.setText("✓ " + sites.size() + " SharePoint-Sites gefunden");
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[SP] Failed to load sites", e);
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    statusLabel.setText("✗ Fehler: " + msg);
                    contentArea.setText("Fehler beim Laden der SharePoint-Sites:\n\n" + msg);
                }
            }
        }.execute();
    }

    /**
     * Extract SharePoint links from the parent page HTML.
     * Looks for links containing "sharepoint" in the URL.
     */
    static List<SharePointSite> extractSharePointLinks(String html, String baseUrl) {
        List<SharePointSite> sites = new ArrayList<SharePointSite>();
        if (html == null) return sites;

        Pattern p = Pattern.compile("<a\\s[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        Set<String> seen = new HashSet<String>();

        while (m.find()) {
            String href = m.group(1).trim();
            String label = m.group(2).replaceAll("<[^>]+>", "").trim();

            // Only include SharePoint links
            if (!isSharePointUrl(href)) continue;

            // Resolve relative URLs
            if (!href.startsWith("http://") && !href.startsWith("https://")) {
                href = resolveRelativeUrl(baseUrl, href);
            }

            if (seen.contains(href)) continue;
            seen.add(href);

            // Clean up label
            if (label.isEmpty()) {
                label = extractSiteNameFromUrl(href);
            }
            if (label.length() > 80) {
                label = label.substring(0, 80) + "…";
            }

            sites.add(new SharePointSite(label, href));
        }
        return sites;
    }

    /**
     * Check if a URL looks like a SharePoint URL.
     */
    static boolean isSharePointUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("sharepoint.com")
                || lower.contains("sharepoint.de")
                || lower.contains("/sites/")
                || lower.contains("/_layouts/");
    }

    /**
     * Extract a human-readable site name from a SharePoint URL.
     */
    static String extractSiteNameFromUrl(String url) {
        if (url == null) return "?";
        // Try to extract site name from /sites/SiteName/
        Pattern p = Pattern.compile("/sites/([^/?#]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(url);
        if (m.find()) {
            return m.group(1).replace("%20", " ").replace("-", " ");
        }
        // Fallback: use hostname
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getHost();
        } catch (Exception e) {
            return url.length() > 40 ? url.substring(0, 40) + "…" : url;
        }
    }

    private static String resolveRelativeUrl(String base, String relative) {
        if (relative.startsWith("//")) return "https:" + relative;
        if (relative.startsWith("/")) {
            try {
                java.net.URL baseUrl = new java.net.URL(base);
                return baseUrl.getProtocol() + "://" + baseUrl.getHost()
                        + (baseUrl.getPort() > 0 ? ":" + baseUrl.getPort() : "") + relative;
            } catch (Exception e) {
                return relative;
            }
        }
        int lastSlash = base.lastIndexOf('/');
        return lastSlash >= 0 ? base.substring(0, lastSlash + 1) + relative : base + "/" + relative;
    }

    // ═══════════════════════════════════════════════════════════
    //  Tree management
    // ═══════════════════════════════════════════════════════════

    private void rebuildTree(List<SharePointSite> sites) {
        rootNode.removeAllChildren();
        for (SharePointSite site : sites) {
            rootNode.add(new DefaultMutableTreeNode(site));
        }
        treeModel.reload();
        // Expand root
        siteTree.expandPath(new TreePath(rootNode.getPath()));
    }

    private void applyFilter() {
        String filter = searchField.getText().trim().toLowerCase();
        rootNode.removeAllChildren();
        for (SharePointSite site : allSites) {
            if (filter.isEmpty()
                    || site.getName().toLowerCase().contains(filter)
                    || site.getUrl().toLowerCase().contains(filter)) {
                rootNode.add(new DefaultMutableTreeNode(site));
            }
        }
        treeModel.reload();
        siteTree.expandPath(new TreePath(rootNode.getPath()));
        statusLabel.setText(rootNode.getChildCount() + " von " + allSites.size() + " Sites");
    }

    private void handleTreeDoubleClick() {
        TreePath path = siteTree.getSelectionPath();
        if (path == null || path.getPathCount() < 2) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObj = node.getUserObject();

        if (userObj instanceof SharePointSite) {
            navigateToSite((SharePointSite) userObj);
        } else if (userObj instanceof PageEntry) {
            navigateToPage((PageEntry) userObj);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Site / page navigation
    // ═══════════════════════════════════════════════════════════

    private void navigateToSite(final SharePointSite site) {
        if (sessionAdapter == null) {
            statusLabel.setText("⚠ Browser nicht verbunden.");
            return;
        }

        currentSiteName = site.getName();
        statusLabel.setText("⏳ Lade: " + site.getName() + "…");
        contentArea.setText("Lade " + site.getName() + "…");

        new SwingWorker<SiteData, Void>() {
            @Override
            protected SiteData doInBackground() throws Exception {
                sessionAdapter.navigate(site.getUrl());
                Thread.sleep(2000);

                String html = sessionAdapter.getDomSnapshot();
                String text = sessionAdapter.getPageContent();
                String title = extractTitle(html);

                // Extract sub-pages and documents
                List<PageEntry> pages = extractPages(html, site.getUrl());

                return new SiteData(site, title, text, pages);
            }

            @Override
            protected void done() {
                try {
                    SiteData data = get();
                    currentPageUrl = data.site.getUrl();
                    currentPageTitle = data.title;
                    currentContent = data.text;

                    contentArea.setText(data.text);
                    contentArea.setCaretPosition(0);
                    statusLabel.setText("✓ " + data.site.getName() + " — " + data.pages.size() + " Seiten");

                    // Expand site node with sub-pages
                    TreePath sitePath = siteTree.getSelectionPath();
                    if (sitePath != null) {
                        DefaultMutableTreeNode siteNode = (DefaultMutableTreeNode) sitePath.getLastPathComponent();
                        siteNode.removeAllChildren();
                        for (PageEntry page : data.pages) {
                            siteNode.add(new DefaultMutableTreeNode(page));
                        }
                        treeModel.reload(siteNode);
                        siteTree.expandPath(sitePath);
                    }

                    // Auto-cache the site root page
                    autoCachePage(data.site.getUrl(), data.site.getName(), data.title, data.text);

                    // Update left drawer with page links
                    updateLinks(data.pages);

                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[SP] Navigation failed", e);
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    contentArea.setText("Fehler:\n\n" + msg);
                    statusLabel.setText("✗ " + msg);
                }
            }
        }.execute();
    }

    private void navigateToPage(final PageEntry page) {
        if (sessionAdapter == null) {
            statusLabel.setText("⚠ Browser nicht verbunden.");
            return;
        }

        statusLabel.setText("⏳ Lade: " + page.label + "…");
        contentArea.setText("Lade " + page.label + "…");

        new SwingWorker<PageData, Void>() {
            @Override
            protected PageData doInBackground() throws Exception {
                sessionAdapter.navigate(page.url);
                Thread.sleep(2000);

                String html = sessionAdapter.getDomSnapshot();
                String text = sessionAdapter.getPageContent();
                String title = extractTitle(html);

                return new PageData(page.url, title, text);
            }

            @Override
            protected void done() {
                try {
                    PageData data = get();
                    currentPageUrl = data.url;
                    currentPageTitle = data.title;
                    currentContent = data.text;

                    contentArea.setText(data.text);
                    contentArea.setCaretPosition(0);
                    statusLabel.setText("✓ " + (data.title.isEmpty() ? page.label : data.title));

                    // Auto-cache the page
                    autoCachePage(data.url, page.label, data.title, data.text);

                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[SP] Page load failed", e);
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    contentArea.setText("Fehler:\n\n" + msg);
                    statusLabel.setText("✗ " + msg);
                }
            }
        }.execute();
    }

    /**
     * Auto-cache a SharePoint page (unless blacklisted).
     */
    private void autoCachePage(String url, String siteName, String title, String content) {
        SecurityFilterService sfs = SecurityFilterService.getInstance();
        String spPath = "sp://" + url;
        if (!sfs.isAllowed("SHAREPOINT", spPath)) {
            LOG.fine("[SP] Skipping cache for blacklisted: " + url);
            return;
        }

        cacheService.cacheContent(url, "", (title != null && !title.isEmpty()) ? title : siteName, content);
    }

    // ═══════════════════════════════════════════════════════════
    //  HTML parsing helpers
    // ═══════════════════════════════════════════════════════════

    private static String extractTitle(String html) {
        if (html == null || html.isEmpty()) return "";
        Pattern p = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1).replaceAll("<[^>]+>", "").trim();
        }
        return "";
    }

    /**
     * Extract page links from a SharePoint site page.
     */
    static List<PageEntry> extractPages(String html, String baseUrl) {
        List<PageEntry> pages = new ArrayList<PageEntry>();
        if (html == null) return pages;

        Pattern p = Pattern.compile("<a\\s[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        Set<String> seen = new HashSet<String>();

        while (m.find() && pages.size() < 300) {
            String href = m.group(1).trim();
            String label = m.group(2).replaceAll("<[^>]+>", "").trim();

            // Skip non-navigable and external links
            if (href.isEmpty() || href.startsWith("javascript:") || href.startsWith("mailto:")
                    || href.startsWith("tel:") || href.equals("#")) {
                continue;
            }

            // Resolve relative URLs
            if (!href.startsWith("http://") && !href.startsWith("https://")) {
                href = resolveRelativeUrl(baseUrl, href);
            }

            // Only include links that belong to the same SharePoint domain
            if (!isSameDomain(baseUrl, href)) continue;

            if (seen.contains(href)) continue;
            seen.add(href);

            if (label.isEmpty()) {
                label = extractPageNameFromUrl(href);
            }
            if (label.length() > 100) {
                label = label.substring(0, 100) + "…";
            }

            pages.add(new PageEntry(label, href));
        }
        return pages;
    }

    private static boolean isSameDomain(String base, String target) {
        try {
            java.net.URL baseUrl = new java.net.URL(base);
            java.net.URL targetUrl = new java.net.URL(target);
            return baseUrl.getHost().equalsIgnoreCase(targetUrl.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    private static String extractPageNameFromUrl(String url) {
        if (url == null) return "?";
        String path = url;
        try {
            path = new java.net.URL(url).getPath();
        } catch (Exception ignored) {}
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1).replace("%20", " ").replace(".aspx", "");
        }
        return url.length() > 40 ? url.substring(0, 40) + "…" : url;
    }

    private void updateLinks(List<PageEntry> pages) {
        if (linksCallback == null) return;
        List<de.bund.zrb.ui.drawer.LeftDrawer.RelationEntry> entries =
                new ArrayList<de.bund.zrb.ui.drawer.LeftDrawer.RelationEntry>();
        for (PageEntry page : pages) {
            entries.add(new de.bund.zrb.ui.drawer.LeftDrawer.RelationEntry(page.label, page.url, "SP_LINK"));
        }
        linksCallback.accept(entries);
    }

    // ═══════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════

    public void setLinksCallback(java.util.function.Consumer<List<de.bund.zrb.ui.drawer.LeftDrawer.RelationEntry>> callback) {
        this.linksCallback = callback;
    }

    /**
     * Navigate to a specific SharePoint URL (e.g. from bookmark or left drawer).
     */
    public void navigateToUrl(String url) {
        navigateToPage(new PageEntry(extractPageNameFromUrl(url), url));
    }

    // ═══════════════════════════════════════════════════════════
    //  Browser lifecycle helpers
    // ═══════════════════════════════════════════════════════════

    private void installShutdownHook() {
        shutdownHook = new Thread(new Runnable() {
            @Override
            public void run() {
                BrowserSession session = browserSession;
                if (session != null) {
                    LOG.info("[SP] Shutdown hook — killing browser process");
                    try { session.killBrowserProcess(); } catch (Exception ignored) {}
                }
            }
        }, "SP-ShutdownHook");
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (Exception e) {
            LOG.warning("[SP] Could not register shutdown hook: " + e.getMessage());
        }
    }

    private void removeShutdownHook() {
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
            } catch (Exception e) {
                LOG.fine("[SP] Could not remove shutdown hook: " + e.getMessage());
            }
            shutdownHook = null;
        }
    }

    public void shutdownBrowser() {
        final BrowserSession session = browserSession;
        browserSession = null;
        sessionAdapter = null;

        if (session == null) return;
        removeShutdownHook();

        Thread cleanup = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    session.close();
                    LOG.info("[SP] Session closed normally");
                } catch (Exception e) {
                    LOG.warning("[SP] Error in close(): " + e.getMessage());
                }
                try {
                    Process proc = session.getBrowserProcess();
                    if (proc != null && proc.isAlive()) {
                        LOG.info("[SP] Process still alive — force-killing");
                        session.killBrowserProcess();
                    }
                } catch (Exception e) {
                    LOG.warning("[SP] Error in killBrowserProcess(): " + e.getMessage());
                }
            }
        }, "SP-Cleanup");
        cleanup.setDaemon(true);
        cleanup.start();

        try {
            cleanup.join(5000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static String resolveBrowserPath(Settings settings) {
        String path = settings.browserPath;
        if (path != null && !path.trim().isEmpty()) return path;
        String type = settings.browserType;
        if ("Chrome".equalsIgnoreCase(type)) return BrowserLauncher.DEFAULT_CHROME_PATH;
        if ("Edge".equalsIgnoreCase(type)) return BrowserLauncher.resolveEdgePath();
        return BrowserLauncher.DEFAULT_FIREFOX_PATH;
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
        return currentPageUrl.isEmpty() ? "SharePoint-Verbindung" : currentPageUrl;
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        shutdownBrowser();
    }

    @Override
    public void saveIfApplicable() {
        // Not applicable
    }

    @Override
    public String getContent() {
        return contentArea.getText();
    }

    @Override
    public void markAsChanged() {
        // Not applicable
    }

    @Override
    public String getPath() {
        return currentPageUrl.isEmpty() ? "sp://" : "sp://" + currentPageUrl;
    }

    @Override
    public Type getType() {
        return Type.CONNECTION;
    }

    @Override
    public void focusSearchField() {
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    @Override
    public void searchFor(String searchPattern) {
        searchField.setText(searchPattern);
        applyFilter();
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem refreshItem = new JMenuItem("🔄 Neu laden");
        refreshItem.addActionListener(e -> loadSitesInBackground());
        menu.add(refreshItem);

        if (!currentPageUrl.isEmpty()) {
            JMenuItem copyUrlItem = new JMenuItem("📋 URL kopieren");
            copyUrlItem.addActionListener(e -> {
                java.awt.datatransfer.StringSelection sel =
                        new java.awt.datatransfer.StringSelection(currentPageUrl);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            });
            menu.add(copyUrlItem);
        }

        menu.addSeparator();

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());
        menu.add(closeItem);

        return menu;
    }

    // ═══════════════════════════════════════════════════════════
    //  Data classes
    // ═══════════════════════════════════════════════════════════

    private static class SiteData {
        final SharePointSite site;
        final String title;
        final String text;
        final List<PageEntry> pages;

        SiteData(SharePointSite site, String title, String text, List<PageEntry> pages) {
            this.site = site;
            this.title = title;
            this.text = text;
            this.pages = pages;
        }
    }

    private static class PageData {
        final String url;
        final String title;
        final String text;

        PageData(String url, String title, String text) {
            this.url = url;
            this.title = title;
            this.text = text;
        }
    }

    static class PageEntry {
        final String label;
        final String url;

        PageEntry(String label, String url) {
            this.label = label;
            this.url = url;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Tree cell renderer
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
                } else if (userObj instanceof PageEntry) {
                    setText("📄 " + ((PageEntry) userObj).label);
                    setFont(getFont().deriveFont(Font.PLAIN));
                } else if (userObj instanceof String) {
                    setText("📊 " + userObj);
                    setFont(getFont().deriveFont(Font.BOLD));
                }
                setIcon(null);
            }
            return this;
        }
    }
}

