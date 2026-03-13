package de.bund.zrb.ui.terminal.cosmicclock;

import static de.bund.zrb.ui.terminal.cosmicclock.CelestialMath.*;

/**
 * Calculates equatorial positions (RA/Dec) for the Sun, Moon and the five
 * naked-eye planets for a given Julian Date.
 * <p>
 * Accuracy is ~1–2° — sufficient for a visual cosmic-clock background.
 * Algorithms follow Jean Meeus, <i>Astronomical Algorithms</i>.
 */
public final class SolarSystemCalculator {

    private SolarSystemCalculator() { }

    // ── Sun ────────────────────────────────────────────────────

    /**
     * @return double[]{raDeg, decDeg, eclipticLongDeg}
     */
    public static double[] sun(double jd) {
        double T = julianCenturiesSinceJ2000(jd);
        double M = normalizeDeg(357.5291 + 35999.0503 * T);  // mean anomaly
        double C = 1.9146 * sind(M) + 0.0200 * sind(2 * M) + 0.0003 * sind(3 * M);
        double sunLong = normalizeDeg(280.4665 + 36000.7698 * T + C);
        double obl = obliquity(T);
        double[] eq = eclipticToEquatorial(sunLong, 0, obl);
        return new double[]{eq[0], eq[1], sunLong};
    }

    // ── Moon ───────────────────────────────────────────────────

    /**
     * Simplified lunar position.
     * @return double[]{raDeg, decDeg, phaseAngleDeg}
     */
    public static double[] moon(double jd) {
        double T = julianCenturiesSinceJ2000(jd);

        double Lp = normalizeDeg(218.3165 + 481267.8813 * T); // mean longitude
        double D  = normalizeDeg(297.8502 + 445267.1115 * T); // mean elongation
        double M  = normalizeDeg(357.5291 + 35999.0503  * T); // Sun mean anomaly
        double Mp = normalizeDeg(134.9634 + 477198.8676 * T); // Moon mean anomaly
        double F  = normalizeDeg(93.2721  + 483202.0175 * T); // argument of latitude

        // Ecliptic longitude corrections
        double lon = Lp
                + 6.289 * sind(Mp)
                - 1.274 * sind(2 * D - Mp)
                + 0.658 * sind(2 * D)
                + 0.214 * sind(2 * Mp)
                - 0.186 * sind(M)
                - 0.114 * sind(2 * F);
        lon = normalizeDeg(lon);

        // Ecliptic latitude
        double lat = 5.128 * sind(F)
                + 0.281 * sind(Mp + F)
                + 0.278 * sind(Mp - F)
                + 0.173 * sind(2 * D - F);

        double obl = obliquity(T);
        double[] eq = eclipticToEquatorial(lon, lat, obl);

        // Phase angle ≈ elongation from Sun
        double[] sunPos = sun(jd);
        double phase = normalizeDeg(lon - sunPos[2]);

        return new double[]{eq[0], eq[1], phase};
    }

    // ── Planets ────────────────────────────────────────────────

    /**
     * Heliocentric → geocentric RA/Dec for a planet.
     * @return double[]{raDeg, decDeg}  or null if calculation fails
     */
    public static double[] planet(double jd, PlanetElements planet) {
        double T = julianCenturiesSinceJ2000(jd);

        // Planet heliocentric ecliptic coordinates
        double a  = planet.a0  + planet.aD  * T;
        double e  = planet.e0  + planet.eD  * T;
        double I  = planet.I0  + planet.ID  * T;
        double L  = normalizeDeg(planet.L0  + planet.LD  * T);
        double w  = normalizeDeg(planet.w0  + planet.wD  * T);
        double Om = normalizeDeg(planet.Om0 + planet.OmD * T);

        double M = normalizeDeg(L - w);
        double E = solveKepler(M, e);
        double v = trueAnomaly(E, e);
        double r = a * (1 - e * e) / (1 + e * cosd(v));

        double lonHelio = normalizeDeg(v + w);
        double xHelio = r * (cosd(Om) * cosd(lonHelio - Om)
                - sind(Om) * sind(lonHelio - Om) * cosd(I));
        double yHelio = r * (sind(Om) * cosd(lonHelio - Om)
                + cosd(Om) * sind(lonHelio - Om) * cosd(I));
        double zHelio = r * sind(lonHelio - Om) * sind(I);

        // Earth heliocentric (simplified — use Sun position mirrored)
        double[] sunEq = sun(jd);
        double sunLon = sunEq[2];
        double earthLon = normalizeDeg(sunLon + 180);
        // Earth distance ~1 AU, eccentricity ignored for simplicity
        double xEarth = cosd(earthLon);
        double yEarth = sind(earthLon);

        // Geocentric ecliptic
        double xGeo = xHelio - xEarth;
        double yGeo = yHelio - yEarth;
        double zGeo = zHelio; // Earth's orbit is in the ecliptic plane

        double lonGeo = normalizeDeg(Math.toDegrees(Math.atan2(yGeo, xGeo)));
        double dist = Math.sqrt(xGeo * xGeo + yGeo * yGeo + zGeo * zGeo);
        double latGeo = Math.toDegrees(Math.asin(zGeo / dist));

        double obl = obliquity(T);
        return eclipticToEquatorial(lonGeo, latGeo, obl);
    }

    // ── Orbital elements (J2000.0 + rate per Julian century) ───

    /** Keplerian elements: a, e, I, L, w(lon perihelion), Omega.  Rates per century. */
    public static class PlanetElements {
        final double a0, aD, e0, eD, I0, ID, L0, LD, w0, wD, Om0, OmD;
        /** Display colour as 0xRRGGBB. */
        public final int color;
        /** Display radius in pixels. */
        public final int radius;
        public final String name;

        PlanetElements(String name, int color, int radius,
                       double a0, double aD, double e0, double eD,
                       double I0, double ID, double L0, double LD,
                       double w0, double wD, double Om0, double OmD) {
            this.name = name; this.color = color; this.radius = radius;
            this.a0 = a0; this.aD = aD; this.e0 = e0; this.eD = eD;
            this.I0 = I0; this.ID = ID; this.L0 = L0; this.LD = LD;
            this.w0 = w0; this.wD = wD; this.Om0 = Om0; this.OmD = OmD;
        }
    }

    // JPL approximate elements (https://ssd.jpl.nasa.gov/planets/approx_pos.html)
    public static final PlanetElements MERCURY = new PlanetElements(
            "Mercury", 0xB0B0B0, 2,
            0.38710,  0.0,      0.20563, 0.00002,
            7.005,    0.0019,   252.251, 149472.675,
            77.458,   0.1600,   48.331,  0.1250);

    public static final PlanetElements VENUS = new PlanetElements(
            "Venus", 0xFFFFE0, 3,
            0.72333,  0.0,      0.00677, -0.00005,
            3.395,    0.0010,   181.980, 58517.816,
            131.564,  0.0050,   76.680,  0.0900);

    public static final PlanetElements MARS = new PlanetElements(
            "Mars", 0xFF6040, 3,
            1.52368,  0.0,      0.09340,  0.00009,
            1.850,    -0.0007,  355.453,  19140.300,
            336.061,  0.4440,   49.558,   0.2710);

    public static final PlanetElements JUPITER = new PlanetElements(
            "Jupiter", 0xFFD090, 4,
            5.20260,  0.0,      0.04849,  0.00016,
            1.303,    -0.0019,  34.351,   3034.906,
            14.331,   0.2150,   100.464,  0.1770);

    public static final PlanetElements SATURN = new PlanetElements(
            "Saturn", 0xF0D080, 3,
            9.55491,  0.0,      0.05551, -0.00035,
            2.489,    0.0025,   50.077,   1222.114,
            93.057,   0.5650,   113.665,  0.0500);

    public static final PlanetElements[] ALL_PLANETS = {
            MERCURY, VENUS, MARS, JUPITER, SATURN
    };

    // ── Helpers ────────────────────────────────────────────────

    private static double sind(double deg) { return Math.sin(Math.toRadians(deg)); }
    private static double cosd(double deg) { return Math.cos(Math.toRadians(deg)); }
}

