package com.softwareag.naturalone.natural.pal.type;

import com.softwareag.naturalone.natural.pal.util.ICUCharsetCoder;
import com.softwareag.naturalone.natural.pal.external.IPalTypeStream;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public final class PalTypeStream extends PalType implements IPalTypeStream {
    private static final long serialVersionUID = 1L;
    private byte[] streamRecord;

    public PalTypeStream() { super(); type = 13; }
    public PalTypeStream(byte[] record, int ndvType) {
        this();
        this.ndvType = ndvType;
        if (record != null) this.streamRecord = Arrays.copyOf(record, record.length);
    }

    public void serialize() {
        if (streamRecord != null) {
            if (ndvType == 1) { // Mainframe - hex encode
                byteArrayToBuffer(Palbtos(streamRecord));
            } else {
                byteArrayToBuffer(streamRecord);
            }
        }
    }
    public void restore() {
        byte[] raw = recordToByteArray();
        if (ndvType == 1 && raw != null) { // Mainframe - hex decode
            streamRecord = Palstob(raw);
        } else {
            streamRecord = raw;
        }
    }

    public void convert(String charsetName, boolean toUtf8) throws UnsupportedEncodingException, IOException {
        if (streamRecord == null) return;
        if (toUtf8) {
            StringBuffer sb = getUtf16ICU(streamRecord, charsetName);
            streamRecord = sb.toString().getBytes("UTF-8");
        } else {
            String utf16 = new String(streamRecord, "UTF-8");
            streamRecord = ICUCharsetCoder.encode(charsetName, utf16, false);
        }
    }

    public byte[] getStreamRecord() {
        return streamRecord != null ? Arrays.copyOf(streamRecord, streamRecord.length) : null;
    }
    public void setStreamRecord(byte[] record) {
        this.streamRecord = record != null ? Arrays.copyOf(record, record.length) : null;
    }

    // ── static hex helpers ──
    static byte[] Palstob(byte[] hexInput) {
        if (hexInput == null) return null;
        // validate: all bytes must be hex digits
        for (int i = 0; i < hexInput.length; i++) {
            if (!isxdigit(hexInput[i])) return new byte[0];
        }
        int len = hexInput.length / 2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            int high = hexDigit(hexInput[i * 2]);
            int low = hexDigit(hexInput[i * 2 + 1]);
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    static byte[] Palbtos(byte[] binaryInput) {
        if (binaryInput == null) return null;
        byte[] result = new byte[binaryInput.length * 2];
        for (int i = 0; i < binaryInput.length; i++) {
            int v = binaryInput[i] & 0xFF;
            result[i * 2] = hexChar(v >> 4);
            result[i * 2 + 1] = hexChar(v & 0x0F);
        }
        return result;
    }

    static boolean isxdigit(byte b) {
        return (b >= '0' && b <= '9') || (b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F');
    }

    private static int hexDigit(byte b) {
        if (b >= '0' && b <= '9') return b - '0';
        if (b >= 'a' && b <= 'f') return b - 'a' + 10;
        if (b >= 'A' && b <= 'F') return b - 'A' + 10;
        return 0;
    }

    private static byte hexChar(int nibble) {
        return (byte) (nibble < 10 ? '0' + nibble : 'a' + nibble - 10);
    }
}
