package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.EAttachSessionType;
import com.softwareag.naturalone.natural.pal.external.IPalTypeEnviron;

public class PalTypeEnviron extends PalType implements IPalTypeEnviron {
    private static final long serialVersionUID = 1L;
    private static final int PAL_VERSION = 47;

    private int ndvClientVersion;
    private int natVersion;
    private int palVersion;
    private String operatingSystem = "";
    private String sessionId = "";
    private int osVersion;
    private String startupCommands = "";
    private int ndvVersion;
    private int terminalModel;
    private int logonCounter;
    private int featureFlags;
    private int webVersion;
    private int ndvTypeField;

    public PalTypeEnviron() { super(); type = 0; }
    public PalTypeEnviron(int logonCounter) {
        this();
        this.logonCounter = logonCounter;
        this.natVersion = 0;
        this.palVersion = PAL_VERSION;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("windows")) { this.operatingSystem = "PC"; this.osVersion = WINDOWS; }
        else if (os.contains("linux") || os.contains("unix") || os.contains("aix") || os.contains("hp-ux") || os.contains("sunos")) {
            this.operatingSystem = "UNIX"; this.osVersion = UNIX;
        } else { this.operatingSystem = "PC"; this.osVersion = WINDOWS; }
    }

    public void serialize() {
        intToBuffer(ndvClientVersion); intToBuffer(natVersion); intToBuffer(palVersion);
        stringToBuffer(operatingSystem); stringToBuffer(sessionId);
        intToBuffer(osVersion); stringToBuffer(startupCommands);
        intToBuffer(ndvVersion); intToBuffer(terminalModel); intToBuffer(logonCounter);
        intToBuffer(featureFlags); intToBuffer(webVersion);
    }

    public void restore() {
        natVersion = intFromBuffer();
        palVersion = intFromBuffer();
        operatingSystem = stringFromBuffer();
        sessionId = stringFromBuffer();
        osVersion = intFromBuffer();
        // derive ndvType from OS
        if (operatingSystem != null) {
            String os = operatingSystem.toUpperCase();
            if (os.contains("MVS") || os.contains("OS/390") || os.contains("Z/OS") || os.contains("MAINFRAME")) ndvTypeField = MAINFRAME;
            else if (os.contains("UNIX") || os.contains("LINUX") || os.contains("AIX") || os.contains("HP-UX") || os.contains("SUNOS")) ndvTypeField = UNIX;
            else if (os.contains("PC") || os.contains("WINDOWS")) ndvTypeField = WINDOWS;
            else if (os.contains("VMS")) ndvTypeField = VMS;
            else ndvTypeField = MAINFRAME;
        }
        // optional fields
        if (recordTail < recordLength) startupCommands = stringFromBuffer();
        if (recordTail < recordLength) ndvVersion = intFromBuffer();
        if (recordTail < recordLength) terminalModel = intFromBuffer();
        if (recordTail < recordLength) logonCounter = intFromBuffer();
        if (recordTail < recordLength) featureFlags = intFromBuffer();
        if (recordTail < recordLength) webVersion = intFromBuffer();
    }

    public String getSessionId() { return sessionId; }
    public int getLogonCounter() { return logonCounter; }
    public String getStartupCommands() { return startupCommands; }
    public int getNatVersion() { return natVersion; }
    public int getNdvVersion() { return ndvVersion; }
    public int getPalVersion() { return palVersion; }
    public int getNdvType() { return ndvTypeField; }
    public int getWebVersion() { return webVersion; }
    public void setWebVersion(int v) { this.webVersion = v; }

    public boolean isMfUnicodeSrcPossible() { return (featureFlags & 8) != 0; }
    public boolean isWebIOServer() { return (featureFlags & 4) != 0; }
    public boolean performsTimeStampChecks() { return (featureFlags & 256) != 0; }
    public void setRichGui(boolean e) { featureFlags = e ? featureFlags | 16 : featureFlags & ~16; }
    public void setNdvClientClientId(int id) { /* stored in flags */ }
    public void setNdvClientClientVersion(int v) { ndvClientVersion = v; }
    public void setWebBrowserIO(boolean e) { featureFlags = e ? featureFlags | 4 : featureFlags & ~4; }
    public void setNfnPrivateMode(boolean e) { featureFlags = e ? featureFlags | 64 : featureFlags & ~64; }
    public void setTimeStampChecks(boolean e) { featureFlags = e ? featureFlags | 256 : featureFlags & ~256; }

    public EAttachSessionType getAttachSessionType() {
        if ((featureFlags & 512) != 0) return EAttachSessionType.RPC;
        if ((featureFlags & 1024) != 0) return EAttachSessionType.NAT;
        if ((featureFlags & 2048) != 0) return EAttachSessionType.NJX;
        return EAttachSessionType.NDV;
    }
}
