package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeLibrary;

public final class PalTypeLibrary extends PalType implements IPalTypeLibrary {
    private static final long serialVersionUID = 1L;
    private String library = "";
    private int flags;

    public PalTypeLibrary() { super(); type = 5; }

    public void serialize() { /* server-only */ }
    public void restore() { library = stringFromBuffer(); flags = intFromBuffer(); }

    public String getLibrary() { return library; }
    public int getFlags() { return flags; }

    public String toString() { return library; }
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalTypeLibrary)) return false;
        return library != null ? library.equals(((PalTypeLibrary) o).library) : ((PalTypeLibrary) o).library == null;
    }
    public int hashCode() { return library != null ? library.hashCode() : 0; }
}
