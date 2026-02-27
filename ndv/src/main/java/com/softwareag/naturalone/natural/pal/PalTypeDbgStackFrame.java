package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgStackFrame;

/** Stub — Debugger-Feature nicht implementiert. */
public class PalTypeDbgStackFrame extends PalType implements IPalTypeDbgStackFrame {
    @Override public void serialize() { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public void restore()   { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public int get()        { return 34; }

    public void setData(IPalTypeDbgStackFrame v) {}

    @Override public int getDatabaseId() { return 0; }
    @Override public int getDatabaseIdGda() { return 0; }
    @Override public int getDatabaseIdLog() { return 0; }
    @Override public String getEvent() { return null; }
    @Override public int getExecPos() { return 0; }
    @Override public int getLine() { return 0; }
    @Override public int getExecPosLog() { return 0; }
    @Override public int getFileNbr() { return 0; }
    @Override public int getFileNbrGda() { return 0; }
    @Override public int getFileNbrLog() { return 0; }
    @Override public String getGdaLibrary() { return null; }
    @Override public String getGdaObject() { return null; }
    @Override public int getLevel() { return 0; }
    @Override public String getLibrary() { return null; }
    @Override public String getLogLibrary() { return null; }
    @Override public String getLogObject() { return null; }
    @Override public String getObject() { return null; }
    @Override public String getActiveSourceName() { return null; }
    @Override public String getActiveLibraryName() { return null; }
    @Override public int getActiveDatabaseId() { return 0; }
    @Override public int getActiveFileNumber() { return 0; }
    @Override public int getActiveType() { return 0; }
    @Override public int getNatType() { return 0; }
    @Override public void setExecPos(int v) {}
    @Override public void setExecPosLog(int v) {}
    @Override public boolean hasLocals() { return false; }
    @Override public boolean hasGlobals() { return false; }
    @Override public boolean equals1(Object v) { return false; }
    @Override public void setNatType(int v) {}
    @Override public int getLineNbrInc() { return 0; }
    @Override public void setLineNbrInc(int v) {}
}
