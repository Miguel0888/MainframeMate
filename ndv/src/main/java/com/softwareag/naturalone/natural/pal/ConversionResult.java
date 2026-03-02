package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSource;

/**
 * Ergebnis einer Zeichensatz-Konvertierung oder Serialisierung.
 * Ersetzt die Exception-basierte Fehlermeldung durch einen sauberen Datenfluss.
 *
 * Enthält entweder Erfolg ({@link #isOk()}) oder Fehler-Details
 * (Meldung, betroffene Quelle, Spaltenposition).
 */
public final class ConversionResult {

    private static final ConversionResult OK = new ConversionResult(null, null, -1);

    private final String fehlermeldung;
    private final IPalTypeSource quelle;
    private final int spalte;
    private int zeile = -1;

    private ConversionResult(String fehlermeldung, IPalTypeSource quelle, int spalte) {
        this.fehlermeldung = fehlermeldung;
        this.quelle = quelle;
        this.spalte = spalte;
    }

    /** Erfolg — keine Konvertierungsfehler. */
    public static ConversionResult ok() {
        return OK;
    }

    /** Fehler — nicht abbildbarer Codepunkt erkannt. */
    public static ConversionResult error(String meldung, IPalTypeSource quelle, int spalte) {
        return new ConversionResult(meldung, quelle, spalte);
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

    public int getRow() {
        return zeile;
    }

    public void setRow(int zeile) {
        this.zeile = zeile;
    }
}

