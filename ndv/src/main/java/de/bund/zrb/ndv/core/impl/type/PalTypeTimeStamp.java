package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeTimeStamp;

public final class PalTypeTimeStamp extends PalType implements IPalTypeTimeStamp {
    private static final long serialVersionUID = 1L;
    private int merkmale;
    private String zeitstempel = "";
    private String benutzerKennung = "";

    public PalTypeTimeStamp() { super(); typSchluessel = 54; }
    public PalTypeTimeStamp(int merkmale, String zeitstempel, String benutzerKennung) {
        this();
        this.merkmale = merkmale;
        this.zeitstempel = zeitstempel != null ? zeitstempel : "";
        this.benutzerKennung = benutzerKennung != null ? benutzerKennung : "";
    }

    public void serialize() { ganzzahlInPuffer(merkmale); textInPuffer(zeitstempel); textInPuffer(benutzerKennung); }
    public void restore() { merkmale = intFromBuffer(); zeitstempel = stringFromBuffer(); benutzerKennung = stringFromBuffer(); }

    public int getFlags() { return merkmale; }
    public String getTimeStamp() { return zeitstempel; }
    public String getUserId() { return benutzerKennung; }

    public int hashCode() {
        int r = 17;
        r = 37 * r + merkmale;
        r = 37 * r + (zeitstempel != null ? zeitstempel.hashCode() : 0);
        r = 37 * r + (benutzerKennung != null ? benutzerKennung.hashCode() : 0);
        return r;
    }
}
