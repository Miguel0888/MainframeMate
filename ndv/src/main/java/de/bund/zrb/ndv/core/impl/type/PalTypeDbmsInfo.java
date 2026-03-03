package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeDbmsInfo;

public final class PalTypeDbmsInfo extends PalType implements IPalTypeDbmsInfo {
    private static final long serialVersionUID = 1L;
    private int datenbankNummer;
    private int datenbankTyp;
    private String parameter = "";

    public PalTypeDbmsInfo() { super(); type = 49; }

    public void serialize() { /* server-only */ }
    public void restore() {
        datenbankNummer = intFromBuffer();
        datenbankTyp = intFromBuffer();
        String p = stringFromBuffer();
        if (p != null) parameter = p;
    }

    public int getDbid() { return datenbankNummer; }
    public int getType() { return datenbankTyp; }
    public String getParameter() { return parameter; }

    public boolean isTypeAdabas() { return datenbankTyp == ADABAS; }
    public boolean isTypeSql() { return datenbankTyp == SQL; }
    public boolean isTypeXml() { return datenbankTyp == XML; }
    public boolean isTypeAdabas2() { return datenbankTyp == ADABAS2; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalTypeDbmsInfo)) return false;
        PalTypeDbmsInfo that = (PalTypeDbmsInfo) o;
        return datenbankNummer == that.datenbankNummer && datenbankTyp == that.datenbankTyp &&
                (parameter != null ? parameter.equals(that.parameter) : that.parameter == null);
    }
    public int hashCode() { int r = 17; r = 37 * r + datenbankNummer; r = 37 * r + datenbankTyp; r = 37 * r + (parameter != null ? parameter.hashCode() : 0); return r; }
    public String toString() { return "DbId=" + datenbankNummer + ", Dbtype=" + datenbankTyp + ", Parameter=" + parameter; }
}
