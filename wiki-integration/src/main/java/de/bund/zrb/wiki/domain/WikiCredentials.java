package de.bund.zrb.wiki.domain;

/**
 * Credentials for wiki login. Use {@link #anonymous()} when no login is needed.
 */
public final class WikiCredentials {
    private final String username;
    private final char[] password;

    public static WikiCredentials anonymous() {
        return new WikiCredentials(null, null);
    }

    public WikiCredentials(String username, char[] password) {
        this.username = username;
        this.password = password;
    }

    public boolean isAnonymous() {
        return username == null || username.trim().isEmpty();
    }

    public String username() { return username; }
    public char[] password() { return password; }
}

