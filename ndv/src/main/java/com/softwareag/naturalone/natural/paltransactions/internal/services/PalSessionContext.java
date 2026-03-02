package com.softwareag.naturalone.natural.paltransactions.internal.services;

import com.softwareag.naturalone.natural.pal.*;
import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;
import com.softwareag.naturalone.natural.paltransactions.internal.NatParm;
import com.softwareag.naturalone.natural.paltransactions.internal.PalProperties;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gemeinsamer Sitzungskontext fuer alle PalTransactions-Services.
 * Enthaelt die Pal-Verbindung, gecachte Serverdaten und
 * zentrale Hilfsroutinen (Fehlerauswertung, Properties-Erzeugung).
 */
public class PalSessionContext {

    // ── Netzwerk-Primitive ──
    private Pal pal;

    // ── Identifikation / Preferences ──
    private IPalClientIdentification identification;
    private IPalSQLIdentification palSQLIdentification;
    private IPalPreferences palPreferences;
    private IPalTimeoutHandler timeoutHandler;
    private IPalExecutionContext executionContext;
    private IPalArabicShaping shapingContext;

    // ── Gecachte Serverdaten ──
    private PalProperties palProperties;
    private IServerConfiguration serverConfiguration;
    private PalTypeClientConfig[] clientConfig;
    private IPalTypeSysVar[] systemVariables;
    private IPalTypeDbmsInfo[] dbmsInfo;
    private IPalTypeCP[] codePages;
    private INatParm naturalParameters;
    private ITransactionContext transactionContext;

    // ── Verbindungsflags ──
    private boolean isConnected = false;
    private boolean isDisconnected = false;
    private boolean isAutomaticLogon = true;
    private boolean isNotifyActive = false;
    private int currentNotify = 0;
    private int errorKind = 0;
    private int retrievalKind = 0;
    private boolean isDuplicatePossible = false;
    private Set<String> serverList = new TreeSet<>();

    // ── Verbindungsparameter ──
    private String host = "";
    private String port = "";

    // ══════════════════════════════════════════════════════════════
    //  Zugriff auf Pal
    // ══════════════════════════════════════════════════════════════

    public Pal getPal() { return pal; }
    public void setPal(Pal pal) { this.pal = pal; }

    public void requirePal() {
        if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Identifikation / Preferences
    // ══════════════════════════════════════════════════════════════

    public IPalClientIdentification getIdentification() { return identification; }
    public void setIdentification(IPalClientIdentification id) { this.identification = id; }

    public IPalSQLIdentification getPalSQLIdentification() { return palSQLIdentification; }
    public void setPalSQLIdentification(IPalSQLIdentification sql) { this.palSQLIdentification = sql; }

    public IPalPreferences getPalPreferences() { return palPreferences; }
    public void setPalPreferences(IPalPreferences prefs) { this.palPreferences = prefs; }

    public IPalTimeoutHandler getTimeoutHandler() { return timeoutHandler; }
    public void setTimeoutHandler(IPalTimeoutHandler handler) { this.timeoutHandler = handler; }

    public IPalExecutionContext getExecutionContext() { return executionContext; }
    public void setExecutionContext(IPalExecutionContext ctx) { this.executionContext = ctx; }

    public IPalArabicShaping getShapingContext() { return shapingContext; }
    public void setShapingContext(IPalArabicShaping shaping) { this.shapingContext = shaping; }

    // ══════════════════════════════════════════════════════════════
    //  Gecachte Serverdaten
    // ══════════════════════════════════════════════════════════════

    public PalProperties getPalProperties() { return palProperties; }
    public void setPalProperties(PalProperties p) { this.palProperties = p; }

    public IServerConfiguration getServerConfiguration() { return serverConfiguration; }
    public void setServerConfiguration(IServerConfiguration cfg) { this.serverConfiguration = cfg; }

    public PalTypeClientConfig[] getClientConfig() { return clientConfig; }
    public void setClientConfig(PalTypeClientConfig[] cfg) { this.clientConfig = cfg; }

    public IPalTypeSysVar[] getSystemVariables() { return systemVariables; }
    public void setSystemVariables(IPalTypeSysVar[] vars) { this.systemVariables = vars; }

    public IPalTypeDbmsInfo[] getDbmsInfo() { return dbmsInfo; }
    public void setDbmsInfo(IPalTypeDbmsInfo[] info) { this.dbmsInfo = info; }

    public IPalTypeCP[] getCodePages() { return codePages; }
    public void setCodePages(IPalTypeCP[] cp) { this.codePages = cp; }

    public INatParm getNaturalParameters() { return naturalParameters; }
    public void setNaturalParameters(INatParm parm) { this.naturalParameters = parm; }

    public ITransactionContext getTransactionContext() { return transactionContext; }
    public void setTransactionContext(ITransactionContext ctx) { this.transactionContext = ctx; }

    // ══════════════════════════════════════════════════════════════
    //  Verbindungsflags
    // ══════════════════════════════════════════════════════════════

    public boolean isConnected() { return isConnected && pal != null && !pal.isConnectionLost(); }
    public void setConnected(boolean v) { this.isConnected = v; }

    public boolean isDisconnected() { return isDisconnected; }
    public void setDisconnected(boolean v) { this.isDisconnected = v; }

    public boolean isAutomaticLogon() { return isAutomaticLogon; }
    public void setAutomaticLogon(boolean v) { this.isAutomaticLogon = v; }

    public int getErrorKind() { return errorKind; }
    public void setErrorKind(int kind) { this.errorKind = kind; }

    public int getRetrievalKind() { return retrievalKind; }
    public void setRetrievalKind(int kind) { this.retrievalKind = kind; }

    public String getHost() { return host; }
    public void setHost(String h) { this.host = h; }

    public String getPort() { return port; }
    public void setPort(String p) { this.port = p; }

    // ══════════════════════════════════════════════════════════════
    //  Hilfsmethode: Mainframe-Erkennung
    // ══════════════════════════════════════════════════════════════

    public boolean isMainframe() {
        return palProperties != null && palProperties.getNdvType() == 1;
    }

    public boolean isOpenSystemsServer() {
        return palProperties != null && palProperties.getNdvType() != 1;
    }

    // ══════════════════════════════════════════════════════════════
    //  Zentrale Fehlerauswertung
    // ══════════════════════════════════════════════════════════════

    /**
     * Fehlernummer aus PalTypeResult / PalTypeResultEx auslesen.
     * Exakt nachgebaut aus Original-Bytecode (Zeile 3894-3930).
     */
    public int getError() throws IOException {
        int result = 0;
        this.errorKind = 0;
        IPalTypeResult[] res = (IPalTypeResult[]) pal.retrieve(10);
        if (res != null) {
            result = res[0].getNaturalResult();
        }
        if (result == 0) {
            IPalTypeResultEx[] resEx = (IPalTypeResultEx[]) pal.retrieve(11);
            if (resEx != null) {
                String shortText = resEx[0].getShortText();
                if (shortText.length() > 0) {
                    this.errorKind = 3;
                    result = 9999;
                }
            }
            if (res != null) {
                int sysResult = res[0].getSystemResult();
                if (sysResult != 0) {
                    result = sysResult;
                }
            }
        } else if (result == 7000) {
            this.errorKind = 1;
        } else {
            this.errorKind = 2;
        }
        return result;
    }

    /**
     * Kurztext der letzten Fehlermeldung.
     */
    public String getErrorText() throws IOException {
        String text = null;
        IPalTypeResultEx[] resEx = (IPalTypeResultEx[]) pal.retrieve(11);
        if (resEx != null) {
            text = resEx[0].getShortText();
        }
        return text;
    }

    /**
     * Langtext der letzten Fehlermeldung (zeilenweise).
     */
    public String[] getErrorTextLong() throws IOException {
        String[] lines = null;
        PalTypeSourceCodePage[] src = (PalTypeSourceCodePage[]) pal.retrieve(12);
        if (src != null) {
            lines = new String[src.length];
            for (int i = 0; i < src.length; i++) {
                lines[i] = src[i].getSourceRecord();
            }
        }
        return lines;
    }

    /**
     * Fuehrende Laengenangabe aus dem Kurztext entfernen (bei Warnungen).
     */
    public String removeLeadingLength(String text) {
        if (text == null) return null;
        Pattern p = Pattern.compile("^[ \\s]*[0-9]*[ \\s]*");
        Matcher m = p.matcher(text);
        return m.replaceAll("");
    }

    /**
     * Langtext zu einer Detailmeldung zusammensetzen.
     */
    public String getDetailMessage(String[] longText, String shortText) {
        String detail = shortText;
        if (longText != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < longText.length; i++) {
                sb.append(longText[i]);
                if (i == longText.length - 1) {
                    sb.append(".");
                } else {
                    sb.append("\\n");
                }
            }
            detail = sb.toString();
        }
        return detail;
    }

    /**
     * PalResultException erzeugen, wenn ein Fehler vorliegt.
     */
    public PalResultException getResultException() throws IOException {
        PalResultException ex = null;
        int errNum = this.getError();
        if (errNum != 0) {
            IPalTypeResultEx[] resEx = (IPalTypeResultEx[]) pal.retrieve(11);
            if (resEx != null) {
                String shortText = resEx[0].getShortText();
                if (shortText.length() == 0) {
                    shortText = resEx[0].getSystemText();
                }
                String[] longText = this.getErrorTextLong();
                if (shortText.length() == 0) {
                    shortText = "Nat" + errNum + ": "
                            + (longText != null && longText.length > 0 ? longText[0] : "");
                }
                this.getDetailMessage(longText, shortText);
                ex = new PalResultException(errNum, 2, shortText);
                ex.setLongText(longText);
                ex.setShortText(shortText);
            }
        }
        return ex;
    }

    // ══════════════════════════════════════════════════════════════
    //  PalProperties-Erzeugung
    // ══════════════════════════════════════════════════════════════

    public void createPalProperties(int ndvType, int ndvVersion, int natVersion, int palVersion,
                                    String sessionId, boolean mfUnicodeSrc, boolean webIOServer,
                                    int webVersion, int logonCounter, String codePage,
                                    boolean devEnv, String devEnvPath, String hostName,
                                    boolean timeStampChecks, String logonLibrary,
                                    EAttachSessionType attachType) {
        this.palProperties = new PalProperties(ndvType, ndvVersion, natVersion, palVersion,
                sessionId, mfUnicodeSrc, webIOServer, webVersion, logonCounter, codePage,
                devEnv, devEnvPath, hostName, timeStampChecks, logonLibrary, attachType);
    }

    // ══════════════════════════════════════════════════════════════
    //  Hilfsmethode: Bibliotheks-Aufloesung
    // ══════════════════════════════════════════════════════════════

    public String getLibrary(IPalTypeSystemFile sysFile, String library) {
        if (sysFile.getKind() == 6) {
            return this.isOpenSystemsServer() ? "SYSTEM" : "";
        }
        return library;
    }

    // ══════════════════════════════════════════════════════════════
    //  Logon (von UploadService und DownloadService benoetigt)
    // ══════════════════════════════════════════════════════════════

    /**
     * Auto-Logon in eine Bibliothek (nur wenn isAutomaticLogon aktiv).
     */
    public void logon(String library) throws IOException, PalResultException {
        requirePal();
        if (library == null || library.isEmpty()) return;

        IPalTypeLibId[] stepLibs = new IPalTypeLibId[1];
        stepLibs[0] = PalTypeLibIdFactory.newInstance();

        PalTrace.header("logon");
        pal.add((IPalType) new PalTypeOperation(2, 12));
        pal.add((IPalType) new PalTypeStack("LOGON " + library));
        pal.add((IPalType[]) stepLibs);
        pal.commit();
        PalResultException ex = getResultException();
        if (ex != null) throw ex;
    }

    /**
     * Internes Label-Praefix (fuer renumber/label-Umwandlung).
     */
    public String getInternalLabelPrefix() {
        // Im Original: wird aus ServerConfiguration gelesen, falls vorhanden
        return null;
    }
}

