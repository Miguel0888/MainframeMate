package org.example.ui;

import org.example.ftp.FtpManager;
import org.example.model.Settings;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class SettingsDialog {

    public static void show(Component parent, FtpManager ftpManager) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createDefaultGbc();

        // Zeichensatz-Auswahl
        JComboBox<String> encodingCombo = new JComboBox<>();
        List<String> encodings = SettingsManager.SUPPORTED_ENCODINGS;
        encodings.forEach(encodingCombo::addItem);

        Settings settings = SettingsManager.load();
        String currentEncoding = settings.encoding != null ? settings.encoding : "windows-1252";
        encodingCombo.setSelectedItem(currentEncoding);

        addEncodingSelector(panel, gbc, encodingCombo);

        // Schriftart-Auswahl
        gbc.gridwidth = 2;
        panel.add(new JLabel("Editor-Schriftart:"), gbc);
        gbc.gridy++;

        JComboBox<String> fontCombo = new JComboBox<>(new String[] {
                "Monospaced", "Consolas", "Courier New", "Menlo", "Dialog"
        });
        fontCombo.setSelectedItem(settings.editorFont);
        panel.add(fontCombo, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;

        // Schriftgröße
        gbc.gridwidth = 2;
        panel.add(new JLabel("Editor-Schriftgröße:"), gbc);
        gbc.gridy++;
        JComboBox<Integer> fontSizeCombo = new JComboBox<>(new Integer[] {
                10, 11, 12, 13, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72
        });
        fontSizeCombo.setEditable(true);
        fontSizeCombo.setSelectedItem(settings.editorFontSize);
        panel.add(fontSizeCombo, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;

        // Zeilenumbruch
        JComboBox<String> lineEndingBox = new JComboBox<>(new String[] {
                "LF (\\n)", "CRLF (\\r\\n)", "Keine (Mainframe)"
        });
        String current = settings.lineEnding != null ? settings.lineEnding : "NONE";
        switch (current) {
            case "CRLF":
                lineEndingBox.setSelectedItem("CRLF (\\r\\n)");
                break;
            case "NONE":
                lineEndingBox.setSelectedItem("Keine (Mainframe)");
                break;
            default:
                lineEndingBox.setSelectedItem("LF (\\n)");
        }
        lineEndingBox.setSelectedItem(current);
        panel.add(new JLabel("Zeilenumbruch beim Speichern:"), gbc);
        gbc.gridy++;
        panel.add(lineEndingBox, gbc);

        // Login-Dialog unterdrücken
        JCheckBox hideLoginBox = new JCheckBox("Login-Fenster verbergen (wenn Passwort gespeichert)");
        hideLoginBox.setSelected(settings.hideLoginDialog);
        panel.add(hideLoginBox, gbc);
        gbc.gridy++;

        // Login beim Start (new Session), falls Bookmarks nicht verwendet werden
        JCheckBox autoConnectBox = new JCheckBox("Automatisch verbinden (beim Start)");
        autoConnectBox.setSelected(settings.autoConnect);
        panel.add(autoConnectBox, gbc);
        gbc.gridy++;

        // User Profile Folder
        JButton openFolderButton = new JButton("\uD83D\uDCC1");
        openFolderButton.setToolTipText("Einstellungsordner öffnen");
        openFolderButton.setMargin(new Insets(0, 5, 0, 5));
        openFolderButton.setFocusable(false);
        openFolderButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(SettingsManager.getSettingsFolder());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(parent, "Ordner konnte nicht geöffnet werden:\n" + ex.getMessage());
            }
        });
        panel.add(openFolderButton, gbc);
        gbc.gridy++;

        // Dialog anzeigen
        int result = JOptionPane.showConfirmDialog(parent, panel, "Einstellungen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            settings.encoding = (String) encodingCombo.getSelectedItem();
            settings.editorFont = (String) fontCombo.getSelectedItem();
            settings.editorFontSize = Optional.ofNullable(fontSizeCombo.getEditor().getItem())
                    .map(Object::toString)
                    .map(s -> {
                        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
                    })
                    .orElse(12);
            settings.lineEnding = Optional.ofNullable((String) lineEndingBox.getSelectedItem())
                    .map(String::toUpperCase)
                    .map(s -> {
                        if (s.contains("CRLF")) return "CRLF";
                        if (s.contains("KEINE")) return "NONE";
                        return "LF";
                    })
                    .orElse("LF");
            settings.hideLoginDialog = hideLoginBox.isSelected();
            settings.autoConnect = autoConnectBox.isSelected();
            SettingsManager.save(settings);

            ftpManager.getClient().setControlEncoding(settings.encoding);

            JOptionPane.showMessageDialog(parent,
                    "Einstellungen wurden gespeichert.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static GridBagConstraints createDefaultGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        return gbc;
    }

    private static void addEncodingSelector(JPanel panel, GridBagConstraints gbc, JComboBox<String> encodingCombo) {
        gbc.gridwidth = 2;
        panel.add(new JLabel("Zeichenkodierung:"), gbc);
        gbc.gridy++;
        panel.add(encodingCombo, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;
    }
}
