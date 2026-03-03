package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeSourceUnicode;

import java.util.Base64;

public class PalTypeSourceUnicode extends PalTypeSource implements IPalTypeSourceUnicode {
    private static final long serialVersionUID = 1L;

    public PalTypeSourceUnicode() { super(); typSchluessel = 42; }
    public PalTypeSourceUnicode(String sourceLine) { super(sourceLine); typSchluessel = 42; }

    public void serialize() {
        try {
            byte[] utf8 = (quellZeile + " ").getBytes("UTF-8");
            if (uebertragungsVersion >= 39 && serverArt == 1) { // Mainframe
                byteArrayInPuffer(PalTypeStream.Palbtos(utf8));
            } else {
                String b64 = Base64.getMimeEncoder().encodeToString(utf8);
                byteArrayInPuffer(b64.getBytes());
            }
        } catch (Exception e) { /* defensive */ }
    }

    public void restore() {
        try {
            if (uebertragungsVersion >= 39 && serverArt == 1) { // Mainframe
                byte[] raw = datensatzAlsByteArray();
                byte[] utf8 = PalTypeStream.Palstob(raw);
                quellZeile = new String(utf8, "UTF-8");
            } else {
                byte[] decoded = Base64.getMimeDecoder().decode(new String(datensatzAlsZeichenArray()));
                quellZeile = new String(decoded, "UTF-8");
            }
        } catch (Exception e) { /* defensive */ }
    }

    public void convert(String charsetName) { /* no conversion needed */ }

}
