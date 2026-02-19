package de.bund.zrb.ingestion.usecase;

import de.bund.zrb.ingestion.config.IngestionConfig;
import de.bund.zrb.ingestion.infrastructure.DefaultAcceptancePolicy;
import de.bund.zrb.ingestion.infrastructure.ExtractorRegistry;
import de.bund.zrb.ingestion.infrastructure.TextNormalizer;
import de.bund.zrb.ingestion.infrastructure.TikaContentTypeDetector;
import de.bund.zrb.ingestion.model.AcceptanceDecision;
import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.port.ContentTypeDetector;
import de.bund.zrb.ingestion.port.DocumentAcceptancePolicy;
import de.bund.zrb.ingestion.port.TextExtractor;
import de.bund.zrb.ingestion.port.TextPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main use case for extracting text from documents.
 * Orchestrates the pipeline: Detection -> Acceptance -> Extraction -> Normalization.
 */
public class ExtractTextFromDocumentUseCase {

    private static final Logger LOG = Logger.getLogger(ExtractTextFromDocumentUseCase.class.getName());

    private final IngestionConfig config;
    private final ContentTypeDetector detector;
    private final DocumentAcceptancePolicy acceptancePolicy;
    private final ExtractorRegistry extractorRegistry;
    private final TextPostProcessor normalizer;
    private final ExecutorService executor;

    /**
     * Create use case with default components.
     */
    public ExtractTextFromDocumentUseCase() {
        this(new IngestionConfig());
    }

    /**
     * Create use case with custom configuration.
     */
    public ExtractTextFromDocumentUseCase(IngestionConfig config) {
        this(config,
             new TikaContentTypeDetector(),
             new DefaultAcceptancePolicy(config),
             new ExtractorRegistry(),
             new TextNormalizer());
    }

    /**
     * Create use case with all custom components.
     */
    public ExtractTextFromDocumentUseCase(IngestionConfig config,
                                           ContentTypeDetector detector,
                                           DocumentAcceptancePolicy acceptancePolicy,
                                           ExtractorRegistry extractorRegistry,
                                           TextPostProcessor normalizer) {
        this.config = config;
        this.detector = detector;
        this.acceptancePolicy = acceptancePolicy;
        this.extractorRegistry = extractorRegistry;
        this.normalizer = normalizer;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DocumentExtractor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Extract text from a document.
     * This is the main entry point for the pipeline.
     *
     * @param source the document source
     * @return extraction result with plaintext or error
     */
    public ExtractionResult execute(DocumentSource source) {
        List<String> allWarnings = new ArrayList<>();

        try {
            // Stage A: Detection
            LOG.fine("Stage A: Detecting content type...");
            DetectionResult detection = detector.detect(source);
            LOG.fine("Detected: " + detection);

            // Stage A: Acceptance Check
            LOG.fine("Stage A: Evaluating acceptance policy...");
            AcceptanceDecision decision = acceptancePolicy.evaluate(source, detection);
            if (decision.isRejected()) {
                LOG.info("Document rejected: " + decision.getReason());
                return ExtractionResult.failure("Dokument abgelehnt: " + decision.getReason(), getName());
            }

            // Stage B: Find extractor
            LOG.fine("Stage B: Finding extractor for " + detection.getMimeType());
            TextExtractor extractor = extractorRegistry.findExtractor(detection.getBaseMimeType());
            LOG.fine("Using extractor: " + extractor.getName());

            // Stage C: Extraction with timeout
            LOG.fine("Stage C: Extracting text...");
            ExtractionResult extractionResult = executeWithTimeout(
                    () -> extractor.extract(source, detection),
                    config.getTimeoutPerExtractionMs()
            );

            // Handle extraction failure with fallback
            if (!extractionResult.isSuccess() && config.isEnableFallbackOnExtractorFailure()) {
                LOG.warning("Primary extractor failed, trying fallback: " + extractionResult.getErrorMessage());
                allWarnings.add("Primärer Extractor fehlgeschlagen: " + extractionResult.getErrorMessage());

                TextExtractor fallback = extractorRegistry.getFallbackExtractor();
                if (fallback != extractor) {
                    extractionResult = executeWithTimeout(
                            () -> fallback.extract(source, detection),
                            config.getTimeoutPerExtractionMs()
                    );
                }
            }

            if (!extractionResult.isSuccess()) {
                return extractionResult;
            }

            // Collect warnings
            allWarnings.addAll(extractionResult.getWarnings());

            // Stage D: Normalization
            LOG.fine("Stage D: Normalizing text...");
            String normalizedText = normalizer.process(extractionResult.getPlainText());

            // Build final result
            ExtractionResult finalResult = ExtractionResult.success(
                    normalizedText,
                    allWarnings,
                    extractionResult.getMetadata(),
                    extractionResult.getExtractorName()
            );

            LOG.fine("Extraction complete: " + normalizedText.length() + " chars");
            return finalResult;

        } catch (TimeoutException e) {
            LOG.warning("Extraction timed out");
            return ExtractionResult.failure("Zeitüberschreitung bei der Textextraktion", getName());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Extraction failed", e);
            return ExtractionResult.failure("Fehler bei der Textextraktion: " + e.getMessage(), getName());
        }
    }

    /**
     * Execute extraction with timeout.
     */
    private ExtractionResult executeWithTimeout(Callable<ExtractionResult> task, long timeoutMs)
            throws TimeoutException {
        Future<ExtractionResult> future = executor.submit(task);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExtractionResult.failure("Extraktion unterbrochen", getName());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return ExtractionResult.failure(
                    "Extraktionsfehler: " + (cause != null ? cause.getMessage() : e.getMessage()),
                    getName()
            );
        }
    }

    /**
     * Extract text synchronously (without timeout).
     * Use this for simple cases or testing.
     */
    public ExtractionResult executeSync(DocumentSource source) {
        List<String> allWarnings = new ArrayList<>();

        // Stage A: Detection
        DetectionResult detection = detector.detect(source);

        // Stage A: Acceptance Check
        AcceptanceDecision decision = acceptancePolicy.evaluate(source, detection);
        if (decision.isRejected()) {
            return ExtractionResult.failure("Dokument abgelehnt: " + decision.getReason(), getName());
        }

        // Stage B: Find extractor
        TextExtractor extractor = extractorRegistry.findExtractor(detection.getBaseMimeType());

        // Stage C: Extraction
        ExtractionResult extractionResult = extractor.extract(source, detection);

        // Handle extraction failure with fallback
        if (!extractionResult.isSuccess() && config.isEnableFallbackOnExtractorFailure()) {
            allWarnings.add("Primärer Extractor fehlgeschlagen: " + extractionResult.getErrorMessage());
            TextExtractor fallback = extractorRegistry.getFallbackExtractor();
            if (fallback != extractor) {
                extractionResult = fallback.extract(source, detection);
            }
        }

        if (!extractionResult.isSuccess()) {
            return extractionResult;
        }

        allWarnings.addAll(extractionResult.getWarnings());

        // Stage D: Normalization
        String normalizedText = normalizer.process(extractionResult.getPlainText());

        return ExtractionResult.success(
                normalizedText,
                allWarnings,
                extractionResult.getMetadata(),
                extractionResult.getExtractorName()
        );
    }

    /**
     * Convenience method to extract text from bytes with filename hint.
     */
    public ExtractionResult extractFromBytes(byte[] bytes, String filename) {
        return execute(DocumentSource.fromBytes(bytes, filename));
    }

    /**
     * Convenience method to extract text from bytes.
     */
    public ExtractionResult extractFromBytes(byte[] bytes) {
        return execute(DocumentSource.fromBytes(bytes));
    }

    private String getName() {
        return "ExtractTextFromDocumentUseCase";
    }

    /**
     * Get the extractor registry for customization.
     */
    public ExtractorRegistry getExtractorRegistry() {
        return extractorRegistry;
    }

    /**
     * Shutdown the executor.
     */
    public void shutdown() {
        executor.shutdownNow();
    }
}

