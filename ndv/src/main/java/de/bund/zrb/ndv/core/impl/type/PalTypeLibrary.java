package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeLibrary;

public final class PalTypeLibrary extends PalType implements IPalTypeLibrary {
    private static final long serialVersionUID = 1L;
    private String bibliothek = "";
    private int merkmale;

    public PalTypeLibrary() { super(); type = 5; }

    public void serialize() { /* server-only */ }
    public void restore() { bibliothek = stringFromBuffer(); merkmale = intFromBuffer(); }

    public String getLibrary() { return bibliothek; }
    public int getFlags() { return merkmale; }

    public String toString() { return bibliothek; }
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalTypeLibrary)) return false;
        return bibliothek != null ? bibliothek.equals(((PalTypeLibrary) o).bibliothek) : ((PalTypeLibrary) o).bibliothek == null;
    }
    public int hashCode() { return bibliothek != null ? bibliothek.hashCode() : 0; }
}
