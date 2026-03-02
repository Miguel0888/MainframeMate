package de.bund.zrb.ndv.core.type;

import de.bund.zrb.ndv.core.api.IPalTypeSystemFile;

public final class PalTypeSystemFile extends PalType implements IPalTypeSystemFile {
    private static final long serialVersionUID = 1L;
    private int databaseId;
    private int fileNumber;
    private String password = "";
    private String cipher = "";
    private boolean rosy;
    private int kind;
    private String location = "";
    private String alias = "";

    public PalTypeSystemFile() { super(); type = 3; }
    public PalTypeSystemFile(int databaseId, int fileNumber, int kind) {
        this(); this.databaseId = databaseId; this.fileNumber = fileNumber; this.kind = kind;
    }

    public void serialize() { /* server-only */ }
    public void restore() {
        databaseId = intFromBuffer();
        fileNumber = intFromBuffer();
        password = stringFromBuffer();
        cipher = stringFromBuffer();
        rosy = intFromBuffer() == 1;
        kind = intFromBuffer();
        // remap FDIC to FDDM for non-PC servers
        if (kind == FDIC && ndvType != 3) kind = FDDM;
        location = stringFromBuffer();
        if (recordTail < recordLength) alias = stringFromBuffer();
    }

    public String getAlias() { return alias; }
    public void setAlias(String a) { alias = a != null ? a : ""; }
    public String getCipher() { return cipher; }
    public void setCipher(String c) { cipher = c != null ? c : ""; }
    public int getDatabaseId() { return databaseId; }
    public int getFileNumber() { return fileNumber; }
    public int getKind() { return kind; }
    public String getLocation() { return location; }
    public String getPassword() { return password; }
    public void setPassword(String p) { password = p != null ? p : ""; }
    public boolean isRosy() { return rosy; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalTypeSystemFile)) return false;
        PalTypeSystemFile t = (PalTypeSystemFile) o;
        return databaseId == t.databaseId && fileNumber == t.fileNumber && kind == t.kind;
    }
    public int hashCode() { int r = 17; r = 37 * r + databaseId; r = 37 * r + fileNumber; r = 37 * r + kind; return r; }
    public String toString() {
        String n;
        switch (kind) {
            case FNAT: n = "FNAT"; break;
            case FUSER: n = "FUSER"; break;
            case INACTIVE: n = "INACTIVE"; break;
            case FSEC: n = "FSEC"; break;
            case FDIC: n = "FDIC"; break;
            case FDDM: n = "FDDM"; break;
            default: n = String.valueOf(kind);
        }
        return n + " (" + databaseId + "," + fileNumber + ")";
    }
}
