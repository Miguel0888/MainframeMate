package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.OutlineNode;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

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
    private final OutlineNode outline;

    /** Callback for opening linked pages as new tabs. */
    private LinkCallback linkCallback;

    public WikiFileTab(String siteId, String pageTitle, String htmlContent, OutlineNode outline) {
        this.siteId = siteId;
        this.pageTitle = pageTitle;
        this.htmlContent = htmlContent;
        this.outline = outline;
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

        mainPanel.add(new JScrollPane(htmlPane), BorderLayout.CENTER);

        // Search bar at bottom
        searchField = new JTextField();
        searchField.setToolTipText("Text suchen (Enter)");
        searchField.addActionListener(e -> highlightSearch());
        JPanel searchBar = new JPanel(new BorderLayout(2, 0));
        searchBar.add(new JLabel(" 🔎 "), BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);
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

    private void highlightSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            htmlPane.setText(WikiConnectionTab.wrapHtml(htmlContent));
            htmlPane.setCaretPosition(0);
            return;
        }
        String highlighted = htmlContent.replaceAll(
                "(?i)(" + Pattern.quote(query) + ")",
                "<mark style='background:yellow'>$1</mark>");
        htmlPane.setText(WikiConnectionTab.wrapHtml(highlighted));
        htmlPane.setCaretPosition(0);
    }

    /**
     * Scroll to a heading anchor (called from RightDrawer outline).
     */
    public void scrollToAnchor(String anchor) {
        if (anchor != null) {
            htmlPane.scrollToReference(anchor);
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

