package com.softwareag.naturalone.natural.pal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Replace proprietary Base64Coder with Java 8 Base64 while keeping legacy flags and signatures.
 */
public final class Base64Coder {

    private static final byte BASE64_F_INSERTLINEBREAK = 1;
    public static final byte BASE64_F_IGNOREINVCHAR = 2;
    public static final byte BASE64_F_NOPADDING = 4;

    public static final byte BASE64_F_RFC2045 = 3; // insert line breaks + ignore invalid chars
    public static final byte BASE64_F_RFC3548 = 0; // basic base64
    public static final byte BASE64_F_NATRPC = 4;  // no padding

    private Base64Coder() {
        // Prevent instantiation
    }

    static char[] encode(int[] binary, byte flags) {
        if (binary == null) {
            throw new IllegalArgumentException("binary must not be null");
        }
        if (binary.length == 0) {
            throw new IllegalArgumentException("binary must not be empty");
        }

        byte[] bytes = toByteArray(binary);

        Base64.Encoder encoder = createEncoder(flags);
        String encoded = encoder.encodeToString(bytes);
        return encoded.toCharArray();
    }

    public static byte[] decode(char[] base64, byte flags) {
        if (base64 == null) {
            throw new IllegalArgumentException("base64 must not be null");
        }
        if (base64.length == 0) {
            throw new IllegalArgumentException("base64 must not be empty");
        }

        String input = new String(base64);

        Base64.Decoder decoder = createDecoder(flags);

        try {
            return decoder.decode(input);
        } catch (IllegalArgumentException firstTry) {
            // Accept unpadded input if decoder is strict
            String padded = padToMultipleOf4(input);
            return decoder.decode(padded);
        }
    }

    private static Base64.Encoder createEncoder(byte flags) {
        Base64.Encoder encoder;

        if ((flags & BASE64_F_INSERTLINEBREAK) == BASE64_F_INSERTLINEBREAK) {
            // Use CRLF and 76 chars per line (RFC 2045)
            encoder = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.ISO_8859_1));
        } else {
            encoder = Base64.getEncoder();
        }

        if ((flags & BASE64_F_NOPADDING) == BASE64_F_NOPADDING) {
            encoder = encoder.withoutPadding();
        }

        return encoder;
    }

    private static Base64.Decoder createDecoder(byte flags) {
        if ((flags & BASE64_F_IGNOREINVCHAR) == BASE64_F_IGNOREINVCHAR) {
            // Ignore non-base64 characters (whitespace/line breaks/etc.)
            return Base64.getMimeDecoder();
        }
        return Base64.getDecoder();
    }

    private static byte[] toByteArray(int[] binary) {
        byte[] result = new byte[binary.length];

        for (int i = 0; i < binary.length; i++) {
            int value = binary[i];
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("binary value out of range at index " + i + ": " + value);
            }
            result[i] = (byte) (value & 0xFF);
        }

        return result;
    }

    private static String padToMultipleOf4(String input) {
        // Strip whitespace to mimic "ignore invalid" behavior partially for strict decoders
        String trimmed = input.trim();
        int mod = trimmed.length() % 4;
        if (mod == 0) {
            return trimmed;
        }

        int padCount = 4 - mod;
        StringBuilder sb = new StringBuilder(trimmed.length() + padCount);
        sb.append(trimmed);
        for (int i = 0; i < padCount; i++) {
            sb.append('=');
        }
        return sb.toString();
    }
}
