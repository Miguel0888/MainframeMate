package de.bund.zrb.ndv.transaction.api;

/**
 * Ergebnis eines Quellcode-Downloads.
 */
public interface IDownloadResult {

    String[] getSource();

    int getLineIncrement();
}
