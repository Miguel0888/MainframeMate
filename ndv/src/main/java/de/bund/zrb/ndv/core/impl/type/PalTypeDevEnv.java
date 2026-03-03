package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeDevEnv;

public final class PalTypeDevEnv extends PalType implements IPalTypeDevEnv {
    private static final long serialVersionUID = 1L;
    private String entwicklungsumgebungPfad = "";
    private String rechnername = "";

    public PalTypeDevEnv() { super(); type = 52; }

    public void serialize() { /* server-only */ }
    public void restore() { entwicklungsumgebungPfad = stringFromBuffer(); rechnername = stringFromBuffer(); }

    public String getDevEnvPath() { return entwicklungsumgebungPfad; }
    public boolean isDevEnv() { return entwicklungsumgebungPfad != null && !entwicklungsumgebungPfad.isEmpty(); }
    public String getHostName() { return rechnername; }
}
