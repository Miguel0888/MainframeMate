package de.bund.zrb.ui.settings;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.WindowsCryptoUtil;

import javax.swing.*;
import java.awt.*;

/**
 * Standalone dialog for server credentials (shared by FTP + NDV).
 * Opened via Einstellungen → Server...
 */
public class ServerSettingsDialog {

    public static void show(Component parent) {
        Settings settings = SettingsHelper.load();

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.gridwidth = 2;

        // ─── Server-Zugangsdaten ───
        JLabel header = new JLabel("─── Server-Zugangsdaten (FTP + NDV) ───");
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        panel.add(header, gbc);
        gbc.gridy++;

        panel.add(new JLabel("Server (Host):"), gbc);
        gbc.gridy++;
        JTextField hostField = new JTextField(settings.host != null ? settings.host : "", 24);
        panel.add(hostField, gbc);
        gbc.gridy++;

        panel.add(new JLabel("Benutzer:"), gbc);
        gbc.gridy++;
        JTextField userField = new JTextField(settings.user != null ? settings.user : "", 24);
        panel.add(userField, gbc);
        gbc.gridy++;

        panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridy++;
        JPasswordField passwordField = new JPasswordField(24);
        // Show masked placeholder if password is stored
        if (settings.encryptedPassword != null && !settings.encryptedPassword.isEmpty()) {
            passwordField.setText("********");
        }
        panel.add(passwordField, gbc);
        gbc.gridy++;

        // Options row
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        JCheckBox savePasswordBox = new JCheckBox("Passwort speichern (verschlüsselt)");
        savePasswordBox.setSelected(settings.savePassword);
        panel.add(savePasswordBox, gbc);

        gbc.gridx = 1;
        JButton clearPasswordButton = new JButton("Passwort löschen");
        panel.add(clearPasswordButton, gbc);
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.gridy++;

        JCheckBox autoConnectBox = new JCheckBox("Passwort nur einmal eingeben (Session-Cache)");
        autoConnectBox.setSelected(settings.autoConnect);
        panel.add(autoConnectBox, gbc);
        gbc.gridy++;

        // Track if password was explicitly cleared
        final boolean[] passwordCleared = {false};
        clearPasswordButton.addActionListener(e -> {
            passwordField.setText("");
            passwordCleared[0] = true;
            JOptionPane.showMessageDialog(panel,
                    "Gespeichertes Passwort wird gelöscht.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
        });

        // Show dialog
        int result = JOptionPane.showConfirmDialog(
                parent, panel, "Server-Einstellungen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        // ── Apply ──
        String hostInput = hostField.getText().trim();
        String userInput = userField.getText().trim();

        if (!hostInput.isEmpty()) {
            settings.host = hostInput;
        }
        if (!userInput.isEmpty()) {
            settings.user = userInput;
        }

        settings.savePassword = savePasswordBox.isSelected();
        settings.autoConnect = autoConnectBox.isSelected();

        // Password handling
        char[] passChars = passwordField.getPassword();
        String passStr = new String(passChars);

        if (passwordCleared[0] && passStr.isEmpty()) {
            // User explicitly cleared the password
            settings.encryptedPassword = null;
        } else if (!passStr.equals("********") && passChars.length > 0) {
            // User entered a new password (not the placeholder)
            if (settings.savePassword) {
                settings.encryptedPassword = WindowsCryptoUtil.encrypt(passStr);
            } else {
                settings.encryptedPassword = null;
            }
        }
        // else: password field was not changed, keep existing

        SettingsHelper.save(settings);
    }
}

