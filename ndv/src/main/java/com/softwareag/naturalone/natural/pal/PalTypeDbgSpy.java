package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSpy;

/** Stub — Debugger-Feature nicht implementiert. */
public class PalTypeDbgSpy extends PalType implements IPalTypeDbgSpy {
    @Override public void serialize() { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public void restore()   { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public int get()        { return 40; }

    @Override public int getBefEx() { return 0; }
    @Override public void setBefEx(int v) {}
    @Override public int getConvId() { return 0; }
    @Override public void setConvId(int v) {}
    @Override public int getCount() { return 0; }
    @Override public void setCount(int v) {}
    @Override public int getDatabaseId() { return 0; }
    @Override public void setDatabaseId(int v) {}
    @Override public int getFileNbr() { return 0; }
    @Override public void setFileNbr(int v) {}
    @Override public int getFlags() { return 0; }
    @Override public void setFlags(int v) {}
    @Override public void markAsCopyCode() {}
    @Override public void setActive(boolean v) {}
    @Override public boolean isActive() { return false; }
    @Override public int getId() { return 0; }
    @Override public void setId(int v) {}
    @Override public String getLibrary() { return null; }
    @Override public void setLibrary(String v) {}
    @Override public int getLine() { return 0; }
    @Override public void setLine(int v) {}
    @Override public int getNewLine() { return 0; }
    @Override public void setNewLine(int v) {}
    @Override public int getNumEx() { return 0; }
    @Override public void setNumEx(int v) {}
    @Override public String getObject() { return null; }
    @Override public void setObject(String v) {}
    @Override public int getOperator() { return 0; }
    @Override public void setOperator(int v) {}
    @Override public byte getStatus() { return 0; }
    @Override public void setStatus(byte v) {}
}
