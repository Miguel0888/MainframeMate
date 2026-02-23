package com.softwareag.naturalone.natural.pal;

import java.io.*;
import java.util.ArrayList;

public abstract class PalType implements Serializable, IPalType {

    private static final long serialVersionUID = 1L;

    private ArrayList record;
    protected int type;
    protected int recordTail;
    protected int recordLength;
    protected int palVersion;
    protected int ndvType;
    protected String serverCodePage;

    public PalType() {
        setRecord(new ArrayList());
    }

    // ── Public methods ─────────────────────────────────────────────────

    public final void setRecord(ArrayList buffer) {
        if (buffer == null) return;
        this.recordTail = 0;
        this.recordLength = buffer.size();
        this.record = buffer;
    }

    public final ArrayList getRecord() {
        return record;
    }

    public int get() {
        return type;
    }

    public void setPalVers(int version) {
        this.palVersion = version;
    }

    public void setNdvType(int type) {
        this.ndvType = type;
    }

    public void setServerCodePage(String codePage) {
        this.serverCodePage = codePage;
    }

    public final int intFromBuffer() {
        try {
            // 1. Determine length until null byte
            int len = 0;
            int startPos = recordTail;
            while (startPos + len < record.size() && (Byte) record.get(startPos + len) != 0) {
                len++;
            }

            // 2. Allocate byte array
            byte[] bytes = new byte[len];

            // 3. Read bytes sequentially
            int i = 0;
            while (i < len && recordTail < record.size() && (Byte) record.get(recordTail) != 0) {
                bytes[i] = (Byte) record.get(recordTail);
                recordTail++;
                i++;
            }

            // 4. Skip null byte
            recordTail++;

            // 5. Convert to integer
            return Integer.valueOf(new String(bytes, "ASCII"));
        } catch (Exception e) {
            return 0;
        }
    }

    public final String stringFromBuffer() {
        try {
            // 1. Determine length until null byte
            int len = 0;
            int startPos = recordTail;
            while (startPos + len < record.size() && (Byte) record.get(startPos + len) != 0) {
                len++;
            }

            // 2. Allocate byte array
            byte[] bytes = new byte[len];

            // 3. Read bytes sequentially
            int i = 0;
            while (i < len && recordTail < record.size() && (Byte) record.get(recordTail) != 0) {
                bytes[i] = (Byte) record.get(recordTail);
                recordTail++;
                i++;
            }

            // 4. Skip null byte
            recordTail++;

            // 5. Return as string
            return new String(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    public abstract void serialize();

    public abstract void restore();

    // ── Protected methods (for subclasses) ─────────────────────────────

    protected final void stringToBuffer(String text) {
        byte[] bytes = text.getBytes();
        for (byte b : bytes) {
            record.add(b);
        }
        record.add((byte) 0);
    }

    protected final void intToBuffer(int value) {
        stringToBuffer(Integer.toString(value));
    }

    protected final void byteToBuffer(byte value) {
        record.add(value);
    }

    protected final void byteArrayToBuffer(byte[] data) {
        for (byte b : data) {
            record.add(b);
        }
    }

    protected final byte byteFromBuffer() {
        try {
            byte value = (Byte) record.get(recordTail);
            recordTail++;
            return value;
        } catch (Exception e) {
            return 0;
        }
    }

    protected final boolean booleanFromBuffer() {
        return byteFromBuffer() != 0;
    }

    protected final void booleanToBuffer(boolean value) {
        record.add(value ? (byte) 1 : (byte) 0);
    }

    protected final char[] recordToCharArray() {
        byte[] bytes = new byte[recordLength];
        for (int i = 0; i < recordLength; i++) {
            bytes[i] = (Byte) record.get(i);
        }
        return new String(bytes).toCharArray();
    }

    protected final byte[] recordToByteArray() {
        byte[] bytes = new byte[recordLength];
        for (int i = 0; i < recordLength; i++) {
            bytes[i] = (Byte) record.get(i);
        }
        return bytes;
    }

    protected final int[] utf16ToCharset(String text, String targetCharset, boolean withNullTerminator) {
        try {
            byte[] bytes = text.getBytes(targetCharset);
            int[] result = withNullTerminator ? new int[bytes.length + 1] : new int[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                result[i] = bytes[i] < 0 ? bytes[i] + 256 : bytes[i];
            }
            if (withNullTerminator) {
                result[result.length - 1] = 0;
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    protected final char[] utf16ToCharsetToBase64(String text, String targetCharset, boolean withNullTerminator) {
        try {
            int[] encoded = utf16ToCharset(text, targetCharset, withNullTerminator);
            if (encoded == null) return null;
            return Base64Coder.encode(encoded, (byte) 3);
        } catch (Exception e) {
            return null;
        }
    }

    protected final StringBuffer getUtf16(byte[] rawData, String sourceCharset) throws UnsupportedEncodingException, IOException {
        StringBuffer sb = new StringBuffer();
        ByteArrayInputStream bais = new ByteArrayInputStream(rawData);
        InputStreamReader reader = new InputStreamReader(bais, sourceCharset);
        int ch;
        while ((ch = reader.read()) != -1) {
            sb.append((char) ch);
        }
        reader.close();
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\0') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb;
    }

    protected final StringBuffer getUtf16ICU(byte[] rawData, String sourceCharset) throws UnsupportedEncodingException, IOException {
        String decoded = ICUCharsetCoder.decode(sourceCharset, rawData);
        StringBuffer sb = new StringBuffer();
        StringReader reader = new StringReader(decoded);
        int ch;
        while ((ch = reader.read()) != -1) {
            sb.append((char) ch);
        }
        reader.close();
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\0') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb;
    }
}