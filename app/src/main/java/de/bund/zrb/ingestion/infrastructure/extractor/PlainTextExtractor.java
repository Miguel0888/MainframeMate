package de.bund.zrb.ingestion.infrastructure.extractor;

import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.port.TextExtractor;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extractor for plain text files.
 * Handles character encoding detection and conversion.
 */
public class PlainTextExtractor implements TextExtractor {

    private static final List<String> SUPPORTED_MIME_TYPES = Arrays.asList(
            "text/plain",
            "text/csv",
            "text/tab-separated-values"
    );

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String baseMime = mimeType.contains(";") ? mimeType.substring(0, mimeType.indexOf(';')).trim() : mimeType;
        return SUPPORTED_MIME_TYPES.contains(baseMime);
    }

    @Override
    public int getPriority() {
        return 100; // High priority for text/plain
    }

    @Override
    public String getName() {
        return "PlainTextExtractor";
    }

    @Override
    public ExtractionResult extract(DocumentSource source, DetectionResult detection) {
        List<String> warnings = new ArrayList<>();

        try {
            byte[] bytes = source.getBytes();
            if (bytes == null || bytes.length == 0) {
                return ExtractionResult.failure("Datei ist leer", getName());
            }

            // Determine charset
            Charset charset = determineCharset(bytes, detection, warnings);

            // Convert bytes to string
            String text = new String(bytes, charset);

            return ExtractionResult.success(text, warnings, getName());

        } catch (Exception e) {
            return ExtractionResult.failure("Fehler beim Lesen der Textdatei: " + e.getMessage(), getName());
        }
    }

    private Charset determineCharset(byte[] bytes, DetectionResult detection, List<String> warnings) {
        // 1. Try charset from detection result
        if (detection != null && detection.getCharset() != null) {
            try {
                return Charset.forName(detection.getCharset());
            } catch (Exception e) {
                warnings.add("Charset '" + detection.getCharset() + "' nicht unterstützt, verwende Fallback");
            }
        }

        // 2. Try BOM detection
        Charset bomCharset = detectBom(bytes);
        if (bomCharset != null) {
            return bomCharset;
        }

        // 3. Try UTF-8 validation
        if (isValidUtf8(bytes)) {
            return StandardCharsets.UTF_8;
        }

        // 4. Fallback to ISO-8859-1 (Latin-1) - accepts all byte values
        warnings.add("Charset konnte nicht sicher erkannt werden, verwende ISO-8859-1");
        return StandardCharsets.ISO_8859_1;
    }

    private Charset detectBom(byte[] bytes) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        return null;
    }

    private boolean isValidUtf8(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;

            if (b < 0x80) {
                // ASCII
                i++;
            } else if ((b & 0xE0) == 0xC0) {
                // 2-byte sequence
                if (i + 1 >= bytes.length || (bytes[i + 1] & 0xC0) != 0x80) {
                    return false;
                }
                i += 2;
            } else if ((b & 0xF0) == 0xE0) {
                // 3-byte sequence
                if (i + 2 >= bytes.length ||
                    (bytes[i + 1] & 0xC0) != 0x80 ||
                    (bytes[i + 2] & 0xC0) != 0x80) {
                    return false;
                }
                i += 3;
            } else if ((b & 0xF8) == 0xF0) {
                // 4-byte sequence
                if (i + 3 >= bytes.length ||
                    (bytes[i + 1] & 0xC0) != 0x80 ||
                    (bytes[i + 2] & 0xC0) != 0x80 ||
                    (bytes[i + 3] & 0xC0) != 0x80) {
                    return false;
                }
                i += 4;
            } else {
                // Invalid UTF-8 start byte
                return false;
            }
        }
        return true;
    }
}

