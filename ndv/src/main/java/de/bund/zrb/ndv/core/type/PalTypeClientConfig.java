package de.bund.zrb.ndv.core.type;

public final class PalTypeClientConfig extends PalType {
    private static final long serialVersionUID = 1L;
    private char nonDbField;
    private char sqlSep;
    private char dynSrc;
    private char globalVar;
    private char altCharet;
    private String ident1stValid = "";
    private String identSubsequentValid = "";
    private String object1stValid = "";
    private String objectSubValid = "";
    private String ddm1stValid = "";
    private String ddmSubValid = "";
    private String lib1stValid = "";
    private String libSubValid = "";

    public PalTypeClientConfig() { super(); type = 50; }

    public void serialize() { /* server-only */ }
    public void restore() {
        nonDbField = (char)(byteFromBuffer() & 0xFF);
        sqlSep = (char)(byteFromBuffer() & 0xFF);
        dynSrc = (char)(byteFromBuffer() & 0xFF);
        globalVar = (char)(byteFromBuffer() & 0xFF);
        altCharet = (char)(byteFromBuffer() & 0xFF);
        ident1stValid = stringFromBuffer();
        identSubsequentValid = stringFromBuffer();
        object1stValid = stringFromBuffer();
        objectSubValid = stringFromBuffer();
        ddm1stValid = stringFromBuffer();
        ddmSubValid = stringFromBuffer();
        lib1stValid = stringFromBuffer();
        libSubValid = stringFromBuffer();
    }

    public char getNonDbField() { return nonDbField; }
    public char getSqlSep() { return sqlSep; }
    public char getDynSrc() { return dynSrc; }
    public char getGlobalVar() { return globalVar; }
    public char getAltCharet() { return altCharet; }
    public String getIdent1stValid() { return ident1stValid; }
    public String getIdentSubsequentValid() { return identSubsequentValid; }
    public String getObject1stValid() { return object1stValid; }
    public String getObjectSubValid() { return objectSubValid; }
    public String getDdm1stValid() { return ddm1stValid; }
    public String getDdmSubValid() { return ddmSubValid; }
    public String getLib1stValid() { return lib1stValid; }
    public String getLibSubValid() { return libSubValid; }

    public static long getSerialVersionUID() { return serialVersionUID; }
}
