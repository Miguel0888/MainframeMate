package de.bund.zrb.ndv.core.type;

import de.bund.zrb.ndv.core.api.IPalTypeObjDesc;

public final class PalTypeObjDesc extends PalType implements IPalTypeObjDesc {
    private String filter;
    private int objectKind;
    private int objectType;

    public PalTypeObjDesc() {
        super.type = 7;
    }

    public PalTypeObjDesc(int objectType, int objectKind, String filter) {
        super.type = 7;
        this.objectKind = objectKind;
        this.objectType = objectType;
        this.filter = filter;
    }

    public void serialize() {
        this.stringToBuffer(this.filter);
        this.intToBuffer(this.objectType);
        this.intToBuffer(this.objectKind);
        this.intToBuffer(0);
        this.intToBuffer(0);
        this.intToBuffer(0);
        this.intToBuffer(0);
        this.intToBuffer(0);
    }

    public void restore() {
    }
}

