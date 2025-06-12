package de.bund.zrb.ui.commands;

import de.zrb.bund.api.MenuCommand;
import de.zrb.bund.api.SimpleMenuCommand;

public class ExitMenuCommand extends SimpleMenuCommand {

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
