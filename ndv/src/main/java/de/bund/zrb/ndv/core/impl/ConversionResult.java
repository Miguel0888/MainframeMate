package de.bund.zrb.ndv.core.impl;

import de.bund.zrb.ndv.core.api.IPalTypeSource;

/**
 * Ergebnis einer Zeichensatz-Konvertierung oder Serialisierung.
 * Ersetzt die Exception-basierte Fehlermeldung durch einen sauberen Datenfluss.
 *
 * Enthält entweder Erfolg ({@link #isOk()}) oder Fehler-Details
 * (Meldung, betroffene Quelle, Spaltenposition).
 */
public final class ConversionResult {

    private static final ConversionResult OK = new ConversionResult(null, null, -1, (byte) 0);

    private final String fehlermeldung;
    private final IPalTypeSource quelle;
    private final int spalte;
    private final byte nichtAbbildbaresZeichen;
    private int zeile = -1;

    private ConversionResult(String fehlermeldung, IPalTypeSource quelle, int spalte, byte nichtAbbildbaresZeichen) {
        this.fehlermeldung = fehlermeldung;
        this.quelle = quelle;
        this.spalte = spalte;
        this.nichtAbbildbaresZeichen = nichtAbbildbaresZeichen;
    }

    /** Erfolg — keine Konvertierungsfehler. */
    public static ConversionResult ok() {
        return OK;
    }

    /** Fehler — nicht abbildbarer Codepunkt erkannt. */
    public static ConversionResult error(String meldung, IPalTypeSource quelle, int spalte) {
        return new ConversionResult(meldung, quelle, spalte, (byte) 0);
    }

    /** Fehler — mit konkretem nicht-abbildbarem Byte-Wert und Offset. */
    public static ConversionResult error(String meldung, IPalTypeSource quelle, int spalte, byte nichtAbbildbaresZeichen) {
        return new ConversionResult(meldung, quelle, spalte, nichtAbbildbaresZeichen);
    }

    public boolean isOk() {
        return fehlermeldung == null;
    }

    public boolean hasError() {
        return fehlermeldung != null;
    }

    public String getMessage() {
        return fehlermeldung;
    }

    public IPalTypeSource getSource() {
        return quelle;
    }

    public int getColumn() {
        return spalte;
    }

    /** Das nicht abbildbare Byte (0 wenn nicht zutreffend). */
    public byte getUnmappableCodePoint() {
        return nichtAbbildbaresZeichen;
    }

    public int getRow() {
        return zeile;
    }

    public void setRow(int zeile) {
        this.zeile = zeile;
    }
}

