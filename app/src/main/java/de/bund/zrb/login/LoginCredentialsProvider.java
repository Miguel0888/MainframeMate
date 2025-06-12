package de.bund.zrb.login;

public interface LoginCredentialsProvider {
    LoginCredentials requestCredentials(String host, String username);
}
