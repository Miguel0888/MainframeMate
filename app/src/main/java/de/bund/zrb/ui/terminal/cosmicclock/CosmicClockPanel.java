package de.bund.zrb.ui.terminal.cosmicclock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;

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

    /** Whether to use German constellation names (default: true). */
    private volatile boolean useGermanNames = true;

    /** Visible celestial bodies from the last paint cycle (for tooltip hit-testing). */
    private final List<CelestialBody> visibleBodies = new ArrayList<>();

    /** A rendered celestial body with screen position and name. */
    private static final class CelestialBody {
        final int x, y;
        final double hitRadius;
        final String name;
        CelestialBody(int x, int y, double hitRadius, String name) {
            this.x = x; this.y = y; this.hitRadius = hitRadius; this.name = name;
        }
    }

    public CosmicClockPanel(double timeFactor) {
        this.timeModel = new CosmicClockTimeModel(timeFactor);
        this.projection = new SkyProjection(); // south-facing default
        setOpaque(true);
        setBackground(Color.BLACK);

        // Enable Swing tooltips and speed up the initial delay
        ToolTipManager ttm = ToolTipManager.sharedInstance();
        ttm.registerComponent(this);
        ttm.setInitialDelay(150);
        ttm.setDismissDelay(5000);
    }

    /** Convenience constructor with default time factor 120×. */
    public CosmicClockPanel() {
        this(120);
    }

    /** Set whether constellation names should be displayed in German (true) or English/Latin (false). */
    public void setUseGermanNames(boolean german) {
        this.useGermanNames = german;
    }

    /** Return the name of the nearest celestial body under the mouse, or null. */
    @Override
    public String getToolTipText(MouseEvent e) {
        int mx = e.getX(), my = e.getY();
        double bestDist = Double.MAX_VALUE;
        String bestName = null;
        // snapshot to avoid concurrent modification
        List<CelestialBody> bodies = new ArrayList<>(visibleBodies);
        for (CelestialBody b : bodies) {
            double dx = mx - b.x;
            double dy = my - b.y;
            double dist = Math.sqrt(dx * dx + dy * dy);
            double hitR = Math.max(b.hitRadius, 8); // minimum 8px hit area
            if (dist <= hitR && dist < bestDist) {
                bestDist = dist;
                bestName = b.name;
            }
        }
        return bestName;
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

    /** Reusable off-screen buffer for transparent child compositing. */
    private BufferedImage childBuffer;

    /**
     * Render child components (the terminal screen) into an off-screen
     * buffer, make all near-black pixels transparent, and composite the
     * result over the cosmic-clock sky.  This lets the starry background
     * shine through the terminal's black areas while keeping the coloured
     * text fully visible.
     */
    @Override
    protected void paintChildren(Graphics g0) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) { super.paintChildren(g0); return; }

        // (Re-)allocate the buffer when the panel size changes
        if (childBuffer == null
                || childBuffer.getWidth() != w
                || childBuffer.getHeight() != h) {
            childBuffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        } else {
            // Clear previous frame
            int[] clr = ((DataBufferInt) childBuffer.getRaster().getDataBuffer()).getData();
            java.util.Arrays.fill(clr, 0);
        }

        // Paint children (terminal) into the off-screen buffer
        Graphics2D bg = childBuffer.createGraphics();
        super.paintChildren(bg);
        bg.dispose();

        // Walk every pixel; make near-black pixels transparent.
        // Most pixels outside the terminal area are already alpha=0
        // (because screen.setOpaque(false)), so the fast-path skip
        // keeps this very efficient.
        int[] data = ((DataBufferInt) childBuffer.getRaster().getDataBuffer()).getData();
        final int threshold = 30; // max-channel brightness below this → transparent
        for (int i = 0; i < data.length; i++) {
            int argb = data[i];
            if ((argb & 0xFF000000) == 0) continue; // already transparent

            int r  = (argb >> 16) & 0xFF;
            int gv = (argb >>  8) & 0xFF;
            int b  =  argb        & 0xFF;
            int brightness = Math.max(r, Math.max(gv, b));

            if (brightness < threshold) {
                // Smooth fade: pure black → fully transparent,
                // near-threshold → semi-transparent
                int a = (argb >>> 24) & 0xFF;
                int newAlpha = (brightness * a) / threshold;
                data[i] = (newAlpha << 24) | (argb & 0x00FFFFFF);
            }
        }

        ((Graphics2D) g0).drawImage(childBuffer, 0, 0, null);
    }

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

            // Collect visible bodies for tooltip hit-testing
            List<CelestialBody> bodies = new ArrayList<>();

            // ── Sun ──────────────────────────────────────────────
            double[] sunData = SolarSystemCalculator.sun(jd);
            double[] sunAltAz = equatorialToHorizontal(sunData[0], sunData[1], lstDeg, OBSERVER_LAT);
            if (sunAltAz[1] > -5) {
                int[] spx = projection.project(sunAltAz[0], sunAltAz[1], w, h);
                if (spx != null) {
                    bodies.add(new CelestialBody(spx[0], spx[1], 10, "☀ Sonne"));
                }
            }

            // ── Stars ──────────────────────────────────────────
            // Store projected pixel positions per star index for constellation lines
            int[][] starPixels = new int[StarCatalog.STARS.length][];

            for (int si = 0; si < StarCatalog.STARS.length; si++) {
                double[] star = StarCatalog.STARS[si];
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

                starPixels[si] = px;

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

                // Register for tooltip
                if (si < StarCatalog.STAR_NAMES.length) {
                    String name = StarCatalog.STAR_NAMES[si];
                    bodies.add(new CelestialBody(px[0], px[1], radius * 3, "✦ " + name));
                }

                // Glow for very bright stars
                if (mag < 1.0 && alpha > 0.3f) {
                    float glowAlpha = alpha * 0.15f;
                    g.setColor(new Color(r, gv, b, Math.max(1, (int)(glowAlpha * 255))));
                    double glowR = radius * 3;
                    g.fill(new Ellipse2D.Double(px[0] - glowR, px[1] - glowR,
                            glowR * 2, glowR * 2));
                }
            }

            // ── Constellation lines & labels ───────────────────
            Stroke oldStroke = g.getStroke();
            g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Font labelFont = g.getFont().deriveFont(Font.PLAIN, 10f);
            g.setFont(labelFont);

            for (ConstellationCatalog.Constellation con : ConstellationCatalog.CONSTELLATIONS) {
                boolean anyLineVisible = false;

                // Draw stick-figure lines
                for (int[] seg : con.lines) {
                    int idxA = seg[0];
                    int idxB = seg[1];
                    if (idxA >= starPixels.length || idxB >= starPixels.length) continue;
                    int[] pxA = starPixels[idxA];
                    int[] pxB = starPixels[idxB];
                    if (pxA == null || pxB == null) continue;

                    g.setColor(con.color);
                    g.drawLine(pxA[0], pxA[1], pxB[0], pxB[1]);
                    anyLineVisible = true;
                }

                // Draw constellation name near the label star
                if (anyLineVisible && con.labelStar >= 0 && con.labelStar < starPixels.length) {
                    int[] labelPx = starPixels[con.labelStar];
                    if (labelPx != null) {
                        // Offset label slightly above and to the right of the reference star
                        int lx = labelPx[0] + 6;
                        int ly = labelPx[1] - 6;
                        Color labelColor = new Color(
                                con.color.getRed(), con.color.getGreen(), con.color.getBlue(),
                                Math.min(180, con.color.getAlpha() + 80));
                        g.setColor(labelColor);
                        g.drawString(con.displayName(useGermanNames), lx, ly);
                    }
                }
            }
            g.setStroke(oldStroke);

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

                // Register for tooltip
                bodies.add(new CelestialBody(px[0], px[1], pr * 3, "● " + pl.name));
            }

            // ── Moon ───────────────────────────────────────────
            double[] moonData = SolarSystemCalculator.moon(jd);
            double[] moonAltAz = equatorialToHorizontal(moonData[0], moonData[1], lstDeg, OBSERVER_LAT);
            if (moonAltAz[1] > -3) {
                int[] px = projection.project(moonAltAz[0], moonAltAz[1], w, h);
                if (px != null) {
                    paintMoon(g, px[0], px[1], moonData[2], moonAltAz[1]);
                    bodies.add(new CelestialBody(px[0], px[1], 12, "🌙 Mond"));
                }
            }

            // Publish for tooltip hit-testing
            visibleBodies.clear();
            visibleBodies.addAll(bodies);

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

