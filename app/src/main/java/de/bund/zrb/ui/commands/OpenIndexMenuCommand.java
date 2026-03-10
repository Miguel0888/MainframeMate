package de.bund.zrb.ui.commands;

import de.bund.zrb.archive.ui.IndexConnectionTab;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

/**
 * Opens the Index connection tab.
 */
public class OpenIndexMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public OpenIndexMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "file.index";
    }

    @Override
    public String getLabel() {
        return "Index\u2026";
    }

    @Override
    public void perform() {
        tabManager.addTab(new IndexConnectionTab(tabManager));
    }
}
