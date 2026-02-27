package com.softwareag.naturalone.natural.pal;

/** Stub — Monitor-Feature nicht implementiert. */
public class PalTypeMonitorInfo extends PalType {
    private String sessionId;
    private String eventFilter = "0000000000000FFF";

    public PalTypeMonitorInfo() {}
    public PalTypeMonitorInfo(String sessionId) { this.sessionId = sessionId; }
    public PalTypeMonitorInfo(String sessionId, String eventFilter) { this.sessionId = sessionId; this.eventFilter = eventFilter; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String v) { this.sessionId = v; }
    public String getEventFilter() { return eventFilter; }
    public void setEventFilter(String v) { this.eventFilter = v; }

    @Override public void serialize() { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public void restore()   { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public int get()        { return 56; }
}
