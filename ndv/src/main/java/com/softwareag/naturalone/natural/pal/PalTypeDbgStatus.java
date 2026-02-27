package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgStatus;

/** Stub — Debugger-Feature nicht implementiert. */
public final class PalTypeDbgStatus extends PalType implements IPalTypeDbgStatus {
    private int flags;

    @Override public void serialize() { intToBuffer(flags); }
    @Override public void restore()   { flags = intFromBuffer(); }
    @Override public int get()        { return 35; }

    @Override public boolean isCtxModified() { return (flags & 4) != 0; }
    @Override public boolean isAivModified() { return (flags & 2) != 0; }
    @Override public boolean isGdaModified() { return (flags & 1) != 0; }
    @Override public boolean isTerminated() { return (flags & 8) != 0; }
}
