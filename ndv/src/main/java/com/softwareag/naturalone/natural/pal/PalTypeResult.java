package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeResult;

public final class PalTypeResult extends PalType implements IPalTypeResult {
    private static final long serialVersionUID = 1L;
    private int naturalResult;
    private int systemResult;

    public PalTypeResult() { super(); type = 10; }

    public void serialize() { /* server-only */ }
    public void restore() {
        naturalResult = intFromBuffer();
        systemResult = intFromBuffer();
        intFromBuffer(); // discarded
    }

    public int getNaturalResult() { return naturalResult; }
    public int getSystemResult() { return systemResult; }
}
