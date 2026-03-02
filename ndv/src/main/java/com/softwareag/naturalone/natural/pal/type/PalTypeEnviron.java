package com.softwareag.naturalone.natural.pal.type;

import com.softwareag.naturalone.natural.pal.external.EAttachSessionType;
import com.softwareag.naturalone.natural.pal.external.IPalTypeEnviron;
import com.softwareag.naturalone.natural.pal.external.PalTrace;

import java.io.IOException;

public final class PalTypeEnviron extends PalType implements IPalTypeEnviron {
    private static final long serialVersionUID = 1L;
    private static final int PAL_VERSION = 47;

    private int ndvClientVersion;
    private int natVersion;
    private int palVersion;
    private String opSys = "";
    private String sessionId = "";
    private int opsysVer;
    private String startupCommands = "";
    private int ndvVersion;
    private int tModel;
    private int logonCounter;
    private int flags;
    private int webVersion;
    private int ndvType;

    public PalTypeEnviron() {
    }

    public PalTypeEnviron(int logonCounter) {
        super.type = 0;
        this.natVersion = 8380;
        this.opSys = System.getProperty("os.name");
        String[] verParts = System.getProperty("os.version", "0").split("\\.");
        if (verParts != null) {
            String combined = "";
            for (int i = 0; i < verParts.length; ++i) {
                try {
                    Integer.valueOf(verParts[i]);
                    combined = combined + verParts[i];
                } catch (NumberFormatException e) {
                    try {
                        PalTrace.text("os.version problem:" + verParts[i]);
                    } catch (IOException ignored) {
                    }
                }
            }
            if (!combined.isEmpty()) {
                this.opsysVer = Integer.valueOf(combined);
            }
        }
        this.logonCounter = logonCounter;
    }

    public void serialize() {
        intToBuffer(natVersion);
        intToBuffer(PAL_VERSION);
        stringToBuffer(opSys);
        stringToBuffer(sessionId);
        intToBuffer(opsysVer);
        stringToBuffer(startupCommands);
        intToBuffer(ndvVersion);
        intToBuffer(tModel);
        intToBuffer(logonCounter);
        intToBuffer(flags);
        intToBuffer(webVersion);
        intToBuffer(ndvClientVersion);
    }

    public void restore() {
        natVersion = intFromBuffer();
        palVersion = intFromBuffer();
        opSys = stringFromBuffer();
        ndvType = 1;
        if (opSys.compareTo("OS/390") == 0) {
            ndvType = 1;
        } else if (opSys.compareTo("UNIX") == 0) {
            ndvType = 2;
        } else if (opSys.compareTo("PC") == 0) {
            ndvType = 3;
        } else if (opSys.compareTo("VMS") == 0) {
            ndvType = 4;
        }
        sessionId = stringFromBuffer();
        opsysVer = intFromBuffer();
        if (recordTail < recordLength) startupCommands = stringFromBuffer();
        if (recordTail < recordLength) ndvVersion = intFromBuffer();
        if (recordTail < recordLength) tModel = intFromBuffer();
        if (recordTail < recordLength) logonCounter = intFromBuffer();
        if (recordTail < recordLength) flags = intFromBuffer();
        if (recordTail < recordLength) webVersion = intFromBuffer();
    }

    public String getSessionId() { return sessionId; }
    public int getLogonCounter() { return logonCounter; }
    public String getStartupCommands() { return startupCommands; }
    public int getNatVersion() { return natVersion; }
    public int getNdvVersion() { return ndvVersion; }
    public int getPalVersion() { return palVersion; }
    public int getNdvType() { return ndvType; }
    public int getWebVersion() { return webVersion; }
    public void setWebVersion(int v) { this.webVersion = v; }

    public boolean isMfUnicodeSrcPossible() { return (flags & 8) == 8; }
    public boolean isWebIOServer() { return (flags & 4) == 4; }
    public boolean performsTimeStampChecks() { return (flags & 256) == 256; }

    public void setRichGui(boolean e) {
        if (e) { flags |= 16; }
    }

    public void setNdvClientClientId(int id) {
        flags |= id;
    }

    public void setNdvClientClientVersion(int v) {
        ndvClientVersion = v;
    }

    public void setWebBrowserIO(boolean e) {
        if (e) { flags |= 4; }
    }

    public void setNfnPrivateMode(boolean e) {
        if (e) { flags |= 64; }
    }

    public void setTimeStampChecks(boolean e) {
        if (e) { flags |= 256; }
    }

    public EAttachSessionType getAttachSessionType() {
        EAttachSessionType result = EAttachSessionType.NDV;
        if ((flags & 2048) == 2048) {
            result = EAttachSessionType.NJX;
        } else if ((flags & 512) == 512) {
            result = EAttachSessionType.RPC;
        } else if ((flags & 1024) == 1024) {
            result = EAttachSessionType.NAT;
        }
        return result;
    }
}
