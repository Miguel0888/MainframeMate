package com.softwareag.naturalone.natural.pal.type;

import com.softwareag.naturalone.natural.pal.ConversionErrorInfo;
import com.softwareag.naturalone.natural.pal.ConversionResult;

import java.util.Base64;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.CharBuffer;
import java.nio.ByteBuffer;
import com.softwareag.naturalone.natural.pal.external.IPalTypeSourceCP;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class PalTypeSourceCP extends PalTypeSource implements IPalTypeSourceCP {
    private static final long serialVersionUID = 1L;
    private byte[] ebcdicRecord;
    private ConversionResult lastConversionResult = ConversionResult.ok();

    public PalTypeSourceCP() { super(); type = 48; }
    public PalTypeSourceCP(String sourceRecord, String codePage) {
        this();
        this.sourceLine = sourceRecord;
        this.charSetName = codePage;
    }

    /** Liefert das Ergebnis der letzten serialize()- oder convert()-Operation. */
    public ConversionResult getLastConversionResult() {
        return lastConversionResult;
    }

    public void serialize() {
        lastConversionResult = ConversionResult.ok();
        if (sourceLine == null || charSetName == null) return;
        try {
            int cp = findUnsupportedCodePoint(charSetName, sourceLine);
            if (cp != 0) {
                lastConversionResult = ConversionResult.error(
                        "Unmappable code point: " + cp, this, 0);
                return;
            }
            if (super.palVersion <= 35) {
                char[] b64 = utf16ToCharsetToBase64(sourceLine + " ", charSetName, true);
                if (b64 != null) byteArrayToBuffer(new String(b64).getBytes());
            } else {
                byte[] raw = encodeWithCharset(charSetName, sourceLine + " ");
                byteArrayToBuffer(PalTypeStream.Palbtos(raw));
            }
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
        lastConversionResult = ConversionResult.ok();
        if (ebcdicRecord == null) return;
        try {
            StringBuffer sb = getUtf16ICU(ebcdicRecord, charsetName);
            if (sb != null) {
                sourceLine = sb.toString();
            }
        } catch (Exception e) {
            ConversionErrorInfo err = findUnsupportedByteCodePoint(charsetName, ebcdicRecord);
            if (err != null) {
                lastConversionResult = ConversionResult.error(
                        err.toString(), this, err.getOffset());
                return;
            }
            throw new IOException(e);
        }
    }

    /** Encode a string using the given charset. Throws CharacterCodingException on unmappable chars. */
    private static byte[] encodeWithCharset(String charsetName, String input) throws CharacterCodingException {
        CharsetEncoder encoder = Charset.forName(charsetName).newEncoder()
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .onMalformedInput(CodingErrorAction.REPORT);
        ByteBuffer bb = encoder.encode(CharBuffer.wrap(input));
        byte[] result = new byte[bb.remaining()];
        bb.get(result);
        return result;
    }

    /** Find the first code point in input that cannot be encoded in the given charset. Returns 0 if all ok. */
    private static int findUnsupportedCodePoint(String charsetName, String input) {
        CharsetEncoder encoder = Charset.forName(charsetName).newEncoder()
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .onMalformedInput(CodingErrorAction.REPORT);
        for (int i = 0; i < input.length(); i++) {
            encoder.reset();
            try {
                encoder.encode(CharBuffer.wrap(input, i, i + 1));
            } catch (CharacterCodingException e) {
                return input.codePointAt(i);
            }
        }
        return 0;
    }

    /** Find the byte position of an unmappable byte in a byte array for a given charset. */
    private static ConversionErrorInfo findUnsupportedByteCodePoint(String charsetName, byte[] input) {
        ConversionErrorInfo info = new ConversionErrorInfo();
        info.setUnmappableCodePoint(input[input.length - 1]);
        info.setOffset(1);

        Charset cs = Charset.forName(charsetName);
        for (int i = input.length - 1; i >= 0; i--) {
            byte[] copy = new byte[input.length];
            System.arraycopy(input, 0, copy, 0, input.length);
            copy[i] = 0;
            try {
                cs.newDecoder()
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(copy));
                info.setOffset(i + 1);
                if (i + 1 < input.length) {
                    info.setUnmappableCodePoint(input[i + 1]);
                }
                break;
            } catch (CharacterCodingException e) {
                // continue
            }
        }
        return info;
    }
}
