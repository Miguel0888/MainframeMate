package de.bund.zrb.mermaid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Standalone entry point — renders a sample Mermaid diagram and prints the SVG.
 * <p>
 * Run via: {@code gradlew :mermaid-renderer:run}
 */
public final class MermaidRendererMain {

    public static void main(String[] args) {
        MermaidRenderer renderer = MermaidRenderer.getInstance();

        System.out.println("Mermaid Renderer");
        System.out.println("================");
        System.out.println("Available: " + renderer.isAvailable());
        System.out.println();

        if (!renderer.isAvailable()) {
            System.out.println("mermaid.min.js not found on classpath.");
            System.out.println("Place it into src/main/resources/mermaid/ and re-build.");
            return;
        }

        String diagram = "graph TD; A-->B; B-->C;";
        System.out.println("Diagram: " + diagram);
        System.out.println();

        long start = System.currentTimeMillis();
        String svg = renderer.renderToSvg(diagram);
        long elapsed = System.currentTimeMillis() - start;

        if (svg != null) {
            System.out.println("SVG rendered successfully (" + svg.length() + " chars, " + elapsed + " ms)");
            System.out.println();
            if (svg.length() > 500) {
                System.out.println(svg.substring(0, 500) + "...");
            } else {
                System.out.println(svg);
            }
        } else {
            System.out.println("Rendering FAILED");
        }
    }
}

