package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.ImageRef;
import de.bund.zrb.wiki.domain.OutlineNode;
import de.zrb.bund.newApi.ui.ConnectionTab;
import de.zrb.bund.newApi.ui.FindBarPanel;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Read-only tab for displaying a single wiki page.
 * Links open new WikiFileTabs instead of navigating in-place.
 * Provides outline to RightDrawer and a search bar at the bottom.
 */
public class WikiFileTab implements ConnectionTab {

    private static final Logger LOG = Logger.getLogger(WikiFileTab.class.getName());

    private final JPanel mainPanel;
    private final JEditorPane htmlPane;
    private final FindBarPanel searchBar;
    private final String siteId;
    private final String pageTitle;
    private final String htmlContent;
    /** HTML with inline images (absolute URLs). May be {@code null} for legacy callers. */
    private final String htmlWithImages;
    private final OutlineNode outline;
    private final List<ImageRef> images;

    /** Yellow highlight painter for search hits. */
    private static final Highlighter.HighlightPainter YELLOW_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW);

    /** Callback for opening linked pages as new tabs. */
    private LinkCallback linkCallback;

    /** true = rendered mode (images inline), false = text mode (images in side strip). */
    private boolean renderedMode = false;
    private JToggleButton textModeBtn;
    private JToggleButton renderedModeBtn;
    /** Center panel wrapping htmlScroll + optional image strip or split pane. */
    private JPanel centerPanel;
    /** Scroll pane wrapping htmlPane — kept as field for split-pane attach/detach. */
    private JScrollPane htmlScroll;

    /** true when the image strip is expanded into the thumbnail split pane. */
    private boolean imageStripExpanded = false;
    /** The horizontal split pane used in expanded thumbnail mode. */
    private JSplitPane imageSplitPane;
    /** The thumbnail panel shown in expanded mode. */
    private ImageThumbnailPanel thumbnailPanel;
    /** Remembers last divider position so re-expanding restores the same width. */
    private int lastDividerLocation = -1;
    /** Scroll-sync listener (added/removed on expand/collapse). */
    private ChangeListener scrollSyncListener;

    public WikiFileTab(String siteId, String pageTitle, String htmlContent, OutlineNode outline) {
        this(siteId, pageTitle, htmlContent, null, outline, Collections.<ImageRef>emptyList());
    }

    public WikiFileTab(String siteId, String pageTitle, String htmlContent,
                       OutlineNode outline, List<ImageRef> images) {
        this(siteId, pageTitle, htmlContent, null, outline, images);
    }

    public WikiFileTab(String siteId, String pageTitle, String htmlContent,
                       String htmlWithImages, OutlineNode outline, List<ImageRef> images) {
        this.siteId = siteId;
        this.pageTitle = pageTitle;
        this.htmlContent = htmlContent;
        this.htmlWithImages = htmlWithImages;
        this.outline = outline;
        this.images = images != null ? images : Collections.<ImageRef>emptyList();
        this.mainPanel = new JPanel(new BorderLayout(0, 0));

        // HTML pane
        htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        htmlPane.setContentType("text/html");
        htmlPane.setText(WikiConnectionTab.wrapHtml(htmlContent));
        htmlPane.setCaretPosition(0);
        htmlPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                handleLink(e);
            }
        });

        // Click on inline image in rendered mode → open image overlay dialog
        htmlPane.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (!renderedMode || WikiFileTab.this.images.isEmpty()) return;
                int pos = htmlPane.viewToModel(e.getPoint());
                if (pos < 0) return;
                javax.swing.text.Document doc = htmlPane.getDocument();
                if (!(doc instanceof javax.swing.text.html.HTMLDocument)) return;
                javax.swing.text.Element elem =
                        ((javax.swing.text.html.HTMLDocument) doc).getCharacterElement(pos);
                if (elem == null) return;
                String src = extractImgSrc(elem);
                if (src == null) return;
                int index = findImageIndex(WikiFileTab.this.images, src);
                if (index >= 0) {
                    ImageStripPanel.openOverlay(htmlPane, WikiFileTab.this.images, index);
                }
            }
        });
        // Hand cursor when hovering over images in rendered mode
        htmlPane.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                if (!renderedMode || WikiFileTab.this.images.isEmpty()) {
                    htmlPane.setCursor(Cursor.getDefaultCursor());
                    return;
                }
                int pos = htmlPane.viewToModel(e.getPoint());
                if (pos >= 0) {
                    javax.swing.text.Document doc = htmlPane.getDocument();
                    if (doc instanceof javax.swing.text.html.HTMLDocument) {
                        javax.swing.text.Element elem =
                                ((javax.swing.text.html.HTMLDocument) doc).getCharacterElement(pos);
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

        // Image strip on the right (only if images exist, text mode default)
        if (!this.images.isEmpty()) {
            ImageStripPanel strip = new ImageStripPanel(this.images);
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

        // Find bar at bottom
        searchBar = new FindBarPanel("Im Dokument suchen\u2026");
        searchBar.addSearchAction(e -> highlightSearch());

        // Bottom bar: find bar (center) + view mode toggles (right)
        JPanel bottomBar = new JPanel(new BorderLayout(4, 0));
        bottomBar.add(searchBar, BorderLayout.CENTER);
        bottomBar.add(togglePanel, BorderLayout.EAST);
        mainPanel.add(bottomBar, BorderLayout.SOUTH);
    }

    private void handleLink(HyperlinkEvent e) {
        String desc = e.getDescription();
        if (desc != null && desc.startsWith("#")) {
            htmlPane.scrollToReference(desc.substring(1));
            return;
        }

        String url = e.getURL() != null ? e.getURL().toString() : desc;
        if (url == null) return;

        String linkedTitle = WikiConnectionTab.extractPageTitle(url);
        if (linkedTitle != null && linkCallback != null) {
            linkCallback.openLinkedPage(siteId, linkedTitle);
        } else if (e.getURL() != null) {
            try {
                Desktop.getDesktop().browse(e.getURL().toURI());
            } catch (Exception ex) {
                LOG.log(Level.FINE, "[WikiFileTab] Could not open external link", ex);
            }
        }
    }

    /**
     * Highlight all occurrences of the search query using Swing's Highlighter API.
     * This preserves all HTML formatting (bold, headings, anchors) and scrolls to the first hit.
     */
    private void highlightSearch() {
        Highlighter highlighter = htmlPane.getHighlighter();
        highlighter.removeAllHighlights();

        String query = searchBar.getText().trim();
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

            // Scroll to the first hit
            if (firstHit >= 0) {
                Rectangle rect = htmlPane.modelToView(firstHit);
                if (rect != null) {
                    htmlPane.scrollRectToVisible(rect);
                }
                htmlPane.setCaretPosition(firstHit);
            }
        } catch (BadLocationException ex) {
            LOG.log(Level.FINE, "[WikiFileTab] Search highlight error", ex);
        }
    }

    /**
     * Scroll to a heading anchor (called from RightDrawer outline).
     */
    public void scrollToAnchor(String anchor) {
        if (anchor != null) {
            htmlPane.scrollToReference(anchor);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  View mode switching
    // ═══════════════════════════════════════════════════════════

    /**
     * Re-apply content with the currently selected view mode.
     * Collapsing any expanded thumbnail panel on mode switch.
     */
    private void refreshViewMode() {
        // Collapse expanded thumbnails first
        if (imageStripExpanded) {
            collapseImageStripSilently();
        }

        // Remove existing EAST component (strip)
        Component eastComp = ((BorderLayout) centerPanel.getLayout())
                .getLayoutComponent(BorderLayout.EAST);
        if (eastComp != null) {
            centerPanel.remove(eastComp);
        }

        if (renderedMode && htmlWithImages != null) {
            // ── Rendered mode: images inline ─────────────────────
            String fullHtml = WikiAsyncImageLoader.wrapHtmlWithImages(htmlWithImages);
            htmlPane.setText(fullHtml);
            htmlPane.setCaretPosition(0);
            WikiAsyncImageLoader.loadImagesAsync(htmlPane, htmlWithImages, fullHtml);
        } else {
            // ── Text mode: images in side strip ──────────────────
            htmlPane.setText(WikiConnectionTab.wrapHtml(htmlContent));
            htmlPane.setCaretPosition(0);
            if (!images.isEmpty()) {
                ImageStripPanel strip = new ImageStripPanel(images);
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

    /**
     * Expand the narrow image strip into a resizable thumbnail split pane.
     */
    private void expandImageStrip() {
        if (images.isEmpty() || imageStripExpanded || renderedMode) return;
        imageStripExpanded = true;

        // Remove ImageStripPanel
        Component eastComp = ((BorderLayout) centerPanel.getLayout())
                .getLayoutComponent(BorderLayout.EAST);
        if (eastComp != null) centerPanel.remove(eastComp);

        // Detach htmlScroll from centerPanel
        centerPanel.remove(htmlScroll);

        // Create thumbnail panel
        thumbnailPanel = new ImageThumbnailPanel(images);
        thumbnailPanel.setCollapseCallback(this::collapseImageStrip);

        // Create horizontal split pane
        imageSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, htmlScroll, thumbnailPanel);
        imageSplitPane.setResizeWeight(0.7);
        int divLoc = lastDividerLocation > 0 ? lastDividerLocation
                : centerPanel.getWidth() * 3 / 4;
        imageSplitPane.setDividerLocation(divLoc);

        centerPanel.add(imageSplitPane, BorderLayout.CENTER);

        // Install scroll sync
        installScrollSync();

        centerPanel.revalidate();
        centerPanel.repaint();
    }

    /**
     * Collapse the thumbnail split pane back to the narrow image strip.
     */
    private void collapseImageStrip() {
        if (!imageStripExpanded) return;

        // Remember divider position for next expand
        if (imageSplitPane != null) {
            lastDividerLocation = imageSplitPane.getDividerLocation();
        }

        collapseImageStripSilently();

        // Re-add ImageStripPanel
        ImageStripPanel strip = new ImageStripPanel(images);
        strip.setExpandCallback(this::expandImageStrip);
        centerPanel.add(strip, BorderLayout.EAST);

        centerPanel.revalidate();
        centerPanel.repaint();
    }

    /**
     * Internal collapse without re-adding the strip (used by mode-switch too).
     */
    private void collapseImageStripSilently() {
        if (!imageStripExpanded) return;
        imageStripExpanded = false;

        // Remove scroll sync
        removeScrollSync();

        // Detach from split pane
        if (imageSplitPane != null) {
            imageSplitPane.remove(htmlScroll);
            centerPanel.remove(imageSplitPane);
        }

        // Re-add htmlScroll directly
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

    public OutlineNode getOutline() { return outline; }
    public String getSiteId() { return siteId; }
    public String getPageTitle() { return pageTitle; }

    // ═══════════════════════════════════════════════════════════
    //  ConnectionTab interface
    // ═══════════════════════════════════════════════════════════

    @Override public String getTitle() { return "📖 " + pageTitle; }
    @Override public String getTooltip() { return "Wiki: " + pageTitle + " (" + siteId + ")"; }
    @Override public JComponent getComponent() { return mainPanel; }
    @Override public void onClose() { /* nothing */ }
    @Override public void saveIfApplicable() { /* read-only */ }
    @Override public String getContent() { return htmlContent; }
    @Override public void markAsChanged() { /* not applicable */ }
    @Override public String getPath() { return "wiki://" + siteId + "/" + pageTitle; }
    @Override public Type getType() { return Type.PREVIEW; }

    @Override
    public void focusSearchField() {
        searchBar.focusField();
    }

    @Override
    public void searchFor(String searchPattern) {
        searchBar.setText(searchPattern);
        highlightSearch();
    }

    // ═══════════════════════════════════════════════════════════
    //  Image click helpers (rendered mode)
    // ═══════════════════════════════════════════════════════════

    /**
     * Extract the img src attribute from an HTMLDocument element.
     * Checks both direct IMG tag and nested IMG attribute set.
     */
    private static String extractImgSrc(javax.swing.text.Element elem) {
        if (elem == null) return null;
        javax.swing.text.AttributeSet as = elem.getAttributes();
        // Case 1: element IS an img (tag name = img)
        if (as.getAttribute(javax.swing.text.StyleConstants.NameAttribute)
                == javax.swing.text.html.HTML.Tag.IMG) {
            return (String) as.getAttribute(javax.swing.text.html.HTML.Attribute.SRC);
        }
        // Case 2: content element with nested IMG attribute set
        Object imgAs = as.getAttribute(javax.swing.text.html.HTML.Tag.IMG);
        if (imgAs instanceof javax.swing.text.AttributeSet) {
            return (String) ((javax.swing.text.AttributeSet) imgAs)
                    .getAttribute(javax.swing.text.html.HTML.Attribute.SRC);
        }
        // Case 3: check parent element
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

    /**
     * Find the index of the ImageRef matching the given src URL.
     * Tries exact match, then containment, then filename match.
     */
    private static int findImageIndex(List<ImageRef> images, String src) {
        if (src == null || images == null) return -1;
        // Exact match
        for (int i = 0; i < images.size(); i++) {
            if (src.equals(images.get(i).src())) return i;
        }
        // Containment match (one URL embedded in the other)
        for (int i = 0; i < images.size(); i++) {
            String imgSrc = images.get(i).src();
            if (imgSrc != null && (imgSrc.contains(src) || src.contains(imgSrc))) return i;
        }
        // Filename match (last path segment)
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
    //  Link callback
    // ═══════════════════════════════════════════════════════════

    public void setLinkCallback(LinkCallback callback) {
        this.linkCallback = callback;
    }

    public interface LinkCallback {
        void openLinkedPage(String siteId, String pageTitle);
    }
}
