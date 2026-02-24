package de.bund.zrb.ui.commands;

import de.bund.zrb.archive.ui.ArchiveConnectionTab;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

/**
 * Opens the Archive connection tab.
 */
public class OpenArchiveMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public OpenArchiveMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "file.archive";
    }

    @Override
    public String getLabel() {
        return "Archiv";
    }

    @Override
    public void perform() {
        tabManager.addTab(new ArchiveConnectionTab(tabManager));
    }
}
