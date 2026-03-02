package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeObjDesc2;

public final class PalTypeObjDesc2 extends PalType implements IPalTypeObjDesc2 {
    private String filter;
    private int objectKind;
    private int objectType;

    public PalTypeObjDesc2() {
        super.type = 29;
    }

    public PalTypeObjDesc2(int objectType, int objectKind, String filter) {
        super.type = 29;
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

