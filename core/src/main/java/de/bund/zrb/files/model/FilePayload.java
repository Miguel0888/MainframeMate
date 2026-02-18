package de.bund.zrb.files.model;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

public class FilePayload {

    private final byte[] bytes;
    private final Charset charset;
    private final boolean recordStructure;
    private final String hash;
    private final Map<String, String> attributes;

    private FilePayload(byte[] bytes, Charset charset, boolean recordStructure, String hash,
                        Map<String, String> attributes) {
        this.bytes = bytes == null ? new byte[0] : bytes;
        this.charset = charset;
        this.recordStructure = recordStructure;
        this.hash = hash;
        this.attributes = attributes == null ? Collections.<String, String>emptyMap() : attributes;
    }

    public static FilePayload fromBytes(byte[] bytes, Charset charset, boolean recordStructure) {
        return new FilePayload(bytes, charset, recordStructure, computeHash(bytes),
                Collections.<String, String>emptyMap());
    }

    public static FilePayload fromBytes(byte[] bytes, Charset charset, boolean recordStructure,
                                        Map<String, String> attributes) {
        return new FilePayload(bytes, charset, recordStructure, computeHash(bytes), attributes);
    }

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

