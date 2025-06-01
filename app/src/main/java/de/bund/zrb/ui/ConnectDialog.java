package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.model.Settings;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.util.WindowsCryptoUtil;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class ConnectDialog {

    public static boolean connectIfNeeded(Component parent, FtpManager ftpManager) {
        Settings settings = SettingsHelper.load();

        if (settings.autoConnect) {
            return show(parent, ftpManager, settings);
        }
        return false;
    }

    public static boolean show(Component parent, FtpManager ftpManager) {
        return show(parent, ftpManager, SettingsHelper.load());
    }

    private static boolean show(Component parent, FtpManager ftpManager, Settings settings) {
        // Hide Dialog, if not wanted and not required
        if(settings.hideLoginDialog && settings.host != null && settings.user != null &&
                settings.savePassword && settings.encryptedPassword != null) {
            try {
                String password = WindowsCryptoUtil.decrypt(settings.encryptedPassword);
                ftpManager.connect(settings.host, settings.user, password);
                return true;
            } catch (IOException ex) {
                // Fallback: Dialog anzeigen
            }
        }

        JTextField hostField = new JTextField(settings.host != null ? settings.host : "");
        JTextField userField = new JTextField(settings.user != null ? settings.user : "");
        String storedPassword = null;
        if (settings.savePassword && settings.encryptedPassword != null) {
            storedPassword = WindowsCryptoUtil.decrypt(settings.encryptedPassword);
        }
        JPasswordField passField = new JPasswordField(storedPassword != null ? storedPassword : "");
        JCheckBox savePasswordBox = new JCheckBox("Passwort speichern");
        savePasswordBox.setSelected(settings.savePassword);

        JPanel autoConnectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        autoConnectPanel.add(savePasswordBox);

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Host:"));
        panel.add(hostField);
        panel.add(new JLabel("Benutzer:"));
        panel.add(userField);
        panel.add(new JLabel("Passwort:"));
        panel.add(passField);
        panel.add(autoConnectPanel);

        int result = JOptionPane.showConfirmDialog(parent, panel, "FTP-Verbindung herstellen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String host = hostField.getText();
            String user = userField.getText();
            String pass = new String(passField.getPassword());

            try {
                ftpManager.connect(host, user, pass);
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
                return true;
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(parent, "Verbindung fehlgeschlagen:\n" + ex.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        }

        return false;
    }
}
