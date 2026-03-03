package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeConnect;

public final class PalTypeConnect extends PalType implements IPalTypeConnect {
    private static final long serialVersionUID = 1L;
    private String benutzer;
    private String kennwort;
    private String befehlszeile;

    public PalTypeConnect(String benutzer, String kennwort, String befehlszeile) {
        super(); type = 1;
        this.benutzer = benutzer; this.kennwort = kennwort; this.befehlszeile = befehlszeile;
    }

    public void serialize() { stringToBuffer(benutzer); stringToBuffer(kennwort); stringToBuffer(befehlszeile); }
    public void restore() { /* server does not send this type */ }

    public String getUser() { return benutzer; }
    public String getPassword() { return kennwort; }
}
