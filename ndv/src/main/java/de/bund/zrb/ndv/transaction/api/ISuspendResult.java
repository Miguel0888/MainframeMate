package de.bund.zrb.ndv.transaction.api;

import de.bund.zrb.ndv.core.impl.type.PalTypeDbgNatStack;
import de.bund.zrb.ndv.core.impl.type.PalTypeDbgStackFrame;
import de.bund.zrb.ndv.core.api.IPalTypeDbgSpy;
import de.bund.zrb.ndv.core.api.IPalTypeDbgStatus;
import de.bund.zrb.ndv.core.api.IPalTypeNotify;
import de.bund.zrb.ndv.core.api.IPalTypeStream;

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

