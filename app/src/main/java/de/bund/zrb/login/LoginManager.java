package de.bund.zrb.login;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.WindowsCryptoUtil;

import java.util.HashMap;
import java.util.Map;

public class LoginManager {

    private static final LoginManager INSTANCE = new LoginManager();

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

}

