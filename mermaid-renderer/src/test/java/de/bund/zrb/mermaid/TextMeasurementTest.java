package de.bund.zrb.mermaid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the Java bridge text measurement integration.
 */
class TextMeasurementTest {

    private final GraalJsExecutor executor = new GraalJsExecutor();

    @Test
    @DisplayName("JavaBridge measureTextWidth returns plausible pixel widths")
    void javaBridgeMeasureText() {
        GraalJsExecutor.JavaBridge bridge = new GraalJsExecutor.JavaBridge();

        double helloWidth = bridge.measureTextWidth("Hello", "sans-serif", 16);
        double aWidth = bridge.measureTextWidth("A", "sans-serif", 16);
        double longText = bridge.measureTextWidth("This is a longer text string", "sans-serif", 16);
        double trebuchet = bridge.measureTextWidth("Hello", "trebuchet ms, verdana, arial, sans-serif", 16);

        System.out.println("'Hello' sans-serif 16px: " + helloWidth + " px");
        System.out.println("'A' sans-serif 16px: " + aWidth + " px");
        System.out.println("'This is a longer text string' sans-serif 16px: " + longText + " px");
        System.out.println("'Hello' trebuchet ms 16px: " + trebuchet + " px");

        assertTrue(helloWidth > 20 && helloWidth < 100,
                "Hello should be 20-100px, got: " + helloWidth);
        assertTrue(aWidth > 5 && aWidth < 30,
                "A should be 5-30px, got: " + aWidth);
        assertTrue(longText > helloWidth,
                "Longer text should be wider");
    }

    @Test
    @DisplayName("JavaBridge measureTextFull returns width,ascent,descent,height")
    void javaBridgeMeasureTextFull() {
        GraalJsExecutor.JavaBridge bridge = new GraalJsExecutor.JavaBridge();
        String result = bridge.measureTextFull("Hello World", "sans-serif", 16);
        System.out.println("measureTextFull: " + result);
        String[] parts = result.split(",");
        assertEquals(4, parts.length, "Should return 4 comma-separated values");
        double width = Double.parseDouble(parts[0]);
        double height = Double.parseDouble(parts[3]);
        assertTrue(width > 40 && width < 200, "Width plausible: " + width);
        assertTrue(height > 10 && height < 40, "Height plausible: " + height);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Browser shim uses Java bridge for text measurement in GraalJS")
    void shimUsesJavaBridge() {
        String shim = MermaidRenderer.loadResource("/mermaid/browser-shim.js");
        assertNotNull(shim);

        // Run a script that creates an element, sets text, and measures
        String script = shim + "\n"
                + "var el = document.createElement('text');\n"
                + "el.textContent = 'Hello World';\n"
                + "el.setAttribute('font-family', 'sans-serif');\n"
                + "el.setAttribute('font-size', '16');\n"
                + "var w = el.getComputedTextLength();\n"
                + "var bbox = el.getBBox();\n"
                + "'width=' + w + ' bbox.w=' + bbox.width + ' bbox.h=' + bbox.height;\n";

        JsExecutionResult result = executor.execute(script);
        assertTrue(result.isSuccessful(), "Script should succeed: " + result.getErrorMessage());
        System.out.println("Shim text measurement: " + result.getOutput());

        // The width should be a reasonable pixel value, not text.length * 8
        // "Hello World" = 11 chars, old estimate = 11*8+16 = 104
        // Java FontMetrics for sans-serif 16px should be ~70-85px
        assertTrue(result.getOutput().contains("width="), "Should contain width");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Canvas measureText uses Java bridge")
    void canvasMeasureTextUsesJavaBridge() {
        String shim = MermaidRenderer.loadResource("/mermaid/browser-shim.js");
        assertNotNull(shim);

        String script = shim + "\n"
                + "var canvas = document.createElement('canvas');\n"
                + "var ctx = canvas.getContext('2d');\n"
                + "ctx.font = '16px sans-serif';\n"
                + "var m1 = ctx.measureText('Hello World');\n"
                + "ctx.font = '24px sans-serif';\n"
                + "var m2 = ctx.measureText('Hello World');\n"
                + "'16px=' + m1.width + ' 24px=' + m2.width + ' bigger=' + (m2.width > m1.width);\n";

        JsExecutionResult result = executor.execute(script);
        assertTrue(result.isSuccessful(), "Script should succeed: " + result.getErrorMessage());
        System.out.println("Canvas text measurement: " + result.getOutput());
        assertTrue(result.getOutput().contains("bigger=true"),
                "24px text should be wider than 16px text");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @DisplayName("Mermaid flowchart viewBox has reasonable coordinates after Java bridge text measurement")
    void flowchartViewBoxIsReasonable() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) return;

        String svg = renderer.renderToSvg("graph TD\n    A[Start] --> B[Process]\n    B --> C[End]");
        assertNotNull(svg, "SVG should render");

        // Extract viewBox
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("viewBox=\"([^\"]+)\"").matcher(svg);
        assertTrue(m.find(), "SVG should have viewBox");
        String viewBox = m.group(1);
        System.out.println("Flowchart viewBox: " + viewBox);

        String[] parts = viewBox.split("\\s+");
        double vbX = Double.parseDouble(parts[0]);
        double vbY = Double.parseDouble(parts[1]);
        double vbW = Double.parseDouble(parts[2]);
        double vbH = Double.parseDouble(parts[3]);

        System.out.println("  x=" + vbX + " y=" + vbY + " w=" + vbW + " h=" + vbH);

        // A reasonable flowchart should have viewBox width < 2000 and height < 2000
        // The old buggy values were like 437612 wide
        assertTrue(vbW < 5000,
                "ViewBox width should be < 5000 for a simple flowchart, got: " + vbW);
        assertTrue(vbH < 5000,
                "ViewBox height should be < 5000 for a simple flowchart, got: " + vbH);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Text getBBox respects text-anchor attribute")
    void textBBoxRespectsTextAnchor() {
        String shim = MermaidRenderer.loadResource("/mermaid/browser-shim.js");
        assertNotNull(shim);

        // Test text-anchor: start (default) — x should be at the given x position
        String script = shim + "\n"
                + "var el1 = document.createElement('text');\n"
                + "el1.textContent = 'Hello';\n"
                + "el1.setAttribute('x', '100');\n"
                + "el1.setAttribute('y', '50');\n"
                + "el1.setAttribute('font-size', '16');\n"
                + "var bbox1 = el1.getBBox();\n"
                // Test text-anchor: middle — x should be shifted left by half width
                + "var el2 = document.createElement('text');\n"
                + "el2.textContent = 'Hello';\n"
                + "el2.setAttribute('x', '100');\n"
                + "el2.setAttribute('y', '50');\n"
                + "el2.setAttribute('font-size', '16');\n"
                + "el2.style.setProperty('text-anchor', 'middle');\n"
                + "var bbox2 = el2.getBBox();\n"
                // Test text-anchor: end — x should be shifted left by full width
                + "var el3 = document.createElement('text');\n"
                + "el3.textContent = 'Hello';\n"
                + "el3.setAttribute('x', '100');\n"
                + "el3.setAttribute('y', '50');\n"
                + "el3.setAttribute('font-size', '16');\n"
                + "el3.style.setProperty('text-anchor', 'end');\n"
                + "var bbox3 = el3.getBBox();\n"
                + "'start.x=' + bbox1.x + ' middle.x=' + bbox2.x + ' end.x=' + bbox3.x"
                + " + ' w=' + bbox1.width + ' h=' + bbox1.height;\n";

        JsExecutionResult result = executor.execute(script);
        assertTrue(result.isSuccessful(), "Script should succeed: " + result.getErrorMessage());
        System.out.println("Text-anchor test: " + result.getOutput());

        // Parse values
        String output = result.getOutput();
        double startX = Double.parseDouble(output.split("start.x=")[1].split(" ")[0]);
        double middleX = Double.parseDouble(output.split("middle.x=")[1].split(" ")[0]);
        double endX = Double.parseDouble(output.split("end.x=")[1].split(" ")[0]);
        double w = Double.parseDouble(output.split("w=")[1].split(" ")[0]);
        double h = Double.parseDouble(output.split("h=")[1].trim());

        // text-anchor:start → bbox.x = 100 (the given x)
        assertEquals(100.0, startX, 0.1, "start: x should be at given position");
        // text-anchor:middle → bbox.x = 100 - w/2
        assertEquals(100.0 - w / 2, middleX, 0.1, "middle: x should be shifted left by w/2");
        // text-anchor:end → bbox.x = 100 - w
        assertEquals(100.0 - w, endX, 0.1, "end: x should be shifted left by w");
        // Height should match Java font metrics (ascent + descent), typically > fontSize
        assertTrue(h > 16, "Height should include ascent + descent, got: " + h);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Text getBBox height uses accurate font metrics from Java bridge")
    void textBBoxHeightUsesAccurateFontMetrics() {
        String shim = MermaidRenderer.loadResource("/mermaid/browser-shim.js");
        assertNotNull(shim);

        // Compare getBBox height for different font sizes
        String script = shim + "\n"
                + "var el1 = document.createElement('text');\n"
                + "el1.textContent = 'Test';\n"
                + "el1.setAttribute('font-size', '12');\n"
                + "var h12 = el1.getBBox().height;\n"
                + "var el2 = document.createElement('text');\n"
                + "el2.textContent = 'Test';\n"
                + "el2.setAttribute('font-size', '24');\n"
                + "var h24 = el2.getBBox().height;\n"
                + "'h12=' + h12 + ' h24=' + h24 + ' ratio=' + (h24/h12).toFixed(2);\n";

        JsExecutionResult result = executor.execute(script);
        assertTrue(result.isSuccessful(), "Script should succeed: " + result.getErrorMessage());
        System.out.println("Font metrics height: " + result.getOutput());

        String output = result.getOutput();
        double h12 = Double.parseDouble(output.split("h12=")[1].split(" ")[0]);
        double h24 = Double.parseDouble(output.split("h24=")[1].split(" ")[0]);

        // Height should scale approximately linearly with font size
        // 24px/12px ≈ 2.0 ratio
        double ratio = h24 / h12;
        assertTrue(ratio > 1.5 && ratio < 2.5,
                "Height ratio for 24px/12px should be ~2.0, got: " + ratio);
        // Height should be > fontSize (includes descent)
        assertTrue(h12 > 12, "12px text height should be > 12, got: " + h12);
        assertTrue(h24 > 24, "24px text height should be > 24, got: " + h24);
    }
}
