package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.ShortcutMenuCommand;

public class SaveAndCloseMenuCommand extends ShortcutMenuCommand {

    private final TabbedPaneManager tabManager;

    public SaveAndCloseMenuCommand(TabbedPaneManager tabManager) {
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "file.saveAndClose";
    }

    @Override
    public String getLabel() {
        return "\u2714 Speichern & Schlie\u00DFen";
    }

    @Override
    public void perform() {
        tabManager.saveAndCloseSelectedComponent();
    }
}
