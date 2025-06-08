package de.bund.zrb.ui.commands;

import de.zrb.bund.api.MenuCommand;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class InstallPluginMenuCommand implements MenuCommand {

    @Override
    public String getId() {
        return "plugin.install";
    }

    @Override
    public String getLabel() {
        return "Plugin installieren..."; // erscheint z. B. im Menü

    }

    @Override
    public void perform() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Plugin-JAR auswählen");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JAR-Dateien", "jar"));

        int result = chooser.showOpenDialog(null);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = chooser.getSelectedFile();
        if (selectedFile == null || !selectedFile.getName().endsWith(".jar")) {
            JOptionPane.showMessageDialog(null,
                    "Bitte wähle eine gültige JAR-Datei aus.",
                    "Ungültige Datei", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path pluginDir = Paths.get(System.getProperty("user.home"), ".mainframemate", "plugins");
        Path target = pluginDir.resolve(selectedFile.getName());

        try {
            Files.createDirectories(pluginDir); // falls noch nicht vorhanden
            Files.copy(selectedFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

            JOptionPane.showMessageDialog(null,
                    "Plugin installiert.\nBitte starte die Anwendung neu, um es zu aktivieren.",
                    "Plugin installiert", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Fehler beim Kopieren:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }
}
