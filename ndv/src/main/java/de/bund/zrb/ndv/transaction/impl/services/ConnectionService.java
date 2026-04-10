package de.bund.zrb.ndv.transaction.impl.services;

import de.bund.zrb.ndv.core.impl.Ndv;
import de.bund.zrb.ndv.core.api.*;
import de.bund.zrb.ndv.core.impl.type.*;
import de.bund.zrb.ndv.transaction.api.*;
import de.bund.zrb.ndv.transaction.impl.NaturalParameter;
import de.bund.zrb.ndv.transaction.impl.Password;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Verbindungs- und Session-Lifecycle:
 * connect, disconnect, reconnect, close.
 */
public class ConnectionService {

    private final NdvSessionContext ctx;

    public ConnectionService(NdvSessionContext ctx) {
        this.ctx = ctx;
    }

    public More connect(Map<String, String> params)
            throws IOException, UnknownHostException, ConnectException, PalConnectResultException {

        More ergebnis = null;
        PalConnectResultException verbindungsFehler = null;

        ctx.setHost(params.get(ConnectKey.HOST));
        ctx.setPort(params.get(ConnectKey.PORT));
        String userId = params.get(ConnectKey.USERID);
        String password = params.get(ConnectKey.PASSWORD);
        String sessionParm = params.get(ConnectKey.PARM);
        String internalParm = params.get("internal session parameters");
        String newPassword = params.get(ConnectKey.NEW_PASSWORD);
        String richGui = params.get("rich gui");
        String webioVersion = params.get("webio version");
        String nfnPrivate = params.get("nfn private mode");
        String logonCounter = params.get("logon counter");
        String monitorSessionId = params.get("monitor session ID");
        String monitorEventFilter = params.get("monitor event filter");

        if (ctx.getPal() != null) {
            throw new IllegalStateException("connection already established");
        }
        if (ctx.getHost() == null) {
            throw new IllegalArgumentException("HOST value must not be null");
        }
        if (ctx.getPort() == null) {
            throw new IllegalArgumentException("PORT value must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("USERID value must not be null");
        }
        if (password == null) password = "";
        if (sessionParm == null) sessionParm = "";
        if (password.length() > 8) {
            throw new IllegalArgumentException("password must not exceed 8 characters");
        }
        if (newPassword == null) newPassword = "";
        if (logonCounter == null) logonCounter = "0";

        boolean nfnPrivateMode = "true".equalsIgnoreCase(nfnPrivate);
        boolean richGuiFlag = "true".equalsIgnoreCase(richGui);

        if (webioVersion == null) {
            if (ctx.getIdentification() != null) {
                webioVersion = Integer.valueOf(ctx.getIdentification().getWebIOVersion()).toString();
            } else {
                webioVersion = "0";
            }
        }

        int ndvType = 0, ndvVersion = 0, natVersion = 0, palVersion = 0;
        int webVersion = 0, logonCount = 0;
        String sessionId = "";
        boolean mfUnicodeSrc = false, webIOServer = false, timeStampChecks = false;
        boolean devEnv = false;
        String codePage = "", devEnvPath = "", hostName = "", logonLibrary = "";
        EAttachSessionType attachType = EAttachSessionType.NDV;

        try {
            PalTrace.header("connect");
            IPalPreferences prefs = ctx.getPalPreferences();
            Ndv ndv = new Ndv(prefs != null ? prefs.getTimeOut() : 0, ctx.getTimeoutHandler());
            ndv.connect(ctx.getHost(), ctx.getPort());
            ctx.setPal(ndv);

            PalTypeOperation op = new PalTypeOperation(18);
            ndv.setUserId(userId);
            op.setUserId(userId);
            ndv.add((IPalType) op);

            String encodedPw = Password.encode(userId, password, "", newPassword);
            if (internalParm != null) {
                sessionParm = String.format("%s %s", internalParm, sessionParm);
            }
            PalTypeConnect connectRec = new PalTypeConnect(userId, encodedPw, sessionParm.trim());
            ndv.add((IPalType) connectRec);

            PalTypeEnviron envRec = new PalTypeEnviron(Integer.valueOf(logonCounter));
            envRec.setRichGui(richGuiFlag);
            envRec.setWebVersion(Integer.valueOf(webioVersion));
            if (prefs != null && prefs.checkTimeStamp()) {
                envRec.setTimeStampChecks(true);
            }
            envRec.setWebBrowserIO(true);
            envRec.setNfnPrivateMode(nfnPrivateMode);
            if (ctx.getIdentification() != null) {
                envRec.setNdvClientClientId(ctx.getIdentification().getNdvClientId());
                envRec.setNdvClientClientVersion(ctx.getIdentification().getNdvClientVersion());
            }
            ndv.add((IPalType) envRec);

            PalTypeCP cpRec = new PalTypeCP(resolveClientCodePage(params));
            ndv.add((IPalType) cpRec);

            if (monitorSessionId != null) {
                PalTypeMonitorInfo monRec = new PalTypeMonitorInfo(monitorSessionId);
                if (monitorEventFilter != null) {
                    monRec.setEventFilter(monitorEventFilter);
                }
                ndv.add((IPalType) monRec);
            }

            ndv.commit();

            int secErrorKind = 0;
            int errorNum = ctx.getError();
            if (errorNum != 0) {
                ctx.setPalProperties(null);
                String shortText = ctx.getErrorText();
                String[] longText = ctx.getErrorTextLong();
                if (ctx.getErrorKind() == 1) {
                    shortText = ctx.removeLeadingLength(shortText);
                }
                switch (errorNum) {
                    case 829: secErrorKind = 3; break;
                    case 838: secErrorKind = 2; break;
                    case 855: secErrorKind = 5; break;
                    case 873: secErrorKind = 1; break;
                    case 876: secErrorKind = 4; break;
                }
                String detail = ctx.getDetailMessage(longText, shortText);
                verbindungsFehler = new PalConnectResultException(errorNum, detail, ctx.getErrorKind(), secErrorKind);
                verbindungsFehler.setLongText(longText);
                verbindungsFehler.setShortText(shortText);
            }

            IPalTypeEnviron[] envResults = (IPalTypeEnviron[]) ndv.retrieve(0);
            IPalTypeStream[] streamResults = (IPalTypeStream[]) ndv.retrieve(13);
            if (streamResults != null) {
                throw new PalConnectResultException(9999, "invalid I/O performed on server", 3, 0);
            }

            if (envResults != null) {
                ndvType = envResults[0].getNdvType();
                ergebnis = new More();
                ergebnis.setCommands(envResults[0].getStartupCommands());
                if (ndvType == 1 && streamResults == null) {
                    ergebnis = null;
                }
                webVersion = envResults[0].getWebVersion();
                natVersion = envResults[0].getNatVersion();
                ndvVersion = envResults[0].getNdvVersion();
                palVersion = envResults[0].getPalVersion();
                sessionId = envResults[0].getSessionId();
                mfUnicodeSrc = envResults[0].isMfUnicodeSrcPossible();
                webIOServer = envResults[0].isWebIOServer();
                logonCount = envResults[0].getLogonCounter();
                timeStampChecks = envResults[0].performsTimeStampChecks();
                attachType = envResults[0].getAttachSessionType();
                ndv.setNdvType(ndvType);
                ndv.setPalVersion(palVersion);
                ndv.setSessionId(sessionId);
            }

            IPalTypeDevEnv[] devEnvResults = (IPalTypeDevEnv[]) ndv.retrieve(52);
            if (devEnvResults != null) {
                devEnv = devEnvResults[0].isDevEnv();
                devEnvPath = devEnvResults[0].getDevEnvPath();
                hostName = devEnvResults[0].getHostName();
            }

            int savedErrorKind = ctx.getErrorKind();
            if (ctx.getErrorKind() == 0 || ctx.getErrorKind() == 1) {
                loadServerConfig(true);
                loadCodePages();
                logonLibrary = extractLogonLib(ndvType,
                        envResults != null ? envResults[0].getStartupCommands() : "");
                if (ctx.getNaturalParameters() != null
                        && ctx.getNaturalParameters().getRegional() != null) {
                    codePage = ctx.getNaturalParameters().getRegional().getCodePage().trim();
                }
            }

            ctx.createPalProperties(ndvType, ndvVersion, natVersion, palVersion, sessionId,
                    mfUnicodeSrc, webIOServer, webVersion, logonCount, codePage,
                    devEnv, devEnvPath, hostName, timeStampChecks, logonLibrary, attachType);

            if (savedErrorKind != 0) {
                if (savedErrorKind != 1) {
                    ctx.setPal(null);
                } else {
                    ctx.setConnected(true);
                }
                throw verbindungsFehler;
            }

        } catch (PalConnectResultException e) {
            throw e;
        } catch (PalResultException e) {
            if (e.isWarning()) {
                ctx.createPalProperties(ndvType, ndvVersion, natVersion, palVersion, sessionId,
                        mfUnicodeSrc, webIOServer, webVersion, logonCount, codePage,
                        devEnv, devEnvPath, hostName, timeStampChecks, logonLibrary, attachType);
                ctx.setConnected(true);
            } else {
                ctx.setPal(null);
            }
            throw new PalConnectResultException(e.getErrorNumber(), e.getMessage(), e.getErrorKind(), 0);
        } catch (IllegalStateException | IllegalArgumentException e) {
            ctx.setPal(null);
            throw e;
        } catch (UnknownHostException e) {
            ctx.setPal(null);
            throw e;
        } catch (ConnectException e) {
            ctx.setPal(null);
            throw e;
        } catch (PalTimeoutException e) {
            ctx.setPal(null);
            throw e;
        } catch (IOException e) {
            ctx.setPal(null);
            throw e;
        }

        ctx.setConnected(true);
        return ergebnis;
    }

    public void disconnect() throws IOException {
        try {
            ctx.setDisconnected(true);
            ctx.setConnected(false);
            if (ctx.getPal() != null) {
                ctx.getPal().disconnect();
            }
        } catch (Exception ignored) {
        }
    }

    public void reconnect() throws IOException, PalResultException {
        if (ctx.isMainframe()) {
            ctx.setDisconnected(false);
            ctx.getPal().connect(ctx.getHost(), ctx.getPort());
            ctx.setConnected(true);
            reinitSession();
        }
    }

    public void close() throws IOException, PalResultException {
        intClose(0);
    }

    public void close(int option) throws IOException, PalResultException {
        intClose(option);
    }

    private void intClose(int option) throws IOException, PalResultException {
        ctx.requirePal();
        PalTrace.header("close");
        if (ctx.isConnected()) {
            PalTypeOperation op = new PalTypeOperation(20, option);
            ctx.getPal().add((IPalType) op);
            ctx.getPal().commit();
            ctx.getPal().closeSocket();
            ctx.setPal(null);
            ctx.setConnected(false);
        }
    }

    private void reinitSession() throws IOException, PalResultException {
        PalTrace.header("reinitSession");
        ctx.getPal().add((IPalType) new PalTypeOperation(60, 0));
        ctx.getPal().commit();
        PalResultException ex = ctx.getResultException();
        if (ex != null) throw ex;
        ctx.getPal().setConnectionLost(false);
    }

    // ── Server-Konfiguration laden (wird auch aus connect aufgerufen) ──

    public void loadServerConfig(boolean initial) throws IOException, PalResultException {
        ctx.requirePal();
        PalTrace.header("getServerConfig");
        PalTypeOperation op = new PalTypeOperation(10, 1);
        op.setFlags(initial ? 1 : 0);
        ctx.getPal().add((IPalType) op);
        ctx.getPal().commit();
        PalResultException ex = ctx.getResultException();
        if (ex != null) throw ex;

        IPalTypeNatParm[] natParms = (IPalTypeNatParm[]) ctx.getPal().retrieve(25);
        ctx.setNaturalParameters(new NaturalParameter(natParms));
        ctx.setClientConfig((PalTypeClientConfig[]) ctx.getPal().retrieve(50));
        ctx.setDbmsInfo((IPalTypeDbmsInfo[]) ctx.getPal().retrieve(49));
        ctx.setSystemVariables((IPalTypeSysVar[]) ctx.getPal().retrieve(28));
    }

    public void loadCodePages() throws IOException, PalResultException {
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
    }

    String extractLogonLib(int ndvType, String startupCommands) {
        String lib = "";
        if (ndvType == 1) {
            lib = startupCommands.trim();
        } else {
            int idx = startupCommands.indexOf("LOGON");
            if (idx != -1) {
                lib = startupCommands.substring(idx + 6);
                if (lib.length() > 0) {
                    lib = lib.substring(0, lib.length() - 1);
                }
            }
        }
        if (lib.length() == 0) {
            lib = "SYSTEM";
        }
        return lib;
    }

    /**
     * Resolve the client codepage to report to the NDV server.
     * Uses the explicit {@link ConnectKey#CLIENT_CP} parameter if present,
     * otherwise falls back to {@code Charset.defaultCharset().name()}.
     * <p>
     * The NDV server requires a Single Byte Character Set (SBCS) name;
     * multi-byte charsets like UTF-8 are rejected with NAT7734.
     */
    private static String resolveClientCodePage(Map<String, String> params) {
        String explicit = params.get(ConnectKey.CLIENT_CP);
        if (explicit != null && !explicit.isEmpty()) {
            return explicit;
        }
        return Charset.defaultCharset().name();
    }
}

