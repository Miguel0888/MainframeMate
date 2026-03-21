package de.bund.zrb.mail.service;

import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.mail.infrastructure.MailMetadataIndex;
import de.bund.zrb.mail.model.MailMessageHeader;
import de.bund.zrb.mail.model.MailMessageSkeleton;
import de.bund.zrb.rag.service.RagService;

import java.util.*;
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

    /** Chunk size for incremental metadata index commits. */
    private static final int META_BATCH_CHUNK = 500;

    /**
     * Index all candidates (new + changed) from a delta result.
     * <p>
     * Metadata (subject, sender, date, …) is committed in chunks of {@value #META_BATCH_CHUNK}
     * so that sorted browsing works immediately — even while the slow full-text extraction
     * for the RAG index is still running.
     */
    public UpdateResult indexCandidates(List<MailDeltaDetector.MailCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new UpdateResult(0, 0);
        }

        RagService rag = RagService.getInstance();
        MailMetadataIndex metaIndex = MailMetadataIndex.getInstance();
        int indexed = 0;
        int errors = 0;

        // Collect metadata entries for batch indexing — flushed in chunks
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

                // Flush metadata chunk so sorted browsing works while RAG extraction is still running
                if (metaBatch.size() >= META_BATCH_CHUNK) {
                    try {
                        metaIndex.indexBatch(metaBatch);
                    } catch (Exception ex) {
                        LOG.log(Level.WARNING, "[MailIndex] Metadata chunk flush failed", ex);
                    }
                    metaBatch = new ArrayList<MailMetadataIndex.MailMetadataEntry>();
                }

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

        // Flush remaining metadata entries
        if (!metaBatch.isEmpty()) {
            try {
                metaIndex.indexBatch(metaBatch);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[MailIndex] Metadata final flush failed", e);
            }
        }

        LOG.info("[MailIndex] Indexed: " + indexed + ", errors: " + errors);
        return new UpdateResult(indexed, errors);
    }

    /**
     * Fast metadata-only indexing for a single folder — reads only mail headers
     * (no full text extraction) via {@link de.bund.zrb.mail.port.MailboxReader}.
     * <p>
     * Used when the metadata index is empty for a folder and the user wants to
     * sort by date immediately, without waiting for the full RAG index build.
     *
     * @return number of headers indexed
     */
    public int indexMetadataForFolder(String mailboxPath, String folderPath) {
        MailMetadataIndex metaIndex = MailMetadataIndex.getInstance();
        int count = 0;
        try {
            // Read headers in pages (same as MailConnectionTab)
            int offset = 0;
            int pageSize = 2000;
            List<MailMetadataIndex.MailMetadataEntry> batch =
                    new ArrayList<MailMetadataIndex.MailMetadataEntry>();

            while (true) {
                List<de.bund.zrb.mail.model.MailMessageHeader> headers =
                        mailboxReader.listMessages(mailboxPath, folderPath, offset, pageSize);
                if (headers.isEmpty()) break;

                for (de.bund.zrb.mail.model.MailMessageHeader h : headers) {
                    String itemPath = mailboxPath + "#" + h.getFolderPath() + "#" + h.getDescriptorNodeId();
                    batch.add(new MailMetadataIndex.MailMetadataEntry(
                            itemPath,
                            mailboxPath,
                            h.getFolderPath(),
                            h.getDescriptorNodeId(),
                            h.getSubject(),
                            h.getFrom(),
                            h.getTo(),
                            h.getDate() != null ? h.getDate().getTime() : 0,
                            h.getMessageClass(),
                            h.hasAttachments(),
                            h.getMessageSize()
                    ));

                    if (batch.size() >= META_BATCH_CHUNK) {
                        metaIndex.indexBatch(batch);
                        count += batch.size();
                        batch = new ArrayList<MailMetadataIndex.MailMetadataEntry>();
                    }
                }

                offset += headers.size();
                if (headers.size() < pageSize) break; // last page
            }

            // Flush remainder
            if (!batch.isEmpty()) {
                metaIndex.indexBatch(batch);
                count += batch.size();
            }

            LOG.info("[MailIndex] Metadata-only indexed " + count + " headers for " + folderPath);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MailIndex] Metadata-only index failed for " + folderPath, e);
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Phase 1: Skeleton indexing (ultra-fast, only timestamp + URL)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ultra-fast skeleton indexing for a single folder.
     * <p>
     * Opens the PST file exactly once, iterates through ALL messages, and
     * stores only {@code itemPath}, {@code deliveryTime}, {@code nodeId},
     * {@code mailboxPath}, and {@code folderPath}.  Subject, sender, and all
     * other fields are left empty.
     * <p>
     * This is the Phase 1 of progressive indexing: the user immediately gets
     * a date-sorted view.  Enrichment (Phase 2) fills in subject/sender later.
     *
     * @return number of skeleton entries indexed
     */
    public int indexSkeletonForFolder(String mailboxPath, String folderPath) {
        MailMetadataIndex metaIndex = MailMetadataIndex.getInstance();
        int count = 0;
        try {
            List<MailMessageSkeleton> skeletons =
                    mailboxReader.listMessageSkeletons(mailboxPath, folderPath);

            List<MailMetadataIndex.MailMetadataEntry> batch =
                    new ArrayList<MailMetadataIndex.MailMetadataEntry>();

            for (MailMessageSkeleton sk : skeletons) {
                String itemPath = mailboxPath + "#" + folderPath + "#" + sk.nodeId;
                batch.add(new MailMetadataIndex.MailMetadataEntry(
                        itemPath,
                        mailboxPath,
                        folderPath,
                        sk.nodeId,
                        "",   // subject — empty (Phase 1)
                        "",   // sender — empty (Phase 1)
                        "",   // recipients — empty (Phase 1)
                        sk.deliveryTimeMillis,
                        "",   // messageClass — empty (marks as "not enriched")
                        false, // hasAttachments — unknown
                        0      // size — unknown
                ));

                if (batch.size() >= META_BATCH_CHUNK) {
                    metaIndex.indexBatch(batch);
                    count += batch.size();
                    batch = new ArrayList<MailMetadataIndex.MailMetadataEntry>();
                }
            }

            // Flush remainder
            if (!batch.isEmpty()) {
                metaIndex.indexBatch(batch);
                count += batch.size();
            }

            LOG.info("[MailIndex] Skeleton-indexed " + count + " entries for " + folderPath);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MailIndex] Skeleton index failed for " + folderPath, e);
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Phase 2: Enrichment (visible entries only)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Callback interface for progressive enrichment of visible metadata entries.
     */
    public interface EnrichmentCallback {
        /**
         * Called on the background thread each time an entry has been enriched
         * and persisted in the index.
         */
        void onEntryEnriched(MailMetadataIndex.MailMetadataEntry enrichedEntry);
    }

    /**
     * Enriches a set of skeleton index entries with full header data (subject,
     * sender, recipients, messageClass, hasAttachments, size).
     * <p>
     * Opens the PST file once, iterates through the folder, and for each
     * matching node ID extracts the full header.  The enriched entry is written
     * back to the Lucene metadata index and the callback is invoked immediately.
     * <p>
     * Entries are enriched in PST iteration order (not necessarily date order).
     * The callback should handle re-ordering / UI updates.
     *
     * @param nodeIds  the descriptor node IDs to enrich
     * @param callback called for each enriched entry (may be {@code null})
     * @return number of entries enriched
     */
    public int enrichMetadataEntries(String mailboxPath, String folderPath,
                                     List<Long> nodeIds, EnrichmentCallback callback) {
        if (nodeIds == null || nodeIds.isEmpty()) return 0;

        MailMetadataIndex metaIndex = MailMetadataIndex.getInstance();
        int enriched = 0;

        try {
            Set<Long> nodeIdSet = new LinkedHashSet<>(nodeIds);
            Map<Long, MailMessageHeader> headers =
                    mailboxReader.readHeadersByNodeIds(mailboxPath, folderPath, nodeIdSet);

            for (Map.Entry<Long, MailMessageHeader> entry : headers.entrySet()) {
                long nodeId = entry.getKey();
                MailMessageHeader h = entry.getValue();
                String itemPath = mailboxPath + "#" + folderPath + "#" + nodeId;

                MailMetadataIndex.MailMetadataEntry enrichedEntry =
                        new MailMetadataIndex.MailMetadataEntry(
                                itemPath,
                                mailboxPath,
                                h.getFolderPath(),
                                nodeId,
                                h.getSubject(),
                                h.getFrom(),
                                h.getTo(),
                                h.getDate() != null ? h.getDate().getTime() : 0,
                                h.getMessageClass(),
                                h.hasAttachments(),
                                h.getMessageSize()
                        );

                // Persist enriched entry (replaces skeleton)
                metaIndex.index(enrichedEntry);
                enriched++;

                if (callback != null) {
                    callback.onEntryEnriched(enrichedEntry);
                }
            }

            // Single commit after all enrichments
            metaIndex.flush();
            LOG.info("[MailIndex] Enriched " + enriched + "/" + nodeIds.size()
                    + " entries for " + folderPath);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MailIndex] Enrichment failed for " + folderPath, e);
        }
        return enriched;
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

