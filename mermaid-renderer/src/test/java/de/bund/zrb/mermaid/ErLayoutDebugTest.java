package de.bund.zrb.mermaid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test: traces getBBox() return values for ER diagram attribute structures.
 */
class ErLayoutDebugTest {

    private final GraalJsExecutor executor = new GraalJsExecutor();

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("ER attribute column getBBox returns correct widths")
    void erAttributeColumnBBox() {
        String shim = MermaidRenderer.loadResource("/mermaid/browser-shim.js");
        assertNotNull(shim);

        // Simulate the exact ER attribute structure:
        // <g class="label attribute-type">
        //   <g></g>
        //   <text y="-10.1">
        //     <tspan class="text-outer-tspan row" x="0" y="-0.1em" dy="1.1em">
        //       <tspan class="text-inner-tspan" font-style="normal" font-weight="normal">string</tspan>
        //     </tspan>
        //   </text>
        // </g>
        String script = shim + "\n"
                + "function makeAttrLabel(text) {\n"
                + "  var g = document.createElementNS('http://www.w3.org/2000/svg', 'g');\n"
                + "  var innerG = document.createElementNS('http://www.w3.org/2000/svg', 'g');\n"
                + "  g.appendChild(innerG);\n"
                + "  var textEl = document.createElementNS('http://www.w3.org/2000/svg', 'text');\n"
                + "  textEl.setAttribute('y', '-10.1');\n"
                + "  var outerTspan = document.createElementNS('http://www.w3.org/2000/svg', 'tspan');\n"
                + "  outerTspan.setAttribute('class', 'text-outer-tspan row');\n"
                + "  outerTspan.setAttribute('x', '0');\n"
                + "  outerTspan.setAttribute('y', '-0.1em');\n"
                + "  outerTspan.setAttribute('dy', '1.1em');\n"
                + "  var innerTspan = document.createElementNS('http://www.w3.org/2000/svg', 'tspan');\n"
                + "  innerTspan.setAttribute('font-style', 'normal');\n"
                + "  innerTspan.setAttribute('class', 'text-inner-tspan');\n"
                + "  innerTspan.setAttribute('font-weight', 'normal');\n"
                + "  innerTspan.textContent = text;\n"
                + "  outerTspan.appendChild(innerTspan);\n"
                + "  textEl.appendChild(outerTspan);\n"
                + "  g.appendChild(textEl);\n"
                + "  return g;\n"
                + "}\n"
                + "\n"
                + "var typeLabel = makeAttrLabel('string');\n"
                + "var nameLabel = makeAttrLabel('name');\n"
                + "var keysLabel = makeAttrLabel('');\n"
                + "\n"
                + "var typeBBox = typeLabel.getBBox();\n"
                + "var nameBBox = nameLabel.getBBox();\n"
                + "var keysBBox = keysLabel.getBBox();\n"
                + "\n"
                + "// Also test the inner text element directly\n"
                + "var typeText = typeLabel.querySelector('text');\n"
                + "var typeTextBBox = typeText ? typeText.getBBox() : {width: -1, height: -1};\n"
                + "\n"
                + "// And the tspan\n"
                + "var typeTspan = typeLabel.querySelector('tspan');\n"
                + "var typeTspanBBox = typeTspan ? typeTspan.getBBox() : {width: -1, height: -1};\n"
                + "\n"
                + "'typeGroup=[' + typeBBox.width + 'x' + typeBBox.height + ']'"
                + " + ' nameGroup=[' + nameBBox.width + 'x' + nameBBox.height + ']'"
                + " + ' keysGroup=[' + keysBBox.width + 'x' + keysBBox.height + ']'"
                + " + ' typeText=[' + typeTextBBox.width + 'x' + typeTextBBox.height + ']'"
                + " + ' typeTspan=[' + typeTspanBBox.width + 'x' + typeTspanBBox.height + ']';\n";

        JsExecutionResult result = executor.execute(script);
        assertTrue(result.isSuccessful(), "Script should succeed: " + result.getErrorMessage());
        System.out.println("ER attribute getBBox: " + result.getOutput());

        String output = result.getOutput();
        
        // Parse type group width
        String typeW = output.split("typeGroup=\\[")[1].split("x")[0];
        double typeWidth = Double.parseDouble(typeW);
        
        // Parse name group width
        String nameW = output.split("nameGroup=\\[")[1].split("x")[0];
        double nameWidth = Double.parseDouble(nameW);
        
        System.out.println("typeWidth=" + typeWidth + " nameWidth=" + nameWidth);
        
        // Type column should have measurable width (> 20px for "string" at ~10px font)
        assertTrue(typeWidth > 10, "Type label 'string' should have width > 10px, got: " + typeWidth);
        assertTrue(nameWidth > 10, "Name label 'name' should have width > 10px, got: " + nameWidth);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("getBBox on nested tspan returns correct text metrics")
    void nestedTspanBBox() {
        String shim = MermaidRenderer.loadResource("/mermaid/browser-shim.js");
        assertNotNull(shim);

        // Test the exact structure used by Mermaid's ER labels
        String script = shim + "\n"
                + "var text = document.createElementNS('http://www.w3.org/2000/svg', 'text');\n"
                + "text.setAttribute('y', '-10.1');\n"
                + "var outer = document.createElementNS('http://www.w3.org/2000/svg', 'tspan');\n"
                + "outer.setAttribute('x', '0');\n"
                + "outer.setAttribute('y', '-0.1em');\n"
                + "outer.setAttribute('dy', '1.1em');\n"
                + "var inner = document.createElementNS('http://www.w3.org/2000/svg', 'tspan');\n"
                + "inner.textContent = 'CUSTOMER';\n"
                + "outer.appendChild(inner);\n"
                + "text.appendChild(outer);\n"
                + "\n"
                + "var textBBox = text.getBBox();\n"
                + "var outerBBox = outer.getBBox();\n"
                + "var innerBBox = inner.getBBox();\n"
                + "var textLen = text.getComputedTextLength();\n"
                + "\n"
                + "'textBBox=[x=' + textBBox.x + ',y=' + textBBox.y + ',w=' + textBBox.width + ',h=' + textBBox.height + ']'"
                + " + ' outerTspan=[x=' + outerBBox.x + ',y=' + outerBBox.y + ',w=' + outerBBox.width + ',h=' + outerBBox.height + ']'"
                + " + ' innerTspan=[x=' + innerBBox.x + ',y=' + innerBBox.y + ',w=' + innerBBox.width + ',h=' + innerBBox.height + ']'"
                + " + ' textLen=' + textLen;\n";

        JsExecutionResult result = executor.execute(script);
        assertTrue(result.isSuccessful(), "Script should succeed: " + result.getErrorMessage());
        System.out.println("Nested tspan getBBox: " + result.getOutput());

        // "CUSTOMER" should have non-zero width
        String output = result.getOutput();
        String textW = output.split("textBBox=\\[")[1].split(",w=")[1].split(",h=")[0];
        double textWidth = Double.parseDouble(textW);
        assertTrue(textWidth > 20, "Text 'CUSTOMER' should have width > 20px, got: " + textWidth);
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @DisplayName("Full ER diagram renders with separated attribute columns")
    void erDiagramAttributeColumns() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) return;

        String svg = renderer.renderToSvg(
                "erDiagram\n"
                + "    CUSTOMER {\n"
                + "        string name\n"
                + "        int id\n"
                + "    }");
        assertNotNull(svg, "SVG should render");
        System.out.println("ER SVG (first 2000 chars): " + svg.substring(0, Math.min(2000, svg.length())));

        // Check that attribute-type and attribute-name have DIFFERENT x-positions
        // in their translate() transforms
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "class=\"label (attribute-\\w+)\"\\s+transform=\"translate\\(([^,]+),");
        java.util.regex.Matcher m = p.matcher(svg);
        java.util.Map<String, java.util.Set<String>> columnXPositions = new java.util.LinkedHashMap<String, java.util.Set<String>>();
        while (m.find()) {
            String columnName = m.group(1);
            String xPos = m.group(2).trim();
            if (!columnXPositions.containsKey(columnName)) {
                columnXPositions.put(columnName, new java.util.LinkedHashSet<String>());
            }
            columnXPositions.get(columnName).add(xPos);
            System.out.println("  " + columnName + " x=" + xPos);
        }

        // attribute-type and attribute-name should have different x-positions
        if (columnXPositions.containsKey("attribute-type") && columnXPositions.containsKey("attribute-name")) {
            String typeX = columnXPositions.get("attribute-type").iterator().next();
            String nameX = columnXPositions.get("attribute-name").iterator().next();
            assertNotEquals(typeX, nameX,
                    "attribute-type (x=" + typeX + ") and attribute-name (x=" + nameX 
                    + ") should have DIFFERENT x positions (columns must not overlap!)");
        }
    }
}

