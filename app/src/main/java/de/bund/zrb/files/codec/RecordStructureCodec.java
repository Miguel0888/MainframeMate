package de.bund.zrb.files.codec;

import de.bund.zrb.model.Settings;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Codec for MVS RECORD_STRUCTURE text files.
 *
 * Handles the transformation between:
 * - Remote bytes (with record markers FF01 and EOF marker FF02)
 * - Editor text (with newlines for human editing)
 *
 * This codec is the SINGLE source of truth for record/EOF marker handling.
 * No other code should interpret lineEnding/fileEndMarker/removeFinalNewline.
 *
 * Transformation order (Server → Editor):
 * 1. Remove ALL padding bytes (e.g., 0x00) from the entire stream
 * 2. Replace all record markers (FF01) with newlines
 * 3. Remove EOF marker (FF02) if present at end
 * 4. Optionally remove final newline
 *
 * Transformation order (Editor → Server):
 * 1. Split text by newlines
 * 2. Append record marker after each line
 * 3. Append EOF marker at the end
 */
public final class RecordStructureCodec {

    private RecordStructureCodec() {
        // Utility class
    }

    /**
     * Decode remote bytes to editor-friendly text.
     *
     * Transformation order (matching original FileContentService.transformToLocal):
     * 1. (Padding already removed during FTP download in CommonsNetFtpFileService.readAllBytes)
     * 2. Replace all occurrences of recordMarker (default FF01) with newline
     * 3. Remove EOF marker (default FF02) if present at end
     * 4. Optionally remove final newline if settings.removeFinalNewline is true
     *
     * @param remoteBytes the raw bytes from the FTP server (padding already removed)
     * @param charset the character encoding
     * @param settings the application settings containing marker configurations
     * @return editor-friendly text with proper newlines
     */
    public static String decodeForEditor(byte[] remoteBytes, Charset charset, Settings settings) {
        if (remoteBytes == null || remoteBytes.length == 0) {
            return "";
        }

        if (settings == null) {
            return new String(remoteBytes, charset);
        }

        byte[] recordMarker = parseHex(settings.lineEnding);
        byte[] endMarker = parseHex(settings.fileEndMarker);

        // Step 1: Replace record markers with newlines
        byte[] transformed = replaceBytes(remoteBytes, recordMarker, "\n".getBytes(charset));

        // Step 2: Remove EOF marker if present at end
        if (endMarker != null && endMarker.length > 0 && endsWith(transformed, endMarker)) {
            transformed = Arrays.copyOf(transformed, transformed.length - endMarker.length);
        }

        // Step 3: Optionally remove final newline
        // Note: This checks for byte 0x0A directly (works for ISO-8859-1/UTF-8)
        if (settings.removeFinalNewline && transformed.length > 0) {
            if (transformed[transformed.length - 1] == (byte) 0x0A) {
                transformed = Arrays.copyOf(transformed, transformed.length - 1);
            }
        }

        return new String(transformed, charset);
    }


    /**
     * Encode editor text back to remote byte format.
     *
     * Transformation (matching original FileContentService.transformToRemote):
     * 1. Split text by newlines (including trailing empty lines via split("\n", -1))
     * 2. Append recordMarker after EACH line (including the last one)
     * 3. Append EOF marker at the end if configured
     *
     * Note: Padding is NOT added back. The mainframe/FTP handles that.
     *
     * @param editorText the text from the editor
     * @param charset the character encoding
     * @param settings the application settings containing marker configurations
     * @return bytes in remote format with record markers
     */
    public static byte[] encodeForRemote(String editorText, Charset charset, Settings settings) {
        if (editorText == null) {
            editorText = "";
        }

        if (settings == null) {
            return editorText.getBytes(charset);
        }

        byte[] recordMarker = parseHex(settings.lineEnding);
        byte[] endMarker = parseHex(settings.fileEndMarker);

        // Split preserving trailing empty strings
        String[] lines = editorText.split("\n", -1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (int i = 0; i < lines.length; i++) {
            byte[] lineBytes = lines[i].getBytes(charset);
            out.write(lineBytes, 0, lineBytes.length);

            // Append record marker after each line (except possibly the last if removeFinalNewline is relevant)
            // For MVS, every line gets a record marker
            if (recordMarker != null && recordMarker.length > 0) {
                out.write(recordMarker, 0, recordMarker.length);
            }
        }

        // Append EOF marker if configured
        if (endMarker != null && endMarker.length > 0) {
            out.write(endMarker, 0, endMarker.length);
        }

        return out.toByteArray();
    }

    /**
     * Check if this is a record structure based on settings and context.
     */
    public static boolean isRecordStructure(Settings settings) {
        if (settings == null) {
            return false;
        }
        // Record structure is used when ftpFileStructure is RECORD_STRUCTURE
        // or when on MVS (determined elsewhere)
        return settings.ftpFileStructure != null &&
               settings.ftpFileStructure.name().equals("RECORD_STRUCTURE");
    }

    /**
     * Parse a hex string like "FF01" into bytes.
     * Returns null if the input is null or empty.
     */
    public static byte[] parseHex(String hex) {
        if (hex == null || hex.trim().isEmpty()) {
            return null;
        }

        String clean = hex.trim().replaceAll("\\s+", "");
        if (clean.length() % 2 != 0) {
            return null;
        }

        byte[] result = new byte[clean.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(clean.charAt(i * 2), 16);
            int low = Character.digit(clean.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    /**
     * Replace all occurrences of a byte sequence with another.
     */
    private static byte[] replaceBytes(byte[] source, byte[] search, byte[] replacement) {
        if (source == null || source.length == 0) {
            return source;
        }
        if (search == null || search.length == 0) {
            return source;
        }
        if (replacement == null) {
            replacement = new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;

        while (i < source.length) {
            // Check if search pattern matches at current position
            if (i + search.length <= source.length && matchesAt(source, i, search)) {
                // Write replacement
                out.write(replacement, 0, replacement.length);
                i += search.length;
            } else {
                // Write single byte
                out.write(source[i]);
                i++;
            }
        }

        return out.toByteArray();
    }

    /**
     * Check if pattern matches at given position in source.
     */
    private static boolean matchesAt(byte[] source, int offset, byte[] pattern) {
        if (offset + pattern.length > source.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (source[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if source ends with the given suffix.
     */
    private static boolean endsWith(byte[] source, byte[] suffix) {
        if (source == null || suffix == null) {
            return false;
        }
        if (suffix.length > source.length) {
            return false;
        }
        int offset = source.length - suffix.length;
        return matchesAt(source, offset, suffix);
    }
}

