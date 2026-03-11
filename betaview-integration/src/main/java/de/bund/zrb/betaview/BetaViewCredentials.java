package de.bund.zrb.betaview;

final class BetaViewCredentials {

    private final String username;
    private final String password;

    public BetaViewCredentials(String username, String password) {
        this.username = requireNotBlank(username, "username must not be blank");
        this.password = requireNotBlank(password, "password must not be blank");
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    private static String requireNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
