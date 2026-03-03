package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeObjDesc;

public final class PalTypeObjDesc extends PalType implements IPalTypeObjDesc {
    private String filterMuster;
    private int objektArt;
    private int objektTyp;

    public PalTypeObjDesc() {
        super.typSchluessel = 7;
    }

    public PalTypeObjDesc(int objektTyp, int objektArt, String filterMuster) {
        super.typSchluessel = 7;
        this.objektArt = objektArt;
        this.objektTyp = objektTyp;
        this.filterMuster = filterMuster;
    }

    public void serialize() {
        this.textInPuffer(this.filterMuster);
        this.ganzzahlInPuffer(this.objektTyp);
        this.ganzzahlInPuffer(this.objektArt);
        this.ganzzahlInPuffer(0);
        this.ganzzahlInPuffer(0);
        this.ganzzahlInPuffer(0);
        this.ganzzahlInPuffer(0);
        this.ganzzahlInPuffer(0);
    }

    public void restore() {
    }
}

