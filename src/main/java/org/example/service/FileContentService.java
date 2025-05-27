package org.example.service;

import org.example.ftp.FtpFileBuffer;
import org.example.ftp.FtpManager;
import org.example.model.Settings;
import org.example.util.SettingsManager;

import java.io.*;
import java.nio.charset.Charset;

public class FileContentService {

    private final FtpManager ftpManager;

    public FileContentService(FtpManager ftpManager) {
        this.ftpManager = ftpManager;
    }

    public String decodeWith(FtpFileBuffer buffer) {
        byte[] raw = buffer.getRawBytes();
        if (raw == null) return "";
        if (buffer.hasRecordStructure()) {
            return mapLineEndings(raw, ftpManager.getCharset());
        }
        return new String(raw, ftpManager.getCharset());
    }

    public boolean isUnchanged(FtpFileBuffer original, String newContent) {
        InputStream newStream = createCommitStream(newContent, original.hasRecordStructure());
        try {
            FtpFileBuffer recreated = original.withContent(newStream);
            return original.equals(recreated);
        } catch (IOException e) {
            throw new UncheckedIOException("Vergleich fehlgeschlagen", e);
        }
    }

    public InputStream createCommitStream(String content, boolean recordStructure) {
        Settings settings = SettingsManager.load();
        if (recordStructure) {
            return buildLineMarkedStream(content, settings, ftpManager.getCharset());
        }
        return new ByteArrayInputStream(content.getBytes(ftpManager.getCharset()));
    }

    /**
     * Erzeugt einen InputStream, der Zeilen mit einem konfigurierten Zeilenende-Marker versieht. Der Stream ist nur
     * zum Speichern gedacht. Zum Lesen muss der umgekehrte Prozess verwendet werden.
     *
     * @param content
     * @param settings
     * @param charset
     * @return
     */
    private InputStream buildLineMarkedStream(String content, Settings settings, Charset charset) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String[] lines = content.split("\n", -1);
        byte[] lineMarker = parseHex(settings.lineEnding);

        try {
            for (String line : lines) {
                out.write(line.getBytes(charset));
                out.write(lineMarker);
            }

            if (settings.fileEndMarker != null && !settings.fileEndMarker.isEmpty()) {
                byte[] endMarker = parseHex(settings.fileEndMarker);
                out.write(endMarker);
            }

        } catch (IOException e) {
            throw new UncheckedIOException("Fehler beim Erzeugen des OutputStreams", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    private String mapLineEndings(byte[] bytes, Charset charset) {
        Settings settings = SettingsManager.load();
        String markerHex = settings.lineEnding;

        if (markerHex == null || markerHex.length() != 4) {
            return new String(bytes, charset);
        }

        byte high = (byte) Integer.parseInt(markerHex.substring(0, 2), 16);
        byte low = (byte) Integer.parseInt(markerHex.substring(2, 4), 16);

        ByteArrayOutputStream transformed = new ByteArrayOutputStream();
        for (int i = 0; i < bytes.length; i++) {
            if (i + 1 < bytes.length && bytes[i] == high && bytes[i + 1] == low) {
                transformed.write('\n');
                i++; // Marker überspringen
            } else {
                transformed.write(bytes[i]);
            }
        }

        return new String(transformed.toByteArray(), charset);
    }

    private byte[] parseHex(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex-Zeichenfolge muss gerade Länge haben: " + hex);
        }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }

        return bytes;
    }
}
