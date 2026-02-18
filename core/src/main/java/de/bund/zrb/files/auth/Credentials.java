package de.bund.zrb.files.auth;

public class Credentials {

    private final String host;
    private final String username;
    private final String password;

    public Credentials(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}

