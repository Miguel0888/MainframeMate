package de.bund.zrb.ndv.core.impl.type;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.ArrayList;

public abstract class PalType implements Serializable, IPalType {

    private static final long serialVersionUID = 1L;

    private ArrayList datensatz;
    protected int typSchluessel;
    protected int lesePosition;
    protected int datensatzLaenge;
    protected int uebertragungsVersion;
    protected int serverArt;
    protected String serverZeichensatz;

    public PalType() {
        setRecord(new ArrayList());
    }

    // ── Public methods ─────────────────────────────────────────────────

    public final void setRecord(ArrayList buffer) {
        if (buffer == null) return;
        this.lesePosition = 0;
        this.datensatzLaenge = buffer.size();
        this.datensatz = buffer;
    }

    public final ArrayList getRecord() {
        return datensatz;
    }

    public int get() {
        return typSchluessel;
    }

    public void setPalVers(int version) {
        this.uebertragungsVersion = version;
    }

    public void setNdvType(int type) {
        this.serverArt = type;
    }

    public void setServerCodePage(String codePage) {
        this.serverZeichensatz = codePage;
    }

    public final int intFromBuffer() {
        try {
            // 1. Determine length until null byte
            int len = 0;
            int startPos = lesePosition;
            while (startPos + len < datensatz.size() && (Byte) datensatz.get(startPos + len) != 0) {
                len++;
            }

            // 2. Allocate byte array
            byte[] bytes = new byte[len];

            // 3. Read bytes sequentially
            int i = 0;
            while (i < len && lesePosition < datensatz.size() && (Byte) datensatz.get(lesePosition) != 0) {
                bytes[i] = (Byte) datensatz.get(lesePosition);
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
            while (startPos + len < datensatz.size() && (Byte) datensatz.get(startPos + len) != 0) {
                len++;
            }

            // 2. Allocate byte array
            byte[] bytes = new byte[len];

            // 3. Read bytes sequentially
            int i = 0;
            while (i < len && lesePosition < datensatz.size() && (Byte) datensatz.get(lesePosition) != 0) {
                bytes[i] = (Byte) datensatz.get(lesePosition);
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

    protected final void textInPuffer(String text) {
        byte[] bytes = text.getBytes();
        for (byte b : bytes) {
            datensatz.add(b);
        }
        datensatz.add((byte) 0);
    }

    protected final void ganzzahlInPuffer(int value) {
        textInPuffer(Integer.toString(value));
    }

    protected final void byteInPuffer(byte value) {
        datensatz.add(value);
    }

    protected final void byteArrayInPuffer(byte[] data) {
        for (byte b : data) {
            datensatz.add(b);
        }
    }

    protected final byte byteAusPuffer() {
        try {
            byte value = (Byte) datensatz.get(lesePosition);
            lesePosition++;
            return value;
        } catch (Exception e) {
            return 0;
        }
    }

    protected final boolean wahrheitswertAusPuffer() {
        return byteAusPuffer() != 0;
    }

    protected final void wahrheitswertInPuffer(boolean value) {
        datensatz.add(value ? (byte) 1 : (byte) 0);
    }

    protected final char[] datensatzAlsZeichenArray() {
        byte[] bytes = new byte[datensatzLaenge];
        for (int i = 0; i < datensatzLaenge; i++) {
            bytes[i] = (Byte) datensatz.get(i);
        }
        return new String(bytes).toCharArray();
    }

    protected final byte[] datensatzAlsByteArray() {
        byte[] bytes = new byte[datensatzLaenge];
        for (int i = 0; i < datensatzLaenge; i++) {
            bytes[i] = (Byte) datensatz.get(i);
        }
        return bytes;
    }

    protected final int[] utf16NachZeichensatz(String text, String targetCharset, boolean withNullTerminator) {
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

    protected final char[] utf16NachZeichensatzAlsBase64(String text, String targetCharset, boolean withNullTerminator) {
        try {
            int[] encoded = utf16NachZeichensatz(text, targetCharset, withNullTerminator);
            if (encoded == null) return null;
            byte[] bytes = new byte[encoded.length];
            for (int i = 0; i < encoded.length; i++) bytes[i] = (byte) (encoded[i] & 0xFF);
            return Base64.getMimeEncoder().encodeToString(bytes).toCharArray();
        } catch (Exception e) {
            return null;
        }
    }

    protected final StringBuffer rohDatenNachUtf16(byte[] rawData, String sourceCharset) throws UnsupportedEncodingException, IOException {
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

    protected final StringBuffer rohDatenNachUtf16MitIcu(byte[] rawData, String sourceCharset) throws UnsupportedEncodingException, IOException {
        String decoded = new String(rawData, Charset.forName(sourceCharset));
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