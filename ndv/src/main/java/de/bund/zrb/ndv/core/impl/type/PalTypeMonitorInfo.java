package de.bund.zrb.ndv.core.impl.type;

/** Stub — Monitor-Feature nicht implementiert. */
public class PalTypeMonitorInfo extends PalType {
    private String sitzungsKennung;
    private String ereignisFilter = "0000000000000FFF";

    public PalTypeMonitorInfo() {}
    public PalTypeMonitorInfo(String sitzungsKennung) { this.sitzungsKennung = sitzungsKennung; }
    public PalTypeMonitorInfo(String sitzungsKennung, String ereignisFilter) { this.sitzungsKennung = sitzungsKennung; this.ereignisFilter = ereignisFilter; }

    public String getSessionId() { return sitzungsKennung; }
    public void setSessionId(String v) { this.sitzungsKennung = v; }
    public String getEventFilter() { return ereignisFilter; }
    public void setEventFilter(String v) { this.ereignisFilter = v; }

    @Override public void serialize() { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public void restore()   { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public int get()        { return 56; }
}
