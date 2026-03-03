package de.bund.zrb.ndv.core.impl.type;

public final class PalTypeClientConfig extends PalType {
    private static final long serialVersionUID = 1L;
    private char nichtDbFeld;
    private char sqlTrennzeichen;
    private char dynamischeQuelle;
    private char globaleVariable;
    private char alternativerZeichensatz;
    private String bezeichnerErstesZeichenGueltig = "";
    private String bezeichnerFolgeZeichenGueltig = "";
    private String objektErstesZeichenGueltig = "";
    private String objektFolgeZeichenGueltig = "";
    private String ddmErstesZeichenGueltig = "";
    private String ddmFolgeZeichenGueltig = "";
    private String bibErstesZeichenGueltig = "";
    private String bibFolgeZeichenGueltig = "";

    public PalTypeClientConfig() { super(); type = 50; }

    public void serialize() { /* server-only */ }
    public void restore() {
        nichtDbFeld = (char)(byteFromBuffer() & 0xFF);
        sqlTrennzeichen = (char)(byteFromBuffer() & 0xFF);
        dynamischeQuelle = (char)(byteFromBuffer() & 0xFF);
        globaleVariable = (char)(byteFromBuffer() & 0xFF);
        alternativerZeichensatz = (char)(byteFromBuffer() & 0xFF);
        bezeichnerErstesZeichenGueltig = stringFromBuffer();
        bezeichnerFolgeZeichenGueltig = stringFromBuffer();
        objektErstesZeichenGueltig = stringFromBuffer();
        objektFolgeZeichenGueltig = stringFromBuffer();
        ddmErstesZeichenGueltig = stringFromBuffer();
        ddmFolgeZeichenGueltig = stringFromBuffer();
        bibErstesZeichenGueltig = stringFromBuffer();
        bibFolgeZeichenGueltig = stringFromBuffer();
    }

    public char getNonDbField() { return nichtDbFeld; }
    public char getSqlSep() { return sqlTrennzeichen; }
    public char getDynSrc() { return dynamischeQuelle; }
    public char getGlobalVar() { return globaleVariable; }
    public char getAltCharet() { return alternativerZeichensatz; }
    public String getIdent1stValid() { return bezeichnerErstesZeichenGueltig; }
    public String getIdentSubsequentValid() { return bezeichnerFolgeZeichenGueltig; }
    public String getObject1stValid() { return objektErstesZeichenGueltig; }
    public String getObjectSubValid() { return objektFolgeZeichenGueltig; }
    public String getDdm1stValid() { return ddmErstesZeichenGueltig; }
    public String getDdmSubValid() { return ddmFolgeZeichenGueltig; }
    public String getLib1stValid() { return bibErstesZeichenGueltig; }
    public String getLibSubValid() { return bibFolgeZeichenGueltig; }

    public static long getSerialVersionUID() { return serialVersionUID; }
}
