package org.example.ftp;

import org.apache.commons.net.ftp.FTPFile;
import org.example.util.SettingsManager;

import java.io.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        return decodeWithHexDump(charset);
    }


    private String toBitString(byte b) {
        return String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
    }



    // ToDo: Implement this method to encode the text with RDW markers
    public InputStream encodeFromRdwMarkers(String markedText, Charset charset) {
        // Nichts verändern, nur plain zurückgeben
        return new ByteArrayInputStream(markedText.getBytes(charset));
    }



    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // DEBUGGING
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Gibt den Inhalt des Buffers als Hexdump aus.
     * @param charset Der Zeichensatz, der für die Umwandlung in einen String verwendet werden soll.
     * @return Der Inhalt des Buffers als String.
     */
    public String decodeWithHexDump(Charset charset) {
        if (rawBytes == null || rawBytes.length == 0) {
            System.out.println("Keine Daten vorhanden.");
            return "";
        }

        // Terminalausgabe: Hexdump
        int offset = 0;
        while (offset < rawBytes.length) {
            int len = Math.min(16, rawBytes.length - offset);
            System.out.printf("%08X  ", offset);

            // Hex-Werte
            for (int i = 0; i < 16; i++) {
                if (i < len) {
                    System.out.printf("%02X ", rawBytes[offset + i]);
                } else {
                    System.out.print("   ");
                }
                if (i == 7) System.out.print(" ");
            }

            // ASCII-Darstellung
            System.out.print(" |");
            for (int i = 0; i < len; i++) {
                byte b = rawBytes[offset + i];
                char c = (char) (b & 0xFF);
                if (c >= 32 && c <= 126) {
                    System.out.print(c);
                } else {
                    System.out.print(".");
                }
            }
            System.out.println("|");

            offset += len;
        }

        // Rückgabe: Originalinhalt als String (unverändert)
        return new String(rawBytes, charset);
    }
}
