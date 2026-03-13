package de.bund.zrb.ui.terminal.cosmicclock;

/**
 * Low-level astronomical math helpers.
 * All angles are in <b>degrees</b> unless otherwise noted.
 */
public final class CelestialMath {

    private CelestialMath() { }

    // ── Julian Date ────────────────────────────────────────────

    /** Julian Date of the J2000.0 epoch (2000-Jan-1.5 TT). */
    public static final double J2000 = 2451545.0;

    /** Convert Unix milliseconds to Julian Date. */
    public static double julianDate(long millis) {
        return millis / 86_400_000.0 + 2440587.5;
    }

    /** Julian centuries since J2000.0. */
    public static double julianCenturiesSinceJ2000(double jd) {
        return (jd - J2000) / 36525.0;
    }

    // ── Sidereal Time ──────────────────────────────────────────

    /**
     * Greenwich Mean Sidereal Time in degrees for a given Julian Date.
     * Meeus, "Astronomical Algorithms", Ch. 12.
     */
    public static double gmst(double jd) {
        double T = julianCenturiesSinceJ2000(jd);
        double gmst = 280.46061837
                + 360.98564736629 * (jd - J2000)
                + 0.000387933 * T * T
                - T * T * T / 38710000.0;
        return normalizeDeg(gmst);
    }

    /** Local Sidereal Time in degrees. */
    public static double lst(double jd, double longitudeDeg) {
        return normalizeDeg(gmst(jd) + longitudeDeg);
    }

    // ── Coordinate transforms ──────────────────────────────────

    /** Mean obliquity of the ecliptic in degrees (Meeus). */
    public static double obliquity(double T) {
        return 23.439291 - 0.0130042 * T
                - 1.64e-7 * T * T + 5.04e-7 * T * T * T;
    }

    /**
     * Ecliptic (lambda, beta) → equatorial (RA, Dec).
     * @return double[]{raDeg, decDeg}
     */
    public static double[] eclipticToEquatorial(double lambdaDeg, double betaDeg, double oblDeg) {
        double lam = Math.toRadians(lambdaDeg);
        double bet = Math.toRadians(betaDeg);
        double eps = Math.toRadians(oblDeg);

        double sinDec = Math.sin(bet) * Math.cos(eps)
                + Math.cos(bet) * Math.sin(eps) * Math.sin(lam);
        double dec = Math.asin(sinDec);

        double y = Math.sin(lam) * Math.cos(eps) - Math.tan(bet) * Math.sin(eps);
        double x = Math.cos(lam);
        double ra = Math.atan2(y, x);

        return new double[]{normalizeDeg(Math.toDegrees(ra)), Math.toDegrees(dec)};
    }

    /**
     * Equatorial (RA, Dec) → horizontal (Az, Alt) for a given observer.
     * @param raDeg   right ascension in degrees
     * @param decDeg  declination in degrees
     * @param lstDeg  local sidereal time in degrees
     * @param latDeg  observer latitude in degrees
     * @return double[]{azimuthDeg, altitudeDeg}
     */
    public static double[] equatorialToHorizontal(double raDeg, double decDeg,
                                                   double lstDeg, double latDeg) {
        double ha = Math.toRadians(lstDeg - raDeg);
        double dec = Math.toRadians(decDeg);
        double lat = Math.toRadians(latDeg);

        double sinAlt = Math.sin(dec) * Math.sin(lat)
                + Math.cos(dec) * Math.cos(lat) * Math.cos(ha);
        double alt = Math.asin(sinAlt);

        double cosAz = (Math.sin(dec) - Math.sin(alt) * Math.sin(lat))
                / (Math.cos(alt) * Math.cos(lat));
        cosAz = Math.max(-1, Math.min(1, cosAz)); // clamp for safety
        double az = Math.acos(cosAz);

        if (Math.sin(ha) > 0) az = 2 * Math.PI - az;

        return new double[]{Math.toDegrees(az), Math.toDegrees(alt)};
    }

    // ── Kepler Equation ────────────────────────────────────────

    /**
     * Solve Kepler's equation M = E - e·sin(E) iteratively.
     * @param M_deg mean anomaly in degrees
     * @param e     eccentricity
     * @return eccentric anomaly in degrees
     */
    public static double solveKepler(double M_deg, double e) {
        double M = Math.toRadians(normalizeDeg(M_deg));
        double E = M;
        for (int i = 0; i < 10; i++) {
            double dE = (M - E + e * Math.sin(E)) / (1 - e * Math.cos(E));
            E += dE;
            if (Math.abs(dE) < 1e-9) break;
        }
        return Math.toDegrees(E);
    }

    /**
     * True anomaly from eccentric anomaly and eccentricity.
     * @return true anomaly in degrees
     */
    public static double trueAnomaly(double E_deg, double e) {
        double E = Math.toRadians(E_deg);
        double v = 2 * Math.atan2(
                Math.sqrt(1 + e) * Math.sin(E / 2),
                Math.sqrt(1 - e) * Math.cos(E / 2));
        return normalizeDeg(Math.toDegrees(v));
    }

    // ── Utilities ──────────────────────────────────────────────

    /** Normalize an angle to [0, 360). */
    public static double normalizeDeg(double deg) {
        deg = deg % 360.0;
        if (deg < 0) deg += 360.0;
        return deg;
    }

    /** Normalize an angle to [0, 2π). */
    public static double normalizeRad(double rad) {
        rad = rad % (2 * Math.PI);
        if (rad < 0) rad += 2 * Math.PI;
        return rad;
    }

    /** Linear interpolation. */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}

