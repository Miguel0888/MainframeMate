package de.bund.zrb.ui.commands;

import de.zrb.bund.api.MenuCommand;

public class ExitMenuCommand implements MenuCommand {

    @Override
    public String getId() {
        return "file.exit";
    }

    @Override
    public String getLabel() {
        return "Beenden";
    }

    @Override
    public void perform() {
        System.exit(0);
    }
}
