package de.bund.zrb.ndv.core.impl;

import de.bund.zrb.ndv.core.api.IPalIndices;

import java.io.Serializable;

public class PalIndices implements IPalIndices, Serializable {
    private static final long serialVersionUID = 1L;

    private int anzahlDimensionen;
    private int ersteSichtbareDimension;
    private int[] untergrenzen = new int[3];
    private int[] obergrenzen = new int[3];
    private int markierungen;
    private int vorkommnisse;
    private int indexArt;
    private int erweiterungsDimension;

    public PalIndices() {
    }

    PalIndices(IPalIndices a, IPalIndices b) {
        // empty by design
    }

    public PalIndices(int[] lower, int[] upper, int numberDimensions, int indexType) {
        if (lower != null) {
            System.arraycopy(lower, 0, this.untergrenzen, 0, Math.min(lower.length, 3));
        }
        if (upper != null) {
            System.arraycopy(upper, 0, this.obergrenzen, 0, Math.min(upper.length, 3));
        }
        this.anzahlDimensionen = numberDimensions;
        this.indexArt = indexType;
    }

    public boolean replace(IPalIndices other) {
        boolean changed = false;
        int start = erweiterungsDimension;
        for (int i = start; i < 3; i++) {
            if (this.untergrenzen[i] != other.getLower()[i]) {
                this.untergrenzen[i] = other.getLower()[i];
                changed = true;
            }
            if (this.obergrenzen[i] != other.getUpper()[i]) {
                this.obergrenzen[i] = other.getUpper()[i];
                changed = true;
            }
        }
        if (this.markierungen != other.getFlags()) {
            this.markierungen = other.getFlags();
            changed = true;
        }
        return changed;
    }

    public boolean isArray() {
        return indexArt == ARRAY || indexArt == ARRAY_CHUNK;
    }

    public boolean isArrayElement() {
        return indexArt == ARRAY_ELEMENT;
    }

    public boolean isArrayChunk() {
        return indexArt == ARRAY_CHUNK;
    }

    public boolean isMaterialized() {
        return (markierungen & (NOTMATERIALIZED_LOW1 | NOTMATERIALIZED_UPP1
                | NOTMATERIALIZED_LOW2 | NOTMATERIALIZED_UPP2
                | NOTMATERIALIZED_LOW3 | NOTMATERIALIZED_UPP3)) == 0;
    }

    public boolean contains(Object other) {
        if (other == null) {
            return true;
        }
        if (!(other instanceof PalIndices)) {
            return false;
        }
        PalIndices o = (PalIndices) other;
        if (this.anzahlDimensionen != o.anzahlDimensionen) {
            return false;
        }
        for (int i = 0; i < anzahlDimensionen; i++) {
            if (this.untergrenzen[i] > o.untergrenzen[i]) return false;
            if (this.obergrenzen[i] < o.obergrenzen[i]) return false;
        }
        return true;
    }

    public String toString() {
        if (anzahlDimensionen == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = ersteSichtbareDimension; i < anzahlDimensionen; i++) {
            if (i > ersteSichtbareDimension) {
                sb.append(",");
            }
            boolean lowWild = (markierungen & (1 << (i * 2))) != 0;
            boolean upWild = (markierungen & (1 << (i * 2 + 1))) != 0;

            String lo = lowWild ? "*" : String.valueOf(untergrenzen[i]);
            String up = upWild ? "*" : String.valueOf(obergrenzen[i]);

            if (lo.equals(up) && !(isArray() && i == erweiterungsDimension)) {
                sb.append(lo);
            } else {
                sb.append(lo).append(":").append(up);
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public boolean equals(Object other) {
        if (other == this) return true;
        if (other == null) return false;
        if (!(other instanceof PalIndices)) return false;
        PalIndices o = (PalIndices) other;
        if (this.anzahlDimensionen != o.anzahlDimensionen) return false;
        if (this.markierungen != o.markierungen) return false;
        if (this.indexArt != o.indexArt) return false;
        for (int i = 0; i < 3; i++) {
            if (this.untergrenzen[i] != o.untergrenzen[i]) return false;
            if (this.obergrenzen[i] != o.obergrenzen[i]) return false;
        }
        return true;
    }

    public int hashCode() {
        int result = 17;
        result = 37 * result + indexArt;
        result = 37 * result + markierungen;
        result = 37 * result + anzahlDimensionen;
        for (int i = 0; i < 3; i++) {
            result = 37 * result + untergrenzen[i];
        }
        for (int i = 0; i < 3; i++) {
            result = 37 * result + obergrenzen[i];
        }
        return result;
    }

    public int getFlags() {
        return markierungen;
    }

    public void setFlags(int flags) {
        this.markierungen = flags;
    }

    public int[] getLower() {
        return untergrenzen.clone();
    }

    public void setLower(int[] lower) {
        if (lower != null) {
            System.arraycopy(lower, 0, this.untergrenzen, 0, Math.min(lower.length, 3));
        }
    }

    public int[] getUpper() {
        return obergrenzen.clone();
    }

    public void setUpper(int[] upper) {
        if (upper != null) {
            System.arraycopy(upper, 0, this.obergrenzen, 0, Math.min(upper.length, 3));
        }
    }

    public int getNumberDimensions() {
        return anzahlDimensionen;
    }

    public void setNumberDimensions(int numberDimensions) {
        this.anzahlDimensionen = numberDimensions;
    }

    public int getOccurences() {
        return vorkommnisse;
    }

    public void setOccurences(int occurences) {
        this.vorkommnisse = occurences;
    }

    public int getIndexType() {
        return indexArt;
    }

    public void setIndexType(int indexType) {
        this.indexArt = indexType;
    }

    public int getFirstVisbleDimension() {
        return ersteSichtbareDimension;
    }

    public void setFirstVisbleDimension(int firstVisibleDimension) {
        this.ersteSichtbareDimension = firstVisibleDimension;
    }

    public int getExpandDimension() {
        return erweiterungsDimension;
    }

    public void setExpandDimension(int expandDimension) {
        this.erweiterungsDimension = expandDimension;
    }
}
