package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeDevEnv;

public final class PalTypeDevEnv extends PalType implements IPalTypeDevEnv {
    private static final long serialVersionUID = 1L;
    private String devEnvPath = "";
    private String hostName = "";

    public PalTypeDevEnv() { super(); type = 52; }

    public void serialize() { /* server-only */ }
    public void restore() { devEnvPath = stringFromBuffer(); hostName = stringFromBuffer(); }

    public String getDevEnvPath() { return devEnvPath; }
    public boolean isDevEnv() { return devEnvPath != null && !devEnvPath.isEmpty(); }
    public String getHostName() { return hostName; }
}
