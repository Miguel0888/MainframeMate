package com.softwareag.naturalone.natural.paltransactions.internal;

import com.softwareag.naturalone.natural.pal.PalTypeDbgNatStack;
import com.softwareag.naturalone.natural.pal.PalTypeDbgStackFrame;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSpy;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgStatus;
import com.softwareag.naturalone.natural.pal.external.IPalTypeNotify;
import com.softwareag.naturalone.natural.pal.external.IPalTypeStream;
import com.softwareag.naturalone.natural.paltransactions.external.ISuspendResult;

public final class SuspendResult implements ISuspendResult {
    private IPalTypeDbgStatus debugZustand;
    private PalTypeDbgStackFrame[] aufrufStapelRahmen;
    private IPalTypeDbgSpy ueberwachung;
    private IPalTypeStream bildschirmInhalt;
    private IPalTypeNotify benachrichtigung;
    private byte dezimalZeichen;
    private RuntimeException fehler;
    private PalTypeDbgNatStack[] naturalStapelEintraege;

    public SuspendResult(IPalTypeDbgStatus status, PalTypeDbgStackFrame[] stackFrames,
                         IPalTypeDbgSpy spy, IPalTypeStream screen,
                         IPalTypeNotify notify, byte decimalCharacter,
                         RuntimeException exception, PalTypeDbgNatStack[] natStackEntries) {
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

    public RuntimeException getException() {
        return fehler;
    }

    public void setException(RuntimeException exception) {
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
