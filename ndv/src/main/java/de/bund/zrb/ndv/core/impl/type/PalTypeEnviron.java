package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.EAttachSessionType;
import de.bund.zrb.ndv.core.api.IPalTypeEnviron;
import de.bund.zrb.ndv.core.api.PalTrace;

import java.io.IOException;

public final class PalTypeEnviron extends PalType implements IPalTypeEnviron {
    private static final long serialVersionUID = 1L;
    private static final int PAL_VERSION = 47;

    private int ndvKlientVersion;
    private int naturalVersion;
    private int protokollVersion;
    private String betriebssystem = "";
    private String sitzungsKennung = "";
    private int betriebssystemVersion;
    private String startBefehle = "";
    private int ndvVersion;
    private int terminalModell;
    private int anmeldeZaehler;
    private int merkmale;
    private int webSchichtVersion;
    private int serverTyp;

    public PalTypeEnviron() {
    }

    public PalTypeEnviron(int anmeldeZaehler) {
        super.typSchluessel = 0;
        this.naturalVersion = 8380;
        this.betriebssystem = System.getProperty("os.name");
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
                this.betriebssystemVersion = Integer.valueOf(combined);
            }
        }
        this.anmeldeZaehler = anmeldeZaehler;
    }

    public void serialize() {
        ganzzahlInPuffer(naturalVersion);
        ganzzahlInPuffer(PAL_VERSION);
        textInPuffer(betriebssystem);
        textInPuffer(sitzungsKennung);
        ganzzahlInPuffer(betriebssystemVersion);
        textInPuffer(startBefehle);
        ganzzahlInPuffer(ndvVersion);
        ganzzahlInPuffer(terminalModell);
        ganzzahlInPuffer(anmeldeZaehler);
        ganzzahlInPuffer(merkmale);
        ganzzahlInPuffer(webSchichtVersion);
        ganzzahlInPuffer(ndvKlientVersion);
    }

    public void restore() {
        naturalVersion = intFromBuffer();
        protokollVersion = intFromBuffer();
        betriebssystem = stringFromBuffer();
        serverTyp = 1;
        if (betriebssystem.compareTo("OS/390") == 0) {
            serverTyp = 1;
        } else if (betriebssystem.compareTo("UNIX") == 0) {
            serverTyp = 2;
        } else if (betriebssystem.compareTo("PC") == 0) {
            serverTyp = 3;
        } else if (betriebssystem.compareTo("VMS") == 0) {
            serverTyp = 4;
        }
        sitzungsKennung = stringFromBuffer();
        betriebssystemVersion = intFromBuffer();
        if (lesePosition < datensatzLaenge) startBefehle = stringFromBuffer();
        if (lesePosition < datensatzLaenge) ndvVersion = intFromBuffer();
        if (lesePosition < datensatzLaenge) terminalModell = intFromBuffer();
        if (lesePosition < datensatzLaenge) anmeldeZaehler = intFromBuffer();
        if (lesePosition < datensatzLaenge) merkmale = intFromBuffer();
        if (lesePosition < datensatzLaenge) webSchichtVersion = intFromBuffer();
    }

    public String getSessionId() { return sitzungsKennung; }
    public int getLogonCounter() { return anmeldeZaehler; }
    public String getStartupCommands() { return startBefehle; }
    public int getNatVersion() { return naturalVersion; }
    public int getNdvVersion() { return ndvVersion; }
    public int getPalVersion() { return protokollVersion; }
    public int getNdvType() { return serverTyp; }
    public int getWebVersion() { return webSchichtVersion; }
    public void setWebVersion(int v) { this.webSchichtVersion = v; }

    public boolean isMfUnicodeSrcPossible() { return (merkmale & 8) == 8; }
    public boolean isWebIOServer() { return (merkmale & 4) == 4; }
    public boolean performsTimeStampChecks() { return (merkmale & 256) == 256; }

    public void setRichGui(boolean e) {
        if (e) { merkmale |= 16; }
    }

    public void setNdvClientClientId(int id) {
        merkmale |= id;
    }

    public void setNdvClientClientVersion(int v) {
        ndvKlientVersion = v;
    }

    public void setWebBrowserIO(boolean e) {
        if (e) { merkmale |= 4; }
    }

    public void setNfnPrivateMode(boolean e) {
        if (e) { merkmale |= 64; }
    }

    public void setTimeStampChecks(boolean e) {
        if (e) { merkmale |= 256; }
    }

    public EAttachSessionType getAttachSessionType() {
        EAttachSessionType result = EAttachSessionType.NDV;
        if ((merkmale & 2048) == 2048) {
            result = EAttachSessionType.NJX;
        } else if ((merkmale & 512) == 512) {
            result = EAttachSessionType.RPC;
        } else if ((merkmale & 1024) == 1024) {
            result = EAttachSessionType.NAT;
        }
        return result;
    }
}
