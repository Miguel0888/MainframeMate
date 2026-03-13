package de.bund.zrb.ui.terminal.cosmicclock;

import java.awt.Color;

/**
 * Constellation stick-figure definitions for the cosmic clock.
 * <p>
 * Each constellation references stars by their index in {@link StarCatalog#STARS}
 * and defines line segments between them.  Colours are light pastels so the
 * lines stay subtle against the dark sky.
 */
public final class ConstellationCatalog {

    private ConstellationCatalog() {}

    public static class Constellation {
        public final String name;
        public final Color color;
        /** Each element is {@code {starIndex_A, starIndex_B}}. */
        public final int[][] lines;
        /** Star index near which the constellation label is placed. */
        public final int labelStar;

        Constellation(String name, Color color, int labelStar, int[]... lines) {
            this.name = name;
            this.color = color;
            this.labelStar = labelStar;
            this.lines = lines;
        }
    }

    /*
     * Star index reference (from StarCatalog.STARS):
     *  0 Sirius       1 Canopus      2 Arcturus     3 α Centauri   4 Vega
     *  5 Capella       6 Rigel        7 Procyon      8 Achernar     9 Betelgeuse
     * 10 Hadar        11 Adhara      12 Altair      13 Aldebaran   14 Antares
     * 15 Deneb        16 Mimosa      17 δ Crucis    18 Gacrux      19 Fomalhaut
     * 20 Polaris      21 Alnilam     22 Alnitak     23 Mintaka     24 Miaplacidus
     * 25 Spica        26 Regulus     27 Wezen       28 Mirzam      29 Aludra
     * 30 Mirach       31 Alpheratz   32 Schedar     33 Caph        34 Navi
     * 35 Ruchbah      36 Dubhe       37 Merak       38 Phecda      39 Megrez
     * 40 Alioth       41 Mizar       42 Alkaid      43 Saiph       44 Bellatrix
     * 45 Sadr         46 Pollux      47 Castor(dup) 48 Castor      49 Denebola
     * 50 Shaula       51 Dschubba    52 Acrab       53 Sheliak     54 Sulafat
     * 55 Alcyone      56 Elnath      57 Mirfak      58 Menkalinan  59 Avior
     */

    // ── Pastel colours (low saturation, high brightness) ─────
    private static final Color ROSE       = new Color(255, 160, 180, 100);
    private static final Color TURQUOISE  = new Color(100, 230, 220, 100);
    private static final Color LIGHT_YELLOW = new Color(255, 255, 160, 100);
    private static final Color LIGHT_ORANGE = new Color(255, 200, 130, 100);
    private static final Color LIGHT_BLUE   = new Color(140, 180, 255, 100);
    private static final Color LIGHT_GREEN  = new Color(160, 255, 180, 100);
    private static final Color LAVENDER     = new Color(200, 170, 255, 100);
    private static final Color CORAL        = new Color(255, 150, 140, 100);
    private static final Color MINT         = new Color(150, 255, 210, 100);
    private static final Color PEACH        = new Color(255, 210, 170, 100);

    public static final Constellation[] CONSTELLATIONS = {

        // ── Orion ────────────────────────────────────────────
        // Shoulders → belt → feet
        new Constellation("Orion", LIGHT_ORANGE, 21,
            new int[]{9, 44},    // Betelgeuse – Bellatrix
            new int[]{9, 22},    // Betelgeuse – Alnitak
            new int[]{44, 23},   // Bellatrix – Mintaka
            new int[]{23, 21},   // Mintaka – Alnilam (belt)
            new int[]{21, 22},   // Alnilam – Alnitak (belt)
            new int[]{22, 43},   // Alnitak – Saiph
            new int[]{23, 6},    // Mintaka – Rigel
            new int[]{43, 6}     // Saiph – Rigel
        ),

        // ── Großer Wagen (Big Dipper / Ursa Major) ───────────
        // Bowl + handle
        new Constellation("Gr. Wagen", TURQUOISE, 39,
            new int[]{36, 37},   // Dubhe – Merak
            new int[]{37, 38},   // Merak – Phecda
            new int[]{38, 39},   // Phecda – Megrez
            new int[]{39, 36},   // Megrez – Dubhe (close bowl)
            new int[]{39, 40},   // Megrez – Alioth
            new int[]{40, 41},   // Alioth – Mizar
            new int[]{41, 42}    // Mizar – Alkaid
        ),

        // ── Cassiopeia ───────────────────────────────────────
        // W-shape
        new Constellation("Cassiopeia", ROSE, 34,
            new int[]{33, 32},   // Caph – Schedar
            new int[]{32, 34},   // Schedar – Navi
            new int[]{34, 35}    // Navi – Ruchbah
        ),

        // ── Zwillinge (Gemini) ───────────────────────────────
        new Constellation("Gemini", LIGHT_YELLOW, 46,
            new int[]{46, 48}    // Pollux – Castor
        ),

        // ── Großer Hund (Canis Major) ────────────────────────
        new Constellation("Canis Major", LIGHT_BLUE, 0,
            new int[]{0, 28},    // Sirius – Mirzam
            new int[]{0, 27},    // Sirius – Wezen
            new int[]{27, 29},   // Wezen – Aludra
            new int[]{27, 11}    // Wezen – Adhara
        ),

        // ── Leier (Lyra) ────────────────────────────────────
        new Constellation("Lyra", LAVENDER, 4,
            new int[]{4, 53},    // Vega – Sheliak
            new int[]{53, 54},   // Sheliak – Sulafat
            new int[]{54, 4}     // Sulafat – Vega
        ),

        // ── Skorpion (Scorpius) ──────────────────────────────
        new Constellation("Scorpius", CORAL, 14,
            new int[]{52, 51},   // Acrab – Dschubba
            new int[]{51, 14},   // Dschubba – Antares
            new int[]{14, 50}    // Antares – Shaula
        ),

        // ── Löwe (Leo) ──────────────────────────────────────
        new Constellation("Leo", LIGHT_YELLOW, 26,
            new int[]{26, 49}    // Regulus – Denebola
        ),

        // ── Sommerdreieck (asterism) ─────────────────────────
        new Constellation("Sommerdreieck", MINT, 15,
            new int[]{4, 15},    // Vega – Deneb
            new int[]{15, 12},   // Deneb – Altair
            new int[]{12, 4}     // Altair – Vega
        ),

        // ── Stier (Taurus) ──────────────────────────────────
        new Constellation("Taurus", PEACH, 13,
            new int[]{13, 56},   // Aldebaran – Elnath
            new int[]{13, 55}    // Aldebaran – Alcyone (Pleiades direction)
        ),

        // ── Andromeda ────────────────────────────────────────
        new Constellation("Andromeda", LIGHT_GREEN, 30,
            new int[]{31, 30}    // Alpheratz – Mirach
        ),

        // ── Fuhrmann (Auriga) ───────────────────────────────
        new Constellation("Auriga", LIGHT_ORANGE, 5,
            new int[]{5, 58},    // Capella – Menkalinan
            new int[]{5, 56}     // Capella – Elnath (shared with Taurus)
        ),

        // ── Kreuz des Südens (Crux) ─────────────────────────
        new Constellation("Crux", TURQUOISE, 16,
            new int[]{18, 17},   // Gacrux – δ Crucis (vertical-ish)
            new int[]{16, 17}    // Mimosa – δ Crucis (horizontal-ish)
        ),
    };
}

