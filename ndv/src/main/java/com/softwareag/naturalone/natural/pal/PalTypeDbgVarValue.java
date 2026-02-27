package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgVarValue;

/** Stub — Debugger-Feature nicht implementiert. */
public class PalTypeDbgVarValue extends PalType implements IPalTypeDbgVarValue {
    public PalTypeDbgVarValue() {}
    public PalTypeDbgVarValue(String value) {}

    @Override public void serialize() { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public void restore()   { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public int get()        { return 39; }

    @Override public boolean isUnicode() { return false; }
    @Override public void setCurrentLength(int v) {}
    @Override public int getFlags() { return 0; }
    @Override public void setFlags(int v) {}
    @Override public int getId() { return 0; }
    @Override public void setId(int v) {}
    @Override public int getReturnCode() { return 0; }
    @Override public void setReturnCode(int v) {}
    @Override public String getValue() { return null; }
    @Override public void setValue(String v) {}
    @Override public void markAsUnicode() {}
    @Override public void setValueLength(int v) {}
    @Override public int getCurrentLength() { return 0; }
}
