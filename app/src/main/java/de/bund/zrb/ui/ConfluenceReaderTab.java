package de.bund.zrb.ui;

import de.bund.zrb.confluence.ConfluenceRestClient;
import de.bund.zrb.wiki.domain.ImageRef;
import de.bund.zrb.wiki.domain.OutlineNode;
import de.bund.zrb.wiki.ui.ByteDownloader;
import de.bund.zrb.wiki.ui.HtmlImageExtractor;
import de.bund.zrb.wiki.ui.ImageStripPanel;
import de.bund.zrb.wiki.ui.ImageThumbnailPanel;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only tab for displaying a single Confluence page in a full HTML reader view.
 * <p>
 * Supports two display modes:
 * <ul>
 *   <li><b>Text mode</b> (default): images are stripped from the HTML and shown in a
 *       side strip ({@link ImageStripPanel}) that can be expanded into a thumbnail panel
 *       ({@link ImageThumbnailPanel}).</li>
 *   <li><b>Rendered mode</b>: images are displayed inline in the HTML with async loading.</li>
 * </ul>
 * Toggle buttons at the bottom-right of the search bar switch between modes.
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

    /** Extracted images from the HTML body. */
    private final List<ImageRef> images;

    /** Authenticated image downloader wrapping the ConfluenceRestClient. */
    private final ByteDownloader downloader;

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

    /** Full HTML string for rendered mode (with images). */
    private final String renderedFullHtml;

    /** Full HTML string for text mode (images stripped). */
    private final String textFullHtml;

    // ── View mode state ──
    /** true = rendered mode (images inline), false = text mode (images in side strip). */
    private boolean renderedMode = false;
    private JToggleButton textModeBtn;
    private JToggleButton renderedModeBtn;
    /** Center panel wrapping htmlScroll + optional image strip or split pane. */
    private JPanel centerPanel;
    /** Scroll pane wrapping htmlPane — kept as field for split-pane attach/detach. */
    private JScrollPane htmlScroll;

    // ── Image strip expand / collapse state ──
    private boolean imageStripExpanded = false;
    private JSplitPane imageSplitPane;
    private ImageThumbnailPanel thumbnailPanel;
    private int lastDividerLocation = -1;
    private ChangeListener scrollSyncListener;

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

        // Create authenticated downloader for Confluence images
        this.downloader = new ByteDownloader() {
            @Override
            public byte[] download(String url) throws IOException {
                String path = resolveImagePath(url);
                if (path == null) throw new IOException("Cannot resolve image: " + url);
                return client.getBytes(path);
            }
        };

        // Extract images from HTML
        this.images = HtmlImageExtractor.extractImages(htmlContent, baseUrl);

        // Build HTML variants
        String enrichedHtml = ConfluenceConnectionTab.injectHeadingAnchors(htmlContent);
        this.renderedFullHtml = wrapHtml(enrichedHtml, baseUrl);

        String strippedHtml = HtmlImageExtractor.stripImgTags(htmlContent);
        String enrichedStripped = ConfluenceConnectionTab.injectHeadingAnchors(strippedHtml);
        this.textFullHtml = wrapHtml(enrichedStripped, baseUrl);

        // HTML pane
        htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        htmlPane.setContentType("text/html");

        try {
            ((HTMLDocument) htmlPane.getDocument()).setBase(new URL(baseUrl));
        } catch (MalformedURLException ignore) { }

        // Default: text mode (images stripped)
        htmlPane.setText(textFullHtml);
        htmlPane.setCaretPosition(0);

        htmlPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                handleLink(e);
            }
        });

        // Click on inline image in rendered mode → open image overlay dialog
        htmlPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!renderedMode || images.isEmpty()) return;
                int pos = htmlPane.viewToModel(e.getPoint());
                if (pos < 0) return;
                Document doc = htmlPane.getDocument();
                if (!(doc instanceof HTMLDocument)) return;
                javax.swing.text.Element elem = ((HTMLDocument) doc).getCharacterElement(pos);
                if (elem == null) return;
                String src = extractImgSrc(elem);
                if (src == null) return;
                int index = findImageIndex(images, src);
                if (index >= 0) {
                    ImageStripPanel.openOverlay(htmlPane, images, index, downloader);
                }
            }
        });
        // Hand cursor when hovering over images in rendered mode
        htmlPane.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (!renderedMode || images.isEmpty()) {
                    htmlPane.setCursor(Cursor.getDefaultCursor());
                    return;
                }
                int pos = htmlPane.viewToModel(e.getPoint());
                if (pos >= 0) {
                    Document doc = htmlPane.getDocument();
                    if (doc instanceof HTMLDocument) {
                        javax.swing.text.Element elem = ((HTMLDocument) doc).getCharacterElement(pos);
                        if (extractImgSrc(elem) != null) {
                            htmlPane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            return;
                        }
                    }
                }
                htmlPane.setCursor(Cursor.getDefaultCursor());
            }
        });

        htmlScroll = new JScrollPane(htmlPane);

        // Center panel wrapping htmlScroll + optional image strip
        centerPanel = new JPanel(new BorderLayout(0, 0));
        centerPanel.add(htmlScroll, BorderLayout.CENTER);

        // Image strip on the right (text mode default)
        if (!images.isEmpty()) {
            ImageStripPanel strip = new ImageStripPanel(images, downloader);
            strip.setExpandCallback(this::expandImageStrip);
            centerPanel.add(strip, BorderLayout.EAST);
        }
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // ── Toggle buttons: Text vs Rendered ────────────────────
        textModeBtn = new JToggleButton("Aa");
        textModeBtn.setToolTipText("Textmodus (Bilder als Seitenleiste)");
        textModeBtn.setFocusable(false);
        textModeBtn.setSelected(true);
        textModeBtn.setMargin(new Insets(2, 6, 2, 6));

        renderedModeBtn = new JToggleButton("\uD83D\uDDBC");
        renderedModeBtn.setToolTipText("Gerenderte Ansicht (Bilder inline)");
        renderedModeBtn.setFocusable(false);
        renderedModeBtn.setMargin(new Insets(2, 6, 2, 6));

        ButtonGroup viewModeGroup = new ButtonGroup();
        viewModeGroup.add(textModeBtn);
        viewModeGroup.add(renderedModeBtn);

        textModeBtn.addActionListener(e -> {
            if (!renderedMode) return;
            renderedMode = false;
            refreshViewMode();
        });
        renderedModeBtn.addActionListener(e -> {
            if (renderedMode) return;
            renderedMode = true;
            refreshViewMode();
        });

        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        togglePanel.add(textModeBtn);
        togglePanel.add(renderedModeBtn);

        // Search bar at bottom (with toggle buttons on the right)
        searchField = new JTextField();
        searchField.setToolTipText("Text suchen (Enter)");
        searchField.addActionListener(e -> highlightSearch());
        JPanel searchBar = new JPanel(new BorderLayout(2, 0));
        searchBar.add(new JLabel(" \uD83D\uDD0E "), BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(togglePanel, BorderLayout.EAST);
        mainPanel.add(searchBar, BorderLayout.SOUTH);
    }

    // ═══════════════════════════════════════════════════════════
    //  View mode switching
    // ═══════════════════════════════════════════════════════════

    private void refreshViewMode() {
        if (imageStripExpanded) {
            collapseImageStripSilently();
        }

        Component eastComp = ((BorderLayout) centerPanel.getLayout())
                .getLayoutComponent(BorderLayout.EAST);
        if (eastComp != null) {
            centerPanel.remove(eastComp);
        }

        if (renderedMode) {
            htmlPane.setText(renderedFullHtml);
            htmlPane.setCaretPosition(0);
            try {
                ((HTMLDocument) htmlPane.getDocument()).setBase(new URL(baseUrl));
            } catch (MalformedURLException ignore) { }
            loadImagesAsync(htmlContent);
        } else {
            htmlPane.setText(textFullHtml);
            htmlPane.setCaretPosition(0);
            if (!images.isEmpty()) {
                ImageStripPanel strip = new ImageStripPanel(images, downloader);
                strip.setExpandCallback(this::expandImageStrip);
                centerPanel.add(strip, BorderLayout.EAST);
            }
        }

        centerPanel.revalidate();
        centerPanel.repaint();
    }

    // ═══════════════════════════════════════════════════════════
    //  Image strip expand / collapse
    // ═══════════════════════════════════════════════════════════

    private void expandImageStrip() {
        if (images.isEmpty() || imageStripExpanded || renderedMode) return;
        imageStripExpanded = true;

        Component eastComp = ((BorderLayout) centerPanel.getLayout())
                .getLayoutComponent(BorderLayout.EAST);
        if (eastComp != null) centerPanel.remove(eastComp);

        centerPanel.remove(htmlScroll);

        thumbnailPanel = new ImageThumbnailPanel(images, downloader);
        thumbnailPanel.setCollapseCallback(this::collapseImageStrip);

        imageSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, htmlScroll, thumbnailPanel);
        imageSplitPane.setResizeWeight(0.7);
        int divLoc = lastDividerLocation > 0 ? lastDividerLocation
                : centerPanel.getWidth() * 3 / 4;
        imageSplitPane.setDividerLocation(divLoc);

        centerPanel.add(imageSplitPane, BorderLayout.CENTER);

        installScrollSync();

        centerPanel.revalidate();
        centerPanel.repaint();
    }

    private void collapseImageStrip() {
        if (!imageStripExpanded) return;

        if (imageSplitPane != null) {
            lastDividerLocation = imageSplitPane.getDividerLocation();
        }

        collapseImageStripSilently();

        ImageStripPanel strip = new ImageStripPanel(images, downloader);
        strip.setExpandCallback(this::expandImageStrip);
        centerPanel.add(strip, BorderLayout.EAST);

        centerPanel.revalidate();
        centerPanel.repaint();
    }

    private void collapseImageStripSilently() {
        if (!imageStripExpanded) return;
        imageStripExpanded = false;

        removeScrollSync();

        if (imageSplitPane != null) {
            imageSplitPane.remove(htmlScroll);
            centerPanel.remove(imageSplitPane);
        }

        centerPanel.add(htmlScroll, BorderLayout.CENTER);

        imageSplitPane = null;
        thumbnailPanel = null;
    }

    // ═══════════════════════════════════════════════════════════
    //  Scroll synchronisation
    // ═══════════════════════════════════════════════════════════

    private void installScrollSync() {
        if (scrollSyncListener != null) return;
        scrollSyncListener = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (thumbnailPanel == null || images.isEmpty()) return;
                JViewport viewport = htmlScroll.getViewport();
                int viewY = viewport.getViewPosition().y;
                int totalH = htmlPane.getPreferredSize().height - viewport.getHeight();
                if (totalH <= 0) return;
                double fraction = Math.min(1.0, Math.max(0.0, (double) viewY / totalH));
                int targetIndex = (int) Math.round(fraction * (images.size() - 1));
                thumbnailPanel.scrollToImage(targetIndex);
            }
        };
        htmlScroll.getViewport().addChangeListener(scrollSyncListener);
    }

    private void removeScrollSync() {
        if (scrollSyncListener != null) {
            htmlScroll.getViewport().removeChangeListener(scrollSyncListener);
            scrollSyncListener = null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HTML wrapping
    // ═══════════════════════════════════════════════════════════

    static String wrapHtml(String bodyHtml) {
        return wrapHtml(bodyHtml, null);
    }

    static String wrapHtml(String bodyHtml, String baseUrl) {
        StringBuilder sb = new StringBuilder("<html><head>");
        if (baseUrl != null && !baseUrl.isEmpty()) {
            sb.append("<base href=\"").append(baseUrl.replace("\"", "&quot;")).append("\">");
        }
        sb.append("<style>")
                .append("body { font-family: sans-serif; font-size: 13px; padding: 12px; }")
                .append("h1, h2, h3 { color: #333; }")
                .append("a { color: #0645ad; }")
                .append("pre, code { background: #f4f4f4; padding: 4px; }")
                .append("table { border-collapse: collapse; }")
                .append("td, th { border: 1px solid #ccc; padding: 4px; }")
                .append("img { max-width: 100%; }")
                .append(".confluence-information-macro, .confluence-information-macro-body { ")
                .append("  background: #e8f5e9; border-left: 4px solid #4caf50; padding: 8px; margin: 8px 0; }")
                .append(".confluence-warning-macro, .confluence-warning-macro-body { ")
                .append("  background: #fff3e0; border-left: 4px solid #ff9800; padding: 8px; margin: 8px 0; }")
                .append("</style>")
                .append("</head><body>")
                .append(bodyHtml)
                .append("</body></html>");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════
    //  Asynchronous image loading (rendered mode only)
    // ═══════════════════════════════════════════════════════════

    private void loadImagesAsync(String html) {
        if (html == null || client == null) return;

        new SwingWorker<Hashtable<URL, Image>, Object[]>() {
            private final Hashtable<URL, Image> loaded = new Hashtable<URL, Image>();

            @Override
            protected Hashtable<URL, Image> doInBackground() {
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
                            URL imageUrl = resolveImageUrl(src);
                            if (imageUrl != null) {
                                loaded.put(imageUrl, image);
                                publish(new Object[]{imageUrl, image});
                            }
                        }
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "[ConfluenceReader] Image load failed: " + src, e);
                    }
                }
                return loaded;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void process(java.util.List<Object[]> chunks) {
                Document doc = htmlPane.getDocument();
                if (!(doc instanceof HTMLDocument)) return;

                Dictionary<URL, Image> cache = (Dictionary<URL, Image>) doc.getProperty("imageCache");
                if (cache == null) {
                    cache = new Hashtable<URL, Image>();
                    doc.putProperty("imageCache", cache);
                }
                for (Object[] pair : chunks) {
                    cache.put((URL) pair[0], (Image) pair[1]);
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                try {
                    Hashtable<URL, Image> allImages = get();
                    if (allImages == null || allImages.isEmpty()) return;

                    Document doc = htmlPane.getDocument();
                    doc.putProperty("imageCache", allImages);

                    int caretPos = Math.min(htmlPane.getCaretPosition(), doc.getLength());

                    htmlPane.setText(renderedFullHtml);

                    Document newDoc = htmlPane.getDocument();
                    if (newDoc != doc) {
                        newDoc.putProperty("imageCache", allImages);
                        htmlPane.setText(renderedFullHtml);
                    }

                    try {
                        htmlPane.setCaretPosition(Math.min(caretPos,
                                htmlPane.getDocument().getLength()));
                    } catch (Exception ignored) { }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "[ConfluenceReader] Failed to refresh after image load", e);
                }
            }
        }.execute();
    }

    /**
     * Resolve an img src to a relative REST path for the ConfluenceRestClient.
     */
    private String resolveImagePath(String src) {
        if (src == null || src.isEmpty()) return null;
        if (src.startsWith(baseUrl)) return src.substring(baseUrl.length());
        if (src.startsWith("/")) return src;
        if (src.startsWith("data:") || src.startsWith("http://") || src.startsWith("https://"))
            return null;
        return "/" + src;
    }

    private URL resolveImageUrl(String src) {
        try {
            return new URL(new URL(baseUrl), src);
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

        String linkedPageId = extractPageId(url);
        if (linkedPageId != null && linkCallback != null) {
            linkCallback.openLinkedPage(linkedPageId);
            return;
        }

        try {
            Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception ex) {
            LOG.log(Level.FINE, "[ConfluenceReader] Could not open external link", ex);
        }
    }

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
    //  Image click helpers (rendered mode)
    // ═══════════════════════════════════════════════════════════

    private static String extractImgSrc(javax.swing.text.Element elem) {
        if (elem == null) return null;
        javax.swing.text.AttributeSet as = elem.getAttributes();
        if (as.getAttribute(javax.swing.text.StyleConstants.NameAttribute)
                == javax.swing.text.html.HTML.Tag.IMG) {
            return (String) as.getAttribute(javax.swing.text.html.HTML.Attribute.SRC);
        }
        Object imgAs = as.getAttribute(javax.swing.text.html.HTML.Tag.IMG);
        if (imgAs instanceof javax.swing.text.AttributeSet) {
            return (String) ((javax.swing.text.AttributeSet) imgAs)
                    .getAttribute(javax.swing.text.html.HTML.Attribute.SRC);
        }
        javax.swing.text.Element parent = elem.getParentElement();
        if (parent != null) {
            javax.swing.text.AttributeSet pas = parent.getAttributes();
            if (pas.getAttribute(javax.swing.text.StyleConstants.NameAttribute)
                    == javax.swing.text.html.HTML.Tag.IMG) {
                return (String) pas.getAttribute(javax.swing.text.html.HTML.Attribute.SRC);
            }
            Object pImgAs = pas.getAttribute(javax.swing.text.html.HTML.Tag.IMG);
            if (pImgAs instanceof javax.swing.text.AttributeSet) {
                return (String) ((javax.swing.text.AttributeSet) pImgAs)
                        .getAttribute(javax.swing.text.html.HTML.Attribute.SRC);
            }
        }
        return null;
    }

    private static int findImageIndex(List<ImageRef> images, String src) {
        if (src == null || images == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (src.equals(images.get(i).src())) return i;
        }
        for (int i = 0; i < images.size(); i++) {
            String imgSrc = images.get(i).src();
            if (imgSrc != null && (imgSrc.contains(src) || src.contains(imgSrc))) return i;
        }
        int lastSlash = src.lastIndexOf('/');
        String srcFile = lastSlash >= 0 && lastSlash < src.length() - 1
                ? src.substring(lastSlash + 1) : src;
        int qMark = srcFile.indexOf('?');
        if (qMark > 0) srcFile = srcFile.substring(0, qMark);
        for (int i = 0; i < images.size(); i++) {
            String imgSrc = images.get(i).src();
            if (imgSrc != null && imgSrc.contains(srcFile)) return i;
        }
        return -1;
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

