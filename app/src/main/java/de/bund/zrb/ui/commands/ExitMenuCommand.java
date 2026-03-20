package de.bund.zrb.ui.commands;

import de.zrb.bund.api.ShortcutMenuCommand;

public class ExitMenuCommand extends ShortcutMenuCommand {

    @Override
    public String getId() {
        return "file.exit";
    }

    @Override
    public String getLabel() {
        return "\u2399 Beenden";
    }

    @Override
    public void perform() {
        System.exit(0);
    }
}
