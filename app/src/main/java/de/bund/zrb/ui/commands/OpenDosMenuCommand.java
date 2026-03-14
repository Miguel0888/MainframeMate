package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.ui.dos.DosConnectionTab;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

/**
 * Menu command "DOS-Terminal…" – opens an embedded DOS emulator as a new ConnectionTab.
 */
public class OpenDosMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public OpenDosMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "connection.dos";
    }

    @Override
    public String getLabel() {
        return "DOS-Terminal\u2026";
    }

    @Override
    public void perform() {
        tabManager.addTab(new DosConnectionTab());
    }
}

