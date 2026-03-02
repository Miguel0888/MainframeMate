package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.type.PalTypeDbgNatStack;
import com.softwareag.naturalone.natural.pal.type.PalTypeDbgStackFrame;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSpy;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgStatus;
import com.softwareag.naturalone.natural.pal.external.IPalTypeNotify;
import com.softwareag.naturalone.natural.pal.external.IPalTypeStream;

/**
 * Schnittstelle für das Ergebnis einer Debug-Unterbrechung (Suspend).
 */
public interface ISuspendResult {

    IPalTypeNotify getNotify();

    void setNotify(IPalTypeNotify notify);

    byte getDecimalCharacter();

    IPalTypeDbgSpy getSpy();

    PalTypeDbgStackFrame[] getStackFrames();

    PalTypeDbgNatStack[] getNatStackEntries();

    void setStackFrames(PalTypeDbgStackFrame[] stackFrames);

    IPalTypeDbgStatus getStatus();

    PalResultException getException();

    void setException(PalResultException exception);

    IPalTypeStream getScreen();

    void setScreen(IPalTypeStream screen);
}

