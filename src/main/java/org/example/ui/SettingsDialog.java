package org.example.ui;

import org.example.ftp.FtpManager;
import org.example.model.Settings;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;

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

        // Dialog anzeigen
        int result = JOptionPane.showConfirmDialog(parent, panel, "Einstellungen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String selectedEncoding = (String) encodingCombo.getSelectedItem();
            settings.encoding = selectedEncoding;
            SettingsManager.save(settings);

            // Live setzen (wenn möglich)
            ftpManager.getClient().setControlEncoding(selectedEncoding);

            JOptionPane.showMessageDialog(parent,
                    "Kodierung gesetzt auf: " + selectedEncoding,
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
