package de.bund.zrb.ui;

import de.bund.zrb.ndv.NdvService;
import de.bund.zrb.ndv.NdvObjectInfo;

/**
 * Carries NDV connection state for a VirtualResource, analogous to FtpResourceState for FTP.
 */
public final class NdvResourceState {

    private final NdvService service;
    private final String library;
    private final NdvObjectInfo objectInfo;

    public NdvResourceState(NdvService service, String library, NdvObjectInfo objectInfo) {
        this.service = service;
        this.library = library;
        this.objectInfo = objectInfo;
    }

    public NdvService getService() {
        return service;
    }

    public String getLibrary() {
        return library;
    }

    public NdvObjectInfo getObjectInfo() {
        return objectInfo;
    }
}

