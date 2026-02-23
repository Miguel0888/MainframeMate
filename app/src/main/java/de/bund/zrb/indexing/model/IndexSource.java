package de.bund.zrb.indexing.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A configured data source for indexing.
 *
 * Each source represents one connection point (e.g. a local directory,
 * an FTP server path, a mail store, or a web URL) with its indexing policy.
 */
public class IndexSource {

    private String sourceId = UUID.randomUUID().toString();
    private String name = "";
    private SourceType sourceType = SourceType.LOCAL;
    private boolean enabled = true;

    // ── Scope: what to index ──
    private List<String> scopePaths = new ArrayList<>(); // root paths to scan
    private List<String> includePatterns = new ArrayList<>(); // glob patterns (e.g. "*.pdf", "*.docx")
    private List<String> excludePatterns = new ArrayList<>(); // glob exclusions (e.g. "*.tmp", "node_modules/**")
    private int maxDepth = 10; // max directory recursion depth
    private long maxFileSizeBytes = 50 * 1024 * 1024; // 50 MB default

    // ── Schedule ──
    private ScheduleMode scheduleMode = ScheduleMode.MANUAL;
    private int intervalMinutes = 60; // for INTERVAL mode
    private String cronExpression = ""; // for CRON mode (future)

    // ── Change detection ──
    private ChangeDetectionMode changeDetection = ChangeDetectionMode.MTIME_SIZE;

    // ── Processing options ──
    private boolean embeddingEnabled = true;
    private boolean fulltextEnabled = true;
    private int chunkSize = 512;
    private int chunkOverlap = 64;
    private int maxChunksPerItem = 100;

    // ── Security ──
    private SecurityMode securityMode = SecurityMode.NONE;

    // ── Connection (source-specific, stored as key-value) ──
    // For LOCAL: not needed (paths in scopePaths)
    // For FTP: host, user (password from LoginManager)
    // For MAIL: mailStorePath
    // For NDV: host, port, library
    // For WEB: base URLs in scopePaths
    private String connectionHost = "";
    private int connectionPort = 0;

    // ── Getters & Setters ──

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getScopePaths() { return scopePaths; }
    public void setScopePaths(List<String> scopePaths) { this.scopePaths = scopePaths; }

    public List<String> getIncludePatterns() { return includePatterns; }
    public void setIncludePatterns(List<String> includePatterns) { this.includePatterns = includePatterns; }

    public List<String> getExcludePatterns() { return excludePatterns; }
    public void setExcludePatterns(List<String> excludePatterns) { this.excludePatterns = excludePatterns; }

    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }

    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }

    public ScheduleMode getScheduleMode() { return scheduleMode; }
    public void setScheduleMode(ScheduleMode scheduleMode) { this.scheduleMode = scheduleMode; }

    public int getIntervalMinutes() { return intervalMinutes; }
    public void setIntervalMinutes(int intervalMinutes) { this.intervalMinutes = intervalMinutes; }

    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

    public ChangeDetectionMode getChangeDetection() { return changeDetection; }
    public void setChangeDetection(ChangeDetectionMode changeDetection) { this.changeDetection = changeDetection; }

    public boolean isEmbeddingEnabled() { return embeddingEnabled; }
    public void setEmbeddingEnabled(boolean embeddingEnabled) { this.embeddingEnabled = embeddingEnabled; }

    public boolean isFulltextEnabled() { return fulltextEnabled; }
    public void setFulltextEnabled(boolean fulltextEnabled) { this.fulltextEnabled = fulltextEnabled; }

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    public int getMaxChunksPerItem() { return maxChunksPerItem; }
    public void setMaxChunksPerItem(int maxChunksPerItem) { this.maxChunksPerItem = maxChunksPerItem; }

    public SecurityMode getSecurityMode() { return securityMode; }
    public void setSecurityMode(SecurityMode securityMode) { this.securityMode = securityMode; }

    public String getConnectionHost() { return connectionHost; }
    public void setConnectionHost(String connectionHost) { this.connectionHost = connectionHost; }

    public int getConnectionPort() { return connectionPort; }
    public void setConnectionPort(int connectionPort) { this.connectionPort = connectionPort; }

    @Override
    public String toString() {
        return name + " [" + sourceType.getDisplayName() + "]" + (enabled ? "" : " (deaktiviert)");
    }
}
