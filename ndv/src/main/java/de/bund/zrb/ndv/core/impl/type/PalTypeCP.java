package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeCP;

public final class PalTypeCP extends PalType implements IPalTypeCP {
    private static final long serialVersionUID = 1L;
    private String codePage = "";

    public PalTypeCP() { super(); type = 45; }
    public PalTypeCP(String codePage) { this(); this.codePage = codePage; }

    public void serialize() { stringToBuffer(codePage); }
    public void restore() { codePage = stringFromBuffer(); }

    public String getCodePage() { return codePage; }
    public void setCodePage(String codePage) { this.codePage = codePage; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalTypeCP)) return false;
        return codePage != null ? codePage.equals(((PalTypeCP) o).codePage) : ((PalTypeCP) o).codePage == null;
    }
    public int hashCode() { return codePage != null ? codePage.hashCode() : 0; }
    public String toString() { return codePage; }
}
