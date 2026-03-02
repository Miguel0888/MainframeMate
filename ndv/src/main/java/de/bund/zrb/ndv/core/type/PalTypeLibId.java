package de.bund.zrb.ndv.core.type;

import de.bund.zrb.ndv.core.api.IPalTypeLibId;

public class PalTypeLibId extends PalType implements IPalTypeLibId {
    private static final long serialVersionUID = 1L;
    private int databaseId;
    private int fileNumber;
    private String library = "";
    private String password = "";
    private String cipher = "";

    public PalTypeLibId() { super(); type = 6; }
    public PalTypeLibId(int databaseId, int fileNumber, String library, String password, String cipher, int type) {
        super();
        if (type != 6 && type != 30) throw new IllegalArgumentException("type must be 6 or 30");
        this.type = type;
        this.databaseId = databaseId; this.fileNumber = fileNumber;
        this.library = library != null ? library : "";
        this.password = password != null ? password : "";
        this.cipher = cipher != null ? cipher : "";
    }

    public void serialize() {
        intToBuffer(databaseId); intToBuffer(fileNumber);
        stringToBuffer(library); stringToBuffer(password); stringToBuffer(cipher);
    }
    public void restore() {
        databaseId = intFromBuffer(); fileNumber = intFromBuffer();
        library = stringFromBuffer(); password = stringFromBuffer(); cipher = stringFromBuffer();
    }

    public int getDatabaseId() { return databaseId; }
    public void setDatabaseId(int id) { this.databaseId = id; }
    public int getFileNumber() { return fileNumber; }
    public void setFileNumber(int n) { this.fileNumber = n; }
    public String getLibrary() { return library; }
    public void setLibrary(String lib) { this.library = lib != null ? lib : ""; }
    public String getPassword() { return password; }
    public void setPassword(String pw) { this.password = pw != null ? pw : ""; }
    public String getCipher() { return cipher; }
    public void setCipher(String c) { this.cipher = c != null ? c : ""; }
    public void setType(int type) { this.type = type; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalTypeLibId)) return false;
        PalTypeLibId t = (PalTypeLibId) o;
        return databaseId == t.databaseId && fileNumber == t.fileNumber &&
                library.equals(t.library) && password.equals(t.password) && cipher.equals(t.cipher);
    }
    public int hashCode() {
        int r = 17; r = 37 * r + databaseId; r = 37 * r + fileNumber;
        r = 37 * r + library.hashCode(); r = 37 * r + password.hashCode(); r = 37 * r + cipher.hashCode();
        return r;
    }
    public String toString() { return library + " (" + databaseId + "," + fileNumber + ")"; }
}
