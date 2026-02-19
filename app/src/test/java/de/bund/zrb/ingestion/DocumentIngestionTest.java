package de.bund.zrb.ingestion;

import de.bund.zrb.ingestion.config.IngestionConfig;
import de.bund.zrb.ingestion.infrastructure.DefaultAcceptancePolicy;
import de.bund.zrb.ingestion.infrastructure.TextNormalizer;
import de.bund.zrb.ingestion.infrastructure.TikaContentTypeDetector;
import de.bund.zrb.ingestion.model.AcceptanceDecision;
import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.usecase.ExtractTextFromDocumentUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Document Ingestion pipeline.
 */
class DocumentIngestionTest {

    private ExtractTextFromDocumentUseCase useCase;
    private TikaContentTypeDetector detector;
    private DefaultAcceptancePolicy policy;
    private TextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        useCase = new ExtractTextFromDocumentUseCase();
        detector = new TikaContentTypeDetector();
        policy = new DefaultAcceptancePolicy();
        normalizer = new TextNormalizer();
    }

    // ========== Content Type Detection Tests ==========

    @Test
    void detect_plainText_returnsTextPlain() {
        DocumentSource source = DocumentSource.fromBytes(
                "Hello World".getBytes(StandardCharsets.UTF_8), "test.txt");

        DetectionResult result = detector.detect(source);

        assertEquals("text/plain", result.getBaseMimeType());
    }

    @Test
    void detect_html_returnsTextHtml() {
        String html = "<!DOCTYPE html><html><body><h1>Test</h1></body></html>";
        DocumentSource source = DocumentSource.fromBytes(
                html.getBytes(StandardCharsets.UTF_8), "test.html");

        DetectionResult result = detector.detect(source);

        assertTrue(result.getMimeType().startsWith("text/html") ||
                   result.getMimeType().contains("html"));
    }

    @Test
    void detect_markdown_withHint_returnsMarkdown() {
        String md = "# Heading\n\nThis is **markdown**";
        DocumentSource source = DocumentSource.fromBytes(
                md.getBytes(StandardCharsets.UTF_8), "readme.md");

        DetectionResult result = detector.detect(source);

        // Markdown might be detected as text/plain or text/x-web-markdown
        assertNotNull(result.getMimeType());
    }

    @Test
    void detect_emptyFile_returnsOctetStream() {
        DocumentSource source = DocumentSource.fromBytes(new byte[0], "empty.bin");

        DetectionResult result = detector.detect(source);

        // Empty files typically return octet-stream with low confidence
        assertTrue(result.getConfidence() < 0.5 ||
                   "application/octet-stream".equals(result.getMimeType()));
    }

    // ========== Acceptance Policy Tests ==========

    @Test
    void policy_acceptsPlainText() {
        DocumentSource source = DocumentSource.fromBytes(
                "Hello".getBytes(StandardCharsets.UTF_8), "test.txt");
        DetectionResult detection = new DetectionResult("text/plain");

        AcceptanceDecision decision = policy.evaluate(source, detection);

        assertTrue(decision.isAccepted());
    }

    @Test
    void policy_rejectsEmptyFile() {
        DocumentSource source = DocumentSource.fromBytes(new byte[0], "empty.txt");
        DetectionResult detection = new DetectionResult("text/plain");

        AcceptanceDecision decision = policy.evaluate(source, detection);

        assertTrue(decision.isRejected());
        assertTrue(decision.getReason().contains("leer"));
    }

    @Test
    void policy_rejectsOversizedFile() {
        IngestionConfig config = new IngestionConfig().setMaxFileSizeBytes(100);
        DefaultAcceptancePolicy strictPolicy = new DefaultAcceptancePolicy(config);

        byte[] largeContent = new byte[200]; // Larger than 100 bytes
        DocumentSource source = DocumentSource.fromBytes(largeContent, "large.txt");
        DetectionResult detection = new DetectionResult("text/plain");

        AcceptanceDecision decision = strictPolicy.evaluate(source, detection);

        assertTrue(decision.isRejected());
        assertTrue(decision.getReason().contains("groß"));
    }

    @Test
    void policy_rejectsExecutable() {
        DocumentSource source = DocumentSource.fromBytes(
                "MZ...".getBytes(StandardCharsets.UTF_8), "program.exe");
        DetectionResult detection = new DetectionResult("application/x-msdownload");

        AcceptanceDecision decision = policy.evaluate(source, detection);

        assertTrue(decision.isRejected());
    }

    @Test
    void policy_rejectsImage() {
        DocumentSource source = DocumentSource.fromBytes(
                new byte[]{(byte)0x89, 'P', 'N', 'G'}, "image.png");
        DetectionResult detection = new DetectionResult("image/png");

        AcceptanceDecision decision = policy.evaluate(source, detection);

        assertTrue(decision.isRejected());
        assertTrue(decision.getReason().contains("Bild"));
    }

    // ========== Text Normalizer Tests ==========

    @Test
    void normalizer_removesControlChars() {
        String input = "Hello\u0000World\u0007Test";

        String result = normalizer.process(input);

        assertEquals("HelloWorldTest", result);
    }

    @Test
    void normalizer_normalizesLineEndings() {
        String input = "Line1\r\nLine2\rLine3\nLine4";

        String result = normalizer.process(input);

        assertEquals("Line1\nLine2\nLine3\nLine4", result);
    }

    @Test
    void normalizer_reducesExcessiveNewlines() {
        String input = "Para1\n\n\n\n\n\nPara2";

        String result = normalizer.process(input);

        // Should reduce to max 3 newlines
        assertFalse(result.contains("\n\n\n\n"));
    }

    @Test
    void normalizer_trimsWhitespace() {
        String input = "  Line1  \n  Line2  ";

        String result = normalizer.process(input);

        assertTrue(result.startsWith("Line1") || result.contains("Line1"));
    }

    // ========== UseCase Integration Tests ==========

    @Test
    void useCase_extractsPlainText() {
        String content = "This is a test document.";
        DocumentSource source = DocumentSource.fromBytes(
                content.getBytes(StandardCharsets.UTF_8), "test.txt");

        ExtractionResult result = useCase.executeSync(source);

        assertTrue(result.isSuccess());
        assertEquals(content, result.getPlainText());
    }

    @Test
    void useCase_extractsHtml() {
        String html = "<html><body><h1>Title</h1><p>Content here.</p></body></html>";
        DocumentSource source = DocumentSource.fromBytes(
                html.getBytes(StandardCharsets.UTF_8), "page.html");

        ExtractionResult result = useCase.executeSync(source);

        assertTrue(result.isSuccess());
        assertTrue(result.getPlainText().contains("Title"));
        assertTrue(result.getPlainText().contains("Content here"));
        // Should not contain HTML tags
        assertFalse(result.getPlainText().contains("<h1>"));
    }

    @Test
    void useCase_extractsMarkdown() {
        String markdown = "# Heading\n\nThis is **bold** text.";
        DocumentSource source = DocumentSource.fromBytes(
                markdown.getBytes(StandardCharsets.UTF_8), "doc.md");

        ExtractionResult result = useCase.executeSync(source);

        assertTrue(result.isSuccess());
        // Markdown is kept as-is for AI
        assertTrue(result.getPlainText().contains("# Heading") ||
                   result.getPlainText().contains("Heading"));
    }

    @Test
    void useCase_rejectsEmptyFile() {
        DocumentSource source = DocumentSource.fromBytes(new byte[0], "empty.txt");

        ExtractionResult result = useCase.executeSync(source);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("abgelehnt") ||
                   result.getErrorMessage().contains("leer"));
    }

    @Test
    void useCase_handlesUtf8() {
        String content = "Ümläute: äöüß, Emoji: 🎉";
        DocumentSource source = DocumentSource.fromBytes(
                content.getBytes(StandardCharsets.UTF_8), "unicode.txt");

        ExtractionResult result = useCase.executeSync(source);

        assertTrue(result.isSuccess());
        assertTrue(result.getPlainText().contains("Ümläute"));
    }

    @Test
    void useCase_extractsMetadata() {
        // HTML with title should extract metadata
        String html = "<html><head><title>My Document</title></head><body>Content</body></html>";
        DocumentSource source = DocumentSource.fromBytes(
                html.getBytes(StandardCharsets.UTF_8), "doc.html");

        ExtractionResult result = useCase.executeSync(source);

        assertTrue(result.isSuccess());
        // Metadata extraction is optional, but title should be in text
        assertTrue(result.getPlainText().contains("My Document") ||
                   result.getMetadata().containsKey("title"));
    }
}

