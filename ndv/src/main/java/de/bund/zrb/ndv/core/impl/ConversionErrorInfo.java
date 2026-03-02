package de.bund.zrb.ndv.core.impl;

public class ConversionErrorInfo {
    private byte nichtAbbildbaresZeichen;
    private int versatz;

    public byte getUnmappableCodePoint() {
        return nichtAbbildbaresZeichen;
    }

    public void setUnmappableCodePoint(byte unmappableCodePoint) {
        this.nichtAbbildbaresZeichen = unmappableCodePoint;
    }

    public int getOffset() {
        return versatz;
    }

    public void setOffset(int offset) {
        this.versatz = offset;
    }
}
