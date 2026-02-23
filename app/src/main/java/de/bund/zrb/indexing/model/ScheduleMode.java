package de.bund.zrb.indexing.model;

/**
 * When to run indexing scans.
 */
public enum ScheduleMode {
    /** Only when user clicks "Run Now". */
    MANUAL,
    /** Run once at application startup. */
    ON_STARTUP,
    /** Run at fixed interval (see IndexSource.intervalMinutes). */
    INTERVAL,
    /** Run daily at a specific time (see IndexSource.startHour/startMinute). */
    DAILY
}
