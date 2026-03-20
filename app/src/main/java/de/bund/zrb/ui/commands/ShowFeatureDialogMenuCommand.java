package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.FeatureDialog;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

public class ShowFeatureDialogMenuCommand extends ShortcutMenuCommand {

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
        return "\u2605 Server-Features anzeigen";
    }

    @Override
    public void perform() {
        FeatureDialog.show(parent);
    }
}
