package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeSysVar;

public class PalTypeSysVar extends PalType implements IPalTypeSysVar {
    private static final long serialVersionUID = 1L;
    private int sprache;
    private int art;

    public PalTypeSysVar() { super(); typSchluessel = 28; }

    public void serialize() {
        byteInPuffer((byte) art);
        if (serverArt == 1) { // Mainframe
            ganzzahlInPuffer(sprache);
        } else {
            byteInPuffer((byte) sprache);
        }
    }
    public void restore() {
        art = byteAusPuffer() & 0xFF;
        if (serverArt == 1) { // Mainframe
            sprache = intFromBuffer();
        } else {
            sprache = byteAusPuffer() & 0xFF;
        }
    }

    public int getLanguage() { return sprache; }
    public int getKind() { return art; }
    public void setLanguage(int sprache) { this.sprache = sprache; }
    public String toString() { return "language (ULANG)= " + sprache; }
}
