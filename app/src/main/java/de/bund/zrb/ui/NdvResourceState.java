package de.bund.zrb.ui;

import de.bund.zrb.ndv.NdvClient;
import de.bund.zrb.ndv.NdvObjectInfo;

/**
 * Carries NDV connection state for a VirtualResource, analogous to FtpResourceState for FTP.
 */
public final class NdvResourceState {

    private final NdvClient client;
    private final String library;
    private final NdvObjectInfo objectInfo;

    public NdvResourceState(NdvClient client, String library, NdvObjectInfo objectInfo) {
        this.client = client;
        this.library = library;
        this.objectInfo = objectInfo;
    }

    public NdvClient getClient() {
        return client;
    }

    public String getLibrary() {
        return library;
    }

    public NdvObjectInfo getObjectInfo() {
        return objectInfo;
    }
}

