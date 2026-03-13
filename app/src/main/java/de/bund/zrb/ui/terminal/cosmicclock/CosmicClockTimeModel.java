package de.bund.zrb.ui.terminal.cosmicclock;

/**
 * Time model for the cosmic clock.
 * <p>
 * The simulated time advances faster than real time by a configurable factor.
 * Calling {@link #getSimulatedMillis()} always returns the "current" simulated
 * timestamp, which is astronomically correct for the accelerated instant.
 */
public class CosmicClockTimeModel {

    private final long realStartMillis;
    private volatile double timeFactor;

    /**
     * @param timeFactor  speed multiplier (e.g. 120 = 2 real-minutes ≈ 4 simulated hours)
     */
    public CosmicClockTimeModel(double timeFactor) {
        this.realStartMillis = System.currentTimeMillis();
        this.timeFactor = Math.max(1, timeFactor);
    }

    /** Get the simulated time as Unix milliseconds. */
    public long getSimulatedMillis() {
        long elapsed = System.currentTimeMillis() - realStartMillis;
        return realStartMillis + (long) (elapsed * timeFactor);
    }

    public double getTimeFactor() {
        return timeFactor;
    }

    public void setTimeFactor(double factor) {
        this.timeFactor = Math.max(1, factor);
    }
}

