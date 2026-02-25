package de.bund.zrb.archive.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.logging.Logger;

/**
 * SHA-256 content hashing for deduplication.
 */
public class ContentHasher {

    private static final Logger LOG = Logger.getLogger(ContentHasher.class.getName());

    /**
     * Compute SHA-256 hex digest of text content.
     */
    public static String hash(String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            LOG.warning("[ContentHasher] SHA-256 failed: " + e.getMessage());
            return "";
        }
    }

    /**
     * Compute SHA-256 hex digest of raw bytes.
     */
    public static String hash(byte[] data) {
        if (data == null || data.length == 0) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return bytesToHex(digest);
        } catch (Exception e) {
            LOG.warning("[ContentHasher] SHA-256 failed: " + e.getMessage());
            return "";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
