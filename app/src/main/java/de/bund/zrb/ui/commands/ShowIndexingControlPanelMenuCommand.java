package de.bund.zrb.ui.commands;

import de.bund.zrb.indexing.ui.IndexingControlPanel;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

/**
 * Menu command to open the Indexing Control Panel.
 * Registered under settings.indexing → "Einstellungen → Indexierung..."
 */
public class ShowIndexingControlPanelMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;

    public ShowIndexingControlPanelMenuCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "settings.indexing";
    }

    @Override
    public String getLabel() {
        return "Indexierung...";
    }

    @Override
    public void perform() {
        IndexingControlPanel.show(parent);
    }
}
