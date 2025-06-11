package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.MenuCommand;

public class SaveAndCloseMenuCommand implements MenuCommand {

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
        return "Speichern & Schließen";
    }

    @Override
    public void perform() {
        tabManager.saveAndCloseSelectedComponent();
    }
}
