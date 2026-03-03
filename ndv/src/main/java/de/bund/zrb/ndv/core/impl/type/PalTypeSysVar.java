package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeSysVar;

public class PalTypeSysVar extends PalType implements IPalTypeSysVar {
    private static final long serialVersionUID = 1L;
    private int sprache;
    private int art;

    public PalTypeSysVar() { super(); type = 28; }

    public void serialize() {
        byteToBuffer((byte) art);
        if (ndvType == 1) { // Mainframe
            intToBuffer(sprache);
        } else {
            byteToBuffer((byte) sprache);
        }
    }
    public void restore() {
        art = byteFromBuffer() & 0xFF;
        if (ndvType == 1) { // Mainframe
            sprache = intFromBuffer();
        } else {
            sprache = byteFromBuffer() & 0xFF;
        }
    }

    public int getLanguage() { return sprache; }
    public int getKind() { return art; }
    public void setLanguage(int sprache) { this.sprache = sprache; }
    public String toString() { return "language (ULANG)= " + sprache; }
}
