package de.bund.zrb.ui;

import de.bund.zrb.confluence.ConfluenceRestClient;
import de.bund.zrb.wiki.domain.OutlineNode;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only tab for displaying a single Confluence page in a full HTML reader view.
 * <p>
 * Analogous to {@code WikiFileTab} in the wiki-integration module.
 * Images are loaded asynchronously via the {@link ConfluenceRestClient} (mTLS + Basic Auth).
 * Links to other Confluence pages are resolved via a {@link LinkCallback}.
 * Provides outline to the RightDrawer and a search bar at the bottom.
 */
public class ConfluenceReaderTab implements ConnectionTab {

    private static final Logger LOG = Logger.getLogger(ConfluenceReaderTab.class.getName());

    private final JPanel mainPanel;
    private final JEditorPane htmlPane;
    private final JTextField searchField;
    private final String baseUrl;
    private final String pageId;
    private final String pageTitle;
    private final String htmlContent;
    private final OutlineNode outline;
    private final ConfluenceRestClient client;

    /** Yellow highlight painter for search hits. */
    private static final Highlighter.HighlightPainter YELLOW_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW);

    /** Callback for opening linked Confluence pages as new reader tabs. */
    private LinkCallback linkCallback;

    /** Callback to notify about outline changes (for RightDrawer). */
    private ConfluenceConnectionTab.OutlineCallback outlineCallback;

    /** Callback to update the dependency/relations panel (LeftDrawer). */
    private ConfluenceConnectionTab.DependencyCallback dependencyCallback;

    /** Pattern to extract page IDs from Confluence URLs. */
    private static final Pattern PAGE_ID_PATTERN = Pattern.compile("pageId=(\\d+)");
    private static final Pattern VIEWPAGE_PATTERN = Pattern.compile("/pages/viewpage\\.action\\?pageId=(\\d+)");

    /** Pattern to find img src attributes in HTML. */
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile(
            "<img\\s[^>]*src\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    public ConfluenceReaderTab(ConfluenceRestClient client, String baseUrl,
                               String pageId, String pageTitle,
                               String htmlContent, OutlineNode outline) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.pageId = pageId;
        this.pageTitle = pageTitle;
        this.htmlContent = htmlContent;
        this.outline = outline;
        this.mainPanel = new JPanel(new BorderLayout(0, 0));

        // HTML pane with async image loading support
        htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        htmlPane.setContentType("text/html");

        // Inject heading anchors + wrap in full HTML
        String enrichedHtml = ConfluenceConnectionTab.injectHeadingAnchors(htmlContent);
        String fullHtml = wrapHtml(enrichedHtml);
        htmlPane.setText(fullHtml);
        htmlPane.setCaretPosition(0);

        htmlPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                handleLink(e);
            }
        });

        JScrollPane htmlScroll = new JScrollPane(htmlPane);
        mainPanel.add(htmlScroll, BorderLayout.CENTER);

        // Search bar at bottom
        searchField = new JTextField();
        searchField.setToolTipText("Text suchen (Enter)");
        searchField.addActionListener(e -> highlightSearch());
        JPanel searchBar = new JPanel(new BorderLayout(2, 0));
        searchBar.add(new JLabel(" \uD83D\uDD0E "), BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);
        mainPanel.add(searchBar, BorderLayout.SOUTH);

        // Load images asynchronously in background
        loadImagesAsync(htmlContent);
    }

    // ═══════════════════════════════════════════════════════════
    //  HTML wrapping
    // ═══════════════════════════════════════════════════════════

    static String wrapHtml(String bodyHtml) {
        return "<html><head>"
                + "<style>"
                + "body { font-family: sans-serif; font-size: 13px; padding: 12px; }"
                + "h1, h2, h3 { color: #333; }"
                + "a { color: #0645ad; }"
                + "pre, code { background: #f4f4f4; padding: 4px; }"
                + "table { border-collapse: collapse; }"
                + "td, th { border: 1px solid #ccc; padding: 4px; }"
                + "img { max-width: 100%; }"
                + ".confluence-information-macro, .confluence-information-macro-body { "
                + "  background: #e8f5e9; border-left: 4px solid #4caf50; padding: 8px; margin: 8px 0; }"
                + ".confluence-warning-macro, .confluence-warning-macro-body { "
                + "  background: #fff3e0; border-left: 4px solid #ff9800; padding: 8px; margin: 8px 0; }"
                + "</style>"
                + "</head><body>"
                + bodyHtml
                + "</body></html>";
    }

    // ═══════════════════════════════════════════════════════════
    //  Asynchronous image loading
    // ═══════════════════════════════════════════════════════════

    /**
     * Scans the HTML for img tags and loads each image via the ConfluenceRestClient
     * in a background thread, then injects them into the JEditorPane's image cache.
     */
    private void loadImagesAsync(String html) {
        if (html == null || client == null) return;

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

                        Image image = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
                        if (image != null) {
                            // Resolve the URL that the HTMLDocument uses as key
                            URL imageUrl = resolveImageUrl(src);
                            if (imageUrl != null) {
                                publish(new Object[]{imageUrl, image});
                            }
                        }
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "[ConfluenceReader] Image load failed: " + src, e);
                    }
                }
                return null;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void process(java.util.List<Object[]> chunks) {
                Document doc = htmlPane.getDocument();
                if (!(doc instanceof HTMLDocument)) return;

                // Get or create image cache
                Dictionary<URL, Image> cache = (Dictionary<URL, Image>) doc.getProperty("imageCache");
                if (cache == null) {
                    cache = new Hashtable<URL, Image>();
                    doc.putProperty("imageCache", cache);
                }

                for (Object[] pair : chunks) {
                    URL url = (URL) pair[0];
                    Image img = (Image) pair[1];
                    cache.put(url, img);
                }

                // Force repaint to show newly loaded images
                htmlPane.revalidate();
                htmlPane.repaint();
            }
        }.execute();
    }

    /**
     * Resolve an img src to a relative REST path for the ConfluenceRestClient.
     */
    private String resolveImagePath(String src) {
        if (src == null || src.isEmpty()) return null;

        // Already a full URL pointing to our Confluence instance
        if (src.startsWith(baseUrl)) {
            return src.substring(baseUrl.length());
        }

        // Relative path (e.g. /download/attachments/... or /rest/...)
        if (src.startsWith("/")) {
            return src;
        }

        // Data URIs or external URLs — skip
        if (src.startsWith("data:") || src.startsWith("http://") || src.startsWith("https://")) {
            return null;
        }

        // Relative to base
        return "/" + src;
    }

    /**
     * Resolve the img src to a full URL (as the HTMLDocument would key it).
     */
    private URL resolveImageUrl(String src) {
        try {
            if (src.startsWith("http://") || src.startsWith("https://")) {
                return new URL(src);
            }
            if (src.startsWith("/")) {
                return new URL(baseUrl + src);
            }
            return new URL(baseUrl + "/" + src);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Link handling
    // ═══════════════════════════════════════════════════════════

    private void handleLink(HyperlinkEvent e) {
        String desc = e.getDescription();
        if (desc != null && desc.startsWith("#")) {
            htmlPane.scrollToReference(desc.substring(1));
            return;
        }

        String url = e.getURL() != null ? e.getURL().toString() : desc;
        if (url == null) return;

        // Try to extract a Confluence page ID from the link
        String linkedPageId = extractPageId(url);
        if (linkedPageId != null && linkCallback != null) {
            linkCallback.openLinkedPage(linkedPageId);
            return;
        }

        // External link → open in browser
        try {
            Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception ex) {
            LOG.log(Level.FINE, "[ConfluenceReader] Could not open external link", ex);
        }
    }

    /**
     * Extract a page ID from a Confluence URL.
     */
    static String extractPageId(String url) {
        if (url == null) return null;
        Matcher m = PAGE_ID_PATTERN.matcher(url);
        if (m.find()) return m.group(1);
        m = VIEWPAGE_PATTERN.matcher(url);
        if (m.find()) return m.group(1);
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  Search / highlight
    // ═══════════════════════════════════════════════════════════

    private void highlightSearch() {
        Highlighter highlighter = htmlPane.getHighlighter();
        highlighter.removeAllHighlights();

        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        try {
            Document doc = htmlPane.getDocument();
            String fullText = doc.getText(0, doc.getLength());
            String lowerText = fullText.toLowerCase();
            String lowerQuery = query.toLowerCase();

            int firstHit = -1;
            int pos = 0;

            while (pos < lowerText.length()) {
                int idx = lowerText.indexOf(lowerQuery, pos);
                if (idx < 0) break;

                int end = idx + query.length();
                highlighter.addHighlight(idx, end, YELLOW_PAINTER);

                if (firstHit < 0) {
                    firstHit = idx;
                }
                pos = end;
            }

            if (firstHit >= 0) {
                Rectangle rect = htmlPane.modelToView(firstHit);
                if (rect != null) {
                    htmlPane.scrollRectToVisible(rect);
                }
                htmlPane.setCaretPosition(firstHit);
            }
        } catch (BadLocationException ex) {
            LOG.log(Level.FINE, "[ConfluenceReader] Search highlight error", ex);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Public accessors
    // ═══════════════════════════════════════════════════════════

    public void scrollToAnchor(String anchor) {
        if (anchor != null) {
            htmlPane.scrollToReference(anchor);
        }
    }

    public OutlineNode getOutline() { return outline; }
    public String getPageId() { return pageId; }
    public String getPageTitle() { return pageTitle; }
    public String getBaseUrl() { return baseUrl; }
    public ConfluenceRestClient getClient() { return client; }
    public String getHtmlContent() { return htmlContent; }

    // ═══════════════════════════════════════════════════════════
    //  ConnectionTab interface
    // ═══════════════════════════════════════════════════════════

    @Override public String getTitle() { return "\uD83D\uDCD6 " + pageTitle; }
    @Override public String getTooltip() { return "Confluence: " + pageTitle; }
    @Override public JComponent getComponent() { return mainPanel; }
    @Override public void onClose() { /* nothing */ }
    @Override public void saveIfApplicable() { /* read-only */ }
    @Override public String getContent() { return htmlContent; }
    @Override public void markAsChanged() { /* not applicable */ }
    @Override public String getPath() { return "confluence://" + baseUrl + "/page/" + pageId; }
    @Override public Type getType() { return Type.PREVIEW; }

    @Override
    public void focusSearchField() {
        searchField.requestFocusInWindow();
    }

    @Override
    public void searchFor(String searchPattern) {
        searchField.setText(searchPattern);
        highlightSearch();
    }

    // ═══════════════════════════════════════════════════════════
    //  Callbacks
    // ═══════════════════════════════════════════════════════════

    public void setLinkCallback(LinkCallback callback) {
        this.linkCallback = callback;
    }

    public void setOutlineCallback(ConfluenceConnectionTab.OutlineCallback callback) {
        this.outlineCallback = callback;
    }

    public void setDependencyCallback(ConfluenceConnectionTab.DependencyCallback callback) {
        this.dependencyCallback = callback;
    }

    public interface LinkCallback {
        void openLinkedPage(String pageId);
    }
}

