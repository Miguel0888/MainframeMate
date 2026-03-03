package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeSystemFile;

public final class PalTypeSystemFile extends PalType implements IPalTypeSystemFile {
    private static final long serialVersionUID = 1L;
    private int datenbankNummer;
    private int dateiNummer;
    private String kennwort = "";
    private String chiffre = "";
    private boolean rosig;
    private int art;
    private String standort = "";
    private String aliasName = "";

    public PalTypeSystemFile() { super(); typSchluessel = 3; }
    public PalTypeSystemFile(int datenbankNummer, int dateiNummer, int art) {
        this(); this.datenbankNummer = datenbankNummer; this.dateiNummer = dateiNummer; this.art = art;
    }

    public void serialize() { /* server-only */ }
    public void restore() {
        datenbankNummer = intFromBuffer();
        dateiNummer = intFromBuffer();
        kennwort = stringFromBuffer();
        chiffre = stringFromBuffer();
        rosig = intFromBuffer() == 1;
        art = intFromBuffer();
        // remap FDIC to FDDM for non-PC servers
        if (art == FDIC && serverArt != 3) art = FDDM;
        standort = stringFromBuffer();
        if (lesePosition < datensatzLaenge) aliasName = stringFromBuffer();
    }

    public String getAlias() { return aliasName; }
    public void setAlias(String a) { aliasName = a != null ? a : ""; }
    public String getCipher() { return chiffre; }
    public void setCipher(String c) { chiffre = c != null ? c : ""; }
    public int getDatabaseId() { return datenbankNummer; }
    public int getFileNumber() { return dateiNummer; }
    public int getKind() { return art; }
    public String getLocation() { return standort; }
    public String getPassword() { return kennwort; }
    public void setPassword(String p) { kennwort = p != null ? p : ""; }
    public boolean isRosy() { return rosig; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalTypeSystemFile)) return false;
        PalTypeSystemFile t = (PalTypeSystemFile) o;
        return datenbankNummer == t.datenbankNummer && dateiNummer == t.dateiNummer && art == t.art;
    }
    public int hashCode() { int r = 17; r = 37 * r + datenbankNummer; r = 37 * r + dateiNummer; r = 37 * r + art; return r; }
    public String toString() {
        String n;
        switch (art) {
            case FNAT: n = "FNAT"; break;
            case FUSER: n = "FUSER"; break;
            case INACTIVE: n = "INACTIVE"; break;
            case FSEC: n = "FSEC"; break;
            case FDIC: n = "FDIC"; break;
            case FDDM: n = "FDDM"; break;
            default: n = String.valueOf(art);
        }
        return n + " (" + datenbankNummer + "," + dateiNummer + ")";
    }
}
