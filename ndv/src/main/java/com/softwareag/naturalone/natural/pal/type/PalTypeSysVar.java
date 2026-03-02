package com.softwareag.naturalone.natural.pal.type;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSysVar;

public class PalTypeSysVar extends PalType implements IPalTypeSysVar {
    private static final long serialVersionUID = 1L;
    private int language;
    private int kind;

    public PalTypeSysVar() { super(); type = 28; }

    public void serialize() {
        byteToBuffer((byte) kind);
        if (ndvType == 1) { // Mainframe
            intToBuffer(language);
        } else {
            byteToBuffer((byte) language);
        }
    }
    public void restore() {
        kind = byteFromBuffer() & 0xFF;
        if (ndvType == 1) { // Mainframe
            language = intFromBuffer();
        } else {
            language = byteFromBuffer() & 0xFF;
        }
    }

    public int getLanguage() { return language; }
    public int getKind() { return kind; }
    public void setLanguage(int language) { this.language = language; }
    public String toString() { return "language (ULANG)= " + language; }
}
