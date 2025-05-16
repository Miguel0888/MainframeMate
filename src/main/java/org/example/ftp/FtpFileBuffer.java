package org.example.ftp;

import org.apache.commons.net.ftp.FTPFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class FtpFileBuffer {

    private final String remotePath;
    private final long expectedSize;
    private final FTPFile fileMeta;

    private String content;
    private String originalHash;

    public FtpFileBuffer(String remotePath, FTPFile fileMeta) {
        this.remotePath = remotePath;
        this.fileMeta = fileMeta;
        this.expectedSize = fileMeta != null ? fileMeta.getSize() : -1;
    }

    /**
     * Load content from input stream and calculate original hash.
     */
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

        this.content = out.toString(StandardCharsets.UTF_8.name());
        this.originalHash = sha256(this.content);
    }

    /**
     * Return current content as InputStream for uploading.
     */
    public InputStream toInputStream(String updatedContent) {
        byte[] bytes = updatedContent.getBytes(StandardCharsets.UTF_8);
        return new ByteArrayInputStream(bytes);
    }

    /**
     * Check if updated content matches original hash.
     */
    public boolean isUnchanged(String currentRemoteContent) {
        String currentHash = sha256(currentRemoteContent);
        return originalHash != null && originalHash.equals(currentHash);
    }

    public String getRemotePath() {
        return remotePath;
    }

    public String getOriginalContent() {
        return content;
    }

    public long getExpectedSize() {
        return expectedSize;
    }

    public FTPFile getMeta() {
        return fileMeta;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Optional: Interface for reporting progress.
     */
    public interface ProgressListener {
        void onProgress(int percent);
    }
}
