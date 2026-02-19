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

    /**
     * Decision for retry after login failure.
     */
    public enum RetryDecision {
        CANCEL,
        RETRY,
        RETRY_WITH_NEW_PASSWORD
    }

    private static final LoginManager INSTANCE = new LoginManager();

    private boolean loginTemporarilyBlocked = false;

    /** Flag indicating an active session exists (successful login occurred) */
    private volatile boolean sessionActive = false;

    private final Map<String, String> encryptedPasswordCache = new HashMap<String, String>();
    private LoginCredentialsProvider credentialsProvider;

    private LoginManager() {}

    public static LoginManager getInstance() {
        return INSTANCE;
    }

    public void setCredentialsProvider(LoginCredentialsProvider provider) {
        this.credentialsProvider = provider;
    }

    /**
     * Check if there's an active session that warrants application locking.
     * Returns true if:
     * - A successful login occurred (sessionActive = true), OR
     * - Password is cached in memory, OR
     * - Password is stored in settings
     */
    public boolean hasActiveSession() {
        if (sessionActive) {
            return true;
        }

        // Check if password is cached in memory
        if (!encryptedPasswordCache.isEmpty()) {
            return true;
        }

        // Check if password is stored in settings
        Settings settings = SettingsHelper.load();
        return settings.encryptedPassword != null && !settings.encryptedPassword.isEmpty();
    }

    /**
     * Mark session as active after successful login.
     * Called after successful FTP connection.
     */
    public void setSessionActive(boolean active) {
        this.sessionActive = active;
    }

    /**
     * Check if application should be locked (for ApplicationLocker).
     * Application should be lockable if there's any way someone could access FTP data.
     */
    public boolean shouldLockApplication() {
        return hasActiveSession();
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

    /**
     * Called when a login attempt fails.
     * Immediately invalidates the password to prevent using wrong password for locking.
     */
    public void onLoginFailed(String host, String username) {
        // Immediately clear password to prevent using wrong password
        invalidatePassword(host, username);

        // Block further login attempts temporarily
        blockLoginTemporarily();
    }

    /**
     * Called when a login attempt succeeds.
     * Marks session as active so ApplicationLocker knows to lock the application.
     * Also persists credentials if savePassword setting is enabled.
     */
    public void onLoginSuccess(String host, String username) {
        sessionActive = true;
        // Now persist credentials since login was successful
        persistCredentialsAfterSuccess(host, username);
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

        // Store credentials temporarily in RAM cache only
        // Persistent storage happens in onLoginSuccess() after successful connection
        cacheCredentialsTemporarily(credentials, settings);
        return credentials.getPassword();
    }

    /**
     * Cache credentials temporarily in RAM only (not persistent).
     * Used during login attempt - only persisted after successful connection.
     */
    private void cacheCredentialsTemporarily(LoginCredentials credentials, Settings settings) {
        String password = credentials.getPassword();
        String encrypted = WindowsCryptoUtil.encrypt(password);

        // Always cache in RAM for immediate use
        String key = toCacheKey(credentials.getHost(), credentials.getUsername());
        encryptedPasswordCache.put(key, encrypted);

        // Update host/user in settings but NOT the password yet
        settings.host = credentials.getHost();
        settings.user = credentials.getUsername();
        SettingsHelper.save(settings);
    }

    /**
     * Persist credentials after successful login.
     */
    private void persistCredentialsAfterSuccess(String host, String username) {
        Settings settings = SettingsHelper.load();
        String key = toCacheKey(host, username);

        if (!encryptedPasswordCache.containsKey(key)) {
            return; // Nothing to persist
        }

        String encrypted = encryptedPasswordCache.get(key);

        // Store password only if savePassword setting is enabled
        if (settings.savePassword && host.equals(settings.host) && username.equals(settings.user)) {
            settings.encryptedPassword = encrypted;
            SettingsHelper.save(settings);
        }
    }

    private String toCacheKey(String host, String username) {
        return String.valueOf(host) + "|" + String.valueOf(username);
    }
}
