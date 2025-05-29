package de.bund.zrb.plugins;

import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.commands.Command;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface MainframeMatePlugin {

    String getPluginName();

    void initialize(MainFrame mainFrame);

    default Optional<JMenuItem> getSettingsMenuItem(MainFrame mainFrame) {
        return Optional.empty();
    }

    // optionale Commands, standardmäßig leer
    default List<Command> getCommands(MainFrame mainFrame) {
        return Collections.emptyList();
    }
}
