package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeObjDesc;

public final class PalTypeObjDesc extends PalType implements IPalTypeObjDesc {
    private String filterMuster;
    private int objektArt;
    private int objektTyp;

    public PalTypeObjDesc() {
        super.type = 7;
    }

    public PalTypeObjDesc(int objektTyp, int objektArt, String filterMuster) {
        super.type = 7;
        this.objektArt = objektArt;
        this.objektTyp = objektTyp;
        this.filterMuster = filterMuster;
    }

    public void serialize() {
        this.stringToBuffer(this.filterMuster);
        this.intToBuffer(this.objektTyp);
        this.intToBuffer(this.objektArt);
        this.intToBuffer(0);
        this.intToBuffer(0);
        this.intToBuffer(0);
        this.intToBuffer(0);
        this.intToBuffer(0);
    }

    public void restore() {
    }
}

