package de.bund.zrb.ndv.core.type;

import de.bund.zrb.ndv.core.api.IPalTypeSource;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public abstract class PalTypeSource extends PalType implements IPalTypeSource {
    private static final long serialVersionUID = 1L;
    protected String sourceLine = "";
    protected String charSetName = "";

    PalTypeSource() { super(); }
    public PalTypeSource(String sourceLine) { super(); this.sourceLine = sourceLine != null ? sourceLine : ""; }

    public abstract void convert(String charsetName) throws UnsupportedEncodingException, IOException;

    public void setSourceRecord(String line) { this.sourceLine = line; }
    public String getSourceRecord() { return sourceLine; }

    public void setCharSetName(String name) {
        if (name != null) this.charSetName = name;
    }
}
