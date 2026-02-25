package de.bund.zrb.archive.service;

import de.bund.zrb.archive.model.*;
import de.bund.zrb.archive.store.ArchiveRepository;
import de.bund.zrb.archive.tools.WebArchiveSnapshotTool;
import de.bund.zrb.archive.tools.WebCacheAddUrlsTool;
import de.bund.zrb.archive.tools.WebCacheStatusTool;
import de.bund.zrb.runtime.ToolRegistryImpl;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central service for the Archive system.
 * Manages runs, resources, documents, and the catalog pipeline.
 */
public class ArchiveService {

    private static final Logger LOG = Logger.getLogger(ArchiveService.class.getName());
    private static ArchiveService instance;

    private final ArchiveRepository repository;
    private final WebSnapshotPipeline snapshotPipeline;
    private final ResourceStorageService storageService;
    private final CatalogPipeline catalogPipeline;

    private ArchiveService() {
        this.repository = ArchiveRepository.getInstance();
        this.storageService = new ResourceStorageService();
        this.snapshotPipeline = new WebSnapshotPipeline(repository);
        this.catalogPipeline = new CatalogPipeline(repository, storageService);
    }

    public static synchronized ArchiveService getInstance() {
        if (instance == null) {
            instance = new ArchiveService();
        }
        return instance;
    }

    /**
     * Registers all archive-related tools in the global ToolRegistry.
     */
    public void registerTools() {
        ToolRegistryImpl registry = ToolRegistryImpl.getInstance();
        registry.registerTool(new WebCacheStatusTool(repository));
        registry.registerTool(new WebCacheAddUrlsTool(repository));
        registry.registerTool(new WebArchiveSnapshotTool(snapshotPipeline));
        LOG.info("[Archive] 3 archive tools registered.");
    }

    // ═══════════════════════════════════════════════════════════
    //  Run Management
    // ═══════════════════════════════════════════════════════════

    /**
     * Start a new research run.
     *
     * @param mode             RESEARCH or AGENT
     * @param seedUrls         comma-separated seed URLs
     * @param domainPolicyJson JSON string of domain policy
     * @return the created ArchiveRun
     */
    public ArchiveRun startRun(String mode, String seedUrls, String domainPolicyJson) {
        ArchiveRun run = new ArchiveRun();
        run.setMode(mode != null ? mode.toUpperCase() : "RESEARCH");
        run.setCreatedAt(System.currentTimeMillis());
        run.setSeedUrls(seedUrls != null ? seedUrls : "");
        run.setDomainPolicyJson(domainPolicyJson != null ? domainPolicyJson : "{}");
        run.setStatus("RUNNING");

        repository.saveRun(run);
        LOG.info("[Archive] Run started: " + run.getRunId() + " (mode=" + run.getMode() + ")");
        return run;
    }

    /**
     * End a research run.
     */
    public void endRun(String runId) {
        repository.updateRunStatus(runId, "COMPLETED");
        repository.updateRunCounts(runId);
        LOG.info("[Archive] Run completed: " + runId);
    }

    // ═══════════════════════════════════════════════════════════
    //  Resource Ingestion (Data Lake)
    // ═══════════════════════════════════════════════════════════

    /**
     * Ingest a captured network response into the Data Lake.
     * Handles URL normalization, content hashing, deduplication,
     * resource classification, storage, and catalog pipeline.
     *
     * @param runId    the run this capture belongs to
     * @param url      the original URL
     * @param mimeType the MIME type
     * @param status   HTTP status code
     * @param bodyText the response body text
     * @param headers  filtered response headers
     * @param capturedAt timestamp of capture
     * @return the docId (if a Document was created) or resourceId
     */
    public String ingestNetworkResponse(String runId, String url, String mimeType,
                                         long status, String bodyText,
                                         Map<String, String> headers, long capturedAt) {
        if (url == null || bodyText == null) return null;

        try {
            // 1. URL normalization
            String canonicalUrl = UrlNormalizer.canonicalize(url);
            String urlHash = ContentHasher.hash(canonicalUrl);
            String contentHash = ContentHasher.hash(bodyText);
            String host = UrlNormalizer.extractHost(url);

            // 2. Resource classification
            ResourceKind kind = ResourceKind.fromMimeAndUrl(mimeType, url);
            boolean indexable = kind.isDefaultIndexable();

            // Special case: JSON with text content may be indexable
            if (kind == ResourceKind.API_JSON && bodyText.length() > 200) {
                String lower = bodyText.toLowerCase();
                if (lower.contains("\"text\"") || lower.contains("\"content\"")
                        || lower.contains("\"body\"") || lower.contains("\"article\"")
                        || lower.contains("\"description\"")) {
                    indexable = true;
                }
            }

            // 3. Deduplication check
            ArchiveResource existing = repository.findResourceByContentHashAndUrl(contentHash, canonicalUrl);
            if (existing != null) {
                // Same content at same URL → update seen count only
                repository.updateResourceSeen(existing.getResourceId());
                LOG.fine("[Archive] Dedupe hit: " + url + " (seen=" + (existing.getSeenCount() + 1) + ")");
                return existing.getResourceId();
            }

            // 4. Store blob on filesystem
            String storagePath = storageService.store(runId, host, kind, contentHash, bodyText);

            // 5. Extract title
            String title = extractTitle(bodyText, url, kind);

            // 6. Create ArchiveResource
            ArchiveResource resource = new ArchiveResource();
            resource.setRunId(runId);
            resource.setCapturedAt(capturedAt);
            resource.setSource("NETWORK");
            resource.setUrl(url);
            resource.setCanonicalUrl(canonicalUrl);
            resource.setUrlHash(urlHash);
            resource.setContentHash(contentHash);
            resource.setMimeType(mimeType != null ? mimeType : "");
            resource.setHttpStatus((int) status);
            resource.setKind(kind.name());
            resource.setSizeBytes(bodyText.length());
            resource.setIndexable(indexable);
            resource.setStoragePath(storagePath != null ? storagePath : "");
            resource.setTitle(title);
            resource.setFirstSeenAt(capturedAt);
            resource.setLastSeenAt(capturedAt);

            repository.saveResource(resource);

            // 7. Catalog Pipeline: derive Document if indexable
            String resultId = resource.getResourceId();
            if (indexable) {
                ArchiveDocument doc = catalogPipeline.process(resource, bodyText);
                if (doc != null) {
                    resultId = doc.getDocId();
                }
            }

            LOG.fine("[Archive] Ingested: " + kind.name() + " " + url
                    + " (indexable=" + indexable + ", id=" + resultId + ")");
            return resultId;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Archive] Ingestion failed for " + url, e);
            return null;
        }
    }

    /**
     * Quick title extraction from body content.
     */
    private String extractTitle(String body, String url, ResourceKind kind) {
        if (body == null) return buildFallbackTitle(url);
        if (kind == ResourceKind.PAGE_HTML || kind == ResourceKind.DOM_SNAPSHOT) {
            int titleStart = body.indexOf("<title>");
            if (titleStart < 0) titleStart = body.indexOf("<TITLE>");
            if (titleStart >= 0) {
                titleStart += 7;
                int titleEnd = body.indexOf("</title>", titleStart);
                if (titleEnd < 0) titleEnd = body.indexOf("</TITLE>", titleStart);
                if (titleEnd > titleStart && (titleEnd - titleStart) < 500) {
                    String t = body.substring(titleStart, titleEnd).trim();
                    if (!t.isEmpty()) return truncateTitle(t);
                }
            }
        }
        return buildFallbackTitle(url);
    }

    private String buildFallbackTitle(String url) {
        String host = UrlNormalizer.extractHost(url);
        try {
            java.net.URI uri = new java.net.URI(url);
            String path = uri.getPath();
            if (path != null && !path.isEmpty() && !path.equals("/")) {
                String combined = host + path;
                return combined.length() > 120 ? combined.substring(0, 120) + "…" : combined;
            }
        } catch (Exception ignore) {}
        return host;
    }

    private String truncateTitle(String title) {
        if (title == null) return "";
        return title.length() <= 500 ? title : title.substring(0, 500) + "…";
    }

    // ═══════════════════════════════════════════════════════════
    //  Accessors
    // ═══════════════════════════════════════════════════════════

    public ArchiveRepository getRepository() {
        return repository;
    }

    public WebSnapshotPipeline getSnapshotPipeline() {
        return snapshotPipeline;
    }

    public ResourceStorageService getStorageService() {
        return storageService;
    }

    public CatalogPipeline getCatalogPipeline() {
        return catalogPipeline;
    }

    public void shutdown() {
        repository.close();
    }
}
