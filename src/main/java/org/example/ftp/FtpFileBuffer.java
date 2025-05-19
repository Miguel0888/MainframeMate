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
            return encodeFromRdwMarkers(updatedContent, currentCharset);
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

    // TODO: Implement this method to decode the text with RDW markers
    public String decodeWithRdwMarkers(Charset charset) {
        if (rawBytes == null || rawBytes.length < 4) {
            return "No data or too short.";
        }

        StringBuilder output = new StringBuilder();
        int pos = 0;
        int recordNum = 1;

        while (pos + 4 <= rawBytes.length) {
            byte b1 = rawBytes[pos];
            byte b2 = rawBytes[pos + 1];
            byte b3 = rawBytes[pos + 2];
            byte b4 = rawBytes[pos + 3];

            int length = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
            output.append(String.format("RDW [%d] @ pos %d → Bytes: %02X %02X %02X %02X → len=%d",
                    recordNum, pos, b1, b2, b3, b4, length));

            if (length < 4 || pos + length > rawBytes.length) {
                output.append(" ⚠ Possibly invalid or incomplete");
                pos++; // nur einen Schritt weiter, um nicht hängen zu bleiben
            } else {
                pos += length;
            }

            output.append("\n");
            recordNum++;
        }

        if (pos < rawBytes.length) {
            output.append(String.format("⚠ Remaining %d byte(s) after last RDW at pos %d\n",
                    rawBytes.length - pos, pos));
        }

        return output.toString();
    }


    // ToDo: Implement this method to encode the text with RDW markers
    public InputStream encodeFromRdwMarkers(String markedText, Charset charset) {
        // Nichts verändern, nur plain zurückgeben
        return new ByteArrayInputStream(markedText.getBytes(charset));
    }


}
