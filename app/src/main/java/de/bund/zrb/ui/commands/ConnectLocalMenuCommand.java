package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.LocalConnectionTabImpl;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

public class ConnectLocalMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public ConnectLocalMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "file.local";
    }

    @Override
    public String getLabel() {
        return "Dieser Computer";
    }

    @Override
    public void perform() {
        tabManager.addTab(new LocalConnectionTabImpl(tabManager));
    }
}

