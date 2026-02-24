package de.bund.zrb.indexing.model;

import java.util.UUID;

/**
 * Status record for a single indexing run (scan + process cycle).
 */
public class IndexRunStatus {

    private String runId = UUID.randomUUID().toString();
    private String sourceId;
    private long startedAt;
    private long completedAt;
    private RunState runState = RunState.RUNNING;

    // ── Counters ──
    private int itemsScanned;
    private int itemsNew;
    private int itemsChanged;
    private int itemsDeleted;
    private int itemsSkipped;
    private int itemsErrored;
    private int itemsUnchanged;

    // ── Error info ──
    private String lastError;

    public enum RunState {
        RUNNING, COMPLETED, FAILED, CANCELLED
    }

    // ── Getters & Setters ──

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }

    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }

    public RunState getRunState() { return runState; }
    public void setRunState(RunState runState) { this.runState = runState; }

    public int getItemsScanned() { return itemsScanned; }
    public void setItemsScanned(int itemsScanned) { this.itemsScanned = itemsScanned; }

    public int getItemsNew() { return itemsNew; }
    public void setItemsNew(int n) { this.itemsNew = n; }

    public int getItemsChanged() { return itemsChanged; }
    public void setItemsChanged(int n) { this.itemsChanged = n; }

    public int getItemsDeleted() { return itemsDeleted; }
    public void setItemsDeleted(int n) { this.itemsDeleted = n; }

    public int getItemsSkipped() { return itemsSkipped; }
    public void setItemsSkipped(int n) { this.itemsSkipped = n; }

    public int getItemsErrored() { return itemsErrored; }
    public void setItemsErrored(int n) { this.itemsErrored = n; }

    public int getItemsUnchanged() { return itemsUnchanged; }
    public void setItemsUnchanged(int n) { this.itemsUnchanged = n; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    /** Increment convenience methods for pipeline use. */
    public void incScanned() { itemsScanned++; }
    public void incNew() { itemsNew++; }
    public void incChanged() { itemsChanged++; }
    public void incDeleted() { itemsDeleted++; }
    public void incSkipped() { itemsSkipped++; }
    public void incErrored() { itemsErrored++; }
    public void incUnchanged() { itemsUnchanged++; }

    public long getDurationMs() {
        if (completedAt > 0 && startedAt > 0) return completedAt - startedAt;
        if (startedAt > 0) return System.currentTimeMillis() - startedAt;
        return 0;
    }

    @Override
    public String toString() {
        return "[" + runState + "] source=" + sourceId
                + " scanned=" + itemsScanned
                + " new=" + itemsNew + " changed=" + itemsChanged
                + " deleted=" + itemsDeleted + " errors=" + itemsErrored
                + " duration=" + getDurationMs() + "ms";
    }
}
