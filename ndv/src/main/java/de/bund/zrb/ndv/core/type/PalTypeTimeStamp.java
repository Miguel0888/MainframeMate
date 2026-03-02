package de.bund.zrb.ndv.core.type;

import de.bund.zrb.ndv.core.api.IPalTypeTimeStamp;

public final class PalTypeTimeStamp extends PalType implements IPalTypeTimeStamp {
    private static final long serialVersionUID = 1L;
    private int flags;
    private String timeStamp = "";
    private String userId = "";

    public PalTypeTimeStamp() { super(); type = 54; }
    public PalTypeTimeStamp(int flags, String timeStamp, String userId) {
        this();
        this.flags = flags;
        this.timeStamp = timeStamp != null ? timeStamp : "";
        this.userId = userId != null ? userId : "";
    }

    public void serialize() { intToBuffer(flags); stringToBuffer(timeStamp); stringToBuffer(userId); }
    public void restore() { flags = intFromBuffer(); timeStamp = stringFromBuffer(); userId = stringFromBuffer(); }

    public int getFlags() { return flags; }
    public String getTimeStamp() { return timeStamp; }
    public String getUserId() { return userId; }

    public int hashCode() {
        int r = 17;
        r = 37 * r + flags;
        r = 37 * r + (timeStamp != null ? timeStamp.hashCode() : 0);
        r = 37 * r + (userId != null ? userId.hashCode() : 0);
        return r;
    }
}
