package de.bund.zrb.ndv.transaction.internal;

import de.bund.zrb.ndv.core.api.IPalTypeObject;
import de.bund.zrb.ndv.transaction.api.ISourceLookupResult;

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

