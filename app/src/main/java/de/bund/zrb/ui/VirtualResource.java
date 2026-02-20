package de.bund.zrb.ui;

import de.bund.zrb.files.path.VirtualResourceRef;

public final class VirtualResource {

    private final VirtualResourceRef ref;
    private final VirtualResourceKind kind;
    private final String resolvedPath;
    private final boolean local;
    private final VirtualBackendType backendType;
    private final FtpResourceState ftpState;
    private final NdvResourceState ndvState;

    public VirtualResource(VirtualResourceRef ref, VirtualResourceKind kind, String resolvedPath, boolean local) {
        this(ref, kind, resolvedPath, local ? VirtualBackendType.LOCAL : VirtualBackendType.FTP, null);
    }

    public VirtualResource(VirtualResourceRef ref,
                           VirtualResourceKind kind,
                           String resolvedPath,
                           VirtualBackendType backendType,
                           FtpResourceState ftpState) {
        this(ref, kind, resolvedPath, backendType, ftpState, null);
    }

    public VirtualResource(VirtualResourceRef ref,
                           VirtualResourceKind kind,
                           String resolvedPath,
                           VirtualBackendType backendType,
                           FtpResourceState ftpState,
                           NdvResourceState ndvState) {
        this.ref = ref;
        this.kind = kind;
        this.resolvedPath = resolvedPath;
        this.backendType = backendType == null ? VirtualBackendType.LOCAL : backendType;
        this.ftpState = ftpState;
        this.ndvState = ndvState;
        this.local = this.backendType == VirtualBackendType.LOCAL;
    }

    public VirtualResourceRef getRef() {
        return ref;
    }

    public VirtualResourceKind getKind() {
        return kind;
    }

    public String getResolvedPath() {
        return resolvedPath;
    }

    public boolean isLocal() {
        return local;
    }

    public VirtualBackendType getBackendType() {
        return backendType;
    }

    public FtpResourceState getFtpState() {
        return ftpState;
    }

    public NdvResourceState getNdvState() {
        return ndvState;
    }

    /**
     * Create a copy of this resource with a different kind (FILE/DIRECTORY).
     */
    public VirtualResource withKind(VirtualResourceKind newKind) {
        return new VirtualResource(ref, newKind, resolvedPath, backendType, ftpState, ndvState);
    }
}
