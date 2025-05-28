package de.bund.zrb.ftp;

import org.apache.commons.net.ftp.FTPFile;

import java.io.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class FtpFileBuffer {

    private final String remotePath;
    private final long expectedSize;
    private final FTPFile fileMeta;
    private final byte[] rawBytes;
    private final String originalHash;
    private final boolean recordStructure;
    private final Charset charset;
    private final Byte padding;

    public FtpFileBuffer(InputStream in, Charset charset, String remotePath, FTPFile fileMeta, boolean recordStructure, ProgressListener progress) throws IOException {
        this(in, null, charset, remotePath, fileMeta, recordStructure, progress);
    }

    public FtpFileBuffer(InputStream in, Byte padding, Charset charset, String remotePath, FTPFile fileMeta, boolean recordStructure, ProgressListener progress) throws IOException {
        this.padding = padding;
        this.charset = charset;
        this.remotePath = remotePath;
        this.fileMeta = fileMeta;
        this.expectedSize = fileMeta != null ? fileMeta.getSize() : -1;
        this.recordStructure = recordStructure;

        this.rawBytes = readAllBytes(in, progress);
        this.originalHash = computeHash(this.rawBytes);
    }

    public Charset getCharset() {
        return charset != null ? charset : Charset.defaultCharset();
    }

    private byte[] readAllBytes(InputStream in, ProgressListener progress) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;

        while ((read = in.read(buffer)) != -1) {
            if (padding == null) {
                // Kein Padding → direkte Übernahme
                out.write(buffer, 0, read);
            } else {
                for (int i = 0; i < read; i++) {
                    if (buffer[i] != padding) { // skip padding bytes
                        out.write(buffer[i]);
                    }
                }
            }
            total += read;
            if (expectedSize > 0 && progress != null) {
                int percent = (int) (100L * total / expectedSize);
                progress.onProgress(percent);
            }
        }

        return out.toByteArray();
    }

    private String computeHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // Vergleich auf Byte-Ebene per Hash
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        FtpFileBuffer other = (FtpFileBuffer) obj;
        return originalHash != null && originalHash.equals(other.originalHash);
    }

    @Override
    public int hashCode() {
        return originalHash != null ? originalHash.hashCode() : 0;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public long getExpectedSize() {
        return expectedSize;
    }

    public FTPFile getMeta() {
        return fileMeta;
    }

    public boolean hasRecordStructure() {
        return recordStructure;
    }

    public String getOriginalHash() {
        return originalHash;
    }

    public interface ProgressListener {
        void onProgress(int percent);
    }

    /**
     * Creates a copy but applies new content
     * @param input
     * @return
     * @throws IOException
     */
    public FtpFileBuffer withContent(InputStream input) throws IOException {
        return new FtpFileBuffer(
                input,
                this.padding,
                this.getCharset(),
                this.remotePath,
                this.fileMeta,
                this.recordStructure,
                null
        );
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    // Debug
    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    public void printHexDump() {
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

}
