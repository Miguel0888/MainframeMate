package org.example.ftp;

import org.apache.commons.net.ftp.FTPFile;
import org.example.model.Settings;
import org.example.util.SettingsManager;

import java.io.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class FtpFileBuffer {

    private final String remotePath;
    private final long expectedSize;
    private final FTPFile fileMeta;

    private byte[] rawBytes;
    private String content;
    private String originalHash;

    private Charset currentCharset;
    private final boolean recordStructure;

    public FtpFileBuffer(String remotePath, FTPFile fileMeta, boolean recordStructure) {
        this.remotePath = remotePath;
        this.fileMeta = fileMeta;
        this.expectedSize = fileMeta != null ? fileMeta.getSize() : -1;
        this.recordStructure = recordStructure;

        Settings settings = SettingsManager.load();
        this.currentCharset = Charset.forName(settings.encoding);
    }

    public void loadContent(InputStream in, ProgressListener progress) throws IOException {
        this.rawBytes = readAllBytes(in, progress);
        this.originalHash = sha256(rawBytes);

        if (recordStructure) {
            this.content = mapLineAndFileEndings(rawBytes, currentCharset);
        } else {
            this.content = new String(rawBytes, currentCharset);
        }
    }

    private boolean endsWith(byte[] data, byte[] suffix) {
        if (data.length < suffix.length) return false;
        for (int i = 0; i < suffix.length; i++) {
            if (data[data.length - suffix.length + i] != suffix[i]) return false;
        }
        return true;
    }

    public InputStream toInputStream(String updatedContent) {
        Settings settings = SettingsManager.load();

        if (recordStructure) {
            String markerHex = settings.lineEnding; // z. B. "FF01"
            byte[] markerBytes = (markerHex != null && !markerHex.isEmpty())
                    ? parseHexMarker(markerHex)
                    : null;

            byte[] fileEndMarker = (settings.fileEndMarker != null && !settings.fileEndMarker.isEmpty())
                    ? parseHexMarker(settings.fileEndMarker)
                    : null;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String[] lines = updatedContent.split("\n", -1); // auch leere letzte Zeile berücksichtigen

            try {
                for (String line : lines) {
                    out.write(line.getBytes(currentCharset));
                    if (markerBytes != null) {
                        out.write(markerBytes);
                    }
                }

                // 🆕 Vor dem Datei-Ende-Zeichen ggf. zusätzliches lineEnding setzen
                if (fileEndMarker != null) {
                    if (markerBytes != null) {
                        out.write(markerBytes);
                    }
                    out.write(fileEndMarker);
                }

            } catch (IOException e) {
                throw new UncheckedIOException("Fehler beim Schreiben der Datei", e);
            }

            return new ByteArrayInputStream(out.toByteArray());

        } else {
            return new ByteArrayInputStream(updatedContent.getBytes(currentCharset));
        }
    }



    private byte[] parseHexMarker(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex-Zeichenfolge muss gerade Länge haben: " + hex);
        }

        int len = hex.length() / 2;
        byte[] bytes = new byte[len];

        for (int i = 0; i < len; i++) {
            int index = i * 2;
            bytes[i] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
        }

        return bytes;
    }

    public boolean isUnchanged(String currentRemoteContent) {
        byte[] currentBytes = currentRemoteContent.getBytes(currentCharset);
        return originalHash != null && originalHash.equals(sha256(currentBytes));
    }

    public void applyEncoding(Charset charset) {
        if (rawBytes != null) {
            this.content = new String(rawBytes, charset);
        }
    }

    public String decodeWith(Charset charset) {
        setCharset(charset);
        return rawBytes != null ? new String(rawBytes, charset) : "";
    }

    private void setCharset(Charset charset) {
        this.currentCharset = charset;
        if (rawBytes != null) {
            this.content = new String(rawBytes, charset);
        }
    }

    private byte[] readAllBytes(InputStream in, ProgressListener progress) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;

        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            total += read;

            if (expectedSize > 0 && progress != null) {
                int percent = (int) (100L * total / expectedSize);
                progress.onProgress(percent);
            }
        }

        return out.toByteArray();
    }

    private String mapLineAndFileEndings(byte[] bytes, Charset charset) {
        Settings settings = SettingsManager.load();
        String markerHex = settings.lineEnding;

        // Datei-Ende-Marker parsen (unabhängig von lineEnding)
        byte[] fileEndMarker = settings.fileEndMarker != null && !settings.fileEndMarker.isEmpty()
                ? parseHexMarker(settings.fileEndMarker)
                : null;

        int effectiveLength = bytes.length;

        // Entferne Datei-Ende-Marker (und optional vorheriges lineEnding)
        if (fileEndMarker != null && endsWith(bytes, fileEndMarker)) {
            effectiveLength -= fileEndMarker.length;

            if (markerHex != null && !markerHex.isEmpty()) {
                byte[] lineMarker = parseHexMarker(markerHex);
                if (lineMarker.length == 2 &&
                        effectiveLength >= 2 &&
                        bytes[effectiveLength - 2] == lineMarker[0] &&
                        bytes[effectiveLength - 1] == lineMarker[1]) {
                    effectiveLength -= 2;
                }
            }
        }

        // Optional: Zeilenenden ersetzen
        ByteArrayOutputStream transformed = new ByteArrayOutputStream();
        byte[] lineMarker = markerHex != null && !markerHex.isEmpty()
                ? parseHexMarker(markerHex)
                : null;

        for (int i = 0; i < effectiveLength; i++) {
            if (lineMarker != null &&
                    lineMarker.length == 2 &&
                    i + 1 < effectiveLength &&
                    bytes[i] == lineMarker[0] &&
                    bytes[i + 1] == lineMarker[1]) {
                transformed.write('\n');
                i++; // Skip both bytes
            } else {
                transformed.write(bytes[i]);
            }
        }

        return new String(transformed.toByteArray(), charset);
    }


    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void printHexDump() {
        if (rawBytes == null || rawBytes.length == 0) {
            System.out.println("Keine Daten vorhanden.");
            return;
        }

        int offset = 0;
        while (offset < rawBytes.length) {
            int len = Math.min(16, rawBytes.length - offset);
            System.out.printf("%08X  ", offset);

            for (int i = 0; i < 16; i++) {
                if (i < len) {
                    System.out.printf("%02X ", rawBytes[offset + i]);
                } else {
                    System.out.print("   ");
                }
                if (i == 7) System.out.print(" ");
            }

            System.out.print(" |");
            for (int i = 0; i < len; i++) {
                byte b = rawBytes[offset + i];
                char c = (char) (b & 0xFF);
                System.out.print((c >= 32 && c <= 126) ? c : '.');
            }
            System.out.println("|");
            offset += len;
        }
    }

    /**
     * Prepare future support for RDW-based decoding in Binary Mode.
     * Will be used if TYPE = BINARY and STRUCTURE = RECORD.
     */
    @SuppressWarnings("unused")
    private String decodeRdwStructure() {
        // ToDo: Implement binary RDW decoding logic here when needed
        return "";
    }

    // Getter

    public String getRemotePath() {
        return remotePath;
    }

    public long getExpectedSize() {
        return expectedSize;
    }

    public FTPFile getMeta() {
        return fileMeta;
    }

    public String getContent() {
        return content;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public Charset getCharset() {
        return currentCharset;
    }

    public interface ProgressListener {
        void onProgress(int percent);
    }

    public void updateOriginalHashFromContent(String updatedContent) {
        this.originalHash = sha256(updatedContent.getBytes(currentCharset));
    }

}
