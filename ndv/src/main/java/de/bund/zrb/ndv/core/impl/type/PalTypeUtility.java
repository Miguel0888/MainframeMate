package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeUtility;

import java.util.Arrays;

public final class PalTypeUtility extends PalType implements IPalTypeUtility {
    private static final long serialVersionUID = 1L;
    private byte[] dienstprogrammDatensatz;

    public PalTypeUtility() { super(); type = 14; }
    public PalTypeUtility(byte[] datensatz) {
        this();
        if (datensatz != null) this.dienstprogrammDatensatz = Arrays.copyOf(datensatz, datensatz.length);
    }

    public void serialize() {
        if (dienstprogrammDatensatz != null) byteArrayToBuffer(dienstprogrammDatensatz);
    }
    public void restore() { dienstprogrammDatensatz = recordToByteArray(); }

    public byte[] getUtilityRecord() {
        return dienstprogrammDatensatz != null ? Arrays.copyOf(dienstprogrammDatensatz, dienstprogrammDatensatz.length) : null;
    }
    public void setUtilityRecord(byte[] datensatz) {
        if (datensatz != null) {
            this.dienstprogrammDatensatz = Arrays.copyOf(datensatz, datensatz.length);
        }
    }
}
