package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeSource;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public abstract class PalTypeSource extends PalType implements IPalTypeSource {
    private static final long serialVersionUID = 1L;
    protected String quellZeile = "";
    protected String zeichensatzName = "";

    PalTypeSource() { super(); }
    public PalTypeSource(String sourceLine) { super(); this.quellZeile = sourceLine != null ? sourceLine : ""; }

    public abstract void convert(String charsetName) throws UnsupportedEncodingException, IOException;

    public void setSourceRecord(String line) { this.quellZeile = line; }
    public String getSourceRecord() { return quellZeile; }

    public void setCharSetName(String name) {
        if (name != null) this.zeichensatzName = name;
    }
}
