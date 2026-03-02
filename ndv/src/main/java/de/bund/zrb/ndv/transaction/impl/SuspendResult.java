package de.bund.zrb.ndv.transaction.impl;

import de.bund.zrb.ndv.core.impl.type.PalTypeDbgNatStack;
import de.bund.zrb.ndv.core.impl.type.PalTypeDbgStackFrame;
import de.bund.zrb.ndv.core.api.IPalTypeDbgSpy;
import de.bund.zrb.ndv.core.api.IPalTypeDbgStatus;
import de.bund.zrb.ndv.core.api.IPalTypeNotify;
import de.bund.zrb.ndv.core.api.IPalTypeStream;
import de.bund.zrb.ndv.transaction.api.ISuspendResult;
import de.bund.zrb.ndv.transaction.api.PalResultException;

public final class SuspendResult implements ISuspendResult {
    private IPalTypeDbgStatus debugZustand;
    private PalTypeDbgStackFrame[] aufrufStapelRahmen;
    private IPalTypeDbgSpy ueberwachung;
    private IPalTypeStream bildschirmInhalt;
    private IPalTypeNotify benachrichtigung;
    private byte dezimalZeichen;
    private PalResultException fehler;
    private PalTypeDbgNatStack[] naturalStapelEintraege;

    public SuspendResult(IPalTypeDbgStatus status, PalTypeDbgStackFrame[] stackFrames,
                         IPalTypeDbgSpy spy, IPalTypeStream screen,
                         IPalTypeNotify notify, byte decimalCharacter,
                         PalResultException exception, PalTypeDbgNatStack[] natStackEntries) {
        this.debugZustand = status;
        this.aufrufStapelRahmen = stackFrames != null ? stackFrames.clone() : null;
        this.ueberwachung = spy;
        this.bildschirmInhalt = screen;
        this.benachrichtigung = notify;
        this.dezimalZeichen = decimalCharacter;
        this.fehler = exception;
        this.naturalStapelEintraege = natStackEntries != null ? natStackEntries.clone() : null;
    }

    public IPalTypeNotify getNotify() {
        return benachrichtigung;
    }

    public void setNotify(IPalTypeNotify notify) {
        this.benachrichtigung = notify;
    }

    public byte getDecimalCharacter() {
        return dezimalZeichen;
    }

    public IPalTypeDbgSpy getSpy() {
        return ueberwachung;
    }

    public PalTypeDbgStackFrame[] getStackFrames() {
        return aufrufStapelRahmen != null ? aufrufStapelRahmen.clone() : null;
    }

    public void setStackFrames(PalTypeDbgStackFrame[] stackFrames) {
        if (stackFrames != null) {
            this.aufrufStapelRahmen = stackFrames.clone();
        }
    }

    public IPalTypeDbgStatus getStatus() {
        return debugZustand;
    }

    public PalResultException getException() {
        return fehler;
    }

    public void setException(PalResultException exception) {
        this.fehler = exception;
    }

    public IPalTypeStream getScreen() {
        return bildschirmInhalt;
    }

    public void setScreen(IPalTypeStream screen) {
        this.bildschirmInhalt = screen;
    }

    public PalTypeDbgNatStack[] getNatStackEntries() {
        return naturalStapelEintraege != null ? naturalStapelEintraege.clone() : null;
    }

    public void setNatStackEntries(PalTypeDbgNatStack[] natStackEntries) {
        if (natStackEntries != null) {
            this.naturalStapelEintraege = natStackEntries.clone();
        }
    }
}
