package com.softwareag.naturalone.natural.paltransactions.internal.services;

import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * Debugging, Screen-I/O, DAS, Utility-Buffer, Logon:
 * Alle Methoden die im Moment nicht implementiert werden muessen.
 */
public class DebugService {

    private final PalSessionContext ctx;

    public DebugService(PalSessionContext ctx) {
        this.ctx = ctx;
    }

    // ── Logon ──

    public void setAutomaticLogon(boolean auto) {
        ctx.setAutomaticLogon(auto);
    }

    public void logon(String library) throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.logon not yet implemented");
    }

    public void logon(String library, IPalTypeLibId[] stepLibs) throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.logon not yet implemented");
    }

    public String getLogonLibrary() throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.getLogonLibrary not yet implemented");
    }

    // ── Debug ──

    public ISuspendResult debugStart(String program, String library, String params)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.debugStart not yet implemented");
    }

    public ISuspendResult debugResume() throws IOException {
        throw new UnsupportedOperationException("DebugService.debugResume not yet implemented");
    }

    public ISuspendResult debugStepInto() throws IOException {
        throw new UnsupportedOperationException("DebugService.debugStepInto not yet implemented");
    }

    public ISuspendResult debugStepOver() throws IOException {
        throw new UnsupportedOperationException("DebugService.debugStepOver not yet implemented");
    }

    public ISuspendResult debugStepReturn() throws IOException {
        throw new UnsupportedOperationException("DebugService.debugStepReturn not yet implemented");
    }

    public void debugExit() throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.debugExit not yet implemented");
    }

    public IPalTypeDbgStackFrame[] setNextStatement(IPalTypeDbgStackFrame frame)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.setNextStatement not yet implemented");
    }

    public IPalTypeDbgSyt[] getSymbolTable(IPalTypeDbgVarContainer container)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.getSymbolTable not yet implemented");
    }

    public IPalTypeDbgVarValue[] getValue(IPalTypeDbgVarContainer container, IPalTypeDbgVarDesc desc)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.getValue not yet implemented");
    }

    public void modifyValue(IPalTypeDbgVarContainer container, IPalTypeDbgVarDesc desc,
                            IPalTypeDbgVarValue value)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.modifyValue not yet implemented");
    }

    public IPalTypeDbgVarDesc[] resolveIndices(boolean flag, IPalTypeDbgVarContainer container,
                                                IPalTypeDbgVarDesc desc)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.resolveIndices not yet implemented");
    }

    public IPalTypeDbgSpy spySet(IPalTypeDbgSpy spy) throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.spySet not yet implemented");
    }

    public IPalTypeDbgSpy spySet(IPalTypeDbgSpy spy, IPalTypeDbgVarDesc desc, IPalTypeDbgVarValue value)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.spySet not yet implemented");
    }

    public IPalTypeDbgSpy spyModify(IPalTypeDbgSpy spy) throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.spyModify not yet implemented");
    }

    public IPalTypeDbgSpy spyModify(IPalTypeDbgSpy spy, IPalTypeDbgVarDesc desc, IPalTypeDbgVarValue value)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.spyModify not yet implemented");
    }

    public IPalTypeDbgSpy spyDelete(IPalTypeDbgSpy spy) throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.spyDelete not yet implemented");
    }

    // ── Screen / Command / Execute ──

    public ISuspendResult command(String cmd, int option) throws IOException {
        throw new UnsupportedOperationException("DebugService.command not yet implemented");
    }

    public ISuspendResult sendScreen(byte[] data) throws IOException {
        throw new UnsupportedOperationException("DebugService.sendScreen not yet implemented");
    }

    public ISuspendResult nextScreen() throws IOException {
        throw new UnsupportedOperationException("DebugService.nextScreen not yet implemented");
    }

    public ISuspendResult execute(String command) throws IOException {
        throw new UnsupportedOperationException("DebugService.execute not yet implemented");
    }

    public void terminateIO() throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.terminateIO not yet implemented");
    }

    // ── Utility Buffer ──

    public void utilityBufferSend(short type, byte[] data) throws IOException {
        throw new UnsupportedOperationException("DebugService.utilityBufferSend not yet implemented");
    }

    public byte[] utilityBufferReceive() throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.utilityBufferReceive not yet implemented");
    }

    // ── DAS (Debug Attach Session) ──

    public void dasConnect(Map<String, String> params, int type)
            throws IOException, UnknownHostException, ConnectException, PalResultException {
        throw new UnsupportedOperationException("DebugService.dasConnect not yet implemented");
    }

    public void dasWaitForAttach(String sessionId, IDebugAttachWaitCallBack callback)
            throws PalResultException {
        throw new UnsupportedOperationException("DebugService.dasWaitForAttach not yet implemented");
    }

    public void dasRegisterDebugAttachRecord(IPalTypeDbgaRecord record) throws PalResultException {
        throw new UnsupportedOperationException("DebugService.dasRegisterDebugAttachRecord not yet implemented");
    }

    public void dasUnregisterDebugAttachRecord(IPalTypeDbgaRecord record, int option)
            throws PalResultException {
        throw new UnsupportedOperationException("DebugService.dasUnregisterDebugAttachRecord not yet implemented");
    }

    public void dasBindToAttachSession(String session, String library, String program)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.dasBindToAttachSession not yet implemented");
    }

    public void dasSignIn() throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.dasSignIn not yet implemented");
    }

    public ISuspendResult dasDebugStart() throws IOException, PalResultException {
        throw new UnsupportedOperationException("DebugService.dasDebugStart not yet implemented");
    }
}

