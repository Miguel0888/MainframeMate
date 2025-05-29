package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.Command;

public class SaveCommand implements Command {

    private final TabbedPaneManager tabManager;

    public SaveCommand(TabbedPaneManager tabManager) {
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "file.save";
    }

    @Override
    public String getLabel() {
        return "Speichern";
    }

    @Override
    public void perform() {
        tabManager.saveSelectedComponent();
    }
}
