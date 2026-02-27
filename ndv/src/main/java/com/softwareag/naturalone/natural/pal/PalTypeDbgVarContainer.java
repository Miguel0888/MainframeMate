package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgVarContainer;

/** Stub — Debugger-Feature nicht implementiert. */
public class PalTypeDbgVarContainer extends PalType implements IPalTypeDbgVarContainer {
    public PalTypeDbgVarContainer() {}
    public PalTypeDbgVarContainer(int containerType) {}

    @Override public void serialize() { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public void restore()   { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public int get()        { return 36; }

    @Override public void setDatabaseId(int v) {}
    @Override public void setFileNumber(int v) {}
    @Override public void setFlags(int v) {}
    @Override public int getContainerType() { return 0; }
    @Override public int getDatabaseId() { return 0; }
    @Override public int getFileNumber() { return 0; }
    @Override public String getLibrary() { return null; }
    @Override public boolean isAiv() { return false; }
    @Override public boolean isContext() { return false; }
    @Override public boolean isLocal() { return false; }
    @Override public boolean isGlobal() { return false; }
    @Override public boolean isSystem() { return false; }
    @Override public boolean isHex() { return false; }
    @Override public void setLibrary(String v) {}
    @Override public void setNatType(int v) {}
    @Override public void setObject(String v) {}
    @Override public void setStackLevel(int v) {}
    @Override public void setLocal() {}
    @Override public void setGlobal() {}
    @Override public void setContext() {}
    @Override public void setAiv() {}
    @Override public void setSystem() {}
    @Override public void setHex() {}
    @Override public int getStackLevel() { return 0; }
}
