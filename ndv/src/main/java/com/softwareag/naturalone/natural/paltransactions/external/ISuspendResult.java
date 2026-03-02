package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.PalTypeDbgNatStack;
import com.softwareag.naturalone.natural.pal.PalTypeDbgStackFrame;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSpy;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgStatus;
import com.softwareag.naturalone.natural.pal.external.IPalTypeNotify;
import com.softwareag.naturalone.natural.pal.external.IPalTypeStream;

/**
 * Schnittstelle für das Ergebnis einer Debug-Unterbrechung (Suspend).
 * Enthält Status, Stapelrahmen, Bildschirminhalt und Fehlerinformationen.
 */
public interface ISuspendResult {

    IPalTypeNotify getNotify();

    void setNotify(IPalTypeNotify notify);

    byte getDecimalCharacter();

    IPalTypeDbgSpy getSpy();

    PalTypeDbgStackFrame[] getStackFrames();

    void setStackFrames(PalTypeDbgStackFrame[] stackFrames);

    IPalTypeDbgStatus getStatus();

    RuntimeException getException();

    void setException(RuntimeException exception);

    IPalTypeStream getScreen();

    void setScreen(IPalTypeStream screen);

    PalTypeDbgNatStack[] getNatStackEntries();

    void setNatStackEntries(PalTypeDbgNatStack[] natStackEntries);
}

