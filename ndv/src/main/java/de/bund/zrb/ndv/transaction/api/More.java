package de.bund.zrb.ndv.transaction.api;

import de.bund.zrb.ndv.core.api.IPalTypeStream;

public class More {
    private final IPalTypeStream bildschirmInhalt = null;
    private String befehle;

    public final String getCommands() {
        return befehle;
    }

    public final IPalTypeStream getScreen() {
        return bildschirmInhalt;
    }

    public final void setCommands(String commands) {
        this.befehle = commands;
    }
}
