package org.example.ftp;

import org.apache.commons.net.ftp.FTPFile;
import org.example.util.SettingsManager;

import java.io.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

public class FtpFileBuffer {
    private Charset currentCharset = Charset.forName(SettingsManager.load().encoding);

    private final String remotePath;
    private final long expectedSize;
    private final FTPFile fileMeta;

    private byte[] rawBytes; // Originaldaten vom Server
    private String content;  // Aktueller Textinhalt
    private String originalHash;

    private final boolean recordStructure;

    public FtpFileBuffer(String remotePath, FTPFile fileMeta, boolean recordStructure) {
        this.remotePath = remotePath;
        this.fileMeta = fileMeta;
        this.expectedSize = fileMeta != null ? fileMeta.getSize() : -1;
        this.recordStructure = recordStructure;
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
        if (recordStructure) {
            this.content = decodeWithRdwMarkers(currentCharset); // RDW wird als [Länge]Zeile interpretiert
        } else {
            this.content = new String(rawBytes, currentCharset);
        }
//        this.originalHash = sha256(this.content); // decoded hash
        this.originalHash = sha256(rawBytes);
    }

    public void applyEncoding(Charset charset) {
        if (rawBytes != null) {
            this.content = new String(rawBytes, charset);
        }
    }

    public String decodeWith(Charset charset) {
        setCharset(charset); // required later to save the file, if wanted
        if (rawBytes != null) {
            return new String(rawBytes, charset);
        }
        return "";
    }

    public InputStream toInputStream(String updatedContent) {
        if (recordStructure) {
            try {
                return encodeFromRdwMarkers(updatedContent, currentCharset);
            } catch (IOException e) {
                throw new UncheckedIOException("Fehler beim Serialisieren von RDW-Inhalt", e);
            }
        } else {
            return new ByteArrayInputStream(updatedContent.getBytes(currentCharset));
        }
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

    public String getContent() {
        return content;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    // old version uses decoding
//    private String sha256(String input) {
//        try {
//            MessageDigest digest = MessageDigest.getInstance("SHA-256");
//            byte[] hashBytes = digest.digest(input.getBytes(Charset.defaultCharset()));
//            return Base64.getEncoder().encodeToString(hashBytes);
//        } catch (NoSuchAlgorithmException e) {
//            throw new RuntimeException("SHA-256 not available", e);
//        }
//    }
//
//    public boolean isUnchanged(String currentRemoteContent) {
//        return originalHash != null && originalHash.equals(sha256(currentRemoteContent));
//    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public boolean isUnchanged(String currentRemoteContent) {
        byte[] currentBytes = currentRemoteContent.getBytes(currentCharset);
        return originalHash != null && originalHash.equals(sha256(currentBytes));
    }

    public interface ProgressListener {
        void onProgress(int percent);
    }

    private void setCharset(Charset charset) {
        this.currentCharset = charset;
        if (rawBytes != null) {
            this.content = new String(rawBytes, charset);
        }
    }

    public Charset getCharset() {
        return currentCharset;
    }

    public String getNormalizedContentForEditing() throws IOException {
        if (recordStructure) {
            return decodeWithRdwMarkers(currentCharset);
        } else {
            return content != null ? content : "";
        }
    }

    public InputStream toUploadStream(String editedContent) throws IOException {
        if (recordStructure) {
            return encodeFromRdwMarkers(editedContent, currentCharset);
        } else {
            return new ByteArrayInputStream(editedContent.getBytes(currentCharset));
        }
    }

    public String decodeWithRdwMarkers(Charset charset) throws IOException {
        if (rawBytes == null) return "";

        StringBuilder builder = new StringBuilder();
        int pos = 0;
        while (pos + 4 <= rawBytes.length) {
            int recordLength = ((rawBytes[pos] & 0xFF) << 8) | (rawBytes[pos + 1] & 0xFF);
            if (recordLength < 4 || pos + recordLength > rawBytes.length) {
                throw new IOException("Ungültiger RDW-Record an Position " + pos);
            }

            byte[] content = Arrays.copyOfRange(rawBytes, pos + 4, pos + recordLength);
            String line = new String(content, charset);

            builder.append("[").append(recordLength).append("]").append(line).append("\n");
            pos += recordLength;
        }
        return builder.toString();
    }

    public InputStream encodeFromRdwMarkers(String markedText, Charset charset) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferedReader reader = new BufferedReader(new StringReader(markedText));
        String line;

        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("[") || !line.contains("]")) {
                throw new IOException("Ungültige Formatierung: " + line);
            }

            int endIndex = line.indexOf("]");
            int declaredLength = Integer.parseInt(line.substring(1, endIndex));
            String content = line.substring(endIndex + 1);

            byte[] contentBytes = content.getBytes(charset);
            int actualLength = contentBytes.length + 4;
            if (actualLength != declaredLength) {
                throw new IOException("RDW-Länge stimmt nicht mit Inhalt überein: " + line);
            }

            out.write((byte) ((actualLength >> 8) & 0xFF));
            out.write((byte) (actualLength & 0xFF));
            out.write(0x00);
            out.write(0x00);
            out.write(contentBytes);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

}
