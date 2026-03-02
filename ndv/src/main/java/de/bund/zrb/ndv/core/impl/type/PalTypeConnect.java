package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeConnect;

public final class PalTypeConnect extends PalType implements IPalTypeConnect {
    private static final long serialVersionUID = 1L;
    private String user;
    private String password;
    private String commandline;

    public PalTypeConnect(String user, String password, String commandline) {
        super(); type = 1;
        this.user = user; this.password = password; this.commandline = commandline;
    }

    public void serialize() { stringToBuffer(user); stringToBuffer(password); stringToBuffer(commandline); }
    public void restore() { /* server does not send this type */ }

    public String getUser() { return user; }
    public String getPassword() { return password; }
}
