package de.bund.zrb.mermaid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for ER diagram attribute column layout.
 * <p>
 * The browser shim must correctly handle:
 * <ol>
 *   <li>{@code :not()} CSS pseudo-selector — required for Mermaid's ER column positioning</li>
 *   <li>{@code <rect>} without width/height → 0×0 bbox (not 20×20 fallback)</li>
 *   <li>{@code <text>}/{@code <tspan>} with empty content → 0×0 bbox</li>
 * </ol>
 */
class ErLayoutTest {

    private final GraalJsExecutor executor = new GraalJsExecutor();

    @Test
    @DisplayName("getBBox returns 0x0 for rect without width/height")
    void rectWithoutDimensionsReturnsZero() {
        String shim = MermaidRenderer.loadResource("/mermaid/browser-shim.js");
        String script = shim + "\n"
                + "var rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');\n"
                + "rect.setAttribute('class', 'background');\n"
                + "var bbox = rect.getBBox();\n"
                + "'w=' + bbox.width + ' h=' + bbox.height;\n";

        JsExecutionResult result = executor.execute(script);
        assertTrue(result.isSuccessful(), "Script should succeed: " + result.getErrorMessage());
        assertEquals("w=0 h=0", result.getOutput(), "Rect without w/h should have 0x0 bbox");
    }

    @Test
    @DisplayName(":not(:first-child) selector matches non-first children")
    void notFirstChildSelectorWorks() {
        String shim = MermaidRenderer.loadResource("/mermaid/browser-shim.js");
        String script = shim + "\n"
                + "var parent = document.createElementNS('http://www.w3.org/2000/svg', 'g');\n"
                + "var c1 = document.createElementNS('http://www.w3.org/2000/svg', 'g');\n"
                + "c1.setAttribute('class', 'first');\n"
                + "var c2 = document.createElementNS('http://www.w3.org/2000/svg', 'g');\n"
                + "c2.setAttribute('class', 'second');\n"
                + "var c3 = document.createElementNS('http://www.w3.org/2000/svg', 'g');\n"
                + "c3.setAttribute('class', 'third');\n"
                + "parent.appendChild(c1);\n"
                + "parent.appendChild(c2);\n"
                + "parent.appendChild(c3);\n"
                + "var matches = parent.querySelectorAll('g:not(:first-child)');\n"
                + "'count=' + matches.length + ' classes=' + matches.map(function(m){return m.className;}).join(',');\n";

        JsExecutionResult result = executor.execute(script);
        assertTrue(result.isSuccessful(), "Script should succeed: " + result.getErrorMessage());
        System.out.println(":not(:first-child) test: " + result.getOutput());
        assertTrue(result.getOutput().contains("count=2"), "Should match 2 non-first children");
        assertTrue(result.getOutput().contains("second"), "Should include second child");
        assertTrue(result.getOutput().contains("third"), "Should include third child");
        assertFalse(result.getOutput().contains("first"), "Should NOT include first child");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @DisplayName("ER diagram has separated attribute columns (not overlapping)")
    void erDiagramAttributeColumnsAreSeparated() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) return;

        String svg = renderer.renderToSvg(
                "erDiagram\n"
                + "    CUSTOMER {\n"
                + "        string name\n"
                + "        int id\n"
                + "    }");
        assertNotNull(svg, "SVG should render");

        // Extract attribute column x-positions from translate() transforms
        Pattern p = Pattern.compile(
                "class=\"label (attribute-\\w+)\"\\s+transform=\"translate\\(([^,]+),");
        Matcher m = p.matcher(svg);
        Map<String, Set<String>> columnXPositions = new LinkedHashMap<String, Set<String>>();
        while (m.find()) {
            String col = m.group(1);
            String x = m.group(2).trim();
            if (!columnXPositions.containsKey(col))
                columnXPositions.put(col, new LinkedHashSet<String>());
            columnXPositions.get(col).add(x);
        }

        assertTrue(columnXPositions.containsKey("attribute-type"), "Should have attribute-type column");
        assertTrue(columnXPositions.containsKey("attribute-name"), "Should have attribute-name column");

        String typeX = columnXPositions.get("attribute-type").iterator().next();
        String nameX = columnXPositions.get("attribute-name").iterator().next();

        assertNotEquals(typeX, nameX,
                "attribute-type (x=" + typeX + ") and attribute-name (x=" + nameX
                + ") must have DIFFERENT x positions — columns must not overlap");
    }
}

