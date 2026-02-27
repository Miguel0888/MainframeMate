package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalIndices;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSyt;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgVarDesc;

/** Stub — Debugger-Feature nicht implementiert. */
public class PalTypeDbgVarDesc extends PalType implements IPalTypeDbgVarDesc {
    public PalTypeDbgVarDesc() {}
    public PalTypeDbgVarDesc(IPalTypeDbgSyt syt, IPalIndices indices) {}

    @Override public void serialize() { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public void restore()   { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public int get()        { return 38; }

    @Override public IPalTypeDbgVarDesc getInstance(IPalTypeDbgSyt v) { return null; }
    @Override public IPalIndices getIndices() { return null; }
    @Override public IPalIndices[] getAllIndices() { return null; }
    @Override public void setConvid(int v) {}
    @Override public void setFlags(int v) {}
    @Override public void setLength(int v) {}
    @Override public void setOcxFormat(int v) {}
    @Override public void setRange(int v) {}
    @Override public void setRedef(boolean v) {}
    @Override public void setStartOffset(int v) {}
    @Override public void setId(int v) {}
    @Override public void setQualifier(String v) {}
    @Override public void setVariable(String v) {}
    @Override public void setFormat(int v) {}
    @Override public void setIndices(IPalIndices v) {}
}
