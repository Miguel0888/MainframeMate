package org.example.service;

import org.example.ftp.FtpFileBuffer;
import org.example.ftp.FtpManager;
import org.example.model.Settings;
import org.example.util.ByteUtil;
import org.example.util.SettingsManager;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;

public class FileContentService {

    private final FtpManager ftpManager;

    public FileContentService(FtpManager ftpManager) {
        this.ftpManager = ftpManager;
    }

    public String decodeWith(FtpFileBuffer buffer) {
        byte[] raw = buffer.getRawBytes();
        if (raw == null) return "";
        if (buffer.hasRecordStructure()) {
            return transformToLocal(raw, ftpManager.getCharset());
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
            return transformToRemote(content, settings, ftpManager.getCharset());
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
    private InputStream transformToRemote(String content, Settings settings, Charset charset) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String[] lines = content.split("\n", -1);
        byte[] lineMarker = parseHex(settings.lineEnding);

        try {
            for (String line : lines) {
                out.write(line.getBytes(charset));
                out.write(lineMarker);
            }

            // Add EOF Marker for Mainframes if present in settings:
            if (settings.fileEndMarker != null && !settings.fileEndMarker.isEmpty()) {
                byte[] endMarker = parseHex(settings.fileEndMarker);
                out.write(endMarker);
            }

        } catch (IOException e) {
            throw new UncheckedIOException("Fehler beim Erzeugen des OutputStreams", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    /**
     * Transformiert die Daten, sodass sie lokal dargestellt werden können.
     *
     * @param bytes
     * @param charset
     * @return
     */
    private String transformToLocal(byte[] bytes, Charset charset) {
        Settings settings = SettingsManager.load();

        byte[] recordMarker = parseHex(settings.lineEnding);
        byte[] endMarker = parseHex(settings.fileEndMarker);

        if (recordMarker.length == 0) {
            return new String(bytes, charset); // kein Marker konfiguriert
        }

        // Ersetze alle Record-Marker durch '\n'
        byte[] newline = "\n".getBytes(charset);
        byte[] transformed = ByteUtil.replaceAll(bytes, recordMarker, newline);

        // Entferne ggf. Dateiende-Marker
        if (endMarker.length > 0 && ByteUtil.endsWith(transformed, endMarker)) {
            transformed = Arrays.copyOf(transformed, transformed.length - endMarker.length);
        }

        if (settings.removeFinalNewline && transformed.length > 0 && transformed[transformed.length - 1] == '\n') {
            transformed = Arrays.copyOf(transformed, transformed.length - 1);
        }

        return new String(transformed, charset);
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
