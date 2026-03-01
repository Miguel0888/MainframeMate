package com.softwareag.naturalone.natural.paltransactions.internal;

import com.softwareag.naturalone.natural.pal.external.EAttachSessionType;
import com.softwareag.naturalone.natural.paltransactions.external.IPalProperties;

import java.io.Serializable;

public class PalProperties implements IPalProperties, Serializable {
    private static final long serialVersionUID = 1L;

    private int serverPlattformTyp;
    private int serverVersion;
    private int naturalVersion;
    private int protokollVersion;
    private int webSchichtVersion;
    private String sitzungsKennung;
    private boolean grossrechnerUnicodeQuelleMoeglich;
    private boolean webSchichtServer;
    private int anmeldeZaehler;
    private String standardZeichensatz;
    private boolean entwicklungsumgebungVorhanden;
    private String entwicklungsumgebungPfad;
    private String rechnername;
    private boolean zeitstempelPruefung;
    private String anmeldeBibliothek = "";
    private EAttachSessionType sitzungsAnbindungsTyp;

    public PalProperties() {
    }

    public PalProperties(int ndvType, int ndvVersion, int natVersion, int palVersion,
                         String ndvSessionId, boolean mfUnicodeSrcPossible, boolean webIOServer,
                         int webioVersion, int logonCounter, String defaultCodePage,
                         boolean devEnv, String devEnvPath, String hostName,
                         boolean timeStampCheck, String logonLibrary,
                         EAttachSessionType attachSessionType) {
        this.serverPlattformTyp = ndvType;
        this.serverVersion = ndvVersion;
        this.naturalVersion = natVersion;
        this.protokollVersion = palVersion;
        this.sitzungsKennung = ndvSessionId;
        this.grossrechnerUnicodeQuelleMoeglich = mfUnicodeSrcPossible;
        this.webSchichtServer = webIOServer;
        this.webSchichtVersion = webioVersion;
        this.anmeldeZaehler = logonCounter;
        this.standardZeichensatz = defaultCodePage;
        this.entwicklungsumgebungVorhanden = devEnv;
        this.entwicklungsumgebungPfad = devEnvPath;
        this.rechnername = hostName;
        this.zeitstempelPruefung = timeStampCheck;
        this.anmeldeBibliothek = logonLibrary;
        this.sitzungsAnbindungsTyp = attachSessionType;
    }

    public String getDefaultCodePage() {
        return standardZeichensatz;
    }

    public final int getNatVersion() {
        return naturalVersion;
    }

    public final int getNdvType() {
        return serverPlattformTyp;
    }

    public final String getNdvTypeString() {
        switch (serverPlattformTyp) {
            case 1: return "Mainframe";
            case 2: return "UNIX";
            case 3: return "Windows";
            case 4: return "OpenVMS";
            default: return "";
        }
    }

    public final int getNdvVersion() {
        return serverVersion;
    }

    public final int getPalVersion() {
        return protokollVersion;
    }

    public final String getNdvSessionId() {
        return sitzungsKennung;
    }

    public final boolean isMfUnicodeSrcPossible() {
        return grossrechnerUnicodeQuelleMoeglich;
    }

    public final boolean isWebIOServer() {
        return webSchichtServer;
    }

    public final int getWebioVersion() {
        return webSchichtVersion;
    }

    public final int getLogonCounter() {
        return anmeldeZaehler;
    }

    public final boolean isDevEnv() {
        return entwicklungsumgebungVorhanden;
    }

    public final String getDevEnvPath() {
        return entwicklungsumgebungPfad;
    }

    public final String getHostName() {
        return rechnername;
    }

    public final boolean timeStampCheck() {
        return zeitstempelPruefung;
    }

    public String getLogonLibrary() {
        return anmeldeBibliothek;
    }

    public EAttachSessionType getAttachSessionType() {
        return sitzungsAnbindungsTyp;
    }

    // IPalProperties methods
    public String getServerCodePage() {
        return standardZeichensatz;
    }

    public boolean isOpenSystemsServer() {
        return serverPlattformTyp != 1;
    }

    public boolean isMainframeServer() {
        return serverPlattformTyp == 1;
    }

    public int getMaxLibraryNameLength() {
        return isMainframeServer() ? 8 : 128;
    }

    public int getMaxObjectNameLength() {
        return isMainframeServer() ? 8 : 128;
    }

    public boolean isLongObjectNames() {
        return !isMainframeServer();
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PalProperties)) return false;
        PalProperties o = (PalProperties) other;
        return this.serverPlattformTyp == o.serverPlattformTyp
                && this.serverVersion == o.serverVersion
                && this.naturalVersion == o.naturalVersion
                && this.protokollVersion == o.protokollVersion
                && (this.sitzungsKennung == null ? o.sitzungsKennung == null : this.sitzungsKennung.equals(o.sitzungsKennung));
    }

    public int hashCode() {
        int result = 17;
        result = 37 * result + serverPlattformTyp;
        result = 37 * result + serverVersion;
        result = 37 * result + naturalVersion;
        result = 37 * result + protokollVersion;
        result = 37 * result + (sitzungsKennung != null ? sitzungsKennung.hashCode() : 0);
        return result;
    }

    public String toString() {
        return "NdvType=" + serverPlattformTyp
                + ", NdvVersion=" + serverVersion
                + ", NatVersion=" + naturalVersion
                + ", PalVersion=" + protokollVersion
                + ", NdvSessionId=" + sitzungsKennung
                + ", Session typSchluessel=" + sitzungsAnbindungsTyp;
    }
}
