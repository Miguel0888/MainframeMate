package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.settings.SentenceTypeSettingsDialog;
import de.zrb.bund.api.MenuCommand;

import javax.swing.*;

public class ShowSentenceDialogMenuCommand implements MenuCommand {

    private final JFrame parent;

    public ShowSentenceDialogMenuCommand(JFrame parent) {
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
