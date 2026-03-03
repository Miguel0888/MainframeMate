package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeSourceCodePage;

public class PalTypeSourceCodePage extends PalTypeSource implements IPalTypeSourceCodePage {
    private static final long serialVersionUID = 1L;

    public PalTypeSourceCodePage() { super(); typSchluessel = 12; }
    public PalTypeSourceCodePage(String sourceLine) { super(sourceLine); typSchluessel = 12; }

    public void serialize() { textInPuffer(quellZeile); }
    public void restore() { quellZeile = stringFromBuffer(); }

    public void convert(String charsetName) { /* no conversion needed */ }

    public String toString() { return quellZeile; }
}
