package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IPalTypeObject;

/**
 * Ergebnis einer Quellcode-Suche nach Objektname.
 */
public interface ISourceLookupResult {

    IPalTypeObject getObject();

    String getLibrary();

    int getDatabaseId();

    int getFileNumber();
}

