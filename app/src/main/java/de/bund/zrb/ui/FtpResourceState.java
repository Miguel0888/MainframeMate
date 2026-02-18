package de.bund.zrb.ui;

import de.bund.zrb.files.auth.ConnectionId;

public final class FtpResourceState {

    private final ConnectionId connectionId;
    private final Boolean mvsMode;
    private final String systemType;
    private final String encoding;

    public FtpResourceState(ConnectionId connectionId, Boolean mvsMode, String systemType, String encoding) {
        this.connectionId = connectionId;
        this.mvsMode = mvsMode;
        this.systemType = systemType;
        this.encoding = encoding;
    }

    public ConnectionId getConnectionId() {
        return connectionId;
    }

    public Boolean getMvsMode() {
        return mvsMode;
    }

    public String getSystemType() {
        return systemType;
    }

    public String getEncoding() {
        return encoding;
    }
}

