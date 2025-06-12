package de.bund.zrb.ui.commands.sub;

import de.bund.zrb.ui.FileTabImpl;
import de.zrb.bund.api.ShortcutMenuCommand;
import de.zrb.bund.api.MainframeContext;

import javax.swing.*;

public class ShowComparePanelCommand extends ShortcutMenuCommand {

    private final MainframeContext context;

    public ShowComparePanelCommand(MainframeContext context) {
        this.context = context;
    }

    @Override
    public String getId() {
        return "file.compare";
    }

    @Override
    public String getLabel() {
        return "Vergleichsansicht anzeigen";
    }

    @Override
    public void perform() {
        context.getSelectedTab().ifPresent(tab -> {
            if (tab instanceof FileTabImpl) {
                ((FileTabImpl) tab).toggleComparePanel();
            } else {
                JOptionPane.showMessageDialog(null,
                        "Dieser Tab unterstützt keine Vergleichsansicht.",
                        "Nicht verfügbar",
                        JOptionPane.WARNING_MESSAGE);
            }
        });
    }
}
