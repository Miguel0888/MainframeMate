package de.bund.zrb.ui.commands;

import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

/**
 * Placeholder menu command for future Confluence integration.
 */
public class OpenConfluenceMenuCommand extends ShortcutMenuCommand {

    @Override
    public String getId() {
        return "connection.confluence";
    }

    @Override
    public String getLabel() {
        return "Confluence\u2026";
    }

    @Override
    public void perform() {
        JOptionPane.showMessageDialog(null,
                "Die Confluence-Anbindung ist noch nicht implementiert.\n"
                        + "Dieses Feature wird in einem späteren Release verfügbar sein.",
                "Noch nicht verfügbar",
                JOptionPane.INFORMATION_MESSAGE);
    }
}

