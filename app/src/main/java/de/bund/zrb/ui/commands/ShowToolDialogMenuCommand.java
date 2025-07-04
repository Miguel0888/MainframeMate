package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.settings.ToolSettingsDialog;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

public class ShowToolDialogMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;

    public ShowToolDialogMenuCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "settings.tools";
    }

    @Override
    public String getLabel() {
        return "Werkzeugdefinitionen...";
    }

    @Override
    public void perform() {
        ToolSettingsDialog.show(parent);
    }
}
