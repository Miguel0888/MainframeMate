package de.bund.zrb.ui;

import de.bund.zrb.model.BookmarkEntry;
import de.bund.zrb.ui.search.SearchTab;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.ui.AppTab;
import de.zrb.bund.newApi.ui.SearchBarPanel;

import javax.swing.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.List;

/**
 * A polished search control designed to sit inside a {@link JMenuBar}.
 * <p>
 * Extends the generic {@link SearchBarPanel} (rounded border, magnifying-glass
 * icon, placeholder, go-button) and adds menu-bar-specific behaviour:
 * <ul>
 *   <li>Enter / button-click opens a {@link SearchTab}</li>
 *   <li>Resource-path syntax ({@code ftp://}, {@code ndv:}, ...) triggers direct open</li>
 *   <li>Escape clears and returns focus to the active tab</li>
 * </ul>
 */
public class MenuBarSearchField extends SearchBarPanel {

    /** Protocol prefixes that trigger direct resource opening instead of search. */
    private static final List<String> RESOURCE_PREFIXES = Arrays.asList(
            BookmarkEntry.PREFIX_FTP,        // ftp://
            BookmarkEntry.PREFIX_NDV,        // ndv://
            BookmarkEntry.PREFIX_LOCAL,      // local://
            BookmarkEntry.PREFIX_MAIL,       // mail://
            BookmarkEntry.PREFIX_SHAREPOINT, // sp://
            BookmarkEntry.PREFIX_BETAVIEW,   // betaview://
            BookmarkEntry.PREFIX_TN3270,     // tn3270://
            BookmarkEntry.PREFIX_HTTP,       // http://
            BookmarkEntry.PREFIX_HTTPS,      // https://
            BookmarkEntry.PREFIX_CONFLUENCE, // confluence://
            BookmarkEntry.PREFIX_WIKI        // wiki://
    );

    /** Short-hand prefixes (without "//") that also trigger direct open. */
    private static final List<String> SHORT_PREFIXES = Arrays.asList(
            "ftp:", "ndv:", "local:", "mail:", "sp:", "betaview:", "tn3270:",
            "confluence:", "wiki:",
            "search-ftp:", "search-ndv:", "search-local:", "search-mail:",
            "search-sp:", "search-betaview:", "search-tn3270:",
            "search-confluence:", "search-wiki:"
    );

    private final TabbedPaneManager tabManager;

    public MenuBarSearchField(TabbedPaneManager tabManager) {
        super("\u00DCberall suchen\u2026");
        this.tabManager = tabManager;

        // -- Wire search action (Enter + go-button) --
        addSearchAction(e -> performSearch());

        // -- Escape: clear and return focus to active tab --
        getTextField().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    setText("");
                    tabManager.getSelectedTab().ifPresent(tab -> {
                        if (tab instanceof AppTab) {
                            ((AppTab) tab).getComponent().requestFocusInWindow();
                        }
                    });
                }
            }
        });
    }

    // =================================================================
    //  Search action
    // =================================================================

    private void performSearch() {
        String query = getText().trim();

        // -- Check for bookmark / resource syntax --
        if (!query.isEmpty() && isResourcePath(query)) {
            String resourcePath = normalizeResourcePath(query);
            setText("");
            MainframeContext ctx = tabManager.getMainframeContext();
            if (ctx != null) {
                ctx.openFileOrDirectory(resourcePath);
            }
            return;
        }

        // -- Normal full-text search --
        SearchTab searchTab = new SearchTab(tabManager);
        tabManager.addTab(searchTab);
        if (!query.isEmpty()) {
            searchTab.searchFor(query);
        } else {
            SwingUtilities.invokeLater(searchTab::focusSearchField);
        }
        setText("");
    }

    /**
     * Returns {@code true} if the input looks like a resource path with a known protocol prefix
     * (including "search-*" variants).
     */
    private static boolean isResourcePath(String input) {
        String lower = input.toLowerCase();
        if (lower.startsWith(BookmarkEntry.SEARCH_PREFIX)) return true;
        for (String prefix : RESOURCE_PREFIXES) {
            if (lower.startsWith(prefix.toLowerCase())) return true;
        }
        for (String prefix : SHORT_PREFIXES) {
            if (lower.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Normalise short-hand prefixes into their full {@code protocol://} form so that
     * {@link MainframeContext#openFileOrDirectory(String)} can route them correctly.
     */
    private static String normalizeResourcePath(String input) {
        for (String shortPrefix : SHORT_PREFIXES) {
            if (input.toLowerCase().startsWith(shortPrefix)
                    && !input.toLowerCase().startsWith(shortPrefix + "//")) {
                return input.substring(0, shortPrefix.length()) + "//"
                        + input.substring(shortPrefix.length());
            }
        }
        return input;
    }

    // =================================================================
    //  Public API
    // =================================================================

    /** Request focus on the text field (e.g. via keyboard shortcut). */
    public void focusSearch() {
        focusAndSelectAll();
    }
}
