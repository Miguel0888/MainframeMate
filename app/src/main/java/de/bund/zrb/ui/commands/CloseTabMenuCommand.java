package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.ShortcutMenuCommand;

public class CloseTabMenuCommand extends ShortcutMenuCommand {

    private final TabbedPaneManager tabManager;

    public CloseTabMenuCommand(TabbedPaneManager tabManager) {
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "file.close";
    }

    @Override
    public String getLabel() {
        return "Schlie\u00DFen";
    }

    @Override
    public void perform() {
        tabManager.closeSelectedComponent();
    }
}

