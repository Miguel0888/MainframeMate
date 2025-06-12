package de.bund.zrb.login;

import java.awt.*;

public class DialogLoginCredentialsProvider implements LoginCredentialsProvider {

    private final Component parentComponent;

    public DialogLoginCredentialsProvider(Component parentComponent) {
        this.parentComponent = parentComponent;
    }

    public LoginCredentials requestCredentials(String host, String username) {
//        ConnectDialog dialog = new ConnectDialog(parentComponent, host, username);
//        if (dialog.showDialog()) {
//            return new LoginCredentials(dialog.getUsername(), dialog.getPassword());
//        }
        return null;
    }
}
