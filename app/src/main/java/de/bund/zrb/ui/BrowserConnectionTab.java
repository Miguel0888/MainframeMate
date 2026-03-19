 package de.bund.zrb.ui;

import de.bund.zrb.browser.Wd4jBrowserSessionAdapter;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.mcpserver.browser.BrowserLauncher;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.drawer.LeftDrawer;
import de.bund.zrb.wiki.domain.OutlineNode;
import de.zrb.bund.newApi.browser.NavigationResult;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Browser connection tab — embeds a headless browser as a MainframeMate tab.
 * Provides an address bar for navigation, displays extracted page text content,
 * and shows extracted links and a document outline in the left drawer.
 */
public class BrowserConnectionTab implements ConnectionTab {

    private static final Logger LOG = Logger.getLogger(BrowserConnectionTab.class.getName());

    private final JPanel mainPanel;
    private final JTextField addressBar;
    private final JButton backButton;
    private final JButton forwardButton;
    private final JButton goButton;
    private final JButton refreshButton;
    private final JTextArea contentArea;
    private final JLabel statusLabel;

    private BrowserSession browserSession;
    private de.zrb.bund.newApi.browser.BrowserSession sessionAdapter;
    private String currentUrl = "";
    private String currentTitle = "";

    // Callback for updating left drawer relations
    private java.util.function.Consumer<List<LeftDrawer.RelationEntry>> linksCallback;
    // Callback for updating the RightDrawer outline
    private java.util.function.Consumer<OutlineNode> outlineCallback;
    // Current page outline (cached for tab switch)
    private OutlineNode currentOutline;

    // Navigation history
    private final List<String> history = new ArrayList<String>();
    private int historyIndex = -1;

    public BrowserConnectionTab(String homePage) {
        mainPanel = new JPanel(new BorderLayout(0, 2));

        // ── Toolbar with address bar ──────────────────────────────
        JPanel toolbar = new JPanel(new BorderLayout(4, 0));
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        backButton = new JButton("◀");
        backButton.setToolTipText("Zurück");
        backButton.setMargin(new Insets(2, 6, 2, 6));
        backButton.setEnabled(false);
        backButton.addActionListener(e -> navigateBack());

        forwardButton = new JButton("▶");
        forwardButton.setToolTipText("Vorwärts");
        forwardButton.setMargin(new Insets(2, 6, 2, 6));
        forwardButton.setEnabled(false);
        forwardButton.addActionListener(e -> navigateForward());

        refreshButton = new JButton("↻");
        refreshButton.setToolTipText("Neu laden");
        refreshButton.setMargin(new Insets(2, 6, 2, 6));
        refreshButton.addActionListener(e -> navigateTo(currentUrl));

        navButtons.add(backButton);
        navButtons.add(forwardButton);
        navButtons.add(refreshButton);

        addressBar = new JTextField(homePage != null ? homePage : "https://www.google.com", 40);
        addressBar.addActionListener(e -> navigateTo(addressBar.getText().trim()));

        goButton = new JButton("→");
        goButton.setToolTipText("Navigieren");
        goButton.setMargin(new Insets(2, 8, 2, 8));
        goButton.addActionListener(e -> navigateTo(addressBar.getText().trim()));

        toolbar.add(navButtons, BorderLayout.WEST);
        toolbar.add(addressBar, BorderLayout.CENTER);
        toolbar.add(goButton, BorderLayout.EAST);

        // ── Content area ──────────────────────────────────────────
        contentArea = new JTextArea();
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        contentArea.setText("Browser wird gestartet…");


        // ── Status bar ────────────────────────────────────────────
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        mainPanel.add(toolbar, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(contentArea), BorderLayout.CENTER);
        mainPanel.add(statusLabel, BorderLayout.SOUTH);
    }

    // ═══════════════════════════════════════════════════════════
    //  Browser lifecycle
    // ═══════════════════════════════════════════════════════════

    /**
     * Launch the headless browser and navigate to the home page.
     * Called from a background thread (SwingWorker).
     */
    public void launchBrowser() throws Exception {
        Settings settings = SettingsHelper.load();

        String browserPath = resolveBrowserPath(settings);
        boolean headless = settings.browserHeadless;
        int debugPort = settings.browserDebugPort;
        int timeout = settings.browserNavigateTimeoutSeconds;

        LOG.info("[Browser] Launching: " + browserPath + " (headless=" + headless + ", debugPort=" + debugPort + ")");

        browserSession = new BrowserSession();
        browserSession.launchAndConnect(browserPath, new ArrayList<String>(), headless, 30000L, debugPort);
        sessionAdapter = new Wd4jBrowserSessionAdapter(browserSession);

        LOG.info("[Browser] Connected, contextId=" + browserSession.getContextId());
    }

    /**
     * Navigate to the initial page on the EDT after browser launch.
     */
    public void navigateToHomePage() {
        String url = addressBar.getText().trim();
        if (!url.isEmpty()) {
            navigateTo(url);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Navigation
    // ═══════════════════════════════════════════════════════════

    public void navigateTo(String url) {
        if (url == null || url.trim().isEmpty()) return;

        // Auto-prepend https:// if no scheme
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
            url = "https://" + url;
        }

        final String targetUrl = url;
        statusLabel.setText("⏳ Lade: " + targetUrl);
        addressBar.setText(targetUrl);
        contentArea.setText("Laden…");
        setNavigating(true);

        new SwingWorker<PageData, Void>() {
            @Override
            protected PageData doInBackground() throws Exception {
                NavigationResult navResult = sessionAdapter.navigate(targetUrl);

                // Give the page a moment to settle
                Thread.sleep(1500);

                String finalUrl = sessionAdapter.getCurrentUrl();
                if (finalUrl == null) finalUrl = targetUrl;

                String html = sessionAdapter.getDomSnapshot();
                String text = sessionAdapter.getPageContent();

                // Extract title from HTML
                String title = extractTitle(html);

                // Extract links from HTML
                List<LinkInfo> links = extractLinks(html, finalUrl);

                // Extract outline from HTML headings
                List<HeadingInfo> headings = extractHeadings(html);

                return new PageData(finalUrl, title, text, links, headings);
            }

            @Override
            protected void done() {
                setNavigating(false);
                try {
                    PageData data = get();
                    currentUrl = data.url;
                    currentTitle = data.title;

                    // Update address bar and content
                    addressBar.setText(currentUrl);
                    contentArea.setText(data.text);
                    contentArea.setCaretPosition(0);

                    String titleDisplay = (data.title != null && !data.title.isEmpty())
                            ? data.title : currentUrl;
                    statusLabel.setText("✓ " + titleDisplay + " (" + data.links.size() + " Links)");

                    // Update history
                    addToHistory(currentUrl);
                    updateNavigationButtons();

                    // Update left drawer with links
                    updateLinks(data.links);

                    // Update outline tree
                    updateOutline(data.headings);

                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Browser] Navigation failed", e);
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    contentArea.setText("Fehler beim Laden der Seite:\n\n" + msg);
                    statusLabel.setText("✗ Fehler: " + msg);
                }
            }
        }.execute();
    }

    private void navigateBack() {
        if (historyIndex > 0) {
            historyIndex--;
            String url = history.get(historyIndex);
            navigateToWithoutHistory(url);
        }
    }

    private void navigateForward() {
        if (historyIndex < history.size() - 1) {
            historyIndex++;
            String url = history.get(historyIndex);
            navigateToWithoutHistory(url);
        }
    }

    private void navigateToWithoutHistory(String url) {
        final String targetUrl = url;
        statusLabel.setText("⏳ Lade: " + targetUrl);
        addressBar.setText(targetUrl);
        contentArea.setText("Laden…");
        setNavigating(true);

        new SwingWorker<PageData, Void>() {
            @Override
            protected PageData doInBackground() throws Exception {
                sessionAdapter.navigate(targetUrl);
                Thread.sleep(1500);

                String finalUrl = sessionAdapter.getCurrentUrl();
                if (finalUrl == null) finalUrl = targetUrl;

                String html = sessionAdapter.getDomSnapshot();
                String text = sessionAdapter.getPageContent();
                String title = extractTitle(html);
                List<LinkInfo> links = extractLinks(html, finalUrl);
                List<HeadingInfo> headings = extractHeadings(html);

                return new PageData(finalUrl, title, text, links, headings);
            }

            @Override
            protected void done() {
                setNavigating(false);
                try {
                    PageData data = get();
                    currentUrl = data.url;
                    currentTitle = data.title;
                    addressBar.setText(currentUrl);
                    contentArea.setText(data.text);
                    contentArea.setCaretPosition(0);
                    statusLabel.setText("✓ " + (data.title != null ? data.title : currentUrl)
                            + " (" + data.links.size() + " Links)");
                    updateNavigationButtons();
                    updateLinks(data.links);
                    updateOutline(data.headings);
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    contentArea.setText("Fehler:\n\n" + msg);
                    statusLabel.setText("✗ " + msg);
                }
            }
        }.execute();
    }

    private void addToHistory(String url) {
        // Remove future entries if we navigated back and then to a new URL
        while (history.size() > historyIndex + 1) {
            history.remove(history.size() - 1);
        }
        history.add(url);
        historyIndex = history.size() - 1;
    }

    private void updateNavigationButtons() {
        backButton.setEnabled(historyIndex > 0);
        forwardButton.setEnabled(historyIndex < history.size() - 1);
    }

    private void setNavigating(boolean busy) {
        goButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy);
        addressBar.setEnabled(!busy);
    }

    // ═══════════════════════════════════════════════════════════
    //  HTML parsing helpers (lightweight, no Jsoup dependency)
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

    static List<LinkInfo> extractLinks(String html, String baseUrl) {
        List<LinkInfo> links = new ArrayList<LinkInfo>();
        if (html == null) return links;

        Pattern p = Pattern.compile("<a\\s[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        Set<String> seen = new HashSet<String>();

        while (m.find() && links.size() < 200) {
            String href = m.group(1).trim();
            String label = m.group(2).replaceAll("<[^>]+>", "").trim();

            // Skip non-navigable
            if (href.isEmpty() || href.startsWith("javascript:") || href.startsWith("mailto:")
                    || href.startsWith("tel:") || href.equals("#")) {
                continue;
            }

            // Resolve relative URLs
            if (!href.startsWith("http://") && !href.startsWith("https://") && !href.startsWith("file://")) {
                href = resolveRelativeUrl(baseUrl, href);
            }

            if (seen.contains(href)) continue;
            seen.add(href);

            if (label.isEmpty()) {
                label = href.length() > 80 ? href.substring(0, 80) + "…" : href;
            }
            if (label.length() > 100) {
                label = label.substring(0, 100) + "…";
            }

            links.add(new LinkInfo(label, href));
        }
        return links;
    }

    static List<HeadingInfo> extractHeadings(String html) {
        List<HeadingInfo> headings = new ArrayList<HeadingInfo>();
        if (html == null) return headings;

        Pattern p = Pattern.compile("<(h[1-6])[^>]*>(.*?)</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        while (m.find() && headings.size() < 100) {
            int level = m.group(1).charAt(1) - '0';
            String text = m.group(2).replaceAll("<[^>]+>", "").trim();
            if (!text.isEmpty()) {
                headings.add(new HeadingInfo(level, text));
            }
        }
        return headings;
    }

    private static String resolveRelativeUrl(String base, String relative) {
        if (relative.startsWith("//")) {
            return "https:" + relative;
        }
        if (relative.startsWith("/")) {
            // Absolute path
            try {
                java.net.URL baseUrl = new java.net.URL(base);
                return baseUrl.getProtocol() + "://" + baseUrl.getHost()
                        + (baseUrl.getPort() > 0 ? ":" + baseUrl.getPort() : "") + relative;
            } catch (Exception e) {
                return relative;
            }
        }
        // Relative path
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash >= 0) {
            return base.substring(0, lastSlash + 1) + relative;
        }
        return base + "/" + relative;
    }

    // ═══════════════════════════════════════════════════════════
    //  UI updates
    // ═══════════════════════════════════════════════════════════

    private void updateLinks(List<LinkInfo> links) {
        List<LeftDrawer.RelationEntry> entries = new ArrayList<LeftDrawer.RelationEntry>();
        for (LinkInfo link : links) {
            entries.add(new LeftDrawer.RelationEntry(link.label, link.href, "BROWSER_LINK"));
        }
        if (linksCallback != null) {
            linksCallback.accept(entries);
        }
    }

    private void updateOutline(List<HeadingInfo> headings) {
        currentOutline = buildOutlineTree(headings);
        if (outlineCallback != null) {
            outlineCallback.accept(currentOutline);
        }
    }

    /**
     * Build a hierarchical OutlineNode tree from flat heading list.
     * h1 contains h2, h2 contains h3, etc.
     */
    static OutlineNode buildOutlineTree(List<HeadingInfo> headings) {
        if (headings == null || headings.isEmpty()) {
            return new OutlineNode("Gliederung", null, Collections.<OutlineNode>emptyList());
        }

        // Stack-based approach: each entry is (level, childrenList)
        // We keep a stack so that when a deeper heading comes, it becomes a child.
        List<OutlineNode> rootChildren = new ArrayList<OutlineNode>();
        java.util.Deque<Object[]> stack = new java.util.ArrayDeque<Object[]>();
        // stack entry: [int level, String text, String anchor, List<OutlineNode> children]

        for (HeadingInfo h : headings) {
            String anchor = h.text.toLowerCase()
                    .replaceAll("[^a-z0-9äöüß ]+", "-")
                    .replaceAll("\\s+", "-")
                    .replaceAll("^-+|-+$", "");

            // Pop entries that are at the same level or deeper — they are completed
            while (!stack.isEmpty() && (int) stack.peek()[0] >= h.level) {
                Object[] popped = stack.pop();
                OutlineNode node = new OutlineNode(
                        (String) popped[1], (String) popped[2],
                        (List<OutlineNode>) popped[3]);
                if (stack.isEmpty()) {
                    rootChildren.add(node);
                } else {
                    ((List<OutlineNode>) stack.peek()[3]).add(node);
                }
            }
            // Push current heading with empty children list
            stack.push(new Object[]{h.level, h.text, anchor, new ArrayList<OutlineNode>()});
        }

        // Drain remaining stack entries
        while (!stack.isEmpty()) {
            Object[] popped = stack.pop();
            OutlineNode node = new OutlineNode(
                    (String) popped[1], (String) popped[2],
                    (List<OutlineNode>) popped[3]);
            if (stack.isEmpty()) {
                rootChildren.add(node);
            } else {
                ((List<OutlineNode>) stack.peek()[3]).add(node);
            }
        }

        return new OutlineNode("Gliederung", null, rootChildren);
    }

    /**
     * @return the current page outline, or {@code null} if no page is loaded
     */
    public OutlineNode getCurrentOutline() {
        return currentOutline;
    }

    /**
     * @return the current page title
     */
    public String getCurrentTitle() {
        return currentTitle;
    }

    /**
     * Set callback for updating the left drawer with extracted links.
     */
    public void setLinksCallback(java.util.function.Consumer<List<LeftDrawer.RelationEntry>> callback) {
        this.linksCallback = callback;
    }

    /**
     * Set callback for updating the RightDrawer outline when a page loads.
     */
    public void setOutlineCallback(java.util.function.Consumer<OutlineNode> callback) {
        this.outlineCallback = callback;
    }

    /**
     * Navigate to a link (called from left drawer when user clicks a relation entry).
     */
    public void navigateToLink(String url) {
        navigateTo(url);
    }

    /**
     * Returns the current browser session adapter (for MCP tool integration).
     */
    public de.zrb.bund.newApi.browser.BrowserSession getSessionAdapter() {
        return sessionAdapter;
    }

    // ═══════════════════════════════════════════════════════════
    //  ConnectionTab / FtpTab interface
    // ═══════════════════════════════════════════════════════════

    @Override
    public String getTitle() {
        if (currentTitle != null && !currentTitle.isEmpty()) {
            String display = currentTitle.length() > 25
                    ? currentTitle.substring(0, 25) + "…" : currentTitle;
            return "🌐 " + display;
        }
        return "🌐 Browser";
    }

    @Override
    public String getTooltip() {
        return currentUrl != null ? currentUrl : "Browser-Verbindung";
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        if (browserSession != null) {
            try {
                browserSession.close();
            } catch (Exception e) {
                LOG.warning("[Browser] Error closing session: " + e.getMessage());
                try { browserSession.killBrowserProcess(); } catch (Exception ignored) {}
            }
            browserSession = null;
            sessionAdapter = null;
        }
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
        return "browser://" + (currentUrl != null ? currentUrl : "");
    }

    @Override
    public Type getType() {
        return Type.CONNECTION;
    }

    @Override
    public void focusSearchField() {
        addressBar.requestFocusInWindow();
        addressBar.selectAll();
    }

    @Override
    public void searchFor(String searchPattern) {
        if (searchPattern != null && !searchPattern.isEmpty()) {
            navigateTo(searchPattern);
        }
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem homeItem = new JMenuItem("🏠 Startseite");
        homeItem.addActionListener(e -> {
            Settings settings = SettingsHelper.load();
            navigateTo(settings.browserHomePage);
        });
        menu.add(homeItem);

        JMenuItem copyUrlItem = new JMenuItem("📋 URL kopieren");
        copyUrlItem.addActionListener(e -> {
            if (currentUrl != null) {
                java.awt.datatransfer.StringSelection sel =
                        new java.awt.datatransfer.StringSelection(currentUrl);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            }
        });
        menu.add(copyUrlItem);

        menu.addSeparator();

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());
        menu.add(closeItem);

        return menu;
    }

    // ═══════════════════════════════════════════════════════════
    //  Data classes
    // ═══════════════════════════════════════════════════════════

    private static class PageData {
        final String url;
        final String title;
        final String text;
        final List<LinkInfo> links;
        final List<HeadingInfo> headings;

        PageData(String url, String title, String text, List<LinkInfo> links, List<HeadingInfo> headings) {
            this.url = url;
            this.title = title;
            this.text = text;
            this.links = links;
            this.headings = headings;
        }
    }

    static class LinkInfo {
        final String label;
        final String href;

        LinkInfo(String label, String href) {
            this.label = label;
            this.href = href;
        }
    }

    static class HeadingInfo {
        final int level;
        final String text;

        HeadingInfo(int level, String text) {
            this.level = level;
            this.text = text;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════

    private static String resolveBrowserPath(Settings settings) {
        String path = settings.browserPath;
        if (path != null && !path.trim().isEmpty()) {
            return path;
        }
        String type = settings.browserType;
        if ("Chrome".equalsIgnoreCase(type)) {
            return BrowserLauncher.DEFAULT_CHROME_PATH;
        }
        if ("Edge".equalsIgnoreCase(type)) {
            return BrowserLauncher.resolveEdgePath();
        }
        return BrowserLauncher.DEFAULT_FIREFOX_PATH;
    }
}

