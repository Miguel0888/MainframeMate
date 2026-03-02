package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Ergebnis eines Quellcode-Downloads.
 */
public interface IDownloadResult {

    /**
     * Gibt den heruntergeladenen Quellcode als Zeilen-Array zurück.
     */
    String[] getSource();
}

