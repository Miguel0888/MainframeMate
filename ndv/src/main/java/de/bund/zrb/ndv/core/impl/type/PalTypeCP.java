package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeCP;

public final class PalTypeCP extends PalType implements IPalTypeCP {
    private static final long serialVersionUID = 1L;
    private String zeichensatzSeite = "";

    public PalTypeCP() { super(); typSchluessel = 45; }
    public PalTypeCP(String zeichensatzSeite) { this(); this.zeichensatzSeite = zeichensatzSeite; }

    public void serialize() { textInPuffer(zeichensatzSeite); }
    public void restore() { zeichensatzSeite = stringFromBuffer(); }

    public String getCodePage() { return zeichensatzSeite; }
    public void setCodePage(String seite) { this.zeichensatzSeite = seite; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalTypeCP)) return false;
        return zeichensatzSeite != null ? zeichensatzSeite.equals(((PalTypeCP) o).zeichensatzSeite) : ((PalTypeCP) o).zeichensatzSeite == null;
    }
    public int hashCode() { return zeichensatzSeite != null ? zeichensatzSeite.hashCode() : 0; }
    public String toString() { return zeichensatzSeite; }
}
