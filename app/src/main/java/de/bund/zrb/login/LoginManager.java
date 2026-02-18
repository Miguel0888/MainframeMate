package de.bund.zrb.login;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.WindowsCryptoUtil;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class LoginManager {

    public enum BlockedLoginDecision {
        CANCEL,
        RETRY,
        RETRY_WITH_NEW_PASSWORD
    }

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
        Settings settings = SettingsHelper.load();
        if (!settings.autoConnect) {
            return false;
        }

        String key = toCacheKey(host, username);

        if (encryptedPasswordCache.containsKey(key)) {
            return true;
        }

        return settings.savePassword
                && host != null && host.equals(settings.host)
                && username != null && username.equals(settings.user)
                && settings.encryptedPassword != null;
    }

    public String getPassword(String host, String username) {
        String key = toCacheKey(host, username);
        Settings settings = SettingsHelper.load();

        // 1) Use RAM cache only if allowed
        if (settings.autoConnect && encryptedPasswordCache.containsKey(key)) {
            return WindowsCryptoUtil.decrypt(encryptedPasswordCache.get(key));
        }

        // 2) Use stored password only if allowed and matching host/user
        if (settings.savePassword
                && host != null && host.equals(settings.host)
                && username != null && username.equals(settings.user)
                && settings.encryptedPassword != null) {
            try {
                String decrypted = WindowsCryptoUtil.decrypt(settings.encryptedPassword);
                if (settings.autoConnect) {
                    encryptedPasswordCache.put(key, settings.encryptedPassword);
                }
                return decrypted;
            } catch (Exception e) {
                // Ignore error and fall back to interactive prompt
            }
        }

        // 3) Ask user interactively
        return requestCredentialsAndPersist(host, username, settings);
    }

    /**
     * Non-interactive lookup: returns cached or stored password if available, otherwise null.
     * This method must not trigger any UI prompt.
     */
    public String getCachedPassword(String host, String username) {
        String key = toCacheKey(host, username);
        Settings settings = SettingsHelper.load();

        if (settings.autoConnect && encryptedPasswordCache.containsKey(key)) {
            return WindowsCryptoUtil.decrypt(encryptedPasswordCache.get(key));
        }

        if (settings.savePassword
                && host != null && host.equals(settings.host)
                && username != null && username.equals(settings.user)
                && settings.encryptedPassword != null) {
            try {
                return WindowsCryptoUtil.decrypt(settings.encryptedPassword);
            } catch (Exception ignore) {
                // ignore and return null
            }
        }

        return null;
    }

    public String requestFreshPassword(String host, String username) {
        Settings settings = SettingsHelper.load();

        // Ensure cached/stored password is not reused for this host/user
        invalidatePassword(host, username);

        return requestCredentialsAndPersist(host, username, settings);
    }

    public void invalidatePassword(String host, String username) {
        String key = toCacheKey(host, username);
        encryptedPasswordCache.remove(key);

        Settings settings = SettingsHelper.load();
        if (host != null && host.equals(settings.host) && username != null && username.equals(settings.user)) {
            settings.encryptedPassword = null;
            SettingsHelper.save(settings);
        }
    }

    public void clearCache() {
        encryptedPasswordCache.clear();
    }

    public boolean verifyPassword(String host, String username, String plainPassword) {
        String key = toCacheKey(host, username);
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

    public BlockedLoginDecision showBlockedLoginDialog() {
        JCheckBox confirmBox = new JCheckBox("Ich habe verstanden, dass eine Kontosperrung möglich ist.");
        confirmBox.setBackground(Color.WHITE);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.add(new JLabel("<html><b>⚠ Achtung:</b><br>Mehrere fehlerhafte Logins können zur Sperrung Ihrer Kennung führen.<br><br>Wollen Sie es trotzdem noch einmal versuchen?</html>"),
                BorderLayout.NORTH);
        panel.add(confirmBox, BorderLayout.CENTER);

        JButton retryButton = new JButton("OK");
        JButton retryWithNewPasswordButton = new JButton("Neues Passwort…");
        JButton cancelButton = new JButton("Abbrechen");

        final JOptionPane optionPane = new JOptionPane(
                panel,
                JOptionPane.WARNING_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                new Object[]{cancelButton, retryButton, retryWithNewPasswordButton},
                cancelButton
        );

        JDialog dialog = optionPane.createDialog(null, "Login blockiert");

        retryButton.addActionListener(e -> {
            // Require confirmation before continuing
            if (confirmBox.isSelected()) {
                optionPane.setValue(retryButton);
                dialog.dispose();
                return;
            }
            JOptionPane.showMessageDialog(dialog,
                    "Bitte bestätige den Hinweis durch Setzen des Hakens.",
                    "Hinweis fehlt",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        retryWithNewPasswordButton.addActionListener(e -> {
            // Require confirmation before continuing
            if (confirmBox.isSelected()) {
                optionPane.setValue(retryWithNewPasswordButton);
                dialog.dispose();
                return;
            }
            JOptionPane.showMessageDialog(dialog,
                    "Bitte bestätige den Hinweis durch Setzen des Hakens.",
                    "Hinweis fehlt",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        cancelButton.addActionListener(e -> {
            optionPane.setValue(cancelButton);
            dialog.dispose();
        });

        dialog.setVisible(true);

        Object value = optionPane.getValue();
        if (value == retryButton) {
            return BlockedLoginDecision.RETRY;
        }
        if (value == retryWithNewPasswordButton) {
            return BlockedLoginDecision.RETRY_WITH_NEW_PASSWORD;
        }
        return BlockedLoginDecision.CANCEL;
    }

    public void resetLoginBlock() {
        this.loginTemporarilyBlocked = false;
    }

    private String requestCredentialsAndPersist(String host, String username, Settings settings) {
        if (credentialsProvider == null) {
            throw new IllegalStateException("No LoginCredentialsProvider set");
        }

        LoginCredentials credentials = credentialsProvider.requestCredentials(host, username);
        if (credentials == null || credentials.getPassword() == null) {
            return null;
        }

        persistCredentials(credentials, settings);
        return credentials.getPassword();
    }

    private void persistCredentials(LoginCredentials credentials, Settings settings) {
        String password = credentials.getPassword();
        String encrypted = WindowsCryptoUtil.encrypt(password);

        // Cache password only if allowed
        if (settings.autoConnect) {
            String key = toCacheKey(credentials.getHost(), credentials.getUsername());
            encryptedPasswordCache.put(key, encrypted);
        }

        // Store password only if allowed
        if (settings.savePassword) {
            settings.encryptedPassword = encrypted;
        } else {
            settings.encryptedPassword = null;
        }

        settings.host = credentials.getHost();
        settings.user = credentials.getUsername();

        SettingsHelper.save(settings);
    }

    private String toCacheKey(String host, String username) {
        return String.valueOf(host) + "|" + String.valueOf(username);
    }
}
