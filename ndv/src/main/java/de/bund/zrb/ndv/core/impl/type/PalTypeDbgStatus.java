package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeDbgStatus;

/** Stub — Debugger-Feature nicht implementiert. */
public final class PalTypeDbgStatus extends PalType implements IPalTypeDbgStatus {
    private int merkmale;

    @Override public void serialize() { ganzzahlInPuffer(merkmale); }
    @Override public void restore()   { merkmale = intFromBuffer(); }
    @Override public int get()        { return 35; }

    @Override public boolean isCtxModified() { return (merkmale & 4) != 0; }
    @Override public boolean isAivModified() { return (merkmale & 2) != 0; }
    @Override public boolean isGdaModified() { return (merkmale & 1) != 0; }
    @Override public boolean isTerminated() { return (merkmale & 8) != 0; }
}
