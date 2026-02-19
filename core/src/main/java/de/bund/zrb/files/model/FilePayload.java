package de.bund.zrb.files.model;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

/**
 * Immutable payload representing file content.
 *
 * IMPORTANT: getBytes() always returns the REMOTE bytes (including record markers/EOF).
 * For display in editor, use getEditorText() which handles RECORD_STRUCTURE transformation.
 */
public class FilePayload {

    private final byte[] bytes;
    private final Charset charset;
    private final boolean recordStructure;
    private final String hash;
    private final Map<String, String> attributes;
    private final String editorText; // Cached editor-friendly text

    private FilePayload(byte[] bytes, Charset charset, boolean recordStructure, String hash,
                        Map<String, String> attributes, String editorText) {
        this.bytes = bytes == null ? new byte[0] : bytes;
        this.charset = charset;
        this.recordStructure = recordStructure;
        this.hash = hash;
        this.attributes = attributes == null ? Collections.<String, String>emptyMap() : attributes;
        this.editorText = editorText;
    }

    public static FilePayload fromBytes(byte[] bytes, Charset charset, boolean recordStructure) {
        return new FilePayload(bytes, charset, recordStructure, computeHash(bytes),
                Collections.<String, String>emptyMap(), null);
    }

    public static FilePayload fromBytes(byte[] bytes, Charset charset, boolean recordStructure,
                                        Map<String, String> attributes) {
        return new FilePayload(bytes, charset, recordStructure, computeHash(bytes), attributes, null);
    }

    /**
     * Create payload with pre-computed editor text (for record structure decoding).
     */
    public static FilePayload fromBytesWithEditorText(byte[] bytes, Charset charset, boolean recordStructure,
                                                       String editorText) {
        return new FilePayload(bytes, charset, recordStructure, computeHash(bytes),
                Collections.<String, String>emptyMap(), editorText);
    }

    /**
     * Get the raw remote bytes (including record markers, EOF markers, etc.).
     * Use this for hash calculation and writing back to server.
     */
    public byte[] getBytes() {
        return bytes;
    }

    public Charset getCharset() {
        return charset;
    }

    public boolean hasRecordStructure() {
        return recordStructure;
    }

    public String getHash() {
        return hash;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    /**
     * Get editor-friendly text.
     *
     * For RECORD_STRUCTURE files, this returns text with proper newlines
     * (record markers replaced, EOF removed).
     *
     * For non-RECORD_STRUCTURE files, this is equivalent to new String(bytes, charset).
     *
     * @return text suitable for display in editor
     */
    public String getEditorText() {
        if (editorText != null) {
            return editorText;
        }
        // Fallback: simple string conversion (no record structure transformation)
        Charset cs = charset != null ? charset : Charset.defaultCharset();
        return new String(bytes, cs);
    }

    /**
     * Check if editor text was explicitly set (for record structure files).
     */
    public boolean hasEditorText() {
        return editorText != null;
    }

    public static String computeHash(byte[] data) {
        byte[] safe = data == null ? new byte[0] : data;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(safe);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

