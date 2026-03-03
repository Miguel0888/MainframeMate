package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeObjDesc2;

public final class PalTypeObjDesc2 extends PalType implements IPalTypeObjDesc2 {
    private String filterMuster;
    private int objektArt;
    private int objektTyp;

    public PalTypeObjDesc2() {
        super.type = 29;
    }

    public PalTypeObjDesc2(int objektTyp, int objektArt, String filterMuster) {
        super.type = 29;
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

