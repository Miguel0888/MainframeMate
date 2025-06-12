package de.bund.zrb.login;

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
        String key = host + "|" + username;
        if (encryptedPasswordCache.containsKey(key)) {
            return true;
        }
        return false;
    }

    public String getPassword(String host, String username) {
        String key = host + "|" + username;
        if (encryptedPasswordCache.containsKey(key)) {
            return WindowsCryptoUtil.decrypt(encryptedPasswordCache.get(key));
        }

        if (credentialsProvider == null) {
            throw new IllegalStateException("No LoginCredentialsProvider set");
        }

        LoginCredentials credentials = credentialsProvider.requestCredentials(host, username);
        if (credentials != null && credentials.getPassword() != null) {
            String encrypted = WindowsCryptoUtil.encrypt(credentials.getPassword());
            encryptedPasswordCache.put(key, encrypted);
            return credentials.getPassword(); // Klartext-Rückgabe zur Verwendung
        }

        return null;
    }

    public void clearCache() {
        encryptedPasswordCache.clear();
    }
}

