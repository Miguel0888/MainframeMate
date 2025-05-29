package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.FeatureDialog;
import de.zrb.bund.api.Command;

import javax.swing.*;

public class ShowFeatureDialogCommand implements Command {

    private final JFrame parent;

    public ShowFeatureDialogCommand(JFrame parent) {
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
