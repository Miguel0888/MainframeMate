package com.softwareag.naturalone.natural.pal;

import java.io.*;
import java.util.ArrayList;

public abstract class PalType implements Serializable, IPalType {

    private static final long serialVersionUID = 1L;

    private ArrayList datenpuffer;
    protected int typSchluessel;
    protected int lesePosition;
    protected int pufferLaenge;
    protected int protokollVersion;
    protected int serverTyp;
    protected String serverZeichensatz;

    public PalType() {
        setRecord(new ArrayList());
    }

    // ── Public methods ─────────────────────────────────────────────────

    public final void setRecord(ArrayList buffer) {
        if (buffer == null) return;
        this.lesePosition = 0;
        this.pufferLaenge = buffer.size();
        this.datenpuffer = buffer;
    }

    public final ArrayList getRecord() {
        return datenpuffer;
    }

    public int get() {
        return typSchluessel;
    }

    public void setPalVers(int version) {
        this.protokollVersion = version;
    }

    public void setNdvType(int type) {
        this.serverTyp = type;
    }

    public void setServerCodePage(String codePage) {
        this.serverZeichensatz = codePage;
    }

    public final int intFromBuffer() {
        try {
            // 1. Determine length until null byte
            int len = 0;
            int startPos = lesePosition;
            while (startPos + len < datenpuffer.size() && (Byte) datenpuffer.get(startPos + len) != 0) {
                len++;
            }

            // 2. Allocate byte array
            byte[] bytes = new byte[len];

            // 3. Read bytes sequentially
            int i = 0;
            while (i < len && lesePosition < datenpuffer.size() && (Byte) datenpuffer.get(lesePosition) != 0) {
                bytes[i] = (Byte) datenpuffer.get(lesePosition);
                lesePosition++;
                i++;
            }

            // 4. Skip null byte
            lesePosition++;

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
            int startPos = lesePosition;
            while (startPos + len < datenpuffer.size() && (Byte) datenpuffer.get(startPos + len) != 0) {
                len++;
            }

            // 2. Allocate byte array
            byte[] bytes = new byte[len];

            // 3. Read bytes sequentially
            int i = 0;
            while (i < len && lesePosition < datenpuffer.size() && (Byte) datenpuffer.get(lesePosition) != 0) {
                bytes[i] = (Byte) datenpuffer.get(lesePosition);
                lesePosition++;
                i++;
            }

            // 4. Skip null byte
            lesePosition++;

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
            datenpuffer.add(b);
        }
        datenpuffer.add((byte) 0);
    }

    protected final void intToBuffer(int value) {
        stringToBuffer(Integer.toString(value));
    }

    protected final void byteToBuffer(byte value) {
        datenpuffer.add(value);
    }

    protected final void byteArrayToBuffer(byte[] data) {
        for (byte b : data) {
            datenpuffer.add(b);
        }
    }

    protected final byte byteFromBuffer() {
        try {
            byte value = (Byte) datenpuffer.get(lesePosition);
            lesePosition++;
            return value;
        } catch (Exception e) {
            return 0;
        }
    }

    protected final boolean booleanFromBuffer() {
        return byteFromBuffer() != 0;
    }

    protected final void booleanToBuffer(boolean value) {
        datenpuffer.add(value ? (byte) 1 : (byte) 0);
    }

    protected final char[] recordToCharArray() {
        byte[] bytes = new byte[pufferLaenge];
        for (int i = 0; i < pufferLaenge; i++) {
            bytes[i] = (Byte) datenpuffer.get(i);
        }
        return new String(bytes).toCharArray();
    }

    protected final byte[] recordToByteArray() {
        byte[] bytes = new byte[pufferLaenge];
        for (int i = 0; i < pufferLaenge; i++) {
            bytes[i] = (Byte) datenpuffer.get(i);
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