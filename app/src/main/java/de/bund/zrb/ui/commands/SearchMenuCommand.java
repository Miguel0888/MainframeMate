package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.ui.search.SearchTab;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

/**
 * Menu command that opens the global search tab.
 * Registered under edit.globalSearch.
 */
public class SearchMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public SearchMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "edit.globalSearch";
    }

    @Override
    public String getLabel() {
        return "Übergreifende Suche…";
    }

    @Override
    public void perform() {
        SearchTab searchTab = new SearchTab();
        tabManager.addTab(searchTab);
        // Focus the search field after the tab is shown
        SwingUtilities.invokeLater(searchTab::focusSearchField);
    }
}
