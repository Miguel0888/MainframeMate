package de.bund.zrb.ui.terminal.cosmicclock;

/**
 * Projects horizontal coordinates (azimuth, altitude) to pixel coordinates
 * within a panel of a given size.
 * <p>
 * Uses a simple cylindrical projection of the southern sky hemisphere:
 * azimuth maps to x, altitude maps to y (horizon at bottom, zenith at top).
 */
public class SkyProjection {

    /** Centre azimuth of the viewport (degrees). Default: 180° = due South. */
    private double centreAz = 180.0;

    /** Horizontal field of view (degrees). Default: 200° (generous southern sky). */
    private double fovAz = 200.0;

    /** Minimum altitude shown (degrees). Slightly below horizon for aesthetics. */
    private double minAlt = -5.0;

    /** Maximum altitude shown (degrees). */
    private double maxAlt = 90.0;

    public SkyProjection() { }

    public SkyProjection(double centreAz, double fovAz, double minAlt, double maxAlt) {
        this.centreAz = centreAz;
        this.fovAz = fovAz;
        this.minAlt = minAlt;
        this.maxAlt = maxAlt;
    }

    /**
     * Project an azimuth/altitude to pixel coordinates.
     *
     * @param azDeg  azimuth in degrees (0 = North, 90 = East, 180 = South, 270 = West)
     * @param altDeg altitude in degrees (0 = horizon, 90 = zenith)
     * @param w      panel width in pixels
     * @param h      panel height in pixels
     * @return {@code int[]{x, y}} or {@code null} if the object is outside the viewport
     */
    public int[] project(double azDeg, double altDeg, int w, int h) {
        if (altDeg < minAlt || altDeg > maxAlt) return null;

        // Normalise azimuth difference to [-180, 180]
        double dAz = azDeg - centreAz;
        if (dAz > 180)  dAz -= 360;
        if (dAz < -180) dAz += 360;

        double halfFov = fovAz / 2.0;
        if (dAz < -halfFov || dAz > halfFov) return null;

        // x: left = -halfFov, right = +halfFov  (East on left for a sky view)
        // We MIRROR so east is on the left (as when looking south and up)
        double xNorm = 0.5 - dAz / fovAz;  // 0..1
        // y: bottom = minAlt, top = maxAlt
        double yNorm = 1.0 - (altDeg - minAlt) / (maxAlt - minAlt); // 0..1

        int px = (int) Math.round(xNorm * (w - 1));
        int py = (int) Math.round(yNorm * (h - 1));

        return new int[]{px, py};
    }

    // ── Accessors ──────────────────────────────────────────────

    public double getCentreAz()   { return centreAz; }
    public double getFovAz()      { return fovAz; }
    public double getMinAlt()     { return minAlt; }
    public double getMaxAlt()     { return maxAlt; }
}

