package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalIndices;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSyt;

/** Stub — Debugger-Feature nicht implementiert. */
public class PalTypeDbgSyt extends PalType implements IPalTypeDbgSyt {
    @Override public void serialize() { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public void restore()   { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public int get()        { return 37; }

    @Override public int getConvId() { return 0; }
    @Override public int getFlags() { return 0; }
    @Override public int getFormat() { return 0; }
    @Override public int getId() { return 0; }
    @Override public int getLength() { return 0; }
    @Override public int getLevel() { return 0; }
    @Override public int getLineReference() { return 0; }
    @Override public String getName() { return null; }
    @Override public int getNumberOfElements() { return 0; }
    @Override public int getOcxFormat() { return 0; }
    @Override public int getPrecision() { return 0; }
    @Override public boolean isUnicode() { return false; }
    @Override public boolean isGroup() { return false; }
    @Override public boolean isNoSymbolic() { return false; }
    @Override public boolean isVarray() { return false; }
    @Override public boolean isDynamic() { return false; }
    @Override public boolean isXarray() { return false; }
    @Override public boolean isReadOnly() { return false; }
    @Override public boolean isLineRef() { return false; }
    @Override public boolean isRedef() { return false; }
    @Override public boolean isRedefBase() { return false; }
    @Override public IPalIndices getIndices() { return null; }
    @Override public void setFlags(int v) {}
    @Override public void setLevel(int v) {}
    @Override public void setName(String v) {}
    @Override public boolean equalsVariable(Object v) { return false; }
    @Override public boolean equalsLineReference(Object v) { return false; }
    @Override public boolean equalsLabel(Object v) { return false; }
    @Override public String getOutpFormatLength() { return null; }
    @Override public void setIndices(IPalIndices v) {}
    @Override public void setLineReference(int v) {}
}
