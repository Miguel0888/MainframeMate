package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.settings.SentenceTypeSettingsDialog;
import de.zrb.bund.api.Command;

import javax.swing.*;

public class ShowSentenceDialogCommand implements Command {

    private final JFrame parent;

    public ShowSentenceDialogCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "settings.sentences";
    }

    @Override
    public String getLabel() {
        return "Satzartdefinitionen...";
    }

    @Override
    public void perform() {
        SentenceTypeSettingsDialog.show(parent);
    }
}
