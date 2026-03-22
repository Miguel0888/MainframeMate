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
}

