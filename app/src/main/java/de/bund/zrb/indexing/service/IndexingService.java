package de.bund.zrb.indexing.service;

import de.bund.zrb.indexing.connector.LocalSourceScanner;
import de.bund.zrb.indexing.model.*;
import de.bund.zrb.indexing.store.IndexSourceRepository;
import de.bund.zrb.indexing.store.IndexStatusStore;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central indexing service that manages sources, scheduling, and pipeline execution.
 *
 * Responsibilities:
 * - Holds the IndexingPipeline + all registered scanners
 * - Runs indexing on demand (manual) or scheduled (interval/startup)
 * - Reports status for UI display
 *
 * Usage:
 *   IndexingService service = IndexingService.getInstance();
 *   service.setContentProcessor(processor); // wire up RAG infrastructure
 *   service.runNow("sourceId");             // manual trigger
 *   service.startScheduler();               // start interval-based indexing
 */
public class IndexingService {

    private static final Logger LOG = Logger.getLogger(IndexingService.class.getName());
    private static IndexingService instance;

    private final IndexSourceRepository sourceRepo = new IndexSourceRepository();
    private final IndexStatusStore statusStore = new IndexStatusStore();
    private final IndexingPipeline pipeline;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Indexing-Worker");
        t.setDaemon(true);
        return t;
    });

    private ScheduledExecutorService scheduler;
    private final Map<String, Future<?>> runningJobs = new ConcurrentHashMap<>();

    // Listeners for UI updates
    private final List<IndexingListener> listeners = new CopyOnWriteArrayList<>();

    public interface IndexingListener {
        void onRunStarted(String sourceId);
        void onRunCompleted(String sourceId, IndexRunStatus result);
        void onRunFailed(String sourceId, String error);
    }

    private IndexingService() {
        pipeline = new IndexingPipeline(statusStore);
        // Register built-in scanners
        pipeline.registerScanner(SourceType.LOCAL, new LocalSourceScanner());
        pipeline.registerScanner(SourceType.MAIL, new de.bund.zrb.indexing.connector.MailSourceScanner());
        // FTP, NDV, WEB scanners will be registered as they are implemented
    }

    public static synchronized IndexingService getInstance() {
        if (instance == null) {
            instance = new IndexingService();
        }
        return instance;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Configuration
    // ═══════════════════════════════════════════════════════════════

    public void setContentProcessor(IndexingPipeline.ContentProcessor processor) {
        pipeline.setContentProcessor(processor);
    }

    public void addListener(IndexingListener listener) {
        listeners.add(listener);
    }

    public void removeListener(IndexingListener listener) {
        listeners.remove(listener);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Source management (delegates to repository)
    // ═══════════════════════════════════════════════════════════════

    public List<IndexSource> getAllSources() {
        return sourceRepo.loadAll();
    }

    public void saveSource(IndexSource source) {
        sourceRepo.save(source);
    }

    public boolean removeSource(String sourceId) {
        statusStore.deleteSource(sourceId);
        return sourceRepo.remove(sourceId);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Run management
    // ═══════════════════════════════════════════════════════════════

    /**
     * Run indexing for a specific source immediately (async).
     */
    public void runNow(String sourceId) {
        if (runningJobs.containsKey(sourceId)) {
            LOG.info("[Indexing] Run already in progress for: " + sourceId);
            return;
        }

        IndexSource source = sourceRepo.findById(sourceId);
        if (source == null) {
            LOG.warning("[Indexing] Source not found: " + sourceId);
            return;
        }

        Future<?> future = executor.submit(() -> {
            for (IndexingListener l : listeners) l.onRunStarted(sourceId);
            try {
                IndexRunStatus result = pipeline.runForSource(source);
                for (IndexingListener l : listeners) l.onRunCompleted(sourceId, result);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "[Indexing] Run failed", e);
                for (IndexingListener l : listeners) l.onRunFailed(sourceId, e.getMessage());
            } finally {
                runningJobs.remove(sourceId);
            }
        });
        runningJobs.put(sourceId, future);
    }

    /**
     * Run indexing for all enabled sources (async, sequential).
     */
    public void runAll() {
        executor.submit(() -> {
            for (IndexSource source : sourceRepo.getEnabled()) {
                if (!runningJobs.containsKey(source.getSourceId())) {
                    for (IndexingListener l : listeners) l.onRunStarted(source.getSourceId());
                    try {
                        IndexRunStatus result = pipeline.runForSource(source);
                        for (IndexingListener l : listeners) l.onRunCompleted(source.getSourceId(), result);
                    } catch (Exception e) {
                        for (IndexingListener l : listeners) l.onRunFailed(source.getSourceId(), e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Check if a source is currently being indexed.
     */
    public boolean isRunning(String sourceId) {
        return runningJobs.containsKey(sourceId);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Status queries
    // ═══════════════════════════════════════════════════════════════

    public Map<IndexItemState, Integer> getItemCounts(String sourceId) {
        return statusStore.countByState(sourceId);
    }

    public List<IndexRunStatus> getRunHistory(String sourceId) {
        return statusStore.loadRuns(sourceId);
    }

    public IndexRunStatus getLastSuccessfulRun(String sourceId) {
        return statusStore.getLastSuccessfulRun(sourceId);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Scheduler
    // ═══════════════════════════════════════════════════════════════

    /**
     * Start the scheduler for interval-based sources.
     * Also runs ON_STARTUP sources immediately.
     */
    public void startScheduler() {
        if (scheduler != null) return;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Indexing-Scheduler");
            t.setDaemon(true);
            return t;
        });

        // Run ON_STARTUP sources
        for (IndexSource source : sourceRepo.getEnabled()) {
            if (source.getScheduleMode() == ScheduleMode.ON_STARTUP) {
                runNow(source.getSourceId());
            }
        }

        // Schedule periodic check for INTERVAL and DAILY sources (check every minute)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                java.util.Calendar now = java.util.Calendar.getInstance();
                int currentHour = now.get(java.util.Calendar.HOUR_OF_DAY);
                int currentMinute = now.get(java.util.Calendar.MINUTE);

                for (IndexSource source : sourceRepo.getEnabled()) {
                    if (isRunning(source.getSourceId())) continue;

                    if (source.getScheduleMode() == ScheduleMode.INTERVAL) {
                        IndexRunStatus lastRun = statusStore.getLastSuccessfulRun(source.getSourceId());
                        long intervalMs = source.getIntervalMinutes() * 60_000L;
                        long lastRunTime = lastRun != null ? lastRun.getCompletedAt() : 0;

                        if (System.currentTimeMillis() - lastRunTime >= intervalMs) {
                            runNow(source.getSourceId());
                        }
                    } else if (source.getScheduleMode() == ScheduleMode.DAILY) {
                        // Check if it's the right time and hasn't run today yet
                        if (currentHour == source.getStartHour()
                                && currentMinute == source.getStartMinute()) {
                            IndexRunStatus lastRun = statusStore.getLastSuccessfulRun(source.getSourceId());
                            if (lastRun == null || !isSameDay(lastRun.getStartedAt())) {
                                runNow(source.getSourceId());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[Indexing] Scheduler error", e);
            }
        }, 1, 1, TimeUnit.MINUTES);

        LOG.info("[Indexing] Scheduler started");
    }

    /**
     * Stop the scheduler.
     */
    public void stopScheduler() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            LOG.info("[Indexing] Scheduler stopped");
        }
    }

    /**
     * Shutdown the service (call on application exit).
     */
    public void shutdown() {
        stopScheduler();
        executor.shutdownNow();
    }

    /**
     * Check if a timestamp is from today.
     */
    private static boolean isSameDay(long timestampMs) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int todayDay = cal.get(java.util.Calendar.DAY_OF_YEAR);
        int todayYear = cal.get(java.util.Calendar.YEAR);
        cal.setTimeInMillis(timestampMs);
        return cal.get(java.util.Calendar.DAY_OF_YEAR) == todayDay
                && cal.get(java.util.Calendar.YEAR) == todayYear;
    }
}
