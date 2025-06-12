package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.settings.SentenceTypeSettingsDialog;
import de.zrb.bund.api.MenuCommand;
import de.zrb.bund.api.SimpleMenuCommand;

import javax.swing.*;

public class ShowSentenceDialogMenuCommand extends SimpleMenuCommand {

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
