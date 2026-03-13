package de.bund.zrb.ui.terminal.cosmicclock;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

import static de.bund.zrb.ui.terminal.cosmicclock.CelestialMath.*;

/**
 * A Swing panel that renders an astronomically correct, accelerated night sky
 * as a cosmic clock.  Designed to be used as a terminal background.
 * <p>
 * Usage: create, call {@link #start()}, add to your component hierarchy.
 * Call {@link #stop()} when the panel is no longer needed.
 */
public class CosmicClockPanel extends JPanel {

    // ── Observer location: Berlin ──────────────────────────────
    private static final double OBSERVER_LAT = 52.52;   // °N
    private static final double OBSERVER_LON = 13.405;  // °E

    // ── Components ─────────────────────────────────────────────
    private final CosmicClockTimeModel timeModel;
    private final SkyProjection projection;
    private Timer animationTimer;

    public CosmicClockPanel(double timeFactor) {
        this.timeModel = new CosmicClockTimeModel(timeFactor);
        this.projection = new SkyProjection(); // south-facing default
        setOpaque(true);
        setBackground(Color.BLACK);
    }

    /** Convenience constructor with default time factor 120×. */
    public CosmicClockPanel() {
        this(120);
    }

    // ── Lifecycle ──────────────────────────────────────────────

    /** Start the animation timer (~20 fps). */
    public void start() {
        if (animationTimer != null) return;
        animationTimer = new Timer(50, e -> repaint());
        animationTimer.setCoalesce(true);
        animationTimer.start();
    }

    /** Stop the animation timer and free resources. */
    public void stop() {
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
    }

    public CosmicClockTimeModel getTimeModel() {
        return timeModel;
    }

    // ── Rendering ──────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g0) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Black sky background
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);

            // Subtle horizon gradient (very dark blue at the bottom few pixels)
            int horizonH = Math.max(h / 15, 4);
            GradientPaint horizonGrad = new GradientPaint(
                    0, h - horizonH, new Color(8, 12, 24),
                    0, h, new Color(3, 5, 12));
            g.setPaint(horizonGrad);
            g.fillRect(0, h - horizonH, w, horizonH);

            // Compute current simulated time
            long simMillis = timeModel.getSimulatedMillis();
            double jd = julianDate(simMillis);
            double lstDeg = lst(jd, OBSERVER_LON);

            // ── Stars ──────────────────────────────────────────
            for (double[] star : StarCatalog.STARS) {
                double raH   = star[0];
                double decDeg = star[1];
                double mag   = star[2];
                double bv    = star[3];

                double raDeg = raH * 15.0; // hours → degrees
                double[] altAz = equatorialToHorizontal(raDeg, decDeg, lstDeg, OBSERVER_LAT);
                double az  = altAz[0];
                double alt = altAz[1];

                if (alt < -2) continue; // below horizon

                int[] px = projection.project(az, alt, w, h);
                if (px == null) continue;

                // Size & brightness from magnitude
                double radius = magnitudeToRadius(mag);
                float alpha = magnitudeToAlpha(mag, alt);

                int rgb = StarCatalog.bvToRgb(bv);
                int r = (rgb >> 16) & 0xFF;
                int gv = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                g.setColor(new Color(r, gv, b, (int)(alpha * 255)));
                double d = radius * 2;
                g.fill(new Ellipse2D.Double(px[0] - radius, px[1] - radius, d, d));

                // Glow for very bright stars
                if (mag < 1.0 && alpha > 0.3f) {
                    float glowAlpha = alpha * 0.15f;
                    g.setColor(new Color(r, gv, b, Math.max(1, (int)(glowAlpha * 255))));
                    double glowR = radius * 3;
                    g.fill(new Ellipse2D.Double(px[0] - glowR, px[1] - glowR,
                            glowR * 2, glowR * 2));
                }
            }

            // ── Planets ────────────────────────────────────────
            for (SolarSystemCalculator.PlanetElements pl : SolarSystemCalculator.ALL_PLANETS) {
                double[] eq = SolarSystemCalculator.planet(jd, pl);
                if (eq == null) continue;

                double[] altAz = equatorialToHorizontal(eq[0], eq[1], lstDeg, OBSERVER_LAT);
                if (altAz[1] < -2) continue;

                int[] px = projection.project(altAz[0], altAz[1], w, h);
                if (px == null) continue;

                float alpha = altAz[1] < 0 ? 0.3f : (altAz[1] < 5 ? 0.5f : 0.9f);
                int cr = (pl.color >> 16) & 0xFF;
                int cg = (pl.color >> 8) & 0xFF;
                int cb = pl.color & 0xFF;
                g.setColor(new Color(cr, cg, cb, (int)(alpha * 255)));
                int pr = pl.radius;
                g.fill(new Ellipse2D.Double(px[0] - pr, px[1] - pr, pr * 2, pr * 2));
            }

            // ── Moon ───────────────────────────────────────────
            double[] moonData = SolarSystemCalculator.moon(jd);
            double[] moonAltAz = equatorialToHorizontal(moonData[0], moonData[1], lstDeg, OBSERVER_LAT);
            if (moonAltAz[1] > -3) {
                int[] px = projection.project(moonAltAz[0], moonAltAz[1], w, h);
                if (px != null) {
                    paintMoon(g, px[0], px[1], moonData[2], moonAltAz[1]);
                }
            }

        } finally {
            g.dispose();
        }
    }

    // ── Moon phase rendering ───────────────────────────────────

    private void paintMoon(Graphics2D g, int x, int y, double phaseDeg, double altDeg) {
        int r = 6; // moon radius in pixels
        float alpha = altDeg < 0 ? 0.3f : (altDeg < 5 ? 0.6f : 0.95f);

        // Draw illuminated portion
        Color moonBright = new Color(240, 235, 220, (int)(alpha * 255));
        Color moonDark   = new Color(40, 40, 45, (int)(alpha * 255));

        // Full disc background (dark side)
        g.setColor(moonDark);
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));

        // Phase: 0° = new, 90° = first quarter, 180° = full, 270° = last quarter
        double phase = normalizeDeg(phaseDeg);
        // Illumination fraction: 0 at new, 1 at full
        double illum = (1 - Math.cos(Math.toRadians(phase))) / 2.0;

        if (illum > 0.02) {
            g.setColor(moonBright);
            // Simple approximation: draw an ellipse whose width varies with phase
            // For waxing (0–180°): right side illuminated
            // For waning (180–360°): left side illuminated
            boolean waxing = phase < 180;
            double ew = r * 2 * Math.abs(illum * 2 - 1); // ellipse width
            if (illum >= 0.5) {
                // More than half illuminated: draw full circle then clip
                g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
                if (illum < 0.98) {
                    g.setColor(moonDark);
                    double ex = waxing ? x - ew / 2 : x - ew / 2;
                    // Draw the dark terminator as an ellipse
                    if (waxing) {
                        g.fill(new Ellipse2D.Double(x - ew / 2, y - r, ew, r * 2));
                    } else {
                        g.fill(new Ellipse2D.Double(x - ew / 2, y - r, ew, r * 2));
                    }
                }
            } else {
                // Less than half: draw illuminated crescent
                if (waxing) {
                    g.fill(new Ellipse2D.Double(x + r - ew, y - r, ew, r * 2));
                } else {
                    g.fill(new Ellipse2D.Double(x - r, y - r, ew, r * 2));
                }
            }
        }

        // Subtle glow around moon
        g.setColor(new Color(200, 200, 180, (int)(alpha * 30)));
        int gr = r + 4;
        g.fill(new Ellipse2D.Double(x - gr, y - gr, gr * 2, gr * 2));
    }

    // ── Magnitude → visual size & brightness ───────────────────

    /** Star radius in pixels from apparent magnitude. */
    private static double magnitudeToRadius(double mag) {
        // Bright stars (mag < 0) → ~3px, faint stars (mag ~3) → ~0.8px
        double r = 3.0 - mag * 0.6;
        return Math.max(0.6, Math.min(4.0, r));
    }

    /** Star alpha (opacity) from magnitude and altitude. */
    private static float magnitudeToAlpha(double mag, double alt) {
        // Base alpha from magnitude
        float a = (float)(1.0 - (mag + 1.5) / 5.5);
        a = Math.max(0.15f, Math.min(1.0f, a));
        // Fade near horizon (atmospheric extinction)
        if (alt < 10) {
            a *= (float) Math.max(0.1, alt / 10.0);
        }
        return a;
    }
}

