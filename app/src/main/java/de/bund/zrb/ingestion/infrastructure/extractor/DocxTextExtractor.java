package de.bund.zrb.ingestion.infrastructure.extractor;

import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.port.TextExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extractor for DOCX files using Apache POI.
 * Extracts text content including paragraphs, tables, and headers/footers.
 */
public class DocxTextExtractor implements TextExtractor {

    private static final List<String> SUPPORTED_MIME_TYPES = Arrays.asList(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String baseMime = mimeType.contains(";") ? mimeType.substring(0, mimeType.indexOf(';')).trim() : mimeType;
        return SUPPORTED_MIME_TYPES.contains(baseMime);
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public String getName() {
        return "DocxTextExtractor";
    }

    @Override
    public ExtractionResult extract(DocumentSource source, DetectionResult detection) {
        List<String> warnings = new ArrayList<>();
        Map<String, String> metadata = new HashMap<>();

        XWPFDocument document = null;
        XWPFWordExtractor extractor = null;

        try {
            byte[] bytes = source.getBytes();
            if (bytes == null || bytes.length == 0) {
                return ExtractionResult.failure("Datei ist leer", getName());
            }

            // Load DOCX document
            document = new XWPFDocument(new ByteArrayInputStream(bytes));

            // Extract text using POI extractor
            extractor = new XWPFWordExtractor(document);
            String text = extractor.getText();

            if (text == null || text.trim().isEmpty()) {
                warnings.add("Dokument enthaelt keinen Text");
                return ExtractionResult.success("", warnings, metadata, getName());
            }

            return ExtractionResult.success(text, warnings, metadata, getName());

        } catch (IOException e) {
            return ExtractionResult.failure("Fehler beim Lesen der DOCX-Datei: " + e.getMessage(), getName());
        } catch (Exception e) {
            return ExtractionResult.failure("Unerwarteter Fehler bei der DOCX-Verarbeitung: " + e.getMessage(), getName());
        } finally {
            if (extractor != null) {
                try {
                    extractor.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    // Ignore close errors
                }
            }
        }
    }
}

