package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.AttachmentRef;
import de.bund.zrb.wiki.domain.ImageRef;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Reusable HTML preview panel with image strip / thumbnail / inline-image support.
 * <p>
 * Used by WikiConnectionTab, ConfluenceConnectionTab, and SearchTab to display
 * HTML content with a consistent image preview experience:
 * <ul>
 *   <li><b>Text mode</b> (default): images stripped from HTML, shown as icons
 *       in a narrow {@link ImageStripPanel} on the right; expandable into
 *       {@link ImageThumbnailPanel} via ◀◀ button</li>
 *   <li><b>Rendered mode</b>: images displayed inline within the HTML via
 *       {@link WikiAsyncImageLoader}</li>
 * </ul>
 * Callers set content via {@link #setContent(String, String, List)} or
 * {@link #setContent(String, String, List, String)}.
 * Mode toggle buttons are optional — callers can include them in their own
 * toolbar via {@link #getTextModeButton()} / {@link #getRenderedModeButton()},
 * or let the panel manage its own toolbar via {@link #setShowModeToggle(boolean)}.
 */
public class HtmlPreviewPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(HtmlPreviewPanel.class.getName());

    // ── Core UI components ──
    private final JEditorPane htmlPane;
    private final JScrollPane htmlScrollPane;
    /** Outer panel that holds htmlScrollPane + optional ImageStripPanel (EAST). */
    private final JPanel contentPanel;

    // ── Mode toggle ──
    private boolean renderedMode = false;
    private JToggleButton textModeBtn;
    private JToggleButton renderedModeBtn;
    private JPanel modeToolbar;
    private boolean showModeToggle = false;

    // ── Current content state ──
    private String cleanedHtml;       // HTML without images (for text mode)
    private String htmlWithImages;    // HTML with images (for rendered mode)
    private List<ImageRef> images = Collections.emptyList();
    private String baseUrl;           // optional base URL for relative image resolution

    // ── Image strip / thumbnail state ──
    private boolean imageStripExpanded = false;
    private JSplitPane imageSplitPane;
    private ImageThumbnailPanel thumbnailPanel;
    private int lastDividerLocation = -1;
    private ChangeListener scrollSyncListener;

    // ── Document attachments ──
    /** Document (non-image) attachments shown alongside images. */
    private List<AttachmentRef> attachments = Collections.emptyList();
    /** Optional renderer for document thumbnails (e.g. PDF first-page). */
    private DocumentThumbnailRenderer documentThumbnailRenderer;
    /** Optional callback when user clicks a document attachment. */
    private ImageStripPanel.DocumentClickCallback documentClickCallback;

    /** Optional authenticated downloader (e.g. for Confluence mTLS). */
    private ByteDownloader byteDownloader;

    /** Optional hyperlink listener for the HTML pane (e.g. for wiki links). */
    private HyperlinkListener externalHyperlinkListener;

    public HtmlPreviewPanel() {
        super(new BorderLayout(0, 0));

        // HTML preview pane
        htmlPane = new JEditorPane();
        htmlPane.setContentType("text/html");
        htmlPane.setEditable(false);
        htmlPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        htmlPane.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
                if (externalHyperlinkListener != null) {
                    externalHyperlinkListener.hyperlinkUpdate(e);
                }
            }
        });

        htmlScrollPane = new JScrollPane(htmlPane);

        // Content panel with BorderLayout (CENTER=htmlScroll, EAST=optional strip)
        contentPanel = new JPanel(new BorderLayout(0, 0));
        contentPanel.add(htmlScrollPane, BorderLayout.CENTER);

        add(contentPanel, BorderLayout.CENTER);

        // Mode toggle buttons (hidden by default)
        createModeToggle();
    }

    // ═══════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════

    /**
     * Set the content to display.
     *
     * @param cleanedHtml    HTML body without images (shown in text mode)
     * @param htmlWithImages HTML body with images (shown in rendered mode, may be null)
     * @param images         extracted image references (may be null or empty)
     */
    public void setContent(String cleanedHtml, String htmlWithImages, List<ImageRef> images) {
        setContent(cleanedHtml, htmlWithImages, images, null);
    }

    /**
     * Set the content to display.
     *
     * @param cleanedHtml    HTML body without images (shown in text mode)
     * @param htmlWithImages HTML body with images (shown in rendered mode, may be null)
     * @param images         extracted image references (may be null or empty)
     * @param baseUrl        optional base URL for resolving relative image paths
     */
    public void setContent(String cleanedHtml, String htmlWithImages,
                           List<ImageRef> images, String baseUrl) {
        this.cleanedHtml = cleanedHtml;
        this.htmlWithImages = htmlWithImages;
        this.images = images != null ? images : Collections.<ImageRef>emptyList();
        this.baseUrl = baseUrl;
        applyContent();
    }

    /**
     * Convenience: set raw HTML content and auto-extract images.
     * Suitable for Confluence / SearchTab where raw HTML is available.
     *
     * @param rawHtml raw HTML body (images will be extracted and stripped)
     * @param baseUrl optional base URL for relative image resolution
     */
    public void setRawHtmlContent(String rawHtml, String baseUrl) {
        if (rawHtml == null) {
            setContent(null, null, null, baseUrl);
            return;
        }
        List<ImageRef> extracted = HtmlImageExtractor.extractImages(rawHtml, baseUrl);
        String stripped = HtmlImageExtractor.stripImgTags(rawHtml);
        setContent(stripped, rawHtml, extracted, baseUrl);
    }

    /**
     * Set plain text content (no images, no HTML).
     *
     * @param text plain text to display
     */
    public void setPlainText(String text) {
        this.cleanedHtml = null;
        this.htmlWithImages = null;
        this.images = Collections.emptyList();
        this.baseUrl = null;

        collapseImageStripSilently();
        removeEastComponent();

        if (text != null) {
            String escaped = escHtml(text);
            htmlPane.setText(wrapHtml("<pre style='white-space:pre-wrap;font-family:Consolas,monospace;"
                    + "font-size:11px;'>" + escaped + "</pre>"));
        } else {
            htmlPane.setText("");
        }
        htmlPane.setCaretPosition(0);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /** Clear all content. */
    public void clear() {
        this.cleanedHtml = null;
        this.htmlWithImages = null;
        this.images = Collections.emptyList();
        collapseImageStripSilently();
        removeEastComponent();
        htmlPane.setText("");
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /** Get the underlying JEditorPane (for direct access, e.g. for hyperlink listeners). */
    public JEditorPane getHtmlPane() {
        return htmlPane;
    }

    /** Get the scroll pane wrapping the HTML pane. */
    public JScrollPane getHtmlScrollPane() {
        return htmlScrollPane;
    }

    /** Set an authenticated byte downloader (e.g. for Confluence mTLS images). */
    public void setByteDownloader(ByteDownloader downloader) {
        this.byteDownloader = downloader;
    }

    /** Set a hyperlink listener for the HTML pane. */
    public void setHyperlinkListener(HyperlinkListener listener) {
        this.externalHyperlinkListener = listener;
    }

    /** @return the text-mode toggle button (for embedding in external toolbars). */
    public JToggleButton getTextModeButton() {
        return textModeBtn;
    }

    /** @return the rendered-mode toggle button (for embedding in external toolbars). */
    public JToggleButton getRenderedModeButton() {
        return renderedModeBtn;
    }

    /** Whether to show a built-in mode toggle toolbar. Default: false. */
    public void setShowModeToggle(boolean show) {
        this.showModeToggle = show;
        if (show) {
            add(modeToolbar, BorderLayout.NORTH);
        } else {
            remove(modeToolbar);
        }
        revalidate();
        repaint();
    }

    /** Whether currently in rendered mode (images inline). */
    public boolean isRenderedMode() {
        return renderedMode;
    }

    /** Set rendered mode programmatically. */
    public void setRenderedMode(boolean rendered) {
        this.renderedMode = rendered;
        textModeBtn.setSelected(!rendered);
        renderedModeBtn.setSelected(rendered);
        applyContent();
    }

    // ── Attachment API ──

    /**
     * Set document (non-image) attachments to show alongside images.
     * Call this before or after {@link #setContent} — the strip/thumbnail panels
     * will include document icons/cards.
     *
     * @param attachments document attachments (may include images — image-type entries
     *                    are filtered out because images use {@link ImageRef})
     */
    public void setAttachments(List<AttachmentRef> attachments) {
        this.attachments = attachments != null ? attachments : Collections.<AttachmentRef>emptyList();
        applyContent();
    }

    /**
     * Set document attachments together with content in a single call.
     *
     * @param cleanedHtml    HTML body without images
     * @param htmlWithImages HTML body with images (may be null)
     * @param images         image references (may be null/empty)
     * @param attachments    document attachments (may be null/empty)
     * @param baseUrl        optional base URL
     */
    public void setContentWithAttachments(String cleanedHtml, String htmlWithImages,
                                          List<ImageRef> images, List<AttachmentRef> attachments,
                                          String baseUrl) {
        this.cleanedHtml = cleanedHtml;
        this.htmlWithImages = htmlWithImages;
        this.images = images != null ? images : Collections.<ImageRef>emptyList();
        this.attachments = attachments != null ? attachments : Collections.<AttachmentRef>emptyList();
        this.baseUrl = baseUrl;
        applyContent();
    }

    /**
     * Set a renderer for generating document thumbnails (e.g. PDF first page).
     * The wiki-integration module has no PDFBox dependency — callers from the app
     * module can inject a renderer that uses PDFBox or any other library.
     */
    public void setDocumentThumbnailRenderer(DocumentThumbnailRenderer renderer) {
        this.documentThumbnailRenderer = renderer;
    }

    /**
     * Set a callback for document attachment clicks (e.g. open in external viewer).
     */
    public void setDocumentClickCallback(ImageStripPanel.DocumentClickCallback callback) {
        this.documentClickCallback = callback;
    }

    /** @return the current document attachments. */
    public List<AttachmentRef> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }

    // ═══════════════════════════════════════════════════════════
    //  Content rendering
    // ═══════════════════════════════════════════════════════════

    private void applyContent() {
        if (cleanedHtml == null && htmlWithImages == null) {
            // Even with no HTML, we may still have document attachments to show
            if (!attachments.isEmpty()) {
                removeEastComponent();
                if (imageStripExpanded) collapseImageStripSilently();
                htmlPane.setText("");
                ImageStripPanel strip = createStripPanel();
                if (strip.hasContent()) {
                    contentPanel.add(strip, BorderLayout.EAST);
                }
                contentPanel.revalidate();
                contentPanel.repaint();
                return;
            }
            clear();
            return;
        }

        // Collapse expanded thumbnails if switching content
        if (imageStripExpanded) {
            collapseImageStripSilently();
        }

        // Remove existing image strip (EAST component)
        removeEastComponent();

        if (renderedMode && htmlWithImages != null) {
            // ── Rendered mode: images inline ─────────────────────
            String fullHtml = buildRenderedHtml(htmlWithImages);
            htmlPane.setText(fullHtml);
            htmlPane.setCaretPosition(0);
            // Load images asynchronously
            WikiAsyncImageLoader.loadImagesAsync(htmlPane, htmlWithImages, fullHtml,
                    baseUrl, byteDownloader);

            // Even in rendered mode, show document attachments in the strip
            if (!attachments.isEmpty()) {
                ImageStripPanel strip = createStripPanel();
                if (strip.hasContent()) {
                    contentPanel.add(strip, BorderLayout.EAST);
                }
            }
        } else {
            // ── Text mode: images in side strip ──────────────────
            String textHtml = cleanedHtml != null ? cleanedHtml : htmlWithImages;
            if (textHtml != null && htmlWithImages == null) {
                // If we only have cleanedHtml (already stripped), use it directly
                htmlPane.setText(wrapHtml(textHtml));
            } else if (textHtml != null) {
                // Strip images for text mode
                String stripped = HtmlImageExtractor.stripImgTags(textHtml);
                htmlPane.setText(wrapHtml(stripped));
            } else {
                htmlPane.setText("");
            }
            htmlPane.setCaretPosition(0);

            // Show strip if we have images OR document attachments
            if (!images.isEmpty() || !attachments.isEmpty()) {
                ImageStripPanel strip = createStripPanel();
                if (strip.hasContent()) {
                    contentPanel.add(strip, BorderLayout.EAST);
                }
            }
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    // ═══════════════════════════════════════════════════════════
    //  Image strip expand / collapse
    // ═══════════════════════════════════════════════════════════

    private void expandImageStrip() {
        if ((images.isEmpty() && attachments.isEmpty()) || imageStripExpanded || renderedMode) return;
        imageStripExpanded = true;

        // Remove ImageStripPanel
        removeEastComponent();

        // Detach htmlScrollPane
        contentPanel.remove(htmlScrollPane);

        // Create thumbnail panel with images + document attachments
        thumbnailPanel = new ImageThumbnailPanel(images, attachments, byteDownloader);
        thumbnailPanel.setCollapseCallback(new Runnable() {
            @Override
            public void run() {
                collapseImageStrip();
            }
        });
        if (documentThumbnailRenderer != null) {
            thumbnailPanel.setDocumentThumbnailRenderer(documentThumbnailRenderer);
        }
        if (documentClickCallback != null) {
            thumbnailPanel.setDocumentClickCallback(documentClickCallback);
        }

        // Create horizontal split pane
        imageSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, htmlScrollPane, thumbnailPanel);
        imageSplitPane.setResizeWeight(0.7);
        int divLoc = lastDividerLocation > 0 ? lastDividerLocation
                : contentPanel.getWidth() * 3 / 4;
        imageSplitPane.setDividerLocation(divLoc);

        contentPanel.add(imageSplitPane, BorderLayout.CENTER);
        installScrollSync();
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void collapseImageStrip() {
        if (!imageStripExpanded) return;
        if (imageSplitPane != null) {
            lastDividerLocation = imageSplitPane.getDividerLocation();
        }
        collapseImageStripSilently();

        // Re-add strip panel
        if (!images.isEmpty() || !attachments.isEmpty()) {
            ImageStripPanel strip = createStripPanel();
            if (strip.hasContent()) {
                contentPanel.add(strip, BorderLayout.EAST);
            }
        }
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void collapseImageStripSilently() {
        if (!imageStripExpanded) return;
        imageStripExpanded = false;
        removeScrollSync();
        if (imageSplitPane != null) {
            imageSplitPane.remove(htmlScrollPane);
            contentPanel.remove(imageSplitPane);
        }
        contentPanel.add(htmlScrollPane, BorderLayout.CENTER);
        imageSplitPane = null;
        thumbnailPanel = null;
    }

    // ═══════════════════════════════════════════════════════════
    //  Scroll sync between HTML and thumbnails
    // ═══════════════════════════════════════════════════════════

    private void installScrollSync() {
        if (scrollSyncListener != null) return;
        scrollSyncListener = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (thumbnailPanel == null || images.isEmpty()) return;
                JViewport viewport = htmlScrollPane.getViewport();
                int viewY = viewport.getViewPosition().y;
                int totalH = htmlPane.getPreferredSize().height - viewport.getHeight();
                if (totalH <= 0) return;
                double fraction = Math.min(1.0, Math.max(0.0, (double) viewY / totalH));
                int targetIndex = (int) Math.round(fraction * (images.size() - 1));
                thumbnailPanel.scrollToImage(targetIndex);
            }
        };
        htmlScrollPane.getViewport().addChangeListener(scrollSyncListener);
    }

    private void removeScrollSync() {
        if (scrollSyncListener != null) {
            htmlScrollPane.getViewport().removeChangeListener(scrollSyncListener);
            scrollSyncListener = null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Mode toggle
    // ═══════════════════════════════════════════════════════════

    private void createModeToggle() {
        textModeBtn = new JToggleButton("📝 Text", true);
        textModeBtn.setFont(textModeBtn.getFont().deriveFont(11f));
        textModeBtn.setFocusPainted(false);
        textModeBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!renderedMode) return;
                renderedMode = false;
                renderedModeBtn.setSelected(false);
                applyContent();
            }
        });

        renderedModeBtn = new JToggleButton("🖼 Bilder", false);
        renderedModeBtn.setFont(renderedModeBtn.getFont().deriveFont(11f));
        renderedModeBtn.setFocusPainted(false);
        renderedModeBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (renderedMode) return;
                renderedMode = true;
                textModeBtn.setSelected(false);
                applyContent();
            }
        });

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(textModeBtn);
        modeGroup.add(renderedModeBtn);

        modeToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        modeToolbar.add(new JLabel("Ansicht:"));
        modeToolbar.add(textModeBtn);
        modeToolbar.add(renderedModeBtn);
    }

    // ═══════════════════════════════════════════════════════════
    //  HTML helpers
    // ═══════════════════════════════════════════════════════════

    /**
     * Create a configured {@link ImageStripPanel} with images + document attachments.
     */
    private ImageStripPanel createStripPanel() {
        ImageStripPanel strip = new ImageStripPanel(images, attachments, byteDownloader);
        strip.setExpandCallback(new Runnable() {
            @Override
            public void run() {
                expandImageStrip();
            }
        });
        if (documentClickCallback != null) {
            strip.setDocumentClickCallback(documentClickCallback);
        }
        return strip;
    }

    private void removeEastComponent() {
        Component eastComp = ((BorderLayout) contentPanel.getLayout())
                .getLayoutComponent(BorderLayout.EAST);
        if (eastComp != null) {
            contentPanel.remove(eastComp);
        }
    }

    private String buildRenderedHtml(String bodyHtml) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head>");
        if (baseUrl != null) {
            sb.append("<base href=\"").append(escHtml(baseUrl)).append("\">");
        }
        sb.append("<style>");
        sb.append("body{font-family:sans-serif;font-size:13px;padding:8px;}");
        sb.append("table{border-collapse:collapse;} td,th{border:1px solid #ccc;padding:4px;}");
        sb.append("img{max-width:100%;height:auto;} h1,h2,h3{color:#333;} a{color:#0645ad;}");
        sb.append("pre,code{background:#f4f4f4;padding:4px;}");
        sb.append(".thumb,figure{margin:8px 0;}");
        sb.append("</style></head><body>");
        sb.append(bodyHtml);
        sb.append("</body></html>");
        return sb.toString();
    }

    static String wrapHtml(String bodyHtml) {
        return "<html><head><style>"
                + "body{font-family:sans-serif;font-size:13px;margin:12px;}"
                + "h1,h2,h3{color:#333;} a{color:#0645ad;}"
                + "pre,code{background:#f4f4f4;padding:4px;}"
                + "table{border-collapse:collapse;} td,th{border:1px solid #ccc;padding:4px;}"
                + "</style></head><body>"
                + bodyHtml
                + "</body></html>";
    }

    static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

