package com.softwareag.naturalone.natural.paltransactions.internal;

import com.softwareag.naturalone.natural.paltransactions.external.IDownloadResult;

/**
 * Ergebnis eines Quellcode-Downloads.
 * Enthält die heruntergeladenen Quellzeilen und die Schrittweite der Zeilennummerierung.
 */
public class DownloadResult implements IDownloadResult {

    private final String[] quellzeilen;
    private final int schrittweite;

    public DownloadResult(String[] quellzeilen, int schrittweite) {
        this.quellzeilen = quellzeilen;
        this.schrittweite = schrittweite;
    }

    @Override
    public String[] getSource() {
        return this.quellzeilen;
    }

    @Override
    public int getLineIncrement() {
        return this.schrittweite;
    }
}

