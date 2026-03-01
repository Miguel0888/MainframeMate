package com.softwareag.naturalone.natural.pal;

import com.ibm.icu.charset.CharsetICU;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.HashMap;
import java.util.Map;

public class ICUCharsetCoder {
    private static Map<String, CharsetInfo> zeichensatzPuffer;

    private static CharsetInfo getCharsetInfo(String charsetName) {
        if (zeichensatzPuffer == null) {
            zeichensatzPuffer = new HashMap<>();
        }
        CharsetInfo info = zeichensatzPuffer.get(charsetName);
        if (info == null) {
            Charset cs = CharsetICU.forNameICU(charsetName);
            CharsetDecoder decoder = cs.newDecoder();
            CharsetEncoder encoder = cs.newEncoder();
            info = new CharsetInfo(cs, decoder, encoder);
            zeichensatzPuffer.put(charsetName, info);
        }
        return info;
    }

    public static String decode(String charsetName, byte[] input) throws CharacterCodingException {
        CharsetInfo info = getCharsetInfo(charsetName);
        ByteBuffer bb = ByteBuffer.wrap(input);
        CharBuffer cb = info.getDecoder().decode(bb);
        return cb.toString();
    }

    public static byte[] encode(String charsetName, String input, boolean fallback) throws CharacterCodingException {
        CharsetInfo info = getCharsetInfo(charsetName);
        CharBuffer cb = CharBuffer.wrap(input);
        try {
            ByteBuffer bb = info.getEncoder().encode(cb);
            byte[] result = new byte[bb.remaining()];
            bb.get(result);
            return result;
        } catch (UnmappableCharacterException e) {
            if (fallback) {
                try {
                    Charset fallbackCs = Charset.forName(charsetName);
                    CharsetEncoder fallbackEncoder = fallbackCs.newEncoder();
                    fallbackEncoder.onUnmappableCharacter(CodingErrorAction.REPORT);
                    cb.rewind();
                    ByteBuffer bb = fallbackEncoder.encode(cb);
                    byte[] result = new byte[bb.remaining()];
                    bb.get(result);
                    return result;
                } catch (Exception ex) {
                    throw e;
                }
            }
            throw e;
        }
    }

    public static int getUnsupportedCodePoint(String charsetName, String input) {
        for (int i = 1; i < input.length(); i++) {
            try {
                String sub = input.substring(0, i + 1);
                encode(charsetName, sub, false);
            } catch (CharacterCodingException e) {
                return input.codePointAt(i);
            }
        }
        return 0;
    }

    public static ConversionErrorInfo getUnsupportedCodePoint(String charsetName, byte[] input) {
        ConversionErrorInfo info = new ConversionErrorInfo();
        info.setUnmappableCodePoint(input[input.length - 1]);
        info.setOffset(1);

        for (int i = input.length - 1; i >= 0; i--) {
            byte[] copy = new byte[input.length];
            System.arraycopy(input, 0, copy, 0, input.length);
            copy[i] = 0;
            try {
                decode(charsetName, copy);
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
