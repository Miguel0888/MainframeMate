package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeGeneric;

public final class PalTypeGeneric extends PalType implements IPalTypeGeneric {
    private static final long serialVersionUID = 1L;
    private int datenTyp;
    private Object daten;

    public PalTypeGeneric() { super(); typSchluessel = 20; }
    public PalTypeGeneric(int datenTyp, Object daten) { this(); this.datenTyp = datenTyp; this.daten = daten; }

    public void serialize() {
        ganzzahlInPuffer(datenTyp);
        if (daten != null) {
            if (datenTyp == TYPE_STRING) { textInPuffer(daten.toString()); }
            else { ganzzahlInPuffer(((Number) daten).intValue()); }
        }
    }
    public void restore() {
        datenTyp = intFromBuffer();
        if (datenTyp == TYPE_STRING) { daten = stringFromBuffer(); }
        else { daten = intFromBuffer(); }
    }

    public int getData() { return ((Number) daten).intValue(); }
    public Object getDataObject() { return daten; }
    public void setData(int datenTyp, int wert) { this.datenTyp = datenTyp; this.daten = wert; }
    public void setDataObject(int datenTyp, Object daten) { this.datenTyp = datenTyp; this.daten = daten; }
}
