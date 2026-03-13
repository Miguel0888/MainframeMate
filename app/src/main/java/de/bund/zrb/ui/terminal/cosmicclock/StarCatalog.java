package de.bund.zrb.ui.terminal.cosmicclock;

/**
 * Embedded catalogue of the brightest stars visible to the naked eye.
 * <p>
 * Each entry: {@code {RA_hours, Dec_degrees, apparentMagnitude, B-V_colorIndex}}.
 * Epoch J2000.0 — proper motion is negligible for visualisation purposes.
 */
public final class StarCatalog {

    private StarCatalog() { }

    /**
     * Bright-star data.  Columns:
     * <ol>
     *   <li>Right Ascension (hours, 0–24)</li>
     *   <li>Declination (degrees, −90 to +90)</li>
     *   <li>Apparent visual magnitude</li>
     *   <li>B−V colour index (negative = blue, 0 = white, positive = red)</li>
     * </ol>
     */
    public static final double[][] STARS = {
        // ── Magnitude ≤ 1.0 ──────────────────────────────────────
        { 6.752, -16.716,  -1.46,  0.00}, // Sirius
        { 6.399, -52.696,  -0.74,  0.16}, // Canopus
        {14.261, +19.182,  -0.05,  1.23}, // Arcturus
        {14.660, -60.835,  -0.01, -0.24}, // Alpha Centauri A
        {18.616, +38.784,   0.03,  0.00}, // Vega
        { 5.278, +45.998,   0.08,  0.80}, // Capella
        { 5.242,  -8.202,   0.13, -0.03}, // Rigel
        { 7.655,  +5.225,   0.34,  0.42}, // Procyon
        { 1.628, -57.237,   0.46, -0.16}, // Achernar
        { 5.919,  +7.407,   0.50,  1.85}, // Betelgeuse
        {14.064, -60.373,   0.61, -0.23}, // Hadar
        { 7.577, -28.972,   0.71, -0.26}, // Adhara
        {19.846,  +8.868,   0.77,  0.22}, // Altair
        { 4.598, +16.509,   0.85,  1.54}, // Aldebaran
        {16.490, -26.432,   0.96,  1.83}, // Antares
        {20.690, +45.280,   1.25,  0.09}, // Deneb

        // ── 1.0 < Magnitude ≤ 2.0 ───────────────────────────────
        {12.443, -63.099,   0.77, -0.24}, // Mimosa
        {12.795, -59.688,   1.63, -0.24}, // Delta Crucis
        {12.264, -57.113,   1.40,  1.59}, // Gacrux
        {22.960, -29.622,   1.16, -0.23}, // Fomalhaut
        { 2.120, +89.264,   1.98,  0.60}, // Polaris
        { 5.418, -01.202,   1.70, -0.19}, // Alnilam
        { 5.680, -01.943,   1.77, -0.22}, // Alnitak
        { 5.534, -01.202,   2.09, -0.17}, // Mintaka
        { 5.603, -69.756,   0.85, -0.22}, // Miaplacidus
        {13.398, -11.161,   0.98, -0.24}, // Spica
        {10.139, +11.967,   1.35,  0.00}, // Regulus
        { 7.139, -26.393,   1.50, -0.21}, // Wezen
        { 6.378, -17.956,   1.50,  0.07}, // Mirzam
        { 6.977, -28.972,   1.84,  1.73}, // Aludra
        { 1.162, +35.621,   2.00, -0.11}, // Mirach
        { 0.140, +29.091,   2.06,  0.00}, // Alpheratz
        { 0.726, +56.537,   2.23,  0.34}, // Schedar
        { 0.153, +59.150,   2.27, -0.05}, // Caph
        { 0.945, +60.717,   2.47,  0.58}, // Navi

        // ...existing code...
        { 1.907, +60.235,   2.68,  0.13}, // Ruchbah

        // ── Big Dipper / Ursa Major ──────────────────────────────
        {11.062, +61.751,   1.79, -0.02}, // Dubhe
        {11.031, +56.382,   2.37,  0.00}, // Merak
        {11.897, +53.695,   2.44,  0.00}, // Phecda
        {12.257, +57.033,   3.31,  0.00}, // Megrez
        {12.900, +55.960,   1.77, -0.02}, // Alioth
        {13.399, +54.925,   2.27,  0.01}, // Mizar
        {13.792, +49.313,   1.86, -0.19}, // Alkaid

        // ── Orion extras ─────────────────────────────────────────
        { 5.920, -01.942,   2.06, -0.18}, // Saiph
        { 5.586, +09.934,   2.06,  0.18}, // Bellatrix

        // ── Summer Triangle / Cygnus extras ──────────────────────
        {20.370, +40.257,   2.20, -0.06}, // Sadr

        // ── Gemini ───────────────────────────────────────────────
        { 7.576, +31.888,   1.14,  0.03}, // Pollux
        { 7.577, +31.888,   1.58,  0.00}, // Castor
        { 7.577, +31.888,   1.58,  0.04}, // Castor

        // ── Leo ──────────────────────────────────────────────────
        {11.817, +14.572,   2.14,  0.09}, // Denebola

        // ── Scorpius ─────────────────────────────────────────────
        {17.560, -37.104,   1.63, -0.22}, // Shaula
        {16.006, -22.622,   2.62, -0.07}, // Dschubba
        {16.091, -19.806,   2.29,  0.02}, // Acrab

        // ── Lyra / Aquila ────────────────────────────────────────
        {18.982, +32.690,   3.26,  0.00}, // Sheliak
        {18.746, +37.605,   3.24,  0.00}, // Sulafat

        // ── Taurus ───────────────────────────────────────────────
        { 3.791, +24.105,   2.87,  0.00}, // Alcyone
        { 5.438, +28.608,   1.65, -0.13}, // Elnath

        // ── Perseus ──────────────────────────────────────────────
        { 3.405, +49.861,   1.79, -0.15}, // Mirfak

        // ── Auriga ───────────────────────────────────────────────
        { 5.993, +44.948,   2.69, -0.08}, // Menkalinan

        // ── Bright southern stars visible from Berlin horizon ────
        { 8.375, -59.510,   1.68, -0.22}, // Avior
    };

    /** Names of the stars in the same order as {@link #STARS}. */
    public static final String[] STAR_NAMES = {
        // Magnitude ≤ 1.0
        "Sirius", "Canopus", "Arcturus", "α Centauri", "Vega",
        "Capella", "Rigel", "Procyon", "Achernar", "Betelgeuse",
        "Hadar", "Adhara", "Altair", "Aldebaran", "Antares", "Deneb",
        // 1.0 < Magnitude ≤ 2.0
        "Mimosa", "δ Crucis", "Gacrux", "Fomalhaut", "Polaris",
        "Alnilam", "Alnitak", "Mintaka", "Miaplacidus", "Spica",
        "Regulus", "Wezen", "Mirzam", "Aludra", "Mirach",
        "Alpheratz", "Schedar", "Caph", "Navi",
        // Cassiopeia
        "Ruchbah",
        // Big Dipper
        "Dubhe", "Merak", "Phecda", "Megrez", "Alioth", "Mizar", "Alkaid",
        // Orion extras
        "Saiph", "Bellatrix",
        // Summer Triangle / Cygnus
        "Sadr",
        // Gemini
        "Pollux", "Castor", "Castor",
        // Leo
        "Denebola",
        // Scorpius
        "Shaula", "Dschubba", "Acrab",
        // Lyra / Aquila
        "Sheliak", "Sulafat",
        // Taurus
        "Alcyone", "Elnath",
        // Perseus
        "Mirfak",
        // Auriga
        "Menkalinan",
        // Southern
        "Avior",
    };

    /**
     * Approximate star colour from B-V index.
     * Returns an RGB int (0xRRGGBB) with no alpha.
     */
    public static int bvToRgb(double bv) {
        // Clamp to reasonable range
        bv = Math.max(-0.4, Math.min(2.0, bv));

        double r, g, b;

        if (bv < 0.0) {
            // Blue-white stars
            r = 0.62 + bv * 0.4;
            g = 0.70 + bv * 0.2;
            b = 1.0;
        } else if (bv < 0.4) {
            // White stars
            r = 0.82 + bv * 0.45;
            g = 0.84 + bv * 0.25;
            b = 1.0 - bv * 0.5;
        } else if (bv < 1.0) {
            // Yellow-ish stars
            double t = (bv - 0.4) / 0.6;
            r = 1.0;
            g = 0.94 - t * 0.30;
            b = 0.80 - t * 0.55;
        } else {
            // Orange-red stars
            double t = (bv - 1.0) / 1.0;
            r = 1.0;
            g = 0.64 - t * 0.30;
            b = 0.25 - t * 0.20;
        }

        int ri = clamp((int) (r * 255), 0, 255);
        int gi = clamp((int) (g * 255), 0, 255);
        int bi = clamp((int) (b * 255), 0, 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}

