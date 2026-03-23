package de.bund.zrb.mermaid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BatikBBoxService} — verifies that Batik's GVT tree
 * produces accurate bounding boxes for SVG fragments.
 */
class BatikBBoxServiceTest {

    private final BatikBBoxService service = new BatikBBoxService();

    @Test
    void simpleTextElement() {
        String svg = "<text x=\"10\" y=\"20\" style=\"font-size:16px; font-family:sans-serif\">Hello</text>";
        String result = service.computeBBox(svg);
        assertNotNull(result, "Batik should compute BBox for simple text");

        double[] bbox = parseBBox(result);
        // x should be near 10, width should be > 0
        assertTrue(bbox[2] > 0, "width should be > 0, got: " + bbox[2]);
        assertTrue(bbox[3] > 0, "height should be > 0, got: " + bbox[3]);
        System.out.println("Simple text BBox: " + result);
    }

    @Test
    void textWithTspanChildren() {
        String svg = "<text x=\"50\" y=\"30\" style=\"font-size:14px; font-family:sans-serif\">"
                + "<tspan x=\"50\" dy=\"0\">Zeile 1</tspan>"
                + "<tspan x=\"50\" dy=\"1.2em\">Zeile 2</tspan>"
                + "<tspan x=\"50\" dy=\"1.2em\">Zeile 3</tspan>"
                + "</text>";
        String result = service.computeBBox(svg);
        assertNotNull(result, "Batik should compute BBox for text with tspans");

        double[] bbox = parseBBox(result);
        assertTrue(bbox[2] > 0, "width should be > 0");
        // Height should accommodate 3 lines (at least 2.4em × 14px ≈ 34px)
        assertTrue(bbox[3] > 30, "height should be > 30px for 3 lines, got: " + bbox[3]);
        System.out.println("Multi-line text BBox: " + result);
    }

    @Test
    void rectElement() {
        String svg = "<rect x=\"5\" y=\"10\" width=\"100\" height=\"50\" fill=\"#ccc\"/>";
        String result = service.computeBBox(svg);
        assertNotNull(result, "Batik should compute BBox for rect");

        double[] bbox = parseBBox(result);
        assertEquals(5.0, bbox[0], 0.5, "x");
        assertEquals(10.0, bbox[1], 0.5, "y");
        assertEquals(100.0, bbox[2], 0.5, "width");
        assertEquals(50.0, bbox[3], 0.5, "height");
        System.out.println("Rect BBox: " + result);
    }

    @Test
    void cacheHit() {
        String svg = "<text x=\"0\" y=\"16\" style=\"font-size:16px\">Cached</text>";
        String first = service.computeBBox(svg);
        String second = service.computeBBox(svg);
        assertEquals(first, second, "Cached result should be identical");
    }

    @Test
    void invalidFragment() {
        String result = service.computeBBox("<not-valid-svg");
        assertNull(result, "Invalid SVG should return null");
    }

    @Test
    void emptyFragment() {
        assertNull(service.computeBBox(""));
        assertNull(service.computeBBox(null));
    }

    @Test
    void textWithEmUnits() {
        // em-based positioning: the key use case that JS heuristics get wrong
        String svg = "<text x=\"100\" y=\"200\" style=\"font-size:16px; font-family:sans-serif\">"
                + "<tspan x=\"100\" dy=\"0\">Passwort pruefen</tspan>"
                + "<tspan x=\"100\" dy=\"1.5em\">Account sperren</tspan>"
                + "</text>";
        String result = service.computeBBox(svg);
        assertNotNull(result);

        double[] bbox = parseBBox(result);
        assertTrue(bbox[2] > 50, "width should be > 50px for long text");
        // 2 lines with 1.5em spacing at 16px = 24px gap + line heights
        assertTrue(bbox[3] > 20, "height should reflect multi-line layout");
        System.out.println("Em-units text BBox: " + result);
    }

    // ── Helper ──

    private static double[] parseBBox(String result) {
        String[] parts = result.split(",");
        return new double[] {
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3])
        };
    }
}

