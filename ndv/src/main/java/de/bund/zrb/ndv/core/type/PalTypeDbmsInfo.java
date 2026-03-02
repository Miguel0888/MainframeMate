package de.bund.zrb.ndv.core.type;

import de.bund.zrb.ndv.core.api.IPalTypeDbmsInfo;

public final class PalTypeDbmsInfo extends PalType implements IPalTypeDbmsInfo {
    private static final long serialVersionUID = 1L;
    private int dbid;
    private int dbType;
    private String parameter = "";

    public PalTypeDbmsInfo() { super(); type = 49; }

    public void serialize() { /* server-only */ }
    public void restore() {
        dbid = intFromBuffer();
        dbType = intFromBuffer();
        String p = stringFromBuffer();
        if (p != null) parameter = p;
    }

    public int getDbid() { return dbid; }
    public int getType() { return dbType; }
    public String getParameter() { return parameter; }

    public boolean isTypeAdabas() { return dbType == ADABAS; }
    public boolean isTypeSql() { return dbType == SQL; }
    public boolean isTypeXml() { return dbType == XML; }
    public boolean isTypeAdabas2() { return dbType == ADABAS2; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalTypeDbmsInfo)) return false;
        PalTypeDbmsInfo that = (PalTypeDbmsInfo) o;
        return dbid == that.dbid && dbType == that.dbType &&
                (parameter != null ? parameter.equals(that.parameter) : that.parameter == null);
    }
    public int hashCode() { int r = 17; r = 37 * r + dbid; r = 37 * r + dbType; r = 37 * r + (parameter != null ? parameter.hashCode() : 0); return r; }
    public String toString() { return "DbId=" + dbid + ", Dbtype=" + dbType + ", Parameter=" + parameter; }
}
