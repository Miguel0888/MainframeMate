package de.bund.zrb.ui.commands;

import de.bund.zrb.archive.ui.CacheConnectionTab;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

/**
 * Opens the Cache connection tab.
 */
public class OpenCacheMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public OpenCacheMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "file.cache";
    }

    @Override
    public String getLabel() {
        return "Cache\u2026";
    }

    @Override
    public void perform() {
        tabManager.addTab(new CacheConnectionTab(tabManager));
    }
}
