package de.bund.zrb.login;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.WindowsCryptoUtil;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class LoginManager {

    private static final LoginManager INSTANCE = new LoginManager();

    private boolean loginTemporarilyBlocked = false;

    private final Map<String, String> encryptedPasswordCache = new HashMap<String, String>();
    private LoginCredentialsProvider credentialsProvider;

    private LoginManager() {}

    public static LoginManager getInstance() {
        return INSTANCE;
    }

    public void setCredentialsProvider(LoginCredentialsProvider provider) {
        this.credentialsProvider = provider;
    }

    public boolean isLoggedIn(String host, String username) {
        if (!SettingsHelper.load().autoConnect) {
            return false;
        }
        String key = host + "|" + username;
        return encryptedPasswordCache.containsKey(key);
    }

    public String getPassword(String host, String username) {
        String key = host + "|" + username;

        Settings settings = SettingsHelper.load();

        // 1. Prüfe RAM-Cache (nur wenn erlaubt)
        if (settings.autoConnect && encryptedPasswordCache.containsKey(key)) {
            return WindowsCryptoUtil.decrypt(encryptedPasswordCache.get(key));
        }

        // 2. Prüfe gespeichertes Passwort in Settings
        if (settings.savePassword &&
                host != null && host.equals(settings.host) &&
                username != null && username.equals(settings.user) &&
                settings.encryptedPassword != null) {
            try {
                String decrypted = WindowsCryptoUtil.decrypt(settings.encryptedPassword);
                if (settings.autoConnect) {
                    encryptedPasswordCache.put(key, settings.encryptedPassword); // Cache nur, wenn erlaubt
                }
                return decrypted;
            } catch (Exception e) {
                // Ignorieren → Passwort wird ggf. erneut abgefragt
            }
        }

        // 3. Frage interaktiv beim Benutzer nach
        if (credentialsProvider == null) {
            throw new IllegalStateException("No LoginCredentialsProvider set");
        }

        LoginCredentials credentials = credentialsProvider.requestCredentials(host, username);
        if (credentials != null && credentials.getPassword() != null) {
            if (settings.autoConnect) {
                String encrypted = WindowsCryptoUtil.encrypt(credentials.getPassword());
                String newKey = credentials.getHost() + "|" + credentials.getUsername();
                encryptedPasswordCache.put(newKey, encrypted);
            }
            if (settings.savePassword) {
                settings.encryptedPassword = WindowsCryptoUtil.encrypt(credentials.getPassword());
            } else {
                settings.encryptedPassword = null; // overwrites existing, if function was disabled
            }
            settings.host = credentials.getHost();
            settings.user = credentials.getUsername();
            SettingsHelper.save(settings);
            return credentials.getPassword();
        }

        return null; // Benutzer hat abgebrochen
    }


    public void clearCache() {
        encryptedPasswordCache.clear();
    }

    public boolean verifyPassword(String host, String username, String plainPassword) {
        String key = host + "|" + username;
        if (!encryptedPasswordCache.containsKey(key)) {
            return false;
        }
        String expected = WindowsCryptoUtil.decrypt(encryptedPasswordCache.get(key));
        return expected.equals(plainPassword);
    }

    public boolean isLoginBlocked() {
        return loginTemporarilyBlocked;
    }

    public void blockLoginTemporarily() {
        this.loginTemporarilyBlocked = true;
    }

    public boolean showBlockedLoginDialog() {
        JCheckBox confirmBox = new JCheckBox("Ich habe verstanden, dass eine Kontosperrung möglich ist.");
        confirmBox.setBackground(Color.WHITE);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.add(new JLabel("<html><b>⚠ Achtung:</b><br>Mehrere fehlerhafte Logins können zur Sperrung Ihrer Kennung führen.<br><br>Wollen Sie es trotzdem noch einmal versuchen?</html>"), BorderLayout.NORTH);
        panel.add(confirmBox, BorderLayout.CENTER);

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Abbrechen");

        final JOptionPane optionPane = new JOptionPane(panel, JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, new Object[]{cancelButton, okButton}, cancelButton);
        JDialog dialog = optionPane.createDialog(null, "Login blockiert");

        okButton.addActionListener(e -> {
            if (confirmBox.isSelected()) {
                optionPane.setValue(okButton);
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog, "Bitte bestätigen Sie den Hinweis durch Setzen des Hakens.", "Hinweis fehlt", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> {
            optionPane.setValue(cancelButton);
            dialog.dispose();
        });

        dialog.setVisible(true);
        return optionPane.getValue() == okButton;
    }

    public void resetLoginBlock() {
        this.loginTemporarilyBlocked = false;
    }
}

