package com.softwareag.naturalone.natural.pal;

/** Stub — SQL-Authentifizierung nicht unterstützt (nur ADABAS). */
public class PalTypeSQLAuthentification extends PalType {
    @Override public void serialize() { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public void restore()   { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public int get()        { return 26; }
}

