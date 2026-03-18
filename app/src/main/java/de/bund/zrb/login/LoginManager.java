package de.bund.zrb.login;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.SessionCipher;
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

    /**
     * RAM-only password cache — encrypted with {@link SessionCipher} (pure Java, fast).
     * Never touches JNA, PowerShell, or the filesystem.
     */
    private final Map<String, String> sessionPasswordCache = new HashMap<String, String>();
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
        if (!sessionPasswordCache.isEmpty()) {
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

        if (sessionPasswordCache.containsKey(key)) {
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

        // 1) RAM cache — uses SessionCipher (pure Java, fast, no JNA/PS1)
        if (settings.autoConnect && sessionPasswordCache.containsKey(key)) {
            return SessionCipher.decrypt(sessionPasswordCache.get(key));
        }

        // 2) Stored password on disk — uses WindowsCryptoUtil (JNA/PS1/AES per setting)
        if (settings.savePassword
                && host != null && host.equals(settings.host)
                && username != null && username.equals(settings.user)
                && settings.encryptedPassword != null) {
            try {
                String decrypted = WindowsCryptoUtil.decrypt(settings.encryptedPassword);
                // Cache in RAM with SessionCipher so subsequent calls are fast
                if (settings.autoConnect) {
                    sessionPasswordCache.put(key, SessionCipher.encrypt(decrypted));
                }
                return decrypted;
            } catch (de.bund.zrb.util.JnaBlockedException e) {
                throw e; // must not be swallowed — user needs to switch password method
            } catch (de.bund.zrb.util.PowerShellBlockedException e) {
                throw e; // must not be swallowed — user needs to switch password method
            } catch (de.bund.zrb.util.KeePassNotAvailableException e) {
                throw e; // must not be swallowed — user needs to check KeePass config
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

        // RAM cache — SessionCipher only, no JNA/PS1
        if (settings.autoConnect && sessionPasswordCache.containsKey(key)) {
            return SessionCipher.decrypt(sessionPasswordCache.get(key));
        }

        // Disk — WindowsCryptoUtil (slow path, only if not yet cached)
        if (settings.savePassword
                && host != null && host.equals(settings.host)
                && username != null && username.equals(settings.user)
                && settings.encryptedPassword != null) {
            try {
                String decrypted = WindowsCryptoUtil.decrypt(settings.encryptedPassword);
                if (settings.autoConnect) {
                    sessionPasswordCache.put(key, SessionCipher.encrypt(decrypted));
                }
                return decrypted;
            } catch (de.bund.zrb.util.JnaBlockedException e) {
                throw e; // must not be swallowed — user needs to switch password method
            } catch (de.bund.zrb.util.PowerShellBlockedException e) {
                throw e; // must not be swallowed — user needs to switch password method
            } catch (de.bund.zrb.util.KeePassNotAvailableException e) {
                throw e; // must not be swallowed — user needs to check KeePass config
            } catch (Exception ignore) {
                // ignore and return null
            }
        }

        return null;
    }

    public String requestFreshPassword(String host, String username) {
        // Ensure cached/stored password is not reused for this host/user
        invalidatePassword(host, username);

        // Load settings AFTER invalidation so encryptedPassword is null
        Settings settings = SettingsHelper.load();

        return requestCredentialsAndPersist(host, username, settings);
    }

    public void invalidatePassword(String host, String username) {
        String key = toCacheKey(host, username);
        sessionPasswordCache.remove(key);

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
        sessionPasswordCache.clear();
    }

    public boolean verifyPassword(String host, String username, String plainPassword) {
        String key = toCacheKey(host, username);
        if (!sessionPasswordCache.containsKey(key)) {
            return false;
        }
        String expected = SessionCipher.decrypt(sessionPasswordCache.get(key));
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

        // Store credentials temporarily in RAM cache only (SessionCipher — fast, no JNA/PS1)
        // Persistent storage happens in onLoginSuccess() after successful connection
        cacheCredentialsTemporarily(credentials, settings);
        return credentials.getPassword();
    }

    /**
     * Cache credentials temporarily in RAM only (not persistent).
     * Uses {@link SessionCipher} — pure Java, fast, no JNA/PS1 dependency.
     */
    private void cacheCredentialsTemporarily(LoginCredentials credentials, Settings settings) {
        String password = credentials.getPassword();

        // Encrypt with SessionCipher for RAM-only cache (fast, pure Java)
        String key = toCacheKey(credentials.getHost(), credentials.getUsername());
        sessionPasswordCache.put(key, SessionCipher.encrypt(password));

        // Update host/user in settings but NOT the password yet
        settings.host = credentials.getHost();
        settings.user = credentials.getUsername();
        SettingsHelper.save(settings);
    }

    /**
     * Persist credentials after successful login.
     * Decrypts from RAM cache (SessionCipher), re-encrypts with WindowsCryptoUtil for disk.
     */
    private void persistCredentialsAfterSuccess(String host, String username) {
        Settings settings = SettingsHelper.load();
        String key = toCacheKey(host, username);

        if (!sessionPasswordCache.containsKey(key)) {
            return; // Nothing to persist
        }

        // Store password only if savePassword setting is enabled
        if (settings.savePassword && host.equals(settings.host) && username.equals(settings.user)) {
            // Decrypt from RAM (SessionCipher) → re-encrypt for disk (WindowsCryptoUtil)
            String plainPassword = SessionCipher.decrypt(sessionPasswordCache.get(key));
            try {
                settings.encryptedPassword = WindowsCryptoUtil.encrypt(plainPassword);
                SettingsHelper.save(settings);
            } catch (de.bund.zrb.util.JnaBlockedException e) {
                System.err.println("[LoginManager] Cannot persist password (JNA blocked): " + e.getMessage());
                // Not critical — password is still in RAM cache for this session
            } catch (de.bund.zrb.util.PowerShellBlockedException e) {
                System.err.println("[LoginManager] Cannot persist password (PowerShell blocked): " + e.getMessage());
                // Not critical — password is still in RAM cache for this session
            } catch (de.bund.zrb.util.KeePassNotAvailableException e) {
                System.err.println("[LoginManager] Cannot persist password (KeePass): " + e.getMessage());
                // Not critical — password is still in RAM cache for this session
            } catch (Exception e) {
                System.err.println("[LoginManager] Cannot persist password: " + e.getMessage());
            }
        }
    }

    private String toCacheKey(String host, String username) {
        return String.valueOf(host) + "|" + String.valueOf(username);
    }
}
