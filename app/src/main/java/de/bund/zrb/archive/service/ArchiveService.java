package de.bund.zrb.archive.service;

import de.bund.zrb.archive.store.ArchiveRepository;
import de.bund.zrb.archive.tools.WebArchiveSnapshotTool;
import de.bund.zrb.archive.tools.WebCacheAddUrlsTool;
import de.bund.zrb.archive.tools.WebCacheStatusTool;
import de.bund.zrb.runtime.ToolRegistryImpl;

import java.util.logging.Logger;

/**
 * Central service for the Archive system.
 * Initializes the database and registers archive-specific tools.
 */
public class ArchiveService {

    private static final Logger LOG = Logger.getLogger(ArchiveService.class.getName());
    private static ArchiveService instance;

    private final ArchiveRepository repository;
    private final WebSnapshotPipeline snapshotPipeline;

    private ArchiveService() {
        this.repository = ArchiveRepository.getInstance();
        this.snapshotPipeline = new WebSnapshotPipeline(repository);
    }

    public static synchronized ArchiveService getInstance() {
        if (instance == null) {
            instance = new ArchiveService();
        }
        return instance;
    }

    /**
     * Registers all archive-related tools in the global ToolRegistry.
     * Should be called during application startup, after plugin init.
     */
    public void registerTools() {
        ToolRegistryImpl registry = ToolRegistryImpl.getInstance();
        registry.registerTool(new WebCacheStatusTool(repository));
        registry.registerTool(new WebCacheAddUrlsTool(repository));
        registry.registerTool(new WebArchiveSnapshotTool(snapshotPipeline));
        LOG.info("[Archive] 3 archive tools registered.");
    }

    public ArchiveRepository getRepository() {
        return repository;
    }

    public WebSnapshotPipeline getSnapshotPipeline() {
        return snapshotPipeline;
    }

    public void shutdown() {
        repository.close();
    }
}
