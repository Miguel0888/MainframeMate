package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.ImageRef;
import de.bund.zrb.wiki.domain.OutlineNode;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
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
    private final JTextField searchField;
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
    /** Center panel wrapping htmlScroll + optional image strip. */
    private JPanel centerPanel;

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

        JScrollPane htmlScroll = new JScrollPane(htmlPane);

        // Center panel wrapping htmlScroll + optional image strip
        centerPanel = new JPanel(new BorderLayout(0, 0));
        centerPanel.add(htmlScroll, BorderLayout.CENTER);

        // Image strip on the right (only if images exist, text mode default)
        if (!this.images.isEmpty()) {
            centerPanel.add(new ImageStripPanel(this.images), BorderLayout.EAST);
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
        searchBar.add(new JLabel(" 🔎 "), BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(togglePanel, BorderLayout.EAST);
        mainPanel.add(searchBar, BorderLayout.SOUTH);
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

    /**
     * Re-apply content with the currently selected view mode.
     */
    private void refreshViewMode() {
        // Remove existing image strip (EAST component)
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
                centerPanel.add(new ImageStripPanel(images), BorderLayout.EAST);
            }
        }

        centerPanel.revalidate();
        centerPanel.repaint();
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
        searchField.requestFocusInWindow();
    }

    @Override
    public void searchFor(String searchPattern) {
        searchField.setText(searchPattern);
        highlightSearch();
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
