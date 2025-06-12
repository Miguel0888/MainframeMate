package de.bund.zrb.login;

import de.bund.zrb.model.Settings;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.util.WindowsCryptoUtil;

import javax.swing.*;
import java.awt.*;

public class ConnectDialog implements LoginCredentialsProvider {

    private final Component parent;

    public ConnectDialog(Component parent) {
        this.parent = parent;
    }

    @Override
    public LoginCredentials requestCredentials(String defaultHost, String defaultUser) {
        Settings settings = SettingsHelper.load();

        String storedPassword = null;
        if (settings.savePassword && settings.encryptedPassword != null) {
            try {
                storedPassword = WindowsCryptoUtil.decrypt(settings.encryptedPassword);
            } catch (Exception e) {
                // falls Entschlüsselung fehlschlägt: Passwort ignorieren
                storedPassword = null;
            }
        }

        JTextField hostField = new JTextField(defaultHost != null ? defaultHost : settings.host != null ? settings.host : "");
        JTextField userField = new JTextField(defaultUser != null ? defaultUser : settings.user != null ? settings.user : "");
        JPasswordField passField = new JPasswordField(storedPassword != null ? storedPassword : "");
        JCheckBox savePasswordBox = new JCheckBox("Passwort speichern");
        savePasswordBox.setSelected(settings.savePassword);

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Host:"));
        panel.add(hostField);
        panel.add(new JLabel("Benutzer:"));
        panel.add(userField);
        panel.add(new JLabel("Passwort:"));
        panel.add(passField);
        panel.add(savePasswordBox);

        int result = JOptionPane.showConfirmDialog(parent, panel, "FTP-Verbindung herstellen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String host = hostField.getText().trim();
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword());

            settings.host = host;
            settings.user = user;

            if (savePasswordBox.isSelected()) {
                settings.savePassword = true;
                settings.encryptedPassword = WindowsCryptoUtil.encrypt(pass);
            } else {
                settings.savePassword = false;
                settings.encryptedPassword = null;
            }

            SettingsHelper.save(settings);

            return new LoginCredentials(user, pass);
        }

        return null; // User hat abgebrochen
    }
}
