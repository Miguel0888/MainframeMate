package de.bund.zrb.mermaid;

import de.bund.zrb.wiki.ui.SvgRenderer;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic test: render mindmap SVG → dump to file → try Batik rasterisation.
 */
class MindmapBatikTest {

    private static final String MINDMAP =
            "mindmap\n"
                    + "  root((Humus))\n"
                    + "    Arten\n"
                    + "      N\u00e4hrhumus\n"
                    + "      Dauerhumus\n"
                    + "    Entstehung\n"
                    + "      Zersetzung organischer Stoffe\n"
                    + "      Bodenorganismen\n"
                    + "        Regenw\u00fcrmer\n"
                    + "        Bakterien\n"
                    + "        Pilze\n"
                    + "      Laub und Pflanzenreste\n"
                    + "    Bestandteile\n"
                    + "      Organische Substanz\n"
                    + "      Mineralstoffe\n"
                    + "      Wasser\n"
                    + "      Luft\n"
                    + "    Funktionen\n"
                    + "      N\u00e4hrstoffspeicher\n"
                    + "      Wasserspeicherung\n"
                    + "      Bodenlockerung\n"
                    + "      F\u00f6rderung des Bodenlebens\n"
                    + "      Kohlenstoffspeicher\n"
                    + "    Bedeutung f\u00fcr Pflanzen\n"
                    + "      Bessere Wurzelbildung\n"
                    + "      N\u00e4hrstoffversorgung\n"
                    + "      Schutz vor Austrocknung\n"
                    + "    Einflussfaktoren\n"
                    + "      Klima\n"
                    + "      Feuchtigkeit\n"
                    + "      pH-Wert\n"
                    + "      Bodenart\n"
                    + "      Bewirtschaftung\n"
                    + "    Gefahren\n"
                    + "      Erosion\n"
                    + "      \u00dcberd\u00fcngung\n"
                    + "      Verdichtung\n"
                    + "      Austrocknung\n"
                    + "    F\u00f6rderung\n"
                    + "      Kompost\n"
                    + "      Mulchen\n"
                    + "      Fruchtfolge\n"
                    + "      Gr\u00fcnd\u00fcngung\n"
                    + "      Schonende Bodenbearbeitung";

    @Test
    void dumpAndTestMindmapSvg() throws Exception {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            System.out.println("Mermaid not available — skip");
            return;
        }

        // Step 1: Get raw SVG via detailed API (before postProcessSvg)
        JsExecutionResult detailed = renderer.renderToSvgDetailed(MINDMAP);
        assertTrue(detailed.isSuccessful(), "Render should succeed: " + detailed.getErrorMessage());
        String raw = detailed.getOutput();
        assertNotNull(raw);
        assertFalse(raw.startsWith("ERROR:"), "Should not be an error: " + raw.substring(0, Math.min(200, raw.length())));

        System.out.println("=== RAW SVG length: " + raw.length() + " ===");
        System.out.println("Contains <foreignObject>: " + raw.toLowerCase().contains("foreignobject"));
        System.out.println("Contains nested <svg>: " + countOccurrences(raw, "<svg"));
        System.out.println("Contains @keyframes: " + raw.contains("@keyframes"));
        System.out.println("Contains hsl(: " + raw.contains("hsl("));
        System.out.println("Contains rgba(: " + raw.contains("rgba("));
        System.out.println("Contains alignment-baseline: " + raw.contains("alignment-baseline"));
        System.out.println("Contains currentColor: " + raw.contains("currentColor"));

        // Save raw SVG
        File outDir = new File("build/mindmap-debug");
        outDir.mkdirs();
        save(new File(outDir, "1-raw.svg"), raw);

        // Step 2: Apply postProcessSvg
        String postProcessed = MermaidRenderer.postProcessSvg(raw);
        System.out.println("\n=== POST-PROCESSED SVG length: " + postProcessed.length() + " ===");
        save(new File(outDir, "2-postprocessed.svg"), postProcessed);

        // Step 3: Apply fixForBatik
        String fixed = MermaidSvgFixup.fixForBatik(postProcessed);
        System.out.println("\n=== FIXED SVG length: " + fixed.length() + " ===");
        save(new File(outDir, "3-fixed.svg"), fixed);

        // === ANALYSIS of problematic patterns ===
        System.out.println("\n=== SVG ANALYSIS ===");
        System.out.println("'undefined' occurrences: " + countOccurrences(fixed, "undefined"));
        System.out.println("'dominant-baseline' in CSS: " + countOccurrences(fixed, "dominant-baseline"));
        System.out.println("'CDATA' present: " + fixed.contains("CDATA"));

        // style attributes with "undefined"
        Pattern undefinedStyle = Pattern.compile("style=\"[^\"]*undefined[^\"]*\"");
        Matcher um = undefinedStyle.matcher(fixed);
        int undCount = 0;
        while (um.find()) {
            if (undCount < 3) System.out.println("  style-undef: " + um.group());
            undCount++;
        }
        System.out.println("  Total style attrs with 'undefined': " + undCount);

        // negative stroke-width
        Pattern negStroke = Pattern.compile("stroke-width\\s*:\\s*-\\d+");
        Matcher ns = negStroke.matcher(fixed);
        int negCount = 0;
        while (ns.find()) {
            if (negCount < 3) System.out.println("  neg stroke: " + ns.group());
            negCount++;
        }
        System.out.println("  Total negative stroke-widths: " + negCount);

        // CSS color: (not fill/stroke — Batik ignores this)
        System.out.println("  'color:' in CSS: " + countOccurrences(fixed, "color:"));

        // Check for @keyframes, hsl, rgba
        System.out.println("  '@keyframes' remaining: " + countOccurrences(fixed, "@keyframes"));
        System.out.println("  'hsl(' remaining: " + countOccurrences(fixed, "hsl("));
        System.out.println("  'rgba(' remaining: " + countOccurrences(fixed, "rgba("));
        System.out.println("  'alignment-baseline' remaining: " + countOccurrences(fixed, "alignment-baseline"));

        // Print first 200 chars of <style> block
        int styleStart = fixed.indexOf("<style>");
        int styleEnd = fixed.indexOf("</style>");
        if (styleStart >= 0 && styleEnd > styleStart) {
            String styleContent = fixed.substring(styleStart + 7, Math.min(styleStart + 500, styleEnd));
            System.out.println("\n  <style> first 500 chars: " + styleContent);
            // Check for negative stroke-width in CSS
            String fullStyle = fixed.substring(styleStart + 7, styleEnd);
            Pattern negStrokeCSS = Pattern.compile("stroke-width\\s*:\\s*-\\d+");
            Matcher nsCss = negStrokeCSS.matcher(fullStyle);
            int cssNegCount = 0;
            while (nsCss.find()) {
                if (cssNegCount < 3) System.out.println("  CSS neg stroke: " + nsCss.group());
                cssNegCount++;
            }
            System.out.println("  CSS negative stroke-widths: " + cssNegCount);
        }

        // Verify valid XML
        javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
        try {
            org.w3c.dom.Document doc = db.parse(new java.io.ByteArrayInputStream(fixed.getBytes("UTF-8")));
            System.out.println("\nValid XML - root: " + doc.getDocumentElement().getTagName());
        } catch (Exception e) {
            System.out.println("\nINVALID XML: " + e.getMessage());
            fail("Fixed SVG is not valid XML: " + e.getMessage());
        }

        // Step 4: Try Batik with full exception logging
        System.out.println("\n=== BATIK RASTERISATION ===");

        // Enable Batik logging
        java.util.logging.Logger batikLogger = java.util.logging.Logger.getLogger("org.apache.batik");
        batikLogger.setLevel(java.util.logging.Level.ALL);
        java.util.logging.ConsoleHandler ch = new java.util.logging.ConsoleHandler();
        ch.setLevel(java.util.logging.Level.ALL);
        batikLogger.addHandler(ch);

        java.util.logging.Logger svgLogger = java.util.logging.Logger.getLogger("de.bund.zrb.wiki.ui.SvgRenderer");
        svgLogger.setLevel(java.util.logging.Level.ALL);
        svgLogger.addHandler(ch);

        try {
            byte[] bytes = fixed.getBytes(Charset.forName("UTF-8"));
            BufferedImage img = SvgRenderer.renderToBufferedImageForced(bytes, 800);
            if (img != null) {
                System.out.println("Batik SUCCESS: " + img.getWidth() + " x " + img.getHeight());
            } else {
                System.out.println("Batik returned null — trying to get more info...");

                // Try with a simpler approach - strip ALL CSS
                String noCss = fixed.replaceAll("<style[^>]*>.*?</style>", "");
                save(new File(outDir, "4-no-css.svg"), noCss);
                byte[] noCssBytes = noCss.getBytes(Charset.forName("UTF-8"));
                BufferedImage noCssImg = SvgRenderer.renderToBufferedImageForced(noCssBytes, 800);
                if (noCssImg != null) {
                    System.out.println("WITHOUT CSS -> Batik SUCCESS: " + noCssImg.getWidth() + " x " + noCssImg.getHeight());
                    System.out.println(">>> CSS is the problem! <<<");
                } else {
                    System.out.println("WITHOUT CSS -> Batik still null");

                    // Try with minimum SVG
                    String minimal = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"-174 -60 1618 696\" width=\"2000\" height=\"861\">"
                            + "<circle cx=\"283\" cy=\"230\" r=\"32\" fill=\"blue\"/>"
                            + "<text x=\"283\" y=\"230\" text-anchor=\"middle\" fill=\"white\">Humus</text>"
                            + "</svg>";
                    byte[] minBytes = minimal.getBytes(Charset.forName("UTF-8"));
                    BufferedImage minImg = SvgRenderer.renderToBufferedImageForced(minBytes, 800);
                    System.out.println("MINIMAL SVG -> " + (minImg != null ? "OK " + minImg.getWidth() + "x" + minImg.getHeight() : "FAIL"));
                }
                fail("Batik cannot render the mindmap SVG");
            }
        } catch (Exception e) {
            System.out.println("Batik EXCEPTION: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
            fail("Batik threw exception: " + e.getMessage());
        }
    }

    private void save(File f, String content) {
        try {
            FileWriter fw = new FileWriter(f);
            fw.write(content);
            fw.close();
            System.out.println("  Saved: " + f.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("  Save failed: " + e.getMessage());
        }
    }

    private int countOccurrences(String text, String search) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) >= 0) {
            count++;
            idx += search.length();
        }
        return count;
    }
}

