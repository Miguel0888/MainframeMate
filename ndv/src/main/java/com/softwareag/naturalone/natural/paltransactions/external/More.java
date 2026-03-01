package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IPalTypeStream;

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
