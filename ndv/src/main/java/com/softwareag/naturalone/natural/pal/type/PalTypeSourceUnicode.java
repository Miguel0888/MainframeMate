package com.softwareag.naturalone.natural.pal.type;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSourceUnicode;

import java.util.Base64;

public class PalTypeSourceUnicode extends PalTypeSource implements IPalTypeSourceUnicode {
    private static final long serialVersionUID = 1L;

    public PalTypeSourceUnicode() { super(); type = 42; }
    public PalTypeSourceUnicode(String sourceLine) { super(sourceLine); type = 42; }

    public void serialize() {
        try {
            byte[] utf8 = (sourceLine + " ").getBytes("UTF-8");
            if (palVersion >= 39 && ndvType == 1) { // Mainframe
                byteArrayToBuffer(PalTypeStream.Palbtos(utf8));
            } else {
                String b64 = Base64.getMimeEncoder().encodeToString(utf8);
                byteArrayToBuffer(b64.getBytes());
            }
        } catch (Exception e) { /* defensive */ }
    }

    public void restore() {
        try {
            if (palVersion >= 39 && ndvType == 1) { // Mainframe
                byte[] raw = recordToByteArray();
                byte[] utf8 = PalTypeStream.Palstob(raw);
                sourceLine = new String(utf8, "UTF-8");
            } else {
                byte[] decoded = Base64.getMimeDecoder().decode(new String(recordToCharArray()));
                sourceLine = new String(decoded, "UTF-8");
            }
        } catch (Exception e) { /* defensive */ }
    }

    public void convert(String charsetName) { /* no conversion needed */ }

}
