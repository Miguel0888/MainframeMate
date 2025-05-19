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

        Settings settings = SettingsManager.load();
        if (recordStructure) {
            this.content = mapLineEndings(new String(rawBytes, currentCharset), settings.lineEnding);
        } else {
            this.content = new String(rawBytes, currentCharset);
        }

        this.originalHash = sha256(rawBytes);

        if (settings.enableHexDump) {
            printHexDump();
        }
    }

    public InputStream toInputStream(String updatedContent) {
        Settings settings = SettingsManager.load();
        byte[] encoded;

        if (recordStructure) {
            String restored = unmapLineEndings(updatedContent, settings.lineEnding);
            encoded = restored.getBytes(currentCharset);
        } else {
            encoded = updatedContent.getBytes(currentCharset);
        }

        return new ByteArrayInputStream(encoded);
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

    private String mapLineEndings(String text, String marker) {
        if (marker == null || marker.isEmpty()) return text;
        return text.replace(marker, "\n");
    }

    private String unmapLineEndings(String text, String marker) {
        if (marker == null || marker.isEmpty()) return text;
        return text.replace("\n", marker);
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
}
