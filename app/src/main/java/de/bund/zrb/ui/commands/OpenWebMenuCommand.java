package de.bund.zrb.ui.commands;

import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

/**
 * Placeholder menu command for future Web/Browser integration.
 */
public class OpenWebMenuCommand extends ShortcutMenuCommand {

    @Override
    public String getId() {
        return "file.web";
    }

    @Override
    public String getLabel() {
        return "Web-Recherche\u2026";
    }

    @Override
    public void perform() {
        JOptionPane.showMessageDialog(null,
                "Die Web-Recherche ist noch nicht implementiert.\n"
                        + "Dieses Feature wird in einem späteren Release verfügbar sein.",
                "Noch nicht verfügbar",
                JOptionPane.INFORMATION_MESSAGE);
    }
}

