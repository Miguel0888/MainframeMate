package com.softwareag.naturalone.natural.paltransactions.internal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeObject;
import com.softwareag.naturalone.natural.paltransactions.external.ISourceLookupResult;

/**
 * Ergebnis einer Quellcode-Suche nach Objektname.
 * Enthält das gefundene Objekt, die Bibliothek sowie Datenbank-ID und Dateinummer.
 */
public class SourceLookupResult implements ISourceLookupResult {

    private final IPalTypeObject objekt;
    private final String bibliothek;
    private final int datenbankId;
    private final int dateiNummer;

    public SourceLookupResult(IPalTypeObject objekt, String bibliothek, int datenbankId, int dateiNummer) {
        this.bibliothek = bibliothek;
        this.objekt = objekt;
        this.datenbankId = (datenbankId == 0 && dateiNummer == 0) ? -1 : datenbankId;
        this.dateiNummer = (datenbankId == 0 && dateiNummer == 0) ? -1 : dateiNummer;
    }

    @Override
    public IPalTypeObject getObject() {
        return this.objekt;
    }

    @Override
    public String getLibrary() {
        return this.bibliothek;
    }

    @Override
    public int getDatabaseId() {
        return this.datenbankId;
    }

    @Override
    public int getFileNumber() {
        return this.dateiNummer;
    }
}

