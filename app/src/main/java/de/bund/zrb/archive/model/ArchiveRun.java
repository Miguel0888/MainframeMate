package de.bund.zrb.archive.model;

import java.util.UUID;

/**
 * Represents a research run (session) – a logically grouped set of resources
 * captured during one research session.
 */
public class ArchiveRun {

    private String runId = UUID.randomUUID().toString();
    private String mode = "RESEARCH";           // RESEARCH | AGENT
    private long createdAt;                     // epoch millis UTC
    private long endedAt;                       // epoch millis UTC (0 = still running)
    private String seedUrls = "";               // comma-separated seed URLs
    private String domainPolicyJson = "";       // JSON representation of domain policy
    private String status = "RUNNING";          // RUNNING | COMPLETED | FAILED
    private String notes = "";
    private int resourceCount;                  // cached count
    private int documentCount;                  // cached count

    // ── Getters & Setters ──

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getEndedAt() { return endedAt; }
    public void setEndedAt(long endedAt) { this.endedAt = endedAt; }

    public String getSeedUrls() { return seedUrls; }
    public void setSeedUrls(String seedUrls) { this.seedUrls = seedUrls; }

    public String getDomainPolicyJson() { return domainPolicyJson; }
    public void setDomainPolicyJson(String domainPolicyJson) { this.domainPolicyJson = domainPolicyJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public int getResourceCount() { return resourceCount; }
    public void setResourceCount(int resourceCount) { this.resourceCount = resourceCount; }

    public int getDocumentCount() { return documentCount; }
    public void setDocumentCount(int documentCount) { this.documentCount = documentCount; }

    @Override
    public String toString() {
        return "Run[" + runId + " " + mode + " " + status + "]";
    }
}
