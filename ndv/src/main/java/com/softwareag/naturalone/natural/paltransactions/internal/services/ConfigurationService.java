package com.softwareag.naturalone.natural.paltransactions.internal.services;

import com.softwareag.naturalone.natural.pal.*;
import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.pal.type.IPalType;
import com.softwareag.naturalone.natural.pal.type.PalTypeOperation;
import com.softwareag.naturalone.natural.paltransactions.external.*;

import java.io.IOException;

/**
 * Konfiguration und Abfrage von Server-Eigenschaften:
 * CodePages, ServerConfiguration, NatParm, SystemVariables, StepLibs, DDM-Generierung, CmdGuard.
 */
public class ConfigurationService {

    private final PalSessionContext ctx;

    public ConfigurationService(PalSessionContext ctx) {
        this.ctx = ctx;
    }

    public IPalTypeCP[] getCodePages() throws IOException, PalResultException {
        if (ctx.getCodePages() == null) {
            ctx.requirePal();
            PalTrace.header("getCodePages");
            PalTypeOperation op = new PalTypeOperation(10, 6);
            ctx.getPal().add((IPalType) op);
            ctx.getPal().commit();
            PalResultException ex = ctx.getResultException();
            if (ex != null) throw ex;
            ctx.setCodePages((IPalTypeCP[]) ctx.getPal().retrieve(45));
        }
        return ctx.getCodePages();
    }

    public IPalTypeSysVar[] getSystemVariables() throws IOException, PalResultException {
        if (ctx.getSystemVariables() == null) {
            ctx.requirePal();
            PalTrace.header("getSystemVariables");
            PalTypeOperation op = new PalTypeOperation(10, 5);
            ctx.getPal().add((IPalType) op);
            ctx.getPal().commit();
            PalResultException ex = ctx.getResultException();
            if (ex != null) throw ex;
            ctx.setSystemVariables((IPalTypeSysVar[]) ctx.getPal().retrieve(28));
        }
        return ctx.getSystemVariables();
    }

    public IPalProperties getPalProperties() {
        return ctx.getPalProperties();
    }

    public INatParm getNaturalParameters() {
        return ctx.getNaturalParameters();
    }

    public String getSessionId() {
        Pal pal = ctx.getPal();
        return pal != null ? pal.getSessionId() : null;
    }

    // ── Noch nicht implementierte Methoden (Stubs) ──

    public void setCodePageOfSource(IPalTypeSystemFile sysFile, String library,
                                    String name, int type, String codePage)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ConfigurationService.setCodePageOfSource not yet implemented");
    }

    public IServerConfiguration getServerConfiguration(boolean refresh)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ConfigurationService.getServerConfiguration not yet implemented");
    }

    public void setNaturalParameters(INatParm parm) {
        // Stub
    }

    public void setSystemVariables(IPalTypeSysVar[] vars) {
        // Stub
    }

    public IPalTypeLibId[] getStepLibs() throws IOException, PalResultException {
        throw new UnsupportedOperationException("ConfigurationService.getStepLibs not yet implemented");
    }

    public void setStepLibs(IPalTypeLibId[] libs, EStepLibFormat format)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ConfigurationService.setStepLibs not yet implemented");
    }

    public String[] generateAdabasDdm(int dbid, int fnr, String ddmName)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ConfigurationService.generateAdabasDdm not yet implemented");
    }

    public String[] generateSqlDdm(int dbid, int fnr, String ddmName, String tableName)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ConfigurationService.generateSqlDdm not yet implemented");
    }

    public String[] generateXmlDdm(int dbid, int fnr, String ddmName,
                                    String schemaUri, String rootElement, String prefix)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ConfigurationService.generateXmlDdm not yet implemented");
    }

    public IPalTypeCmdGuard getCmdGuardInfo(int option, IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ConfigurationService.getCmdGuardInfo not yet implemented");
    }

    public Object createTransactionContext(Class contextClass) {
        if (this.ctx.getTransactionContext() != null) {
            throw new IllegalStateException(
                    "The transaction context cannot be created since there is still another transaction context in use");
        }
        if (ITransactionContextDownload.class.equals(contextClass)) {
            DownloadService.ContextDownload dlCtx = new DownloadService.ContextDownload();
            this.ctx.setTransactionContext(dlCtx);
            return dlCtx;
        }
        throw new IllegalStateException(
                "The transaction context " + contextClass.getCanonicalName() + " is illegal");
    }

    public void disposeTransactionContext(ITransactionContext txCtx) {
        if (txCtx != this.ctx.getTransactionContext()) {
            throw new IllegalStateException("The transaction context is not active");
        }
        if (txCtx instanceof ITransactionContextDownload) {
            DownloadService.ContextDownload dlCtx = (DownloadService.ContextDownload) txCtx;
            if (dlCtx.isStarted() && !dlCtx.isTerminated()) {
                try {
                    // Offene Datei-Operation am Server abbrechen
                    new DownloadService(this.ctx)
                            .dateiOperationAbbrechen(dlCtx.getInitOptions());
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("connection broken", e);
                } catch (PalResultException e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
            }
        }
        this.ctx.setTransactionContext(null);
    }
}

