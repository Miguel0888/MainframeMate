package org.example.ftp;

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

    private byte[] rawBytes; // Originaldaten vom Server
    private String content;  // Aktueller Textinhalt
    private String originalHash;

    public FtpFileBuffer(String remotePath, FTPFile fileMeta) {
        this.remotePath = remotePath;
        this.fileMeta = fileMeta;
        this.expectedSize = fileMeta != null ? fileMeta.getSize() : -1;
    }

    public void loadContent(InputStream in, ProgressListener progress) throws IOException {
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

        this.rawBytes = out.toByteArray();
        this.content = new String(rawBytes, Charset.defaultCharset()); // Initialanzeige
        this.originalHash = sha256(this.content);
    }

    public void applyEncoding(Charset charset) {
        if (rawBytes != null) {
            this.content = new String(rawBytes, charset);
        }
    }

    public String decodeWith(Charset charset) {
        if (rawBytes != null) {
            return new String(rawBytes, charset);
        }
        return "";
    }

    public InputStream toInputStream(String updatedContent) {
        return new ByteArrayInputStream(updatedContent.getBytes(Charset.defaultCharset()));
    }

    public boolean isUnchanged(String currentRemoteContent) {
        return originalHash != null && originalHash.equals(sha256(currentRemoteContent));
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

    public String getOriginalContent() {
        return content;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(Charset.defaultCharset()));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public interface ProgressListener {
        void onProgress(int percent);
    }
}
