package de.bund.zrb.ui;

import de.bund.zrb.files.path.VirtualResourceRef;

public final class VirtualResource {

    private final VirtualResourceRef ref;
    private final VirtualResourceKind kind;
    private final String resolvedPath;
    private final boolean local;

    public VirtualResource(VirtualResourceRef ref, VirtualResourceKind kind, String resolvedPath, boolean local) {
        this.ref = ref;
        this.kind = kind;
        this.resolvedPath = resolvedPath;
        this.local = local;
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
}

