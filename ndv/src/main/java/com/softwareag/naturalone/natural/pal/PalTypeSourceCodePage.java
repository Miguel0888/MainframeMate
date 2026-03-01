package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSourceCodePage;

public class PalTypeSourceCodePage extends PalTypeSource implements IPalTypeSourceCodePage {
    private static final long serialVersionUID = 1L;

    public PalTypeSourceCodePage() { super(); type = 12; }
    public PalTypeSourceCodePage(String sourceLine) { super(sourceLine); type = 12; }

    public void serialize() { stringToBuffer(sourceLine); }
    public void restore() { sourceLine = stringFromBuffer(); }

    public void convert(String charsetName) { /* no conversion needed */ }

    public String toString() { return sourceLine; }
}
