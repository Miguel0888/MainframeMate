package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.FeatureDialog;
import de.zrb.bund.api.MenuCommand;

import javax.swing.*;

public class ShowFeatureDialogMenuCommand implements MenuCommand {

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
