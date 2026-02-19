package de.bund.zrb.ingestion.infrastructure.extractor;

import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.port.TextExtractor;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback extractor using Apache Tika.
 * Handles any document type that Tika supports but we don't have specialized extractors for.
 */
public class TikaFallbackExtractor implements TextExtractor {

    private final Tika tika;
    private final AutoDetectParser parser;
    private final int maxContentLength;

    public TikaFallbackExtractor() {
        this(10 * 1024 * 1024); // 10 MB default max content
    }

    public TikaFallbackExtractor(int maxContentLength) {
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
        this.maxContentLength = maxContentLength;
    }

    @Override
    public boolean supports(String mimeType) {
        // Fallback supports everything
        return true;
    }

    @Override
    public int getPriority() {
        return -100; // Lowest priority - only used when no specialized extractor matches
    }

    @Override
    public String getName() {
        return "TikaFallbackExtractor";
    }

    @Override
    public ExtractionResult extract(DocumentSource source, DetectionResult detection) {
        List<String> warnings = new ArrayList<>();
        Map<String, String> metadata = new HashMap<>();

        try {
            byte[] bytes = source.getBytes();
            if (bytes == null || bytes.length == 0) {
                return ExtractionResult.failure("Datei ist leer", getName());
            }

            // Prepare metadata
            Metadata tikaMetadata = new Metadata();
            if (source.getResourceName() != null) {
                tikaMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, source.getResourceName());
            }
            if (detection != null && detection.getMimeType() != null) {
                tikaMetadata.set(Metadata.CONTENT_TYPE, detection.getMimeType());
            }

            // Create handler with size limit
            BodyContentHandler handler = new BodyContentHandler(maxContentLength);

            // Parse
            ParseContext context = new ParseContext();
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                parser.parse(is, handler, tikaMetadata, context);
            }

            // Extract text
            String text = handler.toString();

            // Extract metadata
            String title = tikaMetadata.get(TikaCoreProperties.TITLE);
            if (title != null && !title.isEmpty()) {
                metadata.put("title", title);
            }

            String author = tikaMetadata.get(TikaCoreProperties.CREATOR);
            if (author != null && !author.isEmpty()) {
                metadata.put("author", author);
            }

            String contentType = tikaMetadata.get(Metadata.CONTENT_TYPE);
            if (contentType != null) {
                metadata.put("contentType", contentType);
            }

            // Add warning that this is fallback extraction
            warnings.add("Verwendet Tika-Fallback-Extraktion (kein spezialisierter Extractor verfügbar)");

            if (text == null || text.trim().isEmpty()) {
                warnings.add("Keine Textinhalte extrahiert");
                return ExtractionResult.success("", warnings, metadata, getName());
            }

            return ExtractionResult.success(text, warnings, metadata, getName());

        } catch (TikaException e) {
            return ExtractionResult.failure("Tika-Extraktionsfehler: " + e.getMessage(), getName());
        } catch (IOException e) {
            return ExtractionResult.failure("IO-Fehler bei der Extraktion: " + e.getMessage(), getName());
        } catch (Exception e) {
            return ExtractionResult.failure("Unerwarteter Fehler: " + e.getMessage(), getName());
        }
    }

    /**
     * Simple extraction method that returns just the text.
     */
    public String extractText(byte[] bytes, String filename) {
        try {
            if (filename != null && !filename.isEmpty()) {
                Metadata metadata = new Metadata();
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
                return tika.parseToString(new ByteArrayInputStream(bytes), metadata);
            }
            return tika.parseToString(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }
}

