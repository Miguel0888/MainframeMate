package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Ergebnis eines Quellcode-Downloads.
 */
public interface IDownloadResult {

    String[] getSource();

    int getLineIncrement();
}
