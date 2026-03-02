package com.softwareag.naturalone.natural.pal.type;

import com.softwareag.naturalone.natural.pal.ConversionErrorInfo;

import java.util.Base64;
import com.softwareag.naturalone.natural.pal.util.ICUCharsetCoder;
import com.softwareag.naturalone.natural.pal.PalUnmappableCodePointException;
import com.softwareag.naturalone.natural.pal.external.IPalTypeSourceCP;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class PalTypeSourceCP extends PalTypeSource implements IPalTypeSourceCP {
    private static final long serialVersionUID = 1L;
    private byte[] ebcdicRecord;

    public PalTypeSourceCP() { super(); type = 48; }
    public PalTypeSourceCP(String sourceRecord, String codePage) {
        this();
        this.sourceLine = sourceRecord;
        this.charSetName = codePage;
    }

    public void serialize() {
        if (sourceLine == null || charSetName == null) return;
        try {
            // check for unmappable code points
            int cp = ICUCharsetCoder.getUnsupportedCodePoint(charSetName, sourceLine);
            if (cp != 0) {
                throw new PalUnmappableCodePointException("Unmappable code point: " + cp, this, 0);
            }
            if (super.palVersion <= 35) {
                char[] b64 = utf16ToCharsetToBase64(sourceLine + " ", charSetName, true);
                if (b64 != null) byteArrayToBuffer(new String(b64).getBytes());
            } else {
                byte[] raw = ICUCharsetCoder.encode(charSetName, sourceLine + " ", false);
                byteArrayToBuffer(PalTypeStream.Palbtos(raw));
            }
        } catch (PalUnmappableCodePointException e) {
            throw e;
        } catch (Exception e) {
            // defensive
        }
    }

    public void restore() {
        if (super.palVersion <= 35) {
            ebcdicRecord = Base64.getMimeDecoder().decode(new String(this.recordToCharArray()));
        } else {
            ebcdicRecord = this.recordToByteArray();
            ebcdicRecord = PalTypeStream.Palstob(ebcdicRecord);
        }
    }

    public void convert(String charsetName) throws UnsupportedEncodingException, IOException {
        if (ebcdicRecord == null) return;
        try {
            StringBuffer sb = getUtf16ICU(ebcdicRecord, charsetName);
            if (sb != null) {
                sourceLine = sb.toString();
            }
        } catch (Exception e) {
            ConversionErrorInfo err = ICUCharsetCoder.getUnsupportedCodePoint(charsetName, ebcdicRecord);
            if (err != null) {
                throw new PalUnmappableCodePointException(err.toString(), this, err.getOffset());
            }
            throw new IOException(e);
        }
    }
}
