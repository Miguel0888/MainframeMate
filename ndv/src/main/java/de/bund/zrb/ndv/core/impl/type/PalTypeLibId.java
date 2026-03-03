package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeLibId;

public class PalTypeLibId extends PalType implements IPalTypeLibId {
    private static final long serialVersionUID = 1L;
    private int datenbankNummer;
    private int dateiNummer;
    private String bibliothek = "";
    private String kennwort = "";
    private String chiffre = "";

    public PalTypeLibId() { super(); type = 6; }
    public PalTypeLibId(int datenbankNummer, int dateiNummer, String bibliothek, String kennwort, String chiffre, int type) {
        super();
        if (type != 6 && type != 30) throw new IllegalArgumentException("type must be 6 or 30");
        this.type = type;
        this.datenbankNummer = datenbankNummer; this.dateiNummer = dateiNummer;
        this.bibliothek = bibliothek != null ? bibliothek : "";
        this.kennwort = kennwort != null ? kennwort : "";
        this.chiffre = chiffre != null ? chiffre : "";
    }

    public void serialize() {
        intToBuffer(datenbankNummer); intToBuffer(dateiNummer);
        stringToBuffer(bibliothek); stringToBuffer(kennwort); stringToBuffer(chiffre);
    }
    public void restore() {
        datenbankNummer = intFromBuffer(); dateiNummer = intFromBuffer();
        bibliothek = stringFromBuffer(); kennwort = stringFromBuffer(); chiffre = stringFromBuffer();
    }

    public int getDatabaseId() { return datenbankNummer; }
    public void setDatabaseId(int id) { this.datenbankNummer = id; }
    public int getFileNumber() { return dateiNummer; }
    public void setFileNumber(int n) { this.dateiNummer = n; }
    public String getLibrary() { return bibliothek; }
    public void setLibrary(String lib) { this.bibliothek = lib != null ? lib : ""; }
    public String getPassword() { return kennwort; }
    public void setPassword(String pw) { this.kennwort = pw != null ? pw : ""; }
    public String getCipher() { return chiffre; }
    public void setCipher(String c) { this.chiffre = c != null ? c : ""; }
    public void setType(int type) { this.type = type; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalTypeLibId)) return false;
        PalTypeLibId t = (PalTypeLibId) o;
        return datenbankNummer == t.datenbankNummer && dateiNummer == t.dateiNummer &&
                bibliothek.equals(t.bibliothek) && kennwort.equals(t.kennwort) && chiffre.equals(t.chiffre);
    }
    public int hashCode() {
        int r = 17; r = 37 * r + datenbankNummer; r = 37 * r + dateiNummer;
        r = 37 * r + bibliothek.hashCode(); r = 37 * r + kennwort.hashCode(); r = 37 * r + chiffre.hashCode();
        return r;
    }
    public String toString() { return bibliothek + " (" + datenbankNummer + "," + dateiNummer + ")"; }
}
