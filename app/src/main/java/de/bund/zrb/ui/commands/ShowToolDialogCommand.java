package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.ToolDialog;
import de.zrb.bund.api.Command;

import javax.swing.*;

public class ShowToolDialogCommand implements Command {

    private final JFrame parent;

    public ShowToolDialogCommand(JFrame parent) {
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
        ToolDialog.show(parent);
    }
}
