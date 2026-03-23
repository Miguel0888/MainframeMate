package de.bund.zrb.mermaid;

import com.aresstack.mermaid.JsExecutionResult;
import com.aresstack.mermaid.MermaidRenderer;
import com.aresstack.mermaid.MermaidSvgFixup;
import de.bund.zrb.wiki.ui.SvgRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diagnostic Batik smoke test: tries multiple SVG processing approaches
 * to find which pipeline step breaks Batik rendering.
 * <p>
 * Approach:
 * <ol>
 *   <li>A0: Hand-crafted minimal SVG (Batik sanity check)</li>
 *   <li>A1: Truly raw Mermaid SVG (before any Java post-processing)</li>
 *   <li>A2: Minimal fixes only (xmlns, alignment-baseline, hsl→hex)</li>
 *   <li>A3: postProcessSvg() only (no fixForBatik)</li>
 *   <li>A4: Full pipeline (postProcessSvg + fixForBatik)</li>
 * </ol>
 * Starts via Gradle: {@code ./gradlew :app:testBatikSmoke}
 */
public final class BatikSmokeTest {

    private static final String DIAGRAM =
            "graph TD\n"
                    + "    A[Start] --> B{Entscheidung}\n"
                    + "    B -- Ja --> C[Aktion]\n"
                    + "    B -- Nein --> D[Ende]\n"
                    + "    C --> D";

    private static final File OUT_DIR = new File("build/batik-smoke");

    public static void main(String[] args) {
        OUT_DIR.mkdirs();

        // Enable ALL logging for Batik/SvgRenderer
        Logger svgLogger = Logger.getLogger("de.bund.zrb.wiki.ui.SvgRenderer");
        svgLogger.setLevel(Level.ALL);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        svgLogger.addHandler(handler);

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║         Batik Smoke Test — Diagnostic Pipeline          ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        // ── A0: Sanity check ──
        System.out.println("\n── A0: Hand-crafted SVG (Batik sanity check) ──");
        String minimalSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"300\" height=\"150\" viewBox=\"0 0 300 150\">"
                + "<rect width=\"300\" height=\"150\" fill=\"#ECECFF\" stroke=\"#9370DB\" rx=\"10\"/>"
                + "<text x=\"150\" y=\"80\" text-anchor=\"middle\" font-size=\"18\" fill=\"#333\">Batik OK!</text>"
                + "</svg>";
        BufferedImage a0img = tryBatik("A0", minimalSvg);
        if (a0img == null) {
            System.err.println("FATAL: Even hand-crafted SVG fails → Batik itself is broken.");
            System.exit(1);
        }
        System.out.println("A0: ✅ " + a0img.getWidth() + "×" + a0img.getHeight());

        // ── Render Mermaid ──
        System.out.println("\n── Rendering Mermaid diagram ──");
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            System.err.println("FATAL: Mermaid bundle not available!");
            System.exit(1);
        }

        // Get truly raw SVG (no postProcessSvg)
        JsExecutionResult detailed = renderer.renderToSvgDetailed(DIAGRAM);
        if (!detailed.isSuccessful() || detailed.getOutput() == null || detailed.getOutput().isEmpty()) {
            System.err.println("FATAL: Mermaid render failed: " + detailed.getErrorMessage());
            System.exit(1);
        }
        String trulyRaw = detailed.getOutput();
        if (trulyRaw.startsWith("ERROR:")) {
            System.err.println("FATAL: Mermaid render error: " + trulyRaw);
            System.exit(1);
        }
        System.out.println("Truly raw SVG: " + trulyRaw.length() + " chars");
        save("raw_truly.svg", trulyRaw);

        // Print structure info
        System.out.println("  Contains <foreignObject>: " + trulyRaw.toLowerCase().contains("foreignobject"));
        System.out.println("  Nested <svg> count: " + countOccurrences(trulyRaw, "<svg"));
        System.out.println("  <g class=\"node\"> count: " + countOccurrences(trulyRaw, "class=\"node"));
        System.out.println("  viewBox: " + extractAttr(trulyRaw, "viewBox"));

        // ── A1: Truly raw SVG ──
        System.out.println("\n── A1: Truly raw Mermaid SVG → Batik ──");
        BufferedImage a1img = tryBatik("A1", trulyRaw);
        report("A1 (truly raw)", a1img);

        // ── A2: Minimal essential fixes ──
        System.out.println("\n── A2: Minimal fixes (xmlns + alignment-baseline + hsl) ──");
        String minimal = applyMinimalFixes(trulyRaw);
        save("raw_minimal.svg", minimal);
        System.out.println("  Minimal SVG: " + minimal.length() + " chars");
        System.out.println("  Nested <svg> count: " + countOccurrences(minimal, "<svg"));
        System.out.println("  <foreignObject> present: " + minimal.toLowerCase().contains("foreignobject"));
        BufferedImage a2img = tryBatik("A2", minimal);
        report("A2 (minimal fixes)", a2img);

        // ── A3: renderToSvg (includes post-processing) ──
        System.out.println("\n── A3: renderToSvg() (post-processed) ──");
        String postProcessed = renderer.renderToSvg(DIAGRAM);
        save("postprocessed.svg", postProcessed);
        System.out.println("  Post-processed SVG: " + postProcessed.length() + " chars");
        System.out.println("  Nested <svg> count: " + countOccurrences(postProcessed, "<svg"));
        System.out.println("  <g class=\"node\"> count: " + countOccurrences(postProcessed, "class=\"node"));
        BufferedImage a3img = tryBatik("A3", postProcessed);
        report("A3 (renderToSvg)", a3img);

        // ── A4: Full pipeline ──
        System.out.println("\n── A4: Full pipeline (postProcessSvg + fixForBatik) ──");
        String fixed = MermaidSvgFixup.fixForBatik(postProcessed);
        save("full_pipeline.svg", fixed);
        System.out.println("  Full pipeline SVG: " + fixed.length() + " chars");
        System.out.println("  <g class=\"node\"> count: " + countOccurrences(fixed, "class=\"node"));
        BufferedImage a4img = tryBatik("A4", fixed);
        report("A4 (full pipeline)", a4img);

        // ── Summary ──
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║                      SUMMARY                            ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.println("║ A0 Hand-crafted SVG    : " + (a0img != null ? "✅ OK " : "❌ FAIL") + "                       ║");
        System.out.println("║ A1 Truly raw           : " + (a1img != null ? "✅ OK " : "❌ FAIL") + "                       ║");
        System.out.println("║ A2 Minimal fixes       : " + (a2img != null ? "✅ OK " : "❌ FAIL") + "                       ║");
        System.out.println("║ A3 renderToSvg         : " + (a3img != null ? "✅ OK " : "❌ FAIL") + "                       ║");
        System.out.println("║ A4 Full pipeline       : " + (a4img != null ? "✅ OK " : "❌ FAIL") + "                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        // Show results in Swing window
        Map<String, BufferedImage> results = new LinkedHashMap<String, BufferedImage>();
        if (a0img != null) results.put("A0: Hand-crafted", a0img);
        if (a1img != null) results.put("A1: Truly raw", a1img);
        if (a2img != null) results.put("A2: Minimal fixes", a2img);
        if (a3img != null) results.put("A3: renderToSvg", a3img);
        if (a4img != null) results.put("A4: Full pipeline", a4img);

        if (!results.isEmpty()) {
            final Map<String, BufferedImage> finalResults = results;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showResults(finalResults);
                }
            });
        } else {
            System.err.println("No Mermaid SVG could be rendered by Batik.");
            System.exit(1);
        }
    }

    /**
     * Apply only the absolute minimum fixes needed for Batik,
     * WITHOUT any structural changes (no flattenNestedSvgs, no foreignObject removal).
     */
    private static String applyMinimalFixes(String svg) {
        if (svg == null) return svg;

        // Extract only <svg>...</svg> — strip wrapper divs or trailing markup
        int svgStart = svg.indexOf("<svg");
        int svgEnd = svg.lastIndexOf("</svg>");
        if (svgStart >= 0 && svgEnd > svgStart) {
            svg = svg.substring(svgStart, svgEnd + 6);
        }

        // Ensure xmlns on root <svg>
        if (!svg.contains("xmlns=\"http://www.w3.org/2000/svg\"")) {
            svg = svg.replaceFirst("<svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"");
        }

        // Remove alignment-baseline (Batik rejects "central")
        svg = svg.replaceAll("\\s+alignment-baseline\\s*=\\s*\"[^\"]*\"", "");
        svg = svg.replaceAll("alignment-baseline\\s*:\\s*[^;\"'}<]+[;]?", "");

        // Remove @keyframes blocks (Batik CSS parser NPE) — iterative approach
        svg = removeKeyframesBlocks(svg);

        // Remove animation CSS property
        svg = svg.replaceAll("animation\\s*:\\s*[^;\"'}<]+[;]?", "");

        // Remove stroke-linecap
        svg = svg.replaceAll("stroke-linecap\\s*:\\s*[^;\"'}<]+[;]?", "");

        // Convert hsl()/rgba() to simple fallback values (library-internal methods not public)
        svg = svg.replaceAll("hsl\\([^)]*\\)", "#888888");
        svg = svg.replaceAll("hsla\\([^)]*\\)", "#888888");
        svg = svg.replaceAll("rgba\\([^)]*\\)", "#888888");

        // Remove unsupported CSS properties
        svg = svg.replaceAll("position\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("z-index\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("pointer-events\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("cursor\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("text-align\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("background-color\\s*:\\s*[^;\"]+;?", "");

        // Replace currentColor in CSS context too
        svg = svg.replace("currentColor", "#333333");

        // Replace width="100%" with pixel width
        svg = svg.replaceFirst("width=\"100%\"", "width=\"800\"");

        // Remove max-width
        svg = svg.replaceAll("max-width:\\s*[^;\"]+;?\\s*", "");

        // Replace fill="currentColor" with concrete color
        svg = svg.replace("fill=\"currentColor\"", "fill=\"#333333\"");

        // Remove xmlns="http://www.w3.org/1999/xhtml" from child elements
        svg = svg.replace(" xmlns=\"http://www.w3.org/1999/xhtml\"", "");

        // Remove foreignObject blocks (Batik can't handle them)
        svg = svg.replaceAll("(?is)<foreignObject[^>]*>.*?</foreignObject>", "");
        svg = svg.replaceAll("(?i)<foreignObject[^>]*/\\s*>", "");
        svg = svg.replaceAll("(?i)<foreignobject[^>]*>", "");
        svg = svg.replaceAll("(?i)</foreignobject>", "");

        // Remove stray HTML tags
        String htmlTags = "div|span|p|body|section|i|b|em|strong|h[1-6]|ul|ol|li|table|tr|td|th|label";
        svg = svg.replaceAll("<(" + htmlTags + ")(\\s[^>]*)?>", "");
        svg = svg.replaceAll("</(" + htmlTags + ")>", "");

        // Fix HTML5 void elements
        svg = svg.replaceAll("<(br|hr|wbr|img)(\\s[^>]*?)?\\s*(?<!/)>", "<$1$2/>");

        // Strip CSS :root rules with custom properties
        svg = svg.replaceAll("#mmd-\\d+\\s*:root\\s*\\{[^}]*\\}", "");

        // Wrap <style> content in CDATA so CSS ">" doesn't break XML
        svg = wrapStylesInCdata(svg);

        return svg;
    }

    private static String wrapStylesInCdata(String svg) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(<style[^>]*>)(.*?)(</style>)",
                java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher m = p.matcher(svg);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String content = m.group(2);
            if (!content.contains("<![CDATA[") &&
                    (content.contains(">") || content.contains("<") || content.contains("&"))) {
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                        m.group(1) + "<![CDATA[" + content + "]]>" + m.group(3)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Remove {@code @keyframes} blocks by iteratively scanning for them.
     * More reliable than regex for nested braces.
     */
    private static String removeKeyframesBlocks(String css) {
        StringBuilder sb = new StringBuilder(css.length());
        int i = 0;
        int len = css.length();
        while (i < len) {
            int kfIdx = css.indexOf("@keyframes", i);
            if (kfIdx < 0) {
                sb.append(css, i, len);
                break;
            }
            sb.append(css, i, kfIdx);
            int braceStart = css.indexOf('{', kfIdx);
            if (braceStart < 0) {
                sb.append(css, kfIdx, len);
                break;
            }
            int depth = 0;
            int j = braceStart;
            while (j < len) {
                char c = css.charAt(j);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { j++; break; }
                }
                j++;
            }
            i = j;
        }
        return sb.toString();
    }

    /**
     * Try rendering SVG with Batik via SvgRenderer. On failure, print details.
     */
    private static BufferedImage tryBatik(String label, String svg) {
        try {
            byte[] bytes = svg.getBytes(Charset.forName("UTF-8"));
            BufferedImage img = SvgRenderer.renderToBufferedImageForced(bytes, 800);
            if (img == null) {
                System.out.println("[" + label + "] SvgRenderer returned null — Batik failed silently.");
                // Print first 200 chars and last 200 chars for context
                System.out.println("[" + label + "]   SVG head: " + svg.substring(0, Math.min(200, svg.length())));
                System.out.println("[" + label + "]   SVG tail: " + svg.substring(Math.max(0, svg.length() - 200)));
            }
            return img;
        } catch (Exception e) {
            System.out.println("[" + label + "] Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
            return null;
        }
    }

    private static void report(String name, BufferedImage img) {
        if (img != null) {
            System.out.println(name + ": ✅ " + img.getWidth() + "×" + img.getHeight());
        } else {
            System.out.println(name + ": ❌ FAILED");
        }
    }

    private static void save(String filename, String content) {
        try {
            File f = new File(OUT_DIR, filename);
            FileWriter fw = new FileWriter(f);
            fw.write(content);
            fw.close();
            System.out.println("  Saved: " + f.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("  Save failed: " + e.getMessage());
        }
    }

    private static int countOccurrences(String text, String search) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) >= 0) {
            count++;
            idx += search.length();
        }
        return count;
    }

    private static String extractAttr(String svg, String attrName) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(attrName + "\\s*=\\s*\"([^\"]*)\"")
                .matcher(svg);
        return m.find() ? m.group(1) : "(not found)";
    }

    private static void showResults(Map<String, BufferedImage> results) {
        JFrame frame = new JFrame("Batik Smoke Test — Pipeline Diagnostic");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTabbedPane tabs = new JTabbedPane();
        for (Map.Entry<String, BufferedImage> entry : results.entrySet()) {
            JLabel imgLabel = new JLabel(new ImageIcon(entry.getValue()));
            imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
            JScrollPane scroll = new JScrollPane(imgLabel);
            tabs.addTab(entry.getKey(), scroll);
        }
        panel.add(tabs, BorderLayout.CENTER);

        JButton ok = new JButton("OK – Schließen");
        ok.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                System.exit(0);
            }
        });
        JPanel bottom = new JPanel();
        bottom.add(ok);
        panel.add(bottom, BorderLayout.SOUTH);

        frame.setContentPane(panel);
        frame.setSize(900, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
