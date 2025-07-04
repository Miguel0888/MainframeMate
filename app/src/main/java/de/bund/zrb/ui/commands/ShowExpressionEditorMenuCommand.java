package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.settings.ExpressionEditorDialog;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

public class ShowExpressionEditorMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;

    public ShowExpressionEditorMenuCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "settings.expressions";
    }

    @Override
    public String getLabel() {
        return "Ausdruckseditor...";
    }

    @Override
    public void perform() {
        ExpressionEditorDialog.show(parent);
    }
}
