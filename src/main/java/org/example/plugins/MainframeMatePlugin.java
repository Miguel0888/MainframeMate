
package org.example.plugins;

import org.example.ui.MainFrame;

import javax.swing.*;
import java.util.Optional;

public interface MainframeMatePlugin {
    String getPluginName();

    void initialize(MainFrame mainFrame);

    // Optional: Einstellungsmenüpunkt zurückgeben
    default Optional<JMenuItem> getSettingsMenuItem(MainFrame mainFrame) {
        return Optional.empty();
    }
}
