package de.bund.zrb.ndv.transaction.api;

import de.bund.zrb.ndv.core.api.IPalTypeObject;

/**
 * Ergebnis einer Quellcode-Suche nach Objektname.
 */
public interface ISourceLookupResult {

    IPalTypeObject getObject();

    String getLibrary();

    int getDatabaseId();

    int getFileNumber();
}

