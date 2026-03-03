package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeNotify;

public final class PalTypeNotify extends PalType implements IPalTypeNotify {
    private static final long serialVersionUID = 1L;
    private int benachrichtigung;
    private int erweiterung;

    public PalTypeNotify() { super(); typSchluessel = 19; }
    public PalTypeNotify(int benachrichtigung) { this(); this.benachrichtigung = benachrichtigung; }

    public void serialize() { ganzzahlInPuffer(benachrichtigung); ganzzahlInPuffer(erweiterung); }
    public void restore() { benachrichtigung = intFromBuffer(); erweiterung = intFromBuffer(); }

    public int getNotification() { return benachrichtigung; }
}
