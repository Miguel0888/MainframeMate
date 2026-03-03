package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeResultEx;

public final class PalTypeResultEx extends PalType implements IPalTypeResultEx {
    private static final long serialVersionUID = 1L;
    private String kurzText = "";
    private String systemText = "";
    private String quellenName = "";
    private String bibliothek = "";
    private int naturalTyp;
    private int zeile;
    private int spalte;
    private int symbolLaenge;
    private int datenbankNummer;
    private int dateiNummer;
    private int fehlerArt;

    public PalTypeResultEx() { super(); typSchluessel = 11; }

    public void serialize() { /* server-only */ }
    public void restore() {
        kurzText = stringFromBuffer();     // 1 nat error text
        systemText = stringFromBuffer();    // 2 sys error text
        quellenName = stringFromBuffer();   // 3 source name
        bibliothek = stringFromBuffer();    // 4 library
        naturalTyp = intFromBuffer();       // 5 nat typSchluessel
        zeile = intFromBuffer();            // 6 row
        intFromBuffer();                    // 7 reserved
        spalte = intFromBuffer();           // 8 column
        symbolLaenge = intFromBuffer();     // 9 symbol length
        datenbankNummer = intFromBuffer();  // 10 dbid
        dateiNummer = intFromBuffer();      // 11 fnr
        intFromBuffer();                    // 12 reserved
        fehlerArt = intFromBuffer();        // 13 error kind
        intFromBuffer();                    // 14 reserved
    }

    public String getShortText() { return kurzText; }
    public String getSystemText() { return systemText; }
    public int getRow() { return zeile; }
    public int getColumn() { return spalte; }
    public int getLengthSymbol() { return symbolLaenge; }
    public String getName() { return quellenName; }
    public String getLibrary() { return bibliothek; }
    public int getKind() { return fehlerArt; }
    public int getType() { return naturalTyp; }
    public int getDatabaseId() { return datenbankNummer; }
    public int getFileNumber() { return dateiNummer; }
}
