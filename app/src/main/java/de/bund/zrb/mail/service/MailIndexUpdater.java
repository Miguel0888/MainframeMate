package de.bund.zrb.mail.service;

import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.mail.infrastructure.MailMetadataIndex;
import de.bund.zrb.rag.service.RagService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Responsible for extracting full text from new/changed mails and updating
 * the Lucene search index via {@link RagService}.
 * <p>
 * Performs lazy reindexing: only mails identified by {@link MailDeltaDetector}
 * as new or changed are processed. Unchanged mails are never re-extracted.
 */
public class MailIndexUpdater {

    private static final Logger LOG = Logger.getLogger(MailIndexUpdater.class.getName());

    private final de.bund.zrb.mail.port.MailboxReader mailboxReader;

    public MailIndexUpdater(de.bund.zrb.mail.port.MailboxReader mailboxReader) {
        this.mailboxReader = mailboxReader;
    }

    /**
     * Result of an index update run.
     */
    public static class UpdateResult {
        public final int indexed;
        public final int errors;

        public UpdateResult(int indexed, int errors) {
            this.indexed = indexed;
            this.errors = errors;
        }
    }

    /**
     * Index all candidates (new + changed) from a delta result.
     */
    public UpdateResult indexCandidates(List<MailDeltaDetector.MailCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new UpdateResult(0, 0);
        }

        RagService rag = RagService.getInstance();
        MailMetadataIndex metaIndex = MailMetadataIndex.getInstance();
        int indexed = 0;
        int errors = 0;

        // Collect metadata entries for batch indexing
        List<MailMetadataIndex.MailMetadataEntry> metaBatch =
                new ArrayList<MailMetadataIndex.MailMetadataEntry>();

        for (MailDeltaDetector.MailCandidate mc : candidates) {
            try {
                // Build document ID (same format as MailSourceScanner)
                String documentId = mc.toItemPath();
                String documentName = buildDocumentName(mc);

                // ── 1. Metadata index (lightweight — always succeeds) ──
                metaBatch.add(new MailMetadataIndex.MailMetadataEntry(
                        documentId,
                        mc.mailboxPath,
                        mc.folderPath,
                        mc.descriptorNodeId,
                        mc.subject,
                        mc.sender,
                        mc.recipients,
                        mc.deliveryTime != null ? mc.deliveryTime.getTime() : 0,
                        mc.messageClass,
                        mc.hasAttachments,
                        mc.size
                ));

                // ── 2. Full-text RAG index (may fail for large/corrupt mails) ──
                // Read full content via MailboxReader
                String fullText = extractFullText(mc);
                if (fullText == null || fullText.trim().isEmpty()) {
                    LOG.fine("[MailIndex] Empty content for: " + documentName);
                    errors++;
                    continue;
                }

                // Remove old version if exists (for changed mails)
                if (rag.isIndexed(documentId)) {
                    rag.removeDocument(documentId);
                }

                // Build Document for RAG indexing
                DocumentMetadata metadata = DocumentMetadata.builder()
                        .sourceName(documentName)
                        .mimeType("message/rfc822")
                        .attribute("source_type", "MAIL")
                        .attribute("subject", mc.subject != null ? mc.subject : "")
                        .attribute("sender", mc.sender != null ? mc.sender : "")
                        .attribute("folder", mc.folderPath != null ? mc.folderPath : "")
                        .build();

                Document document = Document.fromText(fullText, metadata);

                // Index (without embeddings for performance — mail text is often large)
                rag.indexDocument(documentId, documentName, document, false);
                indexed++;

                if (indexed % 50 == 0) {
                    LOG.info("[MailIndex] Progress: " + indexed + "/" + candidates.size());
                }

            } catch (Exception e) {
                errors++;
                LOG.log(Level.FINE, "[MailIndex] Error indexing: " + mc.subject, e);
            }
        }

        // Flush RAG index to persist
        try {
            rag.flushIndex();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MailIndex] Flush failed", e);
        }

        // Batch-flush metadata index
        try {
            metaIndex.indexBatch(metaBatch);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MailIndex] Metadata batch index failed", e);
        }

        LOG.info("[MailIndex] Indexed: " + indexed + ", errors: " + errors);
        return new UpdateResult(indexed, errors);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Content extraction
    // ═══════════════════════════════════════════════════════════════

    private String extractFullText(MailDeltaDetector.MailCandidate mc) {
        try {
            de.bund.zrb.mail.model.MailMessageContent content =
                    mailboxReader.readMessage(mc.mailboxPath, mc.folderPath, mc.descriptorNodeId);

            StringBuilder sb = new StringBuilder();

            // Header metadata
            sb.append("Betreff: ").append(safe(mc.subject)).append("\n");
            sb.append("Von: ").append(safe(mc.sender)).append("\n");
            sb.append("An: ").append(safe(mc.recipients)).append("\n");
            if (mc.deliveryTime != null) {
                sb.append("Datum: ").append(mc.deliveryTime).append("\n");
            }
            sb.append("Ordner: ").append(safe(mc.folderPath)).append("\n");
            sb.append("\n");

            // Body
            String body = content.getBodyText();
            if (body != null && !body.trim().isEmpty()) {
                sb.append(body);
            } else if (content.getBodyHtml() != null) {
                // Strip HTML tags for plain-text indexing
                sb.append(stripHtml(content.getBodyHtml()));
            }

            // Attachment names (for searchability)
            List<String> attachments = content.getAttachmentNames();
            if (attachments != null && !attachments.isEmpty()) {
                sb.append("\n\nAnhänge:\n");
                for (String att : attachments) {
                    sb.append("- ").append(att).append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            LOG.log(Level.FINE, "[MailIndex] Content extraction failed for " + mc.subject, e);
            // Fallback: index just the metadata
            StringBuilder sb = new StringBuilder();
            sb.append("Betreff: ").append(safe(mc.subject)).append("\n");
            sb.append("Von: ").append(safe(mc.sender)).append("\n");
            sb.append("An: ").append(safe(mc.recipients)).append("\n");
            return sb.toString();
        }
    }

    private String buildDocumentName(MailDeltaDetector.MailCandidate mc) {
        String subj = mc.subject != null && !mc.subject.isEmpty() ? mc.subject : "(kein Betreff)";
        String from = mc.sender != null ? mc.sender : "";
        return "✉ " + subj + " — " + from;
    }

    /**
     * Simple HTML tag stripper for fallback text extraction.
     */
    private static String stripHtml(String html) {
        if (html == null) return "";
        // Remove scripts, styles
        String s = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        s = s.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        // Replace block tags with newlines
        s = s.replaceAll("(?i)<br[^>]*>", "\n");
        s = s.replaceAll("(?i)</(p|div|tr|li|h[1-6])>", "\n");
        // Remove all remaining tags
        s = s.replaceAll("<[^>]+>", "");
        // Decode common entities
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&nbsp;", " ").replace("&quot;", "\"");
        // Collapse whitespace
        s = s.replaceAll("[ \t]+", " ");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }

    private static String safe(String s) { return s != null ? s : ""; }
}

