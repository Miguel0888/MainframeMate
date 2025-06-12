package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.FeatureDialog;
import de.zrb.bund.api.MenuCommand;
import de.zrb.bund.api.SimpleMenuCommand;

import javax.swing.*;

public class ShowFeatureDialogMenuCommand extends SimpleMenuCommand {

    private final JFrame parent;

    public ShowFeatureDialogMenuCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "help.features";
    }

    @Override
    public String getLabel() {
        return "📋 Server-Features anzeigen";
    }

    @Override
    public void perform() {
        FeatureDialog.show(parent);
    }
}
