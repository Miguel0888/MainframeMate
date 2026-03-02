package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.*;

import java.util.ArrayList;

/**
 * Stub-Implementierung für Natural-Parameter (NATPARM).
 * Enthält Unterstrukturen für Limits, Report, Zeichenzuweisung, Compiler-Optionen usw.
 */
public class PalTypeNatParm extends PalType implements IPalTypeNatParm {

    private Limit grenzwerte;
    private Report bericht;
    private FldApp feldAnwendung;
    private CharAssign zeichenZuweisung;
    private Err fehlerEinstellungen;
    private CompOpt compilerOptionen;
    private Rpc fernaufruf;
    private BuffSize pufferGroessen;
    private Regional regional;
    private int datensatzIndex;

    public PalTypeNatParm() {
        this.type = 25;
    }

    @Override
    public void serialize() {
        // Stub
    }

    @Override
    public void restore() {
        // Stub — vollständige Deserialisierung noch nicht implementiert
        this.grenzwerte = new Limit();
        this.bericht = new Report();
        this.feldAnwendung = new FldApp();
        this.zeichenZuweisung = new CharAssign();
        this.fehlerEinstellungen = new Err();
        this.compilerOptionen = new CompOpt();
        this.fernaufruf = new Rpc();
        this.pufferGroessen = new BuffSize();
        this.regional = new Regional();
    }

    @Override public void setLimit(Limit v) { this.grenzwerte = v; }
    @Override public void setReport(Report v) { this.bericht = v; }
    @Override public void setFldApp(FldApp v) { this.feldAnwendung = v; }
    @Override public void setCharAssign(CharAssign v) { this.zeichenZuweisung = v; }
    @Override public void setErr(Err v) { this.fehlerEinstellungen = v; }
    @Override public void setCompOpt(CompOpt v) { this.compilerOptionen = v; }
    @Override public void setRpc(Rpc v) { this.fernaufruf = v; }
    @Override public void setBuffSize(BuffSize v) { this.pufferGroessen = v; }
    @Override public void setRegional(Regional v) { this.regional = v; }

    @Override public Limit getLimit() { return grenzwerte; }
    @Override public BuffSize getBuffSize() { return pufferGroessen; }
    @Override public CharAssign getCharAssign() { return zeichenZuweisung; }
    @Override public CompOpt getCompOpt() { return compilerOptionen; }
    @Override public Err getErr() { return fehlerEinstellungen; }
    @Override public FldApp getFldApp() { return feldAnwendung; }
    @Override public Regional getRegional() { return regional; }
    @Override public Report getReport() { return bericht; }
    @Override public Rpc getRpc() { return fernaufruf; }

    @Override public void setRecordIndex(int v) { this.datensatzIndex = v; }
    @Override public int getRecordIndex() { return datensatzIndex; }

    // =====================================================================
    //  Innere Klassen
    // =====================================================================

    public static class Limit implements ILimit {
        private int merkmale;
        private int maxCpuZeit;
        private int seitenDatensatz;
        private int verarbeitungsSchleifenGrenze;

        @Override public int getFlags() { return merkmale; }
        @Override public int getMaximumCPUTime() { return maxCpuZeit; }
        @Override public int getPageDataSet() { return seitenDatensatz; }
        @Override public int getProcessingLoopLimit() { return verarbeitungsSchleifenGrenze; }
        @Override public void setProcessingLoopLimit(int v) { this.verarbeitungsSchleifenGrenze = v; }
    }

    public static class Report implements IReport {
        private int merkmale;
        private int zeilenBreite;
        private int seitenGroesse;
        private int abstandsFaktor;
        private byte terminalModus;

        @Override public int getFlags() { return merkmale; }
        @Override public int getLineSize() { return zeilenBreite; }
        @Override public int getPageSize() { return seitenGroesse; }
        @Override public int getSpacingFactor() { return abstandsFaktor; }
        @Override public byte getTerminalMode() { return terminalModus; }
        @Override public void setLineSize(int v) { this.zeilenBreite = v; }
        @Override public void setPageSize(int v) { this.seitenGroesse = v; }
        @Override public void setSpacingFactor(int v) { this.abstandsFaktor = v; }
        @Override public void setTerminalMode(byte v) { this.terminalModus = v; }
    }

    public static class FldApp implements IFldApp {
        private byte datumsFormat;
        private byte datumsFormatAusgabe;
        private byte datumsFormatStapel;
        private byte datumsFormatTitel;
        private int merkmale;
        private int maxJahr;
        private byte druckModus;

        @Override public byte getDateFormat() { return datumsFormat; }
        @Override public byte getDateFormatOutput() { return datumsFormatAusgabe; }
        @Override public byte getDateFormatStack() { return datumsFormatStapel; }
        @Override public byte getDateFormatTitle() { return datumsFormatTitel; }
        @Override public int getFlags() { return merkmale; }
        @Override public int getMaxyear() { return maxJahr; }
        @Override public byte getPrintMode() { return druckModus; }
        @Override public void setDateFormat(byte v) { this.datumsFormat = v; }
        @Override public void setDateFormatOutput(byte v) { this.datumsFormatAusgabe = v; }
        @Override public void setDateFormatStack(byte v) { this.datumsFormatStapel = v; }
        @Override public void setDateFormatTitle(byte v) { this.datumsFormatTitel = v; }
        @Override public void setFlags(int v) { this.merkmale = v; }
        @Override public void setMaxyear(int v) { this.maxJahr = v; }
        @Override public void setPrintMode(byte v) { this.druckModus = v; }
    }

    public static class CharAssign implements ICharAssign {
        private byte dezimalZeichen;
        private byte eingabeZuweisung;
        private byte eingabeTrennzeichen;
        private byte terminalBefehlszeichen;
        private byte tausenderTrennzeichen;

        @Override public byte getDecimalChar() { return dezimalZeichen; }
        @Override public byte getInputAssignment() { return eingabeZuweisung; }
        @Override public byte getInputDelimiter() { return eingabeTrennzeichen; }
        @Override public byte getTermCommandChar() { return terminalBefehlszeichen; }
        @Override public byte getThousandSeperator() { return tausenderTrennzeichen; }
        @Override public void setDecimalChar(byte v) { this.dezimalZeichen = v; }
        @Override public void setInputAssignment(byte v) { this.eingabeZuweisung = v; }
        @Override public void setInputDelimiter(byte v) { this.eingabeTrennzeichen = v; }
        @Override public void setTermCommandChar(byte v) { this.terminalBefehlszeichen = v; }
        @Override public void setThousandSeperator(byte v) { this.tausenderTrennzeichen = v; }
    }

    public static class Err implements IErr {
        private int merkmale;

        @Override public int getFlags() { return merkmale; }
    }

    public static class CompOpt implements ICompOpt {
        private int merkmale;
        private int quellZeilenLaenge;
        private int maxGenauigkeit;

        @Override public int getFlags() { return merkmale; }
        @Override public int getSourceLinelength() { return quellZeilenLaenge; }
        @Override public int getMaxprec() { return maxGenauigkeit; }
        @Override public void setFlags(int v) { this.merkmale = v; }
        @Override public void resetFlags(int v) { this.merkmale &= ~v; }
        @Override public void setMaxprec(int v) { this.maxGenauigkeit = v; }
    }

    public static class Rpc implements IRpc {
        private int komprimierung;
        private int merkmale;
        private int wartezeit;

        @Override public int getCompression() { return komprimierung; }
        @Override public int getFlags() { return merkmale; }
        @Override public int getTimeout() { return wartezeit; }
    }

    public static class BuffSize implements IBuffSize {
        private int edtGroesse;
        private int groesse2;
        private int groesse3;
        private int groesse4;
        private int groesse5;

        @Override public int getEdtSize() { return edtGroesse; }
        @Override public int getSize2() { return groesse2; }
        @Override public int getSize3() { return groesse3; }
        @Override public int getSize4() { return groesse4; }
        @Override public int getSize5() { return groesse5; }
    }

    public static class Regional implements IRegional {
        private String zeichensatz = "";
        private boolean konvertierungsFehler;
        private boolean beibehalten;
        private boolean utf8;

        @Override public String getCodePage() { return zeichensatz; }
        @Override public boolean isConvErr() { return konvertierungsFehler; }
        @Override public boolean isRetain() { return beibehalten; }
        @Override public boolean isUtf8() { return utf8; }
        @Override public void setCodePage(String v) { this.zeichensatz = v; }
    }
}

