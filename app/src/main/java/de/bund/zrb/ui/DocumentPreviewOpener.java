package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.ingestion.config.IngestionConfig;
import de.bund.zrb.ingestion.infrastructure.render.RendererRegistry;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.port.render.RenderFormat;
import de.bund.zrb.ingestion.usecase.BuildDocumentFromTextUseCase;
import de.bund.zrb.ingestion.usecase.ExtractTextFromDocumentUseCase;
import de.bund.zrb.ingestion.usecase.RenderDocumentUseCase;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service that coordinates the async document preview workflow:
 * 1. Read file bytes
 * 2. Run ingestion pipeline (detect, filter, extract, normalize)
 * 3. Build Document model
 * 4. Render to Markdown
 * 5. Open preview tab
 */
public class DocumentPreviewOpener {

    private static final Logger LOG = Logger.getLogger(DocumentPreviewOpener.class.getName());

    private final TabbedPaneManager tabbedPaneManager;
    private final ExtractTextFromDocumentUseCase extractUseCase;
    private final BuildDocumentFromTextUseCase buildUseCase;
    private final RenderDocumentUseCase renderUseCase;
    private final ExecutorService executor;

    public DocumentPreviewOpener(TabbedPaneManager tabbedPaneManager) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.extractUseCase = new ExtractTextFromDocumentUseCase(createConfig());
        this.buildUseCase = new BuildDocumentFromTextUseCase();
        this.renderUseCase = new RenderDocumentUseCase(RendererRegistry.createDefault());
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DocumentPreviewWorker");
            t.setDaemon(true);
            return t;
        });
    }

    private IngestionConfig createConfig() {
        return new IngestionConfig()
                .setMaxFileSizeBytes(25 * 1024 * 1024) // 25 MB
                .setTimeoutPerExtractionMs(15000) // 15 seconds
                .setEnableFallbackOnExtractorFailure(true);
    }

    /**
     * Open a document preview asynchronously.
     *
     * @param fileService the file service to use for reading
     * @param path the file path
     * @param fileName the display name
     * @param parentComponent the parent component for dialogs
     */
    public void openPreviewAsync(FileService fileService, String path, String fileName, Component parentComponent) {
        // Set busy cursor
        Window window = SwingUtilities.getWindowAncestor(parentComponent);
        Cursor originalCursor = null;
        if (window != null) {
            originalCursor = window.getCursor();
            window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        final Cursor cursorToRestore = originalCursor;
        final Window windowRef = window;

        executor.submit(() -> {
            try {
                LOG.info("Starting document preview for: " + path);

                // Step 1: Read file bytes
                LOG.fine("Step 1: Reading file...");
                FilePayload payload = fileService.readFile(path);
                byte[] bytes = payload.getBytes();

                if (bytes == null || bytes.length == 0) {
                    showError(parentComponent, "Die Datei ist leer.", "Leere Datei");
                    return;
                }

                // Step 2: Run ingestion pipeline
                LOG.fine("Step 2: Running ingestion pipeline...");
                DocumentSource source = DocumentSource.fromBytes(bytes, fileName);
                ExtractionResult extractionResult = extractUseCase.execute(source);

                if (!extractionResult.isSuccess()) {
                    showError(parentComponent, extractionResult.getErrorMessage(), "Extraktion fehlgeschlagen");
                    return;
                }

                LOG.info("Extraction successful using: " + extractionResult.getExtractorName());
                if (extractionResult.hasWarnings()) {
                    LOG.warning("Warnings: " + extractionResult.getWarnings());
                }

                // Step 3: Build Document model
                LOG.fine("Step 3: Building document model...");
                DocumentMetadata metadata = DocumentMetadata.builder()
                        .sourceName(fileName)
                        .attributes(extractionResult.getMetadata())
                        .build();

                // Use structure detection for better formatting
                Document document = buildUseCase.buildWithStructure(
                        extractionResult.getPlainText(),
                        metadata
                );

                // Step 4: Render to Markdown
                LOG.fine("Step 4: Rendering to Markdown...");
                String markdown = renderUseCase.renderToMarkdown(document);

                // Step 5: Open preview tab on EDT
                LOG.fine("Step 5: Opening preview tab...");
                List<String> warnings = extractionResult.getWarnings();
                final Document finalDocument = document;
                SwingUtilities.invokeLater(() -> {
                    try {
                        DocumentPreviewTab previewTab = new DocumentPreviewTab(
                                fileName,
                                markdown,
                                metadata,
                                warnings,
                                finalDocument
                        );
                        tabbedPaneManager.addTab(previewTab);
                        LOG.info("Preview tab opened for: " + fileName);
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Failed to open preview tab", e);
                        showError(parentComponent, "Fehler beim Öffnen des Preview-Tabs:\n" + e.getMessage(), "Fehler");
                    }
                });

            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Document preview failed", e);
                showError(parentComponent, "Fehler bei der Dokument-Verarbeitung:\n" + e.getMessage(), "Fehler");
            } finally {
                // Restore cursor
                if (windowRef != null && cursorToRestore != null) {
                    SwingUtilities.invokeLater(() -> windowRef.setCursor(cursorToRestore));
                }
            }
        });
    }

    /**
     * Open a document preview from raw bytes.
     */
    public void openPreviewFromBytes(byte[] bytes, String fileName, Component parentComponent) {
        Window window = SwingUtilities.getWindowAncestor(parentComponent);
        Cursor originalCursor = null;
        if (window != null) {
            originalCursor = window.getCursor();
            window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        final Cursor cursorToRestore = originalCursor;
        final Window windowRef = window;

        executor.submit(() -> {
            try {
                if (bytes == null || bytes.length == 0) {
                    showError(parentComponent, "Die Datei ist leer.", "Leere Datei");
                    return;
                }

                DocumentSource source = DocumentSource.fromBytes(bytes, fileName);
                ExtractionResult extractionResult = extractUseCase.execute(source);

                if (!extractionResult.isSuccess()) {
                    showError(parentComponent, extractionResult.getErrorMessage(), "Extraktion fehlgeschlagen");
                    return;
                }

                DocumentMetadata metadata = DocumentMetadata.builder()
                        .sourceName(fileName)
                        .attributes(extractionResult.getMetadata())
                        .build();

                Document document = buildUseCase.buildWithStructure(
                        extractionResult.getPlainText(),
                        metadata
                );

                String markdown = renderUseCase.renderToMarkdown(document);
                List<String> warnings = extractionResult.getWarnings();
                final Document finalDocument = document;

                SwingUtilities.invokeLater(() -> {
                    try {
                        DocumentPreviewTab previewTab = new DocumentPreviewTab(
                                fileName,
                                markdown,
                                metadata,
                                warnings,
                                finalDocument
                        );
                        tabbedPaneManager.addTab(previewTab);
                    } catch (Exception e) {
                        showError(parentComponent, "Fehler beim Öffnen des Preview-Tabs:\n" + e.getMessage(), "Fehler");
                    }
                });

            } catch (Exception e) {
                showError(parentComponent, "Fehler bei der Dokument-Verarbeitung:\n" + e.getMessage(), "Fehler");
            } finally {
                if (windowRef != null && cursorToRestore != null) {
                    SwingUtilities.invokeLater(() -> windowRef.setCursor(cursorToRestore));
                }
            }
        });
    }

    private void showError(Component parent, String message, String title) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE)
        );
    }

    /**
     * Shutdown the executor.
     */
    public void shutdown() {
        executor.shutdownNow();
        extractUseCase.shutdown();
    }
}

