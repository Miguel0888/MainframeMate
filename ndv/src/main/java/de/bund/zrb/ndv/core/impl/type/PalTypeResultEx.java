package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeResultEx;

public final class PalTypeResultEx extends PalType implements IPalTypeResultEx {
    private static final long serialVersionUID = 1L;
    private String shortText = "";
    private String systemText = "";
    private String name = "";
    private String library = "";
    private int natType;
    private int row;
    private int column;
    private int lengthSymbol;
    private int databaseId;
    private int fileNumber;
    private int kind;

    public PalTypeResultEx() { super(); type = 11; }

    public void serialize() { /* server-only */ }
    public void restore() {
        shortText = stringFromBuffer();     // 1 nat error text
        systemText = stringFromBuffer();    // 2 sys error text
        name = stringFromBuffer();          // 3 source name
        library = stringFromBuffer();       // 4 library
        natType = intFromBuffer();          // 5 nat type
        row = intFromBuffer();              // 6 row
        intFromBuffer();                    // 7 reserved
        column = intFromBuffer();           // 8 column
        lengthSymbol = intFromBuffer();     // 9 symbol length
        databaseId = intFromBuffer();       // 10 dbid
        fileNumber = intFromBuffer();       // 11 fnr
        intFromBuffer();                    // 12 reserved
        kind = intFromBuffer();             // 13 error kind
        intFromBuffer();                    // 14 reserved
    }

    public String getShortText() { return shortText; }
    public String getSystemText() { return systemText; }
    public int getRow() { return row; }
    public int getColumn() { return column; }
    public int getLengthSymbol() { return lengthSymbol; }
    public String getName() { return name; }
    public String getLibrary() { return library; }
    public int getKind() { return kind; }
    public int getType() { return natType; }
    public int getDatabaseId() { return databaseId; }
    public int getFileNumber() { return fileNumber; }
}
