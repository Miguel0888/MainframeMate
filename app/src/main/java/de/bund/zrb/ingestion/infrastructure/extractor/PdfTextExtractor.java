package de.bund.zrb.ingestion.infrastructure.extractor;

import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.port.TextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extractor for PDF files using Apache PDFBox.
 * Extracts text content while preserving paragraph structure.
 */
public class PdfTextExtractor implements TextExtractor {

    private static final String MIME_TYPE = "application/pdf";

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String baseMime = mimeType.contains(";") ? mimeType.substring(0, mimeType.indexOf(';')).trim() : mimeType;
        return MIME_TYPE.equals(baseMime);
    }

    @Override
    public int getPriority() {
        return 100; // High priority for PDF
    }

    @Override
    public String getName() {
        return "PdfTextExtractor";
    }

    @Override
    public ExtractionResult extract(DocumentSource source, DetectionResult detection) {
        List<String> warnings = new ArrayList<>();
        Map<String, String> metadata = new HashMap<>();

        PDDocument document = null;
        try {
            byte[] bytes = source.getBytes();
            if (bytes == null || bytes.length == 0) {
                return ExtractionResult.failure("Datei ist leer", getName());
            }

            // Load PDF document
            document = PDDocument.load(new ByteArrayInputStream(bytes));

            // Check if document is encrypted
            if (document.isEncrypted()) {
                warnings.add("PDF ist verschlüsselt - Textextraktion möglicherweise eingeschränkt");
            }

            // Extract metadata
            PDDocumentInformation info = document.getDocumentInformation();
            if (info != null) {
                if (info.getTitle() != null && !info.getTitle().isEmpty()) {
                    metadata.put("title", info.getTitle());
                }
                if (info.getAuthor() != null && !info.getAuthor().isEmpty()) {
                    metadata.put("author", info.getAuthor());
                }
                if (info.getSubject() != null && !info.getSubject().isEmpty()) {
                    metadata.put("subject", info.getSubject());
                }
                if (info.getCreator() != null && !info.getCreator().isEmpty()) {
                    metadata.put("creator", info.getCreator());
                }
            }

            // Add page count
            int pageCount = document.getNumberOfPages();
            metadata.put("pageCount", String.valueOf(pageCount));

            // Extract text
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setAddMoreFormatting(true);

            String text = stripper.getText(document);

            // Check if text is empty (might be image-only PDF)
            if (text == null || text.trim().isEmpty()) {
                warnings.add("PDF enthält möglicherweise nur Bilder/gescannte Seiten - kein Text extrahiert");
                return ExtractionResult.success("", warnings, metadata, getName());
            }

            return ExtractionResult.success(text, warnings, metadata, getName());

        } catch (IOException e) {
            return ExtractionResult.failure("Fehler beim Lesen der PDF-Datei: " + e.getMessage(), getName());
        } catch (Exception e) {
            return ExtractionResult.failure("Unerwarteter Fehler bei der PDF-Verarbeitung: " + e.getMessage(), getName());
        } finally {
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

